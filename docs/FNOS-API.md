# 飞牛影视（fnOS）原生 API 说明

> 本文是 `FnOsBackend.kt` 的实现依据。飞牛官方没有公开 API 文档，以下内容通过对
> 飞牛 Web 客户端（`https://<nas>:5666/v/api/v1/*`）与开源第三方客户端
> [fntv-client-multiplatform](https://github.com/FNOSP/fntv-client-multiplatform)
> 的协议分析整理，仅用于互操作性实现。

## 1. 基础约定

- Base URL：fnOS Web 端口，默认 `http://<nas>:5666`（HTTPS 为 `5667`）
- 所有接口前缀：`/v/api/v1`
- 响应统一包装：

```json
{ "code": 0, "msg": "", "data": { } }
```

`code == 0` 为成功；`code == -6` 常见于「记录不存在」；`code == 401/403` 为鉴权失败。

## 2. 请求头

| Header | 值 | 说明 |
| --- | --- | --- |
| `Authorization` | `<token>` | 登录返回的 token，原样放入 |
| `Cookie` | `Trim-MC-token=<token>` | 同一 token |
| `Accept` | `application/json` | |
| `x-trim-client` | `web` | 缺少会被当作非影视客户端 |
| `x-trim-client-version` | `616` | 缺少会隐藏直播（IPTV）媒体库 |
| `Authx` | `nonce=..&timestamp=..&sign=..` | 逐请求签名，见下 |
| `User-Agent` | 任意浏览器 UA | 建议伪装成 Chrome |

## 3. Authx 签名

```
sign = md5( apiKey + "_" + path + "_" + nonce + "_" + timestamp + "_" + payloadMd5 + "_" + apiSecret )
```

- `apiKey` / `apiSecret`：随 Web 客户端下发的常量（见 `FnOsBackend.companion`），**不是用户凭证**
- `path`：仅路径，不含 query，例如 `/v/api/v1/item/list`
- `nonce`：6 位随机数字（100000–999999）
- `timestamp`：毫秒时间戳字符串
- `payloadMd5`：
  - 有 JSON body → `md5(实际发送的 body 字符串)`
  - 无 body 但有 query → `md5(参数按 key 升序拼接的 "k=v&k=v")`
  - 都没有 → `md5("")`

最终 `Authx: nonce=<nonce>&timestamp=<ts>&sign=<sign>`。

> 注意：签名对 body 的**原始字符串**做 md5，所以必须先序列化、再用同一字符串发送。
> 播放地址（`media/range`）与图片（`sys/img`）只需要 Cookie + Authorization，
> 不需要 Authx；字幕下载（`subtitle/dl`）需要。

## 4. 常用接口

### 4.1 登录

```
POST /v/api/v1/login
{ "username": "...", "password": "..." }

→ data: { "token": "..." }
```

### 4.2 用户

```
GET /v/api/v1/user/info → data: { guid, username, is_admin, ... }
```

### 4.3 媒体库与浏览

```
GET  /v/api/v1/mediadb/list
     → data: [ { guid, title, posters: [...], category, view_type } ]

POST /v/api/v1/item/list
{
  "ancestor_guid": "<库 guid>",
  "page": 1,
  "page_size": 50,
  "sort_column": "create_time",
  "sort_type": "DESC",
  "exclude_grouped_video": 1,
  "tags": { "type": ["Movie","TV","Directory","Video","LiveChannel"] }
}
     → data: { list: [MediaItem], total, mdb_name, jump_list }
```

`tags.type` 必须显式枚举，传空数组时服务端只返回库名、不返回条目；
浏览直播库时必须包含 `LiveChannel`。

```
GET  /v/api/v1/play/list        → 继续观看（data: [ { guid,title,poster,ts,duration,... } ]）
GET  /v/api/v1/search/list?q=   → 搜索
GET  /v/api/v1/item/{guid}      → 详情（ItemResponse）
GET  /v/api/v1/season/list/{seriesGuid} → 季列表
GET  /v/api/v1/episode/list/{seasonGuid} → 集列表
GET  /v/api/v1/person/list/{itemGuid}    → 演职员
GET  /v/api/v1/tag/genres                → 类型字典（id → 名称）
```

### 4.4 播放

```
POST /v/api/v1/play/info
{ "item_guid": "<条目 guid>", "media_guid": "<可选，指定文件>" }
   → data: { guid, parent_guid, media_guid, video_guid, audio_guid,
             subtitle_guid, ts, type, item: {...}, direct_link_audio_index }

POST /v/api/v1/stream
{ "media_guid": "<media_guid>", "level": 1 }
   → data: { video_stream: {...}, audio_streams: [...], subtitle_streams: [...],
             file_stream: {...}, qualities: [...],
             direct_link_qualities: [...], header: {...} }
```

本地媒体播放地址（**原始文件直读，支持 Range**）：

```
GET {base}/v/api/v1/media/range/{media_guid}
Header: Cookie: Trim-MC-token=<token>; Authorization: <token>
```

网盘媒体（夸克 / 115 等）需先换 CDN 直链：

```
POST /v/api/v1/play/play
{ "media_guid": "...", "video_guid": "...", "video_encoder": "hevc",
  "resolution": "2160p", "bitrate": 20000000, "startTimestamp": 0,
  "audio_encoder": "aac", "audio_guid": "...", "subtitle_guid": "",
  "channels": 2, "forced_sdr": 0 }
   → data: { "play_link": "https://..." }
```

115 的 m3u8 画质需经 `/v/api/v1/wp/m3u8?originalUrl=<urlencoded>` 代理。

### 4.5 字幕

```
GET {base}/v/api/v1/subtitle/dl/{subtitle_guid}   需要 Authx
```

返回字幕文件内容（srt/ass/vtt）。内嵌字幕不走此接口，由播放器直接从容器解析。

### 4.6 进度与收藏

```
POST /v/api/v1/play/record
{ "item_guid","media_guid","video_guid","audio_guid","subtitle_guid",
  "resolution","bitrate","ts","duration","play_link","device_id",
  "direct_link_audio_index","lan","device_name" }

PUT    /v/api/v1/item/favorite   { "item_guid": "..." }   加入收藏
DELETE /v/api/v1/item/favorite   { "item_guid": "..." }   取消收藏
POST   /v/api/v1/item/watched    { "item_guid": "..." }   标记已看
DELETE /v/api/v1/item/watched    { "item_guid": "..." }   取消已看
```

## 5. 图片

```
GET {base}/v/api/v1/sys/img{path}
```

`path` 来自条目字段 `poster` / `posters` / `backdrops` / `profile_path`，
形如 `/xxx/yyy.jpg`，需要携带 Cookie / Authorization。

## 6. 与 Emby 的差异

| 维度 | Emby | 飞牛影视 |
| --- | --- | --- |
| 鉴权 | `X-Emby-Token` + `X-Emby-Authorization` | `Authorization` + `Cookie` + `Authx` 签名 |
| ID 体系 | `ItemId` / `MediaSourceId` | `item_guid` / `media_guid` / `video_guid` / `audio_guid` |
| 直链 | `/Videos/{id}/stream?Static=true` | `/v/api/v1/media/range/{media_guid}` |
| 进度 | `Sessions/Playing*`（ticks, 1e-7s） | `play/record`（秒） |
| 集列表 | `/Shows/{id}/Episodes` | `/episode/list/{seasonGuid}` |
| 外挂字幕 | `DeliveryUrl` 可直接播放 | 需下载后侧载 |
| 转码 | `TranscodingUrl`(HLS) | `play/play` 指定编码 + `media/range` |

两套协议在 `MediaBackend` 接口后统一为同一套领域模型，UI 层无感知。
