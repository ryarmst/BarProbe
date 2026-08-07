package dev.barcodeworkbench.feature.configpacks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.core.model.config.ConfigEntry
import dev.barcodeworkbench.core.model.config.VerificationStatus

@Composable
fun ConfigPacksScreen(
    modifier: Modifier = Modifier,
    viewModel: ConfigPacksViewModel = hiltViewModel(),
    encoder: BarcodeEncoder? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var inspecting by remember { mutableStateOf<ConfigEntry?>(null) }
    // Not keyed on the vendor: someone who opens recovery is usually working
    // through it, and reclosing on every vendor change would be tiresome.
    var showRecovery by remember { mutableStateOf(false) }

    val importPack = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (text != null) viewModel.importPack(text)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Device configuration", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { importPack.launch(arrayOf("application/json", "*/*")) }) {
                Text("Import pack")
            }
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Search all packs") },
            supportingText = { Text("Searches names, descriptions, categories and data") },
            singleLine = true,
        )

        state.message?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isError) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::dismissMessage) { Text("Dismiss") }
                }
            }
        }

        if (!state.isSearching) {
            LazyRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.vendors, key = { it }) { vendor ->
                    val count = state.packs
                        .filter { it.vendor == vendor }
                        .sumOf { it.entryCount }
                    ElevatedFilterChip(
                        selected = state.selectedVendor == vendor,
                        onClick = { viewModel.selectVendor(vendor) },
                        label = { Text("$vendor ($count)") },
                    )
                }
            }

            // Recovery stays one tap from anywhere, but collapsed. Expanded by
            // default it cost a header, a row per entry and a divider before the
            // category chips were even reachable, which pushed the actual content
            // off the first screen. The point was that recovery is never buried in
            // a folder tree; a labelled row on the way past satisfies that without
            // spending a third of the screen on three barcodes.
            if (state.defaults.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRecovery = !showRecovery }
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recovery (${state.defaults.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (showRecovery) "Hide" else "Show",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (showRecovery) {
                    state.defaults.forEach { entry ->
                        EntryRow(entry = entry, onClick = { inspecting = entry })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (state.categories.isEmpty() && !state.isLoading) {
                EmptyVendor(
                    vendor = state.selectedVendor,
                    explanation = state.selectedPack?.description,
                )
            } else {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.categories, key = { it.name }) { category ->
                        FilterChip(
                            selected = state.selectedCategory == category.name,
                            onClick = {
                                viewModel.selectCategory(
                                    category.name.takeIf { it != state.selectedCategory },
                                )
                            },
                            label = { Text("${category.name} (${category.entryCount})") },
                        )
                    }
                }
            }
        }

        Text(
            text = when {
                state.isSearching && entries.isEmpty() -> "No matches"
                state.isSearching -> "${entries.size} matches"
                state.selectedCategory == null -> "Choose a category"
                else -> "${entries.size} entries"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                EntryRow(entry = entry, showPath = state.isSearching) { inspecting = entry }
            }
        }
    }

    val activeEncoder = encoder
    inspecting?.let { entry ->
        if (activeEncoder != null) {
            ConfigEntrySheet(
                entry = entry,
                encoder = activeEncoder,
                onDismiss = { inspecting = null },
            )
        }
    }
}

@Composable
private fun EmptyVendor(vendor: String?, explanation: String?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "No entries bundled for ${vendor ?: "this vendor"}",
                style = MaterialTheme.typography.titleSmall,
            )
            // The pack's own description, so the explanation lives with the data
            // rather than being hard-coded here. The fallback covers an imported pack
            // that supplied none.
            Text(
                text = explanation
                    ?: "This pack contains no entries. Author one from the reference " +
                    "guide for your exact model and import it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryRow(
    entry: ConfigEntry,
    showPath: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.restoresDefaults) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.requiresConfirmation) {
                    // A row-level marker, so caution is visible while browsing rather
                    // than only after tapping through.
                    Text(
                        text = if (entry.destructive) "disruptive" else "unverified",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (showPath) {
                Text(
                    text = entry.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            entry.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.verification == VerificationStatus.EXAMPLE_ONLY) {
                Text(
                    text = "Format example, not a real parameter code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
