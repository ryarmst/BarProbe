package dev.barcodeworkbench.feature.configpacks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.barcode.engine.EncodeRequest
import dev.barcodeworkbench.barcode.engine.EncodeResult
import dev.barcodeworkbench.barcode.render.BitmapSymbolRenderer
import dev.barcodeworkbench.barcode.render.RenderSpec
import dev.barcodeworkbench.core.designsystem.component.SymbolViewerDialog
import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.config.ConfigEntry
import dev.barcodeworkbench.core.model.config.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One programming barcode, with the caution the situation warrants.
 *
 * Scanning a symbol from this screen changes hardware state, so the symbol is not
 * rendered until the user has had a chance to read where the value came from. For
 * anything destructive or not verified against primary documentation, an explicit
 * acknowledgement is required first. The intent is not to nag but to make the one
 * genuinely irreversible action in the app deliberate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEntrySheet(
    entry: ConfigEntry,
    encoder: BarcodeEncoder,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var acknowledged by remember(entry.id) { mutableStateOf(!entry.requiresConfirmation) }
    var showViewer by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(entry.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = entry.path,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.description?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VerificationChip(entry.verification)
                if (entry.restoresDefaults) {
                    AssistChip(onClick = {}, label = { Text("Restores defaults") })
                }
                if (entry.destructive) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Disruptive") },
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
                if (!entry.bundled) {
                    AssistChip(onClick = {}, label = { Text("Imported") })
                }
            }

            // Provenance is shown before the symbol, not tucked away, because
            // cross-checking against the manual is the only real defence against a
            // wrong parameter string.
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Source",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(entry.provenance, style = MaterialTheme.typography.bodySmall)
                }
            }

            entry.warning?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            Text(
                text = "Data",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.data,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
            Text(
                text = "${entry.symbologyId.name}${if (entry.escapesEnabled) ", escapes enabled" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!acknowledged) {
                ConfirmationGate(entry = entry, onAcknowledge = { acknowledged = true })
            } else {
                val matrix = rememberEncoded(entry, encoder)
                SymbolBox(
                    matrix = matrix,
                    onOpen = { showViewer = true }.takeIf { matrix != null },
                )
            }
        }
    }

    val viewerMatrix = if (acknowledged) rememberEncoded(entry, encoder) else null
    if (showViewer && viewerMatrix != null) {
        SymbolViewerDialog(
            matrix = viewerMatrix,
            title = entry.name,
            onDismiss = { showViewer = false },
        )
    }
}

@Composable
private fun ConfirmationGate(entry: ConfigEntry, onAcknowledge: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Confirm before showing",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = buildString {
                    if (entry.destructive) {
                        append(
                            "Scanning this will change device settings in a way that " +
                                "may be disruptive or awkward to undo. ",
                        )
                    }
                    if (!entry.verification.isTrustworthy) {
                        append(
                            when (entry.verification) {
                                VerificationStatus.EXAMPLE_ONLY ->
                                    "This is a format example, not a real parameter " +
                                        "code, and will not configure anything. "
                                else ->
                                    "This value has not been checked against the " +
                                        "vendor's own documentation. Verify it against " +
                                        "the guide for your exact model first. "
                            },
                        )
                    }
                    append("Check the source above before scanning it at a device.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(onClick = onAcknowledge) { Text("I understand, show the barcode") }
        }
    }
}

@Composable
private fun VerificationChip(status: VerificationStatus) {
    val label = when (status) {
        VerificationStatus.VERIFIED -> "Verified"
        VerificationStatus.COMMUNITY_REPORTED -> "Community reported"
        VerificationStatus.EXAMPLE_ONLY -> "Example only"
        VerificationStatus.UNSPECIFIED -> "Unverified"
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = if (status.isTrustworthy) {
            AssistChipDefaults.assistChipColors()
        } else {
            AssistChipDefaults.assistChipColors(
                labelColor = MaterialTheme.colorScheme.error,
            )
        },
    )
}

@Composable
private fun rememberEncoded(entry: ConfigEntry, encoder: BarcodeEncoder): ModuleMatrix? {
    val matrix by produceState<ModuleMatrix?>(null, entry.id) {
        value = withContext(Dispatchers.Default) {
            val result = encoder.encode(
                EncodeRequest(
                    symbology = entry.symbologyId,
                    payload = Payload(
                        bytes = entry.data.toByteArray(Charsets.UTF_8),
                        escapesEnabled = entry.escapesEnabled,
                    ),
                ),
            )
            (result as? EncodeResult.Success)?.matrix
        }
    }
    return matrix
}

@Composable
private fun SymbolBox(matrix: ModuleMatrix?, onOpen: (() -> Unit)?) {
    val renderer = remember { BitmapSymbolRenderer() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix == null) {
            Text(
                text = "This entry's data could not be encoded",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val bitmap = remember(matrix) {
                renderer.render(
                    matrix,
                    RenderSpec(modulePx = if (matrix.width > 120) 3 else 6, quietZoneModules = 4),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(8.dp),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Programming barcode",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height),
                )
                if (onOpen != null) {
                    OutlinedButton(onClick = onOpen) { Text("Full screen") }
                }
            }
        }
    }
}
