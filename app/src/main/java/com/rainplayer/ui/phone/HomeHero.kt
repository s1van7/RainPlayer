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
import androidx.compose.foundation.layout.offset
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
 * 版式对齐参考录屏：
 * - 大图满屏铺满，从屏幕最上沿开始，状态栏图标压在画面上；
 * - 图片底边用**与该页背景同色**的渐变盖住，肉眼看不出边界（不用离屏遮罩，避免滚动掉帧）；
 * - 图下居中「★ 7.7  2026  动作/恐怖」，再是两行居中简介，最后是分页点；
 * - 左右切换时当前页 1.0，滑走的缩到 0.94 并轻微淡出。
 *
 * @param bgDeep 该页整页背景的顶部色，图片底部渐变终点必须用它，否则会出现色带。
 */
@Composable
fun DailyHero(
    items: List<MediaCard>,
    onOpen: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
    bgDeep: Color = rainPalette.bg,
    onTint: (Color?) -> Unit = {},
) {
    if (items.isEmpty()) return
    val p = rainPalette
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // 大图高度：屏高 60%，铺满宽度
    val imageHeight = (screenHeight * 0.60f).coerceIn(360.dp, 620.dp)

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
            modifier = Modifier.fillMaxWidth().height(imageHeight + 178.dp),
        ) { page ->
            val card = items[page]
            val detail = details[card.id]

            // 过渡：当前页 1.0，滑走时缩到 0.94 并淡出到 70%
            val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue.coerceIn(0f, 1f)

            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val s = 1f - offset * 0.06f
                        scaleX = s
                        scaleY = s
                        alpha = 1f - offset * 0.30f
                    }
                    .clickable { onOpen(card) },
            ) {
                Box(Modifier.fillMaxWidth().height(imageHeight)) {
                    AsyncImage(
                        // 用竖版封面（自带片名），走高清地址，铺满整屏也不糊
                        model = imageRequest(
                            card.bigUrl ?: card.posterUrl ?: card.backdropUrl,
                            allowHardware = false,
                        ),
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
                    // 顶部暗角：白色服务器胶囊压在亮色画面上也能读清
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
                    // 底部盖一层「本页背景色」的渐变，图片下沿自然化进背景，没有分界线也没有空白
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(190.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to bgDeep.copy(alpha = 0f),
                                    0.42f to bgDeep.copy(alpha = 0.45f),
                                    0.78f to bgDeep.copy(alpha = 0.92f),
                                    1f to bgDeep,
                                )
                            )
                    )
                }

                // 片名艺术字（Emby 的 Logo 图）压在图片下沿上，位置和参考图一致：
                // 大图 → 艺术字 → ★ 评分 · 年份 · 类型 → 简介 → 分页点
                val logo = card.logoUrl
                if (logo != null) {
                    AsyncImage(
                        model = imageRequest(logo),
                        contentDescription = card.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.74f)
                            .height(68.dp)
                            .align(Alignment.CenterHorizontally)
                            .offset(y = (-14).dp),
                    )
                } else {
                    // 没有艺术字就退回文字标题，别留一块空白
                    Text(
                        card.title,
                        color = p.text,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp)
                            .offset(y = (-10).dp),
                    )
                }

                // ★ 7.7   2026   动作/恐怖   PG-13（居中）
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
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 7.dp),
                    )
                }
            }
        }

        // 分页点：居中
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
