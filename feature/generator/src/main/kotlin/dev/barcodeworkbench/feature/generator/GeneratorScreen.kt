package dev.barcodeworkbench.feature.generator

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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.barcodeworkbench.barcode.render.BitmapSymbolRenderer
import dev.barcodeworkbench.barcode.render.RenderSpec
import dev.barcodeworkbench.barcode.render.SymbolExporter
import dev.barcodeworkbench.core.designsystem.component.SaveToLibraryPicker
import dev.barcodeworkbench.core.designsystem.component.SymbolViewerDialog
import dev.barcodeworkbench.feature.generator.batch.BatchSheet
import dev.barcodeworkbench.core.model.Dimension
import dev.barcodeworkbench.core.model.EciRegistry
import dev.barcodeworkbench.core.model.EscapeCodec
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.InputModeGuide
import dev.barcodeworkbench.core.model.PayloadToken
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry

@Composable
fun GeneratorScreen(
    modifier: Modifier = Modifier,
    viewModel: GeneratorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showPalette by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showBatch by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    // The field owns its selection so the palette can insert at the cursor; the
    // ViewModel only ever needs the text.
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.payloadSource)) }
    if (fieldValue.text != state.payloadSource) {
        fieldValue = TextFieldValue(
            text = state.payloadSource,
            selection = TextRange(state.payloadSource.length),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SymbologySelector(
            selectedName = state.spec.displayName,
            onSelect = viewModel::selectSymbology,
        )

        SymbologyFacts(state)

        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                viewModel.updatePayload(it.text)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Payload") },
            placeholder = { Text(state.spec.sampleValue) },
            isError = state.fieldError != null,
            supportingText = {
                val error = state.fieldError
                if (error != null) {
                    Text(error)
                } else {
                    // Joined with a separator rather than a full stop: the length
                    // rules read as fragments ("up to 4296 characters"), so a period
                    // between them produced a sentence starting in lower case.
                    Text(
                        "${state.spec.charsetRule.description} · " +
                            state.spec.lengthRule.description,
                    )
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            minLines = 2,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showPalette = true }) { Text("Insert…") }
            OutlinedButton(onClick = viewModel::useSampleValue) { Text("Sample") }
            OutlinedButton(onClick = { showBatch = true }) { Text("Batch…") }
            if (state.payloadSource.isNotEmpty()) {
                TextButton(onClick = viewModel::clearPayload) { Text("Clear") }
            }
        }

        InputModeRow(state, viewModel)

        if (state.spec.supportsEci) {
            EciRow(state, viewModel)
        }

        // A decoded reading of the field, so the meaning of an escape is visible
        // without opening the byte inspector.
        if (state.showRawEscapes && state.payloadSource.isNotEmpty()) {
            EscapePreview(state.payloadSource)
        }

        state.encodeWarning?.let { warning ->
            // A warning still yields a usable symbol, so it appears alongside the
            // preview rather than replacing it.
            Text(
                text = "Encoder warning: $warning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        SymbolPreview(
            state = state,
            onOpenFullScreen = { showViewer = true }.takeIf { state.hasSymbol },
            onExport = { showExport = true }.takeIf { state.hasSymbol },
            onSave = { showSave = !showSave }.takeIf { state.hasSymbol },
        )

        if (showSave && state.hasSymbol) {
            SaveToLibraryPicker(
                libraryNames = state.libraries.map { it.name },
                onSave = { name ->
                    viewModel.saveToLibrary(name)
                    showSave = false
                },
            )
        }

        state.saveMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { viewModel.dismissSaveMessage() },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.showInspector,
                onClick = viewModel::toggleInspector,
                label = { Text("Byte inspector") },
            )
            if (EscapeCodec.containsEscapes(state.payloadSource)) {
                FilterChip(
                    selected = state.showRawEscapes,
                    onClick = viewModel::toggleRawEscapes,
                    label = { Text("Decode escapes") },
                )
            }
        }

        if (state.showInspector) {
            state.validation?.let { ByteInspector(it) }
        }
    }

    val matrix = state.matrix
    if (showViewer && matrix != null) {
        SymbolViewerDialog(
            matrix = matrix,
            title = "${state.spec.displayName} symbol",
            onDismiss = { showViewer = false },
        )
    }

    if (showExport && matrix != null) {
        val exporter = viewModel.exporter
        ExportSheet(
            matrix = matrix,
            symbologyName = state.spec.displayName,
            payloadHint = state.payloadSource,
            exporter = exporter,
            onDismiss = { showExport = false },
        )
    }

    if (showBatch) {
        BatchSheet(
            defaultSymbology = state.symbologyId,
            onDismiss = { showBatch = false },
        )
    }

    if (showPalette) {
        InsertPaletteSheet(
            spec = state.spec,
            onInsert = { insert ->
                val selection = fieldValue.selection
                val updated = fieldValue.text.replaceRange(selection.min, selection.max, insert)
                fieldValue = TextFieldValue(updated, TextRange(selection.min + insert.length))
                viewModel.updatePayload(updated)
            },
            onDismiss = { showPalette = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymbologySelector(
    selectedName: String,
    onSelect: (SymbologyId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
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
            // Grouped by category so 26 formats read as a reference rather than an
            // arbitrary list.
            SymbologyRegistry.all
                .groupBy { it.category }
                .forEach { (category, specs) ->
                    DropdownMenuItem(
                        enabled = false,
                        onClick = {},
                        text = {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                    specs.forEach { spec ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(spec.displayName)
                                    if (!spec.isReadable) {
                                        Text(
                                            text = "generate only",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
}

/**
 * Facts worth knowing before committing to a format.
 *
 * The capability chips answer "can this format do what I need"; the expandable detail
 * answers "what will it accept", which is the question that actually causes failed
 * encodes. Both are read from the registry, so this stays true as formats are added
 * and matches the reference page exactly.
 */
@Composable
private fun SymbologyFacts(state: GeneratorUiState) {
    val spec = state.spec
    // Same reasoning as the input-mode hint: the panel is most useful while flipping
    // between formats, so it stays open until dismissed.
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {},
                label = { Text(if (spec.dimension == Dimension.LINEAR) "1D" else "2D") },
            )
            if (spec.supportsGs1) AssistChip(onClick = {}, label = { Text("GS1") })
            if (spec.supportsEci) AssistChip(onClick = {}, label = { Text("ECI") })
            if (!spec.isReadable) {
                AssistChip(onClick = {}, label = { Text("No scan support") })
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide rules" else "Rules")
            }
        }

        if (expanded) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SymbologyFact("Characters", spec.charsetRule.description)
                    SymbologyFact("Length", spec.lengthRule.description)
                    SymbologyFact("Check digit", spec.checkDigit.description)
                    SymbologyFact("Example", spec.sampleValue, mono = true)
                    if (spec.notes.isNotBlank()) {
                        Text(
                            text = spec.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbologyFact(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

@Composable
private fun InputModeRow(state: GeneratorUiState, viewModel: GeneratorViewModel) {
    val spec = state.spec
    val modes = buildList {
        add(InputMode.UNICODE)
        add(InputMode.BINARY)
        if (spec.supportsGs1) add(InputMode.GS1)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Input mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { mode ->
                FilterChip(
                    selected = state.inputMode == mode,
                    onClick = { viewModel.setInputMode(mode) },
                    label = { Text(InputModeGuide.forMode(mode).label) },
                )
            }
        }

        // The mode names alone do not convey how input differs between them, and that
        // difference is exactly where people get stuck. A worked example costs a few
        // lines and removes the guesswork.
        InputModeHint(InputModeGuide.forMode(state.inputMode))
    }
}

/**
 * Inline explanation of the selected input mode.
 *
 * Collapsed to a single summary line by default and expandable to worked examples: the
 * summary answers the common case without adding clutter, and the examples are one tap
 * away when it does not.
 */
@Composable
private fun InputModeHint(guide: InputModeGuide) {
    // Not keyed on the mode: someone who opened the examples is usually comparing
    // modes, and collapsing on every switch would make them re-open it each time.
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = guide.summary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "less" else "examples",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                HorizontalDivider()
                guide.examples.forEach { example ->
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = example.input,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text("→", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = example.produces,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        example.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * ECI selection.
 *
 * Matters because without an ECI a payload containing non-ASCII text is ambiguous: the
 * same bytes decode to different characters under different encodings, and the reader
 * has to guess. Leaving it unset lets the encoder decide, which is right for plain
 * ASCII and wrong as soon as the content is not.
 */
@Composable
private fun EciRow(state: GeneratorUiState, viewModel: GeneratorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Character encoding (ECI)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.eci == null,
                onClick = { viewModel.setEci(null) },
                label = { Text("Automatic") },
            )
            EciRegistry.common.forEach { option ->
                FilterChip(
                    selected = state.eci == option.value,
                    onClick = { viewModel.setEci(option.value) },
                    label = { Text("${option.value} ${option.label}") },
                )
            }
        }
        state.eci?.let { value ->
            EciRegistry.find(value)?.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Renders the payload with escapes resolved into readable tokens.
 *
 * A control character has no visible glyph, so `AB\x1DCD` in the field gives no clue
 * whether the escape was understood. Showing it as `AB⟨GS⟩CD` confirms the parse at a
 * glance, which is quicker than reading hex.
 */
@Composable
private fun EscapePreview(source: String) {
    val rendered = remember(source) { renderEscapes(source) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Decoded",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = rendered,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        )
    }
}

/** Control bytes become bracketed mnemonics; directives keep their own labels. */
private fun renderEscapes(source: String): String {
    val parsed = EscapeCodec.parse(source)
    if (!parsed.isValid) return "(fix the escape errors above to see this)"
    return buildString {
        parsed.tokens.forEach { token ->
            when (token) {
                is PayloadToken.Instruction -> append("⟨${token.directive.label}⟩")
                is PayloadToken.Data -> {
                    val v = token.byte
                    if (v in 0x20..0x7E) {
                        append(v.toChar())
                    } else {
                        append("⟨${controlMnemonic(v)}⟩")
                    }
                }
            }
        }
    }
}

private fun controlMnemonic(value: Int): String = when (value) {
    0x00 -> "NUL"; 0x07 -> "BEL"; 0x08 -> "BS"; 0x09 -> "HT"
    0x0A -> "LF"; 0x0B -> "VT"; 0x0C -> "FF"; 0x0D -> "CR"
    0x1B -> "ESC"; 0x1C -> "FS"; 0x1D -> "GS"; 0x1E -> "RS"
    0x1F -> "US"; 0x7F -> "DEL"
    else -> "%02X".format(value)
}

@Composable
private fun SymbolPreview(
    state: GeneratorUiState,
    onOpenFullScreen: (() -> Unit)?,
    onExport: (() -> Unit)?,
    onSave: (() -> Unit)?,
) {
    val renderer = remember { BitmapSymbolRenderer() }
    val matrix = state.matrix

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            // A fixed white field regardless of theme: a barcode needs true
            // black-on-white contrast, and tinting it for dark mode would work
            // against the scanner.
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isEncoding && matrix == null -> CircularProgressIndicator()

            matrix != null -> {
                val bitmap = remember(matrix) {
                    renderer.render(
                        matrix,
                        RenderSpec(
                            // Wide symbols would overflow at a large module size.
                            modulePx = if (matrix.width > 120) 2 else 6,
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
                        contentDescription = "Generated ${state.spec.displayName}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onOpenFullScreen != null) {
                            OutlinedButton(onClick = onOpenFullScreen) { Text("Full screen") }
                        }
                        if (onExport != null) {
                            OutlinedButton(onClick = onExport) { Text("Export") }
                        }
                        if (onSave != null) {
                            OutlinedButton(onClick = onSave) { Text("Save") }
                        }
                    }
                }
            }

            else -> Text(
                text = state.fieldError ?: "Enter a payload to preview",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
