package com.rain.player.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.UiModeManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.rain.player.ui.phone.PhoneRoot
import com.rain.player.ui.theme.RainTheme
import com.rain.player.ui.tv.TvActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isTelevision()) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }
        setContent {
            RainTheme {
                val session = remember { AppSession() }
                PhoneRoot(session)
            }
        }
    }

    private fun isTelevision(): Boolean {
        val uiMode = getSystemService(UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return isTvMode || (hasLeanback && !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
    }
}
