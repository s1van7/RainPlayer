using RainPlayer.Core.Models;

namespace RainPlayer.Core.Backends;

/// <summary>
/// 统一媒体后端接口。Emby / Jellyfin / 飞牛影视 各实现一份，
/// 上层 UI 与播放器只依赖这个接口。
/// </summary>
public interface IMediaBackend
{
    ServerConfig Config { get; }

    /// <summary>登录并返回带 token 的新配置（不落盘，由调用方保存）。</summary>
    Task<ServerConfig> AuthenticateAsync(string username, string password, CancellationToken ct = default);

    Task<List<Library>> GetLibrariesAsync(CancellationToken ct = default);

    Task<List<HomeRow>> GetHomeRowsAsync(CancellationToken ct = default);

    Task<PagedItems> GetItemsAsync(string libraryId, int startIndex, int limit, CancellationToken ct = default);

    Task<List<MediaCard>> SearchAsync(string query, CancellationToken ct = default);

    Task<MediaDetail> GetDetailAsync(string itemId, CancellationToken ct = default);

    /// <summary>「更多类似作品」。服务器不支持时返回空列表，UI 自动隐藏该区块。</summary>
    Task<List<MediaCard>> GetSimilarAsync(string itemId, int limit = 12, CancellationToken ct = default)
        => Task.FromResult(new List<MediaCard>());

    /// <summary>单集详情。默认用 GetDetailAsync 兜底。</summary>
    async Task<Episode> GetEpisodeAsync(string itemId, CancellationToken ct = default)
    {
        var d = await GetDetailAsync(itemId, ct).ConfigureAwait(false);
        return new Episode
        {
            Id = d.Id,
            Title = d.Title,
            Overview = d.Overview,
            ImageUrl = d.PosterUrl,
            BackdropUrl = d.BackdropUrl,
            RuntimeMinutes = d.RuntimeMinutes,
            Played = d.Played,
            Progress = d.Progress,
        };
    }

    Task<List<Season>> GetSeasonsAsync(string seriesId, CancellationToken ct = default);

    Task<List<Episode>> GetEpisodesAsync(string seriesId, string seasonId, CancellationToken ct = default);

    /// <summary>
    /// 生成播放方案。默认走「直通/直链」以保证 4K/8K/HDR/杜比视界与
    /// 全景声音轨不被服务器转码破坏；forceTranscode 为 true 时才请求转码。
    /// </summary>
    Task<PlaybackPlan> GetPlaybackAsync(
        string itemId,
        long startMs,
        bool forceTranscode = false,
        string? sourceId = null,
        int? audioIndex = null,
        int? subtitleIndex = null,
        bool burnSubtitle = false,
        CancellationToken ct = default);

    /// <summary>详情页展示用的版本 / 音轨 / 字幕清单。</summary>
    Task<PlaybackOptions> GetPlaybackOptionsAsync(string itemId, string? sourceId = null, CancellationToken ct = default);

    Task ReportStartAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default);

    Task ReportProgressAsync(PlaybackPlan plan, long positionMs, bool paused, CancellationToken ct = default);

    Task ReportStopAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default);

    /// <summary>
    /// 兜底：直接把观看位置写进用户数据。会话上报丢失时靠这一步保证记录不被清零。
    /// </summary>
    Task PersistPositionAsync(PlaybackPlan plan, long positionMs, bool completed, CancellationToken ct = default)
        => Task.CompletedTask;

    Task SetFavoriteAsync(string itemId, bool favorite, CancellationToken ct = default);

    Task SetWatchedAsync(string itemId, bool watched, CancellationToken ct = default);

    /// <summary>外挂字幕需要鉴权时，把它落地成本地文件；返回 true 表示成功。</summary>
    Task<bool> DownloadSubtitleAsync(SubtitleTrack track, string destPath, CancellationToken ct = default);

    /// <summary>海报等图片需要的请求头（飞牛要鉴权）。</summary>
    IReadOnlyDictionary<string, string> ImageHeaders { get; }
}

public static class BackendFactory
{
    public static IMediaBackend Create(ServerConfig config) => config.Kind switch
    {
        ServerKind.FnOs => new FnOsBackend(config),
        _ => new EmbyBackend(config),
    };

    /// <summary>归一化地址：补协议、去尾部斜杠。</summary>
    public static string NormalizeUrl(string raw)
    {
        var url = (raw ?? "").Trim();
        if (url.Length == 0) return url;
        if (!url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) &&
            !url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
        {
            url = "http://" + url;
        }
        return url.TrimEnd('/');
    }
}
