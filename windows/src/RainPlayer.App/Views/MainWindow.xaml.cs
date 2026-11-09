using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
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
        Libraries,
        Detail,
        Episode,
        Search,
    }

    private sealed record Navigation(ViewKind Kind, string Id, string Title);

    private IMediaBackend _backend;
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
        RefreshServerHeader();

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

    }

    /// <summary>按用户配置的顺序与可见性整理媒体库。</summary>
    private List<Library> ArrangeLibraries()
    {
        var cfg = _backend.Config;
        var visible = _libraries.Where(l => !cfg.HiddenLibraries.Contains(l.Id)).ToList();
        if (cfg.LibraryOrder.Count == 0) return visible;

        var rank = cfg.LibraryOrder.Select((id, i) => (id, i)).ToDictionary(x => x.id, x => x.i);
        return visible.OrderBy(l => rank.TryGetValue(l.Id, out var r) ? r : int.MaxValue).ToList();
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
            var rows = ArrangeRows(await _backend.GetHomeRowsAsync());
            if (rows.Count == 0)
            {
                ShowEmpty("服务器上还没有可显示的媒体");
                return;
            }

            HideEmpty();

            var settings = ((App)Application.Current).Settings;
            if (settings.DailyRecommend)
            {
                var daily = PickDaily(rows);
                if (daily.Count > 0) ContentPanel.Children.Add(BuildDailyBanner(daily));
            }

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
        control.CardClicked += async (_, clicked) => await OpenCardAsync(clicked);
        return control;
    }

    /// <summary>
    /// 打开卡片。聚合搜索的结果可能来自别的服务器，先切过去再进详情，
    /// 否则详情接口会拿当前服务器的 token 去请求另一台机器。
    /// </summary>
    private async Task OpenCardAsync(MediaCard card)
    {
        var app = (App)Application.Current;
        if (!string.IsNullOrEmpty(card.ServerId) && card.ServerId != _backend.Config.Id)
        {
            var target = app.Settings.FindServer(card.ServerId!);
            if (target != null)
            {
                SwitchToServer(target);
                return;
            }
        }
        await ShowDetailAsync(card.Id, card.Title);
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
            var app = (App)Application.Current;
            List<MediaCard> items;

            if (app.Settings.AggregateSearch && app.Settings.Servers.Count > 1)
            {
                var outcome = await AggregateSearch.SearchAsync(query, app.Settings.Servers);
                items = outcome.Cards;

                PageTitle.Text = $"搜索「{query}」· 聚合 {app.Settings.Servers.Count} 台服务器";
                if (outcome.Failures.Count > 0)
                {
                    ContentPanel.Children.Add(new TextBlock
                    {
                        Text = string.Join("；", outcome.Failures.Select(f => $"{f.Server}：{f.Error}")),
                        FontSize = 11,
                        Foreground = (Brush)FindResource("SubTextBrush"),
                        TextWrapping = TextWrapping.Wrap,
                        Margin = new Thickness(0, 0, 0, 10),
                    });
                }
            }
            else
            {
                items = await _backend.SearchAsync(query);
            }

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

    // 详情页的版本 / 音轨 / 字幕选择
    private MediaDetail? _detailCache;
    private PlaybackOptions? _options;
    private string? _sourceId;
    private int? _audioIndex;
    private int? _subtitleIndex;

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
            _detailCache = detail;
            _current = new Navigation(ViewKind.Detail, itemId, detail.Title);
            PageTitle.Text = detail.Title;

            _sourceId = null;
            _audioIndex = null;
            _subtitleIndex = null;
            _options = null;
            try
            {
                _options = await _backend.GetPlaybackOptionsAsync(detail.PlayTargetId);
                _audioIndex = _options.DefaultAudioIndex;
                _subtitleIndex = _options.DefaultSubtitleIndex;
            }
            catch
            {
                // 拿不到轨道清单就不显示选择项
            }

            HideEmpty();
            var detailRoot = BuildDetail(detail);
            ContentPanel.Children.Add(detailRoot);
            _ = EnrichDetailAsync(detail, detailRoot);
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

        var optionBar = BuildPlaybackOptionBar();
        if (optionBar != null)
        {
            info.Children.Add(optionBar);
        }

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
                    row.Click += async (_, _) => await ShowEpisodeAsync(captured.Id, detail.Title);
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

    /// <summary>详情页的「版本 / 音轨 / 字幕」下拉选择条。</summary>
    private UIElement? BuildPlaybackOptionBar()
    {
        var options = _options;
        if (options == null) return null;
        if (!options.HasVersions && !options.HasAudioChoice && !options.HasSubtitleChoice) return null;

        var bar = new WrapPanel { Margin = new Thickness(0, 16, 0, 0) };

        bar.Children.Add(new TextBlock
        {
            Text = "播放设置",
            Foreground = (Brush)FindResource("SubTextBrush"),
            FontSize = 12,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 12, 0),
        });

        if (options.HasVersions)
        {
            var box = new ComboBox
            {
                Width = 260,
                Margin = new Thickness(0, 0, 12, 0),
                ItemsSource = options.Versions.Select(v => $"{v.Name}   {v.Summary}").ToList(),
                SelectedIndex = Math.Max(0, options.Versions.FindIndex(v => v.Id == _sourceId)),
            };
            box.SelectionChanged += async (_, _) =>
            {
                if (box.SelectedIndex < 0 || box.SelectedIndex >= options.Versions.Count) return;
                _sourceId = options.Versions[box.SelectedIndex].Id;
                // 换版本后音轨/字幕清单会变，重新拉一次
                try
                {
                    _options = await _backend.GetPlaybackOptionsAsync(_current.Id, _sourceId);
                    _audioIndex = _options.DefaultAudioIndex;
                    _subtitleIndex = _options.DefaultSubtitleIndex;
                    // 原地重绘，不污染返回栈
                    if (_detailCache != null)
                    {
                        ContentPanel.Children.Clear();
                        ContentPanel.Children.Add(BuildDetail(_detailCache));
                    }
                }
                catch
                {
                }
            };
            bar.Children.Add(box);
        }

        if (options.HasAudioChoice)
        {
            var box = new ComboBox
            {
                Width = 200,
                Margin = new Thickness(0, 0, 12, 0),
                ItemsSource = options.AudioTracks.Select(t =>
                    $"{t.Label}{(t.Codec != null ? " · " + t.Codec.ToUpperInvariant() : "")}").ToList(),
                SelectedIndex = Math.Max(0, options.AudioTracks.FindIndex(t => t.Index == _audioIndex)),
            };
            box.SelectionChanged += (_, _) =>
            {
                if (box.SelectedIndex >= 0 && box.SelectedIndex < options.AudioTracks.Count)
                    _audioIndex = options.AudioTracks[box.SelectedIndex].Index;
            };
            bar.Children.Add(box);
        }

        if (options.HasSubtitleChoice)
        {
            var items = new List<string> { "关闭字幕" };
            items.AddRange(options.SubtitleTracks.Select(t =>
                $"{t.Label} · {t.CodecLabel}{(t.ImageBased ? "（需转码烧录）" : "")}"));
            var box = new ComboBox
            {
                Width = 220,
                ItemsSource = items,
                SelectedIndex = _subtitleIndex == null
                    ? 0
                    : Math.Max(0, options.SubtitleTracks.FindIndex(t => t.Index == _subtitleIndex) + 1),
            };
            box.SelectionChanged += (_, _) =>
            {
                _subtitleIndex = box.SelectedIndex <= 0
                    ? null
                    : options.SubtitleTracks[box.SelectedIndex - 1].Index;
            };
            bar.Children.Add(box);
        }

        return bar;
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

            var chosenSubtitle = _options?.SubtitleTracks.FirstOrDefault(t => t.Index == _subtitleIndex);
            var player = new PlayerWindow(
                _backend, itemId, detail.Title, queue,
                _sourceId, _audioIndex, _subtitleIndex, chosenSubtitle?.ImageBased == true);
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

    /// <summary>从单集详情页直接开播（没有 MediaDetail 上下文时用）。</summary>
    private async Task StartPlaybackAsync(string itemId, bool fromStart = false)
    {
        try
        {
            SetBusy(true);
            var player = new PlayerWindow(
                _backend, itemId, PageTitle.Text, new List<Episode>(),
                null, null, null, false,
                fromStart ? -1 : 0);
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
            case ViewKind.Libraries:
                await ShowLibrariesAsync();
                break;
            case ViewKind.Episode:
                await ShowEpisodeAsync(previous.Id, previous.Title);
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
        LibrariesNav.IsChecked = false;
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

    // ============================================================ 服务器菜单

    private void RefreshServerHeader()
    {
        var cfg = _backend.Config;
        ServerText.Text = cfg.Name;

        var parts = new List<string> { cfg.Kind.DisplayName() };
        if (!string.IsNullOrWhiteSpace(cfg.ActiveLine?.Name)) parts.Add(cfg.ActiveLine!.Name!);
        if (!string.IsNullOrWhiteSpace(cfg.ActiveUser?.Username)) parts.Add(cfg.ActiveUser!.Username);
        ServerSubText.Text = string.Join(" · ", parts);

        if (cfg.IconIsUrl)
        {
            ServerIconText.Visibility = Visibility.Collapsed;
            ServerIconImage.Visibility = Visibility.Visible;
            LoadImageInto(ServerIconImage, cfg.IconUrl);
        }
        else
        {
            ServerIconImage.Visibility = Visibility.Collapsed;
            ServerIconText.Visibility = Visibility.Visible;
            ServerIconText.Text = cfg.IconText;
        }
    }

    private void OnServerButtonClick(object sender, RoutedEventArgs e)
    {
        var app = (App)Application.Current;
        var menu = new ContextMenu { PlacementTarget = ServerButton, Placement = PlacementMode.Bottom };

        foreach (var cfg in app.Settings.Servers)
        {
            var isCurrent = cfg.Id == _backend.Config.Id;
            var item = new MenuItem
            {
                Header = (isCurrent ? "✓ " : "   ") + cfg.Name,
                ToolTip = cfg.Summary,
                FontWeight = isCurrent ? FontWeights.SemiBold : FontWeights.Normal,
            };
            var captured = cfg;
            item.Click += (_, _) => SwitchToServer(captured);
            menu.Items.Add(item);
        }

        menu.Items.Add(new Separator());
        menu.Items.Add(MenuItemFor("媒体库", async () => await ShowLibrariesAsync()));
        menu.Items.Add(MenuItemFor("服务器设置…", () =>
        {
            new ServerWindow { Owner = this }.ShowDialog();
            ReloadBackend();
            _ = ShowHomeAsync();
        }));
        menu.Items.Add(MenuItemFor("添加服务器…", () =>
        {
            var login = new LoginWindow();
            login.Show();
            Close();
        }));
        menu.Items.Add(MenuItemFor("应用设置…", () =>
        {
            new SettingsWindow { Owner = this }.ShowDialog();
        }));
        menu.Items.Add(new Separator());
        menu.Items.Add(MenuItemFor("退出登录", OnSignOutClick));

        menu.IsOpen = true;
    }

    private static MenuItem MenuItemFor(string header, Action action)
    {
        var item = new MenuItem { Header = header };
        item.Click += (_, _) => action();
        return item;
    }

    private static MenuItem MenuItemFor(string header, RoutedEventHandler handler)
    {
        var item = new MenuItem { Header = header };
        item.Click += handler;
        return item;
    }

    private void SwitchToServer(ServerConfig config)
    {
        if (config.Id == _backend.Config.Id) return;
        ((App)Application.Current).SwitchServer(config);
        var main = new MainWindow();
        Application.Current.MainWindow = main;
        main.Show();
        Close();
    }

    // ============================================================ 媒体库页（开关 / 排序）

    private async void OnLibrariesNav(object sender, RoutedEventArgs e)
    {
        if (_current.Kind == ViewKind.Libraries) return;
        await ShowLibrariesAsync();
    }

    private async Task ShowLibrariesAsync()
    {
        if (_busy) return;
        SetBusy(true);
        ResetNavSelection(LibrariesNav);
        _history.Push(_current);
        _current = new Navigation(ViewKind.Libraries, "", "媒体库");
        PageTitle.Text = "媒体库";
        BackButton.Visibility = Visibility.Visible;
        ContentPanel.Children.Clear();

        try
        {
            if (_libraries.Count == 0) await LoadLibrariesAsync();

            HideEmpty();
            ContentPanel.Children.Add(new TextBlock
            {
                Text = "关掉的媒体库不会出现在首页；上下箭头调整首页里的顺序",
                Foreground = (Brush)FindResource("SubTextBrush"),
                FontSize = 12,
                Margin = new Thickness(0, 0, 0, 14),
            });

            var list = new StackPanel();
            foreach (var library in _libraries)
            {
                list.Children.Add(BuildLibraryRow(library));
            }
            ContentPanel.Children.Add(list);
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

    private UIElement BuildLibraryRow(Library library)
    {
        var cfg = _backend.Config;
        var hidden = cfg.HiddenLibraries.Contains(library.Id);

        var border = new Border
        {
            CornerRadius = new CornerRadius(12),
            Background = (Brush)FindResource("CardBrush"),
            BorderBrush = (Brush)FindResource("GlassBorderBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(16, 12, 16, 12),
            Margin = new Thickness(0, 0, 0, 8),
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(new TextBlock
        {
            Text = library.Name,
            FontSize = 15,
            FontWeight = FontWeights.SemiBold,
            Foreground = hidden ? (Brush)FindResource("FaintTextBrush") : (Brush)FindResource("TextBrush"),
        });
        text.Children.Add(new TextBlock
        {
            Text = library.CollectionType ?? "混合内容",
            FontSize = 11,
            Foreground = (Brush)FindResource("SubTextBrush"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetColumn(text, 0);
        grid.Children.Add(text);

        var actions = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

        actions.Children.Add(SmallButton("▲", () => MoveLibrary(library.Id, -1)));
        actions.Children.Add(SmallButton("▼", () => MoveLibrary(library.Id, 1)));

        var toggle = new CheckBox
        {
            IsChecked = !hidden,
            Content = "显示",
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(12, 0, 0, 0),
            Foreground = (Brush)FindResource("TextBrush"),
        };
        toggle.Checked += (_, _) => SetLibraryHidden(library.Id, false);
        toggle.Unchecked += (_, _) => SetLibraryHidden(library.Id, true);
        actions.Children.Add(toggle);

        Grid.SetColumn(actions, 1);
        grid.Children.Add(actions);

        border.Child = grid;
        border.MouseEnter += (_, _) => border.BorderBrush = (Brush)FindResource("AccentBrush");
        border.MouseLeave += (_, _) => border.BorderBrush = (Brush)FindResource("GlassBorderBrush");
        return border;
    }

    private Button SmallButton(string text, Action action)
    {
        var button = new Button
        {
            Content = text,
            Style = (Style)FindResource("GhostButton"),
            Padding = new Thickness(8, 2, 8, 2),
            Margin = new Thickness(0, 0, 4, 0),
            FontSize = 12,
        };
        button.Click += (_, _) => action();
        return button;
    }

    private void SetLibraryHidden(string libraryId, bool hidden)
    {
        var app = (App)Application.Current;
        var cfg = _backend.Config.Clone();
        if (hidden)
        {
            if (!cfg.HiddenLibraries.Contains(libraryId)) cfg.HiddenLibraries.Add(libraryId);
        }
        else
        {
            cfg.HiddenLibraries.Remove(libraryId);
        }
        app.Settings.UpsertServer(cfg);
        app.Settings.Save();
        app.SwitchServer(cfg);
        ReloadBackend();
    }

    /// <summary>配置变更后重新取一次后端，否则当前窗口还拿着旧 Config 在跑。</summary>
    private void ReloadBackend()
    {
        var app = (App)Application.Current;
        if (app.Backend != null) _backend = app.Backend;
        ImageLoader.Headers = _backend.ImageHeaders;
        RefreshServerHeader();
    }

    private void MoveLibrary(string libraryId, int delta)
    {
        var app = (App)Application.Current;
        var cfg = _backend.Config.Clone();
        var order = cfg.LibraryOrder.ToList();
        foreach (var lib in _libraries)
            if (!order.Contains(lib.Id)) order.Add(lib.Id);

        var index = order.IndexOf(libraryId);
        if (index < 0) return;
        var target = Math.Clamp(index + delta, 0, order.Count - 1);
        if (target == index) return;

        order.RemoveAt(index);
        order.Insert(target, libraryId);
        cfg.LibraryOrder = order;

        app.Settings.UpsertServer(cfg);
        app.Settings.Save();
        app.SwitchServer(cfg);
        ReloadBackend();
        _ = ShowLibrariesAsync();
    }

    // ============================================================ 首页整理与每日推荐

    private List<HomeRow> ArrangeRows(List<HomeRow> rows)
    {
        var cfg = _backend.Config;
        var visible = rows.Where(r => r.LibraryId == null || !cfg.HiddenLibraries.Contains(r.LibraryId)).ToList();
        if (cfg.LibraryOrder.Count == 0) return visible;

        var rank = cfg.LibraryOrder.Select((id, i) => (id, i)).ToDictionary(x => x.id, x => x.i);
        var head = visible.Where(r => r.LibraryId == null).ToList();
        var tail = visible.Where(r => r.LibraryId != null)
            .OrderBy(r => rank.TryGetValue(r.LibraryId!, out var v) ? v : int.MaxValue);
        return head.Concat(tail).ToList();
    }

    private static List<MediaCard> PickDaily(List<HomeRow> rows)
    {
        var pool = new Dictionary<string, MediaCard>();
        foreach (var card in rows.Where(r => r.Kind != RowKind.Continue)
                     .SelectMany(r => r.Items)
                     .Where(c => !string.IsNullOrWhiteSpace(c.BackdropUrl)))
        {
            pool.TryAdd(card.Id, card);
        }

        var list = pool.Values.OrderByDescending(c => c.Rating ?? 0).Take(12).ToList();
        if (list.Count == 0) return list;

        var day = DateTime.Now.DayOfYear;
        var start = day % list.Count;
        return list.Skip(start).Concat(list.Take(start)).Take(6).ToList();
    }

    private UIElement BuildDailyBanner(List<MediaCard> items)
    {
        var host = new Grid { Height = 260, Margin = new Thickness(0, 0, 0, 26) };

        var border = new Border
        {
            CornerRadius = new CornerRadius(18),
            ClipToBounds = true,
            Background = (Brush)FindResource("CardBrush"),
        };
        host.Children.Add(border);

        var image = new Image { Stretch = Stretch.UniformToFill };
        border.Child = image;

        var overlay = new Border
        {
            Background = new LinearGradientBrush(
                Color.FromArgb(0, 0, 0, 0),
                Color.FromArgb(235, 0, 0, 0),
                new Point(0.5, 0),
                new Point(0.5, 1)),
        };
        host.Children.Add(overlay);

        var textStack = new StackPanel
        {
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(26, 0, 26, 22),
        };
        var titleText = new TextBlock { FontSize = 26, FontWeight = FontWeights.Bold, Foreground = Brushes.White };
        var metaText = new TextBlock
        {
            FontSize = 12.5,
            Foreground = new SolidColorBrush(Color.FromArgb(220, 255, 255, 255)),
            Margin = new Thickness(0, 6, 0, 0),
        };
        textStack.Children.Add(new TextBlock
        {
            Text = "今日推荐 · 每天换一批",
            FontSize = 12,
            Foreground = (Brush)FindResource("AccentBrush"),
            Margin = new Thickness(0, 0, 0, 6),
        });
        textStack.Children.Add(titleText);
        textStack.Children.Add(metaText);
        host.Children.Add(textStack);

        var dots = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(0, 0, 26, 26),
        };
        host.Children.Add(dots);

        var index = 0;

        void Render()
        {
            var card = items[index];
            LoadImageInto(image, card.BackdropUrl ?? card.PosterUrl);
            titleText.Text = card.Title;
            var meta = new List<string>();
            if (card.Rating is > 0) meta.Add($"★ {card.Rating:0.0}");
            if (card.Year != null) meta.Add(card.Year.ToString()!);
            if (!string.IsNullOrWhiteSpace(card.Subtitle)) meta.Add(card.Subtitle!);
            metaText.Text = string.Join(" · ", meta);

            dots.Children.Clear();
            for (var i = 0; i < items.Count; i++)
            {
                dots.Children.Add(new Border
                {
                    Width = i == index ? 8 : 6,
                    Height = i == index ? 8 : 6,
                    CornerRadius = new CornerRadius(4),
                    Margin = new Thickness(3, 0, 3, 0),
                    Background = i == index ? (Brush)FindResource("AccentBrush") : new SolidColorBrush(Color.FromArgb(70, 255, 255, 255)),
                });
            }
        }

        Render();

        var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(7) };
        timer.Tick += (_, _) =>
        {
            index = (index + 1) % items.Count;
            Render();
        };
        timer.Start();
        host.Unloaded += (_, _) => timer.Stop();

        border.Cursor = Cursors.Hand;
        border.MouseLeftButtonUp += async (_, _) => await ShowDetailAsync(items[index].Id, items[index].Title);
        return host;
    }

    // ============================================================ 单集详情

    private async Task ShowEpisodeAsync(string episodeId, string? fallbackTitle)
    {
        if (_busy) return;
        SetBusy(true);
        _history.Push(_current);
        PageTitle.Text = fallbackTitle ?? "单集";
        BackButton.Visibility = Visibility.Visible;
        ContentPanel.Children.Clear();

        try
        {
            var episode = await _backend.GetEpisodeAsync(episodeId);
            _current = new Navigation(ViewKind.Episode, episodeId, episode.Title);
            PageTitle.Text = episode.Title;
            HideEmpty();
            ContentPanel.Children.Add(BuildEpisodePage(episode, fallbackTitle));
        }
        catch (Exception ex)
        {
            ShowEmpty($"加载单集失败：{ex.Message}");
        }
        finally
        {
            SetBusy(false);
        }
    }

    private UIElement BuildEpisodePage(Episode episode, string? fallbackTitle)
    {
        var root = new StackPanel();

        var hero = new Border
        {
            Height = 300,
            CornerRadius = new CornerRadius(16),
            ClipToBounds = true,
            Background = (Brush)FindResource("CardBrush"),
            Margin = new Thickness(0, 0, 0, 20),
        };
        var heroGrid = new Grid();
        var heroImage = new Image { Stretch = Stretch.UniformToFill };
        heroGrid.Children.Add(heroImage);
        LoadImageInto(heroImage, episode.BackdropUrl ?? episode.ImageUrl);
        heroGrid.Children.Add(new Border
        {
            Background = new LinearGradientBrush(
                Color.FromArgb(0, 0, 0, 0),
                Color.FromArgb(240, 0, 0, 0),
                new Point(0.5, 0.1),
                new Point(0.5, 1)),
        });

        var heroText = new StackPanel
        {
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(26, 0, 26, 22),
        };
        heroText.Children.Add(new TextBlock
        {
            Text = episode.Label,
            Foreground = (Brush)FindResource("AccentBrush"),
            FontSize = 12.5,
            FontWeight = FontWeights.SemiBold,
        });
        heroText.Children.Add(new TextBlock
        {
            Text = episode.Title,
            Foreground = Brushes.White,
            FontSize = 26,
            FontWeight = FontWeights.Bold,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 6, 0, 0),
        });
        heroGrid.Children.Add(heroText);
        hero.Child = heroGrid;
        root.Children.Add(hero);

        var actions = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 18) };
        var play = new Button
        {
            Content = episode.Progress > 0.01 ? "继续播放" : "播放",
            Style = (Style)FindResource("PrimaryButton"),
        };
        play.Click += async (_, _) => await StartPlaybackAsync(episode.Id);
        actions.Children.Add(play);

        if (episode.Progress > 0.01)
        {
            var restart = new Button
            {
                Content = "从头播放",
                Style = (Style)FindResource("GhostButton"),
                Margin = new Thickness(10, 0, 0, 0),
            };
            restart.Click += async (_, _) => await StartPlaybackAsync(episode.Id, fromStart: true);
            actions.Children.Add(restart);
        }

        if (!string.IsNullOrWhiteSpace(episode.SeriesId))
        {
            var series = new Button
            {
                Content = "查看整部剧集",
                Style = (Style)FindResource("GhostButton"),
                Margin = new Thickness(10, 0, 0, 0),
            };
            var sid = episode.SeriesId!;
            var sname = episode.SeriesName ?? fallbackTitle ?? "";
            series.Click += async (_, _) => await ShowDetailAsync(sid, sname);
            actions.Children.Add(series);
        }
        root.Children.Add(actions);

        var info = new Border
        {
            CornerRadius = new CornerRadius(14),
            Background = (Brush)FindResource("CardBrush"),
            BorderBrush = (Brush)FindResource("GlassBorderBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(18),
            Margin = new Thickness(0, 0, 0, 18),
        };
        var infoStack = new StackPanel();
        infoStack.Children.Add(new TextBlock
        {
            Text = "本集信息",
            FontSize = 14,
            FontWeight = FontWeights.SemiBold,
            Margin = new Thickness(0, 0, 0, 8),
        });
        void AddLine(string label, string? value)
        {
            if (string.IsNullOrWhiteSpace(value)) return;
            var row = new Grid { Margin = new Thickness(0, 3, 0, 3) };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(84) });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            var l = new TextBlock { Text = label, FontSize = 12, Foreground = (Brush)FindResource("SubTextBrush") };
            var v = new TextBlock { Text = value, FontSize = 12, TextWrapping = TextWrapping.Wrap };
            Grid.SetColumn(l, 0);
            Grid.SetColumn(v, 1);
            row.Children.Add(l);
            row.Children.Add(v);
            infoStack.Children.Add(row);
        }
        AddLine("所属剧集", episode.SeriesName ?? fallbackTitle);
        AddLine("季集", episode.Label);
        AddLine("播出日期", episode.AirDate);
        AddLine("观看状态", episode.Played ? "已看完" : null);
        info.Child = infoStack;
        root.Children.Add(info);

        root.Children.Add(new TextBlock
        {
            Text = "简介",
            FontSize = 15,
            FontWeight = FontWeights.SemiBold,
            Margin = new Thickness(0, 0, 0, 6),
        });
        root.Children.Add(new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(episode.Overview) ? "这一集还没有简介" : episode.Overview!,
            TextWrapping = TextWrapping.Wrap,
            FontSize = 13,
            LineHeight = 21,
            Foreground = (Brush)FindResource("SubTextBrush"),
        });

        return root;
    }

    // ============================================================ 详情页补充：评分与类似作品

    private async Task EnrichDetailAsync(MediaDetail detail, UIElement container)
    {
        if (container is not Panel root) return;
        var app = (App)Application.Current;
        var settings = app.Settings;

        ExternalRatings? ratings = null;
        try
        {
            ratings = await RatingsService.FetchAsync(
                detail.Title,
                detail.OriginalTitle,
                detail.Year,
                detail.Type == MediaType.Series,
                settings.TmdbApiKey,
                settings.DoubanRating);
        }
        catch
        {
            // 评分拿不到就不显示
        }

        if (ratings is { IsEmpty: false })
        {
            var bar = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Margin = new Thickness(0, 4, 0, 16),
            };
            if (ratings.Tmdb != null) bar.Children.Add(RatingBadge("TMDB", ratings.Tmdb.Value, ratings.TmdbVotes));
            if (ratings.Douban != null) bar.Children.Add(RatingBadge("豆瓣", ratings.Douban.Value, ratings.DoubanVotes));
            if (root.Children.Count > 1) root.Children.Insert(1, bar);
            else root.Children.Add(bar);
        }

        List<MediaCard> similar;
        try
        {
            similar = await _backend.GetSimilarAsync(detail.Id, 14);
        }
        catch
        {
            return;
        }
        if (similar.Count == 0) return;

        var section = new StackPanel { Margin = new Thickness(0, 22, 0, 0) };
        section.Children.Add(new TextBlock
        {
            Text = "更多类似作品",
            FontSize = 17,
            FontWeight = FontWeights.SemiBold,
            Margin = new Thickness(0, 0, 0, 12),
        });

        var scroller = new ScrollViewer
        {
            HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
            VerticalScrollBarVisibility = ScrollBarVisibility.Disabled,
        };
        var strip = new StackPanel { Orientation = Orientation.Horizontal };
        foreach (var card in similar)
        {
            var poster = new PosterCard { Width = 132, Margin = new Thickness(0, 0, 12, 0) };
            poster.Bind(card);
            poster.CardClicked += async (_, clicked) => await ShowDetailAsync(clicked.Id, clicked.Title);
            strip.Children.Add(poster);
        }
        scroller.Content = strip;
        section.Children.Add(scroller);
        root.Children.Add(section);
    }

    private UIElement RatingBadge(string source, double score, int? votes)
    {
        var border = new Border
        {
            CornerRadius = new CornerRadius(10),
            Background = (Brush)FindResource("CardBrush"),
            BorderBrush = new SolidColorBrush(Color.FromArgb(90, 245, 196, 81)),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(12, 7, 12, 7),
            Margin = new Thickness(0, 0, 10, 0),
        };
        var panel = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        panel.Children.Add(new TextBlock
        {
            Text = source,
            FontSize = 11,
            Foreground = (Brush)FindResource("SubTextBrush"),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 8, 0),
        });
        panel.Children.Add(new TextBlock
        {
            Text = score.ToString("0.0"),
            FontSize = 17,
            FontWeight = FontWeights.Bold,
            Foreground = (Brush)FindResource("GoldBrush"),
        });
        if (votes is > 0)
        {
            panel.Children.Add(new TextBlock
            {
                Text = votes >= 10000 ? $" {votes / 10000}万" : $" {votes}",
                FontSize = 10,
                Foreground = (Brush)FindResource("SubTextBrush"),
                VerticalAlignment = VerticalAlignment.Center,
            });
        }
        border.Child = panel;
        return border;
    }
}
