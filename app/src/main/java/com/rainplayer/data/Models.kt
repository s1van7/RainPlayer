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

/** 一台服务器的一条线路（同一个服务器的多个访问地址，比如内网 / 外网 / 中转）。 */
@Serializable
data class ServerLine(
    val id: String = "",
    val name: String = "",
    val url: String = "",
) {
    val displayName: String get() = name.ifBlank { url }
}

/** 一台服务器上的一个登录账号。同一个服务器可以挂多个账号。 */
@Serializable
data class ServerUser(
    val id: String = "",
    val username: String = "",
    val token: String = "",
    val userId: String = "",
) {
    val displayName: String get() = username.ifBlank { "账号" }
}

/**
 * 一台服务器的完整配置。
 *
 * 结构说明：
 * - [lines] / [users] 是「目录」，记录这台服务器配了哪些地址、登了哪些账号；
 * - [url] / [token] / [userId] / [username] 是「当前生效值」，由 [activeLineId] /
 *   [activeUserId] 指向的条目折叠而来。
 *
 * 之所以把当前值冗余存一份，是为了让后端实现（EmbyBackend / FnOsBackend）
 * 继续直接读 `config.url`、`config.token`，不用在每次请求时做一次查表。
 * 切换线路/账号时调用 [withLine] / [withUser] 重算这些字段即可。
 */
@Serializable
data class ServerConfig(
    val id: String = "",
    val kind: ServerKind = ServerKind.EMBY,
    val name: String = "",
    /** 自定义服务器图标：支持 http(s) 图片地址，也支持 emoji / 单个汉字。 */
    val iconUrl: String = "",
    val lines: List<ServerLine> = emptyList(),
    val users: List<ServerUser> = emptyList(),
    val activeLineId: String = "",
    val activeUserId: String = "",
    // 当前生效值（由 lines/users 折叠得到，见类注释）
    val url: String = "",
    val username: String = "",
    val token: String = "",
    val userId: String = "",
    val deviceId: String = "",
    /** 首页隐藏的媒体库 id。 */
    val hiddenLibraries: List<String> = emptyList(),
    /** 首页媒体库显示顺序（存 id）；未列出的排在后面。 */
    val libraryOrder: List<String> = emptyList(),
) {

    val activeLine: ServerLine?
        get() = lines.firstOrNull { it.id == activeLineId } ?: lines.firstOrNull()

    val activeUser: ServerUser?
        get() = users.firstOrNull { it.id == activeUserId } ?: users.firstOrNull()

    /** 兼容老数据：把旧的单 url / 单 token 折叠成目录。幂等，可反复调用。 */
    fun normalize(): ServerConfig {
        if (id.isBlank()) return this
        var result = this

        if (result.lines.isEmpty() && result.url.isNotBlank()) {
            result = result.copy(lines = listOf(ServerLine(id = "line-0", name = "主线路", url = result.url)))
        }
        if (result.users.isEmpty() && result.token.isNotBlank()) {
            result = result.copy(
                users = listOf(
                    ServerUser(
                        id = "user-0",
                        username = result.username,
                        token = result.token,
                        userId = result.userId,
                    )
                )
            )
        }
        val line = result.lines.firstOrNull { it.id == result.activeLineId } ?: result.lines.firstOrNull()
        val user = result.users.firstOrNull { it.id == result.activeUserId } ?: result.users.firstOrNull()
        return result.copy(
            activeLineId = line?.id ?: "",
            activeUserId = user?.id ?: "",
            url = line?.url ?: result.url,
            username = user?.username ?: result.username,
            token = user?.token ?: result.token,
            userId = user?.userId ?: result.userId,
        )
    }

    fun withLine(lineId: String): ServerConfig {
        val line = lines.firstOrNull { it.id == lineId } ?: return this
        return copy(activeLineId = line.id, url = line.url)
    }

    fun withUser(userId: String): ServerConfig {
        val user = users.firstOrNull { it.id == userId } ?: return this
        return copy(
            activeUserId = user.id,
            username = user.username,
            token = user.token,
            userId = user.userId,
        )
    }

    fun addLine(line: ServerLine): ServerConfig {
        val list = lines + line
        return if (activeLineId.isBlank() || lines.isEmpty()) {
            copy(lines = list).withLine(line.id)
        } else {
            copy(lines = list)
        }
    }

    fun updateLine(line: ServerLine): ServerConfig {
        val list = lines.map { if (it.id == line.id) line else it }
        val next = copy(lines = list)
        return if (activeLineId == line.id) next.withLine(line.id) else next
    }

    fun removeLine(lineId: String): ServerConfig {
        if (lines.size <= 1) return this
        val list = lines.filterNot { it.id == lineId }
        val next = copy(lines = list)
        return if (activeLineId == lineId) next.withLine(list.first().id) else next
    }

    fun addUser(user: ServerUser): ServerConfig {
        val list = users.filterNot { it.id == user.id } + user
        val next = copy(users = list)
        return if (activeUserId.isBlank() || users.isEmpty()) next.withUser(user.id) else next
    }

    fun removeUser(userId: String): ServerConfig {
        if (users.size <= 1) return this
        val list = users.filterNot { it.id == userId }
        val next = copy(users = list)
        return if (activeUserId == userId) next.withUser(list.first().id) else next
    }

    /** 列表里展示的副标题：线路数 + 账号数。 */
    val summary: String
        get() = buildList {
            add(kind.displayName)
            if (lines.size > 1) add("${lines.size} 条线路")
            if (users.size > 1) add("${users.size} 个账号")
        }.joinToString(" · ")

    /** 图标显示用：URL 走图片，否则当文本（emoji/首字母）。 */
    val iconIsUrl: Boolean
        get() = iconUrl.startsWith("http://") || iconUrl.startsWith("https://")

    val iconText: String
        get() = iconUrl.takeIf { !iconIsUrl && it.isNotBlank() }
            ?: name.take(1).uppercase().ifBlank { "R" }

    /**
     * 服务器自带的账号头像。
     *
     * Emby / Jellyfin 都提供 `/Users/{id}/Images/Primary`，用户没设头像时返回 404。
     * 有这个地址就优先显示它，用户手动填的图标只作为兜底，不去覆盖服务器头像。
     */
    val userAvatarUrl: String?
        get() {
            if (kind == ServerKind.FNOS) return null
            val user = activeUser ?: return null
            if (user.userId.isBlank() || user.token.isBlank() || url.isBlank()) return null
            return "${url.trimEnd('/')}/Users/${user.userId}/Images/Primary" +
                "?maxHeight=192&quality=80&api_key=${user.token}"
        }
}


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
    /** 已看时长（毫秒），用于「继续观看」卡片上的 11:14 / 48:09。 */
    val progressMs: Long = 0L,
    /** 总时长（毫秒）。 */
    val durationMs: Long = 0L,
    /** 聚合搜索时标注来源服务器；单服务器场景为 null。 */
    val serverId: String? = null,
    /** 聚合搜索时标注来源账号。 */
    val accountId: String? = null,
    /** 来源服务器名，用于聚合结果上的小标签。 */
    val serverName: String? = null,
    /** 剧集单集所属的季号，用于列表副标题。 */
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
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
    val progressMs: Long = 0L,
    val airDate: String? = null,
    val seriesId: String? = null,
    /** 所属季的 id，用于从单集跳到同季其它集。 */
    val seasonId: String? = null,
    val seriesName: String? = null,
    /** 单集自己的背景图，详情页大图用。 */
    val backdropUrl: String? = null,
) {
    /** 例：S02E05 · 42 分钟 */
    val label: String
        get() = buildString {
            append("S%02dE%02d".format(seasonIndex, index))
            runtimeMinutes?.takeIf { it > 0 }?.let { append(" · ").append("$it 分钟") }
        }
}

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
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    /** 剧集类条目直接开播时使用的第一个可播放条目。 */
    val playTargetId: String = id,
    val studio: String? = null,
    val premiereDate: String? = null,
    val communityRating: Double? = null,
    val criticRating: Double? = null,
    val similar: List<MediaCard> = emptyList(),
)

data class PagedItems(
    val items: List<MediaCard>,
    val total: Int,
    val startIndex: Int,
)

enum class RowKind { CONTINUE, LATEST, LIBRARY, DAILY }

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
