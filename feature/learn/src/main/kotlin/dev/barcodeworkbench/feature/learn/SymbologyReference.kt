package dev.barcodeworkbench.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.barcodeworkbench.core.model.Category
import dev.barcodeworkbench.core.model.Dimension
import dev.barcodeworkbench.core.model.SymbologyRegistry
import dev.barcodeworkbench.core.model.SymbologySpec

/**
 * Per-symbology reference, generated from the registry.
 *
 * Deliberately derived rather than written out. The registry is already the single
 * source of truth for character sets, length rules and capabilities, and is checked
 * against the encoder by test, so a reference built from it cannot drift from what the
 * app will actually accept. Hand-written documentation would.
 */
@Composable
fun SymbologyReference(modifier: Modifier = Modifier) {
    var category by remember { mutableStateOf<Category?>(null) }
    val specs = remember(category) {
        category?.let { SymbologyRegistry.byCategory(it) } ?: SymbologyRegistry.all
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        item {
            Text(
                text = "Every format this app can produce, with the rules the encoder " +
                    "actually enforces.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = category == null,
                    onClick = { category = null },
                    label = { Text("All") },
                )
                Category.entries.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = if (category == c) null else c },
                        label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }
        items(specs, key = { it.id.name }) { spec ->
            SymbologyCard(spec)
        }
    }
}

@Composable
private fun SymbologyCard(spec: SymbologySpec) {
    var expanded by remember(spec.id) { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (spec.dimension == Dimension.LINEAR) "1D" else "2D",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (spec.supportsGs1) Capability("GS1")
                if (spec.supportsEci) Capability("ECI")
                if (spec.supportsStructuredAppend) Capability("Multi-part")
                if (spec.supportsCodesetEscapes) Capability("Codesets")
                if (!spec.isReadable) Capability("Generate only", warn = true)
            }

            if (expanded) {
                HorizontalDivider()
                Detail("Characters", spec.charsetRule.description)
                Detail("Length", spec.lengthRule.description)
                Detail("Check digit", spec.checkDigit.description)
                Detail("Example", spec.sampleValue, mono = true)
                if (!spec.isReadable) {
                    Detail(
                        "Scanning",
                        "This app can produce this format but not read it, because the " +
                            "decode engine does not support it.",
                    )
                }
                if (spec.notes.isNotBlank()) {
                    Text(
                        text = spec.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = spec.charsetRule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Capability(label: String, warn: Boolean = false) {
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = if (warn) {
            AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.error)
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}

@Composable
private fun Detail(label: String, value: String, mono: Boolean = false) {
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
