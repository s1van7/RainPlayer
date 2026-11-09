package com.rainplayer.ui.phone

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.rainplayer.data.Season
import com.rainplayer.player.PlayerActivity
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.common.ErrorView
import com.rainplayer.ui.common.RainPlayerButton
import com.rainplayer.ui.common.LoadingView
import com.rainplayer.ui.common.MetaChip
import com.rainplayer.ui.common.imageRequest
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerSurface
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    session: AppSession,
    itemId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenItem: (com.rainplayer.data.MediaCard) -> Unit,
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
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                itemId = targetId,
                title = if (d.type == MediaType.EPISODE) d.title else d.title,
                subtitle = subtitle ?: d.originalTitle,
                nextId = next,
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
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
                        Box(Modifier.fillMaxWidth().height(240.dp)) {
                            AsyncImage(
                                model = imageRequest(d.backdropUrl ?: d.posterUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        listOf(Color(0x66000000), Color(0xCC0B0D12), Color(0xFF0B0D12))
                                    )
                                )
                            )
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
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            d.genres.take(4).forEach { MetaChip(it) }
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
                        EpisodeRow(ep, d.title) {
                            startPlayback(ep.id, "第 ${ep.seasonIndex} 季 · 第 ${ep.index} 集")
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
