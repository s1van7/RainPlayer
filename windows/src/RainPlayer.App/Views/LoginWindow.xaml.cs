using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using RainPlayer.Core.Backends;
using RainPlayer.Core.Models;
using RainPlayer.Core.Settings;

namespace RainPlayer.App.Views;

public partial class LoginWindow : Window
{
    private static readonly ServerKind[] Kinds = { ServerKind.Emby, ServerKind.Jellyfin, ServerKind.FnOs };

    public LoginWindow()
    {
        InitializeComponent();

        foreach (var kind in Kinds) KindBox.Items.Add(kind.DisplayName());
        KindBox.SelectedIndex = 0;

        UrlBox.Text = "http://192.168.1.10:8096";
        UserBox.Focus();

        ApplyLook();
        Loaded += (_, _) => UpdateHint();
    }

    /// <summary>
    /// 登录页背景跟明暗模式走。
    /// 之前背景写死深蓝，浅色模式下文字是近黑色，整页几乎看不清。
    /// </summary>
    private void ApplyLook()
    {
        if (Themes.ThemeManager.IsLight)
        {
            BgStop0.Color = Themes.ThemeManager.Hex("#E8EEFA");
            BgStop1.Color = Themes.ThemeManager.Hex("#FBFCFE");
            LoginCard.Background = new SolidColorBrush(Color.FromArgb(0xF2, 0xFF, 0xFF, 0xFF));
            LoginCard.BorderBrush = new SolidColorBrush(Color.FromArgb(0x22, 0x00, 0x00, 0x00));
        }
        else
        {
            BgStop0.Color = Themes.ThemeManager.Hex("#1B2A4A");
            BgStop1.Color = Themes.ThemeManager.Hex("#0E1014");
            LoginCard.Background = new SolidColorBrush(Color.FromArgb(0xCC, 0x16, 0x19, 0x1F));
            LoginCard.BorderBrush = new SolidColorBrush(Color.FromArgb(0x22, 0xFF, 0xFF, 0xFF));
        }
    }

    private ServerKind SelectedKind => Kinds[Math.Max(KindBox.SelectedIndex, 0)];

    private void OnKindChanged(object sender, SelectionChangedEventArgs e)
    {
        if (!IsLoaded) return;
        UpdateHint();

        // 只在地址还是默认值时替换，避免覆盖用户已填内容
        if (UrlBox.Text.StartsWith("http://192.168.1.10"))
            UrlBox.Text = SelectedKind == ServerKind.FnOs ? "http://192.168.1.10:5666" : "http://192.168.1.10:8096";
    }

    private void UpdateHint()
    {
        UrlHint.Text = SelectedKind switch
        {
            ServerKind.FnOs => "fnOS 的 Web 端口，通常 5666，影视 API 同源",
            ServerKind.Jellyfin => "Jellyfin 默认 8096 端口",
            _ => "Emby 默认 8096 端口",
        };
    }

    private async void OnLoginClick(object sender, RoutedEventArgs e)
    {
        var url = UrlBox.Text.Trim();
        var username = UserBox.Text.Trim();
        var password = PassBox.Password;

        if (url.Length == 0)
        {
            ShowError("请填写服务器地址");
            return;
        }
        if (username.Length == 0)
        {
            ShowError("请填写用户名");
            return;
        }

        SetBusy(true);
        ShowError(null);

        try
        {
            var config = new ServerConfig
            {
                Id = Guid.NewGuid().ToString("N"),
                Kind = SelectedKind,
                Url = BackendFactory.NormalizeUrl(url),
                Username = username,
                DeviceId = AppSettings.NewDeviceId(),
            };

            var backend = BackendFactory.Create(config);
            var connected = await backend.AuthenticateAsync(username, password);

            StatusText.Text = $"已连接 {connected.Name}";
            ((App)Application.Current).Connect(connected);
        }
        catch (Exception ex)
        {
            ShowError(FriendlyError(ex));
        }
        finally
        {
            SetBusy(false);
        }
    }

    private static string FriendlyError(Exception ex) => ex switch
    {
        HttpRequestException { StatusCode: System.Net.HttpStatusCode.Unauthorized } => "用户名或密码错误",
        HttpRequestException { StatusCode: System.Net.HttpStatusCode.Forbidden } => "账号没有访问权限",
        HttpRequestException http => $"连接失败：{http.Message}",
        TaskCanceledException => "连接超时，检查地址和端口是否正确",
        _ => ex.Message,
    };

    private void ShowError(string? message)
    {
        if (string.IsNullOrEmpty(message))
        {
            ErrorText.Visibility = Visibility.Collapsed;
            return;
        }
        ErrorText.Text = message;
        ErrorText.Visibility = Visibility.Visible;
    }

    private void SetBusy(bool busy)
    {
        LoginButton.IsEnabled = !busy;
        LoginButton.Content = busy ? "正在连接…" : "登录";
        if (!busy) StatusText.Text = "";
    }
}
