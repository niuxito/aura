package com.aura.astronaut.ui.hud

import com.aura.astronaut.i18n.str
import com.aura.astronaut.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.astronaut.heart.FoundWatch
import com.aura.astronaut.heart.HeartSource
import com.aura.astronaut.heart.HeartState
import com.aura.astronaut.heart.WatchStatus
import com.aura.astronaut.ui.theme.HudButton
import com.aura.astronaut.ui.theme.HudColors
import com.aura.astronaut.ui.theme.HudPanel
import com.aura.astronaut.ui.theme.HudText

/** Watch panel actions. */
interface WatchActions {
    fun setSource(source: HeartSource)
    fun requestPermission()
    fun openBluetoothSettings()
    fun search()
    fun stopSearch()
    fun choose(address: String)
    fun forget()
}

/** Heart rate setup: source, watch search and how to enable broadcasting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchPanel(
    heart: HeartState,
    source: HeartSource,
    found: List<FoundWatch>,
    actions: WatchActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HudPanel(modifier.widthIn(max = 820.dp), modal = true) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(str(R.string.watch_title), style = HudText.Title, color = HudColors.Cyan, modifier = Modifier.weight(1f))
                HudButton(str(R.string.close), onClose)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(HeartSource.WATCH to str(R.string.heart_source_watch), HeartSource.SIMULATED to str(R.string.heart_source_simulated), HeartSource.OFF to str(R.string.heart_source_off)).forEach { (s, label) ->
                    HudButton(label, { actions.setSource(s) }, selected = source == s)
                }
            }
            when (source) {
                HeartSource.SIMULATED -> Note(str(R.string.watch_simulated_note))
                HeartSource.OFF -> Note(str(R.string.watch_off_note))
                HeartSource.WATCH -> {
                    Text(
                        str(R.string.watch_status, watchStatusText(heart.status)) + (heart.deviceName?.let { " · $it" } ?: ""),
                        style = HudText.Body,
                        color = if (heart.status == WatchStatus.CONNECTED) HudColors.Green else HudColors.Amber,
                    )
                    when (heart.status) {
                        WatchStatus.NO_PERMISSION -> HudButton(str(R.string.watch_allow_bt), actions::requestPermission, selected = true)
                        WatchStatus.BLUETOOTH_OFF -> HudButton(str(R.string.watch_enable_bt), actions::openBluetoothSettings, selected = true)
                        WatchStatus.UNAVAILABLE -> Note(str(R.string.watch_no_ble))
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (heart.status == WatchStatus.SCANNING) {
                                HudButton(str(R.string.watch_stop_search), actions::stopSearch)
                            } else {
                                HudButton(str(R.string.watch_search), actions::search, selected = heart.status == WatchStatus.NOT_PAIRED)
                            }
                            if (heart.status != WatchStatus.NOT_PAIRED && heart.status != WatchStatus.SCANNING) {
                                HudButton(str(R.string.watch_forget), actions::forget, accent = HudColors.Gray)
                            }
                        }
                    }
                    if (found.isNotEmpty()) {
                        Text(str(R.string.watch_found), style = HudText.Label, color = HudColors.Cyan)
                        found.forEach { w ->
                            HudButton("${w.name}  ·  ${w.rssi} dBm", { actions.choose(w.address) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Note(
                        str(R.string.watch_garmin_help),
                    )
                }
            }
            Text(
                str(R.string.watch_disclaimer),
                style = HudText.Label.copy(fontSize = 12.sp, letterSpacing = 0.3.sp),
                color = HudColors.TextDim,
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = HudText.Body.copy(fontSize = 16.sp), color = HudColors.TextDim)
}
