package com.rain.player.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.rain.player.ui.theme.RainAccent
import com.rain.player.ui.theme.RainSurfaceHigh
import com.rain.player.ui.theme.RainText
import com.rain.player.ui.theme.RainTextDim
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
) {
    val focusRequester = remember { FocusRequester() }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleControls() })
            }
    ) {
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

        if (ui.buffering && ui.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RainAccent)
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
                            color = RainText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ui.subtitle?.let {
                            Text(it, color = RainTextDim, fontSize = 13.sp, maxLines = 1)
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
                            thumbColor = RainAccent,
                            activeTrackColor = RainAccent,
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
                            color = RainText,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(20.dp))
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
                            color = RainTextDim,
                            fontSize = 12.sp,
                        )
                    }
                }

                // 侧边面板
                if (ui.panel != PlayerPanel.NONE) {
                    PanelOverlay(ui, onSelectAudio, onSelectSubtitle, onSetSpeed, onPanel)
                }
            }
        }

        ui.error?.let { message ->
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("播放失败") },
                text = {
                    Column {
                        Text(message, color = RainTextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "常见原因：片源编码超出本机解码能力（如 8K AV1 / 杜比视界 Profile 7），或服务器未开启直通。",
                            color = RainTextDim,
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
                containerColor = RainSurfaceHigh,
            )
        }
    }

    LaunchedEffect(ui.controlsVisible) {
        if (ui.controlsVisible) {
            runCatching { focusRequester.requestFocus() }
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
                .width(360.dp)
                .fillMaxHeight()
                .background(Color(0xF2151922))
                .padding(20.dp)
                .clickable(enabled = false) {},
        ) {
            Text(
                when (ui.panel) {
                    PlayerPanel.AUDIO -> "音轨"
                    PlayerPanel.SUBTITLE -> "字幕"
                    PlayerPanel.SPEED -> "倍速"
                    PlayerPanel.INFO -> "播放信息"
                    PlayerPanel.NONE -> ""
                },
                color = RainText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            when (ui.panel) {
                PlayerPanel.AUDIO -> OptionList(
                    options = ui.audio.map { it to it.label },
                    selectedId = ui.audio.firstOrNull { it.selected }?.id,
                    onClick = onSelectAudio,
                )

                PlayerPanel.SUBTITLE -> {
                    OptionRow(
                        label = "关闭字幕",
                        selected = ui.subtitles.none { it.selected },
                        onClick = { onSelectSubtitle(null) },
                    )
                    OptionList(
                        options = ui.subtitles.map { it to it.label },
                        selectedId = ui.subtitles.firstOrNull { it.selected }?.id,
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

                PlayerPanel.INFO -> {
                    LazyColumn {
                        items(ui.info) { (key, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(key, color = RainTextDim, fontSize = 13.sp, modifier = Modifier.width(96.dp))
                                Text(value, color = RainText, fontSize = 13.sp)
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
private fun OptionList(
    options: List<Pair<TrackOption, String>>,
    selectedId: String?,
    onClick: (TrackOption) -> Unit,
) {
    LazyColumn {
        items(options) { (option, label) ->
            OptionRow(label = label, selected = option.id == selectedId) { onClick(option) }
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
                    focused -> RainAccent
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
            color = if (focused) Color.Black else RainText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", color = if (focused) Color.Black else RainAccent, fontSize = 14.sp)
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
                if (focused) RainAccent else Color(0x40000000),
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
