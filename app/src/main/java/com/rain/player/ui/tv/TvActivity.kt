package com.rain.player.ui.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.rain.player.ui.AppSession
import com.rain.player.ui.theme.RainTheme

class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RainTheme {
                val session = remember { AppSession() }
                TvRoot(session)
            }
        }
    }
}
