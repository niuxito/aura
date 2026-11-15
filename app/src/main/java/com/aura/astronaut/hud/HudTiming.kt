package com.aura.astronaut.hud

/**
 * Parámetros de presentación del HUD. Son valores de demostración, no reglas clínicas
 * ni operativas: viven aquí para no repartir números mágicos por la interfaz.
 */
object HudTiming {
    /**
     * Ventana en la que una actividad del plan se presenta como «toca ahora» desde su MET.
     * El paquete solo trae el MET de inicio, no la duración (cambio pendiente del contrato);
     * a partir del paso 4 la actividad deja de estar pendiente cuando el astronauta la confirma.
     */
    const val NOW_WINDOW_SECONDS: Long = 30 * 60

    /** Una transición gravitatoria se anuncia si ocurre hoy o en los dos días de vuelo siguientes. */
    const val TRANSITION_LOOKAHEAD_DAYS: Int = 2

    /** Cada cuánto rota el panel de misión: tiempo para leer dos líneas sin que se quede fijo. */
    const val MISSION_ROTATION_MS: Long = 6_000

    /** Filas del semáforo visibles a la vez y cada cuánto pasan: el recuento total queda siempre a la vista. */
    const val CAPACITY_ROWS_VISIBLE: Int = 2
    const val CAPACITY_ROTATION_MS: Long = 5_000
}
