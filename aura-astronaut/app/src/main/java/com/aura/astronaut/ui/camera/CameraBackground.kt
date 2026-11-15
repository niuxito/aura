package com.aura.astronaut.ui.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aura.astronaut.ui.theme.HudColors

/** Estado de la imagen de fondo, para que el HUD pueda explicarlo. */
enum class CameraFeed { Starting, Live, NoBackCamera, Failed }

private const val TAG = "AuraCamera"

/**
 * Imagen de la cámara trasera a pantalla completa. Solo se usa como fondo del HUD:
 * no se analizan los fotogramas ni se infiere nada de lo que se ve.
 */
@Composable
fun CameraPreview(onFeedChange: (CameraFeed) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // COMPATIBLE dibuja en un TextureView: la imagen respeta el orden de capas de Compose.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(lifecycleOwner) {
        onFeedChange(CameraFeed.Starting)
        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val hasBack = runCatching { provider.hasCamera(selector) }.getOrDefault(false)
        if (!hasBack) {
            onFeedChange(CameraFeed.NoBackCamera)
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview)
            onFeedChange(CameraFeed.Live)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir la cámara trasera", e)
            onFeedChange(CameraFeed.Failed)
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
}

/** Fondo neutro cuando no hay imagen de cámara: el HUD sigue siendo legible y útil. */
@Composable
fun NeutralBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(HudColors.SpaceMid, HudColors.SpaceDeep))),
    )
}
