package com.rainplayer.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.withTimeoutOrNull
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerSurfaceHigh
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    ui: PlayerUi,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onPanel: (PlayerPanel) -> Unit,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectSubtitle: (TrackOption?) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onTranscode: () -> Unit,
    onDismissError: () -> Unit,
    onNext: () -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onSpeedBoost: (active: Boolean, forward: Boolean) -> Unit,
    onSelectEpisode: (String) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize()) {
        // 视频画面
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { container ->
                val existing = container.getChildAt(0) as? PlayerView
                val exo = ui.player
                if (exo == null) {
                    existing?.player = null
                } else if (existing == null || existing.player !== exo) {
                    val view = existing ?: PlayerView(container.context).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        isFocusable = false
                        isFocusableInTouchMode = false
                        subtitleView?.setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                        subtitleView?.setFixedTextSize(
                            android.util.TypedValue.COMPLEX_UNIT_DIP,
                            24f
                        )
                    }
                    view.player = exo
                    if (view.parent == null) container.addView(view)
                }
            }
        )

        // 手势覆盖层：放在视频之上、控件之下，保证滑动手势不会被 PlayerView 吞掉
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(ui.panel) {
                    val slop = 32.dp.toPx()
                    val holdMs = 380L
                    awaitEachGesture {
                        // 只处理没人消费的触摸：控制条上的按钮点击不会被误判成「点击画面」
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        // 左右各占半屏：亮度/音量与长按快进快退用的是同一块区域
                        val leftSide = down.position.x < width / 2f
                        var pointerId = down.id
                        var lastY = down.position.y

                        // 阶段一：等长按超时 / 提前滑动 / 抬手。
                        //
                        // 这里必须用超时而不是在事件循环里比对时间戳：手指完全静止时
                        // 不会有新的 pointer event，循环压根不会被唤醒，长按就永远触发不了
                        // ——之前「必须长按后再滑一下才生效」就是这个原因。
                        val phase = withTimeoutOrNull(holdMs) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: return@withTimeoutOrNull 2
                                if (!change.pressed) return@withTimeoutOrNull 2
                                if (abs(change.position.y - down.position.y) > slop ||
                                    abs(change.position.x - down.position.x) > slop
                                ) {
                                    return@withTimeoutOrNull 1
                                }
                                pointerId = change.id
                            }
                            @Suppress("UNREACHABLE_CODE")
                            1
                        }

                        when (phase) {
                            // 超时 → 长按：直接进倍速，不需要再滑动
                            null -> {
                                onSpeedBoost(true, !leftSide)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    pointerId = change.id
                                }
                                onSpeedBoost(false, !leftSide)
                            }

                            // 先滑了 → 调亮度 / 音量
                            1 -> {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    if (!change.pressed) break
                                    // 上滑为增大：屏幕坐标 y 向下为正，取负
                                    val delta = -(change.position.y - lastY) / height * 1.6f
                                    if (leftSide) onBrightnessDelta(delta) else onVolumeDelta(delta)
                                    lastY = change.position.y
                                    change.consume()
                                    pointerId = change.id
                                }
                            }

                            // 抬手且没动 → 切换控制条
                            else -> onToggleControls()
                        }
                    }
                }
        )

        if (ui.buffering && ui.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RainPlayerAccent)
            }
        }

        // 手势指示：亮度 / 音量竖条，倍速角标
        GestureIndicator(ui.gesture)

        ui.notice?.let { text ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text, color = RainPlayerText, fontSize = 13.sp)
            }
        }

        AnimatedVisibility(
            visible = ui.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // 顶部渐变 + 标题
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC000000), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ui.title,
                            color = RainPlayerText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ui.subtitle?.let {
                            Text(it, color = RainPlayerTextDim, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    IconButton(icon = Icons.Filled.Close, label = null, onClick = onBack)
                }

                // 中间播放控制
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleButton(Icons.Filled.FastRewind) { onSeekBy(-10_000) }
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(Color(0xE6FFFFFF), RoundedCornerShape(38.dp))
                            .clickable { onTogglePlay() }
                            .focusRequester(focusRequester)
                            .focusable(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (ui.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    CircleButton(Icons.Filled.FastForward) { onSeekBy(10_000) }
                    if (ui.canPlayNext) {
                        CircleButton(Icons.Filled.SkipNext) { onNext() }
                    }
                }

                // 底部控制条
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6000000))
                            )
                        )
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    val duration = ui.durationMs.coerceAtLeast(1L)
                    val progress = if (dragging) dragValue else (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                    Slider(
                        value = progress,
                        onValueChange = {
                            dragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            dragging = false
                            onSeekTo((dragValue * duration).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = RainPlayerAccent,
                            activeTrackColor = RainPlayerAccent,
                            inactiveTrackColor = Color(0x55FFFFFF),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${formatTime(if (dragging) (dragValue * duration).toLong() else ui.positionMs)} / ${formatTime(ui.durationMs)}",
                            color = RainPlayerText,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(20.dp))
                        if (ui.episodes.size > 1) {
                            IconButton(Icons.Filled.VideoLibrary, "选集") { onPanel(PlayerPanel.EPISODES) }
                        }
                        IconButton(Icons.Filled.VolumeUp, "音轨") { onPanel(PlayerPanel.AUDIO) }
                        IconButton(Icons.Filled.Subtitles, "字幕") { onPanel(PlayerPanel.SUBTITLE) }
                        IconButton(Icons.Filled.Speed, "${ui.speed}x") { onPanel(PlayerPanel.SPEED) }
                        IconButton(Icons.Filled.Info, "信息") { onPanel(PlayerPanel.INFO) }
                        Spacer(Modifier.weight(1f))
                        Text(
                            buildString {
                                ui.capabilities?.let { caps ->
                                    if (caps.dolbyVision) append("DV ")
                                    if (caps.hdr10) append("HDR10 ")
                                    if (caps.trueHdPassthrough) append("TrueHD直通")
                                }
                            }.trim(),
                            color = RainPlayerTextDim,
                            fontSize = 12.sp,
                        )
                    }
                }

                // 侧边面板
                if (ui.panel != PlayerPanel.NONE) {
                    PanelOverlay(ui, onSelectAudio, onSelectSubtitle, onSetSpeed, onPanel, onSelectEpisode)
                }
            }
        }

        ui.error?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("播放失败") },
                text = {
                    Column {
                        Text(message, color = RainPlayerTextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "常见原因：片源编码超出本机解码能力（如 8K AV1 / 杜比视界 Profile 7），或服务器未开启直通。",
                            color = RainPlayerTextDim,
                            fontSize = 12.sp,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onTranscode) { Text("转码播放") }
                },
                dismissButton = {
                    TextButton(onClick = onRetry) { Text("重试") }
                },
                containerColor = RainPlayerSurfaceHigh,
            )
        }
    }

    LaunchedEffect(ui.controlsVisible) {
        if (ui.controlsVisible) {
            runCatching { focusRequester.requestFocus() }
        }
    }
}

/** 手势反馈：左侧亮度、右侧音量，中间显示倍速。 */
@Composable
private fun GestureIndicator(gesture: GestureState) {
    if (gesture.kind == GestureKind.NONE) return
    val icon = when (gesture.kind) {
        GestureKind.BRIGHTNESS -> Icons.Filled.BrightnessMedium
        GestureKind.VOLUME -> Icons.Filled.VolumeUp
        GestureKind.SPEED -> Icons.Filled.Speed
        GestureKind.NONE -> Icons.Filled.Info
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Color(0xB3000000), RoundedCornerShape(14.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(gesture.label, color = Color.White, fontSize = 13.sp)
            if (gesture.kind != GestureKind.SPEED) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .width(96.dp)
                        .height(4.dp)
                        .background(Color(0x55FFFFFF), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(gesture.value.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(RainPlayerAccent, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelOverlay(
    ui: PlayerUi,
    onSelectAudio: (TrackOption) -> Unit,
    onSelectSubtitle: (TrackOption?) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onPanel: (PlayerPanel) -> Unit,
    onSelectEpisode: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable { onPanel(ui.panel) },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                // 毛玻璃侧栏：半透明面板 + 左侧一道亮边，让底下的画面隐约透出来
                .background(
                    com.rainplayer.ui.theme.rainPalette.surface.copy(
                        alpha = if (com.rainplayer.ui.theme.ThemeState.glass) 0.82f else 1f
                    )
                )
                .padding(20.dp)
                .clickable(enabled = false) {},
        ) {
            Text(
                when (ui.panel) {
                    PlayerPanel.AUDIO -> "音轨"
                    PlayerPanel.SUBTITLE -> "字幕"
                    PlayerPanel.SPEED -> "倍速"
                    PlayerPanel.INFO -> "播放信息"
                    PlayerPanel.EPISODES -> "选集"
                    PlayerPanel.NONE -> ""
                },
                color = RainPlayerText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (ui.panel == PlayerPanel.SUBTITLE) {
                ui.subtitleNote?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = RainPlayerTextDim, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            when (ui.panel) {
                PlayerPanel.AUDIO -> OptionList(
                    options = ui.audio,
                    onClick = onSelectAudio,
                )

                PlayerPanel.SUBTITLE -> {
                    OptionRow(
                        label = "关闭字幕",
                        selected = ui.subtitles.none { it.selected },
                        onClick = { onSelectSubtitle(null) },
                    )
                    OptionList(
                        options = ui.subtitles,
                        onClick = { onSelectSubtitle(it) },
                    )
                }

                PlayerPanel.SPEED -> {
                    LazyColumn {
                        items(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) { speed ->
                            OptionRow(
                                label = "${speed}x",
                                selected = abs(ui.speed - speed) < 0.01f,
                                onClick = { onSetSpeed(speed) },
                            )
                        }
                    }
                }

                PlayerPanel.EPISODES -> {
                    LazyColumn {
                        items(ui.episodes) { ep ->
                            OptionRow(
                                label = if (ep.label.isBlank()) ep.title else "${ep.label} · ${ep.title}",
                                selected = ep.id == ui.currentEpisodeId,
                                onClick = { onSelectEpisode(ep.id) },
                            )
                        }
                    }
                }

                PlayerPanel.INFO -> {
                    LazyColumn {
                        items(ui.info) { (key, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(key, color = RainPlayerTextDim, fontSize = 13.sp, modifier = Modifier.width(96.dp))
                                Text(value, color = RainPlayerText, fontSize = 13.sp)
                            }
                        }
                    }
                }

                PlayerPanel.NONE -> Unit
            }
        }
    }
}

@Composable
private fun OptionList(options: List<TrackOption>, onClick: (TrackOption) -> Unit) {
    LazyColumn {
        items(options, key = { it.key }) { option ->
            OptionRow(label = option.label, selected = option.selected) { onClick(option) }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                when {
                    focused -> RainPlayerAccent
                    selected -> Color(0x332A3550)
                    else -> Color(0x14FFFFFF)
                },
                RoundedCornerShape(8.dp),
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (focused) Color.Black else RainPlayerText,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = if (focused) Color.Black else RainPlayerAccent, fontSize = 14.sp)
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                if (focused) RainPlayerAccent else Color(0x40000000),
                RoundedCornerShape(28.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun IconButton(icon: ImageVector, label: String?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .background(
                if (focused) Color(0x55FFFFFF) else Color(0x22FFFFFF),
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
        if (!label.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 12.sp)
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
