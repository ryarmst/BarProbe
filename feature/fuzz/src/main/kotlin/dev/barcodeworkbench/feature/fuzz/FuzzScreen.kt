package dev.barcodeworkbench.feature.fuzz

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.barcodeworkbench.barcode.render.BitmapSymbolRenderer
import dev.barcodeworkbench.barcode.render.RenderSpec
import dev.barcodeworkbench.core.designsystem.component.SaveToLibraryPicker
import dev.barcodeworkbench.core.designsystem.component.SymbolViewerDialog
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SymbologyRegistry

@Composable
fun FuzzScreen(
    modifier: Modifier = Modifier,
    viewModel: FuzzViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showViewer by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    if (!state.available) {
        EngineUnavailable(modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Fuzz", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Mutates a base payload with radamsa and encodes each variant, " +
                "keeping only those the symbology can actually carry. For testing how " +
                "a scanner and whatever consumes its output handle awkward input.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SymbologyPicker(
            selectedName = state.spec.displayName,
            onSelect = viewModel::setSymbology,
        )
        FuzzabilityNote(state.fuzzability)

        OutlinedTextField(
            value = state.base,
            onValueChange = viewModel::setBase,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base value") },
            supportingText = {
                Text("The seed payload to mutate. Escapes such as \\x1D are honoured.")
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        )

        BaseModeRow(state.inputMode, viewModel::setInputMode)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::previous, enabled = state.canGoBack) {
                Text("Previous")
            }
            Button(onClick = viewModel::next, enabled = !state.working) {
                Text(if (state.current == null) "Start" else "Next")
            }
            if (state.working) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 4.dp))
            }
        }

        state.exhausted?.let { ExhaustedNote(it, state.fuzzability) }

        state.current?.let { case ->
            CaseView(
                base = state.base,
                symbologyName = state.spec.displayName,
                position = state.position,
                case = case,
                onFullScreen = { showViewer = true },
                onSave = { showSave = !showSave },
            )

            if (showSave) {
                SaveToLibraryPicker(
                    libraryNames = state.libraries.map { it.name },
                    onSave = {
                        viewModel.saveCurrent(it)
                        showSave = false
                    },
                    title = "Save this case to a library",
                )
            }
        }

        state.message?.let { message ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
                }
            }
        }
    }

    if (showViewer) {
        state.current?.let { case ->
            SymbolViewerDialog(
                matrix = case.matrix,
                title = "${state.spec.displayName} · fuzz case",
                onDismiss = { showViewer = false },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymbologyPicker(
    selectedName: String,
    onSelect: (dev.barcodeworkbench.core.model.SymbologyId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Symbology") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            // Most-fuzzable formats first, so the good choices are not buried under
            // the numeric ones that will mostly skip.
            SymbologyRegistry.all
                .sortedBy { Fuzzability.of(it).ordinal }
                .forEach { spec ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(spec.displayName)
                                Text(
                                    text = Fuzzability.of(spec).name.lowercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelect(spec.id)
                            expanded = false
                        },
                    )
                }
        }
    }
}

@Composable
private fun FuzzabilityNote(fuzzability: Fuzzability) {
    val colour = when (fuzzability) {
        Fuzzability.GOOD -> MaterialTheme.colorScheme.primary
        Fuzzability.LIMITED -> MaterialTheme.colorScheme.tertiary
        Fuzzability.POOR -> MaterialTheme.colorScheme.error
    }
    Text(
        text = fuzzability.hint,
        style = MaterialTheme.typography.bodySmall,
        color = colour,
    )
}

@Composable
private fun BaseModeRow(mode: InputMode, onSelect: (InputMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Base is read as",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Only Text and Raw bytes: the base is turned into bytes and then
            // mutated, and GS1 element-string structure would not survive mutation
            // in any meaningful way, so it is not offered here.
            listOf(InputMode.UNICODE to "Text", InputMode.BINARY to "Raw bytes").forEach {
                    (m, label) ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onSelect(m) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun CaseView(
    base: String,
    symbologyName: String,
    position: Int,
    case: FuzzCase,
    onFullScreen: () -> Unit,
    onSave: () -> Unit,
) {
    val renderer = remember { BitmapSymbolRenderer() }
    val payload = remember(case) { Payload(case.payload, mode = InputMode.BINARY) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Case $position", style = MaterialTheme.typography.labelLarge)
            Text(
                text = buildString {
                    append("seed ${case.seed}")
                    if (case.skipped > 0) append(" · ${case.skipped} skipped")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .background(Color.White)
                .clickable(onClick = onFullScreen),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = remember(case) {
                renderer.render(
                    case.matrix,
                    RenderSpec(
                        modulePx = if (case.matrix.width > 120) 2 else 6,
                        quietZoneModules = 2,
                    ),
                )
            }
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Fuzzed $symbologyName",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                    .padding(8.dp),
            )
        }

        case.warning?.let {
            Text(
                text = "Encoder warning: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        LabelledMono("Bytes (${case.payload.size})", payload.asHex())
        LabelledMono("As text", payload.asEscapedAscii())

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onFullScreen) { Text("Full screen") }
            OutlinedButton(onClick = onSave) { Text("Save") }
        }
    }
}

@Composable
private fun LabelledMono(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifEmpty { "(empty)" },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ExhaustedNote(outcome: FuzzOutcome.NoneEncodable, fuzzability: Fuzzability) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "No encodable mutation in ${outcome.attempts} tries.",
                style = MaterialTheme.typography.bodyMedium,
            )
            outcome.lastError?.let {
                Text(
                    text = "Encoder said: $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(text = fuzzability.hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EngineUnavailable(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "The mutation engine could not load on this device. Fuzzing is " +
                "unavailable, but every other feature works.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
