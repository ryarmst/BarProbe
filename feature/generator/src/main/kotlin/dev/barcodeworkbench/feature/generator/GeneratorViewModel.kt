package dev.barcodeworkbench.feature.generator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.barcode.engine.EncodeOptions
import dev.barcodeworkbench.barcode.engine.EncodeRequest
import dev.barcodeworkbench.barcode.engine.EncodeResult
import dev.barcodeworkbench.core.model.CodeLibrary
import dev.barcodeworkbench.core.model.CodeRepository
import dev.barcodeworkbench.core.model.CodeSource
import dev.barcodeworkbench.core.model.EscapeCodec
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.PayloadValidator
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import dev.barcodeworkbench.core.model.SymbologySpec
import dev.barcodeworkbench.core.model.ValidationResult
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeneratorUiState(
    val symbologyId: SymbologyId = SymbologyId.QR_CODE,
    val payloadSource: String = "",
    val inputMode: InputMode = InputMode.UNICODE,
    val eci: Int? = null,
    val validation: ValidationResult? = null,
    val matrix: ModuleMatrix? = null,
    /** The encoder's own diagnostic when encoding failed. */
    val encodeError: String? = null,
    /** Set when a symbol was produced but the encoder flagged a caveat. */
    val encodeWarning: String? = null,
    val isEncoding: Boolean = false,
    /** Show escape sequences literally rather than as readable chips. */
    val showRawEscapes: Boolean = false,
    val showInspector: Boolean = false,
    val libraries: List<CodeLibrary> = emptyList(),
    val saveMessage: String? = null,
) {
    val spec: SymbologySpec get() = SymbologyRegistry[symbologyId]

    /** A symbol exists and can be viewed, exported or saved. */
    val hasSymbol: Boolean get() = matrix != null

    /** The message to show under the field, most actionable first. */
    val fieldError: String?
        get() = validation?.takeIf { !it.isValid }?.firstMessage ?: encodeError
}

/**
 * Drives the generator.
 *
 * Validation and encoding are debounced rather than run per keystroke. Encoding a
 * large symbol is not free, and a partially typed payload is usually invalid
 * anyway, so reacting to every character would spend most of its effort on states
 * the user is passing straight through.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class GeneratorViewModel @Inject constructor(
    private val encoder: BarcodeEncoder,
    private val repository: CodeRepository,
    /** Exposed for the export sheet, which needs it at the moment of writing. */
    val exporter: dev.barcodeworkbench.barcode.render.SymbolExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(GeneratorUiState())
    val state: StateFlow<GeneratorUiState> = _state.asStateFlow()

    /** Inputs that require a re-encode, collapsed so identical states are skipped. */
    private data class EncodeInputs(
        val symbologyId: SymbologyId,
        val source: String,
        val mode: InputMode,
        val eci: Int?,
    )

    init {
        viewModelScope.launch {
            repository.observeLibraries().collect { libraries ->
                _state.value = _state.value.copy(libraries = libraries)
            }
        }
        viewModelScope.launch {
            _state
                .map { EncodeInputs(it.symbologyId, it.payloadSource, it.inputMode, it.eci) }
                .distinctUntilChanged()
                .debounce(ENCODE_DEBOUNCE_MS)
                .collect { inputs -> refresh(inputs) }
        }
    }

    fun selectSymbology(id: SymbologyId) {
        val spec = SymbologyRegistry[id]
        _state.value = _state.value.copy(
            symbologyId = id,
            // A mode the new symbology cannot honour would silently produce
            // confusing encoder errors, so fall back when it is unsupported.
            inputMode = coerceMode(_state.value.inputMode, spec),
            eci = _state.value.eci?.takeIf { spec.supportsEci },
        )
    }

    fun updatePayload(source: String) {
        _state.value = _state.value.copy(payloadSource = source)
    }

    fun setInputMode(mode: InputMode) {
        _state.value = _state.value.copy(inputMode = coerceMode(mode, _state.value.spec))
    }

    fun setEci(eci: Int?) {
        if (!_state.value.spec.supportsEci) return
        _state.value = _state.value.copy(eci = eci)
    }

    fun toggleRawEscapes() {
        _state.value = _state.value.copy(showRawEscapes = !_state.value.showRawEscapes)
    }

    fun toggleInspector() {
        _state.value = _state.value.copy(showInspector = !_state.value.showInspector)
    }

    /** Loads the registry's sample value, so a format is never a blank page. */
    fun useSampleValue() {
        val spec = _state.value.spec
        _state.value = _state.value.copy(
            payloadSource = spec.sampleValue,
            inputMode = if (spec.supportsGs1 && spec.sampleValue.startsWith("[")) {
                InputMode.GS1
            } else {
                _state.value.inputMode
            },
        )
    }

    fun clearPayload() {
        _state.value = _state.value.copy(payloadSource = "")
    }

    /**
     * Saves the current code into a library, creating it by name when needed.
     *
     * Stores the authored escape source rather than the expanded bytes, matching what
     * the batch flow does, so the entry stays editable and re-renders exactly as it
     * was written.
     */
    fun saveToLibrary(libraryName: String) {
        val current = _state.value
        if (!current.hasSymbol) return
        val name = libraryName.trim().ifEmpty { DEFAULT_LIBRARY }

        viewModelScope.launch {
            val outcome = runCatching {
                val libraryId = repository.libraryIdFor(name)
                repository.save(
                    SavedCode(
                        id = 0,
                        libraryId = libraryId,
                        symbologyId = current.symbologyId,
                        payload = Payload(
                            bytes = current.payloadSource.toByteArray(Charsets.UTF_8),
                            mode = current.inputMode,
                            eci = current.eci,
                            escapesEnabled = EscapeCodec.containsEscapes(current.payloadSource),
                        ),
                        label = current.payloadSource.take(LABEL_CHARS),
                        source = CodeSource.GENERATED,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            _state.value = _state.value.copy(
                saveMessage = outcome.fold(
                    onSuccess = { "Saved to '$name'" },
                    onFailure = { "Save failed: ${it.message}" },
                ),
            )
        }
    }

    fun dismissSaveMessage() {
        _state.value = _state.value.copy(saveMessage = null)
    }

    private suspend fun refresh(inputs: EncodeInputs) {
        val spec = SymbologyRegistry[inputs.symbologyId]

        if (inputs.source.isEmpty()) {
            _state.value = _state.value.copy(
                validation = null,
                matrix = null,
                encodeError = null,
                encodeWarning = null,
                isEncoding = false,
            )
            return
        }

        val validation = PayloadValidator.validate(spec, inputs.source, inputs.mode)
        if (!validation.isValid) {
            // No point attempting an encode that is already known to fail; the
            // validator's message is more specific than the encoder's would be.
            _state.value = _state.value.copy(
                validation = validation,
                matrix = null,
                encodeError = null,
                encodeWarning = null,
                isEncoding = false,
            )
            return
        }

        _state.value = _state.value.copy(validation = validation, isEncoding = true)

        val result = withContext(Dispatchers.Default) {
            encoder.encode(
                EncodeRequest(
                    symbology = inputs.symbologyId,
                    payload = Payload(
                        bytes = inputs.source.toByteArray(Charsets.UTF_8),
                        mode = inputs.mode,
                        eci = inputs.eci,
                        // Escape expansion is enabled only when there is something
                        // to expand, so a literal backslash in plain text is not
                        // reinterpreted unexpectedly.
                        escapesEnabled = EscapeCodec.containsEscapes(inputs.source),
                    ),
                    options = EncodeOptions(dotty = spec.id == SymbologyId.DOTCODE),
                ),
            )
        }

        // Discard a result whose inputs are already stale.
        val current = _state.value
        if (current.symbologyId != inputs.symbologyId ||
            current.payloadSource != inputs.source ||
            current.inputMode != inputs.mode ||
            current.eci != inputs.eci
        ) {
            return
        }

        _state.value = when (result) {
            is EncodeResult.Success -> current.copy(
                matrix = result.matrix,
                encodeError = null,
                encodeWarning = result.warning,
                isEncoding = false,
            )
            is EncodeResult.Failure -> current.copy(
                matrix = null,
                encodeError = result.message,
                encodeWarning = null,
                isEncoding = false,
            )
        }
    }

    private fun coerceMode(mode: InputMode, spec: SymbologySpec): InputMode = when {
        mode == InputMode.GS1 && !spec.supportsGs1 -> InputMode.UNICODE
        else -> mode
    }

    private companion object {
        const val ENCODE_DEBOUNCE_MS = 180L
        const val DEFAULT_LIBRARY = "Generated"
        const val LABEL_CHARS = 60
    }
}
