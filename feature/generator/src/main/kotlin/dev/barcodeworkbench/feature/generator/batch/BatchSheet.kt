package dev.barcodeworkbench.feature.generator.batch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.barcodeworkbench.barcode.render.ExportFormat
import dev.barcodeworkbench.core.model.SymbologyId

/**
 * Batch generation from a wordlist.
 *
 * The flow is deliberately import, then preview, then produce. Every row is
 * encoded up front and the failures are listed with their source line numbers, so
 * a thousand-row wordlist reveals its problems before a single file is written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchSheet(
    defaultSymbology: SymbologyId,
    onDismiss: () -> Unit,
    viewModel: BatchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pickWordlist = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (content != null) {
                viewModel.load(name, content, defaultSymbology)
            }
        }
    }

    val createOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(viewModel.outputMimeType()),
    ) { uri ->
        if (uri != null) {
            val stream = context.contentResolver.openOutputStream(uri)
            if (stream != null) {
                viewModel.produceFile(stream) {}
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Batch from wordlist", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "One payload per line, or CSV with payload, symbology and label " +
                    "columns. Lines beginning with # are comments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { pickWordlist.launch(arrayOf("text/*", "text/csv", "text/plain")) },
                ) {
                    Text(if (state.fileName == null) "Choose file…" else "Choose another…")
                }
                if (state.isWorking) {
                    TextButton(onClick = viewModel::cancel) { Text("Cancel") }
                }
            }

            state.fileName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }

            if (state.isWorking && state.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${state.progressDone} of ${state.progressTotal}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            state.preview?.let { preview ->
                HorizontalDivider()
                Text(
                    text = "${state.readyCount} ready, ${state.rejectedCount} rejected",
                    style = MaterialTheme.typography.titleSmall,
                )

                if (preview.rejected.isNotEmpty()) {
                    // Failures are itemised with their line numbers so the user can
                    // fix the source file rather than guess.
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(preview.rejected) { rejected ->
                            Text(
                                text = "line ${rejected.entry.lineNumber}: ${rejected.reason}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                if (preview.skippedLines.isNotEmpty()) {
                    Text(
                        text = "${preview.skippedLines.size} lines skipped while parsing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.preview?.hasAnything == true) {
                HorizontalDivider()
                Text(
                    text = "Output",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.output == BatchOutput.ZIP,
                        onClick = { viewModel.setOutput(BatchOutput.ZIP) },
                        label = { Text("ZIP of images") },
                    )
                    FilterChip(
                        selected = state.output == BatchOutput.PDF_SHEET,
                        onClick = { viewModel.setOutput(BatchOutput.PDF_SHEET) },
                        label = { Text("PDF sheet") },
                    )
                    FilterChip(
                        selected = state.output == BatchOutput.LIBRARY,
                        onClick = { viewModel.setOutput(BatchOutput.LIBRARY) },
                        label = { Text("Library") },
                    )
                }

                when (state.output) {
                    BatchOutput.ZIP -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ExportFormat.PNG, ExportFormat.SVG).forEach { format ->
                                FilterChip(
                                    selected = state.zipFormat == format,
                                    onClick = { viewModel.setZipFormat(format) },
                                    label = { Text(format.displayName) },
                                )
                            }
                        }
                    }

                    BatchOutput.LIBRARY -> {
                        OutlinedTextField(
                            value = state.libraryName,
                            onValueChange = viewModel::setLibraryName,
                            label = { Text("Library name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }

                    BatchOutput.PDF_SHEET -> Unit
                }

                OutlinedButton(
                    onClick = {
                        if (state.output == BatchOutput.LIBRARY) {
                            viewModel.produceToLibrary()
                        } else {
                            createOutput.launch(viewModel.suggestedFileName())
                        }
                    },
                    enabled = state.canProduce,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.output == BatchOutput.LIBRARY) {
                            "Save ${state.readyCount} to library"
                        } else {
                            "Choose destination…"
                        },
                    )
                }
            }

            state.message?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
