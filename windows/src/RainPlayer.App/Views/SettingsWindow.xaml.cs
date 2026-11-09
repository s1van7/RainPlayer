using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using RainPlayer.App.Themes;
using RainPlayer.Core.Backends;
using RainPlayer.Core.Models;
using RainPlayer.Core.Settings;

namespace RainPlayer.App.Views;

public partial class SettingsWindow : Window
{
    private readonly AppSettings _settings;

    public SettingsWindow()
    {
        InitializeComponent();

        _settings = ((App)Application.Current).Settings;

        PassthroughBox.IsChecked = _settings.AudioPassthrough;
        HardwareBox.IsChecked = _settings.HardwareDecoding;
        HdrBox.IsChecked = _settings.HdrPassthrough;
        AutoNextBox.IsChecked = _settings.AutoPlayNext;
        VolumeSlider.Value = _settings.Volume;

        BuildThemePicker();
        GlassBox.IsChecked = _settings.GlassEffect;
        AggregateBox.IsChecked = _settings.AggregateSearch;
        DailyBox.IsChecked = _settings.DailyRecommend;
        DoubanBox.IsChecked = _settings.DoubanRating;
        TmdbKeyBox.Text = _settings.TmdbApiKey;

        // 主题与毛玻璃即时预览，点保存才算最终生效
        GlassBox.Checked += (_, _) => ThemeManager.Apply(_settings.ThemeKey, true);
        GlassBox.Unchecked += (_, _) => ThemeManager.Apply(_settings.ThemeKey, false);

        var version = Assembly.GetExecutingAssembly().GetName().Version;
        VersionText.Text = version == null ? "1.0.0" : $"{version.Major}.{version.Minor}.{version.Build}";

        var backend = ((App)Application.Current).Backend;
        ServerInfoText.Text = backend == null
            ? "未连接"
            : $"{backend.Config.Name} · {backend.Config.Kind.DisplayName()} · {backend.Config.Url}";

        var mpvPath = Path.Combine(AppContext.BaseDirectory, "libmpv-2.dll");
        MpvInfoText.Text = File.Exists(mpvPath)
            ? $"libmpv（{new FileInfo(mpvPath).Length / 1024 / 1024} MB）"
            : "缺少 libmpv-2.dll，无法播放";

        ConfigPathText.Text = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RainPlayer");
    }

    /// <summary>把 TMDB Key 还原成内置的公共 Key。</summary>
    private void OnResetTmdbClick(object sender, RoutedEventArgs e)
    {
        TmdbKeyBox.Text = RainPlayer.Core.Keys.TmdbApiKey;
    }

    private void OnSaveClick(object sender, RoutedEventArgs e)
    {
        _settings.AudioPassthrough = PassthroughBox.IsChecked == true;
        _settings.HardwareDecoding = HardwareBox.IsChecked == true;
        _settings.HdrPassthrough = HdrBox.IsChecked == true;
        _settings.AutoPlayNext = AutoNextBox.IsChecked == true;
        _settings.Volume = (int)VolumeSlider.Value;
        _settings.GlassEffect = GlassBox.IsChecked == true;
        _settings.AggregateSearch = AggregateBox.IsChecked == true;
        _settings.DailyRecommend = DailyBox.IsChecked == true;
        _settings.DoubanRating = DoubanBox.IsChecked == true;
        _settings.TmdbApiKey = TmdbKeyBox.Text.Trim();
        _settings.Save();

        ThemeManager.Apply(_settings.ThemeKey, _settings.GlassEffect);
        RatingsService.ClearCache();

        DialogResult = true;
        Close();
    }

    private void OnCancelClick(object sender, RoutedEventArgs e)
    {
        // 取消时把预览过的主题恢复回去
        ThemeManager.Apply(_settings.ThemeKey, _settings.GlassEffect);
        Close();
    }

    /// <summary>主题选择器：每个主题一个小色块 + 名称。</summary>
    private void BuildThemePicker()
    {
        ThemePanel.Children.Clear();
        foreach (var palette in ThemeManager.All)
        {
            var isCurrent = palette.Key == _settings.ThemeKey;

            var row = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            row.Children.Add(new Border
            {
                Width = 12,
                Height = 12,
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(palette.Accent),
                Margin = new Thickness(0, 0, 7, 0),
            });
            row.Children.Add(new TextBlock
            {
                Text = palette.DisplayName,
                FontSize = 12,
                VerticalAlignment = VerticalAlignment.Center,
                Foreground = isCurrent
                    ? new SolidColorBrush(palette.Accent)
                    : (Brush)FindResource("TextBrush"),
            });

            var button = new Button
            {
                Content = row,
                Style = (Style)FindResource("GhostButton"),
                Padding = new Thickness(10, 7, 10, 7),
                Margin = new Thickness(0, 0, 8, 8),
            };
            if (isCurrent) button.BorderBrush = new SolidColorBrush(palette.Accent);
            button.Click += (_, _) =>
            {
                _settings.ThemeKey = palette.Key;
                ThemeManager.Apply(palette, GlassBox.IsChecked == true);
                BuildThemePicker();
            };
            ThemePanel.Children.Add(button);
        }
    }

    private void OnOpenConfigClick(object sender, RoutedEventArgs e)
    {
        var path = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "RainPlayer");
        try
        {
            Directory.CreateDirectory(path);
            Process.Start(new ProcessStartInfo { FileName = path, UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "无法打开目录", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }
}
