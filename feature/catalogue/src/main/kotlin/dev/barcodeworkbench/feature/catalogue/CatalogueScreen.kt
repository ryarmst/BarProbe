package dev.barcodeworkbench.feature.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.barcodeworkbench.core.designsystem.counted
import dev.barcodeworkbench.core.designsystem.plural
import dev.barcodeworkbench.core.model.CodeSortOrder
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.feature.catalogue.backup.BackupSheet

@Composable
fun CatalogueScreen(
    modifier: Modifier = Modifier,
    viewModel: CatalogueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val codes by viewModel.codes.collectAsStateWithLifecycle()

    var showCreateLibrary by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf<SavedCode?>(null) }
    var showBackup by remember { mutableStateOf(false) }
    var managingLibrary by remember { mutableStateOf<Long?>(null) }
    var renamingLibrary by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Libraries", style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { showBackup = true }) { Text("Backup") }
                TextButton(onClick = { showCreateLibrary = true }) { Text("New") }
            }
        }

        if (state.libraries.isEmpty()) {
            EmptyCatalogue(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.libraries, key = { it.id }) { library ->
                    ElevatedFilterChip(
                        selected = state.selectedLibraryId == library.id,
                        onClick = {
                            // A second tap on the already-selected library opens its
                            // management row, which keeps rename and delete reachable
                            // without a long-press gesture nothing else in the app uses.
                            if (state.selectedLibraryId == library.id) {
                                managingLibrary =
                                    if (managingLibrary == library.id) null else library.id
                            } else {
                                viewModel.selectLibrary(library.id)
                                managingLibrary = null
                            }
                        },
                        label = { Text("${library.name} (${library.entryCount})") },
                    )
                }
            }

            managingLibrary?.let { libraryId ->
                val library = state.libraries.firstOrNull { it.id == libraryId }
                if (library != null) {
                    LibraryManagementRow(
                        canMoveEarlier = state.libraries.indexOf(library) > 0,
                        canMoveLater = state.libraries.indexOf(library) < state.libraries.size - 1,
                        onRename = { renamingLibrary = libraryId },
                        onMoveEarlier = { viewModel.reorderLibrary(libraryId, -1) },
                        onMoveLater = { viewModel.reorderLibrary(libraryId, 1) },
                        onDelete = {
                            viewModel.deleteLibrary(libraryId)
                            managingLibrary = null
                        },
                        entryCount = library.entryCount,
                    )
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("Search") },
                // Explains the escaped-search behaviour, which is not obvious but is
                // the only way to find control characters inside a payload.
                supportingText = {
                    Text("Matches labels, notes, tags and the escaped payload, e.g. \\x1D")
                },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortChip(state.sortOrder, viewModel::setSortOrder)
                if (state.hasActiveFilter) {
                    AssistChip(
                        onClick = viewModel::clearFilters,
                        label = { Text("Clear filters") },
                    )
                }
            }

            if (state.allTags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.allTags) { tag ->
                        FilterChip(
                            selected = tag in state.tagFilter,
                            onClick = { viewModel.toggleTagFilter(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text(
                text = if (codes.isEmpty() && state.hasActiveFilter) {
                    "No codes match the current filter"
                } else {
                    counted(codes.size, "code")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(codes, key = { it.id }) { code ->
                    CodeRow(code = code, onClick = { inspecting = code })
                }
            }
        }
    }

    if (showBackup) {
        BackupSheet(onDismiss = { showBackup = false })
    }

    renamingLibrary?.let { libraryId ->
        val library = state.libraries.firstOrNull { it.id == libraryId }
        if (library != null) {
            NameLibraryDialog(
                title = "Rename library",
                initial = library.name,
                onConfirm = {
                    viewModel.renameLibrary(libraryId, it)
                    renamingLibrary = null
                },
                onDismiss = { renamingLibrary = null },
            )
        }
    }

    if (showCreateLibrary) {
        NameLibraryDialog(
            title = "New library",
            initial = "",
            onConfirm = {
                viewModel.createLibrary(it)
                showCreateLibrary = false
            },
            onDismiss = { showCreateLibrary = false },
        )
    }

    inspecting?.let { code ->
        CodeDetailSheet(
            code = code,
            libraries = state.libraries,
            onDismiss = { inspecting = null },
            onDelete = {
                viewModel.deleteCode(code.id)
                inspecting = null
            },
            onMove = { target -> viewModel.moveCode(code.id, target) },
            onCopy = { target -> viewModel.copyCode(code.id, target) },
            encoder = viewModel.encoder,
            onSaveMetadata = { label, notes, tags ->
                viewModel.updateMetadata(code, label, notes, tags)
                inspecting = null
            },
        )
    }
}

/**
 * Rename, reorder and delete for one library.
 *
 * Delete asks for confirmation only when the library holds entries; an empty one is
 * removed immediately, because there is nothing to lose and a dialog would be friction
 * for the common case of tidying up a mistyped name.
 */
@Composable
private fun LibraryManagementRow(
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    entryCount: Int,
    onRename: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onRename) { Text("Rename") }
        TextButton(onClick = onMoveEarlier, enabled = canMoveEarlier) { Text("←") }
        TextButton(onClick = onMoveLater, enabled = canMoveLater) { Text("→") }
        TextButton(
            onClick = { if (entryCount == 0) onDelete() else confirmingDelete = true },
        ) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this library?") },
            text = {
                Text(
                    "Its ${counted(entryCount, "code")} will be deleted too. Export a " +
                        "backup first if you might want ${plural(entryCount, "it", "them")} back.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyCatalogue(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "No libraries yet",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Create one here, or save a code from the generator or scanner.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SortChip(current: CodeSortOrder, onSelect: (CodeSortOrder) -> Unit) {
    val next = when (current) {
        CodeSortOrder.NEWEST_FIRST -> CodeSortOrder.OLDEST_FIRST
        CodeSortOrder.OLDEST_FIRST -> CodeSortOrder.LABEL_ASCENDING
        CodeSortOrder.LABEL_ASCENDING -> CodeSortOrder.SYMBOLOGY
        CodeSortOrder.SYMBOLOGY -> CodeSortOrder.NEWEST_FIRST
    }
    AssistChip(
        onClick = { onSelect(next) },
        label = {
            Text(
                when (current) {
                    CodeSortOrder.NEWEST_FIRST -> "Newest first"
                    CodeSortOrder.OLDEST_FIRST -> "Oldest first"
                    CodeSortOrder.LABEL_ASCENDING -> "By label"
                    CodeSortOrder.SYMBOLOGY -> "By symbology"
                },
            )
        },
    )
}

@Composable
private fun CodeRow(code: SavedCode, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = code.spec?.displayName ?: code.symbologyId.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = code.source.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = code.displayTitle(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (code.tags.isNotEmpty()) {
                Text(
                    text = code.tags.sorted().joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun NameLibraryDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
