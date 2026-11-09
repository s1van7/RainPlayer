using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using RainPlayer.App.Controls;
using RainPlayer.App.Services;
using RainPlayer.Core.Backends;
using RainPlayer.Core.Models;

namespace RainPlayer.App.Views;

public partial class MainWindow : Window
{
    private enum ViewKind
    {
        Home,
        Library,
        Detail,
        Search,
    }

    private sealed record Navigation(ViewKind Kind, string Id, string Title);

    private readonly IMediaBackend _backend;
    private readonly Stack<Navigation> _history = new();
    private readonly List<Library> _libraries = new();
    private Navigation _current = new(ViewKind.Home, "", "首页");
    private bool _busy;

    public MainWindow()
    {
        InitializeComponent();

        _backend = ((App)Application.Current).Backend
                   ?? throw new InvalidOperationException("尚未连接服务器");

        ImageLoader.Headers = _backend.ImageHeaders;
        ServerText.Text = _backend.Config.Name;

        SearchBox.TextChanged += (_, _) =>
            SearchPlaceholder.Visibility = SearchBox.Text.Length == 0 ? Visibility.Visible : Visibility.Collapsed;

        Loaded += async (_, _) => await InitializeAsync();
    }

    private async Task InitializeAsync()
    {
        await LoadLibrariesAsync();
        await ShowHomeAsync();
    }

    // ============================================================ 媒体库导航

    private async Task LoadLibrariesAsync()
    {
        try
        {
            _libraries.Clear();
            _libraries.AddRange(await _backend.GetLibrariesAsync());
        }
        catch (Exception ex)
        {
            ShowEmpty($"媒体库加载失败：{ex.Message}");
            return;
        }

        LibraryNavPanel.Children.Clear();
        foreach (var library in _libraries)
        {
            var button = new RadioButton
            {
                Content = library.Name,
                Style = (Style)FindResource("NavButton"),
                Margin = new Thickness(0, 0, 0, 2),
                GroupName = "nav",
            };
            var id = library.Id;
            var name = library.Name;
            button.Checked += async (_, _) =>
            {
                if (_current.Kind == ViewKind.Library && _current.Id == id) return;
                await ShowLibraryAsync(id, name);
            };
            LibraryNavPanel.Children.Add(button);
        }
    }

    private async void OnHomeNav(object sender, RoutedEventArgs e)
    {
        if (_current.Kind == ViewKind.Home) return;
        await ShowHomeAsync();
    }

    // ============================================================ 首页

    private async Task ShowHomeAsync()
    {
        if (_busy) return;
        SetBusy(true);
        ResetNavSelection(HomeNav);
        _current = new Navigation(ViewKind.Home, "", "首页");
        PageTitle.Text = "首页";
        BackButton.Visibility = Visibility.Collapsed;
        ContentPanel.Children.Clear();

        try
        {
            var rows = await _backend.GetHomeRowsAsync();
            if (rows.Count == 0)
            {
                ShowEmpty("服务器上还没有可显示的媒体");
                return;
            }

            HideEmpty();
            foreach (var row in rows)
                ContentPanel.Children.Add(BuildRow(row.Title, row.Items, row.LibraryId));
        }
        catch (Exception ex)
        {
            ShowEmpty($"加载失败：{ex.Message}");
        }
        finally
        {
            SetBusy(false);
        }
    }

    private UIElement BuildRow(string title, List<MediaCard> items, string? libraryId)
    {
        var panel = new StackPanel { Margin = new Thickness(0, 0, 0, 26) };

        var header = new Grid { Margin = new Thickness(0, 0, 0, 12) };
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var titleText = new TextBlock
        {
            Text = title,
            Style = (Style)FindResource("SectionText"),
            VerticalAlignment = VerticalAlignment.Center,
        };
        Grid.SetColumn(titleText, 0);
        header.Children.Add(titleText);

        if (libraryId != null)
        {
            var more = new Button
            {
                Content = "查看全部 →",
                Style = (Style)FindResource("GhostButton"),
                FontSize = 12,
                Padding = new Thickness(12, 5, 12, 5),
            };
            more.Click += async (_, _) => await ShowLibraryAsync(libraryId, title);
            Grid.SetColumn(more, 1);
            header.Children.Add(more);
        }

        panel.Children.Add(header);

        var scroller = new ScrollViewer
        {
            HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
            VerticalScrollBarVisibility = ScrollBarVisibility.Disabled,
        };
        var strip = new StackPanel { Orientation = Orientation.Horizontal };
        foreach (var item in items) strip.Children.Add(CreateCard(item));
        scroller.Content = strip;
        panel.Children.Add(scroller);

        return panel;
    }

    private PosterCard CreateCard(MediaCard card)
    {
        var control = new PosterCard();
        control.Bind(card);
        control.CardClicked += async (_, clicked) => await ShowDetailAsync(clicked.Id, clicked.Title);
        return control;
    }

    // ============================================================ 媒体库

    private async Task ShowLibraryAsync(string libraryId, string name)
    {
        if (_busy) return;
        SetBusy(true);
        _history.Push(_current);
        _current = new Navigation(ViewKind.Library, libraryId, name);
        PageTitle.Text = name;
        BackButton.Visibility = Visibility.Visible;
        ContentPanel.Children.Clear();

        try
        {
            var page = await _backend.GetItemsAsync(libraryId, 0, 120);
            if (page.Items.Count == 0)
            {
                ShowEmpty("这个媒体库还没有内容");
                return;
            }

            HideEmpty();
            ContentPanel.Children.Add(BuildGrid(page.Items));
        }
        catch (Exception ex)
        {
            ShowEmpty($"加载失败：{ex.Message}");
        }
        finally
        {
            SetBusy(false);
        }
    }

    private UIElement BuildGrid(List<MediaCard> items)
    {
        var wrap = new WrapPanel { Orientation = Orientation.Horizontal };
        foreach (var item in items)
        {
            var card = CreateCard(item);
            card.Margin = new Thickness(0, 0, 16, 22);
            wrap.Children.Add(card);
        }
        return wrap;
    }

    // ============================================================ 搜索

    private async void OnSearchKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Enter) return;
        var query = SearchBox.Text.Trim();
        if (query.Length == 0) return;
        await ShowSearchAsync(query);
    }

    private async Task ShowSearchAsync(string query)
    {
        if (_busy) return;
        SetBusy(true);
        _history.Push(_current);
        _current = new Navigation(ViewKind.Search, query, $"搜索：{query}");
        PageTitle.Text = $"搜索「{query}」";
        BackButton.Visibility = Visibility.Visible;
        ContentPanel.Children.Clear();

        try
        {
            var items = await _backend.SearchAsync(query);
            if (items.Count == 0)
            {
                ShowEmpty($"没有找到与「{query}」相关的内容");
                return;
            }

            HideEmpty();
            ContentPanel.Children.Add(BuildGrid(items));
        }
        catch (Exception ex)
        {
            ShowEmpty($"搜索失败：{ex.Message}");
        }
        finally
        {
            SetBusy(false);
        }
    }

    // ============================================================ 详情

    private async Task ShowDetailAsync(string itemId, string? fallbackTitle = null)
    {
        if (_busy) return;
        SetBusy(true);
        _history.Push(_current);
        PageTitle.Text = fallbackTitle ?? "详情";
        BackButton.Visibility = Visibility.Visible;
        ContentPanel.Children.Clear();

        try
        {
            var detail = await _backend.GetDetailAsync(itemId);
            _current = new Navigation(ViewKind.Detail, itemId, detail.Title);
            PageTitle.Text = detail.Title;

            HideEmpty();
            ContentPanel.Children.Add(BuildDetail(detail));
        }
        catch (Exception ex)
        {
            ShowEmpty($"加载详情失败：{ex.Message}");
        }
        finally
        {
            SetBusy(false);
        }
    }

    private UIElement BuildDetail(MediaDetail detail)
    {
        var root = new StackPanel();

        // ---- 头部：海报 + 信息 ----
        var header = new Grid();
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        header.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

        var poster = new Border
        {
            Width = 250,
            Height = 360,
            CornerRadius = new CornerRadius(12),
            Background = (Brush)FindResource("CardBrush"),
            ClipToBounds = true,
            Margin = new Thickness(0, 0, 30, 0),
        };
        var posterImage = new Image { Stretch = Stretch.UniformToFill };
        poster.Child = posterImage;
        LoadImageInto(posterImage, detail.PosterUrl);
        Grid.SetColumn(poster, 0);
        header.Children.Add(poster);

        var info = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        Grid.SetColumn(info, 1);

        info.Children.Add(new TextBlock
        {
            Text = detail.Title,
            FontSize = 30,
            FontWeight = FontWeights.Bold,
            TextWrapping = TextWrapping.Wrap,
        });

        if (!string.IsNullOrWhiteSpace(detail.OriginalTitle) && detail.OriginalTitle != detail.Title)
        {
            info.Children.Add(new TextBlock
            {
                Text = detail.OriginalTitle!,
                FontSize = 14,
                Foreground = (Brush)FindResource("SubTextBrush"),
                Margin = new Thickness(0, 6, 0, 0),
                TextWrapping = TextWrapping.Wrap,
            });
        }

        var metaParts = new List<string>();
        if (detail.Year is > 0) metaParts.Add(detail.Year.ToString()!);
        if (detail.RuntimeMinutes is > 0) metaParts.Add($"{detail.RuntimeMinutes} 分钟");
        if (!string.IsNullOrWhiteSpace(detail.OfficialRating)) metaParts.Add(detail.OfficialRating!);
        if (detail.Rating is > 0) metaParts.Add($"★ {detail.Rating:0.0}");
        if (!string.IsNullOrWhiteSpace(detail.Studio)) metaParts.Add(detail.Studio!);
        if (metaParts.Count > 0)
        {
            info.Children.Add(new TextBlock
            {
                Text = string.Join("  ·  ", metaParts),
                FontSize = 13,
                Foreground = (Brush)FindResource("SubTextBrush"),
                Margin = new Thickness(0, 12, 0, 0),
            });
        }

        if (detail.Genres.Count > 0)
        {
            var chips = new WrapPanel { Margin = new Thickness(0, 14, 0, 0) };
            foreach (var genre in detail.Genres.Take(8))
            {
                chips.Children.Add(new Border
                {
                    Background = (Brush)FindResource("CardBrush"),
                    CornerRadius = new CornerRadius(6),
                    Padding = new Thickness(9, 4, 9, 4),
                    Margin = new Thickness(0, 0, 8, 8),
                    Child = new TextBlock
                    {
                        Text = genre,
                        FontSize = 12,
                        Foreground = (Brush)FindResource("SubTextBrush"),
                    },
                });
            }
            info.Children.Add(chips);
        }

        if (!string.IsNullOrWhiteSpace(detail.Overview))
        {
            info.Children.Add(new TextBlock
            {
                Text = detail.Overview!,
                FontSize = 13.5,
                LineHeight = 22,
                TextWrapping = TextWrapping.Wrap,
                Foreground = (Brush)FindResource("SubTextBrush"),
                Margin = new Thickness(0, 14, 0, 0),
                MaxHeight = 132,
            });
        }

        // ---- 播放按钮 ----
        var actions = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Margin = new Thickness(0, 24, 0, 0),
        };

        var playButton = new Button
        {
            Content = detail.Type == MediaType.Series ? "▶  播放第一集" : "▶  播放",
            Style = (Style)FindResource("PrimaryButton"),
            Height = 42,
            Padding = new Thickness(26, 0, 26, 0),
        };
        playButton.Click += async (_, _) => await StartPlaybackAsync(detail, detail.PlayTargetId);
        actions.Children.Add(playButton);

        var favoriteButton = new Button
        {
            Content = detail.Favorite ? "★  已收藏" : "☆  收藏",
            Style = (Style)FindResource("GhostButton"),
            Height = 42,
            Margin = new Thickness(12, 0, 0, 0),
        };
        favoriteButton.Click += async (_, _) =>
        {
            var next = !detail.Favorite;
            await _backend.SetFavoriteAsync(detail.Id, next);
            detail.Favorite = next;
            favoriteButton.Content = next ? "★  已收藏" : "☆  收藏";
        };
        actions.Children.Add(favoriteButton);

        var watchedButton = new Button
        {
            Content = detail.Played ? "标记为未看" : "标记为已看",
            Style = (Style)FindResource("GhostButton"),
            Height = 42,
            Margin = new Thickness(10, 0, 0, 0),
        };
        watchedButton.Click += async (_, _) =>
        {
            var next = !detail.Played;
            await _backend.SetWatchedAsync(detail.Id, next);
            detail.Played = next;
            watchedButton.Content = next ? "标记为未看" : "标记为已看";
        };
        actions.Children.Add(watchedButton);

        info.Children.Add(actions);
        header.Children.Add(info);
        root.Children.Add(header);

        // ---- 剧集 ----
        if (detail.Type == MediaType.Series && detail.Seasons.Count > 0)
        {
            root.Children.Add(new Border
            {
                Style = (Style)FindResource("Divider"),
                Margin = new Thickness(0, 34, 0, 22),
            });

            var seasonBar = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Margin = new Thickness(0, 0, 0, 16),
            };
            root.Children.Add(seasonBar);

            var episodeHost = new StackPanel();
            root.Children.Add(episodeHost);

            var seasonButtons = new List<RadioButton>();

            async Task LoadSeasonAsync(Season season)
            {
                episodeHost.Children.Clear();
                episodeHost.Children.Add(new TextBlock
                {
                    Text = "正在加载剧集…",
                    Foreground = (Brush)FindResource("SubTextBrush"),
                    Margin = new Thickness(0, 8, 0, 0),
                });

                List<Episode> episodes;
                try
                {
                    episodes = await _backend.GetEpisodesAsync(detail.Id, season.Id);
                }
                catch (Exception ex)
                {
                    episodeHost.Children.Clear();
                    episodeHost.Children.Add(new TextBlock
                    {
                        Text = $"剧集加载失败：{ex.Message}",
                        Foreground = (Brush)FindResource("DangerBrush"),
                    });
                    return;
                }

                episodeHost.Children.Clear();
                if (episodes.Count == 0)
                {
                    episodeHost.Children.Add(new TextBlock
                    {
                        Text = "这一季还没有剧集",
                        Foreground = (Brush)FindResource("SubTextBrush"),
                    });
                    return;
                }

                foreach (var episode in episodes)
                {
                    var row = new Button
                    {
                        Background = Brushes.Transparent,
                        BorderThickness = new Thickness(0),
                        Padding = new Thickness(0),
                        HorizontalContentAlignment = HorizontalAlignment.Stretch,
                        Cursor = Cursors.Hand,
                        Margin = new Thickness(0, 0, 0, 4),
                    };

                    var content = new Grid { Margin = new Thickness(0, 8, 0, 8) };
                    content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(64) });
                    content.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                    content.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

                    var indexText = new TextBlock
                    {
                        Text = episode.Index > 0 ? episode.Index.ToString("00") : "—",
                        FontSize = 15,
                        FontWeight = FontWeights.SemiBold,
                        Foreground = episode.Played
                            ? (Brush)FindResource("FaintTextBrush")
                            : (Brush)FindResource("AccentBrush"),
                        VerticalAlignment = VerticalAlignment.Center,
                    };
                    Grid.SetColumn(indexText, 0);
                    content.Children.Add(indexText);

                    var titleStack = new StackPanel();
                    titleStack.Children.Add(new TextBlock
                    {
                        Text = episode.Title,
                        FontSize = 14,
                        TextTrimming = TextTrimming.CharacterEllipsis,
                        Foreground = (Brush)FindResource("TextBrush"),
                    });
                    var subParts = new List<string>();
                    if (episode.RuntimeMinutes is > 0) subParts.Add($"{episode.RuntimeMinutes} 分钟");
                    if (episode.Played) subParts.Add("已看完");
                    else if (episode.Progress > 0.01) subParts.Add($"已看 {episode.Progress * 100:0}%");
                    if (subParts.Count > 0)
                    {
                        titleStack.Children.Add(new TextBlock
                        {
                            Text = string.Join(" · ", subParts),
                            FontSize = 11.5,
                            Foreground = (Brush)FindResource("SubTextBrush"),
                            Margin = new Thickness(0, 3, 0, 0),
                        });
                    }
                    Grid.SetColumn(titleStack, 1);
                    content.Children.Add(titleStack);

                    var playIcon = new TextBlock
                    {
                        Text = "▶",
                        FontSize = 13,
                        Foreground = (Brush)FindResource("SubTextBrush"),
                        VerticalAlignment = VerticalAlignment.Center,
                        Margin = new Thickness(12, 0, 12, 0),
                    };
                    Grid.SetColumn(playIcon, 2);
                    content.Children.Add(playIcon);

                    row.Content = content;
                    var captured = episode;
                    row.Click += async (_, _) => await StartPlaybackAsync(detail, captured.Id);
                    row.MouseEnter += (_, _) => row.Background = (Brush)FindResource("CardBrush");
                    row.MouseLeave += (_, _) => row.Background = Brushes.Transparent;

                    episodeHost.Children.Add(row);
                }
            }

            foreach (var season in detail.Seasons)
            {
                var button = new RadioButton
                {
                    Content = season.Name,
                    Style = (Style)FindResource("NavButton"),
                    GroupName = "season",
                    Margin = new Thickness(0, 0, 8, 0),
                    Padding = new Thickness(14, 6, 14, 6),
                    FontSize = 13,
                };
                var captured = season;
                button.Checked += async (_, _) => await LoadSeasonAsync(captured);
                seasonButtons.Add(button);
                seasonBar.Children.Add(button);
            }

            if (seasonButtons.Count > 0) seasonButtons[0].IsChecked = true;
        }

        return root;
    }

    // ============================================================ 播放

    private async Task StartPlaybackAsync(MediaDetail detail, string itemId)
    {
        try
        {
            SetBusy(true);

            // 剧集类：把整季列表交给播放器做连播
            List<Episode> queue = new();
            if (detail.Type == MediaType.Series)
            {
                var season = detail.Seasons.FirstOrDefault(s =>
                    detail.Episodes.Any(e => e.Id == itemId && e.SeasonIndex == s.Index)) ?? detail.Seasons.FirstOrDefault();
                if (season != null)
                {
                    try
                    {
                        queue = await _backend.GetEpisodesAsync(detail.Id, season.Id);
                    }
                    catch
                    {
                        queue = detail.Episodes;
                    }
                }
                if (queue.Count == 0) queue = detail.Episodes;
            }

            var player = new PlayerWindow(_backend, itemId, detail.Title, queue);
            player.Owner = this;
            player.Show();
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "无法开始播放", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally
        {
            SetBusy(false);
        }
    }

    // ============================================================ 导航与工具

    private async void OnBackClick(object sender, RoutedEventArgs e)
    {
        if (_history.Count == 0)
        {
            await ShowHomeAsync();
            return;
        }

        var previous = _history.Pop();
        switch (previous.Kind)
        {
            case ViewKind.Home:
                await ShowHomeAsync();
                break;
            case ViewKind.Library:
                ResetNavSelection(null);
                await ShowLibraryAsync(previous.Id, previous.Title);
                break;
            case ViewKind.Detail:
                await ShowDetailAsync(previous.Id, previous.Title);
                break;
            case ViewKind.Search:
                await ShowSearchAsync(previous.Id);
                break;
        }
    }

    private void ResetNavSelection(RadioButton? target)
    {
        if (target != null)
        {
            target.IsChecked = true;
            return;
        }

        HomeNav.IsChecked = false;
        foreach (var child in LibraryNavPanel.Children.OfType<RadioButton>())
            child.IsChecked = false;
    }

    private void OnSettingsClick(object sender, RoutedEventArgs e)
    {
        var window = new SettingsWindow { Owner = this };
        window.ShowDialog();
    }

    private void OnSignOutClick(object sender, RoutedEventArgs e)
    {
        var result = MessageBox.Show(this, "退出登录会清除本机保存的服务器与账号信息，确定继续？",
            "退出登录", MessageBoxButton.OKCancel, MessageBoxImage.Question);
        if (result != MessageBoxResult.OK) return;

        ((App)Application.Current).SignOut();
        Close();
    }

    private void SetBusy(bool busy)
    {
        _busy = busy;
        LoadingLayer.Visibility = busy ? Visibility.Visible : Visibility.Collapsed;
    }

    private void ShowEmpty(string message)
    {
        EmptyText.Text = message;
        EmptyLayer.Visibility = Visibility.Visible;
    }

    private void HideEmpty() => EmptyLayer.Visibility = Visibility.Collapsed;

    private static async void LoadImageInto(Image target, string? url)
    {
        if (string.IsNullOrEmpty(url)) return;
        var cached = ImageLoader.GetCached(url);
        if (cached != null)
        {
            target.Source = cached;
            return;
        }

        var image = await ImageLoader.LoadAsync(url);
        if (image != null) target.Source = image;
    }
}
