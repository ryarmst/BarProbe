package dev.barcodeworkbench.feature.fuzz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.barcode.engine.Mutator
import dev.barcodeworkbench.core.model.CodeLibrary
import dev.barcodeworkbench.core.model.CodeRepository
import dev.barcodeworkbench.core.model.CodeSource
import dev.barcodeworkbench.core.model.EscapeCodec
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FuzzUiState(
    val available: Boolean = true,
    val symbologyId: SymbologyId = SymbologyId.QR_CODE,
    val base: String = "https://example.com",
    val inputMode: InputMode = InputMode.UNICODE,
    /** Cases produced this session, in order. Navigation is over this cache, so
     *  Previous never has to regenerate anything. */
    val history: List<FuzzCase> = emptyList(),
    val cursor: Int = -1,
    /** Set when the last request found nothing encodable, so the screen can explain. */
    val exhausted: FuzzOutcome.NoneEncodable? = null,
    val working: Boolean = false,
    val libraries: List<CodeLibrary> = emptyList(),
    val message: String? = null,
) {
    val spec get() = SymbologyRegistry[symbologyId]
    val fuzzability get() = Fuzzability.of(spec)
    val current: FuzzCase? get() = history.getOrNull(cursor)
    val canGoBack: Boolean get() = cursor > 0
    val position: Int get() = if (cursor < 0) 0 else cursor + 1
}

@HiltViewModel
class FuzzViewModel @Inject constructor(
    private val engine: FuzzEngine,
    private val mutator: Mutator,
    private val repository: CodeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FuzzUiState(available = mutator.isAvailable()))
    val state: StateFlow<FuzzUiState> = _state.asStateFlow()

    // The radamsa seed stream. It only advances; it does not address into the
    // history, which is why Previous reads the cache rather than re-seeding.
    private var seed: Int = 0

    init {
        viewModelScope.launch {
            repository.observeLibraries().collect { libs ->
                _state.value = _state.value.copy(libraries = libs)
            }
        }
    }

    fun setSymbology(id: SymbologyId) = resetStream { it.copy(symbologyId = id) }

    fun setBase(base: String) = resetStream { it.copy(base = base) }

    fun setInputMode(mode: InputMode) = resetStream { it.copy(inputMode = mode) }

    /** A base or format change invalidates the whole stream, so history is cleared. */
    private fun resetStream(edit: (FuzzUiState) -> FuzzUiState) {
        seed = 0
        _state.value = edit(_state.value).copy(
            history = emptyList(),
            cursor = -1,
            exhausted = null,
            message = null,
        )
    }

    fun previous() {
        val s = _state.value
        if (s.canGoBack) {
            _state.value = s.copy(cursor = s.cursor - 1, exhausted = null)
        }
    }

    fun next() {
        val s = _state.value
        // Forward through cached history first; only generate at the frontier.
        if (s.cursor < s.history.lastIndex) {
            _state.value = s.copy(cursor = s.cursor + 1, exhausted = null)
            return
        }
        if (s.working) return

        val base = expandBase(s.base)
        val symbology = s.symbologyId
        val startSeed = seed
        _state.value = s.copy(working = true, exhausted = null, message = null)

        viewModelScope.launch {
            // Mutation and encoding are both native; keep them off the main thread.
            val outcome = withContext(Dispatchers.Default) {
                engine.next(base, symbology, startSeed)
            }
            val now = _state.value
            _state.value = when (outcome) {
                is FuzzOutcome.Produced -> {
                    seed = outcome.nextSeed
                    val history = now.history + outcome.case
                    now.copy(working = false, history = history, cursor = history.lastIndex)
                }
                is FuzzOutcome.NoneEncodable -> {
                    seed = outcome.nextSeed
                    now.copy(working = false, exhausted = outcome)
                }
            }
        }
    }

    fun saveCurrent(libraryName: String) {
        val s = _state.value
        val case = s.current ?: return
        val name = libraryName.trim().ifEmpty { DEFAULT_LIBRARY }

        viewModelScope.launch {
            val outcome = runCatching {
                val libraryId = repository.libraryIdFor(name)
                repository.save(
                    SavedCode(
                        id = 0,
                        libraryId = libraryId,
                        symbologyId = s.symbologyId,
                        // Stored as raw bytes: the mutation is data, and the bytes
                        // are the reproducible artifact.
                        payload = Payload(bytes = case.payload, mode = InputMode.BINARY),
                        label = "fuzz: ${s.base.take(LABEL_CHARS)}",
                        notes = "Fuzzed from \"${s.base}\" (radamsa seed ${case.seed})",
                        source = CodeSource.FUZZED,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            _state.value = _state.value.copy(
                message = outcome.fold(
                    onSuccess = { "Saved to '$name'" },
                    onFailure = { "Save failed: ${it.message}" },
                ),
            )
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /**
     * Base text to bytes. Escapes are honoured so a base can carry bytes no keyboard
     * offers (`\x1D`, `\xFF`); plain text falls through as its UTF-8 bytes. The mode
     * governs only how the base is read, not how mutations are encoded -- those are
     * always fed back as raw bytes.
     */
    private fun expandBase(base: String): ByteArray {
        val parsed = EscapeCodec.parse(base)
        return if (parsed.isValid) parsed.dataBytes() else base.toByteArray(Charsets.UTF_8)
    }

    private companion object {
        const val DEFAULT_LIBRARY = "Fuzzing"
        const val LABEL_CHARS = 40
    }
}
