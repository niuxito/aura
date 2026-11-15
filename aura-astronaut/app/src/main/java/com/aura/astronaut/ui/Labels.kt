package com.aura.astronaut.ui

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.mission.BlockKind
import com.aura.astronaut.mission.Category
import com.aura.astronaut.mission.ChecksumStatus
import com.aura.astronaut.mission.EventKind
import com.aura.astronaut.mission.Gravity
import com.aura.astronaut.mission.Lang
import com.aura.astronaut.mission.LoadLevel
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/* Texts for the contract values, aligned with AURA Manager's labels (lib/i18n). */

val Gravity.label: String
    get() = str(
        when (this) {
            Gravity.EARTH -> R.string.gravity_earth
            Gravity.MARS -> R.string.gravity_mars
            Gravity.MOON -> R.string.gravity_moon
            Gravity.MICRO -> R.string.gravity_micro
        },
    )

val Gravity.shortLabel: String
    get() = if (this == Gravity.MICRO) str(R.string.gravity_micro_short) else label

/** Gravity relative to Earth as published by the package (`gravityReference`): "1 g", "0.165 g". */
fun gravityValue(reference: Map<Gravity, Double>, gravity: Gravity): String? {
    val g = reference[gravity] ?: return null
    val format = DecimalFormat("0.###", DecimalFormatSymbols(Locale.forLanguageTag(Lang.active)))
    val prefix = if (gravity == Gravity.MICRO && g == 0.0) "≈" else ""
    return "$prefix${format.format(g)} g"
}

fun gravityText(reference: Map<Gravity, Double>, gravity: Gravity): String =
    gravityValue(reference, gravity)?.let { "${gravity.label} · $it" } ?: gravity.label

val LoadLevel.label: String
    get() = str(
        when (this) {
            LoadLevel.LOW -> R.string.load_low
            LoadLevel.MEDIUM -> R.string.load_medium
            LoadLevel.HIGH -> R.string.load_high
            LoadLevel.CRITICAL -> R.string.load_critical
        },
    )

val ChecksumStatus.label: String
    get() = str(
        when (this) {
            ChecksumStatus.VERIFIED -> R.string.checksum_verified
            ChecksumStatus.MISMATCH -> R.string.checksum_mismatch
            ChecksumStatus.MISSING -> R.string.checksum_missing
        },
    )

val BlockKind.label: String
    get() = str(
        when (this) {
            BlockKind.SLEEP -> R.string.block_sleep
            BlockKind.PRE_SLEEP -> R.string.block_presleep
            BlockKind.MEAL -> R.string.block_meal
            BlockKind.EXERCISE -> R.string.block_exercise
            BlockKind.MANEUVER -> R.string.block_maneuver
            BlockKind.TEST -> R.string.block_test
            BlockKind.OPERATION -> R.string.block_operation
            BlockKind.COMMUNICATION -> R.string.block_communication
            BlockKind.HEALTH -> R.string.block_health
            BlockKind.FREE -> R.string.block_free
        },
    )

val EventKind.label: String
    get() = str(
        when (this) {
            EventKind.MANEUVER -> R.string.event_maneuver
            EventKind.MILESTONE -> R.string.event_milestone
            EventKind.TEST -> R.string.event_test
            EventKind.OPERATION -> R.string.event_operation
            EventKind.EVA -> R.string.event_eva
        },
    )

val Category.label: String
    get() = str(
        when (this) {
            Category.EXERCISE -> R.string.category_exercise
            Category.NUTRITION -> R.string.category_nutrition
            Category.ASSESSMENT -> R.string.category_assessment
            Category.SUPPLEMENT -> R.string.category_supplement
            Category.RECOVERY -> R.string.category_recovery
            Category.PREPARATION -> R.string.category_preparation
        },
    )
