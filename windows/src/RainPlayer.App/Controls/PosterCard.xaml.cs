using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using RainPlayer.App.Services;
using RainPlayer.Core.Models;

namespace RainPlayer.App.Controls;

public partial class PosterCard : UserControl
{
    public MediaCard? Card { get; private set; }

    public event EventHandler<MediaCard>? CardClicked;

    public PosterCard()
    {
        InitializeComponent();

        MouseLeftButtonUp += (_, _) =>
        {
            if (Card != null) CardClicked?.Invoke(this, Card);
        };

        MouseEnter += (_, _) =>
        {
            HoverLayer.Visibility = Visibility.Visible;
            CardRoot.Background = (Brush)FindResource("CardHoverBrush");
        };
        MouseLeave += (_, _) =>
        {
            HoverLayer.Visibility = Visibility.Collapsed;
            CardRoot.Background = (Brush)FindResource("CardBrush");
        };
    }

    public void Bind(MediaCard card)
    {
        Card = card;
        TitleText.Text = card.Title;
        SubText.Text = BuildSubtitle(card);
        SubText.Visibility = string.IsNullOrEmpty(SubText.Text) ? Visibility.Collapsed : Visibility.Visible;

        WatchedBadge.Visibility = card.Played ? Visibility.Visible : Visibility.Collapsed;

        if (card.Type == MediaType.Series && card.EpisodeCount is > 0)
        {
            EpisodeBadgeText.Text = $"{card.EpisodeCount}";
            EpisodeBadge.Visibility = Visibility.Visible;
        }
        else
        {
            EpisodeBadge.Visibility = Visibility.Collapsed;
        }

        if (card.Rating is > 0)
        {
            RatingBadgeText.Text = $"{card.Rating:0.0}";
            RatingBadge.Visibility = Visibility.Visible;
        }
        else
        {
            RatingBadge.Visibility = Visibility.Collapsed;
        }

        var progress = Math.Clamp(card.Progress, 0, 1);
        if (progress > 0.01)
        {
            ProgressBar.Visibility = Visibility.Visible;
            ProgressFill.Width = 168 * progress;
        }
        else
        {
            ProgressBar.Visibility = Visibility.Collapsed;
        }

        var cached = ImageLoader.GetCached(card.PosterUrl ?? "");
        if (cached != null)
        {
            Poster.Source = cached;
        }
        else if (!string.IsNullOrEmpty(card.PosterUrl))
        {
            _ = LoadPosterAsync(card.PosterUrl!);
        }
    }

    private async Task LoadPosterAsync(string url)
    {
        var image = await ImageLoader.LoadAsync(url);
        if (image == null) return;

        // 卡片可能已被回收复用，确认还是同一张再赋值
        if (Card?.PosterUrl == url)
            Poster.Source = image;
    }

    private static string BuildSubtitle(MediaCard card)
    {
        var parts = new List<string>();
        if (!string.IsNullOrWhiteSpace(card.Subtitle)) parts.Add(card.Subtitle!);
        if (card.Year is > 0 && !parts.Contains(card.Year.ToString()!)) parts.Add(card.Year.ToString()!);
        return string.Join(" · ", parts);
    }
}
