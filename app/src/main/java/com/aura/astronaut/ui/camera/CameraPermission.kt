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

/** Situación del permiso de cámara, tal como la necesita la interfaz. */
enum class CameraAccess {
    /** Permiso concedido: la cámara es el fondo del HUD. */
    Granted,
    /** Aún no se ha pedido nunca. */
    NotRequested,
    /** Denegado, pero Android deja volver a pedirlo. */
    Denied,
    /** Denegado de forma permanente: solo se puede conceder desde los ajustes del sistema. */
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

    /** Vuelve a leer el permiso; se llama al volver a la app (por ejemplo, desde los ajustes). */
    fun refresh() {
        access = when {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> CameraAccess.Granted
            !prefs.getBoolean(KEY_REQUESTED, false) -> CameraAccess.NotRequested
            // Tras una negativa, Android solo deja volver a preguntar si muestra la justificación.
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
 * Gestiona el permiso de cámara: lo pide la primera vez, distingue la negativa simple de la
 * permanente y se actualiza al volver a la app.
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

    // Primera vez: se pide sin esperar a que el astronauta toque nada.
    LaunchedEffect(controller) {
        if (controller.access == CameraAccess.NotRequested) controller.request()
    }
    return controller
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("La pantalla de cámara necesita una Activity")
}
