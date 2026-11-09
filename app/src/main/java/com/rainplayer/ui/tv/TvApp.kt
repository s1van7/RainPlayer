package com.rainplayer.ui.tv

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rainplayer.data.Episode
import com.rainplayer.data.PlaybackOptions
import com.rainplayer.data.HomeRow
import com.rainplayer.data.Library
import com.rainplayer.data.MediaCard
import com.rainplayer.data.MediaDetail
import com.rainplayer.data.MediaType
import com.rainplayer.data.Season
import com.rainplayer.player.PlayerActivity
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.LoginScreen
import com.rainplayer.ui.SettingsScreen
import com.rainplayer.ui.common.EmptyView
import com.rainplayer.ui.common.ErrorView
import com.rainplayer.ui.common.Load
import com.rainplayer.ui.common.LoadingView
import com.rainplayer.ui.common.RainPlayerButton
import com.rainplayer.ui.common.MetaChip
import com.rainplayer.ui.common.PosterCard
import com.rainplayer.ui.common.SectionRow
import com.rainplayer.ui.common.imageRequest
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerBg
import com.rainplayer.ui.theme.RainPlayerSurface
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim

@Composable
fun TvRoot(session: AppSession) {
    var loggedIn by remember { mutableStateOf(session.backend != null) }
    if (!loggedIn || session.backend == null) {
        LoginScreen(session) { loggedIn = true }
    } else {
        TvShell(session) { loggedIn = false }
    }
}

private enum class TvTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Filled.Home),
    LIBRARY("媒体库", Icons.Filled.VideoLibrary),
    SEARCH("搜索", Icons.Filled.Search),
    SETTINGS("设置", Icons.Filled.Settings),
}

@Composable
private fun TvShell(session: AppSession, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(TvTab.HOME) }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var library by remember { mutableStateOf<Library?>(null) }

    BackHandler(enabled = detail != null || library != null) {
        if (detail != null) detail = null else library = null
    }

    Row(Modifier.fillMaxSize().background(RainPlayerBg)) {
        NavRail(tab, session.backend?.config?.name ?: "") {
            tab = it
            library = null
            detail = null
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val d = detail
            val lib = library
            when {
                d != null -> TvDetailScreen(
                    session = session,
                    itemId = d.first,
                    fallbackTitle = d.second,
                    onBack = { detail = null },
                )

                lib != null -> TvLibraryGrid(session, lib) { detail = it.id to it.title }

                tab == TvTab.HOME -> TvHome(session) { detail = it.id to it.title }

                tab == TvTab.LIBRARY -> TvLibraries(session) { library = it }

                tab == TvTab.SEARCH -> TvSearch(session) { detail = it.id to it.title }

                else -> SettingsScreen(session, onLogout = onLogout, onAddServer = onLogout)
            }
        }
    }
}

@Composable
private fun NavRail(current: TvTab, serverName: String, onSelect: (TvTab) -> Unit) {
    val first = remember { FocusRequester() }
    Column(
        Modifier
            .width(190.dp)
            .fillMaxHeight()
            .background(Color(0xFF0E1118))
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("RainPlayer", color = RainPlayerText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        TvTab.entries.forEachIndexed { index, item ->
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .background(
                        when {
                            focused -> RainPlayerAccent
                            current == item -> RainPlayerSurface
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(10.dp),
                    )
                    .border(
                        if (focused) 2.dp else 0.dp,
                        if (focused) Color.White else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onSelect(item) }
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (focused) Color.Black else RainPlayerTextDim,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    color = if (focused) Color.Black else RainPlayerText,
                    fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            serverName,
            color = RainPlayerTextDim,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun TvHome(session: AppSession, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var state by remember { mutableStateOf<Load<List<HomeRow>>>(Load.Loading) }

    LaunchedEffect(backend) {
        if (backend == null) return@LaunchedEffect
        state = Load.Loading
        state = runCatching { backend.homeRows() }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "加载失败") })
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, top = 26.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "为你推荐",
                color = RainPlayerText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                backend?.config?.name ?: "",
                color = RainPlayerTextDim,
                fontSize = 12.sp,
            )
        }
        when (val s = state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message)
            is Load.Ok -> {
                if (s.data.isEmpty()) EmptyView("服务器上没有找到内容")
                else LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
                    items(s.data) { row ->
                        SectionRow(
                            title = row.title,
                            items = row.items,
                            cardWidth = if (row.kind == com.rainplayer.data.RowKind.CONTINUE) 300.dp else 156.dp,
                            wide = row.kind == com.rainplayer.data.RowKind.CONTINUE,
                            onClick = onOpen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraries(session: AppSession, onOpen: (Library) -> Unit) {
    val backend = session.backend
    var state by remember { mutableStateOf<Load<List<Library>>>(Load.Loading) }

    LaunchedEffect(backend) {
        if (backend == null) return@LaunchedEffect
        state = runCatching { backend.libraries() }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "加载失败") })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Text(
            "媒体库",
            color = RainPlayerText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 26.dp, bottom = 18.dp),
        )
        when (val s = state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message)
            is Load.Ok -> LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(s.data) { library ->
                    TvFocusCard(onClick = { onOpen(library) }) {
                        Column(Modifier.padding(22.dp)) {
                            Text(library.name, color = RainPlayerText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                library.collectionType ?: "混合内容",
                                color = RainPlayerTextDim,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraryGrid(session: AppSession, library: Library, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var items by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var startIndex by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(Int.MAX_VALUE) }

    suspend fun load(reset: Boolean) {
        val b = backend ?: return
        loading = true
        runCatching { b.items(library.id, if (reset) 0 else startIndex, 60) }
            .onSuccess { page ->
                items = if (reset) page.items else items + page.items
                startIndex = if (reset) page.items.size else startIndex + page.items.size
                total = if (page.items.isEmpty()) items.size else page.total
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(library.id) { load(true) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(
            library.name,
            color = RainPlayerText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 14.dp),
        )
        when {
            loading && items.isEmpty() -> LoadingView()
            error != null && items.isEmpty() -> ErrorView(error!!)
            items.isEmpty() -> EmptyView("这个媒体库还没有内容")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.id }) { card ->
                    PosterCard(card, 150.dp) { onOpen(card) }
                }
            }
        }
    }

    if (items.size < total && !loading) {
        LaunchedEffect(items.size) { load(false) }
    }
}

@Composable
private fun TvSearch(session: AppSession, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(loading) {
        val b = backend ?: return@LaunchedEffect
        if (!loading || query.isBlank()) return@LaunchedEffect
        runCatching { b.search(query.trim()) }.onSuccess { results = it }
        loading = false
        searched = true
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Text(
            "搜索",
            color = RainPlayerText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 26.dp, bottom = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("输入片名后按确定键搜索", color = RainPlayerTextDim, fontSize = 14.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { loading = true }),
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Spacer(Modifier.height(16.dp))
        when {
            loading -> LoadingView()
            results.isEmpty() -> EmptyView(if (searched) "没有找到相关结果" else "输入关键词开始搜索")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(results, key = { it.id }) { card ->
                    PosterCard(card, 150.dp) { onOpen(card) }
                }
            }
        }
    }
}

@Composable
private fun TvDetailScreen(
    session: AppSession,
    itemId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
) {
    val backend = session.backend
    val context = LocalContext.current
    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSeason by remember { mutableStateOf<Season?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    val playFocus = remember { FocusRequester() }

    var options by remember { mutableStateOf<PlaybackOptions?>(null) }
    var versionId by remember { mutableStateOf<String?>(null) }
    var audioIndex by remember { mutableStateOf<Int?>(null) }
    var subtitleIndex by remember { mutableStateOf<Int?>(null) }
    var dialog by remember { mutableStateOf(TvDetailDialog.NONE) }

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

    LaunchedEffect(itemId, backend) {
        val b = backend ?: return@LaunchedEffect
        loading = true
        runCatching { b.detail(itemId) }
            .onSuccess {
                detail = it
                episodes = it.episodes
                selectedSeason = it.seasons.firstOrNull()
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

    fun play(targetId: String, subtitle: String?) {
        val d = detail ?: return
        val index = episodes.indexOfFirst { it.id == targetId }
        val next = episodes.getOrNull(index + 1)?.id
        if (episodes.isNotEmpty()) {
            com.rainplayer.player.PlayQueue.set(episodes.map { it.id }, targetId)
        } else {
            com.rainplayer.player.PlayQueue.clear()
        }
        val chosenSubtitle = options?.subtitleTracks?.firstOrNull { it.index == subtitleIndex }
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                itemId = targetId,
                title = d.title.ifBlank { fallbackTitle },
                subtitle = subtitle,
                nextId = next,
                sourceId = versionId,
                audioIndex = audioIndex,
                subtitleIndex = subtitleIndex,
                burnSubtitle = chosenSubtitle?.imageBased == true,
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        TvOptionDialog(
            dialog = dialog,
            options = options,
            versionId = versionId,
            audioIndex = audioIndex,
            subtitleIndex = subtitleIndex,
            onDismiss = { dialog = TvDetailDialog.NONE },
            onVersion = {
                versionId = it
                audioIndex = null
                subtitleIndex = null
                dialog = TvDetailDialog.NONE
            },
            onAudio = {
                audioIndex = it
                dialog = TvDetailDialog.NONE
            },
            onSubtitle = {
                subtitleIndex = it
                dialog = TvDetailDialog.NONE
            },
        )
        when {
            loading -> LoadingView()
            error != null -> ErrorView(error!!, onRetry = onBack)
            detail == null -> ErrorView("未找到该内容", onRetry = onBack)
            else -> {
                val d = detail!!
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = imageRequest(d.backdropUrl ?: d.posterUrl),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF00B0D12), Color(0xCC0B0D12), Color(0x880B0D12))
                            )
                        )
                    )
                }
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(520.dp)
                            .fillMaxHeight()
                            .padding(start = 40.dp, top = 40.dp, bottom = 24.dp),
                    ) {
                        Text(
                            d.title.ifBlank { fallbackTitle },
                            color = RainPlayerText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        d.originalTitle?.takeIf { it != d.title }?.let {
                            Text(it, color = RainPlayerTextDim, fontSize = 13.sp, maxLines = 1)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            d.year?.let { MetaChip("$it") }
                            d.rating?.let { MetaChip("%.1f".format(it)) }
                            d.runtimeMinutes?.let { MetaChip("${it}分钟") }
                            d.officialRating?.let { MetaChip(it) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            d.genres.take(3).forEach { MetaChip(it) }
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.focusRequester(playFocus)) {
                                RainPlayerButton(if (d.progress > 0.01f) "继续播放" else "播放") {
                                    val target = when {
                                        d.type == MediaType.SERIES -> {
                                            val ep = episodes.firstOrNull { !it.played } ?: episodes.firstOrNull()
                                            ep?.id ?: d.playTargetId
                                        }
                                        else -> d.playTargetId
                                    }
                                    val label = episodes.firstOrNull { it.id == target }
                                        ?.let { "第 ${it.seasonIndex} 季 · 第 ${it.index} 集" }
                                    play(target, label)
                                }
                            }
                            RainPlayerButton("返回", primary = false) { onBack() }
                        }
                        val o = options
                        if (o != null && (o.hasVersions || o.hasAudioChoice || o.hasSubtitleChoice)) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (o.hasVersions) {
                                    TvSelectChip(
                                        "版本",
                                        o.versions.firstOrNull { it.id == versionId }?.name
                                            ?: o.versions.firstOrNull()?.name ?: "默认",
                                    ) { dialog = TvDetailDialog.VERSION }
                                }
                                if (o.hasAudioChoice) {
                                    TvSelectChip(
                                        "音轨",
                                        o.audioTracks.firstOrNull { it.index == audioIndex }?.label
                                            ?: "默认",
                                    ) { dialog = TvDetailDialog.AUDIO }
                                }
                                if (o.hasSubtitleChoice) {
                                    TvSelectChip(
                                        "字幕",
                                        o.subtitleTracks.firstOrNull { it.index == subtitleIndex }?.codecLabel
                                            ?: "关闭",
                                    ) { dialog = TvDetailDialog.SUBTITLE }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            d.overview ?: "暂无简介",
                            color = RainPlayerTextDim,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Column(Modifier.weight(1f).fillMaxHeight().padding(end = 32.dp, top = 44.dp)) {
                        if (d.seasons.size > 1) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(d.seasons) { season ->
                                    val selected = season.id == selectedSeason?.id
                                    Box(
                                        Modifier
                                            .background(
                                                if (selected) RainPlayerAccent else RainPlayerSurface,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedSeason = season }
                                            .focusable()
                                            .padding(horizontal = 16.dp, vertical = 9.dp)
                                    ) {
                                        Text(
                                            season.name,
                                            color = if (selected) Color.Black else RainPlayerText,
                                            fontSize = 13.sp,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(episodes, key = { it.id }) { ep ->
                                TvEpisodeRow(ep) {
                                    play(ep.id, "第 ${ep.seasonIndex} 季 · 第 ${ep.index} 集")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(detail != null) {
        if (detail != null) runCatching { playFocus.requestFocus() }
    }
}

@Composable
private fun TvSelectChip(prefix: String, label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .background(
                if (focused) RainPlayerAccent else RainPlayerSurface,
                RoundedCornerShape(8.dp)
            )
            .border(
                2.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            prefix,
            color = if (focused) Color.Black else RainPlayerTextDim,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (focused) Color.Black else RainPlayerText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
    }
}

@Composable
private fun TvOptionDialog(
    dialog: TvDetailDialog,
    options: PlaybackOptions?,
    versionId: String?,
    audioIndex: Int?,
    subtitleIndex: Int?,
    onDismiss: () -> Unit,
    onVersion: (String) -> Unit,
    onAudio: (Int?) -> Unit,
    onSubtitle: (Int?) -> Unit,
) {
    if (dialog == TvDetailDialog.NONE || options == null) return
    val title = when (dialog) {
        TvDetailDialog.VERSION -> "选择版本"
        TvDetailDialog.AUDIO -> "选择音轨"
        TvDetailDialog.SUBTITLE -> "选择字幕"
        TvDetailDialog.NONE -> ""
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = RainPlayerText, fontSize = 17.sp) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                when (dialog) {
                    TvDetailDialog.VERSION -> items(options.versions, key = { it.id }) { v ->
                        TvDialogRow(
                            label = v.name,
                            detail = v.summary.takeIf { it.isNotBlank() },
                            selected = v.id == (versionId ?: options.versions.firstOrNull()?.id),
                        ) { onVersion(v.id) }
                    }

                    TvDetailDialog.AUDIO -> items(options.audioTracks, key = { it.index }) { t ->
                        TvDialogRow(
                            label = t.label,
                            detail = listOfNotNull(
                                t.codec?.uppercase(),
                                t.channels?.let { "${it}ch" },
                            ).joinToString(" · ").ifBlank { null },
                            selected = t.index == audioIndex,
                        ) { onAudio(t.index) }
                    }

                    TvDetailDialog.SUBTITLE -> {
                        item {
                            TvDialogRow(
                                label = "关闭字幕",
                                detail = null,
                                selected = subtitleIndex == null,
                            ) { onSubtitle(null) }
                        }
                        items(options.subtitleTracks, key = { it.index }) { t ->
                            TvDialogRow(
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

                    TvDetailDialog.NONE -> Unit
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
private fun TvDialogRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                when {
                    focused -> RainPlayerAccent
                    selected -> Color(0x332A3550)
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            (if (selected) "✓ " else "") + label,
            color = if (focused) Color.Black else if (selected) RainPlayerAccent else RainPlayerText,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        detail?.let {
            Text(
                it,
                color = if (focused) Color(0xCC000000) else RainPlayerTextDim,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

private enum class TvDetailDialog { NONE, VERSION, AUDIO, SUBTITLE }

@Composable
private fun TvEpisodeRow(ep: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    focused -> RainPlayerAccent
                    ep.progress > 0.01f -> Color(0x332A3550)
                    else -> Color(0x14FFFFFF)
                },
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${ep.index}",
            color = if (focused) Color.Black else RainPlayerTextDim,
            fontSize = 13.sp,
            modifier = Modifier.width(30.dp),
        )
        Text(
            ep.title,
            color = if (focused) Color.Black else RainPlayerText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ep.runtimeMinutes?.let {
            Text(
                "$it 分钟",
                color = if (focused) Color(0xCC000000) else RainPlayerTextDim,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun TvFocusCard(onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(
                if (focused) RainPlayerAccent else RainPlayerSurface,
                RoundedCornerShape(12.dp),
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused },
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}
