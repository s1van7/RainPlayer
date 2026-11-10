package com.rainplayer.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rainplayer.core.CrashReporter
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.common.CrashReportDialog
import com.rainplayer.ui.theme.RainPlayerTheme

class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RainPlayerTheme {
                val session = remember { AppSession() }
                TvRoot(session)

                var crash by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) { crash = CrashReporter.pending(this@TvActivity) }
                crash?.let { report ->
                    CrashReportDialog(report) { crash = null }
                }
            }
        }
    }
}
