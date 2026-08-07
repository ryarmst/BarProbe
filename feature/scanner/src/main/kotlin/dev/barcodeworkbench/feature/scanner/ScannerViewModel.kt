package dev.barcodeworkbench.feature.scanner

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.barcode.engine.BarcodeDecoder
import dev.barcodeworkbench.barcode.engine.CameraFrameDecoder
import dev.barcodeworkbench.barcode.engine.DecodeOptions
import dev.barcodeworkbench.barcode.engine.DecodedBarcode
import dev.barcodeworkbench.barcode.engine.forLiveFrames
import dev.barcodeworkbench.barcode.engine.forStillImage
import dev.barcodeworkbench.core.model.CodeLibrary
import dev.barcodeworkbench.core.model.CodeRepository
import dev.barcodeworkbench.core.model.CodeSource
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import dev.barcodeworkbench.feature.scanner.domain.GateDecision
import dev.barcodeworkbench.feature.scanner.domain.ScanStabilityGate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long the scanner keeps going after a successful read. */
enum class ScanMode {
    /** Stop on the first accepted symbol. */
    SINGLE,

    /** Keep scanning and accumulate distinct symbols. */
    CONTINUOUS,
}

/** A captured read plus when it happened. */
data class CapturedScan(
    val barcode: DecodedBarcode,
    val capturedAt: Long,
)

data class ScannerUiState(
    val mode: ScanMode = ScanMode.SINGLE,
    val enabledSymbologies: Set<SymbologyId> = SymbologyRegistry.readable.map { it.id }.toSet(),
    val torchOn: Boolean = false,
    val torchAvailable: Boolean = false,
    val captures: List<CapturedScan> = emptyList(),
    /** The read currently shown in the inspector. */
    val inspecting: CapturedScan? = null,
    val lastRejection: GateDecision? = null,
    val lastFrameDecodeMs: Int = 0,
    val isDecodingFile: Boolean = false,
    val libraries: List<CodeLibrary> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val captureCount: Int get() = captures.size
    val hasCaptures: Boolean get() = captures.isNotEmpty()

    /** True once a single-mode scan has landed and the camera should stop. */
    val isFinished: Boolean get() = mode == ScanMode.SINGLE && captures.isNotEmpty()
}

/**
 * Drives the scanner.
 *
 * Frame analysis runs on CameraX's analyzer executor, not in a coroutine: the
 * frame must be released promptly and the decode is already native and fast. State
 * updates from that thread go through [MutableStateFlow], which is safe to write
 * from any thread.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val frameDecoder: CameraFrameDecoder,
    private val stillDecoder: BarcodeDecoder,
    private val repository: CodeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLibraries().collect { libraries ->
                _state.value = _state.value.copy(libraries = libraries)
            }
        }
    }

    /**
     * Saves a scan into a library, creating it by name when needed.
     *
     * The payload is stored as raw bytes in binary mode rather than as text. A scanned
     * symbol's content is whatever bytes it carried, and re-interpreting it as text
     * here would corrupt binary and mixed-encoding payloads on the way in.
     */
    fun saveToLibrary(capture: CapturedScan, libraryName: String) {
        val name = libraryName.trim().ifEmpty { DEFAULT_LIBRARY }
        val symbology = capture.barcode.symbology
        if (symbology == null) {
            _state.value = _state.value.copy(
                message = "Cannot save: ${capture.barcode.rawFormatName} has no registry entry",
            )
            return
        }
        _state.value = _state.value.copy(isSaving = true, message = null)
        viewModelScope.launch {
            val outcome = runCatching {
                val libraryId = repository.libraryIdFor(name)
                repository.save(
                    SavedCode(
                        id = 0,
                        libraryId = libraryId,
                        symbologyId = symbology,
                        payload = Payload(capture.barcode.bytes, InputMode.BINARY),
                        label = capture.barcode.text.take(LABEL_CHARS).ifBlank { null },
                        notes = buildString {
                            append("Scanned as ${capture.barcode.rawFormatName}")
                            capture.barcode.symbologyIdentifier?.let { append(", AIM ", it) }
                            capture.barcode.errorCorrectionLevel?.let { append(", EC ", it) }
                        },
                        source = CodeSource.SCANNED,
                        createdAt = capture.capturedAt,
                    ),
                )
            }
            _state.value = _state.value.copy(
                isSaving = false,
                message = outcome.fold(
                    onSuccess = { "Saved to '$name'" },
                    onFailure = { "Save failed: ${it.message}" },
                ),
            )
        }
    }

    private var gate = ScanStabilityGate()

    /**
     * Guards against overlapping analysis. CameraX may deliver another frame while
     * one is still being decoded; dropping the newcomer is correct, because the
     * freshest frame is always the most useful one.
     */
    private val analysing = AtomicBoolean(false)

    /**
     * Analyses one frame. Must be called from the analyzer executor, and the caller
     * remains responsible for closing [image].
     */
    fun analyseFrame(image: ImageProxy) {
        if (_state.value.isFinished) return
        if (!analysing.compareAndSet(false, true)) return
        try {
            val options = DecodeOptions.forLiveFrames(_state.value.enabledSymbologies)
            val results = frameDecoder.decodeFrame(image, options)
            val decodeMs = frameDecoder.lastFrameDecodeMillis()

            val candidate = results.firstOrNull()
            if (candidate == null) {
                _state.value = _state.value.copy(lastFrameDecodeMs = decodeMs)
                return
            }

            when (val decision = gate.evaluate(candidate.symbology, candidate.bytes)) {
                GateDecision.ACCEPT -> accept(candidate, decodeMs)
                else -> _state.value = _state.value.copy(
                    lastRejection = decision,
                    lastFrameDecodeMs = decodeMs,
                )
            }
        } finally {
            analysing.set(false)
        }
    }

    private fun accept(barcode: DecodedBarcode, decodeMs: Int) {
        val capture = CapturedScan(barcode, System.currentTimeMillis())
        val current = _state.value
        _state.value = current.copy(
            captures = current.captures + capture,
            // A single-mode scan opens the inspector immediately, since inspecting
            // it is the whole point; continuous mode keeps the camera unobstructed.
            inspecting = if (current.mode == ScanMode.SINGLE) capture else current.inspecting,
            lastRejection = null,
            lastFrameDecodeMs = decodeMs,
        )
    }

    fun setMode(mode: ScanMode) {
        gate = ScanStabilityGate()
        _state.value = _state.value.copy(
            mode = mode,
            captures = emptyList(),
            inspecting = null,
            lastRejection = null,
            message = null,
        )
    }

    fun toggleSymbology(id: SymbologyId) {
        val current = _state.value.enabledSymbologies
        val updated = if (id in current) current - id else current + id
        // An empty set would mean "all formats" to the engine, the opposite of what
        // clearing every toggle implies.
        if (updated.isEmpty()) {
            _state.value = _state.value.copy(message = "At least one symbology must stay enabled")
            return
        }
        _state.value = _state.value.copy(enabledSymbologies = updated, message = null)
    }

    fun enableAllSymbologies() {
        _state.value = _state.value.copy(
            enabledSymbologies = SymbologyRegistry.readable.map { it.id }.toSet(),
        )
    }

    fun setTorch(on: Boolean) {
        _state.value = _state.value.copy(torchOn = on)
    }

    fun setTorchAvailable(available: Boolean) {
        _state.value = _state.value.copy(
            torchAvailable = available,
            torchOn = _state.value.torchOn && available,
        )
    }

    fun inspect(capture: CapturedScan?) {
        _state.value = _state.value.copy(inspecting = capture)
    }

    fun clearCaptures() {
        gate.resetSession()
        _state.value = _state.value.copy(
            captures = emptyList(),
            inspecting = null,
            lastRejection = null,
            message = null,
        )
    }

    fun restartAfterSingleScan() {
        gate.resetSession()
        _state.value = _state.value.copy(captures = emptyList(), inspecting = null)
    }

    /**
     * Decodes an image the user picked.
     *
     * Uses the still-image preset, which enables every retry the engine offers:
     * there is no next frame coming, so it is worth working hard on this one.
     */
    fun decodeImage(bitmap: Bitmap) {
        _state.value = _state.value.copy(isDecodingFile = true, message = null)
        viewModelScope.launch {
            val results = withContext(Dispatchers.Default) {
                stillDecoder.decode(
                    bitmap = bitmap,
                    options = DecodeOptions.forStillImage(_state.value.enabledSymbologies),
                )
            }
            val now = System.currentTimeMillis()
            val captures = results.map { CapturedScan(it, now) }
            _state.value = _state.value.copy(
                isDecodingFile = false,
                captures = _state.value.captures + captures,
                inspecting = captures.firstOrNull() ?: _state.value.inspecting,
                message = if (captures.isEmpty()) "No barcode found in that image" else null,
            )
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Engine identification for the about screen. */
    fun engineVersion(): String = stillDecoder.engineVersion()

    private companion object {
        const val DEFAULT_LIBRARY = "Scans"
        const val LABEL_CHARS = 60
    }
}
