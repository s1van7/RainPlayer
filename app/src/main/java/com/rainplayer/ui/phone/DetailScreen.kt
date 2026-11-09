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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rainplayer.data.Episode
import com.rainplayer.data.MediaDetail
import com.rainplayer.data.MediaType
import com.rainplayer.data.PlaybackOptions
import com.rainplayer.data.Season
import com.rainplayer.player.PlayerActivity
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.common.ErrorView
import com.rainplayer.ui.common.RainPlayerButton
import com.rainplayer.ui.common.LoadingView
import com.rainplayer.ui.common.MetaChip
import com.rainplayer.ui.common.imageRequest
import com.rainplayer.ui.theme.GlassSurface
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerSurface
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import com.rainplayer.ui.theme.scrimBrush
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    session: AppSession,
    itemId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenItem: (com.rainplayer.data.MediaCard) -> Unit,
    onOpenEpisode: (Episode) -> Unit = {},
) {
    val backend = session.backend
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableStateOf<Season?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var favorite by remember { mutableStateOf(false) }
    var played by remember { mutableStateOf(false) }
    var similar by remember { mutableStateOf<List<com.rainplayer.data.MediaCard>>(emptyList()) }
    var ratings by remember { mutableStateOf(com.rainplayer.data.ExternalRatings()) }

    // 版本 / 音轨 / 字幕 选择
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

    // 更多类似作品：服务器不支持就静默留空
    LaunchedEffect(itemId, backend) {
        val b = backend ?: return@LaunchedEffect
        similar = runCatching { b.similar(itemId, 14) }.getOrDefault(emptyList())
    }

    // TMDB / 豆瓣评分：两个源都失败就整块隐藏，绝不阻塞页面
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

    // 拉取版本/音轨/字幕清单；换版本后重新拉一次该版本的轨道
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

    fun startPlayback(targetId: String, subtitle: String?) {
        val d = detail ?: return
        val list = episodes
        val index = list.indexOfFirst { it.id == targetId }
        val next = if (index >= 0) list.getOrNull(index + 1)?.id else null
        if (list.isNotEmpty()) {
            com.rainplayer.player.PlayQueue.set(list.map { it.id }, targetId)
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
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        // 版本 / 音轨 / 字幕 选择弹窗（Dialog 是独立窗口，放在哪一层都可以）
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
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Box(Modifier.fillMaxWidth().height(260.dp)) {
                            AsyncImage(
                                model = imageRequest(d.backdropUrl ?: d.posterUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(Modifier.fillMaxSize().background(scrimBrush(1f)))
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                            ) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                            }
                            Row(
                                Modifier.align(Alignment.BottomStart).padding(16.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                AsyncImage(
                                    model = imageRequest(d.posterUrl),
                                    contentDescription = d.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(96.dp)
                                        .height(144.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RainPlayerSurface),
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        d.title,
                                        color = RainPlayerText,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    d.originalTitle?.takeIf { it != d.title }?.let {
                                        Text(
                                            it,
                                            color = RainPlayerTextDim,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        d.year?.let { MetaChip("$it") }
                                        d.rating?.let { MetaChip("%.1f".format(it)) }
                                        d.runtimeMinutes?.let { MetaChip("${it}分钟") }
                                        d.officialRating?.let { MetaChip(it) }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RainPlayerButton(
                                if (d.progress > 0.01f || episodes.any { it.progress > 0.01f }) "继续播放" else "播放",
                            ) {
                                val target = when {
                                    d.type == MediaType.SERIES -> {
                                        val ep = episodes.firstOrNull { !it.played } ?: episodes.firstOrNull()
                                        ep?.id ?: d.playTargetId
                                    }
                                    else -> d.playTargetId
                                }
                                val label = episodes.firstOrNull { it.id == target }?.let {
                                    "第 ${it.seasonIndex} 季 · 第 ${it.index} 集"
                                }
                                startPlayback(target, label)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RainPlayerSurface)
                                    .clickable {
                                        favorite = !favorite
                                        scope.launch { backend?.setFavorite(itemId, favorite) }
                                    }
                                    .padding(10.dp)
                            ) {
                                Icon(
                                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "收藏",
                                    tint = if (favorite) RainPlayerAccent else RainPlayerTextDim,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RainPlayerSurface)
                                    .clickable {
                                        played = !played
                                        scope.launch { backend?.setWatched(itemId, played) }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    if (played) "已看" else "标记已看",
                                    color = if (played) RainPlayerAccent else RainPlayerTextDim,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    item {
                        val o = options
                        if (o != null && (o.hasVersions || o.hasAudioChoice || o.hasSubtitleChoice)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (o.hasVersions) {
                                    SelectChip(
                                        label = o.versions.firstOrNull { it.id == versionId }?.name
                                            ?: o.versions.firstOrNull()?.name ?: "版本",
                                        prefix = "版本",
                                    ) { dialog = DetailDialog.VERSION }
                                }
                                if (o.hasAudioChoice) {
                                    SelectChip(
                                        label = o.audioTracks.firstOrNull { it.index == audioIndex }?.label
                                            ?: "默认",
                                        prefix = "音轨",
                                    ) { dialog = DetailDialog.AUDIO }
                                }
                                if (o.hasSubtitleChoice) {
                                    SelectChip(
                                        label = o.subtitleTracks.firstOrNull { it.index == subtitleIndex }?.codecLabel
                                            ?: "关闭",
                                        prefix = "字幕",
                                    ) { dialog = DetailDialog.SUBTITLE }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            d.genres.take(4).forEach { MetaChip(it) }
                        }
                    }

                    // 评分：TMDB / 豆瓣。两个源都拿不到就整块不渲染。
                    if (!ratings.isEmpty) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ratings.tmdb?.let { RatingBadge("TMDB", it, ratings.tmdbVotes) }
                                ratings.douban?.let { RatingBadge("豆瓣", it, ratings.doubanVotes) }
                            }
                        }
                    }

                    // 媒体信息
                    item {
                        val rows = buildList {
                            d.premiereDate?.let { add("上映日期" to it) }
                            d.studio?.takeIf { it.isNotBlank() }?.let { add("出品" to it) }
                            d.runtimeMinutes?.let { add("片长" to "$it 分钟") }
                            d.officialRating?.let { add("分级" to it) }
                            d.genres.takeIf { it.isNotEmpty() }?.let { add("类型" to it.joinToString(" / ")) }
                            options?.versions?.takeIf { it.isNotEmpty() }?.let { vs ->
                                add("版本" to vs.joinToString("、") { it.name })
                            }
                        }
                        if (rows.isNotEmpty()) {
                            GlassSurface(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "媒体信息",
                                        color = RainPlayerText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    rows.forEach { (k, v) ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                            Text(
                                                k,
                                                color = RainPlayerTextDim,
                                                fontSize = 11.sp,
                                                modifier = Modifier.width(72.dp),
                                            )
                                            Text(v, color = RainPlayerText, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            d.overview ?: "暂无简介",
                            color = RainPlayerTextDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                    if (d.seasons.size > 1) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(d.seasons) { season ->
                                    val selected = season.id == selectedSeason?.id
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) RainPlayerAccent else RainPlayerSurface)
                                            .clickable { selectedSeason = season }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            season.name,
                                            color = if (selected) Color.Black else RainPlayerText,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    items(episodes, key = { it.id }) { ep ->
                        EpisodeRow(ep, d.title) { onOpenEpisode(ep) }
                    }

                    // 更多类似作品
                    if (similar.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 8.dp)) {
                                Text(
                                    "更多类似作品",
                                    color = RainPlayerText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(16.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(similar, key = { it.id }) { card ->
                                        com.rainplayer.ui.common.PosterCard(card, 108.dp) { onOpenItem(card) }
                                    }
                                }
                            }
                        }
                    }

                    if (d.people.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    "演职员",
                                    color = RainPlayerText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(16.dp),
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(d.people) { person ->
                                        Column(Modifier.width(72.dp)) {
                                            AsyncImage(
                                                model = imageRequest(person.imageUrl),
                                                contentDescription = person.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(RoundedCornerShape(36.dp))
                                                    .background(RainPlayerSurface),
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                person.name,
                                                color = RainPlayerText,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            person.role?.let {
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
                }
            }
        }
    }
}

@Composable
private fun RatingBadge(source: String, score: Double, votes: Int?) {
    val p = com.rainplayer.ui.theme.rainPalette
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(p.surface.copy(alpha = 0.6f))
            .border(1.dp, com.rainplayer.ui.theme.RainPlayerGold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(source, color = RainPlayerTextDim, fontSize = 10.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "%.1f".format(score),
            color = com.rainplayer.ui.theme.RainPlayerGold,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        votes?.takeIf { it > 0 }?.let {
            Spacer(Modifier.width(6.dp))
            Text(
                if (it >= 10_000) "${it / 10_000}万" else "$it",
                color = RainPlayerTextDim,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun SelectChip(prefix: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RainPlayerSurface)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prefix, color = RainPlayerTextDim, fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = RainPlayerText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
        )
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
                    DetailDialog.VERSION -> items(options.versions, key = { it.id }) { v ->
                        DialogRow(
                            label = v.name,
                            detail = v.summary.takeIf { it.isNotBlank() },
                            selected = v.id == (versionId ?: options.versions.firstOrNull()?.id),
                        ) { onVersion(v.id) }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(112.dp)
                .height(63.dp)
                .clip(RoundedCornerShape(6.dp))
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
                "${ep.index}. ${ep.title}",
                color = RainPlayerText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ep.overview?.let {
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
