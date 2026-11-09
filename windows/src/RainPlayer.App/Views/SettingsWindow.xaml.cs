using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows;
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
        _settings.Save();

        DialogResult = true;
        Close();
    }

    private void OnCancelClick(object sender, RoutedEventArgs e) => Close();

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
