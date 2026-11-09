package com.rainplayer.data

import java.io.File

/**
 * 统一媒体后端接口。Emby / Jellyfin / 飞牛影视 各实现一份，
 * 上层 UI 与播放器只依赖这个接口。
 */
interface MediaBackend {

    val config: ServerConfig

    /** 登录并返回带 token 的新配置（不落盘，由调用方保存）。 */
    suspend fun authenticate(username: String, password: String): ServerConfig

    suspend fun libraries(): List<Library>

    suspend fun homeRows(): List<HomeRow>

    suspend fun items(libraryId: String, startIndex: Int, limit: Int): PagedItems

    suspend fun search(query: String): List<MediaCard>

    suspend fun detail(itemId: String): MediaDetail

    suspend fun seasons(seriesId: String): List<Season>

    suspend fun episodes(seriesId: String, seasonId: String): List<Episode>

    /**
     * 生成播放方案。默认走「直通/直链」以保证 4K/8K/HDR/杜比视界与
     * 全景声音轨不被服务器转码破坏；[forceTranscode] 为 true 时才请求转码。
     */
    suspend fun playback(itemId: String, startMs: Long, forceTranscode: Boolean = false): PlaybackPlan

    suspend fun reportStart(plan: PlaybackPlan, positionMs: Long)

    suspend fun reportProgress(plan: PlaybackPlan, positionMs: Long, paused: Boolean)

    suspend fun reportStop(plan: PlaybackPlan, positionMs: Long)

    suspend fun setFavorite(itemId: String, favorite: Boolean)

    suspend fun setWatched(itemId: String, watched: Boolean)

    /** 外挂字幕需要鉴权时，把它落地成本地文件；返回 true 表示成功。 */
    suspend fun downloadSubtitle(track: SubtitleTrack, dest: File): Boolean
}

object BackendFactory {
    fun create(config: ServerConfig): MediaBackend = when (config.kind) {
        ServerKind.FNOS -> FnOsBackend(config)
        else -> EmbyBackend(config)
    }

    /** 归一化地址：补协议、去尾部斜杠。 */
    fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url.trimEnd('/')
    }
}
