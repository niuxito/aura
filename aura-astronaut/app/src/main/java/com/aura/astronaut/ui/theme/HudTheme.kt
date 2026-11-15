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
 * Viewer palette. Color never goes alone in the HUD: it always accompanies a text
 * that says the same (green/amber/red/gray in the traffic light carry a label and a reason).
 */
object HudColors {
    /** General information and mission context. */
    val Cyan = Color(0xFF5CE1F0)
    /** Caution and viewer frames. */
    val Amber = Color(0xFFFFB547)
    /** Alert. */
    val Red = Color(0xFFFF5A3C)
    /** Traffic light: no deviation. */
    val Green = Color(0xFF5BE38A)
    /** Traffic light: insufficient data. */
    val Gray = Color(0xFFA7B0B5)
    val Text = Color(0xFFF2F7F8)
    val TextDim = Color(0xFFB9C6CA)
    /** Semi-transparent panel background: lets the camera show through and keeps text contrast. */
    val Panel = Color(0x8C07141B)
    /** Modal panel background: almost opaque, so the HUD behind does not show through. */
    val PanelModal = Color(0xF5050D12)
    /** Background when there is no camera image. */
    val SpaceDeep = Color(0xFF02070B)
    val SpaceMid = Color(0xFF0A1E2A)
}

/** Condensed system typeface, like a viewer's; available without network or extra files. */
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

/** HUD box: semi-transparent background with a colored border, as in the viewer mockups. */
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
