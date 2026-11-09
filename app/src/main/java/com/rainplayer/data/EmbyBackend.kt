package com.rainplayer.data

import com.rainplayer.core.Http
import com.rainplayer.core.J
import com.rainplayer.core.arr
import com.rainplayer.core.asArr
import com.rainplayer.core.asObj
import com.rainplayer.core.bool
import com.rainplayer.core.dbl
import com.rainplayer.core.int
import com.rainplayer.core.long
import com.rainplayer.core.obj
import com.rainplayer.core.str
import com.rainplayer.core.strOr
import com.rainplayer.core.stringList
import com.rainplayer.core.ticksToMs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLEncoder

/**
 * Emby / Jellyfin 后端。两者 REST 协议一致，仅少数字段差异。
 */
class EmbyBackend(override val config: ServerConfig) : MediaBackend {

    private val base = BackendFactory.normalizeUrl(config.url)
    private val token = config.token
    private val userId = config.userId

    private val authValue: String
        get() = "MediaBrowser Client=\"RainPlayer\", Device=\"${deviceName()}\", " +
            "DeviceId=\"${config.deviceId}\", Version=\"1.0.0\""

    /**
     * Emby 只认 X-Emby-Authorization；Jellyfin 10.x 主推标准的 Authorization。
     * 两者都同时接受 X-Emby-Token，所以 token 单独走这个头最稳。
     */
    private val authHeaderName: String
        get() = if (config.kind == ServerKind.JELLYFIN) "Authorization" else "X-Emby-Authorization"

    private fun deviceName(): String = "RainPlayer"

    init {
        Http.authInterceptor.update(base, tokenHeaders())
    }

    private fun tokenHeaders(): Map<String, String> = buildMap {
        put(authHeaderName, authValue)
        if (token.isNotBlank()) {
            put("X-Emby-Token", token)
            put("X-Emby-Authorization", "$authValue, Token=\"$token\"")
        }
    }

    private fun url(path: String): String = if (path.startsWith("http")) path else base + path

    private fun q(value: String): String = URLEncoder.encode(value, "UTF-8")

    // ------------------------------------------------------------------ 登录

    override suspend fun authenticate(username: String, password: String): ServerConfig {
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }.toString()
        val text = Http.postJson(
            url("/Users/AuthenticateByName"),
            body,
            mapOf(authHeaderName to authValue),
        )
        val root = J.obj(text) ?: error("服务器返回内容无法解析")
        val accessToken = root.str("AccessToken") ?: error("登录失败：未返回 AccessToken")
        val uid = root.obj("User")?.str("Id") ?: error("登录失败：未返回用户信息")
        return config.copy(
            token = accessToken,
            userId = uid,
            username = username,
            url = base,
            name = config.name.ifBlank { root.obj("ServerInfo")?.str("Name") ?: "Emby" },
        )
    }

    // ------------------------------------------------------------------ 媒体库

    override suspend fun libraries(): List<Library> {
        val text = Http.getText(url("/Users/$userId/Views"), tokenHeaders())
        val items = J.obj(text)?.arr("Items").orEmpty()
        return items.mapNotNull { element ->
            val o = element.asObj() ?: return@mapNotNull null
            val id = o.str("Id") ?: return@mapNotNull null
            Library(id, o.strOr("Name", "媒体库"), o.str("CollectionType"))
        }
    }

    override suspend fun homeRows(): List<HomeRow> {
        val rows = mutableListOf<HomeRow>()

        runCatching {
            val text = Http.getText(
                url(
                    "/Users/$userId/Items/Resume?Limit=20&MediaTypes=Video" +
                        "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,BackdropImageTags" +
                        "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop"
                ),
                tokenHeaders()
            )
            val items = J.obj(text)?.arr("Items").orEmpty().mapNotNull { mapCard(it.asObj()) }
            if (items.isNotEmpty()) rows += HomeRow("继续观看", items, RowKind.CONTINUE)
        }

        val libs = runCatching { libraries() }.getOrDefault(emptyList())
        libs.take(6).forEach { library ->
            runCatching {
                val text = Http.getText(
                    url(
                        "/Users/$userId/Items/Latest?ParentId=${library.id}&Limit=18" +
                            "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,BackdropImageTags" +
                            "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop"
                    ),
                    tokenHeaders()
                )
                val array = J.parse(text)?.asArr().orEmpty()
                val items = array.mapNotNull { mapCard(it.asObj()) }
                if (items.isNotEmpty()) {
                    rows += HomeRow(library.name, items, RowKind.LIBRARY, library.id)
                }
            }
        }
        return rows
    }

    override suspend fun items(libraryId: String, startIndex: Int, limit: Int): PagedItems {
        val text = Http.getText(
            url(
                "/Users/$userId/Items?ParentId=${q(libraryId)}&Recursive=true" +
                    "&IncludeItemTypes=Movie,Series,Video,Folder&SortBy=SortName&SortOrder=Ascending" +
                    "&StartIndex=$startIndex&Limit=$limit" +
                    "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,BackdropImageTags,CommunityRating" +
                    "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop"
            ),
            tokenHeaders()
        )
        val root = J.obj(text) ?: return PagedItems(emptyList(), 0, startIndex)
        val items = root.arr("Items").orEmpty().mapNotNull { mapCard(it.asObj()) }
        return PagedItems(items, root.int("TotalRecordCount") ?: items.size, startIndex)
    }

    override suspend fun search(query: String): List<MediaCard> {
        val text = Http.getText(
            url(
                "/Users/$userId/Items?SearchTerm=${q(query)}&Recursive=true&Limit=60" +
                    "&IncludeItemTypes=Movie,Series,Episode,Video" +
                    "&Fields=PrimaryImageTag,ProductionYear,RunTimeTicks,UserData,CommunityRating" +
                    "&ImageTypeLimit=1&EnableImageTypes=Primary"
            ),
            tokenHeaders()
        )
        return J.obj(text)?.arr("Items").orEmpty().mapNotNull { mapCard(it.asObj()) }
    }

    // ------------------------------------------------------------------ 详情

    override suspend fun detail(itemId: String): MediaDetail {
        val fields = "Overview,People,Genres,ProductionYear,RunTimeTicks,CommunityRating," +
            "OfficialRating,MediaSources,MediaStreams,Taglines,UserData,BackdropImageTags,ImageTags," +
            "PrimaryImageAspectRatio,Studios,ChildCount"
        val text = Http.getText(url("/Users/$userId/Items/$itemId?Fields=$fields"), tokenHeaders())
        val o = J.obj(text) ?: error("无法获取详情")
        val type = parseType(o.str("Type"))

        val people = o.arr("People").orEmpty().take(24).mapNotNull { element ->
            val p = element.asObj() ?: return@mapNotNull null
            val name = p.str("Name") ?: return@mapNotNull null
            Person(
                name = name,
                role = p.str("Role"),
                imageUrl = p.str("PrimaryImageTag")?.let {
                    url("/Items/${p.str("Id")}/Images/Primary?maxHeight=300&quality=85&api_key=$token")
                },
            )
        }

        val seasons = if (type == MediaType.SERIES) runCatching { seasons(itemId) }.getOrDefault(emptyList()) else emptyList()
        val firstSeason = seasons.firstOrNull()
        val episodes = if (type == MediaType.SERIES && firstSeason != null) {
            runCatching { episodes(itemId, firstSeason.id) }.getOrDefault(emptyList())
        } else emptyList()

        val userData = o.obj("UserData")
        val playTarget = when {
            type == MediaType.SERIES -> episodes.firstOrNull { !it.played }?.id ?: episodes.firstOrNull()?.id ?: itemId
            else -> itemId
        }

        return MediaDetail(
            id = itemId,
            title = o.strOr("Name", "未命名"),
            originalTitle = o.str("OriginalTitle"),
            overview = o.str("Overview"),
            tagline = o.stringList("Taglines").firstOrNull(),
            posterUrl = primaryImage(itemId, o),
            backdropUrl = backdropImage(itemId, o),
            year = o.int("ProductionYear"),
            rating = o.dbl("CommunityRating"),
            officialRating = o.str("OfficialRating"),
            genres = o.stringList("Genres"),
            runtimeMinutes = (ticksToMs(o.long("RunTimeTicks")) / 60_000L).toInt().takeIf { it > 0 },
            type = type,
            people = people,
            seasons = seasons,
            episodes = episodes,
            favorite = userData?.bool("IsFavorite") ?: false,
            played = userData?.bool("Played") ?: false,
            progress = ((userData?.dbl("PlayedPercentage") ?: 0.0) / 100.0).toFloat(),
            playTargetId = playTarget,
            studio = o.arr("Studios")?.firstOrNull()?.asObj()?.str("Name"),
        )
    }

    override suspend fun seasons(seriesId: String): List<Season> {
        val text = Http.getText(
            url("/Shows/$seriesId/Seasons?UserId=$userId&Fields=PrimaryImageTag"),
            tokenHeaders()
        )
        return J.obj(text)?.arr("Items").orEmpty().mapIndexedNotNull { index, element ->
            val o = element.asObj() ?: return@mapIndexedNotNull null
            val id = o.str("Id") ?: return@mapIndexedNotNull null
            Season(id, o.strOr("Name", "第 ${index + 1} 季"), o.int("IndexNumber") ?: (index + 1))
        }
    }

    override suspend fun episodes(seriesId: String, seasonId: String): List<Episode> {
        val text = Http.getText(
            url(
                "/Shows/$seriesId/Episodes?UserId=$userId&SeasonId=$seasonId&Fields=Overview,PrimaryImageTag,UserData,RunTimeTicks"
            ),
            tokenHeaders()
        )
        return J.obj(text)?.arr("Items").orEmpty().mapNotNull { element ->
            val o = element.asObj() ?: return@mapNotNull null
            val id = o.str("Id") ?: return@mapNotNull null
            val ud = o.obj("UserData")
            Episode(
                id = id,
                title = o.strOr("Name", "第 ${o.int("IndexNumber") ?: 0} 集"),
                index = o.int("IndexNumber") ?: 0,
                seasonIndex = o.int("ParentIndexNumber") ?: 0,
                overview = o.str("Overview"),
                imageUrl = if (o.obj("ImageTags")?.str("Primary") != null) {
                    url("/Items/$id/Images/Primary?maxWidth=480&quality=85&api_key=$token")
                } else null,
                runtimeMinutes = (ticksToMs(o.long("RunTimeTicks")) / 60_000L).toInt().takeIf { it > 0 },
                played = ud?.bool("Played") ?: false,
                progress = ((ud?.dbl("PlayedPercentage") ?: 0.0) / 100.0).toFloat(),
            )
        }
    }

    // ------------------------------------------------------------------ 播放

    /**
     * 拉一次 PlaybackInfo，把里面的 MediaSources 映射成「版本」，
     * 把选中版本的 MediaStreams 映射成音轨/字幕清单。
     */
    override suspend fun playbackOptions(itemId: String, sourceId: String?): PlaybackOptions {
        val root = requestPlaybackInfo(itemId, 0L, sourceId, null, null, isPlayback = false)
        val sources = root.arr("MediaSources").orEmpty().mapNotNull { it.asObj() }
        if (sources.isEmpty()) return PlaybackOptions()

        val versions = sources.mapIndexed { index, source ->
            val streams = source.arr("MediaStreams").orEmpty().mapNotNull { it.asObj() }
            val video = streams.firstOrNull { it.str("Type") == "Video" }
            val audio = streams.firstOrNull { it.str("Type") == "Audio" }
            val id = source.str("Id") ?: itemId
            val width = video?.int("Width")
            val height = video?.int("Height")
            val version = MediaVersion(
                id = id,
                name = source.str("Name")
                    ?: buildVersionName(width, height, video?.str("Codec"), audio?.str("Codec"))
                    ?: "版本 ${index + 1}",
                container = source.str("Container"),
                width = width,
                height = height,
                bitrate = source.int("Bitrate"),
                sizeBytes = source.long("Size"),
                videoCodec = video?.str("Codec"),
                audioCodec = audio?.str("Codec"),
                dynamicRange = video?.str("VideoRangeType") ?: video?.str("VideoRange"),
                isDefault = index == 0,
            )
            version
        }

        val active = sources.firstOrNull { it.str("Id") == sourceId } ?: sources.first()
        val streams = active.arr("MediaStreams").orEmpty().mapNotNull { it.asObj() }

        val audioTracks = streams.filter { it.str("Type") == "Audio" }.mapIndexed { index, s ->
            MediaTrack(
                index = s.int("Index") ?: index,
                label = s.str("DisplayTitle") ?: s.str("Title") ?: "音轨 ${index + 1}",
                language = s.str("Language"),
                codec = s.str("Codec"),
                channels = s.int("Channels"),
                bitrate = s.int("BitRate"),
            )
        }
        val subtitleTracks = streams.filter { it.str("Type") == "Subtitle" }.mapIndexed { index, s ->
            val delivery = s.str("DeliveryUrl")
            val codec = s.str("Codec")
            SubtitleTrack(
                index = s.int("Index") ?: index,
                label = s.str("DisplayTitle") ?: s.str("Title") ?: "字幕 ${index + 1}",
                language = s.str("Language"),
                codec = codec,
                url = delivery?.let { url(it) + (if (it.contains("?")) "&" else "?") + "api_key=$token" },
                isExternal = s.bool("IsExternal") ?: (delivery != null),
                imageBased = isImageSubtitle(codec),
                forced = s.bool("IsForced") ?: false,
                selected = s.bool("IsDefault") ?: false,
            )
        }
        val defaultAudio = audioTracks.firstOrNull { s ->
            streams.firstOrNull { it.int("Index") == s.index }?.bool("IsDefault") == true
        } ?: audioTracks.firstOrNull()

        return PlaybackOptions(
            versions = versions,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            defaultAudioIndex = defaultAudio?.index,
            defaultSubtitleIndex = subtitleTracks.firstOrNull { it.selected }?.index,
        )
    }

    private fun buildVersionName(width: Int?, height: Int?, videoCodec: String?, audioCodec: String?): String? {
        if (width == null || height == null) return null
        val res = when {
            width >= 7000 || height >= 3500 -> "8K"
            width >= 3500 || height >= 1900 -> "4K"
            width >= 1800 || height >= 1000 -> "1080P"
            else -> "${height}P"
        }
        return listOfNotNull(res, videoCodec?.uppercase(), audioCodec?.uppercase()).joinToString(" · ")
    }

    private fun isImageSubtitle(codec: String?): Boolean = when (codec?.lowercase()) {
        "pgssub", "pgs", "hdmv_pgs_subtitle", "dvdsub", "dvd_subtitle", "dvbsub", "dvb_subtitle" -> true
        else -> false
    }

    private suspend fun requestPlaybackInfo(
        itemId: String,
        startMs: Long,
        sourceId: String?,
        audioIndex: Int?,
        subtitleIndex: Int?,
        isPlayback: Boolean = true,
    ): JsonObject {
        val body = buildJsonObject {
            put("UserId", userId)
            put("StartTimeTicks", startMs * 10_000L)
            // 只是查轨道清单时不能置 true，否则服务器会认为已经开播，
            // 在「正在播放」里留下幽灵会话，还会把观看记录当成已开始。
            put("IsPlayback", isPlayback)
            put("AutoOpenLiveStream", true)
            put("MaxStreamingBitrate", 200_000_000)
            sourceId?.let { put("MediaSourceId", it) }
            audioIndex?.let { put("AudioStreamIndex", it) }
            subtitleIndex?.let { put("SubtitleStreamIndex", it) }
            put("DeviceProfile", deviceProfile(false))
        }.toString()
        var path = "/Items/$itemId/PlaybackInfo?UserId=$userId&StartTimeTicks=${startMs * 10_000L}"
        if (!sourceId.isNullOrBlank()) path += "&MediaSourceId=$sourceId"
        val text = Http.postJson(url(path), body, tokenHeaders())
        return J.obj(text) ?: error("无法获取播放信息")
    }

    override suspend fun playback(
        itemId: String,
        startMs: Long,
        forceTranscode: Boolean,
        sourceId: String?,
        audioIndex: Int?,
        subtitleIndex: Int?,
        burnSubtitle: Boolean,
    ): PlaybackPlan {
        val root = requestPlaybackInfo(itemId, startMs, sourceId, audioIndex, subtitleIndex)
        val sources = root.arr("MediaSources").orEmpty().mapNotNull { it.asObj() }
        val source = sources.firstOrNull { it.str("Id") == sourceId }
            ?: sources.firstOrNull()
            ?: error("服务器未返回媒体源")
        val mediaSourceId = source.str("Id") ?: itemId
        val playSessionId = root.str("PlaySessionId")
        val container = source.str("Container")
        val durationMs = ticksToMs(source.long("RunTimeTicks"))
        // 断点续播：服务器记录的上次播放位置（调用方未指定起点时使用）
        val resumeMs = ticksToMs(
            source.obj("UserData")?.long("PlaybackPositionTicks")
                ?: source.obj("UserData")?.long("PlaybackPosition")
        )

        val streams = source.arr("MediaStreams").orEmpty().mapNotNull { it.asObj() }
        val videoStream = streams.firstOrNull { it.str("Type") == "Video" }
        val audioTracks = streams.filter { it.str("Type") == "Audio" }.mapIndexed { index, s ->
            MediaTrack(
                index = s.int("Index") ?: index,
                label = s.str("DisplayTitle") ?: s.str("Title") ?: "音轨 ${index + 1}",
                language = s.str("Language"),
                codec = s.str("Codec"),
                channels = s.int("Channels"),
                bitrate = s.int("BitRate"),
            )
        }
        val subtitleTracks = streams.filter { it.str("Type") == "Subtitle" }.mapIndexed { index, s ->
            val delivery = s.str("DeliveryUrl")
            val codec = s.str("Codec")
            SubtitleTrack(
                index = s.int("Index") ?: index,
                label = s.str("DisplayTitle") ?: s.str("Title") ?: "字幕 ${index + 1}",
                language = s.str("Language"),
                codec = codec,
                url = delivery?.let { url(it) + (if (it.contains("?")) "&" else "?") + "api_key=$token" },
                isExternal = s.bool("IsExternal") ?: (delivery != null),
                imageBased = isImageSubtitle(codec),
                forced = s.bool("IsForced") ?: false,
            )
        }

        val videoRange = videoStream?.str("VideoRange")
        val videoRangeType = videoStream?.str("VideoRangeType")
        val dv = videoRangeType?.contains("DOVI", true) == true ||
            videoRange?.equals("DOVI", true) == true ||
            videoStream?.str("Codec")?.contains("dv", true) == true

        val transcodingUrl = source.str("TranscodingUrl")
        val supportsDirectPlay = source.bool("SupportsDirectPlay") ?: true
        // 图形字幕只能由服务器烧录，必须转码
        val useTranscode = forceTranscode || burnSubtitle || (!supportsDirectPlay && transcodingUrl != null)

        val playUrl: String
        var isHls = false
        if (useTranscode) {
            val built = transcodingUrl?.takeIf { forceTranscode || !burnSubtitle }
                ?: buildTranscodeUrl(itemId, mediaSourceId, playSessionId, audioIndex, subtitleIndex, burnSubtitle)
            playUrl = url(built) + (if (built.contains("api_key")) "" else "&api_key=$token")
            isHls = built.contains("m3u8", true) || built.contains("master", true) ||
                built.contains("master.m3u8", true)
        } else {
            playUrl = url(
                "/Videos/$itemId/stream?Static=true&MediaSourceId=$mediaSourceId" +
                    "&DeviceId=${config.deviceId}&api_key=$token"
            )
        }

        val title = runCatching {
            Http.getText(url("/Users/$userId/Items/$itemId"), tokenHeaders())
        }.getOrNull()?.let { J.obj(it) }?.str("Name") ?: "正在播放"

        return PlaybackPlan(
            itemId = itemId,
            title = title,
            url = playUrl,
            headers = tokenHeaders(),
            isHls = isHls,
            startPositionMs = if (startMs > 0) startMs else resumeMs,
            durationMs = durationMs,
            videoTracks = videoStream?.let {
                listOf(
                    MediaTrack(
                        index = it.int("Index") ?: 0,
                        label = it.str("DisplayTitle") ?: "视频",
                        codec = it.str("Codec"),
                    )
                )
            }.orEmpty(),
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            playSessionId = playSessionId,
            mediaSourceId = mediaSourceId,
            direct = !useTranscode,
            dynamicRange = videoRangeType ?: videoRange,
            dolbyVision = dv,
            container = container,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
            subtitleBurnedIn = useTranscode && burnSubtitle,
        )
    }

    /** 图形字幕 / 强制转码时自己拼一条 HLS 转码地址。 */
    private fun buildTranscodeUrl(
        itemId: String,
        mediaSourceId: String,
        playSessionId: String?,
        audioIndex: Int?,
        subtitleIndex: Int?,
        burnSubtitle: Boolean,
    ): String = buildString {
        append("/Videos/$itemId/master.m3u8")
        append("?MediaSourceId=$mediaSourceId")
        append("&DeviceId=${config.deviceId}")
        playSessionId?.let { append("&PlaySessionId=$it") }
        append("&VideoCodec=h264&AudioCodec=aac,ac3,eac3")
        append("&VideoBitrate=120000000&AudioBitrate=640000")
        append("&MaxAudioChannels=8&TranscodingMaxAudioChannels=8")
        audioIndex?.let { append("&AudioStreamIndex=$it") }
        if (burnSubtitle && subtitleIndex != null && subtitleIndex >= 0) {
            append("&SubtitleStreamIndex=$subtitleIndex")
            append("&SubtitleMethod=Burn")
        } else {
            append("&SubtitleMethod=Encode")
        }
        append("&SegmentContainer=ts&MinSegments=1&BreakOnNonKeyFrames=True")
        append("&Context=Streaming&Protocol=hls")
    }

    private fun deviceProfile(forceTranscode: Boolean): JsonObject = buildJsonObject {
        put("MaxStreamingBitrate", 200_000_000)
        put("MaxStaticBitrate", 200_000_000)
        if (!forceTranscode) {
            put(
                "DirectPlayProfiles",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("Container", "mkv,mp4,m4v,ts,m2ts,avi,mov,webm,wmv,flv,iso,strm,mpeg,mpg")
                            put("Type", "Video")
                            put(
                                "VideoCodec",
                                "h264,hevc,h265,av1,vp9,vc1,mpeg2video,mpeg4,msmpeg4"
                            )
                            put(
                                "AudioCodec",
                                "aac,ac3,eac3,dts,truehd,mlp,flac,opus,vorbis,mp3,alac,pcm_s16le,pcm_s24le,pcm_bluray"
                            )
                        }
                    )
                }
            )
        }
        put(
            "TranscodingProfiles",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("Container", "ts")
                        put("Type", "Video")
                        put("VideoCodec", "h264")
                        put("AudioCodec", "aac,ac3,eac3")
                        put("Context", "Streaming")
                        put("Protocol", "hls")
                        put("MaxAudioChannels", "8")
                        put("MinSegments", 1)
                        put("BreakOnNonKeyFrames", true)
                    }
                )
            }
        )
        put(
            "SubtitleProfiles",
            buildJsonArray {
                listOf("srt", "ass", "ssa", "subrip", "vtt").forEach { format ->
                    add(
                        buildJsonObject {
                            put("Format", format)
                            put("Method", "External")
                        }
                    )
                }
                listOf("pgssub", "dvdsub", "dvbsub").forEach { format ->
                    add(
                        buildJsonObject {
                            put("Format", format)
                            put("Method", "Embed")
                        }
                    )
                }
            }
        )
    }

    override suspend fun reportStart(plan: PlaybackPlan, positionMs: Long) {
        runCatching {
            Http.postJson(url("/Sessions/Playing"), sessionBody(plan, positionMs, false, true), tokenHeaders())
        }
    }

    override suspend fun reportProgress(plan: PlaybackPlan, positionMs: Long, paused: Boolean) {
        runCatching {
            Http.postJson(url("/Sessions/Playing/Progress"), sessionBody(plan, positionMs, paused, false), tokenHeaders())
        }
    }

    override suspend fun reportStop(plan: PlaybackPlan, positionMs: Long) {
        runCatching {
            // 用完整 body：缺 PlaySessionId / PlayMethod 时部分 Emby 版本会当成
            // 「未知会话的停止」，直接把该条目的观看记录判成已完成或清零。
            val body = buildJsonObject {
                put("ItemId", plan.itemId)
                plan.mediaSourceId?.let { put("MediaSourceId", it) }
                plan.playSessionId?.let { put("PlaySessionId", it) }
                put("PositionTicks", positionMs * 10_000L)
                put("Failed", false)
                put("CanSeek", true)
                put("IsPaused", true)
                put("IsMuted", false)
                put("PlayMethod", if (plan.direct) "DirectPlay" else "Transcode")
                put("PlaylistLength", 1)
                put("PlaylistIndex", 0)
            }.toString()
            Http.postJson(url("/Sessions/Playing/Stopped"), body, tokenHeaders())
        }
    }

    /**
     * 非会话方式直接写用户数据。会话上报失败或被系统打断时靠这一步兜底，
     * 避免「继续观看」里的进度被清零。
     */
    override suspend fun persistPosition(plan: PlaybackPlan, positionMs: Long, completed: Boolean) {
        runCatching {
            val body = buildJsonObject {
                put("PlaybackPositionTicks", positionMs * 10_000L)
                put("Played", completed)
            }.toString()
            Http.postJson(url("/Users/$userId/Items/${plan.itemId}/UserData"), body, tokenHeaders())
        }
    }

    private fun sessionBody(plan: PlaybackPlan, positionMs: Long, paused: Boolean, start: Boolean): String =
        buildJsonObject {
            put("ItemId", plan.itemId)
            plan.mediaSourceId?.let { put("MediaSourceId", it) }
            plan.playSessionId?.let { put("PlaySessionId", it) }
            put("PositionTicks", positionMs * 10_000L)
            put("IsPaused", paused)
            put("IsMuted", false)
            put("CanSeek", true)
            put("PlayMethod", if (plan.direct) "DirectPlay" else "Transcode")
            put("RepeatMode", "RepeatNone")
            // 让服务器知道用户实际选的是哪条轨道，多端切换时不会把记录算到默认轨上
            plan.audioStreamIndex?.let { put("AudioStreamIndex", it) }
            plan.subtitleStreamIndex?.let { put("SubtitleStreamIndex", it) }
            if (start) {
                put("PlaylistLength", 1)
                put("PlaylistIndex", 0)
            }
        }.toString()

    override suspend fun setFavorite(itemId: String, favorite: Boolean) {
        runCatching {
            if (favorite) {
                Http.postJson(url("/Users/$userId/FavoriteItems/$itemId"), "", tokenHeaders())
            } else {
                Http.deleteJson(url("/Users/$userId/FavoriteItems/$itemId"), null, tokenHeaders())
            }
        }
    }

    override suspend fun setWatched(itemId: String, watched: Boolean) {
        runCatching {
            if (watched) {
                Http.postJson(url("/Users/$userId/PlayedItems/$itemId"), "", tokenHeaders())
            } else {
                Http.deleteJson(url("/Users/$userId/PlayedItems/$itemId"), null, tokenHeaders())
            }
        }
    }

    override suspend fun downloadSubtitle(track: SubtitleTrack, dest: File): Boolean = false

    // ------------------------------------------------------------------ 工具

    private fun primaryImage(id: String, o: JsonObject?): String? {
        val tag = o?.obj("ImageTags")?.str("Primary") ?: return null
        return url("/Items/$id/Images/Primary?maxWidth=600&quality=88&tag=$tag&api_key=$token")
    }

    private fun backdropImage(id: String, o: JsonObject?): String? {
        val tags = o?.arr("BackdropImageTags").orEmpty()
        if (tags.isEmpty()) return null
        return url("/Items/$id/Images/Backdrop/0?maxWidth=1800&quality=85&api_key=$token")
    }

    private fun mapCard(o: JsonObject?): MediaCard? {
        if (o == null) return null
        val id = o.str("Id") ?: return null
        val type = parseType(o.str("Type"))
        val ud = o.obj("UserData")
        val tag = o.obj("ImageTags")?.str("Primary")
        val poster = if (tag != null) {
            url("/Items/$id/Images/Primary?maxWidth=500&quality=85&tag=$tag&api_key=$token")
        } else null
        val backdropTag = o.arr("BackdropImageTags")?.isNotEmpty() == true
        return MediaCard(
            id = id,
            title = o.strOr("Name", "未命名"),
            subtitle = subtitleOf(o, type),
            posterUrl = poster,
            backdropUrl = if (backdropTag) {
                url("/Items/$id/Images/Backdrop/0?maxWidth=1400&quality=80&api_key=$token")
            } else poster,
            type = type,
            progress = ((ud?.dbl("PlayedPercentage") ?: 0.0) / 100.0).toFloat(),
            played = ud?.bool("Played") ?: false,
            year = o.int("ProductionYear"),
            rating = o.dbl("CommunityRating"),
        )
    }

    private fun subtitleOf(o: JsonObject, type: MediaType): String? {
        return when (type) {
            MediaType.EPISODE -> {
                val s = o.int("ParentIndexNumber") ?: 0
                val e = o.int("IndexNumber") ?: 0
                "S${s.toString().padStart(2, '0')}E${e.toString().padStart(2, '0')}"
            }
            MediaType.SERIES -> o.int("ProductionYear")?.toString()
            else -> o.int("ProductionYear")?.toString()
        }
    }

    private fun parseType(raw: String?): MediaType = when (raw?.lowercase()) {
        "movie" -> MediaType.MOVIE
        "series" -> MediaType.SERIES
        "season" -> MediaType.SEASON
        "episode" -> MediaType.EPISODE
        "video", "musicvideo" -> MediaType.VIDEO
        "folder", "collectionfolder", "boxset" -> MediaType.FOLDER
        else -> MediaType.UNKNOWN
    }
}
