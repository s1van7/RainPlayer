using System.Text;
using System.Text.Json;
using RainPlayer.Core.Json;
using RainPlayer.Core.Models;
using RainPlayer.Core.Net;

namespace RainPlayer.Core.Backends;

/// <summary>Emby / Jellyfin 后端。两者 REST 协议一致，仅少数字段差异。</summary>
public sealed class EmbyBackend : IMediaBackend
{
    private readonly string _base;
    private readonly string _token;
    private readonly string _userId;

    public ServerConfig Config { get; }

    public EmbyBackend(ServerConfig config)
    {
        Config = config;
        _base = BackendFactory.NormalizeUrl(config.Url);
        _token = config.Token;
        _userId = config.UserId;
    }

    private string AuthValue =>
        $"MediaBrowser Client=\"RainPlayer\", Device=\"RainPlayer\", DeviceId=\"{Config.DeviceId}\", Version=\"1.0.0\"";

    /// <summary>
    /// Emby 只认 X-Emby-Authorization；Jellyfin 10.x 主推标准的 Authorization。
    /// 两者都同时接受 X-Emby-Token，所以 token 单独走这个头最稳。
    /// </summary>
    private string AuthHeaderName => Config.Kind == ServerKind.Jellyfin ? "Authorization" : "X-Emby-Authorization";

    private Dictionary<string, string> TokenHeaders()
    {
        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            [AuthHeaderName] = AuthValue,
        };
        if (!string.IsNullOrWhiteSpace(_token))
        {
            headers["X-Emby-Token"] = _token;
            headers["X-Emby-Authorization"] = $"{AuthValue}, Token=\"{_token}\"";
        }
        return headers;
    }

    public IReadOnlyDictionary<string, string> ImageHeaders => TokenHeaders();

    private string Url(string path) => path.StartsWith("http", StringComparison.OrdinalIgnoreCase) ? path : _base + path;

    private static string Q(string value) => Uri.EscapeDataString(value);

    // ------------------------------------------------------------- 登录

    public async Task<ServerConfig> AuthenticateAsync(string username, string password, CancellationToken ct = default)
    {
        var body = JsonSerializer.Serialize(new { Username = username, Pw = password });
        var text = await Http.PostJsonAsync(
            Url("/Users/AuthenticateByName"),
            body,
            new Dictionary<string, string> { [AuthHeaderName] = AuthValue },
            ct).ConfigureAwait(false);

        using var doc = JsonExt.Parse(text) ?? throw new InvalidOperationException("服务器返回内容无法解析");
        var root = doc.RootElement;
        var accessToken = root.Str("AccessToken") ?? throw new InvalidOperationException("登录失败：未返回 AccessToken");
        var uid = root.Obj("User").Str("Id") ?? throw new InvalidOperationException("登录失败：未返回用户信息");

        var result = Config.Clone();
        result.Token = accessToken;
        result.UserId = uid;
        result.Username = username;
        result.Url = _base;
        if (string.IsNullOrWhiteSpace(result.Name))
            result.Name = root.Obj("ServerInfo").Str("Name") ?? "Emby";
        return result;
    }

    // ------------------------------------------------------------- 媒体库

    public async Task<List<Library>> GetLibrariesAsync(CancellationToken ct = default)
    {
        var text = await Http.GetTextAsync(Url($"/Users/{_userId}/Views"), TokenHeaders(), ct).ConfigureAwait(false);
        using var doc = JsonExt.Parse(text);
        var root = (doc?.RootElement ?? default);
        var libraries = new List<Library>();
        foreach (var element in root.Arr("Items"))
        {
            var id = element.Str("Id");
            if (string.IsNullOrEmpty(id)) continue;
            libraries.Add(new Library
            {
                Id = id,
                Name = element.StrOr("Name", "媒体库"),
                CollectionType = element.Str("CollectionType"),
            });
        }
        return libraries;
    }

    public async Task<List<HomeRow>> GetHomeRowsAsync(CancellationToken ct = default)
    {
        var rows = new List<HomeRow>();

        try
        {
            var text = await Http.GetTextAsync(
                Url($"/Users/{_userId}/Items/Resume?Limit=20&MediaTypes=Video" +
                    "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,BackdropImageTags,CommunityRating,ChildCount,RecursiveItemCount" +
                    "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop"),
                TokenHeaders(), ct).ConfigureAwait(false);
            using var doc = JsonExt.Parse(text);
            var items = (doc?.RootElement ?? default).Arr("Items")
                .Select(MapCard).Where(card => card != null).Select(card => card!).ToList();
            if (items.Count > 0)
                rows.Add(new HomeRow { Title = "继续观看", Items = items, Kind = RowKind.Continue });
        }
        catch
        {
            // 继续观看失败不影响首页其它分区
        }

        List<Library> libraries;
        try
        {
            libraries = await GetLibrariesAsync(ct).ConfigureAwait(false);
        }
        catch
        {
            libraries = new List<Library>();
        }

        foreach (var library in libraries.Take(6))
        {
            try
            {
                var text = await Http.GetTextAsync(
                    Url($"/Users/{_userId}/Items/Latest?ParentId={library.Id}&Limit=18" +
                        "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,BackdropImageTags,CommunityRating,ChildCount,RecursiveItemCount" +
                        "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop"),
                    TokenHeaders(), ct).ConfigureAwait(false);
                using var doc = JsonExt.Parse(text);
                var items = (doc?.RootElement ?? default).Arr()
                    .Select(MapCard).Where(card => card != null).Select(card => card!).ToList();
                if (items.Count > 0)
                    rows.Add(new HomeRow { Title = library.Name, Items = items, Kind = RowKind.Library, LibraryId = library.Id });
            }
            catch
            {
                // 单个库失败跳过
            }
        }

        return rows;
    }

    public async Task<PagedItems> GetItemsAsync(string libraryId, int startIndex, int limit, CancellationToken ct = default)
    {
        var text = await Http.GetTextAsync(
            Url($"/Users/{_userId}/Items?ParentId={Q(libraryId)}&Recursive=true" +
                "&IncludeItemTypes=Movie,Series,Video,Folder&SortBy=SortName&SortOrder=Ascending" +
                $"&StartIndex={startIndex}&Limit={limit}" +
                "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,BackdropImageTags,CommunityRating,ChildCount,RecursiveItemCount" +
                "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop"),
            TokenHeaders(), ct).ConfigureAwait(false);

        using var doc = JsonExt.Parse(text);
        var root = (doc?.RootElement ?? default);
        var items = root.Arr("Items").Select(MapCard).Where(card => card != null).Select(card => card!).ToList();
        return new PagedItems
        {
            Items = items,
            Total = root.Int("TotalRecordCount") ?? items.Count,
            StartIndex = startIndex,
        };
    }

    public async Task<List<MediaCard>> SearchAsync(string query, CancellationToken ct = default)
    {
        var text = await Http.GetTextAsync(
            Url($"/Users/{_userId}/Items?SearchTerm={Q(query)}&Recursive=true&Limit=60" +
                "&IncludeItemTypes=Movie,Series,Episode,Video" +
                "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,CommunityRating,ChildCount,RecursiveItemCount" +
                "&ImageTypeLimit=1&EnableImageTypes=Primary"),
            TokenHeaders(), ct).ConfigureAwait(false);

        using var doc = JsonExt.Parse(text);
        return (doc?.RootElement ?? default).Arr("Items").Select(MapCard)
            .Where(card => card != null).Select(card => card!).ToList();
    }

    // ------------------------------------------------------------- 详情

    public async Task<MediaDetail> GetDetailAsync(string itemId, CancellationToken ct = default)
    {
        const string fields = "Overview,People,Genres,ProductionYear,RunTimeTicks,CommunityRating," +
                              "OfficialRating,MediaSources,MediaStreams,Taglines,UserData,BackdropImageTags,ImageTags," +
                              "PrimaryImageAspectRatio,Studios,ChildCount,PremiereDate,CriticRating";
        var text = await Http.GetTextAsync(Url($"/Users/{_userId}/Items/{itemId}?Fields={fields}"), TokenHeaders(), ct)
            .ConfigureAwait(false);

        using var doc = JsonExt.Parse(text) ?? throw new InvalidOperationException("无法获取详情");
        var o = doc.RootElement;
        var type = ParseType(o.Str("Type"));

        var people = new List<Person>();
        foreach (var element in o.Arr("People").Take(24))
        {
            var name = element.Str("Name");
            if (string.IsNullOrEmpty(name)) continue;
            people.Add(new Person
            {
                Name = name,
                Role = element.Str("Role"),
                ImageUrl = element.Str("PrimaryImageTag") != null
                    ? Url($"/Items/{element.Str("Id")}/Images/Primary?maxHeight=300&quality=85&api_key={_token}")
                    : null,
            });
        }

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

        var userData = o.Obj("UserData");
        var playTarget = type == MediaType.Series
            ? episodes.FirstOrDefault(e => !e.Played)?.Id ?? episodes.FirstOrDefault()?.Id ?? itemId
            : itemId;

        return new MediaDetail
        {
            Id = itemId,
            Title = o.StrOr("Name", "未命名"),
            OriginalTitle = o.Str("OriginalTitle"),
            Overview = o.Str("Overview"),
            Tagline = o.StringList("Taglines").FirstOrDefault(),
            PosterUrl = PrimaryImage(itemId, o),
            BackdropUrl = BackdropImage(itemId, o),
            Year = o.Int("ProductionYear"),
            Rating = o.Dbl("CommunityRating"),
            OfficialRating = o.Str("OfficialRating"),
            Genres = o.StringList("Genres"),
            RuntimeMinutes = (int)(JsonExt.TicksToMs(o.Long("RunTimeTicks")) / 60_000L) is var minutes && minutes > 0 ? minutes : null,
            Type = type,
            People = people,
            Seasons = seasons,
            Episodes = episodes,
            Favorite = userData.Bool("IsFavorite") ?? false,
            Played = userData.Bool("Played") ?? false,
            Progress = (userData.Dbl("PlayedPercentage") ?? 0) / 100.0,
            PlayTargetId = playTarget,
            Studio = o.Arr("Studios").FirstOrDefault().Str("Name"),
            PremiereDate = o.Str("PremiereDate") is { Length: >= 10 } pd ? pd[..10] : null,
            CommunityRating = o.Dbl("CommunityRating"),
            CriticRating = o.Dbl("CriticRating"),
        };
    }

    /// <summary>「更多类似作品」：Emby / Jellyfin 都提供 /Items/{id}/Similar。</summary>
    public async Task<List<MediaCard>> GetSimilarAsync(string itemId, int limit = 12, CancellationToken ct = default)
    {
        var text = await Http.GetTextAsync(
            Url($"/Items/{itemId}/Similar?UserId={_userId}&Limit={limit}" +
                "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,BackdropImageTags,CommunityRating,ChildCount,RecursiveItemCount" +
                "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop"),
            TokenHeaders(), ct).ConfigureAwait(false);

        using var doc = JsonExt.Parse(text);
        var result = new List<MediaCard>();
        foreach (var element in (doc?.RootElement ?? default).Arr("Items"))
        {
            var card = MapCard(element);
            if (card != null) result.Add(card);
        }
        return result;
    }

    /// <summary>单集详情：补上所属剧集、播出日期、剧照。</summary>
    public async Task<Episode> GetEpisodeAsync(string itemId, CancellationToken ct = default)
    {
        const string fields = "Overview,RunTimeTicks,UserData,ImageTags,BackdropImageTags,ProductionYear," +
                              "PremiereDate,IndexNumber,ParentIndexNumber,SeriesId,SeriesName";
        var text = await Http.GetTextAsync(Url($"/Users/{_userId}/Items/{itemId}?Fields={fields}"), TokenHeaders(), ct)
            .ConfigureAwait(false);

        using var doc = JsonExt.Parse(text) ?? throw new InvalidOperationException("无法获取单集详情");
        var o = doc.RootElement;
        var ud = o.Obj("UserData");
        var minutes = (int)(JsonExt.TicksToMs(o.Long("RunTimeTicks")) / 60_000L);
        return new Episode
        {
            Id = itemId,
            Title = o.StrOr("Name", "未命名"),
            Index = o.Int("IndexNumber") ?? 0,
            SeasonIndex = o.Int("ParentIndexNumber") ?? 0,
            Overview = o.Str("Overview"),
            ImageUrl = PrimaryImage(itemId, o),
            BackdropUrl = BackdropImage(itemId, o),
            RuntimeMinutes = minutes > 0 ? minutes : null,
            Played = ud.Bool("Played") ?? false,
            Progress = (ud.Dbl("PlayedPercentage") ?? 0) / 100.0,
            AirDate = o.Str("PremiereDate") is { Length: >= 10 } d ? d[..10] : null,
            SeriesId = o.Str("SeriesId"),
            SeriesName = o.Str("SeriesName"),
        };
    }

    public async Task<List<Season>> GetSeasonsAsync(string seriesId, CancellationToken ct = default)
    {
        var text = await Http.GetTextAsync(
            Url($"/Shows/{seriesId}/Seasons?UserId={_userId}&Fields=PrimaryImageTag"),
            TokenHeaders(), ct).ConfigureAwait(false);

        using var doc = JsonExt.Parse(text);
        var result = new List<Season>();
        var index = 0;
        foreach (var element in (doc?.RootElement ?? default).Arr("Items"))
        {
            var id = element.Str("Id");
            if (!string.IsNullOrEmpty(id))
            {
                result.Add(new Season
                {
                    Id = id,
                    Name = element.StrOr("Name", $"第 {index + 1} 季"),
                    Index = element.Int("IndexNumber") ?? (index + 1),
                });
            }
            index++;
        }
        return result;
    }

    public async Task<List<Episode>> GetEpisodesAsync(string seriesId, string seasonId, CancellationToken ct = default)
    {
        var text = await Http.GetTextAsync(
            Url($"/Shows/{seriesId}/Episodes?UserId={_userId}&SeasonId={seasonId}" +
                "&Fields=Overview,PrimaryImageTag,UserData,RunTimeTicks"),
            TokenHeaders(), ct).ConfigureAwait(false);

        using var doc = JsonExt.Parse(text);
        var result = new List<Episode>();
        foreach (var element in (doc?.RootElement ?? default).Arr("Items"))
        {
            var id = element.Str("Id");
            if (string.IsNullOrEmpty(id)) continue;
            var ud = element.Obj("UserData");
            var runtime = (int)(JsonExt.TicksToMs(element.Long("RunTimeTicks")) / 60_000L);
            result.Add(new Episode
            {
                Id = id,
                Title = element.StrOr("Name", $"第 {element.Int("IndexNumber") ?? 0} 集"),
                Index = element.Int("IndexNumber") ?? 0,
                SeasonIndex = element.Int("ParentIndexNumber") ?? 0,
                Overview = element.Str("Overview"),
                ImageUrl = element.Obj("ImageTags").Str("Primary") != null
                    ? Url($"/Items/{id}/Images/Primary?maxWidth=480&quality=85&api_key={_token}")
                    : null,
                RuntimeMinutes = runtime > 0 ? runtime : null,
                Played = ud.Bool("Played") ?? false,
                Progress = (ud.Dbl("PlayedPercentage") ?? 0) / 100.0,
            });
        }
        return result;
    }

    // ------------------------------------------------------------- 播放

    public async Task<PlaybackPlan> GetPlaybackAsync(
        string itemId,
        long startMs,
        bool forceTranscode = false,
        string? sourceId = null,
        int? audioIndex = null,
        int? subtitleIndex = null,
        bool burnSubtitle = false,
        CancellationToken ct = default)
    {
        var body = JsonSerializer.Serialize(new
        {
            UserId = _userId,
            StartTimeTicks = startMs * 10_000L,
            IsPlayback = true,
            AutoOpenLiveStream = true,
            MaxStreamingBitrate = 200_000_000,
            MediaSourceId = sourceId,
            AudioStreamIndex = audioIndex,
            SubtitleStreamIndex = subtitleIndex,
            DeviceProfile = BuildDeviceProfile(false),
        });

        var infoPath = $"/Items/{itemId}/PlaybackInfo?UserId={_userId}&StartTimeTicks={startMs * 10_000L}";
        if (!string.IsNullOrEmpty(sourceId)) infoPath += $"&MediaSourceId={sourceId}";

        var text = await Http.PostJsonAsync(Url(infoPath), body, TokenHeaders(), ct).ConfigureAwait(false);

        using var doc = JsonExt.Parse(text) ?? throw new InvalidOperationException("无法获取播放信息");
        var root = doc.RootElement;
        var sources = root.Arr("MediaSources");
        if (sources.Count == 0) throw new InvalidOperationException("服务器未返回媒体源");
        var source = sources.FirstOrDefault(s => s.Str("Id") == sourceId);
        if (source.ValueKind != JsonValueKind.Object) source = sources[0];

        var mediaSourceId = source.Str("Id") ?? itemId;
        var playSessionId = root.Str("PlaySessionId");
        var container = source.Str("Container");
        var durationMs = JsonExt.TicksToMs(source.Long("RunTimeTicks"));
        // 断点续播：服务器记录的上次播放位置（调用方未指定起点时使用）
        var resumeMs = JsonExt.TicksToMs(
            source.Obj("UserData").Long("PlaybackPositionTicks")
            ?? source.Obj("UserData").Long("PlaybackPosition"));

        var streams = source.Arr("MediaStreams");
        var videoStream = streams.FirstOrDefault(s => s.Str("Type") == "Video");

        var audioTracks = new List<MediaTrack>();
        var audioOrdinal = 0;
        foreach (var s in streams.Where(s => s.Str("Type") == "Audio"))
        {
            audioTracks.Add(new MediaTrack
            {
                Index = s.Int("Index") ?? audioOrdinal,
                Label = s.StrOr("DisplayTitle", $"音轨 {audioOrdinal + 1}"),
                Language = s.Str("Language"),
                Codec = s.Str("Codec"),
                Channels = s.Int("Channels"),
                Bitrate = s.Int("BitRate"),
            });
            audioOrdinal++;
        }

        var subtitleTracks = new List<SubtitleTrack>();
        var subtitleOrdinal = 0;
        foreach (var s in streams.Where(s => s.Str("Type") == "Subtitle"))
        {
            var delivery = s.Str("DeliveryUrl");
            subtitleTracks.Add(new SubtitleTrack
            {
                Index = s.Int("Index") ?? subtitleOrdinal,
                Label = s.StrOr("DisplayTitle", $"字幕 {subtitleOrdinal + 1}"),
                Language = s.Str("Language"),
                Codec = s.Str("Codec"),
                Url = delivery != null
                    ? Url(delivery) + (delivery.Contains('?') ? "&" : "?") + $"api_key={_token}"
                    : null,
                IsExternal = s.Bool("IsExternal") ?? (delivery != null),
                ImageBased = IsImageSubtitle(s.Str("Codec")),
                Forced = s.Bool("IsForced") ?? false,
            });
            subtitleOrdinal++;
        }

        var videoRange = videoStream.Str("VideoRange");
        var videoRangeType = videoStream.Str("VideoRangeType");
        var dolbyVision = videoRangeType?.Contains("DOVI", StringComparison.OrdinalIgnoreCase) == true
                          || videoRange?.Equals("DOVI", StringComparison.OrdinalIgnoreCase) == true
                          || videoStream.Str("Codec")?.Contains("dv", StringComparison.OrdinalIgnoreCase) == true;

        var transcodingUrl = source.Str("TranscodingUrl");
        var supportsDirectPlay = source.Bool("SupportsDirectPlay") ?? true;
        // 图形字幕只能由服务器烧录，必须转码
        var useTranscode = forceTranscode || burnSubtitle || (!supportsDirectPlay && transcodingUrl != null);

        string playUrl;
        var isHls = false;
        if (useTranscode)
        {
            var built = (!forceTranscode && burnSubtitle)
                ? BuildTranscodeUrl(itemId, mediaSourceId, playSessionId, audioIndex, subtitleIndex, burnSubtitle)
                : transcodingUrl ?? BuildTranscodeUrl(itemId, mediaSourceId, playSessionId, audioIndex, subtitleIndex, burnSubtitle);
            playUrl = Url(built) + (built.Contains("api_key") ? "" : $"&api_key={_token}");
            isHls = built.Contains("m3u8", StringComparison.OrdinalIgnoreCase)
                    || built.Contains("master", StringComparison.OrdinalIgnoreCase);
        }
        else
        {
            playUrl = Url($"/Videos/{itemId}/stream?Static=true&MediaSourceId={mediaSourceId}" +
                          $"&DeviceId={Config.DeviceId}&api_key={_token}");
        }

        var title = "正在播放";
        try
        {
            var itemText = await Http.GetTextAsync(Url($"/Users/{_userId}/Items/{itemId}"), TokenHeaders(), ct)
                .ConfigureAwait(false);
            using var itemDoc = JsonExt.Parse(itemText);
            title = (itemDoc?.RootElement ?? default).Str("Name") ?? title;
        }
        catch
        {
            // 标题取不到不影响播放
        }

        var videoTracks = new List<MediaTrack>();
        if (videoStream.ValueKind == JsonValueKind.Object)
        {
            videoTracks.Add(new MediaTrack
            {
                Index = videoStream.Int("Index") ?? 0,
                Label = videoStream.StrOr("DisplayTitle", "视频"),
                Codec = videoStream.Str("Codec"),
            });
        }

        return new PlaybackPlan
        {
            ItemId = itemId,
            Title = title,
            Url = playUrl,
            Headers = TokenHeaders(),
            IsHls = isHls,
            StartPositionMs = startMs > 0 ? startMs : resumeMs,
            DurationMs = durationMs,
            VideoTracks = videoTracks,
            AudioTracks = audioTracks,
            SubtitleTracks = subtitleTracks,
            PlaySessionId = playSessionId,
            MediaSourceId = mediaSourceId,
            Direct = !useTranscode,
            DynamicRange = videoRangeType ?? videoRange,
            DolbyVision = dolbyVision,
            Container = container,
            AudioStreamIndex = audioIndex,
            SubtitleStreamIndex = subtitleIndex,
            SubtitleBurnedIn = useTranscode && burnSubtitle,
        };
    }

    private static bool IsImageSubtitle(string? codec) => (codec ?? "").ToLowerInvariant() switch
    {
        "pgssub" or "pgs" or "hdmv_pgs_subtitle" or "dvdsub" or "dvd_subtitle" or "dvbsub" or "dvb_subtitle" => true,
        _ => false,
    };

    private string BuildTranscodeUrl(
        string itemId, string mediaSourceId, string? playSessionId,
        int? audioIndex, int? subtitleIndex, bool burnSubtitle)
    {
        var sb = new System.Text.StringBuilder();
        sb.Append($"/Videos/{itemId}/master.m3u8?MediaSourceId={mediaSourceId}&DeviceId={Config.DeviceId}");
        if (!string.IsNullOrEmpty(playSessionId)) sb.Append($"&PlaySessionId={playSessionId}");
        sb.Append("&VideoCodec=h264&AudioCodec=aac,ac3,eac3&VideoBitrate=120000000&AudioBitrate=640000");
        sb.Append("&MaxAudioChannels=8&TranscodingMaxAudioChannels=8");
        if (audioIndex != null) sb.Append($"&AudioStreamIndex={audioIndex.Value}");
        if (burnSubtitle && subtitleIndex is >= 0)
            sb.Append($"&SubtitleStreamIndex={subtitleIndex.Value}&SubtitleMethod=Burn");
        else
            sb.Append("&SubtitleMethod=Encode");
        sb.Append("&SegmentContainer=ts&MinSegments=1&BreakOnNonKeyFrames=True&Context=Streaming&Protocol=hls");
        return sb.ToString();
    }

    /// <summary>详情页用：把 MediaSources 映射成版本，把选中版本的 MediaStreams 映射成音轨/字幕。</summary>
    public async Task<PlaybackOptions> GetPlaybackOptionsAsync(string itemId, string? sourceId = null, CancellationToken ct = default)
    {
        // 只是查轨道清单，不能置 IsPlayback=true，否则服务器会认为已经开播，
        // 留下幽灵会话并影响观看记录
        var body = JsonSerializer.Serialize(new
        {
            UserId = _userId,
            IsPlayback = false,
            AutoOpenLiveStream = false,
            MaxStreamingBitrate = 200_000_000,
            DeviceProfile = BuildDeviceProfile(false),
        });
        var text = await Http.PostJsonAsync(
            Url($"/Items/{itemId}/PlaybackInfo?UserId={_userId}"), body, TokenHeaders(), ct).ConfigureAwait(false);
        using var doc = JsonExt.Parse(text) ?? throw new InvalidOperationException("无法获取播放信息");
        var root = doc.RootElement;
        var sources = root.Arr("MediaSources");
        if (sources.Count == 0) return new PlaybackOptions();

        var options = new PlaybackOptions();
        for (var i = 0; i < sources.Count; i++)
        {
            var s = sources[i];
            var streams = s.Arr("MediaStreams");
            var video = streams.FirstOrDefault(v => v.Str("Type") == "Video");
            var audio = streams.FirstOrDefault(v => v.Str("Type") == "Audio");
            var width = video.Int("Width");
            var height = video.Int("Height");
            options.Versions.Add(new MediaVersion
            {
                Id = s.Str("Id") ?? itemId,
                Name = s.Str("Name") ?? BuildVersionName(width, height, video.Str("Codec"), audio.Str("Codec")) ?? $"版本 {i + 1}",
                Container = s.Str("Container"),
                Width = width,
                Height = height,
                Bitrate = s.Int("Bitrate"),
                SizeBytes = s.Long("Size"),
                VideoCodec = video.Str("Codec"),
                AudioCodec = audio.Str("Codec"),
                DynamicRange = video.Str("VideoRangeType") ?? video.Str("VideoRange"),
                IsDefault = i == 0,
            });
        }

        var active = sources.FirstOrDefault(s => s.Str("Id") == sourceId);
        if (active.ValueKind != JsonValueKind.Object) active = sources[0];
        var activeStreams = active.Arr("MediaStreams");

        var audioIdx = 0;
        foreach (var s in activeStreams.Where(v => v.Str("Type") == "Audio"))
        {
            options.AudioTracks.Add(new MediaTrack
            {
                Index = s.Int("Index") ?? audioIdx,
                Label = s.Str("DisplayTitle") ?? s.Str("Title") ?? $"音轨 {audioIdx + 1}",
                Language = s.Str("Language"),
                Codec = s.Str("Codec"),
                Channels = s.Int("Channels"),
                Bitrate = s.Int("BitRate"),
            });
            audioIdx++;
        }

        var subIdx = 0;
        foreach (var s in activeStreams.Where(v => v.Str("Type") == "Subtitle"))
        {
            var delivery = s.Str("DeliveryUrl");
            options.SubtitleTracks.Add(new SubtitleTrack
            {
                Index = s.Int("Index") ?? subIdx,
                Label = s.Str("DisplayTitle") ?? s.Str("Title") ?? $"字幕 {subIdx + 1}",
                Language = s.Str("Language"),
                Codec = s.Str("Codec"),
                Url = delivery != null
                    ? Url(delivery) + (delivery.Contains('?') ? "&" : "?") + $"api_key={_token}"
                    : null,
                IsExternal = s.Bool("IsExternal") ?? (delivery != null),
                ImageBased = IsImageSubtitle(s.Str("Codec")),
                Forced = s.Bool("IsForced") ?? false,
            });
            subIdx++;
        }

        options.DefaultAudioIndex = options.AudioTracks.FirstOrDefault()?.Index;
        options.DefaultSubtitleIndex = options.SubtitleTracks.FirstOrDefault()?.Index;
        return options;
    }

    private static string? BuildVersionName(int? width, int? height, string? videoCodec, string? audioCodec)
    {
        if (width == null || height == null) return null;
        var res = width >= 7000 || height >= 3500 ? "8K"
            : width >= 3500 || height >= 1900 ? "4K"
            : width >= 1800 || height >= 1000 ? "1080P"
            : $"{height}P";
        return string.Join(" · ", new[] { res, videoCodec?.ToUpperInvariant(), audioCodec?.ToUpperInvariant() }
            .Where(s => !string.IsNullOrEmpty(s)));
    }

    private static object BuildDeviceProfile(bool forceTranscode)
    {
        var profile = new Dictionary<string, object>
        {
            ["MaxStreamingBitrate"] = 200_000_000,
            ["MaxStaticBitrate"] = 200_000_000,
            ["TranscodingProfiles"] = new object[]
            {
                new Dictionary<string, object>
                {
                    ["Container"] = "ts",
                    ["Type"] = "Video",
                    ["VideoCodec"] = "h264",
                    ["AudioCodec"] = "aac,ac3,eac3",
                    ["Context"] = "Streaming",
                    ["Protocol"] = "hls",
                    ["MaxAudioChannels"] = "8",
                    ["MinSegments"] = 1,
                    ["BreakOnNonKeyFrames"] = true,
                },
            },
            ["SubtitleProfiles"] = new object[]
                {
                    new { Format = "srt", Method = "External" },
                    new { Format = "ass", Method = "External" },
                    new { Format = "ssa", Method = "External" },
                    new { Format = "subrip", Method = "External" },
                    new { Format = "vtt", Method = "External" },
                    new { Format = "pgssub", Method = "Embed" },
                    new { Format = "dvdsub", Method = "Embed" },
                    new { Format = "dvbsub", Method = "Embed" },
                },
        };

        if (!forceTranscode)
        {
            profile["DirectPlayProfiles"] = new object[]
            {
                new Dictionary<string, object>
                {
                    ["Container"] = "mkv,mp4,m4v,ts,m2ts,avi,mov,webm,wmv,flv,iso,strm,mpeg,mpg",
                    ["Type"] = "Video",
                    ["VideoCodec"] = "h264,hevc,h265,av1,vp9,vc1,mpeg2video,mpeg4,msmpeg4",
                    ["AudioCodec"] = "aac,ac3,eac3,dts,truehd,mlp,flac,opus,vorbis,mp3,alac,pcm_s16le,pcm_s24le,pcm_bluray",
                },
            };
        }

        return profile;
    }

    public async Task ReportStartAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default)
    {
        try
        {
            await Http.PostJsonAsync(Url("/Sessions/Playing"), SessionBody(plan, positionMs, false, true), TokenHeaders(), ct)
                .ConfigureAwait(false);
        }
        catch
        {
            // 上报失败不影响播放
        }
    }

    public async Task ReportProgressAsync(PlaybackPlan plan, long positionMs, bool paused, CancellationToken ct = default)
    {
        try
        {
            await Http.PostJsonAsync(Url("/Sessions/Playing/Progress"), SessionBody(plan, positionMs, paused, false), TokenHeaders(), ct)
                .ConfigureAwait(false);
        }
        catch
        {
        }
    }

    public async Task ReportStopAsync(PlaybackPlan plan, long positionMs, CancellationToken ct = default)
    {
        try
        {
            // 完整 body：缺 PlaySessionId / PlayMethod 时部分 Emby 版本会把这次停止
            // 当成「未知会话结束」，直接清掉该条目的观看记录。
            var payload = new Dictionary<string, object>
            {
                ["ItemId"] = plan.ItemId,
                ["PositionTicks"] = positionMs * 10_000L,
                ["Failed"] = false,
                ["CanSeek"] = true,
                ["IsPaused"] = true,
                ["IsMuted"] = false,
                ["PlayMethod"] = plan.Direct ? "DirectPlay" : "Transcode",
                ["PlaylistLength"] = 1,
                ["PlaylistIndex"] = 0,
            };
            if (plan.MediaSourceId != null) payload["MediaSourceId"] = plan.MediaSourceId;
            if (plan.PlaySessionId != null) payload["PlaySessionId"] = plan.PlaySessionId;
            await Http.PostJsonAsync(Url("/Sessions/Playing/Stopped"), JsonSerializer.Serialize(payload), TokenHeaders(), ct)
                .ConfigureAwait(false);
        }
        catch
        {
        }
    }

    /// <summary>
    /// 非会话方式直接写用户数据。会话上报失败或被系统打断时靠这一步兜底，
    /// 避免「继续观看」里的进度被清零。
    /// </summary>
    public async Task PersistPositionAsync(PlaybackPlan plan, long positionMs, bool completed, CancellationToken ct = default)
    {
        try
        {
            var payload = new Dictionary<string, object>
            {
                ["PlaybackPositionTicks"] = positionMs * 10_000L,
                ["Played"] = completed,
            };
            await Http.PostJsonAsync(
                Url($"/Users/{_userId}/Items/{plan.ItemId}/UserData"),
                JsonSerializer.Serialize(payload), TokenHeaders(), ct).ConfigureAwait(false);
        }
        catch
        {
        }
    }

    private static string SessionBody(PlaybackPlan plan, long positionMs, bool paused, bool start)
    {
        var payload = new Dictionary<string, object>
        {
            ["ItemId"] = plan.ItemId,
            ["PositionTicks"] = positionMs * 10_000L,
            ["IsPaused"] = paused,
            ["IsMuted"] = false,
            ["CanSeek"] = true,
            ["PlayMethod"] = plan.Direct ? "DirectPlay" : "Transcode",
            ["RepeatMode"] = "RepeatNone",
        };
        if (plan.MediaSourceId != null) payload["MediaSourceId"] = plan.MediaSourceId;
        if (plan.PlaySessionId != null) payload["PlaySessionId"] = plan.PlaySessionId;
        if (plan.AudioStreamIndex != null) payload["AudioStreamIndex"] = plan.AudioStreamIndex.Value;
        if (plan.SubtitleStreamIndex != null) payload["SubtitleStreamIndex"] = plan.SubtitleStreamIndex.Value;
        if (start)
        {
            payload["PlaylistLength"] = 1;
            payload["PlaylistIndex"] = 0;
        }
        return JsonSerializer.Serialize(payload);
    }

    public async Task SetFavoriteAsync(string itemId, bool favorite, CancellationToken ct = default)
    {
        try
        {
            if (favorite)
                await Http.PostJsonAsync(Url($"/Users/{_userId}/FavoriteItems/{itemId}"), "", TokenHeaders(), ct).ConfigureAwait(false);
            else
                await Http.DeleteJsonAsync(Url($"/Users/{_userId}/FavoriteItems/{itemId}"), null, TokenHeaders(), ct).ConfigureAwait(false);
        }
        catch
        {
        }
    }

    public async Task SetWatchedAsync(string itemId, bool watched, CancellationToken ct = default)
    {
        try
        {
            if (watched)
                await Http.PostJsonAsync(Url($"/Users/{_userId}/PlayedItems/{itemId}"), "", TokenHeaders(), ct).ConfigureAwait(false);
            else
                await Http.DeleteJsonAsync(Url($"/Users/{_userId}/PlayedItems/{itemId}"), null, TokenHeaders(), ct).ConfigureAwait(false);
        }
        catch
        {
        }
    }

    /// <summary>Emby 的外挂字幕地址自带 api_key，mpv 可直接加载，无需落地。</summary>
    public Task<bool> DownloadSubtitleAsync(SubtitleTrack track, string destPath, CancellationToken ct = default)
        => Task.FromResult(false);

    // ------------------------------------------------------------- 工具

    private string? PrimaryImage(string id, JsonElement o)
    {
        var tag = o.Obj("ImageTags").Str("Primary");
        if (tag == null) return null;
        return Url($"/Items/{id}/Images/Primary?maxWidth=600&quality=88&tag={tag}&api_key={_token}");
    }

    private string? BackdropImage(string id, JsonElement o)
    {
        if (o.Arr("BackdropImageTags").Count == 0) return null;
        return Url($"/Items/{id}/Images/Backdrop/0?maxWidth=1800&quality=85&api_key={_token}");
    }

    private MediaCard? MapCard(JsonElement element)
    {
        if (element.ValueKind != JsonValueKind.Object) return null;
        var id = element.Str("Id");
        if (string.IsNullOrEmpty(id)) return null;

        var type = ParseType(element.Str("Type"));
        var ud = element.Obj("UserData");
        var tag = element.Obj("ImageTags").Str("Primary");
        var poster = tag != null
            ? Url($"/Items/{id}/Images/Primary?maxWidth=500&quality=85&tag={tag}&api_key={_token}")
            : null;
        var hasBackdrop = element.Arr("BackdropImageTags").Count > 0;

        return new MediaCard
        {
            Id = id,
            Title = element.StrOr("Name", "未命名"),
            Subtitle = SubtitleOf(element, type),
            PosterUrl = poster,
            BackdropUrl = hasBackdrop
                ? Url($"/Items/{id}/Images/Backdrop/0?maxWidth=1400&quality=80&api_key={_token}")
                : poster,
            Type = type,
            Progress = (ud.Dbl("PlayedPercentage") ?? 0) / 100.0,
            Played = ud.Bool("Played") ?? false,
            Year = element.Int("ProductionYear"),
            Rating = element.Dbl("CommunityRating"),
            EpisodeCount = type == MediaType.Series ? EpisodeCountOf(element) : null,
        };
    }

    /// <summary>剧集总集数：优先 RecursiveItemCount，没有就退回 ChildCount。</summary>
    private static int? EpisodeCountOf(JsonElement element)
    {
        var n = element.Int("RecursiveItemCount");
        if (n is not > 0) n = element.Int("ChildCount");
        return n is > 0 ? n : null;
    }

    private static string? SubtitleOf(JsonElement element, MediaType type)
    {
        if (type == MediaType.Episode)
        {
            var episode = element.Int("IndexNumber") ?? 0;
            return episode > 0 ? $"第 {episode} 集" : null;
        }
        return element.Int("ProductionYear")?.ToString();
    }

    private static MediaType ParseType(string? raw) => raw?.ToLowerInvariant() switch
    {
        "movie" => MediaType.Movie,
        "series" => MediaType.Series,
        "season" => MediaType.Season,
        "episode" => MediaType.Episode,
        "video" or "musicvideo" => MediaType.Video,
        "folder" or "collectionfolder" or "boxset" => MediaType.Folder,
        _ => MediaType.Unknown,
    };
}
