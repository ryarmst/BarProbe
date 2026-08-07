package dev.barcodeworkbench.feature.generator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.core.model.Directive
import dev.barcodeworkbench.core.model.ValidationResult

/**
 * Shows the exact bytes the encoder will receive.
 *
 * This is the component that makes the whole escape mechanism trustworthy. Without
 * it, a user inserting a Group Separator has no way to confirm whether they
 * produced one byte 0x1D or the four literal characters "\x1D", and the difference
 * is invisible in the rendered symbol. The offsets, hex column and annotated
 * character column together let someone verify a payload against a specification
 * before printing thousands of labels.
 */
@Composable
fun ByteInspector(
    validation: ValidationResult,
    modifier: Modifier = Modifier,
) {
    val bytes = remember(validation) { validation.effectiveBytes }
    val rows = remember(bytes) { bytes.toList().chunked(BYTES_PER_ROW) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Bytes to encode",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = if (bytes.size == 1) "1 byte" else "${bytes.size} bytes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (validation.directives.isNotEmpty()) {
                DirectiveRow(validation.directives)
            }

            if (bytes.isEmpty()) {
                Text(
                    text = "Nothing to encode yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    rows.forEachIndexed { index, row ->
                        HexDumpRow(offset = index * BYTES_PER_ROW, row = row)
                    }
                }
            }
        }
    }
}

/**
 * Directives are listed separately from the hex dump because they contribute no
 * bytes. Folding them into the byte view would misrepresent the data stream.
 */
@Composable
private fun DirectiveRow(directives: List<Directive>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Encoder instructions (no data bytes)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            directives.forEach { directive ->
                AssistChip(
                    onClick = {},
                    label = { Text(directive.label) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}

@Composable
private fun HexDumpRow(offset: Int, row: List<Byte>) {
    val hex = remember(row) {
        row.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            .padEnd(BYTES_PER_ROW * 3 - 1)
    }
    val glyphs = remember(row) {
        row.joinToString("") { byte ->
            val v = byte.toInt() and 0xFF
            // Non-printable bytes render as a middle dot; showing the raw value
            // would corrupt the column alignment that makes this readable.
            if (v in 0x20..0x7E) v.toChar().toString() else "·"
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "%04X".format(offset),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = hex,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = glyphs,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val BYTES_PER_ROW = 8
