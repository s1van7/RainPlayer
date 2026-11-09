using System.Windows;
using System.Windows.Threading;
using RainPlayer.App.Views;
using RainPlayer.Core.Backends;
using RainPlayer.Core.Models;
using RainPlayer.Core.Settings;
using RainPlayer.App.Themes;

namespace RainPlayer.App;

public partial class App : Application
{
    public static App Instance => (App)Current;

    public AppSettings Settings { get; private set; } = new();

    /// <summary>当前连接的服务器与其后端实例。</summary>
    public IMediaBackend? Backend { get; private set; }

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
        {
            if (args.ExceptionObject is Exception ex) ShowFatal(ex);
        };

        Settings = AppSettings.Load();
        ThemeManager.Apply(Settings.ThemeKey, Settings.GlassEffect);

        var server = Settings.CurrentServer();
        if (server != null)
        {
            try
            {
                Backend = CreateBackend(server);
                ShowMain();
                return;
            }
            catch
            {
                // 配置损坏时退回登录页
            }
        }

        ShowLogin();
    }

    public void ShowLogin()
    {
        var login = new LoginWindow();
        MainWindow = login;
        login.Show();
    }

    /// <summary>登录成功后切换到主界面。</summary>
    public void Connect(ServerConfig config)
    {
        Settings.UpsertServer(config);
        Settings.Save();
        Backend = CreateBackend(config);

        var main = new MainWindow();
        MainWindow = main;
        main.Show();

        foreach (var window in Windows.OfType<Window>().Where(w => w != main).ToList())
            window.Close();
    }

    /// <summary>把后端包一层多线路自动切换；线路切换结果会落盘。</summary>
    public static IMediaBackend CreateBackend(ServerConfig config) =>
        new FailoverBackend(config.Normalize(), updated =>
        {
            var app = (App)Current;
            app.Settings.UpsertServer(updated);
            app.Settings.Save();
        });

    /// <summary>切换活动服务器（不动登录态）。</summary>
    public void SwitchServer(ServerConfig config)
    {
        Settings.UpsertServer(config);
        Settings.Save();
        Backend = CreateBackend(config);
    }

    private void ShowMain()
    {
        var main = new MainWindow();
        MainWindow = main;
        main.Show();
    }

    /// <summary>退出登录：清掉当前服务器后回到登录页。</summary>
    public void SignOut()
    {
        var current = Settings.CurrentServer();
        if (current != null)
        {
            Settings.Servers.RemoveAll(s => s.Id == current.Id);
            Settings.LastServerId = Settings.Servers.FirstOrDefault()?.Id ?? "";
            Settings.Save();
        }

        Backend = null;
        ShutdownMode = ShutdownMode.OnExplicitShutdown;
        ShowLogin();
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        ShowFatal(e.Exception);
        e.Handled = true;
    }

    private static void ShowFatal(Exception ex)
    {
        try
        {
            MessageBox.Show(ex.Message, "RainPlayer 出错了", MessageBoxButton.OK, MessageBoxImage.Error);
        }
        catch
        {
            // 连弹窗都失败时直接忽略
        }
    }
}
