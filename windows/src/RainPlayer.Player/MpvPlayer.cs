using System.Runtime.InteropServices;
using RainPlayer.Core.Models;

namespace RainPlayer.Player;

public sealed class PlayerOptions
{
    public bool HardwareDecoding { get; set; } = true;
    public bool AudioPassthrough { get; set; } = true;
    public bool HdrPassthrough { get; set; } = true;
    public int Volume { get; set; } = 80;
}

/// <summary>mpv 实际识别到的一条轨道（用于音轨/字幕菜单）。</summary>
public sealed class MpvTrack
{
    public long Id { get; set; }
    public string Type { get; set; } = "";
    public string? Title { get; set; }
    public string? Language { get; set; }
    public string? Codec { get; set; }
    public int? Channels { get; set; }
    public bool Selected { get; set; }
    public bool External { get; set; }

    public string DisplayLabel
    {
        get
        {
            var parts = new List<string>();
            if (!string.IsNullOrWhiteSpace(Title)) parts.Add(Title!);
            else if (!string.IsNullOrWhiteSpace(Language)) parts.Add(Language!.ToUpperInvariant());
            else parts.Add(Type == "audio" ? "音轨" : "字幕");

            if (!string.IsNullOrWhiteSpace(Codec)) parts.Add(Codec!.ToUpperInvariant());
            if (Channels is > 0) parts.Add($"{Channels}ch");
            if (External) parts.Add("外挂");
            return string.Join(" · ", parts);
        }
    }
}

/// <summary>
/// libmpv 播放器封装。
///
/// 采用 --wid 把视频渲染到调用方提供的子窗口句柄，音频与字幕解码全部交给 mpv：
/// 这样 4K/8K、HDR10/HLG、杜比视界、DTS/TrueHD 直通都由 mpv 处理，
/// 不受 Media Foundation 编解码器限制。
/// </summary>
public sealed class MpvPlayer : IDisposable
{
    private IntPtr _ctx;
    private readonly IntPtr _hwnd;
    private readonly PlayerOptions _options;
    private Thread? _eventThread;
    private volatile bool _running;
    private readonly object _lock = new();
    private bool _disposed;

    public event Action? FileLoaded;
    public event Action<string?>? PlaybackEnded;
    public event Action<string>? LogReceived;

    public bool IsInitialized => _ctx != IntPtr.Zero;

    public MpvPlayer(IntPtr hwnd, PlayerOptions options)
    {
        _hwnd = hwnd;
        _options = options;
        Initialize();
    }

    private void Initialize()
    {
        _ctx = MpvNative.mpv_create();
        if (_ctx == IntPtr.Zero)
            throw new InvalidOperationException("libmpv-2.dll 加载失败，请确认它与 RainPlayer.exe 在同一目录。");

        // --wid 必须在 mpv_initialize 之前设置
        SetOption("wid", _hwnd.ToInt64().ToString());
        SetOption("vo", "gpu-next");
        SetOption("gpu-api", "d3d11");
        SetOption("gpu-context", "d3d11");
        SetOption("hwdec", _options.HardwareDecoding ? "auto-safe" : "no");
        SetOption("keep-open", "no");
        SetOption("idle", "yes");
        SetOption("osc", "no");
        SetOption("osd-level", "0");
        SetOption("input-default-bindings", "no");
        SetOption("input-vo-keyboard", "no");
        SetOption("input-cursor", "no");
        SetOption("cursor-autohide", "no");
        SetOption("terminal", "no");
        SetOption("config", "no");
        SetOption("sub-auto", "fuzzy");
        SetOption("sub-font-size", "46");
        SetOption("sub-border-size", "2.5");
        SetOption("sub-shadow-offset", "1.5");
        SetOption("sub-ass-override", "scale");
        SetOption("cache", "yes");
        SetOption("demuxer-max-bytes", "512MiB");
        SetOption("demuxer-readahead-secs", "30");
        SetOption("video-sync", "display-resample");
        SetOption("interpolation", "no");
        SetOption("screenshot-format", "png");
        SetOption("volume-max", "100");

        if (_options.HdrPassthrough)
        {
            // 把片源色彩空间原样交给系统，配合 Windows HDR 开关输出 HDR
            SetOption("target-colorspace-hint", "yes");
            SetOption("hdr-compute-peak", "yes");
            SetOption("tone-mapping", "auto");
        }
        else
        {
            SetOption("target-colorspace-hint", "no");
            SetOption("tone-mapping", "bt.2446a");
        }

        if (_options.AudioPassthrough)
        {
            // 原码直通给电视/功放；不支持的格式由 mpv 自行软解
            SetOption("audio-spdif", "ac3,eac3,dts,dts-hd,truehd");
            SetOption("audio-exclusive", "yes");
            SetOption("audio-channels", "auto-safe");
        }
        else
        {
            SetOption("audio-spdif", "");
            SetOption("audio-exclusive", "no");
        }

        var error = MpvNative.mpv_initialize(_ctx);
        if (error < 0)
        {
            var message = MpvNative.ErrorText(error);
            MpvNative.mpv_terminate_destroy(_ctx);
            _ctx = IntPtr.Zero;
            throw new InvalidOperationException($"mpv 初始化失败：{message}");
        }

        MpvNative.mpv_request_log_messages(_ctx, "warn");
        SetProperty("volume", _options.Volume.ToString());

        _running = true;
        _eventThread = new Thread(EventLoop)
        {
            IsBackground = true,
            Name = "mpv-events",
        };
        _eventThread.Start();
    }

    private void SetOption(string name, string value)
    {
        var error = MpvNative.mpv_set_option_string(_ctx, name, value);
        if (error < 0) LogReceived?.Invoke($"选项 {name}={value} 设置失败：{MpvNative.ErrorText(error)}");
    }

    // ---------------------------------------------------------- 事件循环

    private void EventLoop()
    {
        while (_running)
        {
            MpvNative.MpvEvent evt;
            try
            {
                var ptr = MpvNative.mpv_wait_event(_ctx, 0.2);
                if (ptr == IntPtr.Zero) continue;
                evt = Marshal.PtrToStructure<MpvNative.MpvEvent>(ptr);
            }
            catch
            {
                break;
            }

            switch (evt.EventId)
            {
                case MpvNative.EventNone:
                    break;

                case MpvNative.EventShutdown:
                    _running = false;
                    break;

                case MpvNative.EventFileLoaded:
                    FileLoaded?.Invoke();
                    break;

                case MpvNative.EventEndFile:
                    HandleEndFile(evt);
                    break;

                case MpvNative.EventLogMessage:
                    break;
            }
        }
    }

    private void HandleEndFile(MpvNative.MpvEvent evt)
    {
        var reason = -1;
        if (evt.Data != IntPtr.Zero)
        {
            try
            {
                var endFile = Marshal.PtrToStructure<MpvNative.MpvEventEndFile>(evt.Data);
                reason = endFile.Reason;
            }
            catch
            {
                // 结构体解析失败按未知原因处理
            }
        }

        // 0 = EOF（正常播完），其它为停止/错误
        PlaybackEnded?.Invoke(reason == 0 ? null : $"播放结束（原因 {reason}）");
    }

    // ---------------------------------------------------------- 播放控制

    public void Load(PlaybackPlan plan, long? startOverrideMs = null)
    {
        if (!IsInitialized) return;

        var headers = string.Join(",", plan.Headers
            .Where(pair => !string.IsNullOrEmpty(pair.Value))
            .Select(pair => $"{pair.Key}: {pair.Value}"));

        if (!string.IsNullOrEmpty(headers))
            SetProperty("http-header-fields", headers);

        var startMs = startOverrideMs ?? plan.StartPositionMs;
        if (startMs > 0)
            SetProperty("start", (startMs / 1000.0).ToString("0.###", System.Globalization.CultureInfo.InvariantCulture));

        SetProperty("pause", "no");
        MpvNative.Command(_ctx, "loadfile", plan.Url, "replace");
    }

    public void Play()
    {
        if (IsInitialized) SetProperty("pause", "no");
    }

    public void Pause()
    {
        if (IsInitialized) SetProperty("pause", "yes");
    }

    public void TogglePause()
    {
        if (!IsInitialized) return;
        var paused = GetPropertyFlag("pause");
        SetProperty("pause", paused ? "no" : "yes");
    }

    public bool IsPaused => IsInitialized && GetPropertyFlag("pause");

    public void Stop()
    {
        if (IsInitialized) MpvNative.Command(_ctx, "stop");
    }

    public void SeekTo(long positionMs)
    {
        if (!IsInitialized) return;
        var seconds = Math.Max(0, positionMs) / 1000.0;
        MpvNative.Command(_ctx, "seek", seconds.ToString("0.###", System.Globalization.CultureInfo.InvariantCulture), "absolute");
    }

    public void SeekRelative(long deltaMs)
    {
        if (!IsInitialized) return;
        var seconds = deltaMs / 1000.0;
        MpvNative.Command(_ctx, "seek", seconds.ToString("0.###", System.Globalization.CultureInfo.InvariantCulture), "relative");
    }

    public void SetVolume(int volume)
    {
        if (IsInitialized) SetProperty("volume", Math.Clamp(volume, 0, 100).ToString());
    }

    public int GetVolume() => IsInitialized ? (int)GetPropertyDouble("volume") : 0;

    public void SetAudioTrack(long id)
    {
        if (IsInitialized) SetProperty("aid", id <= 0 ? "no" : id.ToString());
    }

    public void SetSubtitleTrack(long id)
    {
        if (IsInitialized) SetProperty("sid", id <= 0 ? "no" : id.ToString());
    }

    public void AddSubtitleFile(string path)
    {
        if (IsInitialized) MpvNative.Command(_ctx, "sub-add", path, "select");
    }

    // ---------------------------------------------------------- 状态读取

    public long GetPositionMs()
    {
        if (!IsInitialized) return 0;
        var seconds = GetPropertyDouble("time-pos");
        return double.IsNaN(seconds) || seconds < 0 ? 0 : (long)(seconds * 1000);
    }

    public long GetDurationMs()
    {
        if (!IsInitialized) return 0;
        var seconds = GetPropertyDouble("duration");
        return double.IsNaN(seconds) || seconds <= 0 ? 0 : (long)(seconds * 1000);
    }

    public double GetCachePercent() => IsInitialized ? GetPropertyDouble("cache-buffering-state") : 0;

    public bool IsBuffering => IsInitialized && GetPropertyFlag("paused-for-cache");

    /// <summary>读取 mpv 当前识别到的轨道列表（比服务器给的清单更贴近实际可播情况）。</summary>
    public List<MpvTrack> GetTracks()
    {
        var tracks = new List<MpvTrack>();
        if (!IsInitialized) return tracks;

        var count = (int)GetPropertyDouble("track-list/count");
        for (var i = 0; i < count; i++)
        {
            var type = GetPropertyString($"track-list/{i}/type");
            if (string.IsNullOrEmpty(type)) continue;

            tracks.Add(new MpvTrack
            {
                Id = (long)GetPropertyDouble($"track-list/{i}/id"),
                Type = type!,
                Title = GetPropertyString($"track-list/{i}/title"),
                Language = GetPropertyString($"track-list/{i}/lang"),
                Codec = GetPropertyString($"track-list/{i}/codec"),
                Channels = (int)GetPropertyDouble($"track-list/{i}/demux-channel-count"),
                Selected = GetPropertyFlag($"track-list/{i}/selected"),
                External = GetPropertyFlag($"track-list/{i}/external"),
            });
        }
        return tracks;
    }

    /// <summary>当前视频分辨率，用于判断是否真的输出 4K/8K。</summary>
    public (int Width, int Height) GetVideoSize()
    {
        if (!IsInitialized) return (0, 0);
        return ((int)GetPropertyDouble("width"), (int)GetPropertyDouble("height"));
    }

    /// <summary>当前解码方式（hwdec-current），排查 4K 卡顿时有用。</summary>
    public string GetHardwareDecoder() => IsInitialized ? GetPropertyString("hwdec-current") ?? "no" : "no";

    public string? GetPropertyString(string name)
    {
        if (!IsInitialized) return null;
        var ptr = MpvNative.mpv_get_property_string(_ctx, name);
        if (ptr == IntPtr.Zero) return null;
        try
        {
            return Marshal.PtrToStringUTF8(ptr);
        }
        finally
        {
            MpvNative.mpv_free(ptr);
        }
    }

    public double GetPropertyDouble(string name)
    {
        if (!IsInitialized) return double.NaN;
        var value = 0.0;
        var error = MpvNative.mpv_get_property(_ctx, name, MpvNative.FormatDouble, ref value);
        if (error >= 0) return value;

        // 有些属性是 int64/flag，退一步再试
        var longValue = 0L;
        error = MpvNative.mpv_get_property(_ctx, name, MpvNative.FormatInt64, ref longValue);
        return error >= 0 ? longValue : double.NaN;
    }

    public bool GetPropertyFlag(string name) => GetPropertyDouble(name) is var value && !double.IsNaN(value) && value != 0;

    public void SetProperty(string name, string value)
    {
        if (IsInitialized) MpvNative.mpv_set_property_string(_ctx, name, value);
    }

    // ---------------------------------------------------------- 释放

    public void Dispose()
    {
        lock (_lock)
        {
            if (_disposed) return;
            _disposed = true;

            _running = false;
            if (_ctx != IntPtr.Zero)
            {
                try
                {
                    MpvNative.mpv_terminate_destroy(_ctx);
                }
                catch
                {
                    // 关闭阶段忽略
                }
                _ctx = IntPtr.Zero;
            }

            _eventThread?.Join(TimeSpan.FromSeconds(1));
            _eventThread = null;
        }
    }
}
