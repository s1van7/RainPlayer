using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using RainPlayer.Core.Json;
using RainPlayer.Core.Models;
using RainPlayer.Core.Net;

namespace RainPlayer.Core.Backends;

/// <summary>
/// 飞牛影视（fnOS）原生后端。
///
/// 飞牛没有公开 API 文档，这里按照其 Web 客户端 https://nas:5666/v/api/v1/…
/// 的实际协议实现：
///  - 鉴权：Authorization: token + Cookie: Trim-MC-token=token
///  - 客户端标识：x-trim-client: web / x-trim-client-version: 616（缺少会隐藏直播库）
///  - 请求签名 Authx：
///      sign = md5(apiKey_path_nonce_timestamp_bodyMd5_apiSecret)
///      有 body 时 bodyMd5 = md5(原始 JSON 串)，GET 带参时 = md5(参数按 key 排序后的 k=v&...)，
///      否则 md5("")
///  - 响应统一为 {code, msg, data}，code == 0 为成功
/// </summary>
public sealed class FnOsBackend : IMediaBackend
{
    // 飞牛 Web 客户端的公开签名密钥（随客户端下发，非用户凭证）
    private const string ApiKey = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private const string ApiSecret = "16CCEB3D-AB42-077D-36A1-F355324E4237";

    private readonly string _base;
    private string _token;
    private Dictionary<int, string> _genreCache = new();

    public ServerConfig Config { get; }

    public FnOsBackend(ServerConfig config)
    {
        Config = config;
        _base = BackendFactory.NormalizeUrl(config.Url);
        _token = config.Token;
    }

    private Dictionary<string, string> StaticHeaders()
    {
        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["Accept"] = "application/json",
            ["x-trim-client"] = "web",
            ["x-trim-client-version"] = "616",
        };
        if (!string.IsNullOrWhiteSpace(_token))
        {
            headers["Authorization"] = _token;
            headers["Cookie"] = $"Trim-MC-token={_token}";
        }
        return headers;
    }

    public IReadOnlyDictionary<string, string> ImageHeaders => StaticHeaders();

    // ------------------------------------------------------------- 签名

    private Dictionary<string, string> SignHeaders(string path, Dictionary<string, string>? query, string? body)
    {
        var nonce = Random.Shared.Next(100_000, 999_999).ToString();
        var timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds().ToString();

        string payloadMd5;
        if (body != null)
        {
            payloadMd5 = Md5(body);
        }
        else if (query is { Count: > 0 })
        {
            var joined = string.Join("&", query
                .OrderBy(pair => pair.Key, StringComparer.Ordinal)
                .Select(pair => $"{pair.Key}={pair.Value}"));
            payloadMd5 = Md5(joined);
        }
        else
        {
            payloadMd5 = Md5("");
        }

        var sign = Md5(string.Join("_", ApiKey, path, nonce, timestamp, payloadMd5, ApiSecret));

        var headers = StaticHeaders();
        headers["Authx"] = $"nonce={nonce}&timestamp={timestamp}&sign={sign}";
        headers["Content-Type"] = "application/json; charset=utf-8";
        return headers;
    }

    private static string Md5(string input)
    {
        var bytes = MD5.HashData(Encoding.UTF8.GetBytes(input));
        var builder = new StringBuilder(bytes.Length * 2);
        foreach (var b in bytes) builder.Append(b.ToString("x2"));
        return builder.ToString();
    }

    // ------------------------------------------------------------- 请求

    private async Task<JsonElement> GetDataAsync(string path, Dictionary<string, string>? query = null, CancellationToken ct = default)
    {
        var full = _base + path;
        if (query is { Count: > 0 })
            full += "?" + string.Join("&", query.Select(pair => $"{pair.Key}={pair.Value}"));
        var text = await Http.GetTextAsync(full, SignHeaders(path, query, null), ct).ConfigureAwait(false);
        return Unwrap(text);
    }

    private async Task<JsonElement> PostDataAsync(string path, string body, CancellationToken ct = default)
    {
        var text = await Http.PostJsonAsync(_base + path, body, SignHeaders(path, null, body), ct).ConfigureAwait(false);
        return Unwrap(text);
    }

    private static JsonElement Unwrap(string text)
    {
        using var doc = JsonExt.Parse(text) ?? throw new InvalidOperationException("飞牛影视返回内容无法解析");
        var root = doc.RootElement;
        var code = root.Int("code") ?? -1;
        if (code != 0)
        {
            var message = root.Str("msg") ?? $"错误码 {code}";
            throw new InvalidOperationException($"飞牛影视：{message}（{code}）");
        }
        // 复制一份脱离 doc 生命周期
        return root.Get("data") is { } data ? data.Clone() : default;
    }

    private static List<JsonElement> List(JsonElement? element)
        => element is { ValueKind: JsonValueKind.Array } array
            ? array.EnumerateArray().Where(item => item.ValueKind == JsonValueKind.Object).ToList()
            : new List<JsonElement>();

    // ------------------------------------------------------------- 登录

    public async Task<ServerConfig> AuthenticateAsync(string username, string password, CancellationToken ct = default)
    {
        var body = JsonSerializer.Serialize(new { username, password });
        var data = await PostDataAsync("/v/api/v1/login", body, ct).ConfigureAwait(false);
        var newToken = data.Str("token") ?? data.Str("access_token")
            ?? throw new InvalidOperationException("登录失败：未返回 token");
        _token = newToken;

        var userGuid = "";
        try
        {
            userGuid = (await GetDataAsync("/v/api/v1/user/info", null, ct).ConfigureAwait(false)).Str("guid") ?? "";
        }
        catch
        {
            // 用户信息接口失败不影响登录
        }

        var result = Config.Clone();
        result.Token = newToken;
        result.UserId = userGuid;
        result.Username = username;
        result.Url = _base;
        if (string.IsNullOrWhiteSpace(result.Name)) result.Name = "飞牛影视";
        return result;
    }

    // ------------------------------------------------------------- 媒体库

    public async Task<List<Library>> GetLibrariesAsync(CancellationToken ct = default)
    {
        var data = await GetDataAsync("/v/api/v1/mediadb/list", null, ct).ConfigureAwait(false);
        var libraries = new List<Library>();
        foreach (var o in List(data))
        {
            var guid = o.Str("guid");
            if (string.IsNullOrEmpty(guid)) continue;
            libraries.Add(new Library
            {
                Id = guid,
                Name = o.StrOr("title", "媒体库"),
                CollectionType = o.Str("category"),
            });
        }
        return libraries;
    }

    public async Task<List<HomeRow>> GetHomeRowsAsync(CancellationToken ct = default)
    {
        var rows = new List<HomeRow>();

        try
        {
            var data = await GetDataAsync("/v/api/v1/play/list", null, ct).ConfigureAwait(false);
            var items = List(data).Select(MapCard).Where(card => card != null).Select(card => card!).ToList();
            if (items.Count > 0)
                rows.Add(new HomeRow { Title = "继续观看", Items = items, Kind = RowKind.Continue });
        }
        catch
        {
            // 继续观看失败不影响首页其它分区
        }

        try
        {
            var libraries = await GetLibrariesAsync(ct).ConfigureAwait(false);
            foreach (var library in libraries.Take(6))
            {
                try
                {
                    var page = await GetItemsAsync(library.Id, 0, 18, ct).ConfigureAwait(false);
                    if (page.Items.Count > 0)
                        rows.Add(new HomeRow
                        {
                            Title = library.Name,
                            Items = page.Items,
                            Kind = RowKind.Library,
                            LibraryId = library.Id,
                        });
                }
                catch
                {
                    // 单个库失败跳过
                }
            }
        }
        catch
        {
        }

        return rows;
    }

    public async Task<PagedItems> GetItemsAsync(string libraryId, int startIndex, int limit, CancellationToken ct = default)
    {
        var page = startIndex / Math.Max(limit, 1) + 1;
        var body = JsonSerializer.Serialize(new
        {
            ancestor_guid = libraryId,
            page,
            page_size = limit,
            sort_column = "create_time",
            sort_type = "DESC",
            exclude_grouped_video = 1,
            // 必须显式列出类型，否则飞牛只返回库标题、不返回条目
            tags = new { type = new[] { "Movie", "TV", "Directory", "Video", "LiveChannel" } },
        });

        var data = await PostDataAsync("/v/api/v1/item/list", body, ct).ConfigureAwait(false);
        var items = List(data.Get("list")).Select(MapCard).Where(card => card != null).Select(card => card!).ToList();
        return new PagedItems
        {
            Items = items,
            Total = data.Int("total") ?? items.Count,
            StartIndex = startIndex,
        };
    }

    public async Task<List<MediaCard>> SearchAsync(string query, CancellationToken ct = default)
    {
        var data = await GetDataAsync("/v/api/v1/search/list",
            new Dictionary<string, string> { ["q"] = query }, ct).ConfigureAwait(false);
        return List(data).Select(MapCard).Where(card => card != null).Select(card => card!).ToList();
    }

    // ------------------------------------------------------------- 详情

    public async Task<MediaDetail> GetDetailAsync(string itemId, CancellationToken ct = default)
    {
        var data = await GetDataAsync($"/v/api/v1/item/{itemId}", null, ct).ConfigureAwait(false);
        if (data is not { ValueKind: JsonValueKind.Object })
            throw new InvalidOperationException("无法获取详情");

        var o = data;
        var type = ParseType(o.Str("type"));
        var genres = await MapGenresAsync(o, ct).ConfigureAwait(false);

        var seasons = new List<Season>();
        var episodes = new List<Episode>();
        if (type == MediaType.Series)
        {
            try
            {
                seasons = await GetSeasonsAsync(itemId, ct).ConfigureAwait(false);
                if (seasons.Count > 0)
                    episodes = await GetEpisodesAsync(itemId, seasons[0].Id, ct).ConfigureAwait(false);
            }
            catch
            {
                // 季/集加载失败时详情页仍可展示基本信息
            }
        }

        var playTarget = type == MediaType.Series
            ? episodes.FirstOrDefault(e => !e.Played)?.Id ?? episodes.FirstOrDefault()?.Id ?? itemId
            : itemId;

        var people = new List<Person>();
        try
        {
            var personData = await GetDataAsync($"/v/api/v1/person/list/{itemId}", null, ct).ConfigureAwait(false);
            foreach (var p in List(personData).Take(24))
            {
                var name = p.Str("name");
                if (string.IsNullOrEmpty(name)) continue;
                people.Add(new Person
                {
                    Name = name,
                    Role = p.Str("role") ?? p.Str("job"),
                    ImageUrl = ImgUrl(p.Str("profile_path")),
                });
            }
        }
        catch
        {
        }

        var runtime = (o.Int("duration") ?? 0) / 60;

        return new MediaDetail
        {
            Id = itemId,
            Title = o.Str("title") is { Length: > 0 } title ? title : o.Str("tv_title") ?? "未命名",
            OriginalTitle = o.Str("original_title"),
            Overview = o.Str("overview"),
            PosterUrl = ImgUrl(o.Str("posters")),
            BackdropUrl = ImgUrl(o.Str("backdrops")) ?? ImgUrl(o.Str("posters")),
            Year = int.TryParse(o.Str("release_date") is { Length: >= 4 } date ? date[..4] : null, out var year) ? year : null,
            Rating = double.TryParse(o.Str("vote_average"), out var rating) ? rating : null,
            OfficialRating = o.Str("content_ratings"),
            Genres = genres,
            RuntimeMinutes = runtime > 0 ? runtime : null,
            Type = type,
            People = people,
            Seasons = seasons,
            Episodes = episodes,
            Favorite = (o.Int("is_favorite") ?? 0) == 1,
            Played = (o.Int("is_watched") ?? 0) == 1,
            PlayTargetId = playTarget,
            Studio = o.Str("ancestor_name"),
        };
    }

    private async Task<List<string>> MapGenresAsync(JsonElement o, CancellationToken ct)
    {
        var ids = new List<int>();
        foreach (var element in o.Arr("genres"))
        {
            if (element.ValueKind == JsonValueKind.Number && element.TryGetInt32(out var id)) ids.Add(id);
            else if (element.ValueKind == JsonValueKind.String && int.TryParse(element.GetString(), out var parsed)) ids.Add(parsed);
        }
        if (ids.Count == 0) return new List<string>();

        if (_genreCache.Count == 0)
        {
            try
            {
                var data = await GetDataAsync("/v/api/v1/tag/genres", null, ct).ConfigureAwait(false);
                var cache = new Dictionary<int, string>();
                foreach (var g in List(data))
                {
                    var id = g.Int("id") ?? g.Int("genre_id");
                    var name = g.Str("name") ?? g.Str("title");
                    if (id != null && name != null) cache[id.Value] = name;
                }
                _genreCache = cache;
            }
            catch
            {
                _genreCache = new Dictionary<int, string>();
            }
        }

        return ids.Where(_genreCache.ContainsKey).Select(id => _genreCache[id]).ToList();
    }

    public async Task<List<Season>> GetSeasonsAsync(string seriesId, CancellationToken ct = default)
    {
        var data = await GetDataAsync($"/v/api/v1/season/list/{seriesId}", null, ct).ConfigureAwait(false);
        var result = new List<Season>();
        var index = 0;
        foreach (var o in List(data))
        {
            var guid = o.Str("guid");
            if (!string.IsNullOrEmpty(guid))
            {
                result.Add(new Season
                {
                    Id = guid,
                    Name = o.StrOr("title", $"第 {index + 1} 季"),
                    Index = o.Int("season_number") ?? (index + 1),
                });
            }
            index++;
        }
        return result;
    }

    public async Task<List<Episode>> GetEpisodesAsync(string seriesId, string seasonId, CancellationToken ct = default)
    {
        var data = await GetDataAsync($"/v/api/v1/episode/list/{seasonId}", null, ct).ConfigureAwait(false);
        var result = new List<Episode>();
        foreach (var o in List(data))
        {
            var guid = o.Str("guid");
            if (string.IsNullOrEmpty(guid)) continue;
            var runtime = (o.Int("runtime") ?? 0) / 60;
            result.Add(new Episode
            {
                Id = guid,
                Title = o.StrOr("title", $"第 {o.Int("episode_number") ?? 0} 集"),
                Index = o.Int("episode_number") ?? 0,
                SeasonIndex = o.Int("season_number") ?? 0,
                Overview = o.Str("overview"),
                ImageUrl = ImgUrl(o.Str("poster")),
                RuntimeMinutes = runtime > 0 ? runtime : null,
                Played = (o.Int("watched") ?? 0) == 1,
            });
        }
        return result;
    }

    // ------------------------------------------------------------- 播放

    public async Task<PlaybackPlan> GetPlaybackAsync(string itemId, long startMs, bool forceTranscode = false, CancellationToken ct = default)
    {
        var infoBody = JsonSerializer.Serialize(new { item_guid = itemId });
        var info = await PostDataAsync("/v/api/v1/play/info", infoBody, ct).ConfigureAwait(false);
        if (info is not { ValueKind: JsonValueKind.Object })
            throw new InvalidOperationException("飞牛影视未返回播放信息");

        var infoRoot = info;
        var mediaGuid = infoRoot.Str("media_guid") ?? throw new InvalidOperationException("未返回 media_guid");
        var startSec = (int)(startMs / 1000L);
        var resumeSec = infoRoot.Int("ts") ?? 0;
        var item = infoRoot.Obj("item");

        var stream = default(JsonElement);
        try
        {
            var streamBody = JsonSerializer.Serialize(new { media_guid = mediaGuid, level = 1 });
            stream = await PostDataAsync("/v/api/v1/stream", streamBody, ct).ConfigureAwait(false);
        }
        catch
        {
            // 拿不到流信息时仍可用 media/range 直链播放
        }

        var videoStream = stream.Obj("video_stream");
        var audioStreams = List(stream.Get("audio_streams"));
        var subtitleStreams = List(stream.Get("subtitle_streams"));
        var fileStream = stream.Obj("file_stream");

        var audioTracks = new List<MediaTrack>();
        for (var index = 0; index < audioStreams.Count; index++)
        {
            var a = audioStreams[index];
            audioTracks.Add(new MediaTrack
            {
                Index = a.Int("index") ?? index,
                Label = a.StrOr("title", a.StrOr("codec_name", $"音轨 {index + 1}")),
                Language = a.Str("language"),
                Codec = a.Str("codec_name"),
                Channels = a.Int("channels"),
                Bitrate = a.Int("bps"),
            });
        }

        var subtitleTracks = new List<SubtitleTrack>();
        for (var index = 0; index < subtitleStreams.Count; index++)
        {
            var s = subtitleStreams[index];
            var external = (s.Int("is_external") ?? 0) == 1;
            subtitleTracks.Add(new SubtitleTrack
            {
                Index = s.Int("index") ?? index,
                Label = s.StrOr("title", s.StrOr("language", $"字幕 {index + 1}")),
                Language = s.Str("language"),
                Codec = s.Str("codec_name"),
                Url = external && s.Str("guid") is { Length: > 0 } guid
                    ? _base + "/v/api/v1/subtitle/dl/" + guid
                    : null,
                IsExternal = external,
                Headers = StaticHeaders(),
            });
        }

        var colorRange = videoStream.Str("color_range_type");
        var dvProfile = videoStream.Int("dv_profile") ?? 0;
        var dolbyVision = dvProfile > 0 || colorRange?.Contains("Dolby", StringComparison.OrdinalIgnoreCase) == true;
        var dynamicRange = dolbyVision
            ? "Dolby Vision"
            : colorRange?.Contains("HDR", StringComparison.OrdinalIgnoreCase) == true
                ? colorRange!
                : colorRange ?? "SDR";

        var resolution = videoStream.Str("resolution_type")
                         ?? (videoStream.Int("width") != null
                             ? $"{videoStream.Int("width")}x{videoStream.Int("height")}"
                             : null);
        var bitrate = videoStream.Int("bps") ?? 0;

        // 网盘类媒体（夸克/115 等）需要走 play/play 换取 CDN 直链，否则用本地代理。
        var hasCloudDirect = stream.Get("direct_link_qualities") is { ValueKind: JsonValueKind.Array } qualities
                             && qualities.GetArrayLength() > 0;
        var playUrl = $"{_base}/v/api/v1/media/range/{mediaGuid}";
        var isHls = false;
        if (hasCloudDirect)
        {
            try
            {
                var linkBody = JsonSerializer.Serialize(new
                {
                    media_guid = mediaGuid,
                    video_guid = videoStream.Str("guid") ?? "",
                    video_encoder = videoStream.Str("codec_name") ?? "hevc",
                    resolution = resolution ?? "1080p",
                    bitrate,
                    startTimestamp = startSec,
                    audio_encoder = "aac",
                    audio_guid = audioStreams.FirstOrDefault().Str("guid") ?? "",
                    subtitle_guid = "",
                    channels = 2,
                    forced_sdr = 0,
                });
                var link = (await PostDataAsync("/v/api/v1/play/play", linkBody, ct).ConfigureAwait(false)).Str("play_link");
                if (!string.IsNullOrWhiteSpace(link))
                {
                    playUrl = link.StartsWith("http", StringComparison.OrdinalIgnoreCase) ? link : _base + link;
                    isHls = link.Contains(".m3u8", StringComparison.OrdinalIgnoreCase);
                }
            }
            catch
            {
                // 云盘直链失败时回落到本地 range 代理
            }
        }

        var durationSec = item.Int("duration") ?? 0;

        var videoTracks = new List<MediaTrack>();
        if (videoStream is { ValueKind: JsonValueKind.Object })
        {
            videoTracks.Add(new MediaTrack
            {
                Index = videoStream.Int("index") ?? 0,
                Label = string.Join(" · ", new[] { videoStream.Str("resolution_type"), videoStream.Str("codec_name") }
                    .Where(part => !string.IsNullOrEmpty(part))),
                Codec = videoStream.Str("codec_name"),
            });
        }

        return new PlaybackPlan
        {
            ItemId = itemId,
            Title = item.Str("title") is { Length: > 0 } t ? t : item.Str("tv_title") ?? "正在播放",
            Subtitle = BuildEpisodeLabel(item),
            Url = playUrl,
            Headers = StaticHeaders(),
            IsHls = isHls,
            StartPositionMs = resumeSec * 1000L,
            DurationMs = durationSec * 1000L,
            VideoTracks = videoTracks,
            AudioTracks = audioTracks,
            SubtitleTracks = subtitleTracks,
            Direct = true,
            DynamicRange = dynamicRange,
            DolbyVision = dolbyVision,
            Container = fileStream.Str("file_name")?.Split('.').LastOrDefault(),
            FnMediaGuid = mediaGuid,
            FnVideoGuid = videoStream.Str("guid"),
            FnAudioGuid = audioStreams.FirstOrDefault().Str("guid"),
            FnSubtitleGuid = infoRoot.Str("subtitle_guid"),
            FnResolution = resolution,
            FnBitrate = bitrate,
            FnDirectAudioIndex = infoRoot.Int("direct_link_audio_index") ?? 0,
            PosterUrl = ImgUrl(item.Str("posters")),
        };
    }

    private static string? BuildEpisodeLabel(JsonElement item)
    {
        if (item is not { ValueKind: JsonValueKind.Object }) return null;
        var season = item.Int("season_number") ?? 0;
        var episode = item.Int("episode_number") ?? 0;
        if (episode <= 0) return item.Str("release_date") is { Length: >= 4 } date ? date[..4] : null;
        return $"第 {season} 季 · 第 {episode} 集";
    }

    public Task ReportStartAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default)
        => ReportRecordAsync(plan, positionMs, ct);

    public Task ReportProgressAsync(PlaybackPlan plan, long positionMs, bool paused, CancellationToken ct = default)
        => ReportRecordAsync(plan, positionMs, ct);

    public Task ReportStopAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default)
        => ReportRecordAsync(plan, positionMs, ct);

    private async Task ReportRecordAsync(PlaybackPlan plan, long positionMs, CancellationToken ct)
    {
        var mediaGuid = plan.FnMediaGuid;
        if (string.IsNullOrEmpty(mediaGuid)) return;

        try
        {
            var body = JsonSerializer.Serialize(new
            {
                item_guid = plan.ItemId,
                media_guid = mediaGuid,
                video_guid = plan.FnVideoGuid ?? "",
                audio_guid = plan.FnAudioGuid ?? "",
                subtitle_guid = plan.FnSubtitleGuid ?? "",
                resolution = plan.FnResolution ?? "1080p",
                bitrate = plan.FnBitrate,
                ts = (int)(positionMs / 1000L),
                duration = (int)(plan.DurationMs / 1000L),
                device_id = Config.DeviceId,
                direct_link_audio_index = plan.FnDirectAudioIndex,
                lan = "zh",
                device_name = "RainPlayer",
            });
            await PostDataAsync("/v/api/v1/play/record", body, ct).ConfigureAwait(false);
        }
        catch
        {
            // 上报失败不影响播放
        }
    }

    public async Task SetFavoriteAsync(string itemId, bool favorite, CancellationToken ct = default)
    {
        try
        {
            const string path = "/v/api/v1/item/favorite";
            var body = JsonSerializer.Serialize(new { item_guid = itemId });
            if (favorite)
                await Http.PostJsonAsync(_base + path, body, SignHeaders(path, null, body), ct).ConfigureAwait(false);
            else
                await Http.DeleteJsonAsync(_base + path, body, SignHeaders(path, null, body), ct).ConfigureAwait(false);
        }
        catch
        {
        }
    }

    public async Task SetWatchedAsync(string itemId, bool watched, CancellationToken ct = default)
    {
        try
        {
            const string path = "/v/api/v1/item/watched";
            var body = JsonSerializer.Serialize(new { item_guid = itemId });
            if (watched)
                await Http.PostJsonAsync(_base + path, body, SignHeaders(path, null, body), ct).ConfigureAwait(false);
            else
                await Http.DeleteJsonAsync(_base + path, body, SignHeaders(path, null, body), ct).ConfigureAwait(false);
        }
        catch
        {
        }
    }

    public async Task<bool> DownloadSubtitleAsync(SubtitleTrack track, string destPath, CancellationToken ct = default)
    {
        if (string.IsNullOrEmpty(track.Url)) return false;
        // 字幕接口按路径签名校验，必须带上 Authx
        var path = "/v/api/v1/subtitle/dl/" + track.Url.Split('/').Last();
        try
        {
            var bytes = await Http.GetBytesAsync(track.Url, SignHeaders(path, null, null), ct).ConfigureAwait(false);
            if (bytes.Length == 0) return false;
            var directory = Path.GetDirectoryName(destPath);
            if (!string.IsNullOrEmpty(directory)) Directory.CreateDirectory(directory);
            await File.WriteAllBytesAsync(destPath, bytes, ct).ConfigureAwait(false);
            return true;
        }
        catch
        {
            return false;
        }
    }

    // ------------------------------------------------------------- 映射

    private string? ImgUrl(string? path)
    {
        if (string.IsNullOrWhiteSpace(path)) return null;
        if (path.StartsWith("http", StringComparison.OrdinalIgnoreCase)) return path;
        var normalized = path.StartsWith('/') ? path : "/" + path;
        return $"{_base}/v/api/v1/sys/img{normalized}";
    }

    private MediaCard? MapCard(JsonElement o)
    {
        if (o.ValueKind != JsonValueKind.Object) return null;
        var guid = o.Str("guid");
        if (string.IsNullOrEmpty(guid)) return null;

        var type = ParseType(o.Str("type"));
        var mediaStream = o.Obj("media_stream");
        var resolutions = new List<string>();
        foreach (var element in mediaStream.Arr("resolutions"))
        {
            if (element.ValueKind == JsonValueKind.String) resolutions.Add(element.GetString()!);
        }

        var subtitle = type == MediaType.Episode
            ? $"第 {o.Int("season_number") ?? 0} 季 · 第 {o.Int("episode_number") ?? 0} 集"
            : resolutions.FirstOrDefault() ?? (o.Str("release_date") is { Length: >= 4 } date ? date[..4] : null);

        var poster = ImgUrl(o.Str("poster") ?? o.Str("posters"));
        if (poster == null)
        {
            var first = o.Arr("poster_list").FirstOrDefault();
            if (first.ValueKind == JsonValueKind.String) poster = ImgUrl(first.GetString());
        }

        return new MediaCard
        {
            Id = guid,
            Title = o.Str("title") is { Length: > 0 } title ? title : o.Str("tv_title") ?? "未命名",
            Subtitle = subtitle,
            PosterUrl = poster,
            BackdropUrl = poster,
            Type = type,
            Progress = 0,
            Played = (o.Int("watched") ?? 0) == 1,
            Year = int.TryParse(o.Str("release_date") is { Length: >= 4 } d ? d[..4] : null, out var year) ? year : null,
            Rating = double.TryParse(o.Str("vote_average"), out var rating) ? rating : null,
        };
    }

    private static MediaType ParseType(string? raw) => raw?.ToLowerInvariant() switch
    {
        "movie" => MediaType.Movie,
        "tv" or "series" => MediaType.Series,
        "season" => MediaType.Season,
        "episode" => MediaType.Episode,
        "video" => MediaType.Video,
        "folder" or "directory" => MediaType.Folder,
        _ => MediaType.Unknown,
    };
}
