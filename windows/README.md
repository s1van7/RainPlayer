# RainPlayer for Windows

Emby / Jellyfin / 飞牛影视（fnOS）的 Windows 桌面播放器，与 [Android 版](https://github.com/s1van7/RainPlayer) 共用同一套协议实现。

基于 **.NET 8 + WPF + libmpv**，直链播放不转码，4K/8K、HDR10/HLG、杜比视界、DTS/TrueHD 直通都由 mpv 处理。

---

## 功能

| 分类 | 说明 |
| --- | --- |
| 后端 | Emby / Jellyfin 走官方 REST；飞牛影视走原生 `/v/api/v1`，含 Authx 签名 |
| 播放 | libmpv 内核，`gpu-next` + D3D11，硬件解码 4K/8K |
| HDR | `target-colorspace-hint` 直通，配合 Windows HDR 开关输出 HDR10 / HLG |
| 杜比 | 杜比视界片源按 mpv 能力解码；杜比全景声（EAC3/TrueHD）可原码直通 |
| 音频直通 | AC3 / EAC3 / DTS / DTS-HD / TrueHD 交给电视或功放，WASAPI 独占输出 |
| 字幕 | 内嵌 SRT/ASS/SSA/PGS 自动加载；外挂字幕可从服务器下载（飞牛需路径签名） |
| 浏览 | 首页横排推荐、媒体库网格、搜索、剧集季/集切换 |
| 播放控制 | 进度记忆、自动连播、音轨/字幕切换、全屏、键盘快捷键 |
| 进度同步 | 上报到服务器的播放记录，多端接续观看 |

## 下载

到 [Releases](https://github.com/s1van7/RainPlayer-Windows/releases) 下载：

| 文件 | 说明 |
| --- | --- |
| `RainPlayer-<版本>-win-x64.zip` | 绿色版，解压即用，不需要装 .NET 运行时 |
| `RainPlayer-<版本>-win-x64-setup.exe` | 安装包，创建开始菜单与桌面快捷方式 |

要求 **Windows 10 1809 及以上 / Windows 11**，64 位。

> 绿色版解压后目录里必须同时有 `RainPlayer.exe` 和 `libmpv-2.dll`，
> 缺了 dll 会提示"播放内核初始化失败"。

## 使用

1. 打开 RainPlayer，选择服务器类型（Emby / Jellyfin / 飞牛影视）
2. 填地址：Emby 和 Jellyfin 一般 `http://192.168.1.10:8096`，飞牛填 `http://192.168.1.10:5666`
3. 输入账号密码登录
4. 首页或媒体库里点开条目，点「播放」

### 快捷键

| 按键 | 作用 |
| --- | --- |
| `空格` | 播放 / 暂停 |
| `←` / `→` | 后退 / 前进 10 秒 |
| `↑` / `↓` | 音量 ±5 |
| `F` | 全屏切换 |
| `Esc` | 退出全屏，再按关闭窗口 |
| `N` / `P` | 下一集 / 上一集 |

鼠标在画面上移动会唤出控制条，3 秒无操作自动收起；点击画面切换播放暂停，双击切换全屏。

### 画质相关

- **4K/8K**：默认硬件解码（D3D11VA）。如果卡顿，先在设置里确认「硬件解码」开着
- **HDR**：需要 Windows 设置 → 系统 → 显示 → 打开「使用 HDR」，否则 HDR 片源会被映射成 SDR
- **杜比视界**：Profile 5 需要显卡和显示器都支持 DV，不支持的设备由 mpv 转成 HDR10 或 SDR
- **音频直通**：设置里开着「音频直通」时，AC3/DTS/TrueHD 原码送到 HDMI；
  如果电视或功放不支持，会没有声音，此时关掉它让播放器软解成 PCM

## 构建

需要 **.NET 8 SDK** 和 **7-Zip**（下载 libmpv 用）。

```powershell
# 1. 下载播放内核（约 30 MB 压缩包，解压出 116 MB 的 dll）
powershell -ExecutionPolicy Bypass -File scripts\fetch-libmpv.ps1

# 2. 构建 + 打包
powershell -ExecutionPolicy Bypass -File scripts\package.ps1

# 只出绿色版，跳过安装包
powershell -ExecutionPolicy Bypass -File scripts\package.ps1 -SkipInstaller
```

产物在 `release\`。安装包需要 [Inno Setup 6](https://jrsoftware.org/isdl.php)：

```powershell
winget install JRSoftware.InnoSetup
```

单独调试运行：

```powershell
dotnet run --project src\RainPlayer.App
```

## 工程结构

```
src/
├── RainPlayer.Core/          协议层，不依赖 WPF
│   ├── Models/               数据模型
│   ├── Backends/             EmbyBackend / FnOsBackend / 工厂
│   ├── Json/                 System.Text.Json 容错读取
│   ├── Http/                 HttpClient 封装
│   └── Settings/             配置读写（%APPDATA%\RainPlayer）
├── RainPlayer.Player/        libmpv 封装
│   ├── MpvNative.cs          P/Invoke 声明
│   ├── MpvPlayer.cs          播放控制、事件循环、轨道查询
│   └── MpvVideoHost.cs       HwndHost 子窗口（--wid 输出）
└── RainPlayer.App/           WPF 界面
    ├── Views/                登录 / 主窗口 / 播放器 / 设置
    ├── Controls/             海报卡片
    ├── Services/             带鉴权的海报加载
    └── Themes/               深色主题
```

### 两个实现上的坑

**mpv 的视频输出是独立 HWND**。用 `HwndHost` 开子窗口给 mpv 的 `--wid`，
好处是 mpv 直接渲染、零拷贝；代价是 WPF 的「airspace」问题——
同层任何 WPF 控件都会被视频盖住。所以播放控制条和标题栏放在 `Popup` 里
（Popup 是独立窗口，可以浮在视频之上）。

**飞牛的海报和字幕要鉴权**。WPF 的 `BitmapImage` 不支持自定义请求头，
所以海报走 `HttpClient` 下载字节再解码（`ImageLoader`）；
外挂字幕同样先下载到临时目录，再让 mpv 加载本地文件。

## 已知限制

- 杜比视界 Profile 5 需要设备硬解，纯软件解码会偏色
- 8K 依赖显卡解码能力，老机器只能转码
- TrueHD / DTS-HD 直通需要 HDMI 链路上的电视或功放支持
- 飞牛影视没有公开 API，按 Web 客户端协议逆向实现，固件升级后字段可能要调
- 未做剧集以外的聚合页面（如按类型筛选、演员页）

## 许可

MIT，见 [LICENSE](LICENSE)。
