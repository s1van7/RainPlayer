using System.Text.Json;
using RainPlayer.Core.Json;
using RainPlayer.Core.Net;
using RainPlayer.Core.Models;

namespace RainPlayer.Core.Backends;

/// <summary>
/// 跨服务器聚合搜索。对所有已配置的服务器（各自用当前线路 + 当前账号）并发搜索，
/// 结果合并成一条列表并标注来源。单台失败不影响其它服务器。
/// </summary>
public static class AggregateSearch
{
    public sealed record Outcome(List<MediaCard> Cards, List<(string Server, string Error)> Failures);

    public static async Task<Outcome> SearchAsync(
        string query,
        IEnumerable<ServerConfig> targets,
        CancellationToken ct = default)
    {
        var keyword = (query ?? "").Trim();
        var list = targets.Select(t => t.Normalize()).ToList();
        if (keyword.Length == 0 || list.Count == 0)
            return new Outcome(new List<MediaCard>(), new List<(string, string)>());

        var tasks = list.Select(async cfg =>
        {
            try
            {
                var backend = BackendFactory.Create(cfg);
                var cards = await backend.SearchAsync(keyword, ct).ConfigureAwait(false);
                foreach (var card in cards)
                {
                    card.ServerId = cfg.Id;
                    card.AccountId = cfg.ActiveUserId;
                    card.ServerName = cfg.Name;
                }
                return (cards: cards, failure: ((string, string)?)null);
            }
            catch (Exception e)
            {
                return (cards: new List<MediaCard>(), failure: ((string, string)?)(cfg.Name, e.Message));
            }
        }).ToList();

        var results = await Task.WhenAll(tasks).ConfigureAwait(false);
        var cards = results.SelectMany(r => r.Item1).ToList();
        var failures = results.Where(r => r.Item2 != null).Select(r => r.Item2!.Value).ToList();
        return new Outcome(cards, failures);
    }
}

/// <summary>
/// 外部评分聚合。
///
/// - TMDB：官方 API，需要用户填自己的 v3 Key；没填整块隐藏。
/// - 豆瓣：官方 API 早已关闭，走公开的第三方聚合接口（wmdb），拿不到就静默返回空。
/// </summary>
public static class RatingsService
{
    private const string TmdbBase = "https://api.themoviedb.org/3";
    private const string WmdbSearch = "https://api.wmdb.tv/api/v1/movie/search";

    private const int CacheMax = 200;
    private static readonly Dictionary<string, ExternalRatings> Cache = new();
    private static readonly object CacheLock = new();

    public static void ClearCache()
    {
        lock (CacheLock) Cache.Clear();
    }

    public static async Task<ExternalRatings> FetchAsync(
        string title,
        string? originalTitle,
        int? year,
        bool isTv,
        string tmdbKey,
        bool doubanEnabled,
        CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(title)) return new ExternalRatings();

        var key = $"{title.Trim()}|{year ?? 0}|{isTv}";
        lock (CacheLock)
        {
            if (Cache.TryGetValue(key, out var cached)) return cached;
        }

        ExternalRatings? tmdb = null;
        if (!string.IsNullOrWhiteSpace(tmdbKey))
        {
            try { tmdb = await QueryTmdbAsync(title, originalTitle, year, isTv, tmdbKey.Trim(), ct).ConfigureAwait(false); }
            catch { /* 评分失败不影响详情页 */ }
        }

        ExternalRatings? douban = null;
        if (doubanEnabled)
        {
            try { douban = await QueryDoubanAsync(title, originalTitle, year, ct).ConfigureAwait(false); }
            catch { /* 同上 */ }
        }

        var merged = new ExternalRatings
        {
            Tmdb = tmdb?.Tmdb,
            TmdbVotes = tmdb?.TmdbVotes,
            TmdbId = tmdb?.TmdbId,
            Douban = douban?.Douban,
            DoubanVotes = douban?.DoubanVotes,
            DoubanId = douban?.DoubanId,
        };

        if (!merged.IsEmpty)
        {
            lock (CacheLock)
            {
                if (Cache.Count > CacheMax) Cache.Clear();
                Cache[key] = merged;
            }
        }
        return merged;
    }

    private static async Task<ExternalRatings> QueryTmdbAsync(
        string title, string? originalTitle, int? year, bool isTv, string apiKey, CancellationToken ct)
    {
        var kind = isTv ? "tv" : "movie";
        var yearParam = year == null ? "" : (isTv ? $"&first_air_date_year={year}" : $"&year={year}");

        var queries = new List<string> { title };
        if (!string.IsNullOrWhiteSpace(originalTitle) && originalTitle != title) queries.Add(originalTitle);

        foreach (var q in queries)
        {
            var url = $"{TmdbBase}/search/{kind}?api_key={apiKey}&language=zh-CN&include_adult=false" +
                      $"&query={Uri.EscapeDataString(q)}{yearParam}";
            var text = await Http.GetTextAsync(url, null, ct).ConfigureAwait(false);
            using var doc = JsonExt.Parse(text);
            if (doc == null) continue;

            foreach (var item in doc.RootElement.Arr("results"))
            {
                var vote = item.Dbl("vote_average");
                if (vote is not > 0) continue;
                return new ExternalRatings
                {
                    Tmdb = Math.Round(vote.Value, 1),
                    TmdbVotes = item.Int("vote_count"),
                    TmdbId = item.Int("id"),
                };
            }
        }
        return new ExternalRatings();
    }

    private static async Task<ExternalRatings> QueryDoubanAsync(
        string title, string? originalTitle, int? year, CancellationToken ct)
    {
        var queries = new List<string> { title };
        if (!string.IsNullOrWhiteSpace(originalTitle) && originalTitle != title) queries.Add(originalTitle);

        foreach (var q in queries)
        {
            var url = $"{WmdbSearch}?q={Uri.EscapeDataString(q)}&lang=Cn&limit=8";
            var text = await Http.GetTextAsync(url, null, ct).ConfigureAwait(false);
            using var doc = JsonExt.Parse(text);
            if (doc == null || doc.RootElement.ValueKind != JsonValueKind.Array) continue;

            var candidates = doc.RootElement.EnumerateArray().ToList();
            if (candidates.Count == 0) continue;

            JsonElement? matched = null;
            if (year != null)
            {
                foreach (var c in candidates)
                {
                    if (int.TryParse((c.Str("year") ?? "").PadRight(4).Substring(0, 4), out var y) && y == year)
                    {
                        matched = c;
                        break;
                    }
                }
            }
            matched ??= candidates.OrderByDescending(c => RatingOf(c) ?? 0).First();

            var rating = RatingOf(matched.Value);
            if (rating == null) continue;

            return new ExternalRatings
            {
                Douban = Math.Round(rating.Value, 1),
                DoubanVotes = VotesOf(matched.Value),
                DoubanId = matched.Value.Str("doubanId") ?? matched.Value.Str("douban_id"),
            };
        }
        return new ExternalRatings();
    }

    private static double? RatingOf(JsonElement e)
    {
        var raw = e.Str("doubanRating") ?? e.Str("douban_rating") ?? e.Str("rating") ?? e.Obj("rating").Str("value");
        return double.TryParse(raw, out var v) && v > 0 ? v : null;
    }

    private static int? VotesOf(JsonElement e)
    {
        var n = e.Int("doubanVotes") ?? e.Int("douban_votes");
        if (n != null) return n;
        var raw = e.Str("doubanVotes") ?? e.Str("douban_votes");
        return double.TryParse(raw, out var v) ? (int)v : null;
    }
}
