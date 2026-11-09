using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Threading;
using RainPlayer.Core.Backends;
using RainPlayer.Core.Models;
using RainPlayer.Player;

namespace RainPlayer.App.Views;

public partial class PlayerWindow : Window
{
    private readonly IMediaBackend _backend;
    private readonly List<Episode> _queue;
    private readonly DispatcherTimer _tickTimer;
    private readonly DispatcherTimer _hideTimer;

    private MpvPlayer? _player;
    private PlaybackPlan? _plan;
    private string _currentItemId;
    private string _title;
    private bool _mpvReady;
    private bool _seeking;
    private bool _closing;
    private bool _ended;
    private DateTime _lastReport = DateTime.MinValue;
    private readonly List<string> _tempSubtitles = new();
    private readonly string? _sourceId;
    private readonly int? _audioIndex;
    private readonly int? _subtitleIndex;
    private readonly bool _burnSubtitle;
    private readonly long _initialStartMs;
    private bool _stopReported;

    public PlayerWindow(
        IMediaBackend backend,
        string itemId,
        string title,
        List<Episode>? queue = null,
        string? sourceId = null,
        int? audioIndex = null,
        int? subtitleIndex = null,
        bool burnSubtitle = false,
        long initialStartMs = 0)
    {
        InitializeComponent();

        _backend = backend;
        _currentItemId = itemId;
        _title = title;
        _queue = queue ?? new List<Episode>();
        _sourceId = sourceId;
        _audioIndex = audioIndex;
        _subtitleIndex = subtitleIndex;
        _burnSubtitle = burnSubtitle;
        _initialStartMs = initialStartMs;

        TitleText.Text = title;
        SubtitleText.Text = "";

        VideoHost.HandleReady += _ => TryInitializeMpv();
        VideoHost.VideoClicked += TogglePlayPause;
        VideoHost.VideoDoubleClicked += ToggleFullscreen;
        VideoHost.MouseMoved += ShowControls;

        _tickTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        _tickTimer.Tick += OnTick;

        _hideTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
        _hideTimer.Tick += (_, _) =>
        {
            _hideTimer.Stop();
            if (_player is { IsPaused: false }) HideControls();
        };

        Loaded += async (_, _) =>
        {
            TryInitializeMpv();
            UpdatePopupLayout();
            ShowControls();
            await LoadCurrentAsync(_initialStartMs);
        };

        SizeChanged += (_, _) => UpdatePopupLayout();
        MouseMove += (_, _) => ShowControls();
        Closing += OnClosing;
    }

    // ============================================================ 初始化

    private void TryInitializeMpv()
    {
        if (_mpvReady || VideoHost.VideoHandle == IntPtr.Zero) return;

        var settings = ((App)Application.Current).Settings;
        try
        {
            _player = new MpvPlayer(VideoHost.VideoHandle, new PlayerOptions
            {
                HardwareDecoding = settings.HardwareDecoding,
                AudioPassthrough = settings.AudioPassthrough,
                HdrPassthrough = settings.HdrPassthrough,
                Volume = settings.Volume,
            });

            _player.FileLoaded += OnFileLoaded;
            _player.PlaybackEnded += OnPlaybackEnded;
            _mpvReady = true;

            VolumeSlider.Value = settings.Volume;
        }
        catch (Exception ex)
        {
            ShowError($"播放内核初始化失败：{ex.Message}\n\n请确认 libmpv-2.dll 与 RainPlayer.exe 在同一目录。");
        }
    }

    // ============================================================ 播放流程

    private async Task LoadCurrentAsync(long startOverrideMs = 0, bool forceTranscode = false)
    {
        if (!_mpvReady || _player == null) return;

        _ended = false;
        ErrorLayer.Visibility = Visibility.Collapsed;
        StatusLayer.Visibility = Visibility.Visible;
        StatusText.Text = "正在获取播放信息…";

        try
        {
            var plan = await _backend.GetPlaybackAsync(
                _currentItemId, startOverrideMs, forceTranscode,
                _sourceId, _audioIndex, _subtitleIndex, _burnSubtitle);
            _plan = plan;

            TitleText.Text = plan.Title;
            _title = plan.Title;
            SubtitleText.Text = BuildSubtitleLine(plan);
            UpdateTechText();

            StatusText.Text = "正在缓冲…";
            _player.Load(plan, startOverrideMs > 0 ? startOverrideMs : null);

            _ = _backend.ReportStartAsync(plan, plan.StartPositionMs);
            _tickTimer.Start();
        }
        catch (Exception ex)
        {
            ShowError($"获取播放地址失败：{ex.Message}");
        }
    }

    /// <summary>
    /// 结束播放时的上报：先落位置（Progress），再结束会话（Stopped），
    /// 最后用 UserData 接口兜底写一次。只执行一次，且位置为 0 时用已知进度代替，
    /// 避免把「继续观看」的记录清空。
    /// </summary>
    private async Task ReportStopSafeAsync(long position)
    {
        var plan = _plan;
        if (plan == null || _stopReported) return;
        _stopReported = true;

        var pos = position > 0 ? position : plan.StartPositionMs;
        if (pos <= 0) pos = 0;
        var completed = plan.DurationMs > 0 && pos >= plan.DurationMs * 0.95;

        try
        {
            await _backend.ReportProgressAsync(plan, pos, true);
            await _backend.ReportStopAsync(plan, pos);
            await _backend.PersistPositionAsync(plan, pos, completed);
        }
        catch
        {
            // 上报失败不影响退出
        }
    }

    /// <summary>把详情页选中的音轨/字幕应用到 mpv（mpv 的轨道 id 从 1 开始，按文件顺序）。</summary>
    private void ApplySelectedTracks()
    {
        var plan = _plan;
        var player = _player;
        if (plan == null || player == null) return;

        if (_audioIndex != null)
        {
            var ordinal = plan.AudioTracks.FindIndex(t => t.Index == _audioIndex.Value);
            if (ordinal >= 0) player.SetAudioTrack(ordinal + 1);
        }
        if (_subtitleIndex != null)
        {
            var ordinal = plan.SubtitleTracks.FindIndex(t => t.Index == _subtitleIndex.Value);
            if (ordinal >= 0) player.SetSubtitleTrack(ordinal + 1);
        }
        else
        {
            player.SetSubtitleTrack(0);
        }
    }

    private void OnFileLoaded()
    {
        Dispatcher.BeginInvoke(() =>
        {
            StatusLayer.Visibility = Visibility.Collapsed;
            _player?.SetVolume((int)VolumeSlider.Value);
            ApplySelectedTracks();
            UpdateTrackButtons();
            ShowControls();
        });
    }

    private void OnPlaybackEnded(string? message)
    {
        Dispatcher.BeginInvoke(async () =>
        {
            if (_closing || _ended) return;
            _ended = true;

            await ReportStopSafeAsync(_plan?.DurationMs ?? 0);

            var settings = ((App)Application.Current).Settings;
            if (settings.AutoPlayNext && await PlayNextAsync()) return;

            Close();
        });
    }

    /// <summary>播放队列里的下一集；没有下一集返回 false。</summary>
    private async Task<bool> PlayNextAsync()
    {
        if (_queue.Count == 0) return false;

        var index = _queue.FindIndex(e => e.Id == _currentItemId);
        if (index < 0 || index + 1 >= _queue.Count) return false;

        var next = _queue[index + 1];
        _currentItemId = next.Id;
        _ended = false;
        TitleText.Text = next.Title;

        await LoadCurrentAsync();
        return true;
    }

    private async Task PlayPreviousAsync()
    {
        if (_queue.Count == 0) return;
        var index = _queue.FindIndex(e => e.Id == _currentItemId);
        if (index <= 0) return;

        _currentItemId = _queue[index - 1].Id;
        _ended = false;
        await LoadCurrentAsync();
    }

    // ============================================================ 定时刷新

    private void OnTick(object? sender, EventArgs e)
    {
        if (_player == null) return;

        var position = _player.GetPositionMs();
        var duration = _player.GetDurationMs();
        if (duration <= 0 && _plan != null) duration = _plan.DurationMs;

        if (!_seeking && duration > 0)
        {
            SeekSlider.Maximum = duration;
            SeekSlider.Value = Math.Clamp(position, 0, duration);
        }

        PositionText.Text = FormatTime(position);
        DurationText.Text = FormatTime(duration);

        PlayButton.Content = _player.IsPaused ? "▶" : "❚❚";

        if (_player.IsBuffering) StatusLayer.Visibility = Visibility.Visible;
        else if (StatusLayer.Visibility == Visibility.Visible && position > 0) StatusLayer.Visibility = Visibility.Collapsed;

        // 每 10 秒上报一次进度
        if (_plan != null && DateTime.UtcNow - _lastReport > TimeSpan.FromSeconds(10))
        {
            _lastReport = DateTime.UtcNow;
            _ = _backend.ReportProgressAsync(_plan, position, _player.IsPaused);
        }
    }

    // ============================================================ 控制条交互

    private void ShowControls()
    {
        TopBar.IsOpen = true;
        BottomBar.IsOpen = true;
        UpdatePopupLayout();

        _hideTimer.Stop();
        _hideTimer.Start();
    }

    private void HideControls()
    {
        if (AudioButton.ContextMenu?.IsOpen == true) return;
        if (SubtitleButton.ContextMenu?.IsOpen == true) return;

        TopBar.IsOpen = false;
        BottomBar.IsOpen = false;
    }

    private void UpdatePopupLayout()
    {
        var width = RootGrid.ActualWidth;
        var height = RootGrid.ActualHeight;
        if (width <= 0 || height <= 0) return;

        TopBarBorder.Width = width;
        BottomBarBorder.Width = width;

        BottomBar.HorizontalOffset = 0;
        BottomBar.VerticalOffset = Math.Max(0, height - BottomBarBorder.ActualHeight);

        if (BottomBarBorder.ActualHeight <= 0)
            BottomBar.VerticalOffset = Math.Max(0, height - 118);
    }

    private void OnPlayPauseClick(object sender, RoutedEventArgs e) => TogglePlayPause();

    private void TogglePlayPause()
    {
        if (_player == null) return;
        _player.TogglePause();
        ShowControls();
        PlayButton.Content = _player.IsPaused ? "▶" : "❚❚";
    }

    private void OnBack10Click(object sender, RoutedEventArgs e) => SeekRelative(-10_000);

    private void OnForward10Click(object sender, RoutedEventArgs e) => SeekRelative(10_000);

    private void SeekRelative(long deltaMs)
    {
        _player?.SeekRelative(deltaMs);
        ShowControls();
    }

    private void OnSeekStart(object sender, DragStartedEventArgs e) => _seeking = true;

    private void OnSeekEnd(object sender, DragCompletedEventArgs e)
    {
        _seeking = false;
        _player?.SeekTo((long)SeekSlider.Value);
        ShowControls();
    }

    private void OnSeekValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_seeking) PositionText.Text = FormatTime((long)e.NewValue);
    }

    private void OnVolumeChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_player == null) return;
        var volume = (int)e.NewValue;
        _player.SetVolume(volume);
        ((App)Application.Current).Settings.Volume = volume;
    }

    private void OnFullscreenClick(object sender, RoutedEventArgs e) => ToggleFullscreen();

    private void ToggleFullscreen()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
        ShowControls();
    }

    private void OnCloseClick(object sender, RoutedEventArgs e) => Close();

    private void OnRetryClick(object sender, RoutedEventArgs e)
    {
        ErrorLayer.Visibility = Visibility.Collapsed;
        _ = LoadCurrentAsync(_player?.GetPositionMs() ?? 0);
    }

    private void OnTranscodeClick(object sender, RoutedEventArgs e)
    {
        ErrorLayer.Visibility = Visibility.Collapsed;
        _ = LoadCurrentAsync(_player?.GetPositionMs() ?? 0, forceTranscode: true);
    }

    // ============================================================ 音轨 / 字幕

    private void UpdateTrackButtons()
    {
        var tracks = _player?.GetTracks() ?? new List<MpvTrack>();
        var audioCount = tracks.Count(t => t.Type == "audio");
        var subCount = tracks.Count(t => t.Type == "subtitle");

        AudioButton.Content = audioCount > 1 ? $"音轨 {audioCount}" : "音轨";
        SubtitleButton.Content = subCount > 0 ? $"字幕 {subCount}" : "字幕";
    }

    private void OnAudioClick(object sender, RoutedEventArgs e)
    {
        var menu = new ContextMenu { PlacementTarget = AudioButton, Placement = PlacementMode.Top };
        var tracks = (_player?.GetTracks() ?? new List<MpvTrack>()).Where(t => t.Type == "audio").ToList();

        if (tracks.Count == 0)
        {
            menu.Items.Add(new MenuItem { Header = "没有可切换的音轨", IsEnabled = false });
        }
        else
        {
            foreach (var track in tracks)
            {
                var item = new MenuItem
                {
                    Header = (track.Selected ? "● " : "    ") + track.DisplayLabel,
                    Tag = track.Id,
                };
                item.Click += (_, _) =>
                {
                    _player?.SetAudioTrack(track.Id);
                    ShowControls();
                };
                menu.Items.Add(item);
            }
        }

        menu.IsOpen = true;
    }

    private void OnSubtitleClick(object sender, RoutedEventArgs e)
    {
        var menu = new ContextMenu { PlacementTarget = SubtitleButton, Placement = PlacementMode.Top };
        var tracks = (_player?.GetTracks() ?? new List<MpvTrack>()).Where(t => t.Type == "subtitle").ToList();

        var off = new MenuItem { Header = "关闭字幕" };
        off.Click += (_, _) => _player?.SetSubtitleTrack(-1);
        menu.Items.Add(off);

        foreach (var track in tracks)
        {
            var item = new MenuItem
            {
                Header = (track.Selected ? "● " : "    ") + track.DisplayLabel,
            };
            item.Click += (_, _) => _player?.SetSubtitleTrack(track.Id);
            menu.Items.Add(item);
        }

        // 服务器上的外挂字幕（飞牛需要先下载再加载）
        var external = _plan?.SubtitleTracks.Where(s => s.IsExternal && s.Url != null).ToList() ?? new List<SubtitleTrack>();
        if (external.Count > 0)
        {
            menu.Items.Add(new Separator());
            foreach (var subtitle in external)
            {
                var item = new MenuItem { Header = $"加载：{subtitle.Label}" };
                item.Click += async (_, _) => await LoadExternalSubtitleAsync(subtitle);
                menu.Items.Add(item);
            }
        }

        menu.Items.Add(new Separator());
        var local = new MenuItem { Header = "打开本地字幕文件…" };
        local.Click += (_, _) => OpenLocalSubtitle();
        menu.Items.Add(local);

        menu.IsOpen = true;
    }

    private async Task LoadExternalSubtitleAsync(SubtitleTrack track)
    {
        if (_player == null || track.Url == null) return;

        StatusLayer.Visibility = Visibility.Visible;
        StatusText.Text = "正在下载字幕…";
        try
        {
            var extension = Path.GetExtension(new Uri(track.Url).AbsolutePath);
            if (string.IsNullOrEmpty(extension)) extension = ".srt";

            var path = Path.Combine(Path.GetTempPath(), "RainPlayer",
                $"sub_{Guid.NewGuid():N}{extension}");
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);

            var ok = await _backend.DownloadSubtitleAsync(track, path);
            if (ok && File.Exists(path))
            {
                _tempSubtitles.Add(path);
                _player.AddSubtitleFile(path);
            }
            else
            {
                MessageBox.Show(this, "字幕下载失败，可能是服务器鉴权或路径签名问题。",
                    "字幕", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "字幕加载失败", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally
        {
            StatusLayer.Visibility = Visibility.Collapsed;
        }
    }

    private void OpenLocalSubtitle()
    {
        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Title = "选择字幕文件",
            Filter = "字幕文件|*.srt;*.ass;*.ssa;*.sub;*.vtt|所有文件|*.*",
        };
        if (dialog.ShowDialog(this) != true) return;
        _player?.AddSubtitleFile(dialog.FileName);
    }

    // ============================================================ 键盘

    private void OnKeyDown(object sender, KeyEventArgs e)
    {
        switch (e.Key)
        {
            case Key.Space:
                TogglePlayPause();
                e.Handled = true;
                break;
            case Key.Left:
                SeekRelative(-10_000);
                e.Handled = true;
                break;
            case Key.Right:
                SeekRelative(10_000);
                e.Handled = true;
                break;
            case Key.Up:
                VolumeSlider.Value = Math.Min(100, VolumeSlider.Value + 5);
                ShowControls();
                e.Handled = true;
                break;
            case Key.Down:
                VolumeSlider.Value = Math.Max(0, VolumeSlider.Value - 5);
                ShowControls();
                e.Handled = true;
                break;
            case Key.F:
                ToggleFullscreen();
                e.Handled = true;
                break;
            case Key.Escape:
                if (WindowState == WindowState.Maximized)
                {
                    WindowState = WindowState.Normal;
                    ShowControls();
                }
                else
                {
                    Close();
                }
                e.Handled = true;
                break;
            case Key.N:
                _ = PlayNextAsync();
                e.Handled = true;
                break;
            case Key.P:
                _ = PlayPreviousAsync();
                e.Handled = true;
                break;
        }
    }

    // ============================================================ 收尾

    private void OnClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        _closing = true;
        _tickTimer.Stop();
        _hideTimer.Stop();

        var position = _player?.GetPositionMs() ?? 0;
        _ = ReportStopSafeAsync(position);

        ((App)Application.Current).Settings.Save();

        _player?.Dispose();
        _player = null;

        foreach (var path in _tempSubtitles)
        {
            try
            {
                if (File.Exists(path)) File.Delete(path);
            }
            catch
            {
                // 临时文件删不掉就留给系统清理
            }
        }
    }

    // ============================================================ 工具

    private void ShowError(string message)
    {
        StatusLayer.Visibility = Visibility.Collapsed;
        ErrorText.Text = message;
        ErrorLayer.Visibility = Visibility.Visible;
        TopBar.IsOpen = true;
        BottomBar.IsOpen = false;
    }

    private void UpdateTechText()
    {
        if (_plan == null) return;
        var parts = new List<string>();
        if (!string.IsNullOrWhiteSpace(_plan.DynamicRange)) parts.Add(_plan.DynamicRange!);
        if (!string.IsNullOrWhiteSpace(_plan.Container)) parts.Add(_plan.Container!.ToUpperInvariant());
        parts.Add(_plan.Direct ? "直链" : "转码");
        TechText.Text = string.Join(" · ", parts);
    }

    private static string BuildSubtitleLine(PlaybackPlan plan)
    {
        var parts = new List<string>();
        if (!string.IsNullOrWhiteSpace(plan.Subtitle)) parts.Add(plan.Subtitle!);
        if (!string.IsNullOrWhiteSpace(plan.DynamicRange)) parts.Add(plan.DynamicRange!);
        return string.Join(" · ", parts);
    }

    private static string FormatTime(long milliseconds)
    {
        if (milliseconds <= 0) return "00:00";
        var span = TimeSpan.FromMilliseconds(milliseconds);
        return span.TotalHours >= 1
            ? $"{(int)span.TotalHours}:{span.Minutes:00}:{span.Seconds:00}"
            : $"{span.Minutes:00}:{span.Seconds:00}";
    }
}
