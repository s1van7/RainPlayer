using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using RainPlayer.Core.Backends;
using RainPlayer.Core.Models;

namespace RainPlayer.App.Views;

/// <summary>
/// 服务器设置：名称、图标、线路、账号。
/// 每改一项就落盘并重建后端，不用点保存。
/// </summary>
public partial class ServerWindow : Window
{
    public ServerWindow()
    {
        InitializeComponent();
        Loaded += (_, _) => Rebuild();
    }

    private App App => (App)Application.Current;

    private void OnCloseClick(object sender, RoutedEventArgs e) => Close();

    private void Rebuild()
    {
        ServerList.Children.Clear();
        foreach (var server in App.Settings.Servers)
            ServerList.Children.Add(BuildServerCard(server));
    }

    private UIElement BuildServerCard(ServerConfig server)
    {
        var isCurrent = server.Id == App.Backend?.Config.Id;

        var card = new Border
        {
            CornerRadius = new CornerRadius(16),
            Background = (Brush)FindResource("CardBrush"),
            BorderBrush = (Brush)FindResource("GlassBorderBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(18),
            Margin = new Thickness(0, 0, 0, 12),
        };

        var stack = new StackPanel();

        // ---- 标题 ----
        var header = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 12) };
        header.Children.Add(new TextBlock
        {
            Text = server.Name,
            FontSize = 17,
            FontWeight = FontWeights.SemiBold,
            VerticalAlignment = VerticalAlignment.Center,
        });
        header.Children.Add(new TextBlock
        {
            Text = isCurrent ? "  · 当前使用" : "  · " + server.Summary,
            FontSize = 11,
            Foreground = (Brush)FindResource("SubTextBrush"),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(6, 0, 0, 0),
        });
        stack.Children.Add(header);

        // ---- 名称 ----
        stack.Children.Add(FieldLabel("显示名称（首页左上角）"));
        var nameBox = TextBoxOf(server.Name, "例如：家里的 Emby");
        nameBox.LostFocus += (_, _) =>
        {
            if (nameBox.Text.Trim() == server.Name) return;
            var cfg = server.Clone();
            cfg.Name = nameBox.Text.Trim();
            Persist(cfg);
            Rebuild();
        };
        stack.Children.Add(nameBox);

        // ---- 图标 ----
        stack.Children.Add(FieldLabel("图标（图片 URL，或 emoji / 文字）"));
        var iconBox = TextBoxOf(server.IconUrl, "https://… 或 🎬");
        iconBox.LostFocus += (_, _) =>
        {
            if (iconBox.Text.Trim() == server.IconUrl) return;
            var cfg = server.Clone();
            cfg.IconUrl = iconBox.Text.Trim();
            Persist(cfg);
        };
        stack.Children.Add(iconBox);

        // ---- 线路 ----
        stack.Children.Add(SectionTitle("线路", "点一下切到这条线路"));
        foreach (var line in server.Lines)
        {
            stack.Children.Add(BuildSelectableRow(
                line.DisplayName,
                line.Url,
                line.Id == server.ActiveLineId,
                server.Lines.Count > 1,
                () =>
                {
                    var cfg = server.WithLine(line.Id);
                    Persist(cfg);
                    Rebuild();
                },
                () =>
                {
                    var cfg = server.Clone();
                    cfg.RemoveLine(line.Id);
                    Persist(cfg);
                    Rebuild();
                }));
        }

        var addLinePanel = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
        var lineName = TextBoxOf("", "线路名（可留空）", 130);
        var lineUrl = TextBoxOf("", "http://example.com:8096", 300);
        addLinePanel.Children.Add(lineName);
        addLinePanel.Children.Add(lineUrl);
        var addLineButton = SmallButton("添加线路", async () =>
        {
            var url = BackendFactory.NormalizeUrl(lineUrl.Text);
            if (string.IsNullOrWhiteSpace(url))
            {
                MessageBox.Show(this, "请输入线路地址", "添加线路", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            if (server.Lines.Any(l => l.Url == url))
            {
                MessageBox.Show(this, "这条线路已经添加过了", "添加线路", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }

            var cfg = server.Clone();
            var line = new ServerLine
            {
                Id = "line-" + Guid.NewGuid().ToString("N")[..8],
                Name = string.IsNullOrWhiteSpace(lineName.Text) ? url : lineName.Text.Trim(),
                Url = url,
            };
            cfg.AddLine(line);
            Persist(cfg);
            Rebuild();
        });
        addLinePanel.Children.Add(addLineButton);
        stack.Children.Add(addLinePanel);

        // ---- 账号 ----
        stack.Children.Add(SectionTitle("登录账号", "同一台服务器可以挂多个账号"));
        foreach (var user in server.Users)
        {
            stack.Children.Add(BuildSelectableRow(
                user.Username,
                "已登录",
                user.Id == server.ActiveUserId,
                server.Users.Count > 1,
                () =>
                {
                    var cfg = server.WithUser(user.Id);
                    Persist(cfg);
                    Rebuild();
                },
                () =>
                {
                    var cfg = server.Clone();
                    cfg.RemoveUser(user.Id);
                    Persist(cfg);
                    Rebuild();
                }));
        }

        var addUserPanel = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
        var userName = TextBoxOf("", "用户名", 150);
        var userPass = TextBoxOf("", "密码", 150);
        userPass.IsEnabled = true;
        addUserPanel.Children.Add(userName);
        addUserPanel.Children.Add(userPass);
        addUserPanel.Children.Add(SmallButton("登录新账号", async () =>
        {
            if (string.IsNullOrWhiteSpace(userName.Text))
            {
                MessageBox.Show(this, "请输入用户名", "登录新账号", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            try
            {
                var backend = BackendFactory.Create(server);
                var authed = await backend.AuthenticateAsync(userName.Text.Trim(), userPass.Text);
                var cfg = server.Clone();
                cfg.AddUser(new ServerUser
                {
                    Id = "user-" + Guid.NewGuid().ToString("N")[..8],
                    Username = authed.Username,
                    Token = authed.Token,
                    UserId = authed.UserId,
                });
                Persist(cfg);
                Rebuild();
            }
            catch (Exception ex)
            {
                MessageBox.Show(this, ex.Message, "登录失败", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }));
        stack.Children.Add(addUserPanel);

        // ---- 删除 ----
        var deleteRow = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 16, 0, 0) };
        deleteRow.Children.Add(SmallButton("删除这台服务器", () =>
        {
            if (App.Settings.Servers.Count <= 1)
            {
                MessageBox.Show(this, "至少保留一台服务器", "删除", MessageBoxButton.OK, MessageBoxImage.Information);
                return;
            }
            var result = MessageBox.Show(this, $"确定删除「{server.Name}」？", "删除服务器",
                MessageBoxButton.OKCancel, MessageBoxImage.Question);
            if (result != MessageBoxResult.OK) return;

            App.Settings.Servers.RemoveAll(s => s.Id == server.Id);
            if (App.Settings.LastServerId == server.Id)
                App.Settings.LastServerId = App.Settings.Servers.FirstOrDefault()?.Id ?? "";
            App.Settings.Save();

            var next = App.Settings.CurrentServer();
            if (next != null) App.SwitchServer(next);
            Rebuild();
        }, danger: true));
        stack.Children.Add(deleteRow);

        card.Child = stack;
        return card;
    }

    private void Persist(ServerConfig config)
    {
        App.Settings.UpsertServer(config);
        App.Settings.Save();
        if (config.Id == App.Settings.LastServerId || App.Backend == null || App.Backend.Config.Id == config.Id)
            App.SwitchServer(config);
    }

    // ------------------------------------------------------------ 控件工厂

    private static TextBlock FieldLabel(string text) => new()
    {
        Text = text,
        FontSize = 11,
        Foreground = (Brush)Application.Current.FindResource("SubTextBrush"),
        Margin = new Thickness(0, 10, 0, 4),
    };

    private static TextBlock SectionTitle(string title, string hint)
    {
        var text = new TextBlock
        {
            Text = title,
            FontSize = 13,
            FontWeight = FontWeights.SemiBold,
            Margin = new Thickness(0, 16, 0, 2),
        };
        text.Inlines.Add(new System.Windows.Documents.Run("  " + hint)
        {
            FontSize = 10.5,
            FontWeight = FontWeights.Normal,
            Foreground = (Brush)Application.Current.FindResource("SubTextBrush"),
        });
        return text;
    }

    private static TextBox TextBoxOf(string value, string placeholder, double width = double.NaN)
    {
        var box = new TextBox
        {
            Text = value,
            FontSize = 13,
            Padding = new Thickness(10, 7, 10, 7),
            Margin = new Thickness(0, 0, 8, 6),
            Background = (Brush)Application.Current.FindResource("SurfaceBrush"),
            Foreground = (Brush)Application.Current.FindResource("TextBrush"),
            CaretBrush = (Brush)Application.Current.FindResource("TextBrush"),
            BorderBrush = (Brush)Application.Current.FindResource("BorderBrush"),
            BorderThickness = new Thickness(1),
            ToolTip = placeholder,
        };
        if (!double.IsNaN(width)) box.Width = width;
        else box.HorizontalAlignment = HorizontalAlignment.Stretch;
        return box;
    }

    private static Button SmallButton(string text, Action action, bool danger = false)
    {
        var button = new Button
        {
            Content = text,
            Style = (Style)Application.Current.FindResource("GhostButton"),
            FontSize = 12,
            Padding = new Thickness(12, 6, 12, 6),
            Margin = new Thickness(0, 0, 6, 0),
            VerticalAlignment = VerticalAlignment.Center,
        };
        if (danger) button.Foreground = (Brush)Application.Current.FindResource("DangerBrush");
        button.Click += (_, _) => action();
        return button;
    }

    private static UIElement BuildSelectableRow(
        string title, string subtitle, bool selected, bool allowDelete,
        Action onSelect, Action onDelete)
    {
        var grid = new Grid { Margin = new Thickness(0, 2, 0, 2) };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var dot = new System.Windows.Shapes.Ellipse
        {
            Width = 12,
            Height = 12,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 10, 0),
            Fill = selected
                ? (Brush)Application.Current.FindResource("AccentBrush")
                : new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
        };
        Grid.SetColumn(dot, 0);
        grid.Children.Add(dot);

        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(new TextBlock
        {
            Text = title,
            FontSize = 13,
            Foreground = selected
                ? (Brush)Application.Current.FindResource("AccentBrush")
                : (Brush)Application.Current.FindResource("TextBrush"),
        });
        text.Children.Add(new TextBlock
        {
            Text = subtitle,
            FontSize = 10.5,
            Foreground = (Brush)Application.Current.FindResource("SubTextBrush"),
            TextTrimming = TextTrimming.CharacterEllipsis,
        });
        Grid.SetColumn(text, 1);
        grid.Children.Add(text);

        var actions = new StackPanel { Orientation = Orientation.Horizontal };
        if (allowDelete) actions.Children.Add(SmallButton("移除", onDelete));
        Grid.SetColumn(actions, 2);
        grid.Children.Add(actions);

        var border = new Border
        {
            CornerRadius = new CornerRadius(9),
            Padding = new Thickness(10, 6, 10, 6),
            Background = Brushes.Transparent,
            Child = grid,
            Cursor = System.Windows.Input.Cursors.Hand,
        };
        border.MouseLeftButtonUp += (_, _) => onSelect();
        border.MouseEnter += (_, _) => border.Background = (Brush)Application.Current.FindResource("SurfaceBrush");
        border.MouseLeave += (_, _) => border.Background = Brushes.Transparent;
        return border;
    }
}
