package dev.barcodeworkbench.feature.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.core.model.CharacterPalette
import dev.barcodeworkbench.core.model.PaletteCategory
import dev.barcodeworkbench.core.model.PaletteItem
import dev.barcodeworkbench.core.model.SymbologySpec

/**
 * The insert palette.
 *
 * This is the app's answer to a requirement no soft keyboard can satisfy: a Group
 * Separator, an FNC1 or byte 0x8F are all legitimate barcode content and none of
 * them can be typed. Every such value gets a labelled key, so the user never has
 * to know the escape syntax underneath.
 *
 * Categories that the selected symbology cannot use are not shown at all, and
 * individual keys whose byte the symbology cannot encode are dimmed rather than
 * hidden, so the palette teaches the format's limits instead of silently
 * producing an invalid payload.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertPaletteSheet(
    spec: SymbologySpec,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categories = remember(spec.id) { CharacterPalette.categoriesFor(spec) }
    var selected by remember(spec.id) { mutableStateOf(categories.first()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Insert into ${spec.displayName}",
                style = MaterialTheme.typography.titleLarge,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selected == category,
                        onClick = { selected = category },
                        label = { Text(shortTitle(category)) },
                    )
                }
            }

            Text(
                text = selected.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            val items = remember(selected) { CharacterPalette.itemsFor(selected) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (selected == PaletteCategory.GS1_AI) 150.dp else 72.dp),
                modifier = Modifier.heightIn(max = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.label + it.insertText }) { item ->
                    PaletteKey(
                        item = item,
                        enabled = CharacterPalette.isUsable(item, spec),
                        wide = selected == PaletteCategory.GS1_AI,
                        onClick = { onInsert(item.insertText) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteKey(
    item: PaletteItem,
    enabled: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
                },
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = if (wide) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 0.75f else DISABLED_ALPHA,
                ),
            )
            item.note?.takeIf { wide }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f

private fun shortTitle(category: PaletteCategory): String = when (category) {
    PaletteCategory.CONTROL -> "Control"
    PaletteCategory.DIRECTIVE -> "Function"
    PaletteCategory.GS1_AI -> "GS1 AI"
    PaletteCategory.HIGH_BYTE -> "Bytes"
}
