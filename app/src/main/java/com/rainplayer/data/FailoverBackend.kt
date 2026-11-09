package com.rainplayer.data

import com.rainplayer.core.HttpException
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException

/**
 * 多线路自动切换的装饰器。
 *
 * 同一个服务器的多条线路（内网 / 外网 / 中转）里，只要当前线路出现
 * **网络层** 故障（连不上、超时、DNS 失败），就自动换下一条重试；
 * 服务器明确返回的 HTTP 错误（401 密码错、404 条目不存在）不会触发切换，
 * 因为换线路也解决不了。
 *
 * 切换成功后通过 [onLineChanged] 回调把新的 [ServerConfig] 交给上层落盘，
 * 这样下次启动直接走能通的那条线路。
 */
class FailoverBackend(
    initial: ServerConfig,
    private val onLineChanged: (ServerConfig) -> Unit = {},
) : MediaBackend {

    @Volatile
    override var config: ServerConfig = initial
        private set

    @Volatile
    private var inner: MediaBackend = BackendFactory.create(config)

    /** 记录这次请求过程中已经试过哪些线路，避免死循环。 */
    private suspend fun <T> withFailover(block: suspend (MediaBackend) -> T): T {
        val tried = LinkedHashSet<String>()
        var lastError: Throwable? = null
        while (true) {
            tried += config.activeLineId
            try {
                return block(inner)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (!isTransient(e)) throw e
                lastError = e
                val next = config.lines.firstOrNull { it.id !in tried } ?: break
                config = config.withLine(next.id)
                inner = BackendFactory.create(config)
                onLineChanged(config)
            }
        }
        throw lastError ?: IOException("所有线路都不可用")
    }

    /** 只有「网络不可达」类错误才值得换线路。 */
    private fun isTransient(e: Throwable): Boolean {
        if (e is HttpException) return false
        if (e is IOException) return true
        val name = e.javaClass.simpleName.lowercase()
        return name.contains("timeout") || name.contains("connect") || name.contains("unreachable")
    }

    override suspend fun authenticate(username: String, password: String): ServerConfig =
        withFailover { it.authenticate(username, password) }

    override suspend fun libraries(): List<Library> = withFailover { it.libraries() }

    override suspend fun homeRows(): List<HomeRow> = withFailover { it.homeRows() }

    override suspend fun items(libraryId: String, startIndex: Int, limit: Int): PagedItems =
        withFailover { it.items(libraryId, startIndex, limit) }

    override suspend fun search(query: String): List<MediaCard> = withFailover { it.search(query) }

    override suspend fun detail(itemId: String): MediaDetail = withFailover { it.detail(itemId) }

    override suspend fun similar(itemId: String, limit: Int): List<MediaCard> =
        withFailover { it.similar(itemId, limit) }

    override suspend fun episode(itemId: String): Episode = withFailover { it.episode(itemId) }

    override suspend fun seasons(seriesId: String): List<Season> = withFailover { it.seasons(seriesId) }

    override suspend fun episodes(seriesId: String, seasonId: String): List<Episode> =
        withFailover { it.episodes(seriesId, seasonId) }

    override suspend fun playback(
        itemId: String,
        startMs: Long,
        forceTranscode: Boolean,
        sourceId: String?,
        audioIndex: Int?,
        subtitleIndex: Int?,
        burnSubtitle: Boolean,
    ): PlaybackPlan = withFailover {
        it.playback(itemId, startMs, forceTranscode, sourceId, audioIndex, subtitleIndex, burnSubtitle)
    }

    override suspend fun playbackOptions(itemId: String, sourceId: String?): PlaybackOptions =
        withFailover { it.playbackOptions(itemId, sourceId) }

    // 上报类接口不做线路切换：切了线路以后 PlaySessionId 对不上，反而会把记录搞乱。
    override suspend fun reportStart(plan: PlaybackPlan, positionMs: Long) =
        runCatching { inner.reportStart(plan, positionMs) }.let { }

    override suspend fun reportProgress(plan: PlaybackPlan, positionMs: Long, paused: Boolean) =
        runCatching { inner.reportProgress(plan, positionMs, paused) }.let { }

    override suspend fun reportStop(plan: PlaybackPlan, positionMs: Long) =
        runCatching { inner.reportStop(plan, positionMs) }.let { }

    override suspend fun persistPosition(plan: PlaybackPlan, positionMs: Long, completed: Boolean) =
        runCatching { inner.persistPosition(plan, positionMs, completed) }.let { }

    override suspend fun setFavorite(itemId: String, favorite: Boolean) =
        withFailover { it.setFavorite(itemId, favorite) }

    override suspend fun setWatched(itemId: String, watched: Boolean) =
        withFailover { it.setWatched(itemId, watched) }

    override suspend fun downloadSubtitle(track: SubtitleTrack, dest: File): Boolean =
        withFailover { it.downloadSubtitle(track, dest) }

    override fun refreshAuth() = inner.refreshAuth()
}
