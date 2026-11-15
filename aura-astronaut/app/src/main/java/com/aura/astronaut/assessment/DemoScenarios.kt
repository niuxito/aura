package com.aura.astronaut.assessment

import androidx.annotation.StringRes
import com.aura.astronaut.R

/**
 * Demo scenarios: **simulated** signal values, plausible and consistent with each other.
 * They are nobody's data and not clinical criteria. They are stored with source `simulated` and the HUD says so.
 * Only signals present in the loaded package are used; if the Manager changes the ids, they are ignored.
 */
enum class DemoScenario(@StringRes val label: Int, @StringRes val description: Int, val values: Map<String, Double>) {
    STABLE(
        R.string.scenario_stable,
        R.string.scenario_stable_desc,
        mapOf(
            "resting_hr_delta" to 2.0,
            "slow_hr_recovery" to 0.0,
            "night_hrv_delta" to -4.0,
            "resp_rate_delta" to 5.0,
            "spo2" to 97.0,
            "ecg_arrhythmia" to 0.0,
            "emg_fatigue" to 8.0,
            "coordination_delta" to 3.0,
        ),
    ),
    FATIGUE(
        R.string.scenario_fatigue,
        R.string.scenario_fatigue_desc,
        mapOf(
            "resting_hr_delta" to 11.0,
            "slow_hr_recovery" to 1.0,
            "night_hrv_delta" to -22.0,
            "resp_rate_delta" to 14.0,
            "spo2" to 96.0,
            "ecg_arrhythmia" to 0.0,
            "emg_fatigue" to 26.0,
            "coordination_delta" to 9.0,
        ),
    ),
    COORDINATION(
        R.string.scenario_coordination,
        R.string.scenario_coordination_desc,
        mapOf(
            "resting_hr_delta" to 3.0,
            "slow_hr_recovery" to 0.0,
            "night_hrv_delta" to -6.0,
            "resp_rate_delta" to 6.0,
            "spo2" to 97.0,
            "ecg_arrhythmia" to 0.0,
            "emg_fatigue" to 10.0,
            "coordination_delta" to 34.0,
        ),
    ),
}

/** Scenario stored in `quality` (by name; earlier versions stored the Spanish label, still accepted). */
fun scenarioOf(stored: String): DemoScenario? =
    DemoScenario.entries.firstOrNull { it.name == stored } ?: LEGACY_SCENARIO_LABELS[stored]

private val LEGACY_SCENARIO_LABELS = mapOf(
    "Perfil estable" to DemoScenario.STABLE,
    "Fatiga acumulada" to DemoScenario.FATIGUE,
    "Empeora la coordinación" to DemoScenario.COORDINATION,
)
