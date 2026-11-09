package com.rain.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rain.player.Graph
import com.rain.player.data.BackendFactory
import com.rain.player.data.ServerConfig
import com.rain.player.data.ServerKind
import com.rain.player.ui.common.RainButton
import com.rain.player.ui.theme.RainAccent
import com.rain.player.ui.theme.RainSurface
import com.rain.player.ui.theme.RainText
import com.rain.player.ui.theme.RainTextDim
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun LoginScreen(
    session: AppSession,
    onLoggedIn: () -> Unit,
) {
    var kind by remember { mutableStateOf(ServerKind.EMBY) }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val hint = when (kind) {
        ServerKind.EMBY -> "http://192.168.1.10:8096"
        ServerKind.JELLYFIN -> "http://192.168.1.10:8096"
        ServerKind.FNOS -> "http://192.168.1.10:5666"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .padding(horizontal = 24.dp, vertical = 56.dp),
        ) {
            Text("Rain", color = RainText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "连接 Emby / Jellyfin / 飞牛影视，支持 4K/8K、HDR、杜比视界与杜比全景声",
                color = RainTextDim,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(26.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerKind.entries.forEach { item ->
                    val selected = item == kind
                    Box(
                        Modifier
                            .background(
                                if (selected) RainAccent else RainSurface,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) RainAccent else Color(0x22FFFFFF),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { kind = item }
                            .focusable()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            item.displayName,
                            color = if (selected) Color.Black else RainText,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Field("服务器地址", url, hint) { url = it }
            Spacer(Modifier.height(10.dp))
            Field("用户名", username, "输入账号") { username = it }
            Spacer(Modifier.height(10.dp))
            Field("密码", password, "输入密码", password = true) { password = it }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }

            Spacer(Modifier.height(20.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = RainAccent,
                        modifier = Modifier.height(18.dp).widthIn(max = 18.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("  正在连接…", color = RainTextDim, fontSize = 13.sp)
                }
            } else {
                RainButton("连接", Modifier.fillMaxWidth()) {
                    val trimmedUrl = url.trim()
                    if (trimmedUrl.isBlank()) {
                        error = "请输入服务器地址"
                        return@RainButton
                    }
                    if (username.isBlank()) {
                        error = "请输入用户名"
                        return@RainButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        val config = ServerConfig(
                            id = UUID.randomUUID().toString(),
                            kind = kind,
                            name = kind.displayName,
                            url = BackendFactory.normalizeUrl(trimmedUrl),
                            username = username.trim(),
                            deviceId = Graph.deviceId,
                        )
                        runCatching {
                            BackendFactory.create(config).authenticate(username.trim(), password)
                        }.onSuccess { authed ->
                            session.activate(authed.copy(deviceId = Graph.deviceId))
                            loading = false
                            onLoggedIn()
                        }.onFailure { e ->
                            loading = false
                            error = e.message ?: "连接失败"
                        }
                    }
                }
            }

            if (session.servers.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("已保存的服务器", color = RainTextDim, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                session.servers.forEach { saved ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(RainSurface, RoundedCornerShape(8.dp))
                            .clickable {
                                session.activate(saved)
                                onLoggedIn()
                            }
                            .focusable()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(saved.name, color = RainText, fontSize = 13.sp)
                            Text(saved.url, color = RainTextDim, fontSize = 11.sp)
                        }
                        Text(saved.kind.displayName, color = RainAccent, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = RainTextDim, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = Color(0x66FFFFFF), fontSize = 13.sp) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = RainText,
                unfocusedTextColor = RainText,
                focusedBorderColor = RainAccent,
                unfocusedBorderColor = Color(0x22FFFFFF),
                cursorColor = RainAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
