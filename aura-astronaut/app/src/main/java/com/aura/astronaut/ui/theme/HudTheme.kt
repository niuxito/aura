package com.aura.astronaut.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta del visor. El color nunca va solo en el HUD: siempre acompaña a un texto
 * que dice lo mismo (verde/ámbar/rojo/gris del semáforo llevan etiqueta y motivo).
 */
object HudColors {
    /** Información general y contexto de misión. */
    val Cyan = Color(0xFF5CE1F0)
    /** Precaución y marcos del visor. */
    val Amber = Color(0xFFFFB547)
    /** Alerta. */
    val Red = Color(0xFFFF5A3C)
    /** Semáforo: sin desviación. */
    val Green = Color(0xFF5BE38A)
    /** Semáforo: datos insuficientes. */
    val Gray = Color(0xFFA7B0B5)
    val Text = Color(0xFFF2F7F8)
    val TextDim = Color(0xFFB9C6CA)
    /** Fondo de panel semitransparente: deja ver la cámara y mantiene el contraste del texto. */
    val Panel = Color(0x8C07141B)
    /** Fondo de los paneles modales: casi opaco, para que no se transparente el HUD de detrás. */
    val PanelModal = Color(0xF5050D12)
    /** Fondo cuando no hay imagen de cámara. */
    val SpaceDeep = Color(0xFF02070B)
    val SpaceMid = Color(0xFF0A1E2A)
}

/** Tipografía condensada del sistema, como la de un visor; disponible sin red ni ficheros extra. */
val HudFontFamily: FontFamily = FontFamily(
    Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)),
)

object HudText {
    val Title = TextStyle(fontFamily = HudFontFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 0.5.sp)
    val Body = TextStyle(fontFamily = HudFontFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp)
    val Label = TextStyle(fontFamily = HudFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
}

@Composable
fun AuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HudColors.Cyan,
            secondary = HudColors.Amber,
            error = HudColors.Red,
            background = HudColors.SpaceDeep,
            surface = HudColors.Panel,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = HudColors.Text,
            onSurface = HudColors.Text,
        ),
        typography = Typography(
            bodyLarge = HudText.Body,
            titleLarge = HudText.Title,
            labelLarge = HudText.Label,
        ),
        content = content,
    )
}

/** Recuadro del HUD: fondo semitransparente con borde de color, como en las maquetas del visor. */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    accent: Color = HudColors.Cyan,
    modal: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .background(if (modal) HudColors.PanelModal else HudColors.Panel, shape)
            .border(BorderStroke(2.dp, accent.copy(alpha = 0.85f)), shape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        content = content,
    )
}
