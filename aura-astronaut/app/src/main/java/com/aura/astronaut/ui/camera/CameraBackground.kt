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

/** State of the background image, so the HUD can explain it. */
enum class CameraFeed { Starting, Live, NoBackCamera, Failed }

private const val TAG = "AuraCamera"

/**
 * Full-screen rear camera image. Used only as the HUD background:
 * frames are not analyzed and nothing is inferred from what is seen.
 */
@Composable
fun CameraPreview(onFeedChange: (CameraFeed) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // COMPATIBLE draws into a TextureView: the image respects Compose's layer order.
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
            Log.e(TAG, "Could not open the rear camera", e)
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

/** Neutral background when there is no camera image: the HUD remains readable and useful. */
@Composable
fun NeutralBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(HudColors.SpaceMid, HudColors.SpaceDeep))),
    )
}
