package com.aura.astronaut

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.aura.astronaut.i18n.AppLanguage
import com.aura.astronaut.i18n.Strings
import com.aura.astronaut.state.AuraViewModel
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aura.astronaut.ui.AuraScreen
import com.aura.astronaut.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    private val vm: AuraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Interface texts in this activity's language (the system's or the one chosen for the app).
        Strings.init(this)
        vm.setUiLanguage(AppLanguage.current(this))
        enableEdgeToEdge()
        // A viewer does not turn off while being looked at.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent {
            AuraTheme { AuraScreen() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // System dialogs (such as the permission one) show the bars again when closed.
        if (hasFocus) hideSystemBars()
    }

    /** Full-screen viewer: the system bars only appear when swiping from the edge. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
