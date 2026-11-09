package com.rainplayer.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.rainplayer.ui.AppSession
import com.rainplayer.ui.theme.RainPlayerTheme

class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RainPlayerTheme {
                val session = remember { AppSession() }
                TvRoot(session)
            }
        }
    }
}
