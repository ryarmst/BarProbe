package dev.barcodeworkbench.feature.generator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.barcode.render.ExportFormat
import dev.barcodeworkbench.barcode.render.RenderSpec
import dev.barcodeworkbench.barcode.render.SymbolExporter
import dev.barcodeworkbench.core.model.ModuleMatrix
import kotlin.math.roundToInt

/**
 * Export options and destination picker.
 *
 * Writing goes through the Storage Access Framework, so the user chooses the
 * destination and the app needs no storage permission at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    matrix: ModuleMatrix,
    symbologyName: String,
    payloadHint: String,
    exporter: SymbolExporter,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var format by remember { mutableStateOf(ExportFormat.PNG) }
    var modulePx by remember { mutableFloatStateOf(DEFAULT_MODULE_PX) }
    var includeQuietZone by remember { mutableStateOf(true) }
    var includeHrt by remember { mutableStateOf(true) }
    var transparent by remember { mutableStateOf(false) }

    val spec = RenderSpec(
        modulePx = modulePx.roundToInt().coerceAtLeast(1),
        quietZoneModules = if (includeQuietZone) 4 else 0,
        includeHrt = includeHrt,
        backgroundColor = if (transparent) {
            RenderSpec.COLOR_TRANSPARENT
        } else {
            RenderSpec.COLOR_WHITE
        },
    )

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(format.mimeType),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                exporter.write(matrix, spec, format, stream)
            }
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Export", style = MaterialTheme.typography.titleLarge)

            Text(
                text = "Format",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportFormat.recommended.forEach { candidate ->
                    FilterChip(
                        selected = format == candidate,
                        onClick = { format = candidate },
                        label = { Text(candidate.displayName) },
                    )
                }
            }

            if (format.isLossy) {
                // Compression artefacts land on exactly the high-contrast edges a
                // decoder relies on, so this warning is worth the space.
                Text(
                    text = "${format.displayName} is lossy. Its artefacts fall on module " +
                        "edges and can make the symbol harder to scan. Prefer PNG or SVG.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (!format.isVector) {
                Text(
                    text = "Module size: ${modulePx.roundToInt()} px",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = modulePx,
                    onValueChange = { modulePx = it },
                    valueRange = 1f..32f,
                    steps = 30,
                )
            }

            if (format == ExportFormat.JPEG && transparent) {
                Text(
                    text = "JPEG cannot store transparency; a white background will be used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = includeQuietZone,
                    onClick = { includeQuietZone = !includeQuietZone },
                    label = { Text("Quiet zone") },
                )
                if (!matrix.hrt.isNullOrEmpty()) {
                    FilterChip(
                        selected = includeHrt,
                        onClick = { includeHrt = !includeHrt },
                        label = { Text("Caption") },
                    )
                }
                FilterChip(
                    selected = transparent,
                    onClick = { transparent = !transparent },
                    label = { Text("Transparent") },
                )
            }

            Button(
                onClick = {
                    createDocument.launch(
                        exporter.suggestFileName(symbologyName, payloadHint, format),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Choose destination…")
            }
        }
    }
}

private const val DEFAULT_MODULE_PX = 10f
