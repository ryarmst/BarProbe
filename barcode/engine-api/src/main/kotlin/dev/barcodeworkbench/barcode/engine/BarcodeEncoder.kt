package dev.barcodeworkbench.barcode.engine

import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SymbologyId

/**
 * Options passed through to the encoder.
 *
 * Null means "leave the engine's own default alone". That distinction matters:
 * the Phase 1 spike showed that substituting a sentinel such as -1 is taken
 * literally by libzint and rejected by PDF417, Aztec and rMQR as an invalid row
 * count or version.
 */
data class EncodeOptions(
    /** Error-correction level, where the symbology defines one. */
    val errorCorrection: Int? = null,
    /** Symbol version or size variant. */
    val version: Int? = null,
    /** Additional symbology-specific selector, such as a QR mask. */
    val variant: Int? = null,
    /** Overall height in X-dimensions for linear symbologies. */
    val heightUnits: Float? = null,
    /** Include the standards-compliant quiet zone. */
    val quietZones: Boolean = true,
    /** Render as dots rather than squares, for DotCode. */
    val dotty: Boolean = false,
    /** Promote engine warnings to hard failures. */
    val strict: Boolean = false,
)

data class EncodeRequest(
    val symbology: SymbologyId,
    val payload: Payload,
    val options: EncodeOptions = EncodeOptions(),
)

sealed interface EncodeResult {

    /**
     * A symbol was produced. [warning] carries the engine's advisory text when it
     * flagged a caveat but still encoded successfully -- for example a
     * non-compliant height or an automatically inserted ECI.
     */
    data class Success(
        val matrix: ModuleMatrix,
        val warning: String? = null,
    ) : EncodeResult

    /**
     * Encoding failed. [message] is the engine's own diagnostic, passed through
     * rather than replaced, because it names the offending character position and
     * is far more useful than anything generic we could substitute.
     */
    data class Failure(
        val code: Int,
        val message: String,
    ) : EncodeResult
}

/**
 * Turns a payload into a module matrix.
 *
 * Implementations must not rasterise; rendering is a separate concern so that one
 * renderer set can serve screen, PNG, SVG and PDF with identical geometry.
 */
interface BarcodeEncoder {

    fun encode(request: EncodeRequest): EncodeResult

    /** The engine's version, for display on the attribution screen. */
    fun engineVersion(): String

    /**
     * Whether this engine can encode [symbology] at all. Lets the registry be
     * cross-checked against the linked engine instead of trusted blindly.
     */
    fun supports(symbology: SymbologyId): Boolean
}
