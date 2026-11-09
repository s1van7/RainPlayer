namespace RainPlayer.Core.Models;

/// <summary>服务器类型。Emby 与 Jellyfin 共用 Emby REST API 协议。</summary>
public enum ServerKind
{
    Emby,
    Jellyfin,
    FnOs,
}

public static class ServerKindExtensions
{
    public static string DisplayName(this ServerKind kind) => kind switch
    {
        ServerKind.Emby => "Emby",
        ServerKind.Jellyfin => "Jellyfin",
        ServerKind.FnOs => "飞牛影视",
        _ => "未知",
    };
}

/// <summary>一台服务器的一条线路（内网 / 外网 / 中转）。</summary>
public sealed class ServerLine
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Url { get; set; } = "";

    public string DisplayName => string.IsNullOrWhiteSpace(Name) ? Url : Name;

    public ServerLine Clone() => new() { Id = Id, Name = Name, Url = Url };
}

/// <summary>一台服务器上的一个登录账号。</summary>
public sealed class ServerUser
{
    public string Id { get; set; } = "";
    public string Username { get; set; } = "";
    public string Token { get; set; } = "";
    public string UserId { get; set; } = "";

    public string DisplayName => string.IsNullOrWhiteSpace(Username) ? "账号" : Username;

    public ServerUser Clone() => new() { Id = Id, Username = Username, Token = Token, UserId = UserId };
}

/// <summary>
/// 一台已登录的服务器配置。
///
/// <see cref="Lines"/> / <see cref="Users"/> 是目录，<see cref="Url"/> / <see cref="Token"/> /
/// <see cref="UserId"/> 是当前生效值，由 <see cref="ActiveLineId"/> / <see cref="ActiveUserId"/> 指向的条目折叠而来。
/// 这样后端实现继续直接读 Url / Token，不必每次请求都查表。
/// </summary>
public sealed class ServerConfig
{
    public string Id { get; set; } = "";
    public ServerKind Kind { get; set; } = ServerKind.Emby;
    public string Name { get; set; } = "";

    /// <summary>自定义图标：http(s) 图片地址，或 emoji / 单个汉字。</summary>
    public string IconUrl { get; set; } = "";

    public List<ServerLine> Lines { get; set; } = new();
    public List<ServerUser> Users { get; set; } = new();
    public string ActiveLineId { get; set; } = "";
    public string ActiveUserId { get; set; } = "";

    // 当前生效值（由 Lines / Users 折叠得到）
    public string Url { get; set; } = "";
    public string Username { get; set; } = "";
    public string Token { get; set; } = "";
    public string UserId { get; set; } = "";
    public string DeviceId { get; set; } = "";

    /// <summary>首页隐藏的媒体库 id。</summary>
    public List<string> HiddenLibraries { get; set; } = new();

    /// <summary>首页媒体库显示顺序（存 id）。</summary>
    public List<string> LibraryOrder { get; set; } = new();

    public ServerLine? ActiveLine =>
        Lines.FirstOrDefault(l => l.Id == ActiveLineId) ?? Lines.FirstOrDefault();

    public ServerUser? ActiveUser =>
        Users.FirstOrDefault(u => u.Id == ActiveUserId) ?? Users.FirstOrDefault();

    /// <summary>兼容老配置：把旧的单 Url / 单 Token 折叠成目录。幂等。</summary>
    public ServerConfig Normalize()
    {
        if (string.IsNullOrEmpty(Id)) return this;

        if (Lines.Count == 0 && !string.IsNullOrWhiteSpace(Url))
            Lines.Add(new ServerLine { Id = "line-0", Name = "主线路", Url = Url });

        if (Users.Count == 0 && !string.IsNullOrWhiteSpace(Token))
            Users.Add(new ServerUser { Id = "user-0", Username = Username, Token = Token, UserId = UserId });

        var line = Lines.FirstOrDefault(l => l.Id == ActiveLineId) ?? Lines.FirstOrDefault();
        var user = Users.FirstOrDefault(u => u.Id == ActiveUserId) ?? Users.FirstOrDefault();

        ActiveLineId = line?.Id ?? "";
        ActiveUserId = user?.Id ?? "";
        if (line != null) Url = line.Url;
        if (user != null)
        {
            Username = user.Username;
            Token = user.Token;
            UserId = user.UserId;
        }
        return this;
    }

    public ServerConfig WithLine(string lineId)
    {
        var line = Lines.FirstOrDefault(l => l.Id == lineId);
        if (line == null) return this;
        var copy = Clone();
        copy.ActiveLineId = line.Id;
        copy.Url = line.Url;
        return copy;
    }

    public ServerConfig WithUser(string userId)
    {
        var user = Users.FirstOrDefault(u => u.Id == userId);
        if (user == null) return this;
        var copy = Clone();
        copy.ActiveUserId = user.Id;
        copy.Username = user.Username;
        copy.Token = user.Token;
        copy.UserId = user.UserId;
        return copy;
    }

    public void AddLine(ServerLine line)
    {
        Lines.Add(line);
        if (string.IsNullOrEmpty(ActiveLineId)) ActiveLineId = line.Id;
        if (ActiveLineId == line.Id) Url = line.Url;
    }

    public void RemoveLine(string lineId)
    {
        if (Lines.Count <= 1) return;
        Lines.RemoveAll(l => l.Id == lineId);
        if (ActiveLineId == lineId) ActiveLineId = Lines[0].Id;
        var line = ActiveLine;
        if (line != null) Url = line.Url;
    }

    public void AddUser(ServerUser user)
    {
        Users.RemoveAll(u => u.Id == user.Id);
        Users.Add(user);
        if (string.IsNullOrEmpty(ActiveUserId)) ActiveUserId = user.Id;
    }

    public void RemoveUser(string userId)
    {
        if (Users.Count <= 1) return;
        Users.RemoveAll(u => u.Id == userId);
        if (ActiveUserId == userId) ActiveUserId = Users[0].Id;
        var user = ActiveUser;
        if (user != null)
        {
            Username = user.Username;
            Token = user.Token;
            UserId = user.UserId;
        }
    }

    /// <summary>例：Emby · 2 条线路 · 2 个账号</summary>
    public string Summary
    {
        get
        {
            var parts = new List<string> { Kind.DisplayName() };
            if (Lines.Count > 1) parts.Add($"{Lines.Count} 条线路");
            if (Users.Count > 1) parts.Add($"{Users.Count} 个账号");
            return string.Join(" · ", parts);
        }
    }

    public bool IconIsUrl =>
        IconUrl.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
        IconUrl.StartsWith("https://", StringComparison.OrdinalIgnoreCase);

    public string IconText =>
        !IconIsUrl && !string.IsNullOrWhiteSpace(IconUrl)
            ? IconUrl
            : (string.IsNullOrEmpty(Name) ? "R" : Name[..1].ToUpperInvariant());

    public ServerConfig Clone() => new()
    {
        Id = Id,
        Kind = Kind,
        Name = Name,
        IconUrl = IconUrl,
        Lines = Lines.Select(l => l.Clone()).ToList(),
        Users = Users.Select(u => u.Clone()).ToList(),
        ActiveLineId = ActiveLineId,
        ActiveUserId = ActiveUserId,
        Url = Url,
        Username = Username,
        Token = Token,
        UserId = UserId,
        DeviceId = DeviceId,
        HiddenLibraries = new List<string>(HiddenLibraries),
        LibraryOrder = new List<string>(LibraryOrder),
    };
}

public enum MediaType
{
    Movie,
    Series,
    Season,
    Episode,
    Video,
    Folder,
    Unknown,
}

/// <summary>列表卡片（首页/媒体库/搜索通用）。</summary>
public sealed class MediaCard
{
    public string Id { get; set; } = "";
    public string Title { get; set; } = "";
    public string? Subtitle { get; set; }
    public string? PosterUrl { get; set; }
    public string? BackdropUrl { get; set; }
    public MediaType Type { get; set; } = MediaType.Unknown;
    public double Progress { get; set; }
    public bool Played { get; set; }
    public int? Year { get; set; }
    public double? Rating { get; set; }

    /// <summary>聚合搜索时标注来源服务器；单服务器场景为 null。</summary>
    public string? ServerId { get; set; }
    public string? AccountId { get; set; }
    public string? ServerName { get; set; }
}

public sealed class Library
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string? CollectionType { get; set; }
}

public sealed class Person
{
    public string Name { get; set; } = "";
    public string? Role { get; set; }
    public string? ImageUrl { get; set; }
}

public sealed class Season
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public int Index { get; set; }
}

public sealed class Episode
{
    public string Id { get; set; } = "";
    public string Title { get; set; } = "";
    public int Index { get; set; }
    public int SeasonIndex { get; set; }
    public string? Overview { get; set; }
    public string? ImageUrl { get; set; }
    public int? RuntimeMinutes { get; set; }
    public bool Played { get; set; }
    public double Progress { get; set; }
    public string? AirDate { get; set; }
    public string? SeriesId { get; set; }
    public string? SeriesName { get; set; }
    public string? BackdropUrl { get; set; }

    /// <summary>例：S02E05 · 42 分钟</summary>
    public string Label
    {
        get
        {
            var text = $"S{SeasonIndex:00}E{Index:00}";
            if (RuntimeMinutes is > 0) text += $" · {RuntimeMinutes} 分钟";
            return text;
        }
    }
}

public sealed class MediaDetail
{
    public string Id { get; set; } = "";
    public string Title { get; set; } = "";
    public string? OriginalTitle { get; set; }
    public string? Overview { get; set; }
    public string? Tagline { get; set; }
    public string? PosterUrl { get; set; }
    public string? BackdropUrl { get; set; }
    public int? Year { get; set; }
    public double? Rating { get; set; }
    public string? OfficialRating { get; set; }
    public List<string> Genres { get; set; } = new();
    public int? RuntimeMinutes { get; set; }
    public MediaType Type { get; set; } = MediaType.Unknown;
    public List<Person> People { get; set; } = new();
    public List<Season> Seasons { get; set; } = new();
    public List<Episode> Episodes { get; set; } = new();
    public bool Favorite { get; set; }
    public bool Played { get; set; }
    public double Progress { get; set; }
    /// <summary>剧集类条目直接开播时使用的第一个可播放条目。</summary>
    public string PlayTargetId { get; set; } = "";
    public string? Studio { get; set; }
    public string? PremiereDate { get; set; }
    public double? CommunityRating { get; set; }
    public double? CriticRating { get; set; }
    public List<MediaCard> Similar { get; set; } = new();
}

/// <summary>外部评分聚合结果。所有字段可空，哪个源挂了只影响自己那一项。</summary>
public sealed class ExternalRatings
{
    public double? Tmdb { get; set; }
    public int? TmdbVotes { get; set; }
    public double? Douban { get; set; }
    public int? DoubanVotes { get; set; }
    public int? TmdbId { get; set; }
    public string? DoubanId { get; set; }

    public bool IsEmpty => Tmdb == null && Douban == null;
}

public sealed class PagedItems
{
    public List<MediaCard> Items { get; set; } = new();
    public int Total { get; set; }
    public int StartIndex { get; set; }
}

public enum RowKind
{
    Continue,
    Latest,
    Library,
    Daily,
}

public sealed class HomeRow
{
    public string Title { get; set; } = "";
    public List<MediaCard> Items { get; set; } = new();
    public RowKind Kind { get; set; } = RowKind.Library;
    public string? LibraryId { get; set; }
}

public sealed class MediaTrack
{
    public int Index { get; set; }
    public string Label { get; set; } = "";
    public string? Language { get; set; }
    public string? Codec { get; set; }
    public int? Channels { get; set; }
    public int? Bitrate { get; set; }
}

public sealed class SubtitleTrack
{
    public int Index { get; set; }
    public string Label { get; set; } = "";
    public string? Language { get; set; }
    public string? Codec { get; set; }
    public string? Url { get; set; }
    public bool IsExternal { get; set; }
    public Dictionary<string, string> Headers { get; set; } = new();

    /// <summary>图形字幕（PGS / DVD / DVB）。桌面端由 libmpv 直接渲染，无需转码。</summary>
    public bool ImageBased { get; set; }

    public bool Forced { get; set; }

    public string CodecLabel => (Codec ?? "").ToLowerInvariant() switch
    {
        "pgssub" or "pgs" or "hdmv_pgs_subtitle" => "PGS",
        "dvdsub" or "dvd_subtitle" => "VOBSUB",
        "dvbsub" or "dvb_subtitle" => "DVB",
        "subrip" or "srt" => "SRT",
        "ass" or "ssa" => "ASS",
        "webvtt" or "vtt" => "VTT",
        "ttml" or "dfxp" => "TTML",
        _ => string.IsNullOrEmpty(Codec) ? "字幕" : Codec!.ToUpperInvariant(),
    };
}

/// <summary>同一部影片的不同版本（不同分辨率 / 编码 / 音轨组合）。</summary>
public sealed class MediaVersion
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string? Container { get; set; }
    public int? Width { get; set; }
    public int? Height { get; set; }
    public int? Bitrate { get; set; }
    public long? SizeBytes { get; set; }
    public string? VideoCodec { get; set; }
    public string? AudioCodec { get; set; }
    public string? DynamicRange { get; set; }
    public bool IsDefault { get; set; }

    public string ResolutionLabel => (Width, Height) switch
    {
        (int w, int h) when w >= 7000 || h >= 3500 => "8K",
        (int w, int h) when w >= 3500 || h >= 1900 => "4K",
        (int w, int h) when w >= 1800 || h >= 1000 => "1080P",
        (int w, int _) => $"{w}P",
        _ => "",
    };

    public string Summary => string.Join(" · ", new[]
    {
        ResolutionLabel,
        VideoCodec?.ToUpperInvariant(),
        AudioCodec?.ToUpperInvariant(),
        Bitrate is > 0 ? $"{Bitrate.Value / 1_000_000.0:F1} Mbps" : null,
    }.Where(s => !string.IsNullOrEmpty(s)));
}

/// <summary>播放前可选的轨道清单（详情页用来挑版本/音轨/字幕）。</summary>
public sealed class PlaybackOptions
{
    public List<MediaVersion> Versions { get; set; } = new();
    public List<MediaTrack> AudioTracks { get; set; } = new();
    public List<SubtitleTrack> SubtitleTracks { get; set; } = new();
    public int? DefaultAudioIndex { get; set; }
    public int? DefaultSubtitleIndex { get; set; }

    public bool HasVersions => Versions.Count > 1;
    public bool HasAudioChoice => AudioTracks.Count > 1;
    public bool HasSubtitleChoice => SubtitleTracks.Count > 0;
}

/// <summary>一次播放所需的全部信息：地址、请求头、起始进度、轨道清单。</summary>
public sealed class PlaybackPlan
{
    public string ItemId { get; set; } = "";
    public string Title { get; set; } = "";
    public string? Subtitle { get; set; }
    public string Url { get; set; } = "";
    public Dictionary<string, string> Headers { get; set; } = new();
    public bool IsHls { get; set; }
    public long StartPositionMs { get; set; }
    public long DurationMs { get; set; }
    public List<MediaTrack> VideoTracks { get; set; } = new();
    public List<MediaTrack> AudioTracks { get; set; } = new();
    public List<SubtitleTrack> SubtitleTracks { get; set; } = new();
    public string? PlaySessionId { get; set; }
    public string? MediaSourceId { get; set; }
    public bool Direct { get; set; } = true;
    public string? DynamicRange { get; set; }
    public bool DolbyVision { get; set; }
    public string? Container { get; set; }

    // 飞牛专用字段
    public string? FnMediaGuid { get; set; }
    public string? FnVideoGuid { get; set; }
    public string? FnAudioGuid { get; set; }
    public string? FnSubtitleGuid { get; set; }
    public string? FnResolution { get; set; }
    public int FnBitrate { get; set; }
    public int FnDirectAudioIndex { get; set; }

    public string? PosterUrl { get; set; }
    public string? NextItemId { get; set; }

    /// <summary>用户选中的音轨 / 字幕序号（服务器侧 stream index）。</summary>
    public int? AudioStreamIndex { get; set; }
    public int? SubtitleStreamIndex { get; set; }

    /// <summary>字幕是否已由服务器烧录进画面（图形字幕无法在本机渲染时的兜底）。</summary>
    public bool SubtitleBurnedIn { get; set; }
}
