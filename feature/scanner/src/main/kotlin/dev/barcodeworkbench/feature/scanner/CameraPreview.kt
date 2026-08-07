package dev.barcodeworkbench.feature.scanner

import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * CameraX preview bound to the scanner's frame analyser.
 *
 * Analysis runs on its own single-thread executor with a keep-only-latest
 * backpressure strategy: when decoding cannot keep up, discarding intermediate
 * frames beats queueing them, because a stale frame is worth less than the one
 * arriving now.
 */
@Composable
fun CameraPreview(
    torchOn: Boolean,
    onFrame: (ImageProxy) -> Unit,
    onTorchAvailability: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener(
            {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                cameraProvider.unbindAll()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                // A deliberate compromise: enough resolution for
                                // dense 2D symbols and small modules, without the
                                // per-frame cost of full sensor output.
                                ResolutionStrategy(
                                    Size(1280, 720),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                                ),
                            )
                            .build(),
                    )
                    .build()
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor) { image ->
                            useAndClose(image, onFrame)
                        }
                    }

                camera = runCatching {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.getOrNull()

                onTorchAvailability(camera?.cameraInfo?.hasFlashUnit() == true)
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            provider?.unbindAll()
            camera = null
        }
    }

    // Torch goes through camera control on the existing binding. Rebinding the use
    // cases to change it would restart the capture session and visibly stutter the
    // preview.
    LaunchedEffect(camera, torchOn) {
        val bound = camera ?: return@LaunchedEffect
        if (bound.cameraInfo.hasFlashUnit()) {
            bound.cameraControl.enableTorch(torchOn)
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * Closes the frame once the consumer is done.
 *
 * The decoder deliberately does not close it, so ownership is unambiguous. Closing
 * exactly once here keeps the buffer pool from starving, which otherwise shows up
 * as the preview silently freezing.
 */
private inline fun useAndClose(image: ImageProxy, consume: (ImageProxy) -> Unit) {
    try {
        consume(image)
    } finally {
        image.close()
    }
}
