package com.rainplayer.ui.phone

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rainplayer.Graph
import com.rainplayer.data.ExternalRatings
import com.rainplayer.data.MediaCard
import com.rainplayer.data.MediaDetail
import com.rainplayer.data.MediaType
import com.rainplayer.data.RatingsRepo
import com.rainplayer.ui.common.imageRequest
import com.rainplayer.ui.theme.RainPlayerGold
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import com.rainplayer.ui.theme.rainPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页顶部的每日推荐轮播。
 *
 * 版式对齐参考录屏：
 * 一张占屏宽约 78% 的大卡片，右边露出下一张的边；图片下面是
 * 「★ 8.6  2011  剧情  TV-14」一行、两行简介、居中的分页点。
 * 整页背景会跟着当前这张图的主色轻微染色。
 */
@Composable
fun DailyHero(
    items: List<MediaCard>,
    onOpen: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
    imageHeight: Dp = heroImageHeight(),
    onTint: (Color?) -> Unit = {},
) {
    if (items.isEmpty()) return
    val p = rainPalette
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // 右侧露出的宽度：约屏宽的 22%，太窄看不出「还有下一张」
    val peek = (screenWidth * 0.22f).coerceIn(70.dp, 120.dp)

    // 详情 / 评分 / 主色都按条目缓存，来回滑不会重复请求
    val details = remember { mutableStateMapOf<String, MediaDetail>() }
    val ratings = remember { mutableStateMapOf<String, ExternalRatings>() }
    val tints = remember { mutableStateMapOf<String, Color>() }
    var tint by remember { mutableStateOf<Color?>(null) }

    val current = items.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState.currentPage, items.size) {
        val card = items.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        tint = tints[card.id]
        onTint(tint)
        val backend = Graph.backend()
        if (backend != null && details[card.id] == null) {
            runCatching { backend.detail(card.id) }.getOrNull()?.let { details[card.id] = it }
        }
        if (ratings[card.id] == null) {
            val d = details[card.id]
            ratings[card.id] = RatingsRepo.fetch(
                title = d?.title ?: card.title,
                originalTitle = d?.originalTitle,
                year = d?.year ?: card.year,
                isTv = (d?.type ?: card.type) == MediaType.SERIES,
                tmdbKey = Graph.prefs.tmdbApiKey,
                doubanEnabled = Graph.prefs.doubanRating,
            )
        }
    }

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(7000)
            val next = (pagerState.currentPage + 1) % items.size
            runCatching { pagerState.animateScrollToPage(next) }
        }
    }

    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(end = peek),
            pageSpacing = 10.dp,
            modifier = Modifier.fillMaxWidth().height(imageHeight + 112.dp),
        ) { page ->
            val card = items[page]
            val detail = details[card.id]
            Column(Modifier.fillMaxWidth().clickable { onOpen(card) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.surfaceHigh),
                ) {
                    AsyncImage(
                        // 海报通常自带片名，和参考稿一样不再额外压标题
                        model = imageRequest(card.posterUrl ?: card.backdropUrl),
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(imageHeight),
                        onSuccess = { state ->
                            val bmp = (state.result.drawable as? BitmapDrawable)?.bitmap
                            val c = bmp?.let { dominantColor(it) }
                            if (c != null) {
                                tints[card.id] = c
                                if (page == pagerState.currentPage) {
                                    tint = c
                                    onTint(c)
                                }
                            }
                        },
                    )
                    // 顶部暗角：保证左上角服务器胶囊在亮图上也能看清
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x73000000), Color.Transparent)
                                )
                            )
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ★ 8.6   2011   剧情   TV-14
                Row(
                    Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (card.rating ?: detail?.rating)?.takeIf { it > 0 }?.let {
                        Text("★", color = RainPlayerGold, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "%.1f".format(it),
                            color = RainPlayerGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    val meta = buildList {
                        (detail?.year ?: card.year)?.let { add("$it") }
                        detail?.genres?.take(3)?.takeIf { it.isNotEmpty() }
                            ?.let { add(it.joinToString(" ")) }
                        if (isEmpty()) card.subtitle?.let { add(it) }
                    }.joinToString("  ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = RainPlayerText.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    detail?.officialRating?.takeIf { it.isNotBlank() }?.let { rating ->
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .border(1.dp, RainPlayerTextDim.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(rating, color = RainPlayerTextDim, fontSize = 9.sp)
                        }
                    }
                }

                (detail?.overview ?: card.subtitle)?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = RainPlayerTextDim,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 7.dp),
                    )
                }
            }
        }

        // 分页点：居中，和参考稿一致
        if (items.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(if (selected) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) RainPlayerText.copy(alpha = 0.85f)
                                else RainPlayerTextDim.copy(alpha = 0.35f)
                            )
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
            }
        }
    }

    // 预取相邻一页的详情，滑动时不至于空一帧
    LaunchedEffect(current?.id) {
        val idx = items.indexOfFirst { it.id == current?.id }
        val next = items.getOrNull(idx + 1) ?: return@LaunchedEffect
        if (details[next.id] == null) {
            Graph.backend()?.let { b ->
                runCatching { b.detail(next.id) }.getOrNull()?.let { details[next.id] = it }
            }
        }
    }
}

/**
 * 取图片主色：缩到 12×12 取平均，再把饱和度抬高一点，
 * 免得灰色画面染出一片脏灰。
 */
private fun dominantColor(bitmap: Bitmap): Color? {
    val size = 12
    val small = runCatching { Bitmap.createScaledBitmap(bitmap, size, size, false) }.getOrNull()
        ?: return null
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    for (y in 0 until size) {
        for (x in 0 until size) {
            val c = small.getPixel(x, y)
            if (android.graphics.Color.alpha(c) < 200) continue
            r += android.graphics.Color.red(c)
            g += android.graphics.Color.green(c)
            b += android.graphics.Color.blue(c)
            n++
        }
    }
    if (small !== bitmap) small.recycle()
    if (n == 0) return null
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv,
    )
    hsv[1] = (hsv[1] * 1.15f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceIn(0.35f, 0.9f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 大图高度：屏高的 52%，手机上大致 320~460dp。 */
@Composable
fun heroImageHeight(): Dp {
    val h = LocalConfiguration.current.screenHeightDp
    return (h * 0.52f).dp.coerceIn(300.dp, 460.dp)
}
