package com.rainplayer.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rainplayer.Graph
import com.rainplayer.data.BackendFactory
import com.rainplayer.data.ServerConfig
import com.rainplayer.data.ServerKind
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.common.RainPlayerButton
import com.rainplayer.ui.common.ServerAvatar
import com.rainplayer.ui.common.UserAvatar
import com.rainplayer.ui.theme.GlassSurface
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerRed
import com.rainplayer.ui.theme.RainPlayerSurfaceHigh
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import kotlinx.coroutines.launch
import java.util.UUID

// ------------------------------------------------------------------ 添加服务器

@Composable
fun AddServerScreen(
    session: AppSession,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var kind by remember { mutableStateOf(ServerKind.EMBY) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val hint = when (kind) {
        ServerKind.FNOS -> "http://192.168.1.10:5666"
        else -> "http://192.168.1.10:8096"
    }

    Column(Modifier.fillMaxSize()) {
        SubHeader("添加服务器", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text("服务类型", color = RainPlayerTextDim, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerKind.entries.forEach { item ->
                    val selected = item == kind
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) RainPlayerAccent else RainPlayerSurfaceHigh)
                            .clickable { kind = item }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            item.displayName,
                            color = if (selected) Color.Black else RainPlayerText,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            GlassField("显示名称（可选）", name, "例如：家里的 Emby") { name = it }
            Spacer(Modifier.height(10.dp))
            GlassField("服务器地址", url, hint) { url = it }
            Spacer(Modifier.height(10.dp))
            GlassField("图标（可选，图片 URL 或 emoji）", icon, "https://… 或 🎬") { icon = it }
            Spacer(Modifier.height(10.dp))
            GlassField("用户名", username, "输入账号") { username = it }
            Spacer(Modifier.height(10.dp))
            GlassField("密码", password, "输入密码", password = true) { password = it }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = RainPlayerRed, fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = RainPlayerAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("正在连接…", color = RainPlayerTextDim, fontSize = 12.sp)
                }
            } else {
                RainPlayerButton("连接并保存", Modifier.fillMaxWidth()) {
                    if (url.isBlank()) {
                        error = "请输入服务器地址"; return@RainPlayerButton
                    }
                    if (username.isBlank()) {
                        error = "请输入用户名"; return@RainPlayerButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        runCatching {
                            val draft = ServerConfig(
                                id = UUID.randomUUID().toString(),
                                kind = kind,
                                name = name.trim().ifBlank { kind.displayName },
                                iconUrl = icon.trim(),
                                url = BackendFactory.normalizeUrl(url),
                                username = username.trim(),
                                deviceId = Graph.deviceId,
                            )
                            BackendFactory.create(draft).authenticate(username.trim(), password)
                        }.onSuccess { authed ->
                            loading = false
                            val final = authed.copy(
                                deviceId = Graph.deviceId,
                                name = name.trim().ifBlank { authed.name },
                                iconUrl = icon.trim(),
                            ).normalize()
                            session.activate(final)
                            onDone()
                        }.onFailure { e ->
                            loading = false
                            error = e.message ?: "连接失败"
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

// ------------------------------------------------------------------ 服务器管理

@Composable
fun ServerManageScreen(
    session: AppSession,
    onBack: () -> Unit,
    onAddServer: () -> Unit,
) {
    var expandedId by remember { mutableStateOf(session.activeId) }
    var lineDialogFor by remember { mutableStateOf<ServerConfig?>(null) }
    var userDialogFor by remember { mutableStateOf<ServerConfig?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { SubHeader("服务器设置", onBack) }
            IconButton(onClick = onAddServer) {
                Icon(Icons.Filled.Add, contentDescription = "添加服务器", tint = RainPlayerAccent)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            session.servers.forEach { cfg ->
                val expanded = cfg.id == expandedId
                GlassSurface(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { expandedId = if (expanded) null else cfg.id }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ServerAvatar(cfg, 34.dp, cfg.id == session.activeId)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(cfg.name, color = RainPlayerText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(cfg.summary, color = RainPlayerTextDim, fontSize = 10.sp)
                            }
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = RainPlayerTextDim,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        if (expanded) {
                            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                DividerSoft()

                                // 名称与图标
                                Spacer(Modifier.height(10.dp))
                                CommitField(
                                    label = "服务器名称",
                                    initial = cfg.name,
                                    placeholder = "显示在首页左上角",
                                ) { session.renameServer(cfg.id, it) }
                                Spacer(Modifier.height(8.dp))
                                CommitField(
                                    label = "图标（图片 URL 或 emoji / 文字）",
                                    initial = cfg.iconUrl,
                                    placeholder = "https://… 或 🎬",
                                ) { session.updateIcon(cfg.id, it) }

                                // 线路
                                Spacer(Modifier.height(14.dp))
                                SectionLabel("线路", "点一下切到这条线路")
                                cfg.lines.forEach { line ->
                                    val active = line.id == cfg.activeLineId
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { session.switchLine(cfg.id, line.id) }
                                            .padding(vertical = 7.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioDot(active)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                line.displayName,
                                                color = if (active) RainPlayerAccent else RainPlayerText,
                                                fontSize = 12.sp,
                                            )
                                            Text(line.url, color = RainPlayerTextDim, fontSize = 10.sp)
                                        }
                                        if (cfg.lines.size > 1) {
                                            IconButton(
                                                onClick = { session.removeLine(cfg.id, line.id) },
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "删除线路",
                                                    tint = RainPlayerTextDim,
                                                    modifier = Modifier.size(15.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                TextButton(onClick = { lineDialogFor = cfg }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = RainPlayerAccent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("添加线路", color = RainPlayerAccent, fontSize = 12.sp)
                                }

                                // 账号
                                Spacer(Modifier.height(6.dp))
                                SectionLabel("登录账号", "同一台服务器可以挂多个账号")
                                cfg.users.forEach { user ->
                                    val active = user.id == cfg.activeUserId
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { session.switchUser(cfg.id, user.id) }
                                            .padding(vertical = 7.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioDot(active)
                                        Spacer(Modifier.width(8.dp))
                                        UserAvatar(user.username, 22.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            user.username,
                                            color = if (active) RainPlayerAccent else RainPlayerText,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (cfg.users.size > 1) {
                                            IconButton(
                                                onClick = { session.removeUser(cfg.id, user.id) },
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "移除账号",
                                                    tint = RainPlayerTextDim,
                                                    modifier = Modifier.size(15.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                TextButton(onClick = { userDialogFor = cfg }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = RainPlayerAccent, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("登录新账号", color = RainPlayerAccent, fontSize = 12.sp)
                                }

                                DividerSoft()
                                TextButton(onClick = {
                                    if (cfg.id == session.activeId) session.logout()
                                    session.remove(cfg)
                                    expandedId = null
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = RainPlayerRed, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("删除这台服务器", color = RainPlayerRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    lineDialogFor?.let { cfg ->
        AddLineDialog(
            server = cfg,
            session = session,
            onDismiss = { lineDialogFor = null },
        )
    }

    userDialogFor?.let { cfg ->
        AddUserDialog(
            server = cfg,
            session = session,
            onDismiss = { userDialogFor = null },
        )
    }
}

@Composable
private fun AddLineDialog(
    server: ServerConfig,
    session: AppSession,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = RainPlayerSurfaceHigh,
        title = { Text("添加线路", color = RainPlayerText, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "同一台服务器的多个访问地址，比如内网、外网或中转。连不上的时候会自动换下一条。",
                    color = RainPlayerTextDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                GlassField("线路名称", name, "例如：外网") { name = it }
                Spacer(Modifier.height(8.dp))
                GlassField("地址", url, "http://example.com:8096") { url = it }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = RainPlayerRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (url.isBlank()) {
                        error = "请输入地址"; return@TextButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        session.addLine(server.id, name, url)
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: "这条线路连不上" }
                        busy = false
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(color = RainPlayerAccent, modifier = Modifier.size(14.dp))
                } else {
                    Text("添加", color = RainPlayerAccent)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("取消", color = RainPlayerTextDim)
            }
        },
    )
}

@Composable
private fun AddUserDialog(
    server: ServerConfig,
    session: AppSession,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = RainPlayerSurfaceHigh,
        title = { Text("登录新账号", color = RainPlayerText, fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "用当前线路 ${server.activeLine?.url ?: ""} 登录",
                    color = RainPlayerTextDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(10.dp))
                GlassField("用户名", username, "输入账号") { username = it }
                Spacer(Modifier.height(8.dp))
                GlassField("密码", password, "输入密码", password = true) { password = it }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = RainPlayerRed, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (username.isBlank()) {
                        error = "请输入用户名"; return@TextButton
                    }
                    busy = true
                    scope.launch {
                        session.addUser(server.id, username.trim(), password)
                            .onSuccess { onDismiss() }
                            .onFailure { error = it.message ?: "登录失败" }
                        busy = false
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(color = RainPlayerAccent, modifier = Modifier.size(14.dp))
                } else {
                    Text("登录", color = RainPlayerAccent)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("取消", color = RainPlayerTextDim)
            }
        },
    )
}

// ------------------------------------------------------------------ 小组件

@Composable
fun GlassField(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = RainPlayerTextDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = Color(0x55FFFFFF), fontSize = 12.sp) },
            singleLine = true,
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RainPlayerText,
                unfocusedTextColor = RainPlayerText,
                focusedBorderColor = RainPlayerAccent,
                unfocusedBorderColor = Color(0x22FFFFFF),
                cursorColor = RainPlayerAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 失焦才提交的输入框。
 *
 * 服务器名称 / 图标这类字段每改一个字都要落盘并重建后端，
 * 逐字符提交会疯狂重建连接，所以这里等用户离开输入框再提交。
 */
@Composable
fun CommitField(
    label: String,
    initial: String,
    placeholder: String,
    onCommit: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text(label, color = RainPlayerTextDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder, color = Color(0x55FFFFFF), fontSize = 12.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RainPlayerText,
                unfocusedTextColor = RainPlayerText,
                focusedBorderColor = RainPlayerAccent,
                unfocusedBorderColor = Color(0x22FFFFFF),
                cursorColor = RainPlayerAccent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    if (focused && !state.isFocused && text != initial) onCommit(text)
                    focused = state.isFocused
                },
        )
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = RainPlayerText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(hint, color = RainPlayerTextDim, fontSize = 10.sp)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun RadioDot(selected: Boolean) {
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(if (selected) RainPlayerAccent else Color.Transparent)
            .then(
                if (selected) Modifier else Modifier.background(Color(0x22FFFFFF), CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun DividerSoft() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
}
