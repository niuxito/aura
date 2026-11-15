package com.aura.astronaut.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Camera permission situation, as the interface needs it. */
enum class CameraAccess {
    /** Permission granted: the camera is the HUD background. */
    Granted,
    /** Never requested yet. */
    NotRequested,
    /** Denied, but Android allows asking again. */
    Denied,
    /** Permanently denied: it can only be granted from system settings. */
    PermanentlyDenied,
}

@Stable
class CameraPermissionController internal constructor(
    private val activity: Activity,
    private val launch: () -> Unit,
) {
    var access by mutableStateOf(CameraAccess.NotRequested)
        private set

    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Reads the permission again; called when returning to the app (for example, from settings). */
    fun refresh() {
        access = when {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> CameraAccess.Granted
            !prefs.getBoolean(KEY_REQUESTED, false) -> CameraAccess.NotRequested
            // After a refusal, Android only allows asking again if it shows the rationale.
            activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> CameraAccess.Denied
            else -> CameraAccess.PermanentlyDenied
        }
    }

    fun request() {
        prefs.edit { putBoolean(KEY_REQUESTED, true) }
        launch()
    }

    fun openAppSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    internal fun onResult() = refresh()

    private companion object {
        const val PREFS = "camera_permission"
        const val KEY_REQUESTED = "requested"
    }
}

/**
 * Manages the camera permission: requests it the first time, tells a simple refusal from a
 * permanent one and updates when returning to the app.
 */
@Composable
fun rememberCameraPermission(): CameraPermissionController {
    val activity = LocalContext.current.findActivity()
    var controller: CameraPermissionController? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller?.onResult()
    }
    controller = remember(activity) {
        CameraPermissionController(activity) { launcher.launch(Manifest.permission.CAMERA) }
            .also { it.refresh() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.refresh() }

    // First time: it is requested without waiting for the astronaut to touch anything.
    LaunchedEffect(controller) {
        if (controller.access == CameraAccess.NotRequested) controller.request()
    }
    return controller
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("The camera screen needs an Activity")
}
