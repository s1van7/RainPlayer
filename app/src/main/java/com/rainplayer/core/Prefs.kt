package com.rainplayer.core

import android.content.Context
import com.rainplayer.data.ServerConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 轻量本地存储。使用 SharedPreferences 而非 DataStore，避免在 API 21 老电视上
 * 引入额外的协程/文件 IO 依赖与启动开销。
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("rainplayer_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var servers: List<ServerConfig>
        get() = runCatching {
            json.decodeFromString<List<ServerConfig>>(sp.getString(KEY_SERVERS, "[]") ?: "[]")
        }.getOrDefault(emptyList())
        set(value) {
            sp.edit().putString(KEY_SERVERS, json.encodeToString(value)).apply()
        }

    var activeServerId: String?
        get() = sp.getString(KEY_ACTIVE, null)
        set(value) = sp.edit().putString(KEY_ACTIVE, value).apply()

    var deviceId: String
        get() {
            val existing = sp.getString(KEY_DEVICE_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = "rainplayer-" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            sp.edit().putString(KEY_DEVICE_ID, generated).apply()
            return generated
        }
        set(value) = sp.edit().putString(KEY_DEVICE_ID, value).apply()

    var deviceName: String
        get() = sp.getString(KEY_DEVICE_NAME, null) ?: defaultDeviceName()
        set(value) = sp.edit().putString(KEY_DEVICE_NAME, value).apply()

    // ---- 播放设置 ----
    /** 音频直通（AC3/EAC3/DTS/TrueHD 交给功放/电视解码），关闭则一律本机解码。 */
    var audioPassthrough: Boolean
        get() = sp.getBoolean("audio_passthrough", true)
        set(v) = sp.edit().putBoolean("audio_passthrough", v).apply()

    /** 优先硬件解码；关闭后强制走 FFmpeg 软解（低端盒子反而更稳时可用）。 */
    var preferHardwareDecode: Boolean
        get() = sp.getBoolean("hw_decode", true)
        set(v) = sp.edit().putBoolean("hw_decode", v).apply()

    /** 允许隧道播放（tunneled playback），部分电视上 HDR 直通更稳。 */
    var tunneledPlayback: Boolean
        get() = sp.getBoolean("tunneled", false)
        set(v) = sp.edit().putBoolean("tunneled", v).apply()

    /** 按片源分辨率/帧率自动切换显示模式（4K/8K/23.976 输出）。 */
    var matchDisplayMode: Boolean
        get() = sp.getBoolean("match_display", true)
        set(v) = sp.edit().putBoolean("match_display", v).apply()

    /** 视频播放时保持屏幕常亮。 */
    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(v) = sp.edit().putBoolean("keep_screen_on", v).apply()

    var subtitleScale: Float
        get() = sp.getFloat("subtitle_scale", 1.0f)
        set(v) = sp.edit().putFloat("subtitle_scale", v).apply()

    /** 0=描边 1=背景框 2=阴影 */
    var subtitleStyle: Int
        get() = sp.getInt("subtitle_style", 0)
        set(v) = sp.edit().putInt("subtitle_style", v).apply()

    var preferredAudioLang: String
        get() = sp.getString("audio_lang", "zh") ?: "zh"
        set(v) = sp.edit().putString("audio_lang", v).apply()

    var preferredSubtitleLang: String
        get() = sp.getString("subtitle_lang", "zh") ?: "zh"
        set(v) = sp.edit().putString("subtitle_lang", v).apply()

    var autoPlayNext: Boolean
        get() = sp.getBoolean("auto_next", true)
        set(v) = sp.edit().putBoolean("auto_next", v).apply()

    // ---- 手势与旋转 ----

    /** 手势总开关：关闭后播放页只响应点击，不响应滑动与长按。 */
    var gestureEnabled: Boolean
        get() = sp.getBoolean("gesture_enabled", true)
        set(v) = sp.edit().putBoolean("gesture_enabled", v).apply()

    /** 左半屏上下滑调亮度。 */
    var brightnessGesture: Boolean
        get() = sp.getBoolean("gesture_brightness", true)
        set(v) = sp.edit().putBoolean("gesture_brightness", v).apply()

    /** 右半屏上下滑调音量。 */
    var volumeGesture: Boolean
        get() = sp.getBoolean("gesture_volume", true)
        set(v) = sp.edit().putBoolean("gesture_volume", v).apply()

    /** 长按左右半屏倍速快退 / 快进。 */
    var longPressSpeedEnabled: Boolean
        get() = sp.getBoolean("gesture_long_press", true)
        set(v) = sp.edit().putBoolean("gesture_long_press", v).apply()

    /** 长按倍速值（2.0 / 3.0 / 4.0）。 */
    var longPressSpeed: Float
        get() = sp.getFloat("long_press_speed", 3.0f)
        set(v) = sp.edit().putFloat("long_press_speed", v).apply()

    /** 播放时跟随重力自动横屏。 */
    var autoRotate: Boolean
        get() = sp.getBoolean("auto_rotate", true)
        set(v) = sp.edit().putBoolean("auto_rotate", v).apply()

    /** 缓存的外部字幕目录（外挂字幕先落地再交给播放器）。 */
    var lastLibraryId: String?
        get() = sp.getString("last_library", null)
        set(v) = sp.edit().putString("last_library", v).apply()

    // ---- 外观 ----

    /** 主题 key，见 [com.rainplayer.ui.theme.AppTheme]。 */
    var themeKey: String
        get() = sp.getString("theme_key", "midnight") ?: "midnight"
        set(v) = sp.edit().putString("theme_key", v).apply()

    /** 毛玻璃效果总开关。 */
    var glassEffect: Boolean
        get() = sp.getBoolean("glass_effect", true)
        set(v) = sp.edit().putBoolean("glass_effect", v).apply()

    // ---- 聚合与推荐 ----

    /** 跨服务器聚合搜索。 */
    var aggregateSearch: Boolean
        get() = sp.getBoolean("aggregate_search", true)
        set(v) = sp.edit().putBoolean("aggregate_search", v).apply()

    /** 首页「继续观看」上方的每日推荐轮播。 */
    var dailyRecommend: Boolean
        get() = sp.getBoolean("daily_recommend", true)
        set(v) = sp.edit().putBoolean("daily_recommend", v).apply()

    // ---- 评分源 ----

    /**
     * TMDB API Key（v3 auth key）。
     *
     * 没设置过（键不存在）时返回内置 Key，开箱即用；
     * 用户主动清空（存的是空串）则视为关闭 TMDB 评分，不再回退到内置 Key。
     */
    var tmdbApiKey: String
        get() = sp.getString("tmdb_key", null) ?: Keys.TMDB_API_KEY
        set(v) = sp.edit().putString("tmdb_key", v.trim()).apply()

    /** 豆瓣评分（走第三方聚合接口，失败静默隐藏）。 */
    var doubanRating: Boolean
        get() = sp.getBoolean("douban_rating", true)
        set(v) = sp.edit().putBoolean("douban_rating", v).apply()

    private fun defaultDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER ?: "Android"
        val model = android.os.Build.MODEL ?: ""
        return "$manufacturer $model".trim().ifBlank { "RainPlayer" }
    }

    companion object {
        private const val KEY_SERVERS = "servers"
        private const val KEY_ACTIVE = "active_server"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
    }
}
