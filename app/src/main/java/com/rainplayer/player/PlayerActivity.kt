package com.rainplayer.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.rainplayer.Graph
import com.rainplayer.core.Http
import com.rainplayer.core.Prefs
import com.rainplayer.data.FnOsBackend
import com.rainplayer.data.MediaBackend
import com.rainplayer.data.PlaybackCapabilities
import com.rainplayer.data.PlaybackPlan
import com.rainplayer.data.SubtitleTrack
import com.rainplayer.ui.theme.RainPlayerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** 一条可选轨道。既可能来自服务器元数据，也可能来自播放器实际解析出的轨道。 */
data class TrackOption(
    val key: String,
    val label: String,
    val serverIndex: Int? = null,
    val groupIndex: Int = -1,
    val trackIndex: Int = 0,
    val language: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val imageBased: Boolean = false,
    val external: Boolean = false,
    val selected: Boolean = false,
    val note: String? = null,
)

enum class PlayerPanel { NONE, AUDIO, SUBTITLE, SPEED, INFO, EPISODES }

enum class GestureKind { NONE, BRIGHTNESS, VOLUME, SPEED }

data class GestureState(
    val kind: GestureKind = GestureKind.NONE,
    val value: Float = 0f,
    val label: String = "",
)

data class PlayerUi(
    val title: String = "",
    val subtitle: String? = null,
    val player: ExoPlayer? = null,
    val playing: Boolean = false,
    val buffering: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val controlsVisible: Boolean = true,
    val panel: PlayerPanel = PlayerPanel.NONE,
    val audio: List<TrackOption> = emptyList(),
    val subtitles: List<TrackOption> = emptyList(),
    val speed: Float = 1f,
    val error: String? = null,
    val info: List<Pair<String, String>> = emptyList(),
    val capabilities: PlaybackCapabilities? = null,
    val canPlayNext: Boolean = false,
    /** 当前播放队列（剧集时为整季），用于选集面板。 */
    val episodes: List<QueueEntry> = emptyList(),
    val currentEpisodeId: String = "",
    val gesture: GestureState = GestureState(),
    val boosted: Boolean = false,
    val notice: String? = null,
    val subtitleNote: String? = null,
)

class PlayerActivity : ComponentActivity() {

    private var ui by mutableStateOf(PlayerUi())
    private var player: ExoPlayer? = null
    private var plan: PlaybackPlan? = null
    private var backend: MediaBackend? = null
    private var reporter: PlaybackReporter? = null
    private var currentItemId: String = ""
    private var nextItemId: String? = null
    private var lastPosition: Long = 0L
    private var lastControlsToggle: Long = 0L

    // 用户选中的版本 / 轨道（详情页带过来，切集时沿用音轨与字幕偏好）
    private var sourceId: String? = null
    private var audioIndex: Int? = null
    private var subtitleIndex: Int? = null
    private var burnSubtitle = false

    private var normalSpeed = 1f
    private var currentBrightness = 0.5f
    private var currentVolume = 0.5f
    private var rewindJob: kotlinx.coroutines.Job? = null

    /** 亮度 / 音量提示条的自动隐藏任务：手一停就撤掉，不用等松手。 */
    private var gestureHideJob: kotlinx.coroutines.Job? = null

    private val displayController by lazy { DisplayModeController(this) }
    private var rotationController: RotationController? = null
    private val prefs: Prefs get() = Graph.prefs

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        backend = Graph.backend()
        if (backend == null) {
            finish()
            return
        }

        val itemId = intent.getStringExtra(EXTRA_ITEM_ID).orEmpty()
        if (itemId.isBlank()) {
            finish()
            return
        }
        currentItemId = itemId
        PlayQueue.mark(itemId)
        lastControlsToggle = System.currentTimeMillis()
        nextItemId = intent.getStringExtra(EXTRA_NEXT_ID)
        sourceId = intent.getStringExtra(EXTRA_SOURCE_ID)
        audioIndex = intent.getIntExtra(EXTRA_AUDIO_INDEX, -1).takeIf { it >= 0 }
        subtitleIndex = intent.getIntExtra(EXTRA_SUBTITLE_INDEX, -1).takeIf { it >= 0 }
        burnSubtitle = intent.getBooleanExtra(EXTRA_BURN_SUBTITLE, false)
        normalSpeed = prefs.longPressSpeed

        ui = ui.copy(
            title = intent.getStringExtra(EXTRA_TITLE) ?: "正在播放",
            subtitle = intent.getStringExtra(EXTRA_SUBTITLE),
            canPlayNext = !nextItemId.isNullOrBlank(),
            episodes = PlayQueue.all(),
            currentEpisodeId = itemId,
        )

        readSystemLevels()

        setContent {
            RainPlayerTheme {
                PlayerScreen(
                    ui = ui,
                    onTogglePlay = ::togglePlay,
                    onSeekTo = ::seekTo,
                    onSeekBy = ::seekBy,
                    onToggleControls = ::toggleControls,
                    onPanel = { panel -> ui = ui.copy(panel = if (ui.panel == panel) PlayerPanel.NONE else panel) },
                    onSelectAudio = ::selectAudio,
                    onSelectSubtitle = ::selectSubtitle,
                    onSetSpeed = ::setSpeed,
                    onBack = { finish() },
                    onRetry = { retry(forceTranscode = false) },
                    onTranscode = { retry(forceTranscode = true) },
                    onDismissError = { ui = ui.copy(error = null) },
                    onNext = { playNext() },
                    onSelectEpisode = { playEpisode(it) },
                    onBrightnessDelta = ::applyBrightnessDelta,
                    onVolumeDelta = ::applyVolumeDelta,
                    onSpeedBoost = ::applySpeedBoost,
                )
            }
        }

        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)
        lifecycleScope.launch { startPlayback(itemId, startMs, forceTranscode = false) }
        lifecycleScope.launch { tickLoop() }
        lifecycleScope.launch { progressLoop() }
    }

    // ------------------------------------------------------------ 播放控制

    private suspend fun startPlayback(
        itemId: String,
        startMs: Long,
        forceTranscode: Boolean,
        keepSelection: Boolean = true,
    ) {
        ui = ui.copy(buffering = true, error = null, panel = PlayerPanel.NONE)
        val b = backend ?: return
        val result = runCatching {
            b.playback(
                itemId = itemId,
                startMs = startMs,
                forceTranscode = forceTranscode,
                sourceId = sourceId,
                audioIndex = if (keepSelection) audioIndex else null,
                subtitleIndex = if (keepSelection) subtitleIndex else null,
                burnSubtitle = if (keepSelection) burnSubtitle else false,
            )
        }
        val p = result.getOrElse { e ->
            ui = ui.copy(buffering = false, error = e.message ?: "无法获取播放地址")
            return
        }
        // 换片前先把上一部的进度落库，避免连播时丢失观看记录
        reporter?.finish(lastPosition)
        reporter = b.let { PlaybackReporter(it, p) }
        plan = p
        currentItemId = itemId
        ui = ui.copy(
            title = p.title.ifBlank { ui.title },
            subtitle = p.subtitle ?: ui.subtitle,
            durationMs = p.durationMs,
        )
        preparePlayer(p)
    }

    private suspend fun preparePlayer(p: PlaybackPlan) {
        releasePlayer()

        val subtitles = buildSubtitleConfigurations(p)
        val builder = MediaItem.Builder().setUri(p.url)
        if (p.isHls) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        if (subtitles.isNotEmpty()) builder.setSubtitleConfigurations(subtitles)

        val httpFactory = OkHttpDataSource.Factory(Http.streamClient)
            .setDefaultRequestProperties(p.headers)
            .setUserAgent(Http.userAgent)
        val dataSource = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSource)

        val exo = PlayerFactory.create(this, prefs, mediaSourceFactory)
        player = exo
        ui = ui.copy(player = exo, capabilities = PlayerCapabilities.detect(this))

        exo.setMediaItem(builder.build())
        exo.prepare()
        if (p.startPositionMs > 0) exo.seekTo(p.startPositionMs)
        exo.playWhenReady = true

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> ui = ui.copy(buffering = true)
                    Player.STATE_READY -> {
                        ui = ui.copy(buffering = false, durationMs = exo.duration.coerceAtLeast(0L))
                        reporter?.markReady(exo.currentPosition)
                        applyDisplayMode(exo)
                    }
                    Player.STATE_ENDED -> {
                        showControls()
                        ui = ui.copy(panel = PlayerPanel.NONE)
                        reporter?.finish(exo.duration.coerceAtLeast(0L))
                        if (prefs.autoPlayNext) playNext()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui = ui.copy(playing = isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = buildString {
                    append(error.errorCodeName)
                    error.message?.let { append("：").append(it.take(180)) }
                }
                showControls()
                ui = ui.copy(error = message, buffering = false)
            }

            override fun onTracksChanged(tracks: Tracks) {
                refreshTracks(exo)
            }
        })

        refreshTracks(exo)
        ui = ui.copy(info = buildInfo(p))
    }

    private fun applyDisplayMode(exo: ExoPlayer) {
        if (!prefs.matchDisplayMode) return
        val format = exo.videoFormat ?: return
        displayController.applyForVideo(format.width, format.height, format.frameRate)
    }

    private fun buildInfo(p: PlaybackPlan): List<Pair<String, String>> {
        val caps = ui.capabilities
        return buildList {
            add("片源" to (p.container?.uppercase() ?: "未知"))
            add("播放方式" to if (p.direct) "直通（不转码）" else "服务器转码")
            if (p.subtitleBurnedIn) add("字幕" to "服务器烧录（图形字幕）")
            add("动态范围" to (p.dynamicRange ?: "SDR"))
            if (p.dolbyVision) {
                add("杜比视界" to if (caps?.dolbyVision == true) "本机支持" else "本机不支持，将回退 HDR10/SDR")
            }
            add("HDR10" to if (caps?.hdr10 == true) "支持" else "不支持")
            add("HLG" to if (caps?.hlg == true) "支持" else "不支持")
            add("音频直通" to buildString {
                append(if (prefs.audioPassthrough) "开启" else "关闭")
                append("（")
                append(
                    listOfNotNull(
                        if (caps?.ac3Passthrough == true) "AC3" else null,
                        if (caps?.eac3Passthrough == true) "EAC3" else null,
                        if (caps?.dtsPassthrough == true) "DTS" else null,
                        if (caps?.trueHdPassthrough == true) "TrueHD" else null,
                    ).joinToString("/").ifBlank { "仅 PCM" }
                )
                append("）")
            })
            add("显示能力" to "${caps?.maxWidth ?: 0}x${caps?.maxHeight ?: 0} @ ${caps?.maxRefreshRate?.toInt() ?: 0}Hz")
            if (p.audioTracks.isNotEmpty()) add("音轨数" to "${p.audioTracks.size}")
            if (p.subtitleTracks.isNotEmpty()) add("字幕数" to "${p.subtitleTracks.size}")
        }
    }

    /**
     * 轨道清单以「服务器元数据」为准，再用播放器实际解析结果补上选中状态。
     *
     * 只依赖 exo.currentTracks 会漏掉两类轨道：本机既不能解码也不能直通的音轨
     * （例如 DTS-HD）、以及 PGS 这类没有渲染器的图形字幕——它们在 ExoPlayer
     * 里是 unsupported，界面上看起来就像「识别不出」。
     */
    private fun refreshTracks(exo: ExoPlayer) {
        val p = plan
        val audioGroups = mutableListOf<Pair<Int, Tracks.Group>>()
        val textGroups = mutableListOf<Pair<Int, Tracks.Group>>()
        exo.currentTracks.groups.forEachIndexed { index, group ->
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> audioGroups += index to group
                C.TRACK_TYPE_TEXT -> textGroups += index to group
            }
        }

        val audio = (p?.audioTracks ?: emptyList()).mapIndexed { i, track ->
            val matched = audioGroups.getOrNull(i)
            val note = AudioSupport.describe(this, track.codec, track.channels)
            TrackOption(
                key = "a-$i",
                label = buildString {
                    append(track.label)
                    track.codec?.let { append(" · ").append(it.uppercase()) }
                    track.channels?.let { if (it > 0) append(" · ").append(channelName(it)) }
                    if (note.isNotBlank()) append(" · ").append(note)
                },
                serverIndex = track.index,
                groupIndex = matched?.first ?: -1,
                trackIndex = 0,
                language = track.language,
                codec = track.codec,
                channels = track.channels,
                selected = matched?.second?.let { g ->
                    (0 until g.length).any { g.isTrackSelected(it) }
                } ?: (track.index == p?.audioStreamIndex),
            )
        }

        val subtitles = (p?.subtitleTracks ?: emptyList()).mapIndexed { i, track ->
            val matched = textGroups.firstOrNull { (_, group) ->
                (0 until group.length).any { idx ->
                    val f = group.getTrackFormat(idx)
                    f.label != null && f.label == track.label
                }
            }
            TrackOption(
                key = "s-$i",
                label = buildString {
                    append(track.label)
                    append(" · ").append(track.codecLabel)
                    if (track.forced) append(" · 强制")
                    if (track.imageBased) append(" · 图形字幕")
                },
                serverIndex = track.index,
                groupIndex = matched?.first ?: -1,
                trackIndex = 0,
                language = track.language,
                codec = track.codec,
                imageBased = track.imageBased,
                external = track.isExternal,
                selected = matched != null && (0 until matched.second.length).any {
                    matched.second.isTrackSelected(it)
                },
            )
        }

        ui = ui.copy(
            audio = audio,
            subtitles = subtitles,
            subtitleNote = if (subtitles.any { it.imageBased }) {
                "PGS 等图形字幕本机无法渲染，选择后需服务器转码烧录"
            } else null,
        )
    }

    private fun channelName(channels: Int): String = when (channels) {
        1 -> "单声道"
        2 -> "立体声"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${channels}ch"
    }

    private suspend fun buildSubtitleConfigurations(p: PlaybackPlan): List<MediaItem.SubtitleConfiguration> {
        val configs = mutableListOf<MediaItem.SubtitleConfiguration>()
        // 图形字幕（PGS/DVB/DVD）Media3 没有渲染器，塞进去只会得到一条空轨道，
        // 这类字幕走服务器烧录，不在这里添加。
        val externals = p.subtitleTracks.filter {
            it.isExternal && !it.url.isNullOrBlank() && !it.imageBased
        }
        externals.forEachIndexed { index, track ->
            val mime = mimeFor(track.codec, track.url) ?: return@forEachIndexed
            val uri = if (backend is FnOsBackend) {
                downloadSubtitle(track, index)?.let { Uri.fromFile(it) } ?: return@forEachIndexed
            } else {
                Uri.parse(track.url)
            }
            configs += MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime)
                .setLanguage(track.language)
                .setLabel(track.label)
                .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        return configs
    }

    private suspend fun downloadSubtitle(track: SubtitleTrack, index: Int): File? {
        val b = backend as? FnOsBackend ?: return null
        val ext = when (track.codec?.lowercase()) {
            "ass", "ssa" -> "ass"
            "vtt" -> "vtt"
            else -> "srt"
        }
        val dir = File(cacheDir, "subtitles").apply { mkdirs() }
        val file = File(dir, "sub_${currentItemId.hashCode()}_$index.$ext")
        if (file.exists() && file.length() > 0) return file
        return withContext(Dispatchers.IO) {
            if (b.downloadSubtitle(track, file)) file else null
        }
    }

    private fun mimeFor(codec: String?, url: String?): String? {
        val name = (codec ?: url?.substringAfterLast('.', "") ?: "").lowercase()
        return when {
            name.contains("ass") || name.contains("ssa") -> MimeTypes.TEXT_SSA
            name.contains("vtt") -> MimeTypes.TEXT_VTT
            name.contains("srt") || name.contains("subrip") || name.contains("sub") -> MimeTypes.APPLICATION_SUBRIP
            name.contains("ttml") || name.contains("dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun showControls() {
        lastControlsToggle = System.currentTimeMillis()
        ui = ui.copy(controlsVisible = true)
    }

    private fun togglePlay() {
        val exo = player ?: return
        if (exo.playbackState == Player.STATE_ENDED) {
            exo.seekTo(0)
            exo.play()
        } else {
            if (exo.isPlaying) exo.pause() else exo.play()
        }
        showControls()
    }

    private fun seekTo(ms: Long) {
        player?.seekTo(ms.coerceAtLeast(0L))
        showControls()
        ui = ui.copy(positionMs = ms)
        reporter?.progress(ms, true)
    }

    private fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        seekTo((exo.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    private fun toggleControls() {
        val now = System.currentTimeMillis()
        if (now - lastControlsToggle < 200) return
        lastControlsToggle = now
        ui = ui.copy(
            controlsVisible = !ui.controlsVisible,
            panel = if (ui.controlsVisible) PlayerPanel.NONE else ui.panel,
        )
    }

    // ------------------------------------------------------------ 轨道切换

    private fun selectAudio(option: TrackOption) {
        val exo = player ?: return
        ui = ui.copy(panel = PlayerPanel.NONE)
        audioIndex = option.serverIndex
        if (option.groupIndex >= 0) {
            runCatching {
                val group = exo.currentTracks.groups[option.groupIndex]
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    .build()
            }
            return
        }
        // 播放器里没有这条轨（多半是本机解不了），改让服务器转码成 AAC
        notice("该音轨本机无法解码，正在切换为服务器转码")
        val position = exo.currentPosition
        lifecycleScope.launch { startPlayback(currentItemId, position, forceTranscode = true) }
    }

    private fun selectSubtitle(option: TrackOption?) {
        val exo = player ?: return
        ui = ui.copy(panel = PlayerPanel.NONE)
        subtitleIndex = option?.serverIndex
        if (option == null) {
            burnSubtitle = false
            runCatching {
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .build()
            }
            return
        }
        if (option.imageBased) {
            // PGS / DVB / VOBSUB 只能由服务器烧录进画面
            notice("图形字幕需服务器转码烧录，画质可能略有下降")
            burnSubtitle = true
            val position = exo.currentPosition
            lifecycleScope.launch {
                startPlayback(currentItemId, position, forceTranscode = true)
            }
            return
        }
        burnSubtitle = false
        if (option.groupIndex >= 0) {
            runCatching {
                val group = exo.currentTracks.groups[option.groupIndex]
                exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    .build()
            }
        } else {
            notice("该字幕无法在当前播放方式下加载，正在切换为服务器转码")
            val position = exo.currentPosition
            lifecycleScope.launch { startPlayback(currentItemId, position, forceTranscode = true) }
        }
    }

    private fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        ui = ui.copy(speed = speed, panel = PlayerPanel.NONE)
    }

    private fun notice(text: String) {
        ui = ui.copy(notice = text)
        lifecycleScope.launch {
            delay(2_500)
            if (ui.notice == text) ui = ui.copy(notice = null)
        }
    }

    private fun retry(forceTranscode: Boolean) {
        val itemId = currentItemId
        val position = player?.currentPosition ?: 0L
        ui = ui.copy(error = null)
        lifecycleScope.launch { startPlayback(itemId, position, forceTranscode) }
    }

    /** 选集面板里点了某一集：直接切过去，队列指针同步移动。 */
    private fun playEpisode(id: String) {
        if (id == currentItemId) {
            ui = ui.copy(panel = PlayerPanel.NONE)
            return
        }
        if (!PlayQueue.jumpTo(id)) return
        currentItemId = id
        nextItemId = PlayQueue.peekNext()
        ui = ui.copy(
            error = null,
            panel = PlayerPanel.NONE,
            canPlayNext = !nextItemId.isNullOrBlank(),
            currentEpisodeId = id,
        )
        lifecycleScope.launch { startPlayback(id, 0L, false) }
    }

    private fun playNext() {
        val next = PlayQueue.peekNext() ?: nextItemId
        if (next.isNullOrBlank()) {
            showControls()
            return
        }
        ui = ui.copy(error = null)
        PlayQueue.advance()
        currentItemId = next
        lifecycleScope.launch { startPlayback(next, 0L, false) }
        nextItemId = PlayQueue.peekNext()
        ui = ui.copy(
            canPlayNext = !nextItemId.isNullOrBlank(),
            currentEpisodeId = next,
        )
    }

    // ------------------------------------------------------------ 手势与旋转

    private fun readSystemLevels() {
        currentBrightness = window.attributes.screenBrightness
            .takeIf { it in 0f..1f } ?: 0.5f
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    /** 手势是增量式：左右半屏上下滑，累加到一个 0..1 的绝对量上。 */
    private fun applyBrightnessDelta(delta: Float) {
        if (!prefs.gestureEnabled || !prefs.brightnessGesture) return
        currentBrightness = (currentBrightness + delta).coerceIn(0.02f, 1f)
        val attrs = window.attributes
        attrs.screenBrightness = currentBrightness
        window.attributes = attrs
        ui = ui.copy(
            gesture = GestureState(
                GestureKind.BRIGHTNESS,
                currentBrightness,
                "${(currentBrightness * 100).roundToInt()}%",
            )
        )
        scheduleGestureHide()
    }

    /** 提示条 0.9 秒后自动消失；还在长按倍速时不撤，松手由 applySpeedBoost 收。 */
    private fun scheduleGestureHide() {
        gestureHideJob?.cancel()
        gestureHideJob = lifecycleScope.launch {
            delay(900)
            if (!ui.boosted) ui = ui.copy(gesture = GestureState())
        }
    }

    private fun applyVolumeDelta(delta: Float) {
        if (!prefs.gestureEnabled || !prefs.volumeGesture) return
        currentVolume = (currentVolume + delta).coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = (currentVolume * max).roundToInt().coerceIn(0, max)
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
        ui = ui.copy(
            gesture = GestureState(
                GestureKind.VOLUME,
                currentVolume,
                "${(currentVolume * 100).roundToInt()}%",
            )
        )
        scheduleGestureHide()
    }

    /** 长按快进/快退：快进直接提播放速率，快退靠连续回跳（负速率在多数机型上不可靠）。 */
    private fun applySpeedBoost(active: Boolean, forward: Boolean) {
        val exo = player ?: return
        if (!prefs.gestureEnabled || !prefs.longPressSpeedEnabled) return
        if (active) {
            if (ui.boosted) return
            normalSpeed = ui.speed
            val speed = prefs.longPressSpeed
            if (forward) {
                exo.setPlaybackSpeed(speed)
            } else {
                rewindJob = lifecycleScope.launch {
                    while (isActive) {
                        val pos = (exo.currentPosition - 1_000L).coerceAtLeast(0L)
                        exo.seekTo(pos)
                        ui = ui.copy(positionMs = pos)
                        delay(180)
                    }
                }
            }
            ui = ui.copy(
                boosted = true,
                speed = speed,
                gesture = GestureState(
                    GestureKind.SPEED,
                    speed,
                    if (forward) "快进 ${speed}x" else "快退 ${speed}x",
                ),
            )
        } else {
            if (!ui.boosted) return
            rewindJob?.cancel()
            rewindJob = null
            gestureHideJob?.cancel()
            exo.setPlaybackSpeed(normalSpeed)
            ui = ui.copy(boosted = false, speed = normalSpeed, gesture = GestureState())
        }
    }

    private fun startRotation() {
        if (!prefs.autoRotate) return
        val controller = rotationController ?: RotationController(this) { orientation ->
            runCatching { requestedOrientation = orientation }
        }.also { rotationController = it }
        if (controller.start()) return
        // 没有加速度计时退回系统自动旋转（同样忽略旋转锁定）
        runCatching { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR }
    }

    private fun stopRotation() {
        rotationController?.stop()
    }

    // ------------------------------------------------------------ 生命周期

    private suspend fun tickLoop() {
        while (lifecycleScope.isActive) {
            val exo = player
            if (exo != null) {
                val position = exo.currentPosition.coerceAtLeast(0L)
                val duration = exo.duration.coerceAtLeast(0L)
                lastPosition = position
                if (position != ui.positionMs || duration != ui.durationMs) {
                    ui = ui.copy(positionMs = position, durationMs = duration)
                }
                if (ui.controlsVisible && exo.isPlaying) {
                    if (System.currentTimeMillis() - lastControlsToggle > 6000) {
                        ui = ui.copy(controlsVisible = false, panel = PlayerPanel.NONE)
                    }
                }
            }
            delay(500)
        }
    }

    private suspend fun progressLoop() {
        while (lifecycleScope.isActive) {
            delay(10_000)
            val exo = player ?: continue
            if (exo.playbackState != Player.STATE_READY) continue
            reporter?.progress(exo.currentPosition, !exo.isPlaying)
        }
    }

    /**
     * 遥控器按键：控制条隐藏时，方向键/确定键直接操作播放，避免「按键没反应」。
     * 控制条可见时交给 Compose 的焦点系统处理。
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        run {
            val hidden = !ui.controlsVisible
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                android.view.KeyEvent.KEYCODE_HEADSETHOOK -> {
                    togglePlay(); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    seekBy(30_000); return true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    seekBy(-30_000); return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER,
                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (hidden) { togglePlay(); return true }
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (hidden) { seekBy(-10_000); return true }
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (hidden) { seekBy(10_000); return true }
                }
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_MENU -> {
                    if (hidden) { showControls(); return true }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        if (prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        startRotation()
    }

    override fun onBackPressed() {
        when {
            ui.panel != PlayerPanel.NONE -> ui = ui.copy(panel = PlayerPanel.NONE)
            ui.controlsVisible -> ui = ui.copy(controlsVisible = false)
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        stopRotation()
        val exo = player ?: return
        // 后台只是暂停，不结束会话；位置照样落库
        reporter?.progress(exo.currentPosition, true)
        exo.pause()
    }

    override fun onStop() {
        super.onStop()
        val pos = player?.currentPosition ?: lastPosition
        if (isFinishing) {
            reporter?.finish(pos)
        } else {
            reporter?.progress(pos, true)
        }
    }

    override fun onDestroy() {
        displayController.restore()
        reporter?.finish(player?.currentPosition ?: lastPosition)
        releasePlayer()
        super.onDestroy()
    }

    private fun releasePlayer() {
        runCatching {
            player?.let {
                it.setVideoSurfaceView(null)
                it.release()
            }
        }
        player = null
        ui = ui.copy(player = null)
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_SUBTITLE = "subtitle"
        const val EXTRA_NEXT_ID = "next_id"
        const val EXTRA_START_MS = "start_ms"
        const val EXTRA_SOURCE_ID = "source_id"
        const val EXTRA_AUDIO_INDEX = "audio_index"
        const val EXTRA_SUBTITLE_INDEX = "subtitle_index"
        const val EXTRA_BURN_SUBTITLE = "burn_subtitle"

        fun intent(
            context: Context,
            itemId: String,
            title: String,
            subtitle: String? = null,
            nextId: String? = null,
            startMs: Long = 0L,
            sourceId: String? = null,
            audioIndex: Int? = null,
            subtitleIndex: Int? = null,
            burnSubtitle: Boolean = false,
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SUBTITLE, subtitle)
            putExtra(EXTRA_NEXT_ID, nextId)
            putExtra(EXTRA_START_MS, startMs)
            putExtra(EXTRA_SOURCE_ID, sourceId)
            putExtra(EXTRA_AUDIO_INDEX, audioIndex ?: -1)
            putExtra(EXTRA_SUBTITLE_INDEX, subtitleIndex ?: -1)
            putExtra(EXTRA_BURN_SUBTITLE, burnSubtitle)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
