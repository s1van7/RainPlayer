package com.rainplayer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rainplayer.core.CrashReporter
import com.rainplayer.ui.theme.GlassSurface
import com.rainplayer.ui.theme.RainPlayerAccent
import com.rainplayer.ui.theme.RainPlayerSurfaceHigh
import com.rainplayer.ui.theme.RainPlayerText
import com.rainplayer.ui.theme.RainPlayerTextDim

/**
 * 崩溃反馈弹窗。
 *
 * 只有用户点「复制并反馈」才会把日志交出去；点「稍后再说」就清掉，
 * 不做任何后台上报。
 */
@Composable
fun CrashReportDialog(
    report: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        GlassSurface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            level = 2,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "上次运行出了点问题",
                    color = RainPlayerText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "应用异常退出了。把这份日志发给我们，能更快定位原因。",
                    color = RainPlayerTextDim,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))

                // 摘要
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RainPlayerSurfaceHigh)
                        .padding(10.dp)
                ) {
                    Text(
                        CrashReporter.summary(report),
                        color = RainPlayerText,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    if (expanded) "收起详情" else "查看完整日志",
                    color = RainPlayerAccent,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )

                if (expanded) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RainPlayerSurfaceHigh)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            report,
                            color = RainPlayerTextDim,
                            fontSize = 9.5.sp,
                            lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, RainPlayerTextDim.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable {
                                CrashReporter.clear(context)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("稍后再说", color = RainPlayerTextDim, fontSize = 13.sp)
                    }
                    Box(
                        Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(RainPlayerAccent)
                            .clickable {
                                CrashReporter.copy(context, report)
                                CrashReporter.share(context, report)
                                CrashReporter.clear(context)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "复制并反馈",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "日志只包含版本号、机型、系统版本和错误堆栈，不含服务器地址、账号或播放记录。",
                    color = RainPlayerTextDim,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                )
            }
        }
    }
}
