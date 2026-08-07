package dev.barcodeworkbench.core.model

/** Stable identifier persisted in the database and in backup files. */
enum class SymbologyId {
    QR_CODE,
    MICRO_QR,
    RMQR,
    DATA_MATRIX,
    AZTEC,
    PDF417,
    MICRO_PDF417,
    DOTCODE,
    MAXICODE,
    CODE_128,
    GS1_128,
    CODE_39,
    CODE_93,
    CODABAR,
    ITF,
    ITF_14,
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    CODE_11,
    MSI_PLESSEY,
    TELEPEN,
    DATABAR_OMNI,
    DATABAR_LIMITED,
    DATABAR_EXPANDED,
}

enum class Dimension { LINEAR, MATRIX }

enum class Category { GENERAL, RETAIL, LOGISTICS, INDUSTRIAL, GS1 }

enum class CheckDigitBehaviour(
    /**
     * Reader-facing wording, kept here rather than in a screen so the generator and
     * the reference page cannot describe the same rule differently.
     */
    val description: String,
) {
    /** No check digit defined. */
    NONE("None"),

    /** Always computed and appended by the encoder. */
    AUTOMATIC("Computed for you"),

    /** Caller may request one. */
    OPTIONAL("Optional; can be added on request"),

    /** Must be present in the input and is validated. */
    REQUIRED_IN_INPUT("Must be supplied and is validated"),
}

/**
 * Everything the app needs to know about one symbology.
 *
 * The UI is generated from this table, so adding a format is one row plus, where
 * relevant, a reader-format mapping. No screen needs editing.
 *
 * [zintSymbolId] values are asserted against the linked libzint's own
 * `ZBarcode_BarcodeName` by test. That check exists because a transcription
 * error here is silent and confusing: EAN-13 was initially recorded as 11, which
 * is the 2-digit add-on symbology, and surfaced only as a baffling
 * "maximum 2 characters" error.
 */
data class SymbologySpec(
    val id: SymbologyId,
    val displayName: String,
    val zintSymbolId: Int,
    /** libzint's canonical constant name, used by the drift-detection test. */
    val zintConstantName: String,
    /**
     * Whether the reader engine can decode this. Null means generate-only:
     * DotCode is the current example, since zxing-cpp does not read it.
     */
    val readerFormat: String?,
    val dimension: Dimension,
    val category: Category,
    val charsetRule: CharsetRule,
    val lengthRule: LengthRule,
    val checkDigit: CheckDigitBehaviour,
    val supportsGs1: Boolean,
    val supportsEci: Boolean,
    val supportsStructuredAppend: Boolean,
    /** Code 128 codeset escapes and FNC1 insertion apply only to some formats. */
    val supportsCodesetEscapes: Boolean,
    val sampleValue: String,
    val notes: String = "",
) {
    val isReadable: Boolean get() = readerFormat != null
    val isSquareish: Boolean get() = dimension == Dimension.MATRIX
}
