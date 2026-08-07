package dev.barcodeworkbench.core.designsystem.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Destination picker for saving a code into a library.
 *
 * Shared by the scanner and the generator so both behave identically. Existing
 * libraries are one-tap chips with a free-text field beside them, because the two
 * common cases are "the library I always use" and "a fresh one for this job", and
 * neither should need a separate dialog.
 */
@Composable
fun SaveToLibraryPicker(
    libraryNames: List<String>,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Save to library",
) {
    var newName by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium)

        if (libraryNames.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                libraryNames.forEach { name ->
                    AssistChip(onClick = { onSave(name) }, label = { Text(name) })
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(if (libraryNames.isEmpty()) "Library name" else "New library") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    onSave(newName)
                    newName = ""
                },
                enabled = newName.isNotBlank(),
            ) {
                Text("Save")
            }
        }
    }
}
