package com.rainplayer.player

import android.content.Context
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.rainplayer.data.PlaybackCapabilities

/**
 * 探测设备的播放能力：HDR / 杜比视界 / 音频直通 / 最大分辨率与刷新率。
 * 用于在播放页提示「本机是否真的能直通」，避免用户误以为是片源问题。
 */
@OptIn(UnstableApi::class)
object PlayerCapabilities {

    fun detect(context: Context): PlaybackCapabilities {
        val display = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            }
        }.getOrNull()

        var hdr10 = false
        var hdr10Plus = false
        var hlg = false
        var dvByDisplay = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && display != null) {
            val caps = display.hdrCapabilities
            caps?.supportedHdrTypes?.forEach { type ->
                when (type) {
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> hdr10 = true
                    Display.HdrCapabilities.HDR_TYPE_HLG -> hlg = true
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> dvByDisplay = true
                    HDR_TYPE_HDR10_PLUS -> hdr10Plus = true
                }
            }
        }

        val dvProfiles = dolbyVisionProfiles()
        val dv = dvByDisplay || dvProfiles.isNotEmpty()

        val audioCaps = runCatching { AudioCapabilities.getCapabilities(context) }
            .getOrDefault(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
        fun passthrough(mime: String): Boolean = runCatching {
            val format = Format.Builder()
                .setSampleMimeType(mime)
                .setChannelCount(8)
                .setSampleRate(48_000)
                .build()
            audioCaps.isPassthroughPlaybackSupported(format)
        }.getOrDefault(false)

        var maxWidth = 0
        var maxHeight = 0
        var maxRefresh = 0f
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && display != null) {
                val current = display.mode
                maxWidth = current.physicalWidth
                maxHeight = current.physicalHeight
                maxRefresh = current.refreshRate
                display.supportedModes?.forEach { mode ->
                    if (mode.physicalWidth > maxWidth) maxWidth = mode.physicalWidth
                    if (mode.physicalHeight > maxHeight) maxHeight = mode.physicalHeight
                    if (mode.refreshRate > maxRefresh) maxRefresh = mode.refreshRate
                }
            }
        }

        return PlaybackCapabilities(
            hdr10 = hdr10,
            hdr10Plus = hdr10Plus,
            hlg = hlg,
            dolbyVision = dv,
            dolbyVisionProfiles = dvProfiles,
            ac3Passthrough = passthrough(MimeTypes.AUDIO_AC3),
            eac3Passthrough = passthrough(MimeTypes.AUDIO_E_AC3),
            dtsPassthrough = passthrough(MimeTypes.AUDIO_DTS),
            trueHdPassthrough = passthrough(MimeTypes.AUDIO_TRUEHD),
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxRefreshRate = maxRefresh,
        )
    }

    private const val HDR_TYPE_HDR10_PLUS = 6

    /** 查询是否存在杜比视界解码器，并返回支持的 profile 列表。 */
    private fun dolbyVisionProfiles(): List<Int> = runCatching {
        MediaCodecUtil.getDecoderInfos("video/dolby-vision", false, false)
            .flatMap { info -> info.profileLevels?.map { it.profile } ?: emptyList() }
            .distinct()
            .sorted()
    }.getOrDefault(emptyList())
}

/**
 * 单条音轨的本机能力判定。
 *
 * 「识别不出 DTS」这类反馈通常不是解析问题，而是这条音轨既不能硬解、
 * 又不能直通，ExoPlayer 就静默跳过了。把结论算出来直接标在音轨列表上，
 * 用户能一眼看出该换哪条轨、还是该让服务器转码。
 */
@OptIn(UnstableApi::class)
object AudioSupport {

    /** 服务器返回的 codec 名 -> Media3 的 mime。 */
    fun mimeOf(codec: String?): String? = when (codec?.lowercase()) {
        "dts", "dca" -> MimeTypes.AUDIO_DTS
        "dts-hd", "dtshd", "dts_hd" -> MimeTypes.AUDIO_DTS_HD
        "truehd" -> MimeTypes.AUDIO_TRUEHD
        "mlp" -> MimeTypes.AUDIO_TRUEHD
        "ac3", "ac-3" -> MimeTypes.AUDIO_AC3
        "eac3", "e-ac3", "ec3" -> MimeTypes.AUDIO_E_AC3
        "aac" -> MimeTypes.AUDIO_AAC
        "flac" -> MimeTypes.AUDIO_FLAC
        "opus" -> MimeTypes.AUDIO_OPUS
        "vorbis" -> MimeTypes.AUDIO_VORBIS
        "mp3" -> MimeTypes.AUDIO_MPEG
        "alac" -> MimeTypes.AUDIO_ALAC
        "pcm_s16le", "pcm_s24le", "pcm_bluray", "lpcm" -> MimeTypes.AUDIO_RAW
        else -> null
    }

    /** 本机是否有解码器（含 FFmpeg 扩展）。 */
    fun canDecode(mime: String?): Boolean {
        if (mime == null) return true
        return runCatching { MediaCodecUtil.getDecoderInfos(mime, false, false).isNotEmpty() }
            .getOrDefault(false)
    }

    /** 本机是否能原码直通到 HDMI。 */
    fun canPassthrough(context: Context, mime: String?, channels: Int = 8): Boolean {
        if (mime == null) return false
        return runCatching {
            val caps = AudioCapabilities.getCapabilities(context)
            val format = Format.Builder()
                .setSampleMimeType(mime)
                .setChannelCount(channels.coerceIn(1, 8))
                .setSampleRate(48_000)
                .build()
            caps.isPassthroughPlaybackSupported(format)
        }.getOrDefault(false)
    }

    /**
     * 给音轨打标签：
     * 「直通」= 能原码送功放；「软解」= 本机解码后输出 PCM；「不支持」= 两条路都不通。
     */
    fun describe(context: Context, codec: String?, channels: Int?): String {
        val mime = mimeOf(codec) ?: return ""
        val passthrough = canPassthrough(context, mime, channels ?: 8)
        if (passthrough) return "直通"
        return if (canDecode(mime)) "软解" else "不支持"
    }
}
