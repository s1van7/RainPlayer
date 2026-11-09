package com.rain.player.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rain.player.Graph
import com.rain.player.core.Prefs
import com.rain.player.player.PlayerCapabilities
import com.rain.player.ui.theme.RainAccent
import com.rain.player.ui.theme.RainSurface
import com.rain.player.ui.theme.RainText
import com.rain.player.ui.theme.RainTextDim

@Composable
fun SettingsScreen(
    session: AppSession,
    onLogout: () -> Unit,
    onAddServer: (() -> Unit)? = null,
) {
    val prefs: Prefs = Graph.prefs
    var passthrough by remember { mutableStateOf(prefs.audioPassthrough) }
    var hwDecode by remember { mutableStateOf(prefs.preferHardwareDecode) }
    var tunneled by remember { mutableStateOf(prefs.tunneledPlayback) }
    var matchDisplay by remember { mutableStateOf(prefs.matchDisplayMode) }
    var keepScreen by remember { mutableStateOf(prefs.keepScreenOn) }
    var autoNext by remember { mutableStateOf(prefs.autoPlayNext) }
    val capabilities = remember { PlayerCapabilities.detect(Graph.app) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("设置", color = RainText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        Section("服务器") {
            session.backend?.let { backend ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(RainSurface, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(backend.config.name, color = RainText, fontSize = 14.sp)
                        Text(backend.config.url, color = RainTextDim, fontSize = 11.sp)
                        Text(
                            "${backend.config.kind.displayName} · ${backend.config.username}",
                            color = RainTextDim,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            session.servers.filter { it.id != session.backend?.config?.id }.forEach { server ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(RainSurface, RoundedCornerShape(10.dp))
                        .clickable { session.activate(server) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("切换到 ${server.name}", color = RainText, fontSize = 13.sp)
                        Text(server.url, color = RainTextDim, fontSize = 11.sp)
                    }
                    Text(server.kind.displayName, color = RainAccent, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onAddServer != null) {
                    Box(
                        Modifier
                            .background(RainSurface, RoundedCornerShape(10.dp))
                            .clickable { onAddServer() }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("添加服务器", color = RainText, fontSize = 12.sp)
                    }
                }
                Box(
                    Modifier
                        .background(RainSurface, RoundedCornerShape(10.dp))
                        .clickable {
                            session.logout()
                            onLogout()
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text("退出登录", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                }
            }
        }

        Section("播放") {
            ToggleRow(
                title = "音频直通",
                desc = "AC3 / EAC3 / DTS / TrueHD 原码输出到功放或电视；关闭后统一解码为 PCM",
                checked = passthrough,
            ) {
                passthrough = it
                prefs.audioPassthrough = it
            }
            ToggleRow(
                title = "优先硬件解码",
                desc = "关闭后强制使用 FFmpeg 软解，适合个别解码器有 bug 的老设备（4K/8K 可能卡顿）",
                checked = hwDecode,
            ) {
                hwDecode = it
                prefs.preferHardwareDecode = it
            }
            ToggleRow(
                title = "隧道播放（Tunneled）",
                desc = "部分电视开启后 HDR 切换更平滑，出现黑屏/花屏请关闭",
                checked = tunneled,
            ) {
                tunneled = it
                prefs.tunneledPlayback = it
            }
            ToggleRow(
                title = "匹配显示模式",
                desc = "按片源分辨率与帧率切换输出（4K/8K、23.976fps）",
                checked = matchDisplay,
            ) {
                matchDisplay = it
                prefs.matchDisplayMode = it
            }
            ToggleRow(
                title = "播放时保持常亮",
                desc = "避免播放中屏幕休眠",
                checked = keepScreen,
            ) {
                keepScreen = it
                prefs.keepScreenOn = it
            }
            ToggleRow(
                title = "自动连播下一集",
                desc = "剧集播放结束后自动播放下一集",
                checked = autoNext,
            ) {
                autoNext = it
                prefs.autoPlayNext = it
            }
        }

        Section("本机解码能力") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(RainSurface, RoundedCornerShape(10.dp))
                    .padding(14.dp),
            ) {
                CapRow("HDR10", capabilities.hdr10)
                CapRow("HLG", capabilities.hlg)
                CapRow("HDR10+", capabilities.hdr10Plus)
                CapRow("杜比视界", capabilities.dolbyVision)
                CapRow("AC3 直通", capabilities.ac3Passthrough)
                CapRow("EAC3 直通", capabilities.eac3Passthrough)
                CapRow("DTS 直通", capabilities.dtsPassthrough)
                CapRow("TrueHD 直通", capabilities.trueHdPassthrough)
                Spacer(Modifier.height(6.dp))
                Text(
                    "最大显示：${capabilities.maxWidth}x${capabilities.maxHeight} @ ${capabilities.maxRefreshRate.toInt()}Hz",
                    color = RainTextDim,
                    fontSize = 11.sp,
                )
                if (capabilities.dolbyVisionProfiles.isNotEmpty()) {
                    Text(
                        "杜比视界 Profile：${capabilities.dolbyVisionProfiles.joinToString(", ")}",
                        color = RainTextDim,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Section("关于") {
            Text(
                "Rain 1.0.0\n" +
                    "基于 Media3 / ExoPlayer + FFmpeg 解码扩展\n" +
                    "支持 Emby / Jellyfin / 飞牛影视\n" +
                    "最低支持 Android 5.0（API 21）",
                color = RainTextDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = RainTextDim,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp, top = 6.dp),
    )
    content()
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(RainSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = RainText, fontSize = 14.sp)
            Text(desc, color = RainTextDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = RainAccent,
            ),
        )
    }
}

@Composable
private fun CapRow(label: String, supported: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = RainTextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            if (supported) "支持" else "不支持",
            color = if (supported) RainAccent else RainTextDim,
            fontSize = 12.sp,
        )
    }
}
