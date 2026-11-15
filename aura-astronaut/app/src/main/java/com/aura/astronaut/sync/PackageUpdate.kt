package com.aura.astronaut.sync

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.mission.ChecksumStatus
import com.aura.astronaut.mission.LoadedPackage
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.MissionPackageParser
import com.aura.astronaut.mission.PackageException

/**
 * Descarga del paquete de misión desde AURA Manager (paso 8 de AGENTS.md).
 * La app nunca se queda sin paquete: si la descarga falla, sigue con el que tiene
 * (el embebido o el último descargado).
 */

/** De dónde sale el paquete cargado. */
sealed interface PackageOrigin {
    data object Embedded : PackageOrigin
    data class Downloaded(val url: String, val atMs: Long) : PackageOrigin
}

/** Resultado de la última comprobación con el Manager. */
sealed interface PackageCheck {
    data object Idle : PackageCheck
    data object Checking : PackageCheck
    /** El Manager publica el mismo paquete que ya está cargado. */
    data class UpToDate(val version: Int) : PackageCheck
    /** Se ha descargado y cargado un paquete distinto. */
    data class Updated(val version: Int) : PackageCheck
    /** El Manager aún no ha publicado ningún paquete (404). */
    data object NonePublished : PackageCheck
    /** El Manager publica un paquete que esta app no lee (por ejemplo, un esquema antiguo). */
    data class Rejected(val reason: String) : PackageCheck
    /** Sin conexión o error del servidor: se reintentará más tarde. */
    data class Offline(val reason: String) : PackageCheck
}

/** Decisión sobre un paquete descargado, sin efectos: se prueba en unitarias. */
sealed interface PackageDecision {
    data class Replace(val loaded: LoadedPackage) : PackageDecision
    data class Keep(val version: Int) : PackageDecision
    data class Reject(val reason: String) : PackageDecision
}

object PackageUpdate {

    /**
     * Qué hacer con el texto descargado frente al paquete cargado:
     * - si no es un v5 utilizable o su checksum no cuadra, se descarta y se sigue con el actual;
     * - si es el mismo (versión y checksum), no se toca nada;
     * - si es distinto, se carga. El Manager elegido es la fuente de verdad, aunque su número de
     *   versión sea menor (cada servidor numera sus publicaciones por separado).
     */
    fun decide(current: MissionPackage?, downloaded: String): PackageDecision {
        val loaded = try {
            MissionPackageParser.parse(downloaded)
        } catch (e: PackageException) {
            return PackageDecision.Reject(e.message ?: str(R.string.pkg_invalid))
        }
        if (loaded.checksum == ChecksumStatus.MISMATCH) {
            return PackageDecision.Reject(str(R.string.pkg_checksum_download))
        }
        val pkg = loaded.pkg
        if (current != null && current.packageVersion == pkg.packageVersion && current.checksum == pkg.checksum) {
            return PackageDecision.Keep(pkg.packageVersion)
        }
        return PackageDecision.Replace(loaded)
    }
}
