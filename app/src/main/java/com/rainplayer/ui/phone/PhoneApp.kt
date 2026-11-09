package com.rainplayer.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rainplayer.Graph
import com.rainplayer.data.AggregateSearch
import com.rainplayer.data.HomeRow
import com.rainplayer.data.Library
import com.rainplayer.data.MediaCard
import com.rainplayer.data.RowKind
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.LoginScreen
import com.rainplayer.ui.SettingsScreen
import com.rainplayer.ui.common.EmptyView
import com.rainplayer.ui.common.ErrorView
import com.rainplayer.ui.common.Load
import com.rainplayer.ui.common.LoadingView
import com.rainplayer.ui.common.PosterCard
import com.rainplayer.ui.common.SectionRow
import com.rainplayer.ui.common.ServerAvatar
import com.rainplayer.ui.common.SourceTag
import com.rainplayer.ui.common.imageRequest
import com.rainplayer.ui.theme.GlassSurface
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerBg
import com.rainplayer.ui.theme.RainPlayerSurface
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import com.rainplayer.ui.theme.rainPalette
import com.rainplayer.ui.theme.scrimBrush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

sealed interface Dest {
    data object Home : Dest

    /** 媒体库列表（含开关与排序），从左上角服务器菜单进入。 */
    data object Libraries : Dest
    data object Search : Dest
    data object Settings : Dest
    data object Servers : Dest
    data object AddServer : Dest
    data class LibraryDest(val library: Library) : Dest
    data class Detail(val id: String, val title: String) : Dest
    data class EpisodeDest(val episodeId: String, val seriesTitle: String) : Dest
}

@Composable
fun PhoneRoot(session: AppSession) {
    var loggedIn by remember { mutableStateOf(session.backend != null) }
    if (!loggedIn || session.backend == null) {
        LoginScreen(session) { loggedIn = true }
    } else {
        PhoneScaffold(session) { loggedIn = false }
    }
}

@Composable
private fun PhoneScaffold(session: AppSession, onLogout: () -> Unit) {
    val stack = remember { mutableStateListOf<Dest>(Dest.Home) }
    val current = stack.lastOrNull() ?: Dest.Home

    fun go(dest: Dest) {
        stack.clear()
        stack.add(dest)
    }

    fun push(dest: Dest) {
        stack.add(dest)
    }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Scaffold(
        containerColor = RainPlayerBg,
        bottomBar = {
            if (stack.size == 1) {
                NavigationBar(containerColor = RainPlayerSurface.copy(alpha = 0.96f)) {
                    val tabs = listOf(
                        Triple(Dest.Home, "首页", Icons.Filled.Home),
                        Triple(Dest.Search, "搜索", Icons.Filled.Search),
                        Triple(Dest.Settings, "设置", Icons.Filled.Settings),
                    )
                    tabs.forEach { (dest, label, icon) ->
                        NavigationBarItem(
                            selected = current == dest,
                            onClick = { go(dest) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = RainPlayerAccent,
                                indicatorColor = RainPlayerAccent,
                                unselectedIconColor = RainPlayerTextDim,
                                unselectedTextColor = RainPlayerTextDim,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (current) {
                Dest.Home -> HomeScreen(
                    session = session,
                    onOpen = { push(Dest.Detail(it.id, it.title)) },
                    onNavigate = { go(it) },
                    onPush = { push(it) },
                    onLogout = {
                        session.logout()
                        onLogout()
                    },
                )

                Dest.Libraries -> LibrariesScreen(
                    session = session,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpen = { push(Dest.LibraryDest(it)) },
                )

                Dest.Search -> SearchScreen(session) { card ->
                    // 聚合搜索结果可能来自别的服务器：先切过去再进详情
                    card.serverId?.let { sid ->
                        if (sid != session.activeId) {
                            session.servers.firstOrNull { it.id == sid }?.let { session.activate(it) }
                        }
                    }
                    push(Dest.Detail(card.id, card.title))
                }

                Dest.Settings -> SettingsScreen(session, onLogout = {
                    session.logout()
                    onLogout()
                })

                Dest.Servers -> ServerManageScreen(
                    session = session,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onAddServer = { push(Dest.AddServer) },
                )

                Dest.AddServer -> AddServerScreen(
                    session = session,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onDone = { stack.removeAt(stack.lastIndex) },
                )

                is Dest.LibraryDest -> LibraryScreen(session, current.library) { card ->
                    push(Dest.Detail(card.id, card.title))
                }

                is Dest.Detail -> DetailScreen(
                    session = session,
                    itemId = current.id,
                    fallbackTitle = current.title,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpenItem = { card -> push(Dest.Detail(card.id, card.title)) },
                    onOpenEpisode = { ep -> push(Dest.EpisodeDest(ep.id, current.title)) },
                )

                is Dest.EpisodeDest -> EpisodeDetailScreen(
                    session = session,
                    episodeId = current.episodeId,
                    fallbackTitle = current.seriesTitle,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpenSeries = { id, title -> push(Dest.Detail(id, title)) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 首页

@Composable
private fun HomeScreen(
    session: AppSession,
    onOpen: (MediaCard) -> Unit,
    onNavigate: (Dest) -> Unit,
    onPush: (Dest) -> Unit,
    onLogout: () -> Unit,
) {
    val backend = session.backend
    val active = session.active
    var state by remember { mutableStateOf<Load<List<HomeRow>>>(Load.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(backend, reloadKey, active?.activeLineId, active?.activeUserId) {
        if (backend == null) return@LaunchedEffect
        state = Load.Loading
        state = runCatching { backend.homeRows() }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "加载失败") })
    }

    val ok = state as? Load.Ok
    val rows = remember(ok, active?.hiddenLibraries, active?.libraryOrder) {
        ok?.let { arrangeRows(active, it.data) } ?: emptyList()
    }
    val daily = remember(rows) { dailyPicks(rows) }
    // 有大图时标题栏浮在图上；否则让出 60dp 给标题栏，内容从它下面开始
    val showHero = ok != null && rows.isNotEmpty() && Graph.prefs.dailyRecommend && daily.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        val snapshot = state
        when (snapshot) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(snapshot.message) { reloadKey++ }
            is Load.Ok -> {
                if (rows.isEmpty()) {
                    EmptyView("服务器上没有找到内容")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = if (showHero) 0.dp else 60.dp,
                            bottom = 28.dp,
                        ),
                    ) {
                        if (showHero) {
                            item(key = "hero") { DailyHero(daily, onOpen) }
                        }
                        items(rows) { row ->
                            SectionRow(
                                title = row.title,
                                items = row.items,
                                cardWidth = if (row.kind == RowKind.CONTINUE) 240.dp else 132.dp,
                                wide = row.kind == RowKind.CONTINUE,
                                onClick = onOpen,
                            )
                        }
                    }
                }
            }
        }

        HomeHeader(
            session = session,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onRefresh = { reloadKey++ },
            onNavigate = onNavigate,
            onPush = onPush,
            onLogout = onLogout,
            onImage = showHero,
        )
    }
}

/** 隐藏用户关掉的媒体库，并按自定义顺序排列。 */
private fun arrangeRows(config: com.rainplayer.data.ServerConfig?, rows: List<HomeRow>): List<HomeRow> {
    if (config == null) return rows
    val visible = rows.filterNot { it.libraryId != null && it.libraryId in config.hiddenLibraries }
    if (config.libraryOrder.isEmpty()) return visible
    val rank = config.libraryOrder.withIndex().associate { (i, id) -> id to i }
    val head = visible.filter { it.libraryId == null }
    val tail = visible.filter { it.libraryId != null }
        .sortedBy { rank[it.libraryId] ?: Int.MAX_VALUE }
    return head + tail
}

/**
 * 每日推荐。
 *
 * 从首页各行里挑出带背景图的条目，按「天」做确定性轮转——同一天进来顺序稳定，
 * 换一天自动换一批，不需要任何服务器端支持。
 */
private fun dailyPicks(rows: List<HomeRow>): List<MediaCard> {
    val pool = LinkedHashMap<String, MediaCard>()
    rows.filter { it.kind != RowKind.CONTINUE }
        .flatMap { it.items }
        .filter { !it.backdropUrl.isNullOrBlank() }
        .forEach { pool.putIfAbsent(it.id, it) }
    if (pool.size < 3) {
        rows.filter { it.kind == RowKind.CONTINUE }.flatMap { it.items }.forEach { pool.putIfAbsent(it.id, it) }
    }
    val list = pool.values.sortedByDescending { it.rating ?: 0.0 }.take(12).toList()
    if (list.isEmpty()) return emptyList()
    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val start = day % list.size
    return (list.drop(start) + list.take(start)).take(6)
}

@Composable
private fun HomeHeader(
    session: AppSession,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onNavigate: (Dest) -> Unit,
    onPush: (Dest) -> Unit,
    onLogout: () -> Unit,
    /** 是否浮在大图之上：是则给胶囊加深色底，保证在任何画面上都读得清。 */
    onImage: Boolean = false,
) {
    val active = session.active
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (onImage) Color(0x73000000) else Color.Transparent)
                    .border(
                        width = if (onImage) 1.dp else 0.dp,
                        color = if (onImage) Color(0x26FFFFFF) else Color.Transparent,
                        shape = RoundedCornerShape(24.dp),
                    )
                    .clickable { onMenuOpenChange(true) }
                    .padding(horizontal = if (onImage) 10.dp else 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active != null) {
                    ServerAvatar(active, 32.dp)
                    Spacer(Modifier.width(9.dp))
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            active?.name ?: "RainPlayer",
                            color = if (onImage) Color.White else RainPlayerText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "服务器菜单",
                            tint = if (onImage) Color(0xCCFFFFFF) else RainPlayerTextDim,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text(
                        subtitleOf(session),
                        color = if (onImage) Color(0x99FFFFFF) else RainPlayerTextDim,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ServerMenu(
                session = session,
                expanded = menuOpen,
                onDismiss = { onMenuOpenChange(false) },
                onNavigate = {
                    onMenuOpenChange(false)
                    onNavigate(it)
                },
                onPush = {
                    onMenuOpenChange(false)
                    onPush(it)
                },
                onLogout = {
                    onMenuOpenChange(false)
                    onLogout()
                },
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (onImage) Color(0x73000000) else Color.Transparent)
                .border(
                    width = if (onImage) 1.dp else 0.dp,
                    color = if (onImage) Color(0x26FFFFFF) else Color.Transparent,
                    shape = CircleShape,
                )
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "刷新",
                tint = if (onImage) Color(0xE6FFFFFF) else RainPlayerTextDim,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun subtitleOf(session: AppSession): String {
    val active = session.active ?: return ""
    val line = active.activeLine?.name
    val user = active.activeUser?.username
    return listOfNotNull(active.kind.displayName, line, user)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
}

@Composable
private fun ServerMenu(
    session: AppSession,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (Dest) -> Unit,
    onPush: (Dest) -> Unit,
    onLogout: () -> Unit,
) {
    val p = rainPalette
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            "服务器",
            color = RainPlayerTextDim,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp),
        )
        session.servers.forEach { cfg ->
            val selected = cfg.id == session.activeId
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServerAvatar(cfg, 26.dp, selected)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                cfg.name,
                                color = if (selected) RainPlayerAccent else RainPlayerText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                cfg.summary,
                                color = RainPlayerTextDim,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = RainPlayerAccent, modifier = Modifier.size(16.dp))
                    }
                },
                onClick = {
                    onDismiss()
                    if (!selected) session.activate(cfg)
                },
            )
        }
        DropdownMenuItem(
            text = { Text("添加服务器", color = RainPlayerText, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = RainPlayerTextDim, modifier = Modifier.size(18.dp)) },
            onClick = { onPush(Dest.AddServer) },
        )

        MenuDivider()

        DropdownMenuItem(
            text = { Text("媒体库", color = RainPlayerText, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = RainPlayerTextDim, modifier = Modifier.size(18.dp)) },
            onClick = { onPush(Dest.Libraries) },
        )
        DropdownMenuItem(
            text = { Text("服务器设置", color = RainPlayerText, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = RainPlayerTextDim, modifier = Modifier.size(18.dp)) },
            onClick = { onPush(Dest.Servers) },
        )
        DropdownMenuItem(
            text = { Text("搜索", color = RainPlayerText, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = RainPlayerTextDim, modifier = Modifier.size(18.dp)) },
            onClick = { onNavigate(Dest.Search) },
        )
        DropdownMenuItem(
            text = { Text("应用设置", color = RainPlayerText, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = RainPlayerTextDim, modifier = Modifier.size(18.dp)) },
            onClick = { onNavigate(Dest.Settings) },
        )

        MenuDivider()

        DropdownMenuItem(
            text = { Text("退出登录", color = RainPlayerTextDim, fontSize = 13.sp) },
            onClick = onLogout,
        )
    }
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(1.dp)
            .background(Color(0x18FFFFFF))
    )
}


// ------------------------------------------------------------------ 媒体库（含开关/排序）

@Composable
private fun LibrariesScreen(session: AppSession, onBack: () -> Unit, onOpen: (Library) -> Unit) {
    val backend = session.backend
    var state by remember { mutableStateOf<Load<List<Library>>>(Load.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(backend, reloadKey) {
        if (backend == null) return@LaunchedEffect
        state = Load.Loading
        state = runCatching { backend.libraries() }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "加载失败") })
    }

    Column(Modifier.fillMaxSize()) {
        SubHeader("媒体库", onBack)
        Text(
            "关掉的媒体库不会出现在首页；上下箭头调整首页里的顺序",
            color = RainPlayerTextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
        )
        when (val s = state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { reloadKey++ }
            is Load.Ok -> {
                val config = session.active
                if (s.data.isEmpty()) {
                    EmptyView("没有可用的媒体库")
                } else {
                    val allIds = s.data.map { it.id }
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        items(s.data) { library ->
                            val hidden = config?.hiddenLibraries?.contains(library.id) == true
                            GlassSurface(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clickable { onOpen(library) }
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Text(
                                            library.name,
                                            color = if (hidden) RainPlayerTextDim else RainPlayerText,
                                            fontSize = 14.sp,
                                        )
                                        Text(
                                            library.collectionType ?: "混合内容",
                                            color = RainPlayerTextDim,
                                            fontSize = 10.sp,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            config?.let { session.moveLibrary(it.id, allIds, library.id, -1) }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowUp,
                                            contentDescription = "上移",
                                            tint = RainPlayerTextDim,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            config?.let { session.moveLibrary(it.id, allIds, library.id, 1) }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "下移",
                                            tint = RainPlayerTextDim,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Switch(
                                        checked = !hidden,
                                        onCheckedChange = { on ->
                                            config?.let { session.setLibraryHidden(it.id, library.id, !on) }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.Black,
                                            checkedTrackColor = RainPlayerAccent,
                                            uncheckedThumbColor = RainPlayerTextDim,
                                            uncheckedTrackColor = Color(0x22FFFFFF),
                                        ),
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

@Composable
fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = RainPlayerText)
        }
        Text(title, color = RainPlayerText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ 媒体库内容

@Composable
private fun LibraryScreen(session: AppSession, library: Library, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var items by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var startIndex by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(Int.MAX_VALUE) }

    suspend fun load(reset: Boolean) {
        if (backend == null) return
        loading = true
        runCatching { backend.items(library.id, if (reset) 0 else startIndex, PAGE) }
            .onSuccess { page ->
                items = if (reset) page.items else items + page.items
                startIndex = if (reset) page.items.size else startIndex + page.items.size
                total = if (page.items.isEmpty()) items.size else page.total
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(library.id) {
        items = emptyList()
        startIndex = 0
        total = Int.MAX_VALUE
        load(true)
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            library.name,
            color = RainPlayerText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        when {
            loading && items.isEmpty() -> LoadingView()
            error != null && items.isEmpty() -> ErrorView(error!!) { }
            items.isEmpty() -> EmptyView("这个媒体库还没有内容")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { card ->
                    PosterCard(card, 108.dp) { onOpen(card) }
                }
                if (loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("加载中…", color = RainPlayerTextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (items.size < total && !loading) {
        LaunchedEffect(items.size) { load(false) }
    }
}

// ------------------------------------------------------------------ 聚合搜索

@Composable
private fun SearchScreen(session: AppSession, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var failures by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    var trigger by remember { mutableStateOf(0) }

    val aggregate = Graph.prefs.aggregateSearch && session.servers.size > 1

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val keyword = query.trim()
        if (keyword.isEmpty()) return@LaunchedEffect
        loading = true
        error = null
        searched = true
        runCatching {
            if (aggregate) {
                val outcome = AggregateSearch.search(keyword, session.servers.map { it.normalize() })
                failures = outcome.failures
                outcome.cards
            } else {
                failures = emptyList()
                backend?.search(keyword) ?: emptyList()
            }
        }.onSuccess { results = it }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("搜索", color = RainPlayerText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (aggregate) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RainPlayerAccent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("聚合 ${session.servers.size} 台服务器", color = RainPlayerAccent, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("输入片名 / 演员", color = RainPlayerTextDim, fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { trigger++ }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RainPlayerText,
                unfocusedTextColor = RainPlayerText,
                focusedBorderColor = RainPlayerAccent,
                unfocusedBorderColor = Color(0x22FFFFFF),
                cursorColor = RainPlayerAccent,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingView()
            error != null -> ErrorView(error!!) { trigger++ }
            results.isEmpty() -> EmptyView(if (searched) "没有找到相关结果" else "输入关键词开始搜索")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { "${it.serverId}:${it.id}" }) { card ->
                    Column {
                        Box {
                            PosterCard(card, 108.dp) { onOpen(card) }
                            card.serverName?.let { name ->
                                SourceTag(name, Modifier.padding(start = 8.dp, top = 6.dp))
                            }
                        }
                    }
                }
                if (failures.isNotEmpty()) {
                    item {
                        Text(
                            failures.joinToString("；") { "${it.first}：${it.second}" },
                            color = RainPlayerTextDim,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val PAGE = 60
