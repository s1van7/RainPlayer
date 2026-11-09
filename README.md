# RainPlayer

[![Release](https://img.shields.io/github/v/release/s1van7/RainPlayer?label=release&color=blue)](https://github.com/s1van7/RainPlayer/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![minSdk](https://img.shields.io/badge/minSdk-21-brightgreen.svg)
![platform](https://img.shields.io/badge/platform-Android%20%7C%20Android%20TV%20%7C%20Windows-3ddc84.svg)

一个同时支持 **Emby / Jellyfin / 飞牛影视（fnOS）** 的播放器，提供 **Android / Android TV** 与 **Windows** 两个客户端，
共用同一套后端协议实现。
参照 [Flow](https://github.com/Flow-Media-Client/Flow-release) 的 TV 交互、
[CineIsle](https://github.com/nu1lkali/CineIsle) 的直链播放思路、
[ChaiChaiEmbyTV](https://github.com/dh374374/ChaiChaiEmbyTV) 的低版本兼容策略实现。

- **最低 Android 5.0（API 21）**，同一份 APK 同时适配手机 / 平板 / 电视盒子
- **Windows 10 1809+ / 11**，.NET 8 + WPF + libmpv，免装运行时（见 [windows/](windows/)）
- **播放内核**：Android 用 AndroidX Media3 (ExoPlayer 1.6) + Jellyfin FFmpeg 扩展；Windows 用 libmpv
- **直通优先**：默认直链播放，不转码，保住 4K/8K、HDR10/HLG、杜比视界与杜比全景声
- **双后端**：Emby 走官方 REST 协议；飞牛影视走其原生 `/v/api/v1` 协议（含 Authx 签名）

---

## 1. 功能

| 分类 | 能力 |
| --- | --- |
| 服务器 | Emby / Jellyfin / 飞牛影视；**服务器数量不限**；每台可配**多条线路**（自动故障切换）与**多个账号**；账号头像优先用服务器自带的，没有才用自定义图标（URL / emoji） |
| 浏览 | 首页（**整屏大图轮播** + 继续观看 + 各媒体库最新）、媒体库网格分页、**跨服务器聚合搜索** |
| 详情 | 大图 + 海报居中、评分与分级、时长/日期/画质、播放按钮带续播时间、**TMDB + 豆瓣 + 烂番茄评分**、演职人员、类似作品、**外站链接（豆瓣/TMDB/IMDb/Trakt）**、视频与音频分栏的媒体信息、版本 / 音轨 / 字幕选择、**单集详情页带选集条（可切季、原地换集）** |
| 界面 | **7 套主题**、**全站毛玻璃**、首页左上角服务器胶囊菜单（切换服务器 / 媒体库开关排序 / 服务器设置 / 添加服务器）、**悬浮底部导航（滚动自动隐藏）** |
| 播放 | 直链直通、HLS 转码兜底、断点续播、自动连播下一集、**播放页内选集**、倍速 0.5×–2× |
| 画面 | 4K/8K 输出、HDR10 / HLG / HDR10+ / 杜比视界、按片源自动切显示模式（分辨率 + 帧率） |
| 声音 | AC3 / EAC3 / DTS / DTS-HD / TrueHD 直通；不支持时 FFmpeg 软解为 PCM |
| 字幕 | 内嵌（SRT / ASS / SSA / PGS）+ 外挂字幕侧载、字幕轨道切换、关闭字幕 |
| 进度 | 播放开始/进度/停止回传服务器（Emby `Sessions/Playing*`，飞牛 `play/record`） |
| 交互 | 手机：Material 3 + 悬浮底部导航、**长按左右半屏直接快退/快进（无需先滑动）**；TV：D-pad 焦点导航、遥控器按键、6 秒自动隐藏控制条 |

---

## 2. 快速开始

### 安装

**Android 手机 / 平板**：

```bash
adb install -r rainplayer-<版本>-phone-universal.apk
```

**Android TV / 电视盒子**（独立安装包，与手机版可同时存在）：

```bash
adb install -r rainplayer-<版本>-tv-universal.apk
```

| 包名 | 说明 |
| --- | --- |
| `rainplayer-<版本>-phone-*.apk` | 手机版，包名 `com.rainplayer`，桌面图标启动 |
| `rainplayer-<版本>-tv-*.apk` | 电视版，包名 `com.rainplayer.tv`，只有遥控器界面与 Leanback 入口 |

两个包签名相同但包名不同，可以装在同一台设备上互不干扰。老电视请用 `armeabi-v7a`，主流盒子用 `arm64-v8a`，不确定就选 `universal`。

**Windows**：到 [Releases](../../releases) 下载

| 文件 | 说明 |
| --- | --- |
| `RainPlayer-<版本>-win-x64.zip` | 绿色版，解压即用，无需安装 .NET 运行时 |
| `RainPlayer-<版本>-win-x64-setup.exe` | 安装包，创建开始菜单与桌面快捷方式 |

解压后目录里必须同时有 `RainPlayer.exe` 和 `libmpv-2.dll`，缺 dll 会提示播放内核初始化失败。
源码与构建说明见 [windows/](windows/)。

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
app/src/main/java/com/rainplayer/
├── RainPlayerApp.kt                  Application + 极简服务定位器(Graph) + Coil 图片加载
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

### 4.4 多服务器 / 多线路 / 多账号

```
ServerConfig
├── lines[]   内网 / 外网 / 中转，各自一个 URL
├── users[]   同一台服务器上的多个登录账号
├── url / token / userId   当前生效值（由 activeLineId / activeUserId 折叠而来）
└── hiddenLibraries[] / libraryOrder[]   首页媒体库的可见性与顺序
```

- **线路故障切换**：`FailoverBackend` 包一层，只在「网络层错误」（连不上、超时、DNS 失败）
  时才换下一条线路重试；服务器明确返回的 401 / 404 不切换，因为换线路也解决不了。
  切换成功后新配置会落盘，下次启动直接走能通的那条。
- **聚合搜索**：对所有服务器并发搜索，结果标注来源。点开非当前服务器的结果会先切过去，
  避免拿错 token。
- **老配置迁移**：`ServerConfig.normalize()` 把旧版的单 `url` / 单 `token` 折叠成
  `lines` / `users`，幂等，启动时自动执行。

### 4.5 主题与毛玻璃

- Android：`ThemeState` 持有当前主题，`RainPlayerBg` 等历史顶层常量改成
  `@Composable` 属性 getter，180+ 处调用点不用改一行，颜色自动跟着主题走。
- Windows：`Theme.xaml` 里的画刷是 `SolidColorBrush` 实例，视图用 `StaticResource`
  引用同一个对象，运行时直接改它的 `Color` 就能全站变色，不用重建窗口。
- 毛玻璃：Android 用「半透明底 + 高光渐变 + 1dp 亮边」三层模拟（API 31 以下
  `Modifier.blur` 不生效，纯 draw 方案没有额外开销）；Windows 同样用半透明画刷 + 亮边。
- 配色基调不是纯黑：底色抬到 `#12`~`#21` 这一档，再叠一点主题色倾向，
  长时间看不闷，毛玻璃的高光与描边也才有东西可透。

### 4.6 手势与旋转

播放页支持手机端手势，全部可在设置页单独开关：

| 手势 | 作用 |
| --- | --- |
| 左半屏上下滑 | 调节画面亮度（只改本窗口亮度，不动系统设置） |
| 右半屏上下滑 | 调节媒体音量 |
| 长按右半屏 | 按设定倍速快进（2x / 3x / 4x 可选） |
| 长按左半屏 | 快退（连续回跳，不依赖负速率） |

长按与滑动共用同一块区域：左右各半屏。判定用 dp 而不是 px（32dp 容差），
按下 420ms 内没超过容差就进倍速，超过就转成亮度/音量滑动；一旦进了倍速，
后续手指移动不会再打断它。
| 点击画面 | 显示 / 隐藏控制条 |

旋转由 `OrientationEventListener` 跟随重力判定，忽略系统的旋转锁定，只切换
竖屏 / 横屏 / 反向横屏，不做倒置竖屏。电视盒子没有加速度计时自动跳过。

### 4.7 字幕

- 内嵌字幕由 Media3 解析（SRT / ASS / SSA / PGS 位图字幕）。
- 外挂字幕侧载为 `MediaItem.SubtitleConfiguration`：
  - Emby 直接给 `DeliveryUrl`（带 `api_key`）；
  - 飞牛需要签名，先把字幕下载到 `cache/subtitles/` 再以本地文件侧载。

### 4.8 失败兜底

播放出错时弹出对话框，可选「重试」或「转码播放」（改用服务器 HLS），
并在信息面板显示 `errorCodeName` 与可能原因（8K AV1 / DV Profile 7 等）。

### 4.9 评分来源

| 来源 | 接口 | 行为 |
| --- | --- | --- |
| TMDB | `api.themoviedb.org/3/search/{movie,tv}` | 需要用户自己在设置页填 v3 Key；留空则整块隐藏，不发请求 |
| 豆瓣 | `api.wmdb.tv/api/v1/movie/search`（第三方聚合） | 任何失败静默返回空，详情页不显示这一项，不弹错、不卡页面 |

拿到 TMDB id 后会再查一次 `/external_ids` 取 IMDb id，详情页的「链接」区块
据此生成豆瓣 / TMDB / IMDb / Trakt 四个跳转。拿不到就少显示几个，不影响评分。

两个源都空时不写缓存，避免用户刚改完 Key 还要等缓存过期；结果按
「标题 + 年份 + 是否剧集」做进程内 LRU（上限 200 条）。

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
./gradlew :app:assembleRelease      # 手机包 + 电视包（各含 ABI 拆分与 universal）
```

产物在 `app/build/outputs/apk/phone/release/` 与 `app/build/outputs/apk/tv/release/`，
每个 flavor 都包含 `universal` 通用包与各 ABI 分包。

### 签名

仓库**不包含**任何签名文件。没有配置密钥时，release 包会自动用 debug 密钥签名，
产物可直接安装（不适合上架）。要出正式包，自己生成一份并让 Gradle 读到：

```bash
keytool -genkeypair -keystore rainplayer.jks -alias rainplayer \
  -keyalg RSA -keysize 2048 -validity 10950
cp keystore.properties.example keystore.properties
# 按需修改 keystore.properties 里的路径与口令
```

`keystore.properties` 与 `*.jks` 已在 `.gitignore` 中，不会被提交。
CI 环境改用 Secrets 注入（见下节），脚本会生成临时的 `keystore.properties`。

主要依赖版本见 `gradle/libs.versions.toml`：AGP 8.7.3 / Kotlin 2.0.21 /
Compose BOM 2024.12.01 / Media3 1.6.1 / Jellyfin FFmpeg 1.6.1+1。

### 自动构建

仓库已配置 GitHub Actions（`.github/workflows/android.yml`，模板同步在
[`docs/ci-android.yml`](docs/ci-android.yml)）：每次 push / PR 构建 release APK
并上传 `rainplayer-apk` artifact，打 `v*` 标签自动创建 Release 并附上全部 APK，
也支持在 Actions 页面手动 `Run workflow`。

构建逻辑在 [`scripts/ci-build.sh`](scripts/ci-build.sh)，不用
`android-actions/setup-android` —— 它内部会执行 `sdkmanager tools`，而
cmdline-tools 16.0 起该包已移除，会直接失败。脚本改为复用 runner 预装的 SDK。

**发布签名**：配置以下四个 Secrets 后，CI 产物会使用正式密钥签名，与 Release
页的 APK 签名一致、可互相覆盖安装；四项全不配则回退 debug 签名，只配一部分会
直接报错（避免产出签名不一致的包）。

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 rainplayer.jks` 的完整输出，单行 |
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥口令 |

fork 本仓库后若不需要正式签名，保持四个 Secrets 均为空即可。

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
