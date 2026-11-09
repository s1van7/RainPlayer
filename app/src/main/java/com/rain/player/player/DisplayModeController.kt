package com.rain.player.player

import android.app.Activity
import android.os.Build
import android.view.Display
import android.view.WindowManager
import kotlin.math.abs

/**
 * 显示模式切换：把电视/盒子的输出分辨率与刷新率对齐片源，
 * 4K/8K 片源避免被系统缩放到 1080p，23.976 片源避免 3:2 反交错抖动。
 */
class DisplayModeController(private val activity: Activity) {

    private var originalModeId: Int = -1
    private var applied = false

    fun applyForVideo(width: Int, height: Int, frameRate: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (width <= 0 || height <= 0) return
        runCatching {
            val window = activity.window ?: return
            val display: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display ?: return
            } else {
                @Suppress("DEPRECATION")
                (activity.getSystemService(Activity.WINDOW_SERVICE) as WindowManager).defaultDisplay
            }
            val modes = display.supportedModes ?: return
            if (modes.isEmpty()) return

            if (originalModeId <= 0) {
                originalModeId = window.attributes.preferredDisplayModeId
            }

            val targetRate = if (frameRate > 1f) frameRate else 0f
            val candidates = modes.filter { mode ->
                mode.physicalWidth >= width && mode.physicalHeight >= height
            }.ifEmpty { modes.toList() }

            val best = candidates.minWithOrNull(
                compareBy<Display.Mode>(
                    { it.physicalWidth.toLong() * it.physicalHeight },      // 面积最小且够大
                    { if (targetRate > 0f) (abs(it.refreshRate - targetRate) * 10).toInt() else 0 },
                )
            ) ?: return

            val attrs = window.attributes
            attrs.preferredDisplayModeId = best.modeId
            window.attributes = attrs
            applied = true
        }
    }

    fun restore() {
        if (!applied || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val window = activity.window ?: return
            val attrs = window.attributes
            attrs.preferredDisplayModeId = originalModeId
            window.attributes = attrs
        }
        applied = false
    }
}
