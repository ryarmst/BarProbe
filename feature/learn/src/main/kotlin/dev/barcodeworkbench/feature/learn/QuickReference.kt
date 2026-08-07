package dev.barcodeworkbench.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.core.model.CharacterPalette
import dev.barcodeworkbench.core.model.Directive
import dev.barcodeworkbench.core.model.InputModeGuide

/** Escape syntax, drawn partly from the same tables the composer palette uses. */
private data class EscapeRow(val syntax: String, val meaning: String, val example: String)

private val escapeRows = listOf(
    EscapeRow("\\xNN", "One byte, hexadecimal", "\\x1D is the Group Separator"),
    EscapeRow("\\dNNN", "One byte, decimal", "\\d029 is also 0x1D"),
    EscapeRow("\\oNNN", "One byte, octal", "\\o035 is also 0x1D"),
    EscapeRow("\\uNNNN", "Unicode code point", "\\u00E9 is é, two UTF-8 bytes"),
    EscapeRow("\\n \\r \\t", "Newline, return, tab", "The usual control characters"),
    EscapeRow("\\0", "NUL byte", "Survives; not a terminator here"),
    EscapeRow("\\\\", "A literal backslash", "Needed when the data contains one"),
    EscapeRow("\\^^", "A literal \\^", "Needed when the data contains that sequence"),
)

/**
 * Extra context for the directives, keyed by the enum so a new directive shows up here
 * with or without a note rather than being silently omitted.
 */
private val directiveNotes: Map<Directive, String> = mapOf(
    Directive.CODESET_A to "Upper case and control characters",
    Directive.CODESET_B to "Upper and lower case",
    Directive.CODESET_C to "Digits in pairs, so numbers are shorter",
    Directive.CODESET_AUTO to "Let the encoder choose again",
    Directive.FNC1 to "Also the GS1 field separator",
)

/**
 * The control characters that come up most often in practice.
 *
 * Selected by label rather than by byte value because the label is what the reader
 * recognises. A test asserts every one of these still resolves, since a rename in the
 * palette would otherwise drop a row here without any visible failure.
 */
internal val notableControlLabels =
    listOf("NUL", "HT", "LF", "CR", "ESC", "FS", "GS", "RS", "US")

internal val notableControls
    get() = CharacterPalette.controlCharacters.filter { it.label in notableControlLabels }

/**
 * The cheat sheet.
 *
 * Escape syntax, input modes and the most-needed control characters in one place, so
 * the answer to "how do I type a Group Separator" is two taps away from the composer
 * rather than buried in prose.
 */
@Composable
fun QuickReference(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        item { SectionTitle("Input modes") }
        items(InputModeGuide.all.size) { index ->
            val guide = InputModeGuide.all[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(guide.label, style = MaterialTheme.typography.titleSmall)
                    Text(guide.detail, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    guide.examples.forEach { example ->
                        Column {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = example.input,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text("→", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = example.produces,
                                    style = MaterialTheme.typography.bodySmall,
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

        item { SectionTitle("Escape sequences") }
        item { EscapeTable(escapeRows) }

        item { SectionTitle("Control characters worth knowing") }
        item {
            val notable = notableControls
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    notable.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = item.label.padEnd(4),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = item.insertText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(item.name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        text = "All ${CharacterPalette.controlCharacters.size} are " +
                            "available from the Insert palette in the generator.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Driven by the Directive enum the escape parser itself uses, so the list
        // cannot describe an instruction the encoder does not accept.
        item { SectionTitle("Directives, for Code 128 and GS1-128") }
        item {
            EscapeTable(
                Directive.entries.map { d ->
                    EscapeRow(d.escape, d.description, directiveNotes[d].orEmpty())
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EscapeTable(rows: List<EscapeRow>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { row ->
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = row.syntax,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(row.meaning, style = MaterialTheme.typography.bodySmall)
                    }
                    if (row.example.isNotEmpty()) {
                        Text(
                            text = row.example,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
