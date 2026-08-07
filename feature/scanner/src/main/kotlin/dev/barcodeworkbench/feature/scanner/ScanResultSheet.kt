package dev.barcodeworkbench.feature.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.barcode.engine.DecodedBarcode
import dev.barcodeworkbench.barcode.engine.DecodedContentType
import dev.barcodeworkbench.core.designsystem.component.SaveToLibraryPicker
import dev.barcodeworkbench.core.model.EscapeCodec
import dev.barcodeworkbench.core.model.SymbologyRegistry

/** How the decoded payload is rendered. */
private enum class ValueView { TEXT, ESCAPED, HEX }

/**
 * Details of one decoded symbol.
 *
 * The three value views exist because a barcode's content is bytes, and text is
 * only one interpretation of them. A payload containing a Group Separator or raw
 * binary looks truncated or garbled as text, and the escaped and hex views are the
 * only way to see what was actually encoded. That distinction is exactly what a
 * professional is usually scanning to check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultSheet(
    capture: CapturedScan,
    onDismiss: () -> Unit,
    onScanAgain: (() -> Unit)? = null,
    libraryNames: List<String> = emptyList(),
    onSave: ((String) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val barcode = capture.barcode

    // Binary content defaults to hex, because showing it as text would misrepresent
    // it from the first glance.
    var view by remember(barcode) {
        mutableStateOf(
            if (barcode.contentType == DecodedContentType.BINARY) ValueView.HEX else ValueView.TEXT,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = symbologyLabel(barcode),
                style = MaterialTheme.typography.titleLarge,
            )

            MetadataChips(barcode)

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

            val rendered = remember(barcode, view) { renderValue(barcode, view) }
            Text(
                text = rendered.ifEmpty { "(empty payload)" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )

            Text(
                text = "${barcode.bytes.size} bytes",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (barcode.readerInit) {
                // A reader-initialisation symbol is a device programming barcode.
                // Saying so matters: scanning one reconfigures hardware.
                Text(
                    text = "This symbol carries the reader-initialisation flag, which " +
                        "means it is intended to reconfigure a scanner rather than " +
                        "carry data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { copyToClipboard(context, rendered) },
                ) {
                    Text("Copy")
                }
                if (onScanAgain != null) {
                    Button(onClick = onScanAgain) { Text("Scan again") }
                }
            }

            if (onSave != null) {
                HorizontalDivider()
                SaveToLibraryPicker(libraryNames = libraryNames, onSave = onSave)
            }
        }
    }
}

@Composable
private fun MetadataChips(barcode: DecodedBarcode) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AssistChip(onClick = {}, label = { Text(contentTypeLabel(barcode.contentType)) })
        barcode.errorCorrectionLevel?.let {
            AssistChip(onClick = {}, label = { Text("EC $it") })
        }
        barcode.symbologyIdentifier?.let {
            AssistChip(onClick = {}, label = { Text("AIM $it") })
        }
        if (barcode.orientationDegrees != 0) {
            AssistChip(onClick = {}, label = { Text("${barcode.orientationDegrees}°") })
        }
        barcode.sequenceSize?.let { size ->
            // Structured append: this symbol is one part of a larger message.
            val index = barcode.sequenceIndex?.plus(1) ?: 1
            AssistChip(onClick = {}, label = { Text("Part $index of $size") })
        }
    }
}

private fun symbologyLabel(barcode: DecodedBarcode): String =
    barcode.symbology
        ?.let { SymbologyRegistry.find(it)?.displayName }
        // Falls back to the engine's own name so an unmapped format still reads
        // sensibly rather than showing "Unknown".
        ?: barcode.rawFormatName

private fun contentTypeLabel(type: DecodedContentType): String = when (type) {
    DecodedContentType.TEXT -> "Text"
    DecodedContentType.BINARY -> "Binary"
    DecodedContentType.MIXED -> "Mixed encoding"
    DecodedContentType.GS1 -> "GS1"
    DecodedContentType.ISO15434 -> "ISO 15434"
    DecodedContentType.UNKNOWN -> "Unknown ECI"
}

private fun renderValue(barcode: DecodedBarcode, view: ValueView): String = when (view) {
    ValueView.TEXT -> barcode.text
    ValueView.ESCAPED -> EscapeCodec.toEscapeSource(barcode.bytes)
    ValueView.HEX -> barcode.bytes
        .joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}

private fun copyToClipboard(context: Context, value: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("Scanned barcode", value))
}
