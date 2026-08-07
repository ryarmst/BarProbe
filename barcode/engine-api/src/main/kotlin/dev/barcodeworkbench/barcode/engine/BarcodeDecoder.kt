package dev.barcodeworkbench.barcode.engine

import android.graphics.Bitmap
import android.graphics.Rect
import dev.barcodeworkbench.core.model.SymbologyId

/** How decoded content should be presented to the user. */
enum class DecodedTextMode {
    /** Plain text, best effort. */
    PLAIN,

    /** Non-printable bytes shown as escapes. */
    ESCAPED,

    /** Hex, for binary payloads. */
    HEX,
}

/** What the engine determined the content to be. */
enum class DecodedContentType {
    TEXT,
    BINARY,
    MIXED,
    GS1,
    ISO15434,
    UNKNOWN,
}

data class DecodeOptions(
    /**
     * Restrict to these symbologies. A smaller set is measurably faster, so the
     * scanner narrows this to what the user has enabled. Only readable formats
     * are valid here; generate-only formats such as DotCode must be excluded.
     */
    val symbologies: Set<SymbologyId>,
    /** Spend more effort per frame. Appropriate for stills, not live preview. */
    val tryHarder: Boolean = false,
    /** Retry at other orientations. */
    val tryRotate: Boolean = true,
    /** Retry inverted, for light-on-dark symbols. */
    val tryInvert: Boolean = true,
    /** Retry at reduced resolution, which helps with large or blurry captures. */
    val tryDownscale: Boolean = true,
    /** Maximum symbols to return from a single image. */
    val maxSymbols: Int = 1,
    /** Require optional check digits to validate where a symbology has them. */
    val validateOptionalChecksum: Boolean = false,
    /**
     * Minimum agreeing scan lines before a linear result is accepted. Guards
     * against single-scanline misreads within one image; cross-frame agreement is
     * handled separately by the stability gate.
     */
    val minLineCount: Int = 2,
) {
    /** Exists so preset factories can hang off [DecodeOptions] as extensions. */
    companion object
}

/**
 * One decoded symbol.
 *
 * [bytes] is authoritative and [text] is a convenience rendering. Barcodes
 * legitimately carry binary and mixed-encoding content, so anything that must
 * round-trip exactly uses the bytes.
 */
data class DecodedBarcode(
    val symbology: SymbologyId?,
    /** The engine's own format name, retained when it maps to no registry entry. */
    val rawFormatName: String,
    val bytes: ByteArray,
    val text: String,
    val contentType: DecodedContentType,
    /** Corner points in image space, for drawing an overlay. */
    val corners: List<Pair<Int, Int>> = emptyList(),
    val orientationDegrees: Int = 0,
    /** Error-correction level the symbol was encoded at, when reported. */
    val errorCorrectionLevel: String? = null,
    /** AIM symbology identifier, e.g. "]C1". */
    val symbologyIdentifier: String? = null,
    /** Structured-append grouping, when the symbol is part of a sequence. */
    val sequenceIndex: Int? = null,
    val sequenceSize: Int? = null,
    val sequenceId: String? = null,
    /** Reader-initialisation flag; relevant to device programming barcodes. */
    val readerInit: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedBarcode) return false
        return symbology == other.symbology &&
            rawFormatName == other.rawFormatName &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = symbology?.hashCode() ?: 0
        result = 31 * result + rawFormatName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

/**
 * Decodes symbols from images.
 *
 * Camera frames are adapted to [Bitmap] by the implementation module so this
 * contract stays independent of any particular camera library.
 */
interface BarcodeDecoder {

    fun decode(
        bitmap: Bitmap,
        options: DecodeOptions,
        cropRect: Rect? = null,
        rotationDegrees: Int = 0,
    ): List<DecodedBarcode>

    fun engineVersion(): String

    /** Formats this engine can decode, for cross-checking against the registry. */
    fun supportedSymbologies(): Set<SymbologyId>
}
