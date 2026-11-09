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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch

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
    SERVERS("服务器", Icons.Filled.VideoLibrary),
    SEARCH("搜索", Icons.Filled.Search),
    SETTINGS("设置", Icons.Filled.Settings),
}

@Composable
private fun TvShell(session: AppSession, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(TvTab.HOME) }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    var episode by remember { mutableStateOf<Pair<String, String>?>(null) }
    var library by remember { mutableStateOf<Library?>(null) }
    var screen by remember { mutableStateOf(TvServerScreen.NONE) }

    BackHandler(enabled = detail != null || library != null || episode != null || screen != TvServerScreen.NONE) {
        when {
            detail != null -> detail = null
            episode != null -> episode = null
            library != null -> library = null
            screen != TvServerScreen.NONE -> screen = TvServerScreen.NONE
        }
    }

    Row(Modifier.fillMaxSize().background(RainPlayerBg)) {
        NavRail(tab, session) {
            tab = it
            library = null
            detail = null
            episode = null
            screen = TvServerScreen.NONE
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val d = detail
            val ep = episode
            val lib = library
            when {
                d != null -> TvDetailScreen(
                    session = session,
                    itemId = d.first,
                    fallbackTitle = d.second,
                    onBack = { detail = null },
                    onOpenItem = { detail = it.id to it.title },
                    onOpenEpisode = { episode = it.id to d.second },
                )

                ep != null -> TvEpisodeDetail(
                    session = session,
                    episodeId = ep.first,
                    fallbackTitle = ep.second,
                    onBack = { episode = null },
                    onOpenSeries = { id, title -> episode = null; detail = id to title },
                )

                lib != null -> TvLibraryGrid(session, lib) { detail = it.id to it.title }

                tab == TvTab.HOME -> TvHome(session) { detail = it.id to it.title }

                tab == TvTab.SERVERS -> when (screen) {
                    TvServerScreen.MANAGE -> com.rainplayer.ui.phone.ServerManageScreen(
                        session = session,
                        onBack = { screen = TvServerScreen.NONE },
                        onAddServer = { screen = TvServerScreen.ADD },
                    )

                    TvServerScreen.ADD -> com.rainplayer.ui.phone.AddServerScreen(
                        session = session,
                        onBack = { screen = TvServerScreen.NONE },
                        onDone = { screen = TvServerScreen.NONE },
                    )

                    TvServerScreen.LIBRARIES -> TvLibraries(session) { library = it }

                    TvServerScreen.NONE -> TvServerPanel(
                        session = session,
                        onOpenLibraries = { screen = TvServerScreen.LIBRARIES },
                        onManage = { screen = TvServerScreen.MANAGE },
                        onAddServer = { screen = TvServerScreen.ADD },
                    )
                }

                tab == TvTab.SEARCH -> TvSearch(session) { card ->
                    card.serverId?.let { sid ->
                        if (sid != session.activeId) {
                            session.servers.firstOrNull { it.id == sid }?.let { session.activate(it) }
                        }
                    }
                    detail = card.id to card.title
                }

                else -> SettingsScreen(session, onLogout = onLogout, onAddServer = onLogout)
            }
        }
    }
}

private enum class TvServerScreen { NONE, LIBRARIES, MANAGE, ADD }

/**
 * 服务器面板：原来底部导航的「媒体库」被收进这里，
 * 和切换服务器、服务器设置放在一起，符合「点服务器名进去」的心智。
 */
@Composable
private fun TvServerPanel(
    session: AppSession,
    onOpenLibraries: () -> Unit,
    onManage: () -> Unit,
    onAddServer: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Text(
            "服务器",
            color = RainPlayerText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 28.dp, bottom = 18.dp),
        )

        session.servers.forEachIndexed { index, cfg ->
            TvPanelRow(
                title = cfg.name,
                subtitle = cfg.summary,
                selected = cfg.id == session.activeId,
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            ) {
                if (cfg.id != session.activeId) session.activate(cfg)
            }
        }

        Spacer(Modifier.height(18.dp))
        TvPanelRow("媒体库", "开关与排序首页显示的媒体库", false) { onOpenLibraries() }
        TvPanelRow("服务器设置", "线路、账号、图标、名称", false) { onManage() }
        TvPanelRow("添加服务器", "接入新的 Emby / Jellyfin / 飞牛影视", false) { onAddServer() }
    }
}

@Composable
private fun TvPanelRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                when {
                    focused -> RainPlayerAccent
                    selected -> RainPlayerSurface
                    else -> Color(0x0AFFFFFF)
                },
                RoundedCornerShape(12.dp),
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (focused) Color.Black else RainPlayerText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                color = if (focused) Color(0xAA000000) else RainPlayerTextDim,
                fontSize = 11.sp,
            )
        }
        if (selected) {
            Text("当前", color = if (focused) Color.Black else RainPlayerAccent, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavRail(current: TvTab, session: AppSession, onSelect: (TvTab) -> Unit) {
    val first = remember { FocusRequester() }
    val active = session.active
    Column(
        Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(com.rainplayer.ui.theme.rainPalette.bg.copy(alpha = 0.72f))
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 左上角：服务器头像 + 名称，点进去就是服务器 / 媒体库入口
        var headerFocused by remember { mutableStateOf(false) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(
                    if (headerFocused) RainPlayerAccent else Color.Transparent,
                    RoundedCornerShape(12.dp),
                )
                .border(
                    if (headerFocused) 2.dp else 0.dp,
                    if (headerFocused) Color.White else Color.Transparent,
                    RoundedCornerShape(12.dp),
                )
                .clickable { onSelect(TvTab.SERVERS) }
                .focusable()
                .onFocusChanged { headerFocused = it.isFocused }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active != null) {
                com.rainplayer.ui.common.ServerAvatar(active, 30.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    active?.name ?: "RainPlayer",
                    color = if (headerFocused) Color.Black else RainPlayerText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    active?.activeLine?.name ?: "",
                    color = if (headerFocused) Color(0xAA000000) else RainPlayerTextDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
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
    }
}

@Composable
private fun TvHome(session: AppSession, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var state by remember { mutableStateOf<Load<List<HomeRow>>>(Load.Loading) }

    LaunchedEffect(backend, session.activeId, session.active?.activeLineId, session.active?.activeUserId) {
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
                session.active?.name ?: "",
                color = RainPlayerTextDim,
                fontSize = 12.sp,
            )
        }
        when (val s = state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message)
            is Load.Ok -> {
                val rows = arrangeTvRows(session, s.data)
                val daily = remember(rows) { tvDailyPicks(rows) }
                if (rows.isEmpty()) EmptyView("服务器上没有找到内容")
                else LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) {
                    if (com.rainplayer.Graph.prefs.dailyRecommend && daily.isNotEmpty()) {
                        item(key = "tv-daily") { TvDailyCarousel(daily, onOpen) }
                    }
                    items(rows) { row ->
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

private fun arrangeTvRows(session: AppSession, rows: List<HomeRow>): List<HomeRow> {
    val cfg = session.active ?: return rows
    val visible = rows.filterNot { it.libraryId != null && it.libraryId in cfg.hiddenLibraries }
    if (cfg.libraryOrder.isEmpty()) return visible
    val rank = cfg.libraryOrder.withIndex().associate { (i, id) -> id to i }
    val head = visible.filter { it.libraryId == null }
    val tail = visible.filter { it.libraryId != null }.sortedBy { rank[it.libraryId] ?: Int.MAX_VALUE }
    return head + tail
}

private fun tvDailyPicks(rows: List<HomeRow>): List<MediaCard> {
    val pool = LinkedHashMap<String, MediaCard>()
    rows.filter { it.kind != com.rainplayer.data.RowKind.CONTINUE }
        .flatMap { it.items }
        .filter { !it.backdropUrl.isNullOrBlank() }
        .forEach { pool.putIfAbsent(it.id, it) }
    val list = pool.values.sortedByDescending { it.rating ?: 0.0 }.take(10).toList()
    if (list.isEmpty()) return emptyList()
    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    val start = day % list.size
    return (list.drop(start) + list.take(start)).take(6)
}

/** TV 版每日推荐：整行大图轮播，遥控器左右键翻页。 */
@Composable
private fun TvDailyCarousel(items: List<MediaCard>, onOpen: (MediaCard) -> Unit) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(items.size) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(8000)
            val next = (pagerState.currentPage + 1) % items.size
            runCatching { pagerState.animateScrollToPage(next) }
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("今日推荐", color = RainPlayerText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text("每天换一批", color = RainPlayerTextDim, fontSize = 11.sp)
        }
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val card = items[page]
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(if (focused) RainPlayerAccent.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(18.dp))
                    .border(
                        if (focused) 3.dp else 0.dp,
                        if (focused) Color.White else Color.Transparent,
                        RoundedCornerShape(18.dp),
                    )
                    .clickable { onOpen(card) }
                    .focusable()
                    .onFocusChanged { focused = it.isFocused }
                    .padding(3.dp)
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                    AsyncImage(
                        model = imageRequest(card.backdropUrl ?: card.posterUrl),
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(Modifier.fillMaxSize().background(com.rainplayer.ui.theme.scrimBrush(1f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(26.dp)) {
                        Text(
                            card.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(
                                card.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                                card.year?.toString(),
                                card.subtitle,
                            ).joinToString(" · "),
                            color = Color(0xCCFFFFFF),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(items.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(if (selected) RainPlayerAccent else Color(0x44FFFFFF))
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }
        }
    }
}

@Composable
private fun TvLibraries(session: AppSession, onOpen: (Library) -> Unit) {
    val backend = session.backend
    var state by remember { mutableStateOf<Load<List<Library>>>(Load.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(backend, reloadKey) {
        if (backend == null) return@LaunchedEffect
        state = runCatching { backend.libraries() }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "加载失败") })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp)) {
        Text(
            "媒体库",
            color = RainPlayerText,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
        )
        Text(
            "选中后按方向键中键进入；开关控制首页是否显示，箭头调整顺序",
            color = RainPlayerTextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        when (val s = state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { reloadKey++ }
            is Load.Ok -> {
                val cfg = session.active
                val allIds = s.data.map { it.id }
                LazyColumn {
                    items(s.data) { library ->
                        val hidden = cfg?.hiddenLibraries?.contains(library.id) == true
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    when {
                                        focused -> RainPlayerAccent
                                        hidden -> Color(0x08FFFFFF)
                                        else -> RainPlayerSurface
                                    },
                                    RoundedCornerShape(12.dp),
                                )
                                .border(
                                    if (focused) 2.dp else 0.dp,
                                    if (focused) Color.White else Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onOpen(library) }
                                .focusable()
                                .onFocusChanged { focused = it.isFocused }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    library.name,
                                    color = if (focused) Color.Black else RainPlayerText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    library.collectionType ?: "混合内容",
                                    color = if (focused) Color(0xAA000000) else RainPlayerTextDim,
                                    fontSize = 11.sp,
                                )
                            }
                            TvMiniButton(if (hidden) "显示" else "隐藏") {
                                cfg?.let { session.setLibraryHidden(it.id, library.id, !hidden) }
                            }
                            Spacer(Modifier.width(8.dp))
                            TvMiniButton("上移") {
                                cfg?.let { session.moveLibrary(it.id, allIds, library.id, -1) }
                            }
                            Spacer(Modifier.width(8.dp))
                            TvMiniButton("下移") {
                                cfg?.let { session.moveLibrary(it.id, allIds, library.id, 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMiniButton(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .background(
                if (focused) Color.White else Color(0x22FFFFFF),
                RoundedCornerShape(8.dp),
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) RainPlayerAccent else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable { onClick() }
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (focused) Color.Black else RainPlayerText, fontSize = 11.sp)
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
    var failures by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var trigger by remember { mutableStateOf(0) }

    val aggregate = com.rainplayer.Graph.prefs.aggregateSearch && session.servers.size > 1

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val keyword = query.trim()
        if (keyword.isEmpty()) return@LaunchedEffect
        loading = true
        searched = true
        runCatching {
            if (aggregate) {
                val outcome = com.rainplayer.data.AggregateSearch.search(
                    keyword, session.servers.map { it.normalize() }
                )
                failures = outcome.failures
                outcome.cards
            } else {
                failures = emptyList()
                backend?.search(keyword) ?: emptyList()
            }
        }.onSuccess { results = it }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("搜索", color = RainPlayerText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            if (aggregate) {
                Box(
                    Modifier
                        .background(RainPlayerAccent.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("聚合 ${session.servers.size} 台服务器", color = RainPlayerAccent, fontSize = 11.sp)
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("输入片名后按确定键搜索", color = RainPlayerTextDim, fontSize = 14.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { trigger++ }),
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
                items(results, key = { "${it.serverId}:${it.id}" }) { card ->
                    Column {
                        Box {
                            PosterCard(card, 150.dp) { onOpen(card) }
                            card.serverName?.let { name ->
                                com.rainplayer.ui.common.SourceTag(
                                    name,
                                    Modifier.padding(start = 10.dp, top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (failures.isNotEmpty()) {
            Text(
                failures.joinToString("；") { "${it.first}：${it.second}" },
                color = RainPlayerTextDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun TvDetailScreen(
    session: AppSession,
    itemId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenItem: (MediaCard) -> Unit = {},
    onOpenEpisode: (Episode) -> Unit = {},
) {
    val backend = session.backend
    val context = LocalContext.current
    var detail by remember { mutableStateOf<MediaDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSeason by remember { mutableStateOf<Season?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var similar by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var ratings by remember { mutableStateOf(com.rainplayer.data.ExternalRatings()) }
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

    // 更多类似作品 + 外部评分：都是可选内容，失败就静默留空
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

    fun play(targetId: String, subtitle: String?) {
        val d = detail ?: return
        val index = episodes.indexOfFirst { it.id == targetId }
        val next = episodes.getOrNull(index + 1)?.id
        if (episodes.isNotEmpty()) {
            com.rainplayer.player.PlayQueue.set(
                episodes.map { com.rainplayer.player.QueueEntry(it.id, it.title, it.label) },
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
                    val tvBg = com.rainplayer.ui.theme.rainPalette.bg
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(
                                    tvBg.copy(alpha = 0.94f),
                                    tvBg.copy(alpha = 0.80f),
                                    tvBg.copy(alpha = 0.53f),
                                )
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
                        if (!ratings.isEmpty) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ratings.tmdb?.let { TvRatingBadge("TMDB", it, ratings.tmdbVotes) }
                                ratings.douban?.let { TvRatingBadge("豆瓣", it, ratings.doubanVotes) }
                            }
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
                                TvEpisodeRow(ep) { onOpenEpisode(ep) }
                            }
                            if (similar.isNotEmpty()) {
                                item {
                                    Column(Modifier.padding(top = 20.dp)) {
                                        Text(
                                            "更多类似作品",
                                            color = RainPlayerText,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            items(similar, key = { it.id }) { card ->
                                                PosterCard(card, 130.dp) { onOpenItem(card) }
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

@Composable
private fun TvRatingBadge(source: String, score: Double, votes: Int?) {
    val p = com.rainplayer.ui.theme.rainPalette
    Row(
        Modifier
            .background(p.surface.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
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

/**
 * TV 版单集详情。遥控器布局：大剧照 + 信息 + 播放按钮。
 */
@Composable
private fun TvEpisodeDetail(
    session: AppSession,
    episodeId: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenSeries: (String, String) -> Unit,
) {
    val backend = session.backend
    val context = LocalContext.current
    var episode by remember { mutableStateOf<Episode?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(episodeId, backend) {
        val b = backend ?: return@LaunchedEffect
        loading = true
        runCatching { b.episode(episodeId) }
            .onSuccess { episode = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(episode != null) {
        if (episode != null) runCatching { playFocus.requestFocus() }
    }

    fun start(ep: Episode, fromStart: Boolean) {
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                itemId = ep.id,
                title = ep.title,
                subtitle = ep.label,
                startMs = if (fromStart) -1L else 0L,
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> LoadingView()
            error != null -> ErrorView(error!!, onRetry = onBack)
            episode == null -> ErrorView("找不到这一集", onRetry = onBack)
            else -> {
                val ep = episode!!
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = imageRequest(ep.backdropUrl ?: ep.imageUrl),
                        contentDescription = ep.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF0000000), Color(0xCC000000), Color(0x88000000))
                            )
                        )
                    )
                }
                Column(
                    Modifier
                        .width(560.dp)
                        .fillMaxHeight()
                        .padding(start = 44.dp, top = 46.dp, bottom = 24.dp),
                ) {
                    Text(ep.label, color = RainPlayerAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        ep.title,
                        color = RainPlayerText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetaChip(ep.seriesName ?: fallbackTitle)
                        ep.airDate?.let { MetaChip(it) }
                        if (ep.played) MetaChip("已看完")
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.focusRequester(playFocus)) {
                            RainPlayerButton(if (ep.progress > 0.01f) "继续播放" else "播放") {
                                start(ep, fromStart = false)
                            }
                        }
                        if (ep.progress > 0.01f) {
                            RainPlayerButton("从头播放", primary = false) { start(ep, fromStart = true) }
                        }
                        RainPlayerButton("返回", primary = false) { onBack() }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        ep.overview?.takeIf { it.isNotBlank() } ?: "这一集还没有简介",
                        color = RainPlayerTextDim,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        maxLines = 12,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ep.seriesId?.let { sid ->
                        Spacer(Modifier.height(20.dp))
                        RainPlayerButton("查看整部剧集", primary = false) {
                            onOpenSeries(sid, ep.seriesName ?: fallbackTitle)
                        }
                    }
                }
            }
        }
    }
}
