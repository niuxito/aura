package com.aura.astronaut.data

/*
 * Códigos del contrato v5 (en inglés) que la app guarda y envía. Las equivalencias con v4
 * están en contract/v4-to-v5.md; la migración 3→4 de la base local traduce lo ya guardado.
 */

/** Revisión de una adaptación por control de misión (`review.status`). */
object ReviewStatus {
    const val PENDING = "pending"
    const val VALIDATED = "validated"
    const val OBJECTED = "objected"
}

/** Acuse de una alerta por control de misión (`ack.status`). */
object AckStatus {
    const val NEW = "new"
    const val SEEN = "seen"
    const val HANDLED = "handled"
}

/** Tipo y nivel de una alerta (`kind`, `level`). */
object AlertCodes {
    const val KIND_RED = "capacity-red"
    const val KIND_PREP = "preparation-lost"
    const val KIND_CONSISTENCY = "inconsistency"
    const val LEVEL_RED = "red"
    const val LEVEL_WARNING = "warning"
}

/** `source` de adaptaciones y alertas generadas por la interfaz. */
const val SOURCE_INTERFACE = "interface"
