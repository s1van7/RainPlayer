# Rain

[![Build APK](https://github.com/s1van7/Rain/actions/workflows/android.yml/badge.svg)](https://github.com/s1van7/Rain/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/minSdk-21-brightgreen.svg)
![platform](https://img.shields.io/badge/platform-Android%20%7C%20Android%20TV-3ddc84.svg)

一个同时支持 **Emby / Jellyfin / 飞牛影视（fnOS）** 的 Android 与 Android TV 播放器。
参照 [Flow](https://github.com/Flow-Media-Client/Flow-release) 的 TV 交互、
[CineIsle](https://github.com/nu1lkali/CineIsle) 的直链播放思路、
[ChaiChaiEmbyTV](https://github.com/dh374374/ChaiChaiEmbyTV) 的低版本兼容策略实现。

- **最低 Android 5.0（API 21）**，同一份 APK 同时适配手机 / 平板 / 电视盒子
- **播放内核**：AndroidX Media3 (ExoPlayer 1.6) + Jellyfin FFmpeg 解码扩展
- **直通优先**：默认直链播放，不转码，保住 4K/8K、HDR10/HLG、杜比视界与杜比全景声
- **双后端**：Emby 走官方 REST 协议；飞牛影视走其原生 `/v/api/v1` 协议（含 Authx 签名）

---

## 1. 功能

| 分类 | 能力 |
| --- | --- |
| 服务器 | Emby / Jellyfin / 飞牛影视；多服务器保存与切换；登录态持久化 |
| 浏览 | 首页（继续观看 + 各媒体库最新）、媒体库网格分页、搜索、详情页（季/集/演职员） |
| 播放 | 直链直通、HLS 转码兜底、断点续播、自动连播下一集、倍速 0.5×–2× |
| 画面 | 4K/8K 输出、HDR10 / HLG / HDR10+ / 杜比视界、按片源自动切显示模式（分辨率 + 帧率） |
| 声音 | AC3 / EAC3 / DTS / DTS-HD / TrueHD 直通；不支持时 FFmpeg 软解为 PCM |
| 字幕 | 内嵌（SRT / ASS / SSA / PGS）+ 外挂字幕侧载、字幕轨道切换、关闭字幕 |
| 进度 | 播放开始/进度/停止回传服务器（Emby `Sessions/Playing*`，飞牛 `play/record`） |
| 交互 | 手机：Material 3 + 底部导航；TV：D-pad 焦点导航、遥控器按键、6 秒自动隐藏控制条 |

---

## 2. 快速开始

### 安装

```bash
adb install -r app-release-universal.apk
```

电视上也可以通过 U 盘 / 当贝市场等安装 `universal` 或 `arm64-v8a` 包。

### 连接服务器

1. 选择服务器类型：`Emby` / `Jellyfin` / `飞牛影视`
2. 填地址（**不要**带路径）：
   - Emby：`http://192.168.1.10:8096`
   - Jellyfin：`http://192.168.1.10:8096`
   - 飞牛影视：`http://192.168.1.10:5666`（fnOS Web 端口，影视 API 与之同源）
3. 输入账号密码 → 连接

> 飞牛影视需要在 fnOS 里已安装并启用「影视」应用，且账号有影视访问权限。
> 若 NAS 开启了「访问码」，请先在浏览器完成一次访问码验证再登录。

---

## 3. 架构

```
app/src/main/java/com/rain/player/
├── RainApp.kt                  Application + 极简服务定位器(Graph) + Coil 图片加载
├── core/
│   ├── Prefs.kt                 本地设置（服务器、播放开关、字幕偏好）
│   ├── Net.kt                   OkHttp 客户端、按 host 注入鉴权头的拦截器
│   └── Json.kt                  宽松 JSON 解析扩展（字段缺失/类型漂移不崩）
├── data/
│   ├── Models.kt                统一领域模型（MediaCard / MediaDetail / PlaybackPlan…）
│   ├── MediaBackend.kt          后端接口 + 工厂
│   ├── EmbyBackend.kt           Emby / Jellyfin 实现
│   └── FnOsBackend.kt           飞牛影视实现（含 Authx 签名）
├── player/
│   ├── PlayerFactory.kt         ExoPlayer 构建（渲染器/直通/缓冲/轨道选择）
│   ├── PlayerCapabilities.kt    HDR / DV / 音频直通能力探测
│   ├── DisplayModeController.kt 输出分辨率与刷新率匹配
│   ├── PlayerActivity.kt        播放会话、进度上报、连播、错误兜底
│   └── PlayerScreen.kt          播放器 UI（Compose 覆盖层，遥控器可用）
└── ui/
    ├── MainActivity.kt          手机入口（识别到 TV 自动跳转）
    ├── LoginScreen.kt           服务器登录
    ├── SettingsScreen.kt        设置 + 本机解码能力
    ├── phone/                   手机端：首页 / 媒体库 / 搜索 / 详情
    └── tv/                      TV 端：左侧导航 + 大屏首页 / 详情
```

数据流：

```
UI ──suspend──> MediaBackend(Emby|FnOs) ──OkHttp──> 服务器
                        │
                        └──> PlaybackPlan(URL + headers + 轨道)
                                     │
                              PlayerActivity ──> Media3 ExoPlayer
                                     │
                              Sessions/Playing* 回传
```

`MediaBackend` 是唯一的后端抽象，UI 与播放器完全不感知服务器类型。

---

## 4. 播放内核细节

### 4.1 直通优先

Emby 请求 `PlaybackInfo` 时提交一个「什么都能直放」的 `DeviceProfile`
（MKV/MP4/TS + H264/HEVC/AV1/VP9 + AAC/AC3/EAC3/DTS/TrueHD/FLAC/Opus），
拿到 `MediaSourceId` 后自行拼 `Videos/{id}/stream?Static=true` 播放原始文件，
**服务器不转码**。只有当服务器明确拒绝直放、或用户点「转码播放」时才走 HLS。

飞牛直接使用 `media/range/{media_guid}`，同样是原文件直读（支持 Range 拖动）。

### 4.2 音频直通 / 软解

- 直通：`AudioCapabilities.getCapabilities(context)` 交给 `DefaultAudioSink`，
  AC3/EAC3/DTS/TrueHD 原码送 HDMI（需要电视或功放支持，见设置页「本机解码能力」）。
- 关闭直通：用 `DEFAULT_AUDIO_CAPABILITIES`（仅 PCM）覆盖，
  所有压缩音频强制走解码器；平台解码器缺失时由 FFmpeg 扩展接管
  （`ac3 / eac3 / truehd / dts / flac / alac / opus`）。

### 4.3 4K / 8K 与 HDR

- 视频解码走 `MediaCodec`，开启 `setEnableDecoderFallback(true)`，
  硬解失败会自动换下一个解码器，而不是直接黑屏。
- HDR10 / HLG / 杜比视界由设备解码器与显示链路决定，播放器只负责
  **不做任何 tone-map 破坏**（不强制转 SDR），并实时探测能力提示用户。
- `DisplayModeController` 在起播后按 `videoFormat` 的宽高与帧率
  选择最接近的 `Display.Mode`（例如 3840×2160@23.976），避免 4K 被压到 1080p 输出。

### 4.4 字幕

- 内嵌字幕由 Media3 解析（SRT / ASS / SSA / PGS 位图字幕）。
- 外挂字幕侧载为 `MediaItem.SubtitleConfiguration`：
  - Emby 直接给 `DeliveryUrl`（带 `api_key`）；
  - 飞牛需要签名，先把字幕下载到 `cache/subtitles/` 再以本地文件侧载。

### 4.5 失败兜底

播放出错时弹出对话框，可选「重试」或「转码播放」（改用服务器 HLS），
并在信息面板显示 `errorCodeName` 与可能原因（8K AV1 / DV Profile 7 等）。

---

## 5. 飞牛影视兼容说明

飞牛没有公开 API，本项目按其 Web 客户端实际协议实现，详见
[`docs/FNOS-API.md`](docs/FNOS-API.md)。要点：

- 鉴权：`Authorization: <token>` + `Cookie: Trim-MC-token=<token>`
- 客户端标识：`x-trim-client: web`、`x-trim-client-version: 616`
- 每请求签名 `Authx`：
  `md5(apiKey_path_nonce_timestamp_bodyMd5_apiSecret)`
- 浏览媒体库时 `tags.type` 必须显式传
  `["Movie","TV","Directory","Video","LiveChannel"]`，否则只返回库名、无条目

---

## 6. 能力边界（重要）

这些是**物理/授权限制**，不是代码没写：

| 场景 | 说明 |
| --- | --- |
| 杜比视界 Profile 5 | 片源无 HDR10 兼容层，**必须**设备支持 DV 解码，否则只能服务器转码 |
| 杜比视界 Profile 7 | 双轨（BL+EL），多数盒子只能解 BL（等同 HDR10），属正常现象 |
| 8K HEVC / AV1 | 依赖 SoC 硬解；老盒子软解 8K 基本不可能，播放器会提示并建议转码 |
| TrueHD / DTS-HD MA | 直通需要 HDMI 链路全程支持；不支持时 FFmpeg 解码为 PCM（无损但非原码） |
| Android 5/6 老设备 | 可用，但 4K 高码率与 ASS 特效字幕性能有限，建议 1080p 或转码 |
| 杜比全景声 (Atmos) | 通过 EAC3-JOC / TrueHD 直通实现；本机解码会丢失 Atmos 元数据（正常） |

---

## 7. 自行编译

环境：JDK 17+、Android SDK（platform 35、build-tools 35.0.0）。

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug        # 调试包
./gradlew :app:assembleRelease      # 签名包（ABI 拆分 + universal）
```

产物在 `app/build/outputs/apk/`（`universal` 通用包 + 各 ABI 分包）。

### 签名

仓库**不包含**任何签名文件。没有配置密钥时，release 包会自动用 debug 密钥签名，
产物可直接安装（不适合上架）。要出正式包，自己生成一份并让 Gradle 读到：

```bash
keytool -genkeypair -keystore rain.jks -alias rain \
  -keyalg RSA -keysize 2048 -validity 10950
cp keystore.properties.example keystore.properties
# 按需修改 keystore.properties 里的路径与口令
```

`keystore.properties` 与 `*.jks` 已在 `.gitignore` 中，不会被提交。
CI 环境可用环境变量代替：`LUMEN_STORE_FILE` / `LUMEN_STORE_PASSWORD` /
`LUMEN_KEY_ALIAS` / `LUMEN_KEY_PASSWORD`。

主要依赖版本见 `gradle/libs.versions.toml`：AGP 8.7.3 / Kotlin 2.0.21 /
Compose BOM 2024.12.01 / Media3 1.6.1 / Jellyfin FFmpeg 1.6.1+1。

### 自动构建（可选）

[`docs/ci-android.yml`](docs/ci-android.yml) 是现成的 GitHub Actions 配置：
每次 push / PR 构建 release APK 并上传 `rain-apk` artifact，打 `v*` 标签
会自动创建 Release 并附上全部 APK，也支持在 Actions 页面手动触发。

启用方式：在网页端新建文件 `.github/workflows/android.yml`，
把 `docs/ci-android.yml` 的内容整段粘进去即可。

> 之所以没有直接放在 `.github/workflows/` 下，是因为推送工作流文件需要
> Personal Access Token 具备 `workflow` 权限；用网页创建一次就永久生效。

---

## 8. 已知限制 / 后续可做

- 未实现弹幕（参考项目里 Flow / ChaiChai 用弹弹play 协议，可后续接入）
- 未实现 Emby Quick Connect / 扫码登录（TV 上输入密码仍靠软键盘）
- 未实现局域网 UDP 自动发现（Emby 7359 端口广播）
- 飞牛的直播（IPTV）条目可列出但未做专门的频道 UI
- 未做音频直通能力的「逐格式强制开关」（目前是全局开关 + 自动协商）

---

## 9. 许可

仅供个人学习与自用。Emby、Jellyfin、飞牛影视 均为其各自权利人的商标，
本项目与其官方无关联。
