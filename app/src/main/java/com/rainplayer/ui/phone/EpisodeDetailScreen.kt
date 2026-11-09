package com.rainplayer.ui.phone

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rainplayer.data.Episode
import com.rainplayer.player.PlayerActivity
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.common.ErrorView
import com.rainplayer.ui.common.LoadingView
import com.rainplayer.ui.common.RainPlayerButton
import com.rainplayer.ui.common.imageRequest
import com.rainplayer.ui.theme.GlassSurface
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim
import com.rainplayer.ui.theme.scrimBrush

/**
 * 单集详情页。
 *
 * 剧集列表里点某一集不再直接开播，而是先进这一页——可以看到这一集的剧照、
 * 简介、播出时间，也方便「先看看这集讲什么再决定要不要看」。
 */
@Composable
fun EpisodeDetailScreen(
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

    LaunchedEffect(episodeId, backend) {
        if (backend == null) return@LaunchedEffect
        loading = true
        runCatching { backend.episode(episodeId) }
            .onSuccess { episode = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }

    fun play(ep: Episode, fromStart: Boolean) {
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra(PlayerActivity.EXTRA_ITEM_ID, ep.id)
                .putExtra(PlayerActivity.EXTRA_TITLE, ep.title)
                .putExtra(PlayerActivity.EXTRA_SUBTITLE, ep.label)
                // 0 = 接着上次看；-1 = 强制从头（后端约定）
                .putExtra(PlayerActivity.EXTRA_START_MS, if (fromStart) -1L else 0L)
        )
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> LoadingView()
            error != null -> ErrorView(error!!) { }
            episode == null -> ErrorView("找不到这一集") { }
            else -> {
                val ep = episode!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 剧照
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = imageRequest(ep.backdropUrl ?: ep.imageUrl),
                            contentDescription = ep.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(Modifier.fillMaxSize().background(scrimBrush(0.9f)))
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x66000000))
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            Text(
                                ep.label,
                                color = RainPlayerAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ep.title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RainPlayerButton(
                                if (ep.progress > 0.01f) "继续播放" else "播放",
                                Modifier.weight(1f),
                            ) { play(ep, fromStart = false) }
                            if (ep.progress > 0.01f) {
                                RainPlayerButton("从头播放", Modifier.weight(1f), primary = false) {
                                    play(ep, fromStart = true)
                                }
                            }
                        }

                        if (ep.progress > 0.01f) {
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x22FFFFFF))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(ep.progress.coerceIn(0f, 1f))
                                        .height(3.dp)
                                        .background(RainPlayerAccent)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("本集信息", color = RainPlayerText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                InfoLine("所属剧集", ep.seriesName ?: fallbackTitle)
                                InfoLine("季集", ep.label)
                                ep.airDate?.let { InfoLine("播出日期", it) }
                                if (ep.played) InfoLine("观看状态", "已看完")
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("简介", color = RainPlayerText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            ep.overview?.takeIf { it.isNotBlank() } ?: "这一集还没有简介",
                            color = RainPlayerTextDim,
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                        )

                        ep.seriesId?.let { sid ->
                            Spacer(Modifier.height(20.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x14FFFFFF))
                                    .clickable { onOpenSeries(sid, ep.seriesName ?: fallbackTitle) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = RainPlayerAccent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "查看整部剧集",
                                    color = RainPlayerText,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("›", color = RainPlayerTextDim, fontSize = 16.sp)
                            }
                        }

                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = RainPlayerTextDim, fontSize = 11.sp, modifier = Modifier.width(72.dp))
        Text(value, color = RainPlayerText, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}
