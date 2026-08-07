package dev.barcodeworkbench.feature.catalogue.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.core.designsystem.counted
import dev.barcodeworkbench.core.model.backup.BackupError
import dev.barcodeworkbench.core.model.backup.ImportPlan
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupUiState(
    val isWorking: Boolean = false,
    val deduplicate: Boolean = true,
    val plan: ImportPlan? = null,
    val envelopeSummary: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun suggestedFileName(): String = manager.suggestedFileName()

    /** Clears any message left over from an earlier attempt. */
    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, isError = false)
    }

    fun toggleDeduplicate() {
        _state.value = _state.value.copy(deduplicate = !_state.value.deduplicate)
    }

    fun export(out: OutputStream) {
        _state.value = _state.value.copy(isWorking = true, message = null, isError = false)
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { out.use { manager.export(it, APP_VERSION) } }
            }
            _state.value = _state.value.copy(
                isWorking = false,
                message = outcome.fold(
                    onSuccess = { "Backup written" },
                    onFailure = { "Export failed: ${it.message}" },
                ),
                isError = outcome.isFailure,
            )
        }
    }

    fun preview(input: InputStream) {
        _state.value = _state.value.copy(
            isWorking = true,
            plan = null,
            message = null,
            isError = false,
        )
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    input.use { manager.preview(it, _state.value.deduplicate) }
                }
            }
            outcome.fold(
                onSuccess = { (envelope, plan) ->
                    _state.value = _state.value.copy(
                        isWorking = false,
                        plan = plan,
                        envelopeSummary = "From app ${envelope.appVersion}, " +
                            "exported ${envelope.exportedAt}, " +
                            "${envelope.libraryCount} libraries",
                        message = null,
                        isError = false,
                    )
                },
                onFailure = { error ->
                    _state.value = _state.value.copy(
                        isWorking = false,
                        plan = null,
                        // BackupError messages are written for users; anything else
                        // gets a generic prefix so a stack-trace string is not shown.
                        message = if (error is BackupError) {
                            error.message
                        } else {
                            "Could not read that file: ${error.message}"
                        },
                        isError = true,
                    )
                },
            )
        }
    }

    fun confirmImport() {
        val plan = _state.value.plan ?: return
        _state.value = _state.value.copy(isWorking = true)
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { manager.applyPlan(plan) }
            }
            _state.value = _state.value.copy(
                isWorking = false,
                plan = null,
                message = outcome.fold(
                    onSuccess = { result ->
                        buildString {
                            append("Imported ${counted(result.imported, "code")}")
                            if (result.skippedDuplicates > 0) {
                                append(", skipped ${result.skippedDuplicates} duplicates")
                            }
                            if (result.skippedUnknown > 0) {
                                append(", skipped ${result.skippedUnknown} unsupported")
                            }
                        }
                    },
                    onFailure = { "Import failed: ${it.message}" },
                ),
                isError = outcome.isFailure,
            )
        }
    }

    private companion object {
        const val APP_VERSION = "0.1.0-dev"
    }
}
