package com.rainplayer.ui

import android.app.UiModeManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rainplayer.ui.phone.PhoneRoot
import com.rainplayer.ui.theme.RainPlayerTheme
import com.rainplayer.ui.theme.SystemBarsState
import com.rainplayer.ui.theme.rainPalette
import com.rainplayer.ui.tv.TvActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isTelevision()) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            RainPlayerTheme {
                // 状态栏 / 导航栏图标颜色跟着明暗走：浅色主题用深色图标
                val palette = rainPalette
                SideEffect {
                    // 顶部压着大图时用白色图标，其余情况跟着主题明暗走
                    applyBarAppearance(palette.isLight && !SystemBarsState.overImage)
                }

                val session = remember { AppSession() }
                PhoneRoot(session)
            }
        }
    }

    /** 内容铺到系统栏下面（沉浸式），系统栏本身全透明。 */
    private fun enableEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun applyBarAppearance(light: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
    }

    private fun isTelevision(): Boolean {
        val uiMode = getSystemService(UI_MODE_SERVICE) as? UiModeManager
        val isTvMode = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return isTvMode || (hasLeanback && !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
    }
}
