package dev.barcodeworkbench.feature.generator.batch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.barcode.render.ExportFormat
import dev.barcodeworkbench.barcode.render.RenderSpec
import dev.barcodeworkbench.barcode.render.SheetLayout
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.SymbologyId
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the batch flow should produce. */
enum class BatchOutput { ZIP, PDF_SHEET, LIBRARY }

data class BatchUiState(
    val fileName: String? = null,
    val parsed: WordlistParseResult? = null,
    val preview: BatchPreview? = null,
    val isWorking: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val output: BatchOutput = BatchOutput.ZIP,
    val zipFormat: ExportFormat = ExportFormat.PNG,
    val libraryName: String = "Batch import",
    val message: String? = null,
) {
    val readyCount: Int get() = preview?.encoded?.size ?: 0
    val rejectedCount: Int get() = preview?.rejected?.size ?: 0
    val canProduce: Boolean get() = !isWorking && (preview?.hasAnything == true)
    val progressFraction: Float
        get() = if (progressTotal <= 0) 0f else progressDone.toFloat() / progressTotal
}

/**
 * Drives batch generation.
 *
 * The preview encode runs before any file is written so the user learns which rows
 * fail, and why, in advance. Discovering on row 900 of 1000 that a payload was
 * invalid, after a file already exists, is the outcome this ordering prevents.
 */
@HiltViewModel
class BatchViewModel @Inject constructor(
    private val generator: BatchGenerator,
    private val libraryWriter: BatchLibraryWriter,
) : ViewModel() {

    private val _state = MutableStateFlow(BatchUiState())
    val state: StateFlow<BatchUiState> = _state.asStateFlow()

    private var work: Job? = null

    /** Parses a picked wordlist and immediately runs the preview encode. */
    fun load(fileName: String?, content: String, defaultSymbology: SymbologyId) {
        work?.cancel()
        val parsed = WordlistParser.parse(fileName, content)
        _state.value = _state.value.copy(
            fileName = fileName,
            parsed = parsed,
            preview = null,
            message = null,
            progressDone = 0,
            progressTotal = parsed.entries.size,
        )
        if (parsed.entries.isEmpty()) {
            _state.value = _state.value.copy(message = "No usable rows found in this file")
            return
        }
        runPreview(defaultSymbology)
    }

    private fun runPreview(defaultSymbology: SymbologyId) {
        val parsed = _state.value.parsed ?: return
        _state.value = _state.value.copy(isWorking = true)
        work = viewModelScope.launch {
            val preview = withContext(Dispatchers.Default) {
                generator.preview(parsed, defaultSymbology) { done, total ->
                    _state.value = _state.value.copy(progressDone = done, progressTotal = total)
                }
            }
            _state.value = _state.value.copy(preview = preview, isWorking = false)
        }
    }

    fun setOutput(output: BatchOutput) {
        _state.value = _state.value.copy(output = output, message = null)
    }

    fun setZipFormat(format: ExportFormat) {
        _state.value = _state.value.copy(zipFormat = format)
    }

    fun setLibraryName(name: String) {
        _state.value = _state.value.copy(libraryName = name)
    }

    fun cancel() {
        work?.cancel()
        _state.value = _state.value.copy(isWorking = false, message = "Cancelled")
    }

    /** Writes the chosen file output to a destination the user already picked. */
    fun produceFile(out: OutputStream, onFinished: () -> Unit) {
        val current = _state.value
        val encoded = current.preview?.encoded.orEmpty()
        if (encoded.isEmpty()) return

        _state.value = current.copy(isWorking = true, progressDone = 0, progressTotal = encoded.size)
        work = viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.Default) {
                    out.use { stream ->
                        when (current.output) {
                            BatchOutput.ZIP -> generator.writeZip(
                                encoded = encoded,
                                format = current.zipFormat,
                                spec = RenderSpec.export(),
                                out = stream,
                            ) { done, total ->
                                _state.value =
                                    _state.value.copy(progressDone = done, progressTotal = total)
                            }

                            BatchOutput.PDF_SHEET -> generator.writePdfSheet(
                                encoded = encoded,
                                spec = RenderSpec.export(),
                                layout = SheetLayout(),
                                out = stream,
                            )

                            // Not a file destination; handled by produceToLibrary.
                            BatchOutput.LIBRARY -> Unit
                        }
                    }
                }
            }
            _state.value = _state.value.copy(
                isWorking = false,
                message = outcome.fold(
                    onSuccess = { "Wrote ${encoded.size} symbols" },
                    onFailure = { "Failed: ${it.message}" },
                ),
            )
            onFinished()
        }
    }

    fun produceToLibrary() {
        val current = _state.value
        val encoded = current.preview?.encoded.orEmpty()
        if (encoded.isEmpty()) return
        val name = current.libraryName.trim().ifEmpty { "Batch import" }

        _state.value = current.copy(isWorking = true)
        work = viewModelScope.launch {
            val outcome = runCatching {
                libraryWriter.write(name, encoded, InputMode.UNICODE)
            }
            _state.value = _state.value.copy(
                isWorking = false,
                message = outcome.fold(
                    onSuccess = { "Saved $it entries to '$name'" },
                    onFailure = { "Failed: ${it.message}" },
                ),
            )
        }
    }

    /** Suggested filename for the current output type. */
    fun suggestedFileName(): String = when (_state.value.output) {
        BatchOutput.ZIP -> "barcodes.zip"
        BatchOutput.PDF_SHEET -> "barcode-sheet.pdf"
        BatchOutput.LIBRARY -> ""
    }

    fun outputMimeType(): String = when (_state.value.output) {
        BatchOutput.ZIP -> "application/zip"
        BatchOutput.PDF_SHEET -> "application/pdf"
        BatchOutput.LIBRARY -> "*/*"
    }
}
