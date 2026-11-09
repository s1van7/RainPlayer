package com.rain.player.player

import android.content.Context
import android.content.Intent
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
import com.rain.player.Graph
import com.rain.player.core.Http
import com.rain.player.core.Prefs
import com.rain.player.data.FnOsBackend
import com.rain.player.data.MediaBackend
import com.rain.player.data.PlaybackCapabilities
import com.rain.player.data.PlaybackPlan
import com.rain.player.ui.theme.RainTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TrackOption(
    val id: String,
    val label: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String? = null,
    val selected: Boolean = false,
)

enum class PlayerPanel { NONE, AUDIO, SUBTITLE, SPEED, INFO }

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
)

class PlayerActivity : ComponentActivity() {

    private var ui by mutableStateOf(PlayerUi())
    private var player: ExoPlayer? = null
    private var plan: PlaybackPlan? = null
    private var backend: MediaBackend? = null
    private var currentItemId: String = ""
    private var nextItemId: String? = null
    private var startReported = false
    private var lastPosition: Long = 0L
    private var lastControlsToggle: Long = 0L

    private val displayController by lazy { DisplayModeController(this) }
    private val prefs: Prefs get() = Graph.prefs

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
        ui = ui.copy(
            title = intent.getStringExtra(EXTRA_TITLE) ?: "正在播放",
            subtitle = intent.getStringExtra(EXTRA_SUBTITLE),
            canPlayNext = !nextItemId.isNullOrBlank(),
        )

        setContent {
            RainTheme {
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
                )
            }
        }

        val startMs = intent.getLongExtra(EXTRA_START_MS, 0L)
        lifecycleScope.launch { startPlayback(itemId, startMs, forceTranscode = false) }
        lifecycleScope.launch { tickLoop() }
        lifecycleScope.launch { progressLoop() }
    }

    // ------------------------------------------------------------ 播放控制

    private suspend fun startPlayback(itemId: String, startMs: Long, forceTranscode: Boolean) {
        ui = ui.copy(buffering = true, error = null, panel = PlayerPanel.NONE)
        val b = backend ?: return
        val result = runCatching { b.playback(itemId, startMs, forceTranscode) }
        val p = result.getOrElse { e ->
            ui = ui.copy(buffering = false, error = e.message ?: "无法获取播放地址")
            return
        }
        plan = p
        currentItemId = itemId
        startReported = false
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
                        if (!startReported) {
                            startReported = true
                            lifecycleScope.launch {
                                runCatching { backend?.reportStart(p, exo.currentPosition) }
                            }
                        }
                        applyDisplayMode(exo)
                    }
                    Player.STATE_ENDED -> {
                        showControls()
                        ui = ui.copy(panel = PlayerPanel.NONE)
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
            if (p.audioTracks.isNotEmpty()) {
                add("音轨数" to "${p.audioTracks.size}")
            }
            if (p.subtitleTracks.isNotEmpty()) {
                add("字幕数" to "${p.subtitleTracks.size}")
            }
        }
    }

    private fun refreshTracks(exo: ExoPlayer) {
        val audio = mutableListOf<TrackOption>()
        val subtitles = mutableListOf<TrackOption>()
        exo.currentTracks.groups.forEachIndexed { groupIndex, group ->
            val type = group.type
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val label = buildString {
                    append(
                        format.label
                            ?: format.language
                            ?: if (type == C.TRACK_TYPE_AUDIO) "音轨 ${trackIndex + 1}" else "字幕 ${trackIndex + 1}"
                    )
                    format.codecs?.let { append(" · ").append(it.uppercase()) }
                    if (type == C.TRACK_TYPE_AUDIO && format.channelCount > 0) {
                        append(" · ").append(channelName(format.channelCount))
                    }
                }
                val option = TrackOption(
                    id = "$groupIndex-$trackIndex",
                    label = label,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    language = format.language,
                    selected = group.isTrackSelected(trackIndex),
                )
                if (type == C.TRACK_TYPE_AUDIO) audio += option else subtitles += option
            }
        }
        ui = ui.copy(audio = audio, subtitles = subtitles)
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
        val externals = p.subtitleTracks.filter { it.isExternal && !it.url.isNullOrBlank() }
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

    private suspend fun downloadSubtitle(track: com.rain.player.data.SubtitleTrack, index: Int): File? {
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
    }

    private fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        seekTo((exo.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    private fun toggleControls() {
        val now = System.currentTimeMillis()
        if (now - lastControlsToggle < 200) return
        lastControlsToggle = now
        if (!ui.controlsVisible) lastControlsToggle = now
        ui = ui.copy(
            controlsVisible = !ui.controlsVisible,
            panel = if (ui.controlsVisible) PlayerPanel.NONE else ui.panel,
        )
    }

    private fun selectAudio(option: TrackOption) {
        val exo = player ?: return
        runCatching {
            val group = exo.currentTracks.groups[option.groupIndex]
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
                .build()
        }
        ui = ui.copy(panel = PlayerPanel.NONE)
    }

    private fun selectSubtitle(option: TrackOption?) {
        val exo = player ?: return
        runCatching {
            val params = exo.trackSelectionParameters.buildUpon()
            if (option == null) {
                params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT)
            } else {
                val group = exo.currentTracks.groups[option.groupIndex]
                params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            }
            exo.trackSelectionParameters = params.build()
        }
        ui = ui.copy(panel = PlayerPanel.NONE)
    }

    private fun setSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        ui = ui.copy(speed = speed, panel = PlayerPanel.NONE)
    }

    private fun retry(forceTranscode: Boolean) {
        val itemId = currentItemId
        val position = player?.currentPosition ?: 0L
        ui = ui.copy(error = null)
        lifecycleScope.launch { startPlayback(itemId, position, forceTranscode) }
    }

    private fun playNext() {
        val next = PlayQueue.peekNext() ?: nextItemId
        if (next.isNullOrBlank()) {
            showControls()
            return
        }
        ui = ui.copy(error = null)
        PlayQueue.advance()
        lifecycleScope.launch { startPlayback(next, 0L, false) }
        nextItemId = PlayQueue.peekNext()
        ui = ui.copy(canPlayNext = !nextItemId.isNullOrBlank())
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
            val p = plan ?: continue
            val exo = player ?: continue
            if (exo.playbackState != Player.STATE_READY) continue
            runCatching { backend?.reportProgress(p, exo.currentPosition, !exo.isPlaying) }
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

    override fun onBackPressed() {
        when {
            ui.panel != PlayerPanel.NONE -> ui = ui.copy(panel = PlayerPanel.NONE)
            ui.controlsVisible -> ui = ui.copy(controlsVisible = false)
            else -> super.onBackPressed()
        }
    }

    override fun onPause() {
        super.onPause()
        val p = plan
        val exo = player
        if (p != null && exo != null) {
            lifecycleScope.launch { runCatching { backend?.reportProgress(p, exo.currentPosition, true) } }
        }
        exo?.pause()
    }

    override fun onStop() {
        super.onStop()
        val p = plan
        val exo = player
        if (p != null && exo != null) {
            val pos = exo.currentPosition
            lifecycleScope.launch { runCatching { backend?.reportStop(p, pos) } }
        }
    }

    override fun onDestroy() {
        displayController.restore()
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

        fun intent(
            context: Context,
            itemId: String,
            title: String,
            subtitle: String? = null,
            nextId: String? = null,
            startMs: Long = 0L,
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_SUBTITLE, subtitle)
            putExtra(EXTRA_NEXT_ID, nextId)
            putExtra(EXTRA_START_MS, startMs)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
