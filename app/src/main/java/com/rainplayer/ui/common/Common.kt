package com.rainplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.rainplayer.data.MediaCard
import com.rainplayer.data.MediaType
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerFill
import com.rainplayer.ui.theme.RainPlayerGold
import com.rainplayer.ui.theme.RainPlayerStroke
import com.rainplayer.ui.theme.RainPlayerSurface
import com.rainplayer.ui.theme.RainPlayerSurfaceHigh
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim

/** 简单的加载状态容器，避免引入额外状态库。 */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RainPlayerAccent)
    }
}

@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("出错了", color = RainPlayerText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = RainPlayerTextDim, fontSize = 13.sp)
            if (onRetry != null) {
                Spacer(Modifier.height(14.dp))
                RainPlayerButton("重试", onClick = onRetry)
            }
        }
    }
}

@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = RainPlayerTextDim, fontSize = 14.sp)
    }
}

/** 焦点感知按钮：TV 上用方向键聚焦时高亮描边。 */
@Composable
fun RainPlayerButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused && primary -> Color.White
                    focused -> RainPlayerAccent
                    primary -> RainPlayerAccent
                    else -> RainPlayerFill
                }
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) RainPlayerAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = when {
                focused -> Color.Black
                primary -> Color.White
                else -> RainPlayerText
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** 竖版海报卡片。 */
@Composable
fun PosterCard(
    card: MediaCard,
    width: Dp = 132.dp,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) RainPlayerSurfaceHigh else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) RainPlayerAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width * 1.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(RainPlayerSurfaceHigh),
        ) {
            AsyncImage(
                model = imageRequest(card.posterUrl),
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (card.progress > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x66000000))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(card.progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(RainPlayerAccent)
                    )
                }
            }
            if (card.played) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("已看", color = RainPlayerTextDim, fontSize = 10.sp)
                }
            }
            // 剧集：右上角总集数。浅底 + 主题色字，和参考稿一致，颜色跟着主题走
            val count = card.episodeCount ?: 0
            if (card.type == MediaType.SERIES && count > 0) {
                BadgePill(
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                    text = "$count",
                    textColor = RainPlayerAccent,
                )
            }
            // 右下角评分
            card.rating?.takeIf { it > 0 }?.let { score ->
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(RainPlayerSurface.copy(alpha = 0.92f))
                        .border(1.dp, RainPlayerAccent.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("★", color = RainPlayerGold, fontSize = 9.sp)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "%.1f".format(score),
                        color = if (score >= 8.5f) RainPlayerAccent else RainPlayerText,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            color = RainPlayerText,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        card.subtitle?.let {
            Text(it, color = RainPlayerTextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 横版卡片（继续观看/剧集用）。 */
@Composable
fun WideCard(
    card: MediaCard,
    width: Dp = 240.dp,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) RainPlayerSurfaceHigh else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) RainPlayerAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width * 0.5625f)
                .clip(RoundedCornerShape(8.dp))
                .background(RainPlayerSurfaceHigh),
        ) {
            AsyncImage(
                model = imageRequest(card.backdropUrl ?: card.posterUrl),
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)))
                    )
            )
            // 播放按钮压在画面中央，和参考稿一致
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x8C000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            // 右下角「11:14 / 48:09」
            if (card.durationMs > 0) {
                Text(
                    buildString {
                        if (card.progressMs > 0) {
                            append(formatClock(card.progressMs))
                            append(" / ")
                        }
                        append(formatClock(card.durationMs))
                    },
                    color = Color(0xE6FFFFFF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp),
                )
            }
            if (card.progress > 0.01f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color(0x66000000))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(card.progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(RainPlayerAccent)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            color = RainPlayerText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        card.subtitle?.let {
            Text(it, color = RainPlayerTextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 毫秒 → 11:14 / 1:02:33，用于卡片角标和播放按钮。 */
fun formatClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun SectionRow(
    title: String,
    items: List<MediaCard>,
    cardWidth: Dp = 132.dp,
    wide: Boolean = false,
    onClick: (MediaCard) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(
            title,
            color = RainPlayerText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { card ->
                if (wide) {
                    WideCard(card, cardWidth) { onClick(card) }
                } else {
                    PosterCard(card, cardWidth) { onClick(card) }
                }
            }
        }
    }
}

@Composable
fun imageRequest(url: String?, allowHardware: Boolean = true): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        // 需要读像素（取主色）时必须关掉硬件位图，否则 getPixel 会直接抛异常
        .allowHardware(allowHardware)
        .build()
}

@Composable
fun MetaChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(RainPlayerFill)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = RainPlayerTextDim, fontSize = 11.sp)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RainPlayerStroke)
    )
}

@Composable
fun SpacerH(size: Dp) = Spacer(Modifier.width(size))

@Composable
fun TextOrEmpty(text: String?, modifier: Modifier = Modifier) {
    if (!text.isNullOrBlank()) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = modifier)
    }
}

@Composable
fun IconTextRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(RainPlayerAccent))
        Spacer(Modifier.width(8.dp))
        Text(text, color = RainPlayerText, fontSize = 14.sp)
    }
}

/** 封面上的小角标：浅底 + 主题色字，比纯黑底更贴合浅色主题。 */
@Composable
private fun BadgePill(
    modifier: Modifier = Modifier,
    text: String,
    textColor: Color,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(RainPlayerSurface.copy(alpha = 0.92f))
            .border(1.dp, textColor.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = textColor, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
    }
}
