package com.rain.player.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.rain.player.core.Prefs

/**
 * 播放器构建器。
 *
 * 关键点：
 *  1. 扩展渲染器模式 ON：平台解码器不支持时自动回退到 FFmpeg（AC3/EAC3/TrueHD/DTS/FLAC/ALAC）；
 *  2. 通过重写 buildAudioSink 控制 AudioCapabilities，实现「音频直通」开关
 *     （直通时 AC3/EAC3/DTS/TrueHD 原码送 HDMI，关闭时统一本机解码成 PCM）；
 *  3. 开启解码器回退，避免个别 8K/AV1 片源找不到解码器直接黑屏；
 *  4. 缓冲策略偏向大缓冲，局域网 4K 高码率更稳。
 */
@OptIn(UnstableApi::class)
class RainRenderersFactory(
    context: Context,
    private val audioPassthrough: Boolean,
    preferHardware: Boolean,
) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(if (preferHardware) EXTENSION_RENDERER_MODE_ON else EXTENSION_RENDERER_MODE_PREFER)
        setEnableDecoderFallback(true)
        setEnableAudioFloatOutput(false)
        setEnableAudioTrackPlaybackParams(true)
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val capabilities = if (audioPassthrough) {
            runCatching { AudioCapabilities.getCapabilities(context) }
                .getOrDefault(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
        } else {
            // 只保留 PCM 立体声能力 -> 强制所有压缩音频走解码器
            AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES
        }
        return DefaultAudioSink.Builder(context)
            .setAudioCapabilities(capabilities)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }
}

object PlayerFactory {

    fun create(
        context: Context,
        prefs: Prefs,
        mediaSourceFactory: androidx.media3.exoplayer.source.MediaSource.Factory? = null,
    ): ExoPlayer {
        val renderersFactory = RainRenderersFactory(
            context = context,
            audioPassthrough = prefs.audioPassthrough,
            preferHardware = prefs.preferHardwareDecode,
        )

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(prefs.preferredAudioLang)
                    .setPreferredTextLanguage(prefs.preferredSubtitleLang)
                    .setSelectUndeterminedTextLanguage(true)
                    .setTunnelingEnabled(prefs.tunneledPlayback)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .setConstrainAudioChannelCountToDeviceCapabilities(true)
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                20_000,   // 最小缓冲
                150_000,  // 最大缓冲（高码率 4K/8K 需要）
                1_500,    // 起播缓冲
                3_000,    // 卡顿后重缓冲
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val builder = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
        if (mediaSourceFactory != null) {
            builder.setMediaSourceFactory(mediaSourceFactory)
        }
        return builder.build()
    }
}
