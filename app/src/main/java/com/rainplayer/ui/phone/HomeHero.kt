package com.rainplayer.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.rainplayer.ui.theme.RainPlayerTextDim
import com.rainplayer.ui.theme.rainPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页顶部的大图轮播。
 *
 * 结构对齐参考稿：整块铺满宽度的大图，底部压一层从透明到页面底色的渐变，
 * 标题、评分、简介、外站评分、分页点全部叠在图上，不再是一张带圆角的小卡片。
 */
@Composable
fun DailyHero(
    items: List<MediaCard>,
    onOpen: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = heroHeight(),
) {
    if (items.isEmpty()) return
    val p = rainPalette
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    // 详情与评分按条目缓存，来回滑不会重复请求
    val details = remember { mutableStateMapOf<String, MediaDetail>() }
    val ratings = remember { mutableStateMapOf<String, ExternalRatings>() }
    val current = items.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState.currentPage, items.size) {
        val card = items.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
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

    Box(modifier.fillMaxWidth().height(height)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val card = items[page]
            Box(Modifier.fillMaxSize().clickable { onOpen(card) }) {
                AsyncImage(
                    model = imageRequest(card.backdropUrl ?: card.posterUrl),
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // 顶部压一层暗角，保证左上角服务器胶囊与右上角按钮在任何画面上都看得清
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x99000000), Color.Transparent)
                            )
                        )
                )
                // 底部渐到页面底色，和下方内容自然衔接。
                // 浅色模式下文字改成深色，所以渐变要更早压到实色，否则读不清。
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (p.isLight) {
                                Brush.verticalGradient(
                                    0.30f to Color.Transparent,
                                    0.58f to p.bg.copy(alpha = 0.88f),
                                    0.78f to p.bg,
                                    1f to p.bg,
                                )
                            } else {
                                Brush.verticalGradient(
                                    0.35f to Color.Transparent,
                                    0.72f to p.bg.copy(alpha = 0.72f),
                                    1f to p.bg,
                                )
                            }
                        )
                )

                val detail = details[card.id]
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        detail?.title ?: card.title,
                        color = if (p.isLight) Color(0xFF12161C) else Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // ★ 7.4   2018 · 动画 / 奇幻
                    Row(
                        Modifier.padding(top = 8.dp),
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
                                ?.let { add(it.joinToString(" / ")) }
                            if (isEmpty()) card.subtitle?.let { add(it) }
                        }.joinToString("  ")
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                color = if (p.isLight) Color(0xCC161A20.toInt()) else Color(0xCCFFFFFF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    (detail?.overview ?: card.subtitle)?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = if (p.isLight) Color(0xB3161A20.toInt()) else Color(0xB3FFFFFF),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    val r = ratings[card.id]
                    if (r != null && !r.isEmpty) {
                        Row(
                            Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            r.tmdb?.let { HeroRatingChip("TMDB", "%.1f".format(it)) }
                            r.douban?.let { HeroRatingChip("豆瓣", "%.1f".format(it)) }
                        }
                    }
                }
            }
        }

        // 分页点
        if (items.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(if (selected) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Color.White else Color(0x66FFFFFF)
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

@Composable
private fun HeroRatingChip(source: String, value: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x66000000))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(source, color = RainPlayerTextDim, fontSize = 9.sp)
        Spacer(Modifier.width(5.dp))
        Text(value, color = RainPlayerGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** 大图高度：屏高的 62%，手机上大致 420~480dp。 */
@Composable
fun heroHeight(): Dp {
    val h = LocalConfiguration.current.screenHeightDp
    return (h * 0.62f).dp.coerceIn(340.dp, 560.dp)
}
