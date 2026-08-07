package dev.barcodeworkbench.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.core.model.Category
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry

/**
 * Per-symbology decode toggles.
 *
 * Worth exposing rather than always enabling everything: each additional format costs
 * time on every frame, and a scanner restricted to what the job actually uses is
 * measurably quicker to lock on. The decode time shown on the scanner screen makes the
 * trade visible.
 *
 * Only readable formats appear. Generate-only ones such as DotCode are absent by
 * construction, since offering a toggle that could never match anything would be a
 * trap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbologySettingsSheet(
    enabled: Set<SymbologyId>,
    onToggle: (SymbologyId) -> Unit,
    onEnableAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val readable = SymbologyRegistry.readable

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Symbologies to decode", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "${enabled.size} of ${readable.size} enabled. Fewer formats means " +
                    "faster detection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onEnableAll) { Text("Enable all") }

            HorizontalDivider()

            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                // Grouped by category so a user can reason about "retail" or "GS1"
                // rather than scanning a flat list of 23 names.
                Category.entries.forEach { category ->
                    val inCategory = readable.filter { it.category == category }
                    if (inCategory.isEmpty()) return@forEach

                    item(key = "header-$category") {
                        Text(
                            text = category.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(inCategory, key = { it.id.name }) { spec ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = spec.charsetRule.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = spec.id in enabled,
                                onCheckedChange = { onToggle(spec.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
