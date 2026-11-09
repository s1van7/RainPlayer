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
     *
     * @param sourceId 选中的版本（多版本影片）；null 表示服务器默认版本
     * @param audioIndex 选中的音轨序号；null 表示服务器默认
     * @param subtitleIndex 选中的字幕序号；null 表示不指定（-1 表示关闭字幕）
     * @param burnSubtitle 图形字幕（PGS 等）需要烧录进画面
     */
    suspend fun playback(
        itemId: String,
        startMs: Long,
        forceTranscode: Boolean = false,
        sourceId: String? = null,
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
        burnSubtitle: Boolean = false,
    ): PlaybackPlan

    /** 详情页展示用的版本 / 音轨 / 字幕清单。 */
    suspend fun playbackOptions(itemId: String, sourceId: String? = null): PlaybackOptions

    suspend fun reportStart(plan: PlaybackPlan, positionMs: Long)

    suspend fun reportProgress(plan: PlaybackPlan, positionMs: Long, paused: Boolean)

    suspend fun reportStop(plan: PlaybackPlan, positionMs: Long)

    /**
     * 兜底：直接把观看位置写进用户数据。
     *
     * 会话式上报（Sessions/Playing 系列接口）在异常退出、被系统杀掉、或多端抢占会话时
     * 可能丢掉最后一次位置，导致「继续观看」记录被清零。这里用非会话接口再写一次，
     * 保证记录一定落库。默认实现为空，由支持的服务器覆盖。
     */
    suspend fun persistPosition(plan: PlaybackPlan, positionMs: Long, completed: Boolean) = Unit

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
