package com.aura.astronaut.ui.crew

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.astronaut.mission.CrewMember
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText

/** First start: each viewer belongs to a crew member, and their plan depends on it. */
@Composable
fun CrewPicker(
    crew: List<CrewMember>,
    simulated: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HudPanel(modifier.widthIn(max = 640.dp), modal = true) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(str(R.string.crew_pick_title), style = HudText.Title, color = HudColors.Cyan)
            Text(
                str(R.string.crew_pick_body),
                style = HudText.Body,
                color = HudColors.Text,
            )
            Spacer(Modifier.height(4.dp))
            crew.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { member ->
                        HudButton(
                            text = member.name.uppercase(),
                            onClick = { onSelect(member.id) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (simulated) {
                Text(
                    str(R.string.crew_pick_demo),
                    style = HudText.Label,
                    color = HudColors.Amber,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
