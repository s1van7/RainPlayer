package com.rain.player.player

import android.content.Context
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.rain.player.data.PlaybackCapabilities

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
