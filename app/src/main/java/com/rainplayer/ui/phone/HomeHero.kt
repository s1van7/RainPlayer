package com.rainplayer.ui.phone

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.absoluteValue

/**
 * 首页顶部的每日推荐轮播。
 *
 * 版式对齐参考图：
 * - 大图**满屏铺满**，从屏幕最上沿开始，状态栏图标压在画面上；
 * - 图片底部渐隐到页面底色，和下面的内容连成一片；
 * - 图下居中一行「★ 6.9  2005  动画/悬疑  PG-13」，再下面是两行居中简介；
 * - 最后是居中的分页点。
 * 左右切换时当前页轻微缩放、邻页淡出，形成过渡。
 */
@Composable
fun DailyHero(
    items: List<MediaCard>,
    onOpen: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
    onTint: (Color?) -> Unit = {},
) {
    if (items.isEmpty()) return
    val p = rainPalette
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // 大图高度：屏高 58%，铺满宽度
    val imageHeight = (screenHeight * 0.58f).coerceIn(340.dp, 600.dp)

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
            modifier = Modifier.fillMaxWidth().height(imageHeight + 96.dp),
        ) { page ->
            val card = items[page]
            val detail = details[card.id]

            // 过渡：当前页 1.0，滑到一半时缩到 0.92 并淡出，切页不突兀
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue.coerceIn(0f, 1f)

            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = 1f - offset * 0.08f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - offset * 0.35f
                    }
                    .clickable { onOpen(card) },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(imageHeight)
                        // 需要离屏图层，底部才能用 DstIn 把图片本身擦成透明
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        // 底部把图片擦成透明，露出的就是整页主色底，
                        // 两边同一个色，不会再出现一条浅色的分界线
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.62f to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    AsyncImage(
                        // 用竖版封面（自带片名），和参考图一致
                        model = imageRequest(card.posterUrl ?: card.backdropUrl, allowHardware = false),
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
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
                    // 顶部压一层暗角：白色服务器胶囊压在亮色画面上也能读清
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x66000000), Color.Transparent)
                                )
                            )
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ★ 6.9   2005   动画/悬疑   PG-13（居中）
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (card.rating ?: detail?.rating)?.takeIf { it > 0 }?.let {
                        Text("★", color = RainPlayerGold, fontSize = 11.sp)
                        Spacer(Modifier.width(3.dp))
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
                        detail?.genres?.take(2)?.takeIf { it.isNotEmpty() }
                            ?.let { add(it.joinToString("/")) }
                        if (isEmpty()) card.subtitle?.let { add(it) }
                    }.joinToString("  ")
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            color = RainPlayerTextDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    detail?.officialRating?.takeIf { it.isNotBlank() }?.let { rating ->
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .border(1.dp, RainPlayerTextDim.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
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
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
                    )
                }
            }
        }

        // 分页点：居中
        if (items.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(if (selected) 6.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) p.text.copy(alpha = 0.8f)
                                else p.textDim.copy(alpha = 0.3f)
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
private fun dominantColor(bitmap: Bitmap): Color? = runCatching {
    // Config#HARDWARE 的位图不支持读像素，直接放弃染色（绝不在这里崩）
    if (bitmap.config == Bitmap.Config.HARDWARE) return null
    if (bitmap.isRecycled) return null

    val size = 12
    val small = Bitmap.createScaledBitmap(bitmap, size, size, false)
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
    Color(android.graphics.Color.HSVToColor(hsv))
}.getOrNull()
