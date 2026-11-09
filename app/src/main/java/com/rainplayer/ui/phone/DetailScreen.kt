package com.rainplayer.ui.phone

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.rainplayer.data.Episode
import com.rainplayer.data.MediaDetail
import com.rainplayer.data.MediaType
import com.rainplayer.data.PlaybackOptions
import com.rainplayer.data.Season
import com.rainplayer.player.PlayerActivity
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.common.ErrorView
import com.rainplayer.ui.common.LoadingView
import com.rainplayer.ui.common.formatClock
import com.rainplayer.ui.common.imageRequest
import com.rainplayer.ui.theme.GlassSurface
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerGold
import com.rainplayer.ui.theme.RainPlayerSurface
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import com.rainplayer.ui.theme.rainPalette
import kotlinx.coroutines.launch

/**
 * 详情页。
 *
 * 排版顺序对齐参考稿：
 * 大图 + 海报 → 评分与分级 → 时长/日期/画质 → 类型 → 播放按钮 → 操作图标 →
 * 简介 → 外站评分 → 选集 → 演职人员 → 类似作品 → 外站链接 → 媒体信息。
 */
@Composable
fun DetailScreen(
    session: AppSession,
    itemId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenItem: (com.rainplayer.data.MediaCard) -> Unit,
    onOpenEpisode: (Episode) -> Unit = {},
    /** 在当前页原地换成另一集（不叠栈），选集条用它。 */
    onReplaceDetail: (String, String) -> Unit = { _, _ -> },
) {
    val backend = session.backend
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = rainPalette

    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableStateOf<Season?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var favorite by remember { mutableStateOf(false) }
    var played by remember { mutableStateOf(false) }
    var similar by remember { mutableStateOf<List<com.rainplayer.data.MediaCard>>(emptyList()) }
    var ratings by remember { mutableStateOf(com.rainplayer.data.ExternalRatings()) }

    // 当前条目本身是一集时：记住它所属的剧集 / 季，并拉出同季其它集供切换
    var currentEpisode by remember { mutableStateOf<Episode?>(null) }
    var seriesSeasons by remember { mutableStateOf<List<Season>>(emptyList()) }
    var siblings by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var episodeSeasonId by remember { mutableStateOf<String?>(null) }
    /** 选集条最右侧「全部集数」九宫格。 */
    var showAllEpisodes by remember { mutableStateOf(false) }

    var options by remember { mutableStateOf<PlaybackOptions?>(null) }
    var versionId by remember { mutableStateOf<String?>(null) }
    var audioIndex by remember { mutableStateOf<Int?>(null) }
    var subtitleIndex by remember { mutableStateOf<Int?>(null) }
    var dialog by remember { mutableStateOf<DetailDialog?>(null) }

    LaunchedEffect(itemId, backend) {
        if (backend == null) return@LaunchedEffect
        loading = true
        runCatching { backend.detail(itemId) }
            .onSuccess { d ->
                detail = d
                episodes = d.episodes
                selectedSeason = d.seasons.firstOrNull()
                favorite = d.favorite
                played = d.played
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(itemId, backend) {
        val b = backend ?: return@LaunchedEffect
        similar = runCatching { b.similar(itemId, 14) }.getOrDefault(emptyList())
    }

    LaunchedEffect(itemId, detail?.title) {
        val d = detail ?: return@LaunchedEffect
        ratings = com.rainplayer.data.RatingsRepo.fetch(
            title = d.title,
            originalTitle = d.originalTitle,
            year = d.year,
            isTv = d.type == MediaType.SERIES,
            tmdbKey = com.rainplayer.Graph.prefs.tmdbApiKey,
            doubanEnabled = com.rainplayer.Graph.prefs.doubanRating,
        )
    }

    // 单集：找到所属剧集与季，把同季的其它集拉出来
    LaunchedEffect(itemId, detail?.type, backend) {
        val d = detail ?: return@LaunchedEffect
        if (d.type != MediaType.EPISODE) {
            currentEpisode = null
            seriesSeasons = emptyList()
            siblings = emptyList()
            return@LaunchedEffect
        }
        val b = backend ?: return@LaunchedEffect
        val ep = runCatching { b.episode(itemId) }.getOrNull() ?: return@LaunchedEffect
        currentEpisode = ep
        val seriesId = ep.seriesId ?: return@LaunchedEffect
        val seasons = runCatching { b.seasons(seriesId) }.getOrDefault(emptyList())
        seriesSeasons = seasons
        val seasonId = ep.seasonId
            ?: seasons.firstOrNull { it.index == ep.seasonIndex }?.id
            ?: seasons.firstOrNull()?.id
        if (seasonId != null) {
            episodeSeasonId = seasonId
            siblings = runCatching { b.episodes(seriesId, seasonId) }.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(itemId, versionId, detail?.playTargetId) {
        val b = backend ?: return@LaunchedEffect
        val target = detail?.playTargetId ?: return@LaunchedEffect
        runCatching { b.playbackOptions(target, versionId) }
            .onSuccess { o ->
                options = o
                if (audioIndex == null) audioIndex = o.defaultAudioIndex
                if (subtitleIndex == null) subtitleIndex = o.defaultSubtitleIndex
            }
    }

    LaunchedEffect(selectedSeason?.id) {
        val b = backend ?: return@LaunchedEffect
        val season = selectedSeason ?: return@LaunchedEffect
        val d = detail ?: return@LaunchedEffect
        if (d.seasons.size > 1) {
            runCatching { b.episodes(itemId, season.id) }.onSuccess { episodes = it }
        }
    }

    fun startPlayback(targetId: String, subtitle: String?, fromStart: Boolean = false) {
        val d = detail ?: return
        val list = episodes
        val index = list.indexOfFirst { it.id == targetId }
        val next = if (index >= 0) list.getOrNull(index + 1)?.id else null
        if (list.isNotEmpty()) {
            com.rainplayer.player.PlayQueue.set(
                list.map { com.rainplayer.player.QueueEntry(it.id, it.title, it.label) },
                targetId,
            )
        } else {
            com.rainplayer.player.PlayQueue.clear()
        }
        val chosenSubtitle = options?.subtitleTracks?.firstOrNull { it.index == subtitleIndex }
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                itemId = targetId,
                title = d.title,
                subtitle = subtitle ?: d.originalTitle,
                nextId = next,
                sourceId = versionId,
                audioIndex = audioIndex,
                subtitleIndex = subtitleIndex,
                burnSubtitle = chosenSubtitle?.imageBased == true,
                startMs = if (fromStart) -1L else 0L,
            )
        )
    }

    /** 当前应该播哪一集：优先续播未看完的，其次第一个没看过的，最后第一集。 */
    fun resumeTarget(d: MediaDetail): String {
        if (d.type != MediaType.SERIES) return d.playTargetId
        val list = episodes
        return list.firstOrNull { it.progress > 0.01f && !it.played }?.id
            ?: list.firstOrNull { !it.played }?.id
            ?: list.firstOrNull()?.id
            ?: d.playTargetId
    }

    fun resumeMs(d: MediaDetail, target: String): Long {
        val ep = episodes.firstOrNull { it.id == target }
        return ep?.progressMs ?: if (d.type == MediaType.SERIES) 0L else d.progressMs
    }

    Box(Modifier.fillMaxSize()) {
        DetailOptionDialog(
            dialog = dialog,
            options = options,
            versionId = versionId,
            audioIndex = audioIndex,
            subtitleIndex = subtitleIndex,
            onDismiss = { dialog = null },
            onVersion = {
                versionId = it
                audioIndex = null
                subtitleIndex = null
                dialog = null
            },
            onAudio = {
                audioIndex = it
                dialog = null
            },
            onSubtitle = {
                subtitleIndex = it
                dialog = null
            },
        )

        when {
            loading -> LoadingView()
            error != null -> ErrorView(error!!) { }
            detail == null -> ErrorView("未找到该内容")
            else -> {
                val d = detail!!
                val target = resumeTarget(d)
                val resume = resumeMs(d, target)
                val targetEp = episodes.firstOrNull { it.id == target }
                val version = options?.versions?.firstOrNull { it.id == versionId }
                    ?: options?.versions?.firstOrNull()
                val audio = options?.audioTracks?.firstOrNull { it.index == audioIndex }
                    ?: options?.audioTracks?.firstOrNull()

                LazyColumn(contentPadding = PaddingValues(bottom = 36.dp)) {
                    // ---------------------------------------------------- 大图
                    item {
                        Box(Modifier.fillMaxWidth().height(420.dp)) {
                            AsyncImage(
                                model = imageRequest(d.backdropUrl ?: d.posterUrl),
                                contentDescription = d.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color(0x66000000),
                                            0.45f to Color.Transparent,
                                            0.78f to p.bg.copy(alpha = 0.86f),
                                            1f to p.bg,
                                        )
                                    )
                            )

                            // 顶部按钮：返回 / 搜索 / 更多
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RoundIconButton(Icons.Filled.ArrowBack, "返回", onBack)
                                Spacer(Modifier.weight(1f))
                                RoundIconButton(Icons.Filled.Refresh, "刷新") {
                                    scope.launch {
                                        runCatching { backend?.detail(itemId) }.getOrNull()?.let {
                                            detail = it
                                            episodes = it.episodes
                                        }
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                RoundIconButton(Icons.Filled.Subtitles, "字幕") {
                                    dialog = DetailDialog.SUBTITLE
                                }
                            }

                            // 海报压在图上，居中
                            AsyncImage(
                                model = imageRequest(d.posterUrl),
                                contentDescription = d.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 18.dp)
                                    .width(136.dp)
                                    .height(204.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RainPlayerSurface),
                            )
                        }
                    }

                    // ------------------------------------------- 标题 / 评分 / 元信息
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                d.title,
                                color = RainPlayerText,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            d.originalTitle?.takeIf { it != d.title }?.let {
                                Text(
                                    it,
                                    color = RainPlayerTextDim,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // ★ 8.0  [R]
                            Row(
                                Modifier.padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                (d.rating ?: ratings.tmdb)?.takeIf { it > 0 }?.let {
                                    Text("★", color = RainPlayerGold, fontSize = 13.sp)
                                    Text(
                                        "%.1f".format(it),
                                        color = RainPlayerGold,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                d.officialRating?.takeIf { it.isNotBlank() }?.let {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .border(1.dp, RainPlayerTextDim, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(it, color = RainPlayerTextDim, fontSize = 9.sp)
                                    }
                                }
                            }

                            // 时长 / 上映 / 画质
                            val meta = buildList {
                                d.runtimeMinutes?.takeIf { it > 0 }?.let { add("${it}分钟") }
                                d.premiereDate?.takeIf { it.isNotBlank() }?.let { add(it) }
                                version?.resolutionLabel?.let { add(it) }
                                version?.dynamicRange?.takeIf { it.isNotBlank() }?.let { add(it) }
                                version?.videoCodec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                            }
                            if (meta.isNotEmpty()) {
                                Text(
                                    meta.joinToString("   "),
                                    color = RainPlayerTextDim,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }

                            if (d.genres.isNotEmpty()) {
                                Text(
                                    d.genres.take(4).joinToString(" · "),
                                    color = RainPlayerTextDim,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    // -------------------------------------------------- 播放按钮
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color.White)
                                .clickable {
                                    val label = targetEp?.let { "第 ${it.seasonIndex} 季 · 第 ${it.index} 集" }
                                    startPlayback(target, label)
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (resume > 0) "继续播放" else "播放",
                                    color = Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (resume > 0) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        formatClock(resume),
                                        color = Color(0x99000000),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }

                    // ------------------------------------------------ 操作图标行
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ActionIcon(
                                Icons.Filled.CheckCircle,
                                if (played) "已看" else "标记已看",
                                active = played,
                            ) {
                                played = !played
                                scope.launch { backend?.setWatched(itemId, played) }
                            }
                            ActionIcon(
                                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (favorite) "已收藏" else "收藏",
                                active = favorite,
                            ) {
                                favorite = !favorite
                                scope.launch { backend?.setFavorite(itemId, favorite) }
                            }
                            ActionIcon(Icons.Filled.VolumeUp, "音轨") { dialog = DetailDialog.AUDIO }
                            ActionIcon(Icons.Filled.Subtitles, "字幕") { dialog = DetailDialog.SUBTITLE }
                            ActionIcon(Icons.Filled.Refresh, "版本") { dialog = DetailDialog.VERSION }
                        }
                    }

                    // ------------------------------------------- 选集（单集专用）
                    if (d.type == MediaType.EPISODE && siblings.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "选集",
                                        color = RainPlayerText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    currentEpisode?.let { cur ->
                                        Text(
                                            "第 ${cur.seasonIndex} 季 · 共 ${siblings.size} 集",
                                            color = RainPlayerTextDim,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    currentEpisode?.seriesId?.let { sid ->
                                        Text(
                                            "整部剧集 ›",
                                            color = RainPlayerAccent,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    onReplaceDetail(
                                                        sid,
                                                        currentEpisode?.seriesName ?: d.title,
                                                    )
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }

                                if (seriesSeasons.size > 1) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(seriesSeasons) { season ->
                                            val selected = season.id == episodeSeasonId
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (selected) RainPlayerAccent else RainPlayerSurface)
                                                    .clickable {
                                                        if (!selected) {
                                                            episodeSeasonId = season.id
                                                            scope.launch {
                                                                val list = runCatching {
                                                                    backend?.episodes(
                                                                        currentEpisode?.seriesId ?: return@launch,
                                                                        season.id,
                                                                    )
                                                                }.getOrNull()
                                                                if (list != null) siblings = list
                                                            }
                                                        }
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    season.name,
                                                    color = if (selected) Color.Black else RainPlayerText,
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LazyRow(
                                        Modifier.weight(1f),
                                        contentPadding = PaddingValues(
                                            start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp,
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(siblings, key = { it.id }) { ep ->
                                            val selected = ep.id == itemId
                                            Box(
                                                Modifier
                                                    .clip(RoundedCornerShape(9.dp))
                                                    .background(
                                                        if (selected) RainPlayerAccent
                                                        else RainPlayerSurface
                                                    )
                                                    .clickable {
                                                        if (!selected) onReplaceDetail(ep.id, ep.title)
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                                            ) {
                                                Text(
                                                    "${ep.index}",
                                                    color = if (selected) Color.Black else RainPlayerText,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selected) FontWeight.SemiBold
                                                    else FontWeight.Normal,
                                                )
                                            }
                                        }
                                    }
                                    // 最右边：直接跳集号（九宫格）
                                    Box(
                                        Modifier
                                            .padding(end = 20.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(RainPlayerAccent.copy(alpha = 0.16f))
                                            .border(
                                                1.dp,
                                                RainPlayerAccent.copy(alpha = 0.45f),
                                                RoundedCornerShape(9.dp),
                                            )
                                            .clickable { showAllEpisodes = true }
                                            .padding(horizontal = 11.dp, vertical = 9.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.GridView,
                                            contentDescription = "全部集数",
                                            tint = RainPlayerAccent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------- 简介
                    item {
                        Text(
                            d.overview ?: "暂无简介",
                            color = RainPlayerTextDim,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }

                    // ------------------------------------------------ 外站评分
                    if (!ratings.isEmpty) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ratings.tmdb?.let { RatingBadge("TMDB", it, ratings.tmdbVotes) }
                                ratings.douban?.let { RatingBadge("豆瓣", it, ratings.doubanVotes) }
                                d.criticRating?.takeIf { it > 0 }?.let {
                                    RatingBadge("烂番茄", it, null, percent = true)
                                }
                            }
                        }
                    }

                    // ------------------------------------------------ 选集
                    if (d.type == MediaType.SERIES && episodes.isNotEmpty()) {
                        if (d.seasons.size > 1) {
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(d.seasons) { season ->
                                        val selected = season.id == selectedSeason?.id
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) RainPlayerAccent else RainPlayerSurface)
                                                .clickable { selectedSeason = season }
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                season.name,
                                                color = if (selected) Color.Black else RainPlayerText,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                SectionTitle("选集", Modifier.padding(top = 14.dp, bottom = 6.dp))
                            }
                        }

                        items(episodes, key = { it.id }) { ep ->
                            EpisodeRow(ep, d.title) { onOpenEpisode(ep) }
                        }
                    }

                    // ---------------------------------------------- 演职人员
                    if (d.people.isNotEmpty()) {
                        item {
                            Column {
                                SectionTitle("演职人员", Modifier.padding(top = 16.dp, bottom = 8.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    items(d.people) { person ->
                                        Column(Modifier.width(76.dp)) {
                                            AsyncImage(
                                                model = imageRequest(person.imageUrl),
                                                contentDescription = person.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(76.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(RainPlayerSurface),
                                            )
                                            Spacer(Modifier.height(5.dp))
                                            Text(
                                                person.name,
                                                color = RainPlayerText,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            person.role?.takeIf { it.isNotBlank() }?.let {
                                                Text(
                                                    it,
                                                    color = RainPlayerTextDim,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------- 类似作品
                    if (similar.isNotEmpty()) {
                        item {
                            Column {
                                SectionTitle("类似作品", Modifier.padding(top = 18.dp, bottom = 8.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(similar, key = { it.id }) { card ->
                                        com.rainplayer.ui.common.PosterCard(card, 112.dp) { onOpenItem(card) }
                                    }
                                }
                            }
                        }
                    }

                    // ------------------------------------------------ 外站链接
                    val links = ratings.links(d.type == MediaType.SERIES)
                    if (links.isNotEmpty()) {
                        item {
                            Column {
                                SectionTitle("链接", Modifier.padding(top = 18.dp, bottom = 8.dp))
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    links.forEach { (name, url) ->
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                                                .clickable {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                        )
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(name, color = RainPlayerText, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ------------------------------------------------ 媒体信息
                    item {
                        Column {
                            SectionTitle("媒体信息", Modifier.padding(top = 18.dp, bottom = 8.dp))
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                MediaInfoCard(
                                    title = "视频",
                                    icon = Icons.Filled.PlayArrow,
                                    rows = buildList {
                                        version?.name?.takeIf { it.isNotBlank() }?.let { add("标题" to it) }
                                        version?.videoCodec?.let { add("编码" to it.lowercase()) }
                                        version?.let { v ->
                                            if (v.width != null && v.height != null) {
                                                add("分辨率" to "${v.width}x${v.height}")
                                            }
                                        }
                                        version?.bitrate?.takeIf { it > 0 }?.let {
                                            add("比特率" to "%.1f mbps".format(it / 1_000_000.0))
                                        }
                                        version?.dynamicRange?.takeIf { it.isNotBlank() }?.let {
                                            add("动态范围" to it)
                                        }
                                        version?.container?.takeIf { it.isNotBlank() }?.let {
                                            add("封装" to it.uppercase())
                                        }
                                        version?.sizeBytes?.takeIf { it > 0 }?.let {
                                            add("大小" to "%.1f GB".format(it / 1_073_741_824.0))
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                MediaInfoCard(
                                    title = "音频",
                                    icon = Icons.Filled.VolumeUp,
                                    rows = buildList {
                                        audio?.label?.takeIf { it.isNotBlank() }?.let { add("标题" to it) }
                                        audio?.codec?.let { add("编码" to it.uppercase()) }
                                        audio?.channels?.takeIf { it > 0 }?.let { add("声道" to "$it") }
                                        audio?.language?.takeIf { it.isNotBlank() }?.let { add("语言" to it) }
                                        audio?.bitrate?.takeIf { it > 0 }?.let {
                                            add("比特率" to "%.0f kbps".format(it / 1000.0))
                                        }
                                        add("外部" to "否")
                                        add("默认" to if (audio?.index == options?.defaultAudioIndex) "是" else "否")
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAllEpisodes && siblings.isNotEmpty()) {
        EpisodeGridDialog(
            episodes = siblings,
            currentId = itemId,
            subtitle = currentEpisode?.let { "第 ${it.seasonIndex} 季 · 共 ${siblings.size} 集" },
            onDismiss = { showAllEpisodes = false },
            onPick = { ep ->
                showAllEpisodes = false
                if (ep.id != itemId) onReplaceDetail(ep.id, ep.title)
            },
        )
    }
}

/** 全部集数九宫格：6 列，点哪集跳哪集。 */
@Composable
private fun EpisodeGridDialog(
    episodes: List<Episode>,
    currentId: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    onPick: (Episode) -> Unit,
) {
    val p = rainPalette
    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            Modifier.fillMaxWidth().heightIn(max = 460.dp),
            shape = RoundedCornerShape(20.dp),
            level = 2,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选集", color = RainPlayerText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    subtitle?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = RainPlayerTextDim, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    gridItems(episodes, key = { it.id }) { ep ->
                        val selected = ep.id == currentId
                        Box(
                            Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) RainPlayerAccent
                                    else if (p.isLight) Color(0xFFF0F1F5) else p.surfaceHigh
                                )
                                .clickable { onPick(ep) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${ep.index}",
                                color = if (selected) Color.Black else RainPlayerText,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = RainPlayerText,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 20.dp, end = 20.dp),
    )
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(0x73000000))
            .border(1.dp, Color(0x26FFFFFF), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ActionIcon(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) RainPlayerAccent else RainPlayerTextDim,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (active) RainPlayerAccent else RainPlayerTextDim, fontSize = 9.sp)
    }
}

@Composable
private fun MediaInfoCard(
    title: String,
    icon: ImageVector,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = RainPlayerTextDim, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = RainPlayerText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            if (rows.isEmpty()) {
                Text("未提供", color = RainPlayerTextDim, fontSize = 10.sp)
            } else {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            k,
                            color = RainPlayerTextDim,
                            fontSize = 10.sp,
                            modifier = Modifier.width(52.dp),
                        )
                        Text(
                            v,
                            color = RainPlayerText,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingBadge(source: String, score: Double, votes: Int?, percent: Boolean = false) {
    val p = rainPalette
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(p.surface.copy(alpha = 0.6f))
            .border(1.dp, RainPlayerGold.copy(alpha = 0.3f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(source, color = RainPlayerTextDim, fontSize = 9.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            if (percent) "${score.toInt()}%" else "%.1f".format(score),
            color = RainPlayerGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        votes?.takeIf { it > 0 }?.let {
            Spacer(Modifier.width(5.dp))
            Text(
                if (it >= 10_000) "${it / 10_000}万" else "$it",
                color = RainPlayerTextDim,
                fontSize = 8.sp,
            )
        }
    }
}

@Composable
private fun DetailOptionDialog(
    dialog: DetailDialog?,
    options: PlaybackOptions?,
    versionId: String?,
    audioIndex: Int?,
    subtitleIndex: Int?,
    onDismiss: () -> Unit,
    onVersion: (String) -> Unit,
    onAudio: (Int?) -> Unit,
    onSubtitle: (Int?) -> Unit,
) {
    if (dialog == null || options == null) return
    val title = when (dialog) {
        DetailDialog.VERSION -> "选择版本"
        DetailDialog.AUDIO -> "选择音轨"
        DetailDialog.SUBTITLE -> "选择字幕"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = RainPlayerText, fontSize = 16.sp) },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                when (dialog) {
                    DetailDialog.VERSION -> {
                        if (options.versions.isEmpty()) {
                            item { DialogRow("服务器没有提供其它版本", null, false) {} }
                        }
                        items(options.versions, key = { it.id }) { v ->
                            DialogRow(
                                label = v.name,
                                detail = v.summary.takeIf { it.isNotBlank() },
                                selected = v.id == (versionId ?: options.versions.firstOrNull()?.id),
                            ) { onVersion(v.id) }
                        }
                    }

                    DetailDialog.AUDIO -> items(options.audioTracks, key = { it.index }) { t ->
                        DialogRow(
                            label = t.label,
                            detail = listOfNotNull(
                                t.codec?.uppercase(),
                                t.channels?.let { "${it}ch" },
                            ).joinToString(" · ").ifBlank { null },
                            selected = t.index == audioIndex,
                        ) { onAudio(t.index) }
                    }

                    DetailDialog.SUBTITLE -> {
                        item {
                            DialogRow(
                                label = "关闭字幕",
                                detail = null,
                                selected = subtitleIndex == null,
                            ) { onSubtitle(null) }
                        }
                        items(options.subtitleTracks, key = { it.index }) { t ->
                            DialogRow(
                                label = t.label,
                                detail = buildString {
                                    append(t.codecLabel)
                                    if (t.imageBased) append(" · 需服务器烧录")
                                    if (t.forced) append(" · 强制")
                                },
                                selected = t.index == subtitleIndex,
                            ) { onSubtitle(t.index) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        containerColor = RainPlayerSurface,
    )
}

@Composable
private fun DialogRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) RainPlayerAccent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            (if (selected) "✓ " else "") + label,
            color = if (selected) RainPlayerAccent else RainPlayerText,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        detail?.let {
            Text(it, color = RainPlayerTextDim, fontSize = 11.sp, maxLines = 1)
        }
    }
}

private enum class DetailDialog { VERSION, AUDIO, SUBTITLE }

@Composable
private fun EpisodeRow(ep: Episode, seriesTitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(112.dp)
                .height(63.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(RainPlayerSurface),
        ) {
            AsyncImage(
                model = imageRequest(ep.imageUrl),
                contentDescription = ep.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (ep.progress > 0.01f) {
                Box(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(Color(0x66000000))
                ) {
                    Box(
                        Modifier.fillMaxWidth(ep.progress.coerceIn(0f, 1f)).height(3.dp)
                            .background(RainPlayerAccent)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (ep.index > 0) "${ep.index} · ${ep.title}" else ep.title,
                color = RainPlayerText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ep.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = RainPlayerTextDim,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ep.runtimeMinutes?.let {
                Text("$it 分钟", color = RainPlayerTextDim, fontSize = 10.sp)
            }
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "播放",
            tint = RainPlayerTextDim,
            modifier = Modifier.size(20.dp),
        )
    }
}
