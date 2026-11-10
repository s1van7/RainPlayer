package com.rainplayer.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.lerp
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
import com.rainplayer.data.MediaType

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

    // 首页再按一次返回才退出，避免误触直接杀掉应用
    val hostContext = androidx.compose.ui.platform.LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler(enabled = stack.size == 1) {
        val now = System.currentTimeMillis()
        if (now - lastBackAt in 1..2000) {
            (hostContext as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast
                .makeText(hostContext, "再按一次退出", android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    val showNav = stack.size == 1
    var navVisible by remember { mutableStateOf(true) }
    var lastScrollAt by remember { mutableStateOf(0L) }

    // 任何竖向拖动都先收起导航栏；停手 400ms 再弹回来
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && kotlin.math.abs(available.y) > 0.5f) {
                    lastScrollAt = System.currentTimeMillis()
                    navVisible = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(showNav) {
        if (!showNav) {
            navVisible = false
            return@LaunchedEffect
        }
        navVisible = true
        while (true) {
            delay(120)
            if (!navVisible && System.currentTimeMillis() - lastScrollAt > 400) navVisible = true
        }
    }

    // 滚动位置提升到这一层：进出详情页时列表状态不会被丢掉，返回后停在原位
    val homeListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val searchGridState = rememberLazyGridState()

    Scaffold(
        containerColor = RainPlayerBg,
        // 沉浸式：内容铺到状态栏 / 导航栏下面，各页自己按需加内边距
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
                // 首页大图要顶到状态栏里，详情页大图同理；其余页面统一避开状态栏
                .then(
                    if (current == Dest.Home || current is Dest.Detail) Modifier
                    else Modifier.statusBarsPadding()
                )
                .nestedScroll(scrollWatcher)
        ) {
            when (current) {
                Dest.Home -> HomeScreen(
                    session = session,
                    listState = homeListState,
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

                Dest.Search -> SearchScreen(session, searchGridState) { card ->
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

                is Dest.LibraryDest -> LibraryScreen(session, current.library, libraryGridState) { card ->
                    push(Dest.Detail(card.id, card.title))
                }

                is Dest.Detail -> DetailScreen(
                    session = session,
                    itemId = current.id,
                    fallbackTitle = current.title,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpenItem = { card -> push(Dest.Detail(card.id, card.title)) },
                    // 点某一集：直接进这一集的详情（同一套排版，带选集条）
                    onOpenEpisode = { ep -> push(Dest.Detail(ep.id, ep.title)) },
                    // 选集条切换：原地替换栈顶，不越点越深
                    onReplaceDetail = { id, title ->
                        stack[stack.lastIndex] = Dest.Detail(id, title)
                    },
                )
            }

            if (showNav) {
                AnimatedVisibility(
                    visible = navVisible,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    FloatingNavBar(current) { go(it) }
                }
            }
        }
    }
}

/** 悬浮底部导航：圆角胶囊，浮在内容之上，不占布局高度。 */
@Composable
private fun FloatingNavBar(current: Dest, onSelect: (Dest) -> Unit) {
    val p = rainPalette
    val tabs = listOf(
        Triple(Dest.Home, "首页", Icons.Filled.Home),
        Triple(Dest.Search, "搜索", Icons.Filled.Search),
        Triple(Dest.Settings, "设置", Icons.Filled.Settings),
    )
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 18.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (p.isLight) Color.White.copy(alpha = 0.72f) else p.surface.copy(alpha = 0.62f)
            )
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                )
            )
            .border(
                1.dp,
                if (p.isLight) Color(0x1A000000) else Color(0x2EFFFFFF),
                RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (dest, label, icon) ->
            val selected = current == dest
            Column(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (selected) RainPlayerAccent.copy(alpha = 0.18f) else Color.Transparent
                    )
                    .clickable { onSelect(dest) }
                    .padding(horizontal = 30.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (selected) RainPlayerAccent else RainPlayerTextDim,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    color = if (selected) RainPlayerAccent else RainPlayerTextDim,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 首页

@Composable
private fun HomeScreen(
    session: AppSession,
    listState: LazyListState,
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
    // 大图主色：整页背景跟着它轻微染色，和参考稿一致
    var heroTint by remember { mutableStateOf<Color?>(null) }
    val p = rainPalette

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

    // 状态栏高度：没有大图时用它给内容让位
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 大图顶到屏幕最上沿，状态栏图标压在画面上，换成白色
    DisposableEffect(showHero) {
        com.rainplayer.ui.theme.SystemBarsState.overImage = showHero
        onDispose { com.rainplayer.ui.theme.SystemBarsState.overImage = false }
    }

    Box(Modifier.fillMaxSize()) {
        // 整页底色：主色大面积染色，从顶到底都是同一色调，大图的底边才能融进去。
        // 换页时颜色用动画过渡，背景不会突然跳。
        val tint by animateColorAsState(
            targetValue = heroTint ?: p.bg,
            animationSpec = tween(650),
            label = "heroTint",
        )
        val deep = lerp(p.bg, tint, if (p.isLight) 0.18f else 0.55f)
        val mid = lerp(p.bg, tint, if (p.isLight) 0.10f else 0.32f)
        val shallow = lerp(p.bg, tint, if (p.isLight) 0.04f else 0.14f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to deep,
                        0.42f to mid,
                        1f to shallow,
                    )
                )
        )
        val snapshot = state
        when (snapshot) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(snapshot.message) { reloadKey++ }
            is Load.Ok -> {
                if (rows.isEmpty()) {
                    EmptyView("服务器上没有找到内容")
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            // 大图从屏幕最上沿开始（满屏），无大图时给标题栏让位
                            top = if (showHero) 0.dp else statusTop + 56.dp,
                            // 底部留白给悬浮导航栏让位，避免最后一行被盖住
                            bottom = 110.dp,
                        ),
                    ) {
                        if (showHero) {
                            item(key = "hero") {
                                DailyHero(daily, onOpen, onTint = { heroTint = it })
                            }
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

        // 滚动后内容会从状态栏下面穿过，铺一层页面底色把它和状态栏图标隔开；
        // 停在顶部时不铺，大图依然是完整的。颜色取页面顶部那层主色，衔接不出色差。
        val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 6
        val veil = if (scrolled) 0.97f else 0f
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusTop + 18.dp)
                .background(
                    Brush.verticalGradient(
                        0f to deep.copy(alpha = veil),
                        0.80f to deep.copy(alpha = veil),
                        1f to deep.copy(alpha = 0f),
                    )
                )
        )

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
 * **每个媒体库各出一个**（取该库里评分最高的那部），保证轮播能覆盖到所有库；
 * 库太少时用其余高分条目补齐到 6 个。最后按「天」做确定性轮转——
 * 同一天进来顺序稳定，换一天自动换一批。
 */
private fun dailyPicks(rows: List<HomeRow>): List<MediaCard> {
    val picks = LinkedHashMap<String, MediaCard>()

    fun usable(card: MediaCard) =
        !card.posterUrl.isNullOrBlank() || !card.backdropUrl.isNullOrBlank()

    // 一行 = 一个媒体库（继续观看那行没有 libraryId，跳过）
    rows.filter { it.kind != RowKind.CONTINUE && it.libraryId != null }
        .forEach { row ->
            val best = row.items.filter { usable(it) }.maxByOrNull { it.rating ?: 0.0 }
            if (best != null) picks.putIfAbsent(best.id, best)
        }

    // 只有一个库（或库太少）时，用其余条目按评分补齐
    if (picks.size < 6) {
        rows.filter { it.kind != RowKind.CONTINUE }
            .flatMap { it.items }
            .filter { usable(it) }
            .sortedByDescending { it.rating ?: 0.0 }
            .forEach { if (picks.size < 6) picks.putIfAbsent(it.id, it) }
    }
    if (picks.size < 3) {
        rows.filter { it.kind == RowKind.CONTINUE }
            .flatMap { it.items }
            .filter { usable(it) }
            .forEach { picks.putIfAbsent(it.id, it) }
    }

    val mixed = picks.values.toList()
    if (mixed.isEmpty()) return emptyList()
    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val start = day % mixed.size
    return (mixed.drop(start) + mixed.take(start)).take(10)
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
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 8.dp, top = if (onImage) 14.dp else 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        // 毛玻璃：半透明白 + 竖向高光，透出底下的画面
                        if (onImage) {
                            Brush.verticalGradient(
                                listOf(Color(0x80FFFFFF), Color(0x4DFFFFFF))
                            )
                        } else {
                            Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                        }
                    )
                    .border(
                        width = if (onImage) 1.dp else 0.dp,
                        color = if (onImage) Color(0x73FFFFFF) else Color.Transparent,
                        shape = RoundedCornerShape(percent = 50),
                    )
                    .clickable { onMenuOpenChange(true) }
                    .padding(
                        start = if (onImage) 4.dp else 3.dp,
                        end = if (onImage) 10.dp else 3.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active != null) {
                    ServerAvatar(active, 26.dp, circle = onImage)
                    Spacer(Modifier.width(7.dp))
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            active?.name ?: "RainPlayer",
                            color = if (onImage) Color(0xFF1B1F26) else RainPlayerText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "服务器菜单",
                            tint = if (onImage) Color(0x99000000) else RainPlayerTextDim,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    if (!onImage) {
                        Text(
                            subtitleOf(session),
                            color = RainPlayerTextDim,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
                .background(
                    if (onImage) {
                        Brush.verticalGradient(
                            listOf(Color(0x80FFFFFF), Color(0x4DFFFFFF))
                        )
                    } else {
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .border(
                    width = if (onImage) 1.dp else 0.dp,
                    color = if (onImage) Color(0x73FFFFFF) else Color.Transparent,
                    shape = CircleShape,
                )
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "刷新",
                tint = if (onImage) Color(0xFF1B1F26) else RainPlayerTextDim,
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

    // 打开菜单时拉一次服务器统计（失败就不显示这一行）
    var counts by remember(expanded, session.activeId) {
        mutableStateOf<com.rainplayer.data.ServerCounts?>(null)
    }
    LaunchedEffect(expanded, session.activeId) {
        if (!expanded) return@LaunchedEffect
        counts = null
        val b = session.backend ?: return@LaunchedEffect
        counts = runCatching { b.counts() }.getOrNull()
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Text("服务器", color = RainPlayerTextDim, fontSize = 10.sp)
            counts?.takeIf { !it.isEmpty }?.let { c ->
                Spacer(Modifier.height(3.dp))
                Text(
                    buildString {
                        append("电影 ${c.movies} · 剧集 ${c.series}")
                        if (c.episodes > 0) append(" · 单集 ${c.episodes}")
                    },
                    color = RainPlayerAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
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
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp,
                        )
                    ) {
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
private fun LibraryScreen(
    session: AppSession,
    library: Library,
    gridState: LazyGridState,
    onOpen: (MediaCard) -> Unit,
) {
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
                state = gridState,
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp, top = 6.dp, bottom = 110.dp,
                ),
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
private fun SearchScreen(
    session: AppSession,
    gridState: LazyGridState,
    onOpen: (MediaCard) -> Unit,
) {
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
                state = gridState,
                contentPadding = PaddingValues(
                    start = 14.dp, end = 14.dp, top = 6.dp, bottom = 110.dp,
                ),
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
