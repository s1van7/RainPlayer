package com.rain.player.ui.common

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
import com.rain.player.data.MediaCard
import com.rain.player.ui.theme.RainAccent
import com.rain.player.ui.theme.RainSurfaceHigh
import com.rain.player.ui.theme.RainText
import com.rain.player.ui.theme.RainTextDim

/** 简单的加载状态容器，避免引入额外状态库。 */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = RainAccent)
    }
}

@Composable
fun ErrorView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("出错了", color = RainText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = RainTextDim, fontSize = 13.sp)
            if (onRetry != null) {
                Spacer(Modifier.height(14.dp))
                RainButton("重试", onClick = onRetry)
            }
        }
    }
}

@Composable
fun EmptyView(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = RainTextDim, fontSize = 14.sp)
    }
}

/** 焦点感知按钮：TV 上用方向键聚焦时高亮描边。 */
@Composable
fun RainButton(
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
                    focused -> RainAccent
                    primary -> RainAccent
                    else -> Color(0x22FFFFFF)
                }
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) RainAccent else Color.Transparent,
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
            color = if (focused) Color.Black else Color.White,
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
            .background(if (focused) RainSurfaceHigh else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) RainAccent else Color.Transparent,
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
                .background(RainSurfaceHigh),
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
                            .background(RainAccent)
                    )
                }
            }
            if (card.played) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xAA000000))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("已看", color = RainTextDim, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            color = if (focused) Color.White else RainText,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        card.subtitle?.let {
            Text(it, color = RainTextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .background(if (focused) RainSurfaceHigh else Color.Transparent)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) RainAccent else Color.Transparent,
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
                .background(RainSurfaceHigh),
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
                            .background(RainAccent)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            color = RainText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        card.subtitle?.let {
            Text(it, color = RainTextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
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
            color = RainText,
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
fun imageRequest(url: String?): ImageRequest {
    val context = LocalContext.current
    return ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
        .build()
}

@Composable
fun MetaChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x1FFFFFFF))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = RainTextDim, fontSize = 11.sp)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x14FFFFFF))
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
        Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(RainAccent))
        Spacer(Modifier.width(8.dp))
        Text(text, color = RainText, fontSize = 14.sp)
    }
}
