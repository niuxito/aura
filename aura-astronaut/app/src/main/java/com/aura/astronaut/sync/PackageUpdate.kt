package com.aura.astronaut.sync

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import com.aura.astronaut.mission.ChecksumStatus
import com.aura.astronaut.mission.LoadedPackage
import com.aura.astronaut.mission.MissionPackage
import com.aura.astronaut.mission.MissionPackageParser
import com.aura.astronaut.mission.PackageException

/**
 * Download of the mission package from AURA Manager (step 8 of AGENTS.md).
 * The app is never left without a package: if the download fails, it keeps the one it has
 * (the embedded one or the last downloaded).
 */

/** Where the loaded package comes from. */
sealed interface PackageOrigin {
    data object Embedded : PackageOrigin
    data class Downloaded(val url: String, val atMs: Long) : PackageOrigin
}

/** Result of the last check with the Manager. */
sealed interface PackageCheck {
    data object Idle : PackageCheck
    data object Checking : PackageCheck
    /** The Manager publishes the same package that is already loaded. */
    data class UpToDate(val version: Int) : PackageCheck
    /** A different package was downloaded and loaded. */
    data class Updated(val version: Int) : PackageCheck
    /** The Manager has not published any package yet (404). */
    data object NonePublished : PackageCheck
    /** The Manager publishes a package this app cannot read (for example, an old schema). */
    data class Rejected(val reason: String) : PackageCheck
    /** Offline or server error: it will be retried later. */
    data class Offline(val reason: String) : PackageCheck
}

/** Decision about a downloaded package, with no side effects: unit-tested. */
sealed interface PackageDecision {
    data class Replace(val loaded: LoadedPackage) : PackageDecision
    data class Keep(val version: Int) : PackageDecision
    data class Reject(val reason: String) : PackageDecision
}

object PackageUpdate {

    /**
     * What to do with the downloaded text compared with the loaded package:
     * - if it is not a usable v5 or its checksum does not match, it is discarded and the current one is kept;
     * - if it is the same (version and checksum), nothing is touched;
     * - if it is different, it is loaded. The chosen Manager is the source of truth, even if its version
     *   number is lower (each server numbers its publications separately).
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
