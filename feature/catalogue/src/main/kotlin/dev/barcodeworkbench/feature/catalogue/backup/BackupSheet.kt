package dev.barcodeworkbench.feature.catalogue.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dev.barcodeworkbench.core.designsystem.counted
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Backup and restore.
 *
 * Import is two steps by design: the file is parsed, checksum-verified and planned
 * first, and only then does the user confirm. A damaged or partly incompatible backup
 * therefore cannot half-populate a library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(
    onDismiss: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let(viewModel::export)
        }
    }

    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.let(viewModel::preview)
        }
    }

    // The ViewModel outlives the sheet, so a message from a previous attempt would
    // otherwise still be on screen when it is reopened.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.dismissMessage() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Backup and restore", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "A backup holds every library and code, compressed and " +
                    "checksummed. Payload bytes are preserved exactly, including " +
                    "control characters and binary content.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { createBackup.launch(viewModel.suggestedFileName()) },
                    enabled = !state.isWorking,
                ) {
                    Text("Export…")
                }
                OutlinedButton(
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                    enabled = !state.isWorking,
                ) {
                    Text("Import…")
                }
            }

            FilterChip(
                selected = state.deduplicate,
                onClick = viewModel::toggleDeduplicate,
                label = { Text("Skip codes already present") },
            )

            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.plan?.let { plan ->
                HorizontalDivider()
                Text("Ready to import", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = buildString {
                        append("${counted(plan.toImport.size, "code")} will be added")
                        if (plan.duplicates > 0) {
                            append(", ${plan.duplicates} already present")
                        }
                        if (plan.unknown > 0) {
                            append(", ${plan.unknown} use unsupported symbologies")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                state.envelopeSummary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = viewModel::confirmImport,
                    enabled = !state.isWorking && plan.toImport.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import ${counted(plan.toImport.size, "code")}")
                }
            }

            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
