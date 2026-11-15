package com.aura.astronaut.hud

/**
 * HUD presentation parameters. They are demonstration values, not clinical or operational
 * rules: they live here so that magic numbers are not spread across the interface.
 */
object HudTiming {
    /**
     * Window in which a plan activity is presented as "due now" from its MET, when the package
     * gives no duration or end (older packages); from step 4 on, the activity stops being pending
     * once the astronaut confirms it.
     */
    const val NOW_WINDOW_SECONDS: Long = 30 * 60

    /** A gravity transition is announced if it happens today or in the next two flight days. */
    const val TRANSITION_LOOKAHEAD_DAYS: Int = 2

    /** How often the mission panel rotates: time to read two lines without it staying fixed. */
    const val MISSION_ROTATION_MS: Long = 6_000

    /** Traffic-light rows visible at once and how often they scroll: the total count always stays visible. */
    const val CAPACITY_ROWS_VISIBLE: Int = 2
    const val CAPACITY_ROTATION_MS: Long = 5_000
}
