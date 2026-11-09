using RainPlayer.Core.Models;

namespace RainPlayer.Core.Backends;

/// <summary>
/// 多线路自动切换的装饰器。
///
/// 当前线路出现网络层故障（连不上、超时、DNS 失败）时自动换下一条重试；
/// 服务器明确返回的 HTTP 错误（401、404）不触发切换，因为换线路也解决不了。
/// 切换成功后通过回调把新配置交给上层落盘。
/// </summary>
public sealed class FailoverBackend : IMediaBackend
{
    private readonly Action<ServerConfig>? _onLineChanged;
    private IMediaBackend _inner;
    private ServerConfig _config;

    public FailoverBackend(ServerConfig config, Action<ServerConfig>? onLineChanged = null)
    {
        _config = config;
        _onLineChanged = onLineChanged;
        _inner = BackendFactory.Create(config);
    }

    public ServerConfig Config => _config;

    public IReadOnlyDictionary<string, string> ImageHeaders => _inner.ImageHeaders;

    /// <summary>只有「网络不可达」类错误才值得换线路。</summary>
    private static bool IsTransient(Exception e) => e switch
    {
        HttpRequestException => true,
        TaskCanceledException => true,
        TimeoutException => true,
        System.Net.Sockets.SocketException => true,
        _ => e.InnerException != null && IsTransient(e.InnerException),
    };

    private async Task<T> WithFailoverAsync<T>(Func<IMediaBackend, Task<T>> action)
    {
        var tried = new HashSet<string>();
        Exception? last = null;

        while (true)
        {
            tried.Add(_config.ActiveLineId);
            try
            {
                return await action(_inner).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception e) when (IsTransient(e))
            {
                last = e;
                var next = _config.Lines.FirstOrDefault(l => !tried.Contains(l.Id));
                if (next == null) break;

                _config = _config.WithLine(next.Id);
                _inner = BackendFactory.Create(_config);
                _onLineChanged?.Invoke(_config);
            }
        }

        throw last ?? new InvalidOperationException("所有线路都不可用");
    }

    private async Task WithFailoverAsync(Func<IMediaBackend, Task> action)
    {
        await WithFailoverAsync<object?>(async b =>
        {
            await action(b).ConfigureAwait(false);
            return null;
        }).ConfigureAwait(false);
    }

    public Task<ServerConfig> AuthenticateAsync(string username, string password, CancellationToken ct = default)
        => WithFailoverAsync(b => b.AuthenticateAsync(username, password, ct));

    public Task<List<Library>> GetLibrariesAsync(CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetLibrariesAsync(ct));

    public Task<List<HomeRow>> GetHomeRowsAsync(CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetHomeRowsAsync(ct));

    public Task<PagedItems> GetItemsAsync(string libraryId, int startIndex, int limit, CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetItemsAsync(libraryId, startIndex, limit, ct));

    public Task<List<MediaCard>> SearchAsync(string query, CancellationToken ct = default)
        => WithFailoverAsync(b => b.SearchAsync(query, ct));

    public Task<MediaDetail> GetDetailAsync(string itemId, CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetDetailAsync(itemId, ct));

    public Task<List<MediaCard>> GetSimilarAsync(string itemId, int limit = 12, CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetSimilarAsync(itemId, limit, ct));

    public Task<Episode> GetEpisodeAsync(string itemId, CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetEpisodeAsync(itemId, ct));

    public Task<List<Season>> GetSeasonsAsync(string seriesId, CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetSeasonsAsync(seriesId, ct));

    public Task<List<Episode>> GetEpisodesAsync(string seriesId, string seasonId, CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetEpisodesAsync(seriesId, seasonId, ct));

    public Task<PlaybackPlan> GetPlaybackAsync(
        string itemId, long startMs, bool forceTranscode = false, string? sourceId = null,
        int? audioIndex = null, int? subtitleIndex = null, bool burnSubtitle = false,
        CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetPlaybackAsync(itemId, startMs, forceTranscode, sourceId, audioIndex, subtitleIndex, burnSubtitle, ct));

    public Task<PlaybackOptions> GetPlaybackOptionsAsync(string itemId, string? sourceId = null, CancellationToken ct = default)
        => WithFailoverAsync(b => b.GetPlaybackOptionsAsync(itemId, sourceId, ct));

    // 上报类接口不做线路切换：换了线路 PlaySessionId 对不上，反而会把观看记录搞乱。
    public async Task ReportStartAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default)
    {
        try { await _inner.ReportStartAsync(plan, positionMs, ct).ConfigureAwait(false); } catch { }
    }

    public async Task ReportProgressAsync(PlaybackPlan plan, long positionMs, bool paused, CancellationToken ct = default)
    {
        try { await _inner.ReportProgressAsync(plan, positionMs, paused, ct).ConfigureAwait(false); } catch { }
    }

    public async Task ReportStopAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default)
    {
        try { await _inner.ReportStopAsync(plan, positionMs, ct).ConfigureAwait(false); } catch { }
    }

    public async Task PersistPositionAsync(PlaybackPlan plan, long positionMs, bool completed, CancellationToken ct = default)
    {
        try { await _inner.PersistPositionAsync(plan, positionMs, completed, ct).ConfigureAwait(false); } catch { }
    }

    public Task SetFavoriteAsync(string itemId, bool favorite, CancellationToken ct = default)
        => WithFailoverAsync(b => b.SetFavoriteAsync(itemId, favorite, ct));

    public Task SetWatchedAsync(string itemId, bool watched, CancellationToken ct = default)
        => WithFailoverAsync(b => b.SetWatchedAsync(itemId, watched, ct));

    public Task<bool> DownloadSubtitleAsync(SubtitleTrack track, string destPath, CancellationToken ct = default)
        => WithFailoverAsync(b => b.DownloadSubtitleAsync(track, destPath, ct));
}
