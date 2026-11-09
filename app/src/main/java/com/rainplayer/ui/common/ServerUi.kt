package com.rainplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.rainplayer.data.ServerConfig
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.rainPalette

/**
 * 服务器头像。
 *
 * 优先级（服务器自带的头像永远优先，不被用户自定义图标覆盖）：
 * 1. 账号在服务器上的头像（Emby / Jellyfin 的 `/Users/{id}/Images/Primary`）；
 * 2. [ServerConfig.iconUrl] 是 http(s) 地址 → 加载远程图片；
 * 3. 是一段普通文本（emoji / 单个汉字）→ 直接显示文本；
 * 4. 都没有 → 用服务器名首字母，背景走主题色渐变。
 */
@Composable
fun ServerAvatar(
    config: ServerConfig,
    size: Dp = 34.dp,
    selected: Boolean = false,
) {
    val p = rainPalette
    val shape = RoundedCornerShape(size * 0.3f)
    val serverAvatar = config.userAvatarUrl
    // 服务器没设头像时接口返回 404，失败一次就退回本地图标，不再重试
    var serverFailed by remember(serverAvatar) { mutableStateOf(false) }

    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(p.accent.copy(alpha = 0.85f), p.accentDim)
                )
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.18f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            serverAvatar != null && !serverFailed -> AsyncImage(
                model = serverAvatar,
                contentDescription = config.name,
                contentScale = ContentScale.Crop,
                onState = { st ->
                    if (st is AsyncImagePainter.State.Error) serverFailed = true
                },
                modifier = Modifier.size(size).clip(shape),
            )

            config.iconIsUrl -> AsyncImage(
                model = config.iconUrl,
                contentDescription = config.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )

            else -> Text(
                config.iconText,
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 圆形小头像（账号列表用）。传了 [url] 就显示服务器头像，否则显示首字母。 */
@Composable
fun UserAvatar(name: String, size: Dp = 28.dp, url: String? = null) {
    val p = rainPalette
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(p.surfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null && !failed) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                onState = { st -> if (st is AsyncImagePainter.State.Error) failed = true },
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                name.take(1).uppercase().ifBlank { "?" },
                color = RainPlayerText,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 来源服务器小标签（聚合搜索结果上用）。 */
@Composable
fun SourceTag(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xCC000000))
            .border(1.dp, RainPlayerAccent.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(text, color = RainPlayerAccent, fontSize = 9.sp)
    }
}
