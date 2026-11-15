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

/** Minimum height of what the astronaut taps: used with gloves. */
val GloveTouchTarget: Dp = 64.dp

/** Minimum height of the demo mode controls, operated by the presenter. */
val DemoTouchTarget: Dp = 48.dp

/**
 * HUD button. [selected] fills it with the accent color; otherwise it has a border.
 * State is never shown by color alone: the button text says what it does.
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
