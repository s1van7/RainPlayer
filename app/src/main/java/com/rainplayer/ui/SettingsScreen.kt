package com.rainplayer.ui

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
import com.rainplayer.Graph
import com.rainplayer.core.Prefs
import com.rainplayer.player.PlayerCapabilities
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerSurface
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim

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
    var gestureEnabled by remember { mutableStateOf(prefs.gestureEnabled) }
    var brightnessGesture by remember { mutableStateOf(prefs.brightnessGesture) }
    var volumeGesture by remember { mutableStateOf(prefs.volumeGesture) }
    var longPressSpeed by remember { mutableStateOf(prefs.longPressSpeedEnabled) }
    var speedValue by remember { mutableStateOf(prefs.longPressSpeed) }
    var autoRotate by remember { mutableStateOf(prefs.autoRotate) }
    var theme by remember { mutableStateOf(com.rainplayer.ui.theme.ThemeState.theme) }
    var glass by remember { mutableStateOf(com.rainplayer.ui.theme.ThemeState.glass) }
    var aggregate by remember { mutableStateOf(prefs.aggregateSearch) }
    var daily by remember { mutableStateOf(prefs.dailyRecommend) }
    var douban by remember { mutableStateOf(prefs.doubanRating) }
    var tmdbKey by remember { mutableStateOf(prefs.tmdbApiKey) }
    val capabilities = remember { PlayerCapabilities.detect(Graph.app) }
    // 运行时读取真实版本号，避免「关于」里的版本号和实际包对不上
    val appVersion = remember {
        runCatching {
            @Suppress("DEPRECATION")
            Graph.app.packageManager.getPackageInfo(Graph.app.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text("设置", color = RainPlayerText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        Section("外观") {
            Text("主题", color = RainPlayerText, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            com.rainplayer.ui.theme.AppTheme.entries.chunked(3).forEach { rowThemes ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowThemes.forEach { item ->
                        val selected = item == theme
                        Row(
                            Modifier
                                .weight(1f)
                                .background(
                                    if (selected) item.palette.accent.copy(alpha = 0.22f) else RainPlayerSurface,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable {
                                    theme = item
                                    com.rainplayer.ui.theme.ThemeState.theme = item
                                    prefs.themeKey = item.key
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .background(item.palette.accent, RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                item.displayName,
                                color = if (selected) item.palette.accent else RainPlayerText,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(3 - rowThemes.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            ToggleRow(
                title = "毛玻璃效果",
                desc = "半透明磨砂面板；低配设备关掉可以更流畅",
                checked = glass,
            ) {
                glass = it
                com.rainplayer.ui.theme.ThemeState.glass = it
                prefs.glassEffect = it
            }
        }

        Section("内容与评分") {
            ToggleRow(
                title = "聚合搜索",
                desc = "同时搜索所有已添加的服务器，结果合并显示",
                checked = aggregate,
            ) {
                aggregate = it
                prefs.aggregateSearch = it
            }
            ToggleRow(
                title = "每日推荐",
                desc = "首页「继续观看」上方显示轮播推荐，每天自动换一批",
                checked = daily,
            ) {
                daily = it
                prefs.dailyRecommend = it
            }
            ToggleRow(
                title = "豆瓣评分",
                desc = "通过第三方聚合接口获取；拿不到时自动隐藏，不影响详情页",
                checked = douban,
            ) {
                douban = it
                prefs.doubanRating = it
                com.rainplayer.data.RatingsRepo.clearCache()
            }
            Spacer(Modifier.height(8.dp))
            Text("TMDB API Key", color = RainPlayerText, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "在 themoviedb.org 免费申请 v3 Key 后填入；留空则不显示 TMDB 评分",
                color = RainPlayerTextDim,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.OutlinedTextField(
                value = tmdbKey,
                onValueChange = {
                    tmdbKey = it
                    prefs.tmdbApiKey = it.trim()
                    com.rainplayer.data.RatingsRepo.clearCache()
                },
                singleLine = true,
                placeholder = { Text("粘贴 API Key", color = Color(0x55FFFFFF), fontSize = 12.sp) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RainPlayerText,
                    unfocusedTextColor = RainPlayerText,
                    focusedBorderColor = RainPlayerAccent,
                    unfocusedBorderColor = Color(0x22FFFFFF),
                    cursorColor = RainPlayerAccent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("服务器") {
            session.backend?.let { backend ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(RainPlayerSurface, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(backend.config.name, color = RainPlayerText, fontSize = 14.sp)
                        Text(backend.config.url, color = RainPlayerTextDim, fontSize = 11.sp)
                        Text(
                            "${backend.config.kind.displayName} · ${backend.config.username}",
                            color = RainPlayerTextDim,
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
                        .background(RainPlayerSurface, RoundedCornerShape(10.dp))
                        .clickable { session.activate(server) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("切换到 ${server.name}", color = RainPlayerText, fontSize = 13.sp)
                        Text(server.url, color = RainPlayerTextDim, fontSize = 11.sp)
                    }
                    Text(server.kind.displayName, color = RainPlayerAccent, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (onAddServer != null) {
                    Box(
                        Modifier
                            .background(RainPlayerSurface, RoundedCornerShape(10.dp))
                            .clickable { onAddServer() }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("添加服务器", color = RainPlayerText, fontSize = 12.sp)
                    }
                }
                Box(
                    Modifier
                        .background(RainPlayerSurface, RoundedCornerShape(10.dp))
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

        Section("手势与旋转") {
            ToggleRow(
                title = "启用手势控制",
                desc = "关闭后播放页只响应点击，不响应滑动和长按",
                checked = gestureEnabled,
            ) {
                gestureEnabled = it
                prefs.gestureEnabled = it
            }
            ToggleRow(
                title = "左半屏上下滑调亮度",
                desc = "在画面左半边上下滑动，调节屏幕亮度（不改系统亮度设置）",
                checked = brightnessGesture,
            ) {
                brightnessGesture = it
                prefs.brightnessGesture = it
            }
            ToggleRow(
                title = "右半屏上下滑调音量",
                desc = "在画面右半边上下滑动，调节媒体音量",
                checked = volumeGesture,
            ) {
                volumeGesture = it
                prefs.volumeGesture = it
            }
            ToggleRow(
                title = "长按倍速快进 / 快退",
                desc = "长按右半屏快进、长按左半屏快退，松手恢复原速",
                checked = longPressSpeed,
            ) {
                longPressSpeed = it
                prefs.longPressSpeedEnabled = it
            }
            if (longPressSpeed) {
                ChoiceRow(
                    title = "长按倍速",
                    options = listOf(2f to "2x", 3f to "3x", 4f to "4x"),
                    selected = speedValue,
                ) { value ->
                    prefs.longPressSpeed = value
                    speedValue = value
                }
            }
            ToggleRow(
                title = "跟随重力旋转",
                desc = "播放时按手机姿态自动切换横竖屏，忽略系统的旋转锁定",
                checked = autoRotate,
            ) {
                autoRotate = it
                prefs.autoRotate = it
            }
        }

        Section("本机解码能力") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(RainPlayerSurface, RoundedCornerShape(10.dp))
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
                    color = RainPlayerTextDim,
                    fontSize = 11.sp,
                )
                if (capabilities.dolbyVisionProfiles.isNotEmpty()) {
                    Text(
                        "杜比视界 Profile：${capabilities.dolbyVisionProfiles.joinToString(", ")}",
                        color = RainPlayerTextDim,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Section("关于") {
            Text(
                "RainPlayer $appVersion\n" +
                    "基于 Media3 / ExoPlayer + FFmpeg 解码扩展\n" +
                    "支持 Emby / Jellyfin / 飞牛影视\n" +
                    "最低支持 Android 5.0（API 21）",
                color = RainPlayerTextDim,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
        // 悬浮导航栏占位
        Spacer(Modifier.height(86.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = RainPlayerTextDim,
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
            .background(RainPlayerSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = RainPlayerText, fontSize = 14.sp)
            Text(desc, color = RainPlayerTextDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = RainPlayerAccent,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<Float, String>>,
    selected: Float,
    onSelect: (Float) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(RainPlayerSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = RainPlayerText, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                val active = kotlin.math.abs(selected - value) < 0.01f
                Box(
                    Modifier
                        .background(
                            if (active) RainPlayerAccent else Color(0x14FFFFFF),
                            RoundedCornerShape(7.dp),
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        label,
                        color = if (active) Color.Black else RainPlayerText,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapRow(label: String, supported: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = RainPlayerTextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            if (supported) "支持" else "不支持",
            color = if (supported) RainPlayerAccent else RainPlayerTextDim,
            fontSize = 12.sp,
        )
    }
}
