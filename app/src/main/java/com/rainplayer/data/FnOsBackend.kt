package com.rainplayer.data

import com.rainplayer.core.Http
import com.rainplayer.core.J
import com.rainplayer.core.asObj
import com.rainplayer.core.bool
import com.rainplayer.core.dbl
import com.rainplayer.core.int
import com.rainplayer.core.obj
import com.rainplayer.core.str
import com.rainplayer.core.strOr
import com.rainplayer.core.stringList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

/**
 * 飞牛影视（fnOS）原生后端。
 *
 * 飞牛没有公开 API 文档，这里按照其 Web 客户端 `https://<nas>:5666/v/api/v1/…`
 * 的实际协议实现：
 *  - 鉴权：`Authorization: <token>` + `Cookie: Trim-MC-token=<token>`
 *  - 客户端标识：`x-trim-client: web` / `x-trim-client-version: 616`（缺少会隐藏直播库）
 *  - 请求签名 `Authx`：
 *      sign = md5(apiKey_path_nonce_timestamp_bodyMd5_apiSecret)
 *      有 body 时 bodyMd5 = md5(原始 JSON 串)，GET 带参时 = md5(参数按 key 排序后的 k=v&...)，否则 md5("")
 *  - 响应统一为 {code, msg, data}，code == 0 为成功
 */
class FnOsBackend(override val config: ServerConfig) : MediaBackend {

    private val base = BackendFactory.normalizeUrl(config.url)
    private var token = config.token

    private var genreCache: Map<Int, String> = emptyMap()

    init {
        Http.authInterceptor.update(base, staticHeaders())
    }

    private fun staticHeaders(): Map<String, String> = buildMap {
        put("Accept", "application/json")
        put("x-trim-client", "web")
        put("x-trim-client-version", "616")
        if (token.isNotBlank()) {
            put("Authorization", token)
            put("Cookie", "Trim-MC-token=$token")
        }
    }

    // ---------------------------------------------------------------- 签名

    private fun signHeaders(path: String, query: Map<String, String>?, body: String?): Map<String, String> {
        val nonce = (100_000..999_999).random().toString()
        val timestamp = System.currentTimeMillis().toString()
        val payloadMd5 = when {
            body != null -> md5(body)
            !query.isNullOrEmpty() -> md5(
                query.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
            )
            else -> md5("")
        }
        val sign = md5(listOf(API_KEY, path, nonce, timestamp, payloadMd5, API_SECRET).joinToString("_"))
        return staticHeaders() + mapOf(
            "Authx" to "nonce=$nonce&timestamp=$timestamp&sign=$sign",
            "Content-Type" to "application/json; charset=utf-8",
        )
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        bytes.forEach { sb.append("%02x".format(it)) }
        return sb.toString()
    }

    // ---------------------------------------------------------------- 请求

    private suspend fun getData(path: String, query: Map<String, String> = emptyMap()): JsonElement? {
        val full = if (query.isEmpty()) {
            base + path
        } else {
            base + path + "?" + query.entries.joinToString("&") { "${it.key}=${it.value}" }
        }
        val text = Http.getText(full, signHeaders(path, query, null))
        return unwrap(text)
    }

    private suspend fun postData(path: String, body: JsonObject): JsonElement? {
        val bodyText = body.toString()
        val text = Http.postJson(base + path, bodyText, signHeaders(path, null, bodyText))
        return unwrap(text)
    }

    private fun unwrap(text: String): JsonElement? {
        val root = J.obj(text) ?: error("飞牛影视返回内容无法解析")
        val code = root.int("code") ?: -1
        if (code != 0) {
            val msg = root.str("msg") ?: "错误码 $code"
            throw IllegalStateException("飞牛影视：$msg（$code）")
        }
        return root["data"]
    }

    private fun JsonElement?.list(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it.asObj() } ?: emptyList()

    // ---------------------------------------------------------------- 登录

    override suspend fun authenticate(username: String, password: String): ServerConfig {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val data = postData("/v/api/v1/login", body)
        val newToken = data?.asObj()?.str("token")
            ?: data?.asObj()?.str("access_token")
            ?: error("登录失败：未返回 token")
        token = newToken

        val userGuid = runCatching {
            getData("/v/api/v1/user/info")?.asObj()?.str("guid")
        }.getOrNull().orEmpty()

        Http.authInterceptor.update(base, staticHeaders())
        return config.copy(
            token = newToken,
            userId = userGuid,
            username = username,
            url = base,
            name = config.name.ifBlank { "飞牛影视" },
        )
    }

    // ---------------------------------------------------------------- 媒体库

    override suspend fun libraries(): List<Library> {
        val data = getData("/v/api/v1/mediadb/list")
        return data.list().mapNotNull { o ->
            val guid = o.str("guid") ?: return@mapNotNull null
            Library(guid, o.strOr("title", "媒体库"), o.str("category"))
        }
    }

    override suspend fun homeRows(): List<HomeRow> {
        val rows = mutableListOf<HomeRow>()

        runCatching {
            val data = getData("/v/api/v1/play/list")
            val items = data.list().mapNotNull { mapCard(it) }
            if (items.isNotEmpty()) rows += HomeRow("继续观看", items, RowKind.CONTINUE)
        }

        runCatching {
            val libs = libraries()
            libs.take(6).forEach { library ->
                val page = runCatching { items(library.id, 0, 18) }.getOrNull()
                if (page != null && page.items.isNotEmpty()) {
                    rows += HomeRow(library.name, page.items, RowKind.LIBRARY, library.id)
                }
            }
        }
        return rows
    }

    override suspend fun items(libraryId: String, startIndex: Int, limit: Int): PagedItems {
        val page = (startIndex / limit.coerceAtLeast(1)) + 1
        val body = buildJsonObject {
            put("ancestor_guid", libraryId)
            put("page", page)
            put("page_size", limit)
            put("sort_column", "create_time")
            put("sort_type", "DESC")
            put("exclude_grouped_video", 1)
            // 必须显式列出类型，否则飞牛只返回库标题、不返回条目
            put("tags", buildJsonObject {
                put(
                    "type",
                    JsonArray(
                        listOf("Movie", "TV", "Directory", "Video", "LiveChannel")
                            .map { JsonPrimitive(it) }
                    )
                )
            })
        }
        val data = postData("/v/api/v1/item/list", body)
        val o = data?.asObj()
        val list = o?.get("list").list().mapNotNull { mapCard(it) }
        return PagedItems(list, o?.int("total") ?: list.size, startIndex)
    }

    override suspend fun search(query: String): List<MediaCard> {
        val data = getData("/v/api/v1/search/list", mapOf("q" to query))
        return data.list().mapNotNull { mapCard(it) }
    }

    // ---------------------------------------------------------------- 详情

    override suspend fun detail(itemId: String): MediaDetail {
        val o = getData("/v/api/v1/item/$itemId")?.asObj() ?: error("无法获取详情")
        val type = parseType(o.str("type"))
        val genres = mapGenres(o)

        val seasons = if (type == MediaType.SERIES) runCatching { seasons(itemId) }.getOrDefault(emptyList()) else emptyList()
        val firstSeason = seasons.firstOrNull()
        val episodes = if (type == MediaType.SERIES && firstSeason != null) {
            runCatching { episodes(itemId, firstSeason.id) }.getOrDefault(emptyList())
        } else emptyList()

        val playTarget = when {
            type == MediaType.SERIES -> episodes.firstOrNull { !it.played }?.id ?: episodes.firstOrNull()?.id ?: itemId
            else -> itemId
        }

        val people = runCatching {
            getData("/v/api/v1/person/list/$itemId").list().take(24).mapNotNull { p ->
                val name = p.str("name") ?: return@mapNotNull null
                Person(name = name, role = p.str("role") ?: p.str("job"), imageUrl = imgUrl(p.str("profile_path")))
            }
        }.getOrDefault(emptyList())

        return MediaDetail(
            id = itemId,
            title = o.str("title")?.takeIf { it.isNotBlank() }
                ?: o.str("tv_title") ?: "未命名",
            originalTitle = o.str("original_title"),
            overview = o.str("overview"),
            posterUrl = imgUrl(o.str("posters")),
            backdropUrl = imgUrl(o.str("backdrops")) ?: imgUrl(o.str("posters")),
            year = o.str("release_date")?.take(4)?.toIntOrNull(),
            rating = o.str("vote_average")?.toDoubleOrNull(),
            officialRating = o.str("content_ratings"),
            genres = genres,
            runtimeMinutes = (o.int("duration") ?: 0).takeIf { it > 0 }?.div(60),
            type = type,
            people = people,
            seasons = seasons,
            episodes = episodes,
            favorite = (o.int("is_favorite") ?: 0) == 1,
            played = (o.int("is_watched") ?: 0) == 1,
            playTargetId = playTarget,
            studio = o.str("ancestor_name"),
        )
    }

    private suspend fun mapGenres(o: JsonObject): List<String> {
        val ids = (o["genres"] as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }
            ?: return emptyList()
        if (ids.isEmpty()) return emptyList()
        if (genreCache.isEmpty()) {
            genreCache = runCatching {
                val data = getData("/v/api/v1/tag/genres")
                val array = data as? JsonArray ?: return@runCatching emptyMap()
                array.mapNotNull { element ->
                    val g = element.asObj() ?: return@mapNotNull null
                    val id = g.int("id") ?: g.int("genre_id") ?: return@mapNotNull null
                    val name = g.str("name") ?: g.str("title") ?: return@mapNotNull null
                    id to name
                }.toMap()
            }.getOrDefault(emptyMap())
        }
        return ids.mapNotNull { genreCache[it] }
    }

    override suspend fun seasons(seriesId: String): List<Season> {
        val data = getData("/v/api/v1/season/list/$seriesId")
        return data.list().mapIndexedNotNull { index, o ->
            val guid = o.str("guid") ?: return@mapIndexedNotNull null
            Season(guid, o.strOr("title", "第 ${index + 1} 季"), o.int("season_number") ?: (index + 1))
        }
    }

    override suspend fun episodes(seriesId: String, seasonId: String): List<Episode> {
        val data = getData("/v/api/v1/episode/list/$seasonId")
        return data.list().mapNotNull { o ->
            val guid = o.str("guid") ?: return@mapNotNull null
            Episode(
                id = guid,
                title = o.strOr("title", "第 ${o.int("episode_number") ?: 0} 集"),
                index = o.int("episode_number") ?: 0,
                seasonIndex = o.int("season_number") ?: 0,
                overview = o.str("overview"),
                imageUrl = imgUrl(o.str("poster")),
                runtimeMinutes = (o.int("runtime") ?: 0).takeIf { it > 0 }?.div(60),
                played = (o.int("watched") ?: 0) == 1,
            )
        }
    }

    // ---------------------------------------------------------------- 播放

    override suspend fun playback(itemId: String, startMs: Long, forceTranscode: Boolean): PlaybackPlan {
        val info = postData("/v/api/v1/play/info", buildJsonObject { put("item_guid", itemId) })?.asObj()
            ?: error("飞牛影视未返回播放信息")

        val mediaGuid = info.str("media_guid") ?: error("未返回 media_guid")
        val startSec = (startMs / 1000L).toInt()
        val resumeSec = (info.int("ts") ?: 0)
        val item = info.obj("item")

        val stream = runCatching {
            postData("/v/api/v1/stream", buildJsonObject {
                put("media_guid", mediaGuid)
                put("level", 1)
            })?.asObj()
        }.getOrNull()

        val videoStream = stream?.obj("video_stream")
        val audioStreams = stream?.get("audio_streams").list()
        val subtitleStreams = stream?.get("subtitle_streams").list()
        val fileStream = stream?.obj("file_stream")

        val audioTracks = audioStreams.mapIndexed { index, a ->
            MediaTrack(
                index = a.int("index") ?: index,
                label = a.strOr("title", a.strOr("codec_name", "音轨 ${index + 1}")),
                language = a.str("language"),
                codec = a.str("codec_name"),
                channels = a.int("channels"),
                bitrate = a.int("bps"),
            )
        }
        val subtitleTracks = subtitleStreams.mapIndexed { index, s ->
            val external = (s.int("is_external") ?: 0) == 1
            SubtitleTrack(
                index = s.int("index") ?: index,
                label = s.strOr("title", s.strOr("language", "字幕 ${index + 1}")),
                language = s.str("language"),
                codec = s.str("codec_name"),
                url = if (external) {
                    s.str("guid")?.let { base + "/v/api/v1/subtitle/dl/" + it }
                } else null,
                isExternal = external,
                headers = staticHeaders(),
            )
        }

        val colorRange = videoStream?.str("color_range_type")
        val dvProfile = videoStream?.int("dv_profile") ?: 0
        val dv = dvProfile > 0 || colorRange?.contains("Dolby", true) == true
        val dynamicRange = when {
            dv -> "Dolby Vision"
            colorRange?.contains("HDR", true) == true -> colorRange
            else -> colorRange ?: "SDR"
        }

        val resolution = videoStream?.str("resolution_type")
            ?: videoStream?.let { "${it.int("width")}x${it.int("height")}" }
        val bitrate = videoStream?.int("bps") ?: 0

        // 网盘类媒体（夸克/115 等）需要走 play/play 换取 CDN 直链，否则用本地代理。
        val directLinkQualities = stream?.get("direct_link_qualities")
        val hasCloudDirect = (directLinkQualities as? JsonArray)?.isNotEmpty() == true
        var playUrl = "$base/v/api/v1/media/range/$mediaGuid"
        var isHls = false
        if (hasCloudDirect) {
            runCatching {
                val link = postData("/v/api/v1/play/play", buildJsonObject {
                    put("media_guid", mediaGuid)
                    put("video_guid", videoStream?.str("guid") ?: "")
                    put("video_encoder", videoStream?.str("codec_name") ?: "hevc")
                    put("resolution", resolution ?: "1080p")
                    put("bitrate", bitrate)
                    put("startTimestamp", startSec)
                    put("audio_encoder", "aac")
                    put("audio_guid", audioStreams.firstOrNull()?.str("guid") ?: "")
                    put("subtitle_guid", "")
                    put("channels", 2)
                    put("forced_sdr", 0)
                })?.asObj()?.str("play_link")
                if (!link.isNullOrBlank()) {
                    playUrl = if (link.startsWith("http")) link else base + link
                    isHls = link.contains(".m3u8", true)
                }
            }
        }

        val durationSec = item?.int("duration") ?: 0

        return PlaybackPlan(
            itemId = itemId,
            title = item?.str("title")?.takeIf { it.isNotBlank() }
                ?: item?.str("tv_title") ?: "正在播放",
            subtitle = buildEpisodeLabel(item),
            url = playUrl,
            headers = staticHeaders(),
            isHls = isHls,
            startPositionMs = resumeSec * 1000L,
            durationMs = durationSec * 1000L,
            videoTracks = videoStream?.let {
                listOf(
                    MediaTrack(
                        index = it.int("index") ?: 0,
                        label = listOfNotNull(it.str("resolution_type"), it.str("codec_name")).joinToString(" · "),
                        codec = it.str("codec_name"),
                    )
                )
            }.orEmpty(),
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            direct = true,
            dynamicRange = dynamicRange,
            dolbyVision = dv,
            container = fileStream?.str("file_name")?.substringAfterLast('.', ""),
            fnMediaGuid = mediaGuid,
            fnVideoGuid = videoStream?.str("guid"),
            fnAudioGuid = audioStreams.firstOrNull()?.str("guid"),
            fnSubtitleGuid = info.str("subtitle_guid"),
            fnResolution = resolution,
            fnBitrate = bitrate,
            fnDirectAudioIndex = info.int("direct_link_audio_index") ?: 0,
            posterUrl = imgUrl(item?.str("posters")),
        )
    }

    private fun buildEpisodeLabel(item: JsonObject?): String? {
        if (item == null) return null
        val s = item.int("season_number") ?: 0
        val e = item.int("episode_number") ?: 0
        if (e <= 0) return item.str("release_date")?.take(4)
        return "第 ${s} 季 · 第 ${e} 集"
    }

    override suspend fun reportStart(plan: PlaybackPlan, positionMs: Long) {
        reportRecord(plan, positionMs)
    }

    override suspend fun reportProgress(plan: PlaybackPlan, positionMs: Long, paused: Boolean) {
        reportRecord(plan, positionMs)
    }

    override suspend fun reportStop(plan: PlaybackPlan, positionMs: Long) {
        reportRecord(plan, positionMs)
    }

    private suspend fun reportRecord(plan: PlaybackPlan, positionMs: Long) {
        val mediaGuid = plan.fnMediaGuid ?: return
        runCatching {
            val body = buildJsonObject {
                put("item_guid", plan.itemId)
                put("media_guid", mediaGuid)
                put("video_guid", plan.fnVideoGuid ?: "")
                put("audio_guid", plan.fnAudioGuid ?: "")
                put("subtitle_guid", plan.fnSubtitleGuid ?: "")
                put("resolution", plan.fnResolution ?: "1080p")
                put("bitrate", plan.fnBitrate)
                put("ts", (positionMs / 1000L).toInt())
                put("duration", (plan.durationMs / 1000L).toInt())
                put("device_id", config.deviceId)
                put("direct_link_audio_index", plan.fnDirectAudioIndex)
                put("lan", "zh")
                put("device_name", "RainPlayer")
            }
            postData("/v/api/v1/play/record", body)
        }
    }

    override suspend fun setFavorite(itemId: String, favorite: Boolean) {
        runCatching {
            val body = buildJsonObject { put("item_guid", itemId) }.toString()
            val path = "/v/api/v1/item/favorite"
            if (favorite) {
                Http.putJson(base + path, body, signHeaders(path, null, body))
            } else {
                Http.deleteJson(base + path, body, signHeaders(path, null, body))
            }
        }
    }

    override suspend fun setWatched(itemId: String, watched: Boolean) {
        runCatching {
            val body = buildJsonObject { put("item_guid", itemId) }.toString()
            val path = "/v/api/v1/item/watched"
            if (watched) {
                Http.postJson(base + path, body, signHeaders(path, null, body))
            } else {
                Http.deleteJson(base + path, body, signHeaders(path, null, body))
            }
        }
    }

    override suspend fun downloadSubtitle(track: SubtitleTrack, dest: File): Boolean {
        val url = track.url ?: return false
        // 字幕接口按路径签名校验，必须带上 Authx
        val path = "/v/api/v1/subtitle/dl/" + url.substringAfterLast('/')
        return runCatching {
            val bytes = Http.getBytes(url, signHeaders(path, null, null))
            if (bytes.isEmpty()) return false
            dest.parentFile?.mkdirs()
            dest.writeBytes(bytes)
            true
        }.getOrDefault(false)
    }

    // ---------------------------------------------------------------- 映射

    private fun imgUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) return path
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "$base/v/api/v1/sys/img$normalized"
    }

    private fun mapCard(o: JsonObject): MediaCard? {
        val guid = o.str("guid") ?: return null
        val type = parseType(o.str("type"))
        val mediaStream = o.obj("media_stream")
        val resolutions = mediaStream?.get("resolutions")?.let { element ->
            (element as? JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.orEmpty()
        val subtitle = when (type) {
            MediaType.EPISODE -> "第 ${o.int("season_number") ?: 0} 季 · 第 ${o.int("episode_number") ?: 0} 集"
            else -> resolutions.firstOrNull() ?: o.str("release_date")?.take(4)
        }
        val poster = imgUrl(o.str("poster") ?: o.str("posters"))
            ?: (o["poster_list"] as? JsonArray)?.firstOrNull()?.let {
                imgUrl((it as? kotlinx.serialization.json.JsonPrimitive)?.content)
            }
        return MediaCard(
            id = guid,
            title = o.str("title")?.takeIf { it.isNotBlank() } ?: o.str("tv_title") ?: "未命名",
            subtitle = subtitle,
            posterUrl = poster,
            backdropUrl = poster,
            type = type,
            progress = 0f,
            played = (o.int("watched") ?: 0) == 1,
            year = o.str("release_date")?.take(4)?.toIntOrNull(),
            rating = o.str("vote_average")?.toDoubleOrNull(),
        )
    }

    private fun parseType(raw: String?): MediaType = when (raw?.lowercase()) {
        "movie" -> MediaType.MOVIE
        "tv", "series" -> MediaType.SERIES
        "season" -> MediaType.SEASON
        "episode" -> MediaType.EPISODE
        "video" -> MediaType.VIDEO
        "folder", "directory" -> MediaType.FOLDER
        else -> MediaType.UNKNOWN
    }

    companion object {
        // 飞牛 Web 客户端的公开签名密钥（随客户端下发，非用户凭证）
        private const val API_KEY = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
        private const val API_SECRET = "16CCEB3D-AB42-077D-36A1-F355324E4237"
    }
}
