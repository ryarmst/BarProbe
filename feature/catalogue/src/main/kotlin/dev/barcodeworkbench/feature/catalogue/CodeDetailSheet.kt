package dev.barcodeworkbench.feature.catalogue

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.barcodeworkbench.core.model.CodeLibrary
import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.SavedCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class ValueView { TEXT, ESCAPED, HEX }

/**
 * Details of one saved code.
 *
 * The symbol is re-encoded from the stored payload rather than loaded as an image.
 * That is what makes the entry re-renderable at any size and in any format later,
 * and it is the reason the payload, its input mode and the escape flag are all
 * persisted rather than just a picture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeDetailSheet(
    code: SavedCode,
    libraries: List<CodeLibrary>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Long) -> Unit,
    onCopy: (Long) -> Unit,
    encoder: BarcodeEncoder? = null,
    onSaveMetadata: ((label: String?, notes: String?, tags: Set<String>) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var view by remember(code.id) { mutableStateOf(ValueView.ESCAPED) }
    var showViewer by remember { mutableStateOf(false) }
    var showMoveTargets by remember { mutableStateOf(false) }
    var editing by remember(code.id) { mutableStateOf(false) }

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
            Text(
                text = code.displayTitle(),
                style = MaterialTheme.typography.titleLarge,
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(code.spec?.displayName ?: code.symbologyId.name) },
                )
                AssistChip(onClick = {}, label = { Text(code.payload.mode.name.lowercase()) })
                code.payload.eci?.let { AssistChip(onClick = {}, label = { Text("ECI $it") }) }
                AssistChip(onClick = {}, label = { Text(code.source.name.lowercase()) })
            }

            val matrix = rememberEncodedMatrix(code, encoder)
            SymbolThumbnail(
                matrix = matrix,
                onOpen = { showViewer = true }.takeIf { matrix != null },
            )

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ValueView.entries.forEach { candidate ->
                    FilterChip(
                        selected = view == candidate,
                        onClick = { view = candidate },
                        label = {
                            Text(
                                when (candidate) {
                                    ValueView.TEXT -> "Text"
                                    ValueView.ESCAPED -> "Escaped"
                                    ValueView.HEX -> "Hex"
                                },
                            )
                        },
                    )
                }
            }

            Text(
                text = when (view) {
                    ValueView.TEXT -> code.payload.asText()
                    ValueView.ESCAPED -> code.payload.asEscapedAscii()
                    ValueView.HEX -> code.payload.asHex()
                }.ifEmpty { "(empty payload)" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )

            Text(
                text = "${code.payload.size} bytes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (editing && onSaveMetadata != null) {
                HorizontalDivider()
                MetadataEditor(
                    code = code,
                    onCancel = { editing = false },
                    onSave = { label, notes, tags ->
                        onSaveMetadata(label, notes, tags)
                        editing = false
                    },
                )
            } else {
                code.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    HorizontalDivider()
                    Text(text = notes, style = MaterialTheme.typography.bodySmall)
                }

                if (code.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        code.tags.sorted().forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag) })
                        }
                    }
                }

                HorizontalDivider()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSaveMetadata != null) {
                        OutlinedButton(onClick = { editing = true }) { Text("Edit") }
                    }
                    if (libraries.size > 1) {
                        OutlinedButton(onClick = { showMoveTargets = !showMoveTargets }) {
                            Text("Move or copy")
                        }
                    }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }

            if (showMoveTargets) {
                Text(
                    text = "Choose a destination library",
                    style = MaterialTheme.typography.labelMedium,
                )
                libraries.filter { it.id != code.libraryId }.forEach { library ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMove(library.id); onDismiss() }) {
                            Text("Move to ${library.name}")
                        }
                        Button(onClick = { onCopy(library.id); onDismiss() }) {
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }

    val viewerMatrix = rememberEncodedMatrix(code, encoder)
    if (showViewer && viewerMatrix != null) {
        SymbolViewerDialog(
            matrix = viewerMatrix,
            title = code.displayTitle(),
            onDismiss = { showViewer = false },
        )
    }
}

/**
 * Editor for label, notes and tags.
 *
 * Tags are entered as comma-separated text rather than through a chip-adding widget.
 * For a handful of short tags that is faster to type and to correct, and it makes
 * bulk edits possible in one gesture.
 */
@Composable
private fun MetadataEditor(
    code: SavedCode,
    onCancel: () -> Unit,
    onSave: (label: String?, notes: String?, tags: Set<String>) -> Unit,
) {
    var label by remember(code.id) { mutableStateOf(code.label.orEmpty()) }
    var notes by remember(code.id) { mutableStateOf(code.notes.orEmpty()) }
    var tagText by remember(code.id) { mutableStateOf(code.tags.sorted().joinToString(", ")) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tagText,
            onValueChange = { tagText = it },
            label = { Text("Tags") },
            supportingText = { Text("Separate with commas") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        label,
                        notes,
                        tagText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                    )
                },
            ) {
                Text("Save")
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/**
 * Re-encodes the stored payload off the main thread.
 *
 * Returns null while encoding, or if the payload no longer encodes -- which can
 * legitimately happen if a symbology's rules tightened between releases. Showing the
 * entry's data without a symbol beats hiding the entry entirely.
 */
@Composable
private fun rememberEncodedMatrix(
    code: SavedCode,
    encoder: BarcodeEncoder?,
): ModuleMatrix? {
    if (encoder == null) return null
    val matrix by produceState<ModuleMatrix?>(initialValue = null, code.id, encoder) {
        value = withContext(Dispatchers.Default) {
            val result = encoder.encode(
                EncodeRequest(symbology = code.symbologyId, payload = code.payload),
            )
            (result as? EncodeResult.Success)?.matrix
        }
    }
    return matrix
}

@Composable
private fun SymbolThumbnail(matrix: ModuleMatrix?, onOpen: (() -> Unit)?) {
    val renderer = remember { BitmapSymbolRenderer() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix == null) {
            Text(
                text = "Symbol preview unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val bitmap = remember(matrix) {
                renderer.render(
                    matrix,
                    RenderSpec(
                        modulePx = if (matrix.width > 120) 2 else 5,
                        quietZoneModules = 2,
                    ),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(8.dp),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Saved symbol",
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
