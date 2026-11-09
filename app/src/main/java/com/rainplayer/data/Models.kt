package com.rainplayer.data

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
    /** 图形字幕（PGS / DVD / DVB）：本机无法渲染，只能让服务器烧录进画面。 */
    val imageBased: Boolean = false,
    val forced: Boolean = false,
    val selected: Boolean = false,
) {
    val codecLabel: String
        get() = when (codec?.lowercase()) {
            "pgssub", "pgs", "hdmv_pgs_subtitle" -> "PGS"
            "dvdsub", "dvd_subtitle" -> "VOBSUB"
            "dvbsub", "dvb_subtitle" -> "DVB"
            "subrip", "srt" -> "SRT"
            "ass", "ssa" -> "ASS"
            "webvtt", "vtt" -> "VTT"
            "ttml", "dfxp" -> "TTML"
            else -> codec?.uppercase() ?: "字幕"
        }
}

/** 同一部影片的不同版本（不同分辨率 / 编码 / 音轨组合）。 */
data class MediaVersion(
    val id: String,
    val name: String,
    val container: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val sizeBytes: Long? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val dynamicRange: String? = null,
    val isDefault: Boolean = false,
) {
    /** 例：4K · HEVC · DTS-HD MA · 24.5 Mbps */
    val summary: String
        get() = listOfNotNull(
            resolutionLabel,
            videoCodec?.uppercase(),
            audioCodec?.uppercase(),
            bitrate?.takeIf { it > 0 }?.let { "%.1f Mbps".format(it / 1_000_000.0) },
        ).joinToString(" · ")

    val resolutionLabel: String?
        get() = when {
            width == null || height == null -> null
            width >= 7000 || height >= 3500 -> "8K"
            width >= 3500 || height >= 1900 -> "4K"
            width >= 1800 || height >= 1000 -> "1080P"
            else -> "${width}P"
        }
}

/** 播放前可选的轨道清单（详情页用来让用户挑版本/音轨/字幕）。 */
data class PlaybackOptions(
    val versions: List<MediaVersion> = emptyList(),
    val audioTracks: List<MediaTrack> = emptyList(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val defaultAudioIndex: Int? = null,
    val defaultSubtitleIndex: Int? = null,
) {
    val hasVersions: Boolean get() = versions.size > 1
    val hasAudioChoice: Boolean get() = audioTracks.size > 1
    val hasSubtitleChoice: Boolean get() = subtitleTracks.isNotEmpty()
}

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
    /** 用户选中的音轨 / 字幕序号（服务器侧 stream index）。 */
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    /** 字幕是否已由服务器烧录进画面（图形字幕无法在本机渲染时的兜底）。 */
    val subtitleBurnedIn: Boolean = false,
    val versions: List<MediaVersion> = emptyList(),
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
