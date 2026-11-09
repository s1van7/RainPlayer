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

        BuildModePicker();
        BuildThemePicker();
        GlassBox.IsChecked = _settings.GlassEffect;
        AggregateBox.IsChecked = _settings.AggregateSearch;
        DailyBox.IsChecked = _settings.DailyRecommend;
        DoubanBox.IsChecked = _settings.DoubanRating;
        TmdbKeyBox.Text = _settings.TmdbApiKey;

        // 主题与毛玻璃即时预览，点保存才算最终生效
        GlassBox.Checked += (_, _) => ThemeManager.Apply(_settings.ThemeKey, true, _settings.ThemeMode);
        GlassBox.Unchecked += (_, _) => ThemeManager.Apply(_settings.ThemeKey, false, _settings.ThemeMode);

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

        ThemeManager.Apply(_settings.ThemeKey, _settings.GlassEffect, _settings.ThemeMode);
        RatingsService.ClearCache();

        DialogResult = true;
        Close();
    }

    private void OnCancelClick(object sender, RoutedEventArgs e)
    {
        // 取消时把预览过的主题恢复回去
        ThemeManager.Apply(_settings.ThemeKey, _settings.GlassEffect, _settings.ThemeMode);
        Close();
    }

    /// <summary>明暗模式：跟随系统 / 浅色 / 深色。</summary>
    private void BuildModePicker()
    {
        ModePanel.Children.Clear();
        var modes = new (string Key, string Name)[]
        {
            ("system", "跟随系统"),
            ("light", "浅色"),
            ("dark", "深色"),
        };
        foreach (var (key, name) in modes)
        {
            var isCurrent = key == _settings.ThemeMode;
            var button = new Button
            {
                Content = name,
                FontSize = 12,
                Style = (Style)FindResource("GhostButton"),
                Padding = new Thickness(14, 7, 14, 7),
                Margin = new Thickness(0, 0, 8, 8),
            };
            if (isCurrent) button.BorderBrush = (Brush)FindResource("AccentBrush");
            button.Click += (_, _) =>
            {
                _settings.ThemeMode = key;
                ThemeManager.Apply(_settings.ThemeKey, GlassBox.IsChecked == true, key);
                BuildModePicker();
                BuildThemePicker();
            };
            ModePanel.Children.Add(button);
        }
    }

    /// <summary>主题选择器：每个主题一个小色块 + 名称。</summary>
    private void BuildThemePicker()
    {
        ThemePanel.Children.Clear();
        foreach (var palette in ThemeManager.All)
        {
            var isCurrent = palette.Key == _settings.ThemeKey;
            var shown = ThemeManager.IsLight ? ThemeManager.LightOf(palette) : palette;

            var row = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
            row.Children.Add(new Border
            {
                Width = 12,
                Height = 12,
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(shown.Accent),
                Margin = new Thickness(0, 0, 7, 0),
            });
            row.Children.Add(new TextBlock
            {
                Text = palette.DisplayName,
                FontSize = 12,
                VerticalAlignment = VerticalAlignment.Center,
                Foreground = isCurrent
                    ? new SolidColorBrush(shown.Accent)
                    : (Brush)FindResource("TextBrush"),
            });

            var button = new Button
            {
                Content = row,
                Style = (Style)FindResource("GhostButton"),
                Padding = new Thickness(10, 7, 10, 7),
                Margin = new Thickness(0, 0, 8, 8),
            };
            if (isCurrent) button.BorderBrush = new SolidColorBrush(shown.Accent);
            button.Click += (_, _) =>
            {
                _settings.ThemeKey = palette.Key;
                ThemeManager.Apply(palette, GlassBox.IsChecked == true, ThemeManager.IsLight);
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
