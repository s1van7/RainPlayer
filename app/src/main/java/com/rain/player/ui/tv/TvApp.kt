package com.rain.player.ui.tv

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.rain.player.data.Episode
import com.rain.player.data.HomeRow
import com.rain.player.data.Library
import com.rain.player.data.MediaCard
import com.rain.player.data.MediaDetail
import com.rain.player.data.MediaType
import com.rain.player.data.Season
import com.rain.player.player.PlayerActivity
import com.rain.player.ui.AppSession
import com.rain.player.ui.LoginScreen
import com.rain.player.ui.SettingsScreen
import com.rain.player.ui.common.EmptyView
import com.rain.player.ui.common.ErrorView
import com.rain.player.ui.common.Load
import com.rain.player.ui.common.LoadingView
import com.rain.player.ui.common.RainButton
import com.rain.player.ui.common.MetaChip
import com.rain.player.ui.common.PosterCard
import com.rain.player.ui.common.SectionRow
import com.rain.player.ui.common.imageRequest
import com.rain.player.ui.theme.RainAccent
import com.rain.player.ui.theme.RainBg
import com.rain.player.ui.theme.RainSurface
import com.rain.player.ui.theme.RainText
import com.rain.player.ui.theme.RainTextDim

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

    Row(Modifier.fillMaxSize().background(RainBg)) {
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
        Text("Rain", color = RainText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        TvTab.entries.forEachIndexed { index, item ->
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .background(
                        when {
                            focused -> RainAccent
                            current == item -> RainSurface
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
                    tint = if (focused) Color.Black else RainTextDim,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item.label,
                    color = if (focused) Color.Black else RainText,
                    fontSize = 15.sp,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            serverName,
            color = RainTextDim,
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
                color = RainText,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                backend?.config?.name ?: "",
                color = RainTextDim,
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
                            cardWidth = if (row.kind == com.rain.player.data.RowKind.CONTINUE) 300.dp else 156.dp,
                            wide = row.kind == com.rain.player.data.RowKind.CONTINUE,
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
            color = RainText,
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
                            Text(library.name, color = RainText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                library.collectionType ?: "混合内容",
                                color = RainTextDim,
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
            color = RainText,
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
            color = RainText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 26.dp, bottom = 16.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("输入片名后按确定键搜索", color = RainTextDim, fontSize = 14.sp) },
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
            com.rain.player.player.PlayQueue.set(episodes.map { it.id }, targetId)
        } else {
            com.rain.player.player.PlayQueue.clear()
        }
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                itemId = targetId,
                title = d.title.ifBlank { fallbackTitle },
                subtitle = subtitle,
                nextId = next,
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
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
                            color = RainText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        d.originalTitle?.takeIf { it != d.title }?.let {
                            Text(it, color = RainTextDim, fontSize = 13.sp, maxLines = 1)
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
                                RainButton(if (d.progress > 0.01f) "继续播放" else "播放") {
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
                            RainButton("返回", primary = false) { onBack() }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            d.overview ?: "暂无简介",
                            color = RainTextDim,
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
                                                if (selected) RainAccent else RainSurface,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedSeason = season }
                                            .focusable()
                                            .padding(horizontal = 16.dp, vertical = 9.dp)
                                    ) {
                                        Text(
                                            season.name,
                                            color = if (selected) Color.Black else RainText,
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
private fun TvEpisodeRow(ep: Episode, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    focused -> RainAccent
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
            color = if (focused) Color.Black else RainTextDim,
            fontSize = 13.sp,
            modifier = Modifier.width(30.dp),
        )
        Text(
            ep.title,
            color = if (focused) Color.Black else RainText,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ep.runtimeMinutes?.let {
            Text(
                "$it 分钟",
                color = if (focused) Color(0xCC000000) else RainTextDim,
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
                if (focused) RainAccent else RainSurface,
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
