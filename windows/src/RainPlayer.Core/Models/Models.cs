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

/// <summary>一台已登录的服务器配置。</summary>
public sealed class ServerConfig
{
    public string Id { get; set; } = "";
    public ServerKind Kind { get; set; } = ServerKind.Emby;
    public string Name { get; set; } = "";
    public string Url { get; set; } = "";
    public string Username { get; set; } = "";
    public string Token { get; set; } = "";
    public string UserId { get; set; } = "";
    public string DeviceId { get; set; } = "";

    public ServerConfig Clone() => new()
    {
        Id = Id,
        Kind = Kind,
        Name = Name,
        Url = Url,
        Username = Username,
        Token = Token,
        UserId = UserId,
        DeviceId = DeviceId,
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
}
