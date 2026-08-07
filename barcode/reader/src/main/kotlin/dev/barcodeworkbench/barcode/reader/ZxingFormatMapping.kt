package dev.barcodeworkbench.barcode.reader

import dev.barcodeworkbench.barcode.engine.DecodedBarcode
import dev.barcodeworkbench.barcode.engine.DecodedContentType
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import zxingcpp.BarcodeReader

/**
 * Translation between the registry and zxing-cpp, shared by the still-image and
 * live-frame decoders so the two cannot disagree about which formats are enabled
 * or how a result maps back to a symbology.
 */
object ZxingFormatMapping {

    /**
     * Registry symbology to engine format.
     *
     * Generate-only formats are absent by construction: the registry records a null
     * reader format for DotCode, Code 11 and MSI Plessey, so they can never reach a
     * decode request. Any registry entry naming a format this engine build does not
     * define is dropped rather than throwing, which keeps a future registry
     * addition from crashing the scanner before the engine catches up.
     */
    private val formatBySymbology: Map<SymbologyId, BarcodeReader.Format> =
        SymbologyRegistry.readable.mapNotNull { spec ->
            val name = spec.readerFormat ?: return@mapNotNull null
            val format = runCatching { BarcodeReader.Format.valueOf(name) }.getOrNull()
            format?.let { spec.id to it }
        }.toMap()

    /** Registry ids that require a positive GS1 signal before being selected. */
    private val gs1Variants = setOf(SymbologyId.GS1_128)

    /**
     * Engine format back to registry symbology.
     *
     * The mapping is many-to-one: Code 128 and GS1-128 are the same symbology and the
     * engine reports both as CODE_128. An `associate` here would be last-wins and
     * silently label every plain Code 128 as GS1-128, so the base entry is chosen
     * explicitly and [refine] disambiguates afterwards.
     */
    private val symbologyByFormatName: Map<String, SymbologyId> =
        formatBySymbology.entries
            .groupBy { it.value.name }
            .mapValues { (_, candidates) ->
                // Prefer the non-GS1 reading as the base; GS1 is the special case and
                // has to be positively identified rather than assumed.
                candidates.map { it.key }.minByOrNull { if (it in gs1Variants) 1 else 0 }!!
            }

    /** Format names that more than one registry entry claims. */
    val ambiguousFormatNames: Set<String> =
        formatBySymbology.entries
            .groupBy { it.value.name }
            .filterValues { it.size > 1 }
            .keys

    /**
     * Disambiguates using the AIM symbology identifier.
     *
     * `]C1` is GS1-128; `]C0` and `]C2` are plain Code 128. The identifier is the only
     * thing in the decoded result that distinguishes them, because the difference is a
     * leading FNC1 rather than a different symbology.
     */
    fun refine(base: SymbologyId?, aimIdentifier: String?): SymbologyId? = when (base) {
        SymbologyId.CODE_128, SymbologyId.GS1_128 ->
            if (aimIdentifier == AIM_GS1_128) SymbologyId.GS1_128 else SymbologyId.CODE_128
        else -> base
    }

    const val AIM_GS1_128 = "]C1"

    val readableSymbologies: Set<SymbologyId> = formatBySymbology.keys

    fun formatFor(id: SymbologyId): BarcodeReader.Format? = formatBySymbology[id]

    fun symbologyFor(formatName: String): SymbologyId? = symbologyByFormatName[formatName]
}

/**
 * Converts an engine result to the domain type, or null when the engine reported an
 * error for that candidate.
 */
internal fun BarcodeReader.Result.toDomain(): DecodedBarcode? {
    if (error != null) return null
    val formatName = format.name
    val aim = symbologyIdentifier?.takeIf { it.isNotBlank() }
    return DecodedBarcode(
        symbology = ZxingFormatMapping.refine(
            ZxingFormatMapping.symbologyFor(formatName),
            aim,
        ),
        rawFormatName = formatName,
        // Bytes are authoritative; text is a convenience rendering that is lossy
        // for binary payloads.
        bytes = bytes ?: ByteArray(0),
        text = text.orEmpty(),
        contentType = when (contentType) {
            BarcodeReader.ContentType.TEXT -> DecodedContentType.TEXT
            BarcodeReader.ContentType.BINARY -> DecodedContentType.BINARY
            BarcodeReader.ContentType.MIXED -> DecodedContentType.MIXED
            BarcodeReader.ContentType.GS1 -> DecodedContentType.GS1
            BarcodeReader.ContentType.ISO15434 -> DecodedContentType.ISO15434
            else -> DecodedContentType.UNKNOWN
        },
        corners = position.let { p ->
            listOf(
                p.topLeft.x to p.topLeft.y,
                p.topRight.x to p.topRight.y,
                p.bottomRight.x to p.bottomRight.y,
                p.bottomLeft.x to p.bottomLeft.y,
            )
        },
        orientationDegrees = orientation,
        errorCorrectionLevel = ecLevel?.takeIf { it.isNotBlank() },
        symbologyIdentifier = aim,
        sequenceIndex = sequenceIndex.takeIf { it >= 0 },
        sequenceSize = sequenceSize.takeIf { it > 0 },
        sequenceId = sequenceId?.takeIf { it.isNotBlank() },
        readerInit = readerInit,
    )
}
