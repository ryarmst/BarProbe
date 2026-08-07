package dev.barcodeworkbench.core.designsystem.component

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.barcodeworkbench.core.designsystem.icon.WorkbenchIcons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.barcodeworkbench.barcode.render.BitmapSymbolRenderer
import dev.barcodeworkbench.barcode.render.RenderSpec
import dev.barcodeworkbench.barcode.render.SymbolRotation
import dev.barcodeworkbench.core.model.ModuleMatrix
import kotlin.math.max

/**
 * Full-screen symbol viewer.
 *
 * Built for the case where the symbol is being read off the display by a hardware
 * scanner or another phone, which drives most of the behaviour here: screen
 * brightness is forced to maximum, the screen is kept awake, and the background is
 * always true white regardless of theme. A dark-themed or dimmed barcode is
 * measurably harder to decode.
 *
 * Rotation is offered manually as well as by turning the device, because long
 * linear symbols are far wider than tall and fit the screen much better rotated.
 */
@Composable
fun SymbolViewerDialog(
    matrix: ModuleMatrix,
    title: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        SymbolViewerContent(matrix = matrix, title = title, onDismiss = onDismiss)
    }
}

@Composable
private fun SymbolViewerContent(
    matrix: ModuleMatrix,
    title: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Maximum brightness and screen-on for as long as the viewer is up, restored
    // on the way out so the user's setting is not permanently changed.
    DisposableEffect(activity) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness
        window?.let { w ->
            w.attributes = w.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowInsetsControllerCompat(w, w.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = previousBrightness
                        ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(w, w.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var rotation by remember { mutableStateOf(initialRotation(matrix)) }
    var showQuietZone by remember { mutableStateOf(true) }
    var showHrt by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val renderer = remember { BitmapSymbolRenderer() }
    val bitmap = remember(matrix, rotation, showQuietZone, showHrt) {
        renderer.render(
            matrix,
            RenderSpec(
                // Generous module size: the viewer is the one place where physical
                // size on glass directly determines whether a scanner can read it.
                modulePx = 12,
                quietZoneModules = if (showQuietZone) 4 else 0,
                includeHrt = showHrt,
                rotation = rotation,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            // Snap back so the symbol cannot be left stranded
                            // off-screen at 1x.
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            FilledTonalIconButton(onClick = onDismiss) {
                Icon(WorkbenchIcons.Close, contentDescription = "Close viewer")
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { rotation = rotation.nextQuarterTurn() },
                ) {
                    Icon(WorkbenchIcons.Refresh, contentDescription = "Rotate 90 degrees")
                }
                if (scale != 1f) {
                    FilledTonalIconButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                    ) {
                        Text("1:1")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = showQuietZone,
                onClick = { showQuietZone = !showQuietZone },
                label = { Text("Quiet zone") },
            )
            if (!matrix.hrt.isNullOrEmpty()) {
                FilterChip(
                    selected = showHrt,
                    onClick = { showHrt = !showHrt },
                    label = { Text("Caption") },
                )
            }
        }
    }
}

/**
 * A symbol much wider than it is tall starts rotated, since portrait screens are
 * the common case and a long linear barcode otherwise shrinks to illegibility.
 */
private fun initialRotation(matrix: ModuleMatrix): SymbolRotation {
    val aspect = matrix.width.toFloat() / max(1f, matrix.totalHeightUnits)
    return if (aspect > WIDE_ASPECT_THRESHOLD) {
        SymbolRotation.CLOCKWISE_90
    } else {
        SymbolRotation.NONE
    }
}

private fun SymbolRotation.nextQuarterTurn(): SymbolRotation = when (this) {
    SymbolRotation.NONE -> SymbolRotation.CLOCKWISE_90
    SymbolRotation.CLOCKWISE_90 -> SymbolRotation.HALF_TURN
    SymbolRotation.HALF_TURN -> SymbolRotation.COUNTER_CLOCKWISE_90
    SymbolRotation.COUNTER_CLOCKWISE_90 -> SymbolRotation.NONE
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 8f
private const val WIDE_ASPECT_THRESHOLD = 2.5f
