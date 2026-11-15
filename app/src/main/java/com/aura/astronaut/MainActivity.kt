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
        // Textos de la interfaz en el idioma de esta actividad (el del sistema o el elegido para la app).
        Strings.init(this)
        vm.setUiLanguage(AppLanguage.current(this))
        enableEdgeToEdge()
        // Un visor no se apaga mientras se mira.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent {
            AuraTheme { AuraScreen() }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Los diálogos del sistema (como el de permisos) vuelven a mostrar las barras al cerrarse.
        if (hasFocus) hideSystemBars()
    }

    /** Visor a pantalla completa: las barras del sistema solo aparecen al deslizar desde el borde. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
