package com.rainplayer.player

import com.rainplayer.data.MediaBackend
import com.rainplayer.data.PlaybackPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 观看进度上报器。
 *
 * 之前直接在 Activity 的 lifecycleScope 里发请求，退出时 Activity 被销毁，
 * 协程随之取消，`Sessions/Playing/Stopped` 经常发不出去；更糟的是有些请求只发了
 * 一半（例如位置是 0），服务器就把「继续观看」的记录清零了。
 *
 * 这里的做法：
 *  1. 所有请求跑在应用级作用域，Activity 销毁不影响；
 *  2. 先 Progress 再 Stopped，保证位置一定先落库一次；
 *  3. 位置为 0 或倒退到 0 时用上一次已知位置兜底，绝不拿 0 覆盖记录；
 *  4. 只有真正 Start 过才发 Stop，没开播就退出不会产生任何写入；
 *  5. 最后再用非会话接口（UserData）显式写一次位置，双保险。
 */
class PlaybackReporter(
    private val backend: MediaBackend,
    private val plan: PlaybackPlan,
) {

    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val lastKnownMs = AtomicLong(0L)
    private val watchedMs = AtomicLong(0L)

    /** 开播（首次进入 READY）。 */
    fun markReady(positionMs: Long) {
        val pos = sanitize(positionMs)
        lastKnownMs.set(pos)
        if (!started.compareAndSet(false, true)) return
        AppScope.launch { runCatching { backend.reportStart(plan, pos) } }
    }

    /** 周期心跳 / 暂停 / 拖动后调用。 */
    fun progress(positionMs: Long, paused: Boolean) {
        val pos = sanitize(positionMs)
        if (!started.get()) {
            markReady(pos)
            return
        }
        if (stopped.get()) return
        val delta = pos - lastKnownMs.get()
        if (delta > 0) watchedMs.addAndGet(delta)
        lastKnownMs.set(pos)
        AppScope.launch { runCatching { backend.reportProgress(plan, pos, paused) } }
    }

    /**
     * 结束播放。[positionMs] 传播放器当前进度。
     * 只会执行一次，重复调用（onStop / onDestroy 都可能触发）直接忽略。
     */
    fun finish(positionMs: Long) {
        if (!started.get()) return
        if (!stopped.compareAndSet(false, true)) return

        val pos = sanitize(positionMs)
        val duration = plan.durationMs
        val completed = duration > 0 && pos >= duration * 0.95
        // 看了不到 2 秒且位置为 0：视为误触，不写任何记录
        if (watchedMs.get() < 2_000 && pos <= 0) return

        AppScope.launch {
            // 1) 先把位置落一次库（Progress 会更新 PlaybackPositionTicks）
            runCatching { backend.reportProgress(plan, pos, true) }
            // 2) 再正常结束会话
            runCatching { backend.reportStop(plan, pos) }
            // 3) 最后用非会话接口显式写一次，防止会话上报被服务器判成异常
            runCatching { backend.persistPosition(plan, pos, completed) }
        }
    }

    /** 位置兜底：0 或异常小的值不覆盖已有记录。 */
    private fun sanitize(positionMs: Long): Long {
        val pos = positionMs.coerceAtLeast(0L)
        val last = lastKnownMs.get()
        return if (pos <= 0L && last > 3_000L) last else pos
    }

    companion object {
        /** 应用级作用域：Activity 销毁后请求仍能跑完。 */
        private val AppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
