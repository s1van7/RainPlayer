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

    /** 缓存的外部字幕目录（外挂字幕先落地再交给播放器）。 */
    var lastLibraryId: String?
        get() = sp.getString("last_library", null)
        set(v) = sp.edit().putString("last_library", v).apply()

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
