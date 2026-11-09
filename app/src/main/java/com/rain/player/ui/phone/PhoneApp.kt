package com.rain.player.ui.phone

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rain.player.data.HomeRow
import com.rain.player.data.Library
import com.rain.player.data.MediaCard
import com.rain.player.ui.AppSession
import com.rain.player.ui.LoginScreen
import com.rain.player.ui.SettingsScreen
import com.rain.player.ui.common.EmptyView
import com.rain.player.ui.common.ErrorView
import com.rain.player.ui.common.Load
import com.rain.player.ui.common.LoadingView
import com.rain.player.ui.common.PosterCard
import com.rain.player.ui.common.SectionRow
import com.rain.player.ui.theme.RainAccent
import com.rain.player.ui.theme.RainBg
import com.rain.player.ui.theme.RainSurface
import com.rain.player.ui.theme.RainText
import com.rain.player.ui.theme.RainTextDim

sealed interface Dest {
    data object Home : Dest
    data object Libraries : Dest
    data object Search : Dest
    data object Settings : Dest
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

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    Scaffold(
        containerColor = RainBg,
        bottomBar = {
            if (stack.size == 1) {
                NavigationBar(containerColor = RainSurface) {
                    val tabs = listOf(
                        Triple(Dest.Home, "首页", Icons.Filled.Home),
                        Triple(Dest.Libraries, "媒体库", Icons.Filled.VideoLibrary),
                        Triple(Dest.Search, "搜索", Icons.Filled.Search),
                        Triple(Dest.Settings, "设置", Icons.Filled.Settings),
                    )
                    tabs.forEach { (dest, label, icon) ->
                        NavigationBarItem(
                            selected = current == dest,
                            onClick = {
                                stack.clear()
                                stack.add(dest)
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black,
                                selectedTextColor = RainAccent,
                                indicatorColor = RainAccent,
                                unselectedIconColor = RainTextDim,
                                unselectedTextColor = RainTextDim,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (current) {
                Dest.Home -> HomeScreen(session) { card ->
                    stack.add(Dest.Detail(card.id, card.title))
                }

                Dest.Libraries -> LibrariesScreen(session) { library ->
                    stack.add(Dest.LibraryDest(library))
                }

                Dest.Search -> SearchScreen(session) { card ->
                    stack.add(Dest.Detail(card.id, card.title))
                }

                Dest.Settings -> SettingsScreen(session, onLogout = {
                    session.logout()
                    onLogout()
                })

                is Dest.LibraryDest -> LibraryScreen(session, current.library) { card ->
                    stack.add(Dest.Detail(card.id, card.title))
                }

                is Dest.Detail -> DetailScreen(
                    session = session,
                    itemId = current.id,
                    fallbackTitle = current.title,
                    onBack = { stack.removeAt(stack.lastIndex) },
                    onOpenItem = { card -> stack.add(Dest.Detail(card.id, card.title)) },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(session: AppSession, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var state by remember { mutableStateOf<Load<List<HomeRow>>>(Load.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(backend, reloadKey) {
        if (backend == null) return@LaunchedEffect
        state = Load.Loading
        state = runCatching { backend.homeRows() }
            .fold({ Load.Ok(it) }, { Load.Err(it.message ?: "加载失败") })
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Rain", color = RainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    backend?.config?.name ?: "",
                    color = RainTextDim,
                    fontSize = 11.sp,
                )
            }
            IconButton(onClick = { reloadKey++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = RainTextDim)
            }
        }
        when (val s = state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { reloadKey++ }
            is Load.Ok -> {
                if (s.data.isEmpty()) {
                    EmptyView("服务器上没有找到内容")
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                        items(s.data) { row ->
                            SectionRow(
                                title = row.title,
                                items = row.items,
                                cardWidth = if (row.kind == com.rain.player.data.RowKind.CONTINUE) 240.dp else 132.dp,
                                wide = row.kind == com.rain.player.data.RowKind.CONTINUE,
                                onClick = onOpen,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrariesScreen(session: AppSession, onOpen: (Library) -> Unit) {
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
        Text(
            "媒体库",
            color = RainText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        when (val s = state) {
            is Load.Loading -> LoadingView()
            is Load.Err -> ErrorView(s.message) { reloadKey++ }
            is Load.Ok -> {
                if (s.data.isEmpty()) EmptyView("没有可用的媒体库")
                else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp)) {
                    items(s.data) { library ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .background(RainSurface, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable { onOpen(library) }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(library.name, color = RainText, fontSize = 15.sp)
                                Text(
                                    library.collectionType ?: "混合内容",
                                    color = RainTextDim,
                                    fontSize = 11.sp,
                                )
                            }
                            Text("›", color = RainTextDim, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

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
            color = RainText,
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
                            Text("加载中…", color = RainTextDim, fontSize = 12.sp)
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

@Composable
private fun SearchScreen(session: AppSession, onOpen: (MediaCard) -> Unit) {
    val backend = session.backend
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }

    fun doSearch() {
        if (query.isBlank() || backend == null) return
        loading = true
        error = null
        searched = true
    }

    LaunchedEffect(query, loading) {
        if (!loading || backend == null) return@LaunchedEffect
        runCatching { backend.search(query.trim()) }
            .onSuccess { results = it }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "搜索",
            color = RainText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("输入片名 / 演员", color = RainTextDim, fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        when {
            loading -> LoadingView()
            error != null -> ErrorView(error!!) { doSearch() }
            results.isEmpty() -> EmptyView(if (searched) "没有找到相关结果" else "输入关键词开始搜索")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results, key = { it.id }) { card ->
                    PosterCard(card, 108.dp) { onOpen(card) }
                }
            }
        }
    }
}

private const val PAGE = 60
