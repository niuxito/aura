package com.aura.astronaut.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Altura mínima de lo que pulsa el astronauta: se usa con guantes. */
val GloveTouchTarget: Dp = 64.dp

/** Altura mínima de los controles del modo demo, que maneja quien presenta. */
val DemoTouchTarget: Dp = 48.dp

/**
 * Botón del HUD. [selected] lo rellena con el color de acento; si no, va con borde.
 * El estado nunca se indica solo con color: el texto del botón dice lo que hace.
 */
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = HudColors.Cyan,
    selected: Boolean = false,
    enabled: Boolean = true,
    minHeight: Dp = GloveTouchTarget,
) {
    val shape = RoundedCornerShape(8.dp)
    val padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = minHeight),
            shape = shape,
            contentPadding = padding,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
        ) { Text(text, style = HudText.Label, textAlign = TextAlign.Center) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = minHeight),
            shape = shape,
            contentPadding = padding,
            border = BorderStroke(1.5.dp, if (enabled) accent.copy(alpha = 0.8f) else HudColors.Gray.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = HudColors.Panel,
                contentColor = HudColors.Text,
                disabledContentColor = HudColors.Gray,
            ),
        ) { Text(text, style = HudText.Label, textAlign = TextAlign.Center) }
    }
}
