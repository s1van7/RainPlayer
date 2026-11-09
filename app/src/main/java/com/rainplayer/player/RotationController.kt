package com.rainplayer.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener

/**
 * 跟随重力的屏幕旋转。
 *
 * 直接用系统的 [OrientationEventListener]，它已经按设备实际传感器坐标系
 * 算好了 0/90/180/270，比自己读加速度计做 atan2 更可靠（不同厂商的
 * X/Y 轴方向并不一致）。
 *
 * 只切换 竖屏 / 横屏 / 反向横屏 三种，不做倒置竖屏——躺着看视频时
 * 屏幕突然上下颠倒体验很差。
 */
class RotationController(
    context: Context,
    private val onOrientation: (Int) -> Unit,
) : OrientationEventListener(context) {

    private var current = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var pending = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var pendingSince = 0L

    /** 有传感器才启用；电视盒子通常没有加速度计。 */
    fun start(): Boolean {
        if (!canDetectOrientation()) return false
        return runCatching { enable(); true }.getOrDefault(false)
    }

    fun stop() {
        runCatching { disable() }
    }

    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return
        /*
         * OrientationEventListener 给的度数对应的是「屏幕内容需要旋转的角度」，
         * 与 SCREEN_ORIENTATION_* 的对应关系正好和直觉相反：
         *   90°（设备逆时针转 90°，顶部朝左）  -> REVERSE_LANDSCAPE
         *   270°（设备顺时针转 90°，顶部朝右） -> LANDSCAPE
         * 之前这里写反了，表现就是横屏方向和重力方向相反。
         */
        val target = when (orientation) {
            in 45..134 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            in 135..224 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            in 225..314 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        if (target == current) {
            pending = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            return
        }
        // 连续 250ms 稳定在同一方向才真正切换，避免手持抖动来回横竖屏
        val now = System.currentTimeMillis()
        if (target != pending) {
            pending = target
            pendingSince = now
            return
        }
        if (now - pendingSince < 250) return
        current = target
        pending = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onOrientation(target)
    }
}
