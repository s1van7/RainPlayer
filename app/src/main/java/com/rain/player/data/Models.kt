package com.rain.player.data

import kotlinx.serialization.Serializable

/** 服务器类型。Emby 与 Jellyfin 共用 Emby REST API 协议。 */
enum class ServerKind {
    EMBY,
    JELLYFIN,
    FNOS;

    val displayName: String
        get() = when (this) {
            EMBY -> "Emby"
            JELLYFIN -> "Jellyfin"
            FNOS -> "飞牛影视"
        }
}

@Serializable
data class ServerConfig(
    val id: String = "",
    val kind: ServerKind = ServerKind.EMBY,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val token: String = "",
    val userId: String = "",
    val deviceId: String = "",
)

enum class MediaType { MOVIE, SERIES, SEASON, EPISODE, VIDEO, FOLDER, UNKNOWN }

/** 列表卡片（首页/媒体库/搜索通用）。 */
data class MediaCard(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val type: MediaType = MediaType.UNKNOWN,
    val progress: Float = 0f,
    val played: Boolean = false,
    val year: Int? = null,
    val rating: Double? = null,
)

data class Library(
    val id: String,
    val name: String,
    val collectionType: String? = null,
)

data class Person(
    val name: String,
    val role: String? = null,
    val imageUrl: String? = null,
)

data class Season(
    val id: String,
    val name: String,
    val index: Int,
)

data class Episode(
    val id: String,
    val title: String,
    val index: Int,
    val seasonIndex: Int,
    val overview: String? = null,
    val imageUrl: String? = null,
    val runtimeMinutes: Int? = null,
    val played: Boolean = false,
    val progress: Float = 0f,
)

data class MediaDetail(
    val id: String,
    val title: String,
    val originalTitle: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val officialRating: String? = null,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int? = null,
    val type: MediaType = MediaType.UNKNOWN,
    val people: List<Person> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val favorite: Boolean = false,
    val played: Boolean = false,
    val progress: Float = 0f,
    /** 剧集类条目直接开播时使用的第一个可播放条目。 */
    val playTargetId: String = id,
    val studio: String? = null,
)

data class PagedItems(
    val items: List<MediaCard>,
    val total: Int,
    val startIndex: Int,
)

enum class RowKind { CONTINUE, LATEST, LIBRARY }

data class HomeRow(
    val title: String,
    val items: List<MediaCard>,
    val kind: RowKind = RowKind.LIBRARY,
    val libraryId: String? = null,
)

data class MediaTrack(
    val index: Int,
    val label: String,
    val language: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val bitrate: Int? = null,
)

data class SubtitleTrack(
    val index: Int,
    val label: String,
    val language: String? = null,
    val codec: String? = null,
    val url: String? = null,
    val isExternal: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

/** 一次播放所需的全部信息：地址、请求头、起始进度、轨道清单。 */
data class PlaybackPlan(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val isHls: Boolean = false,
    val startPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val videoTracks: List<MediaTrack> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val playSessionId: String? = null,
    val mediaSourceId: String? = null,
    val direct: Boolean = true,
    val dynamicRange: String? = null,
    val dolbyVision: Boolean = false,
    val container: String? = null,
    val fnMediaGuid: String? = null,
    val fnVideoGuid: String? = null,
    val fnAudioGuid: String? = null,
    val fnSubtitleGuid: String? = null,
    val fnResolution: String? = null,
    val fnBitrate: Int = 0,
    val fnDirectAudioIndex: Int = 0,
    val posterUrl: String? = null,
    val nextItemId: String? = null,
)

/** 播放器运行时探测到的能力，用于 UI 上给出「是否直通/HDR/DV」提示。 */
data class PlaybackCapabilities(
    val hdr10: Boolean = false,
    val hdr10Plus: Boolean = false,
    val hlg: Boolean = false,
    val dolbyVision: Boolean = false,
    val dolbyVisionProfiles: List<Int> = emptyList(),
    val ac3Passthrough: Boolean = false,
    val eac3Passthrough: Boolean = false,
    val dtsPassthrough: Boolean = false,
    val trueHdPassthrough: Boolean = false,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val maxRefreshRate: Float = 0f,
)
