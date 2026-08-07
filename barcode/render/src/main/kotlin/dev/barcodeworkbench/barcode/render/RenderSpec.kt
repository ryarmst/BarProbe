package dev.barcodeworkbench.barcode.render

/** Where a rendered symbol is going, which sets the quality/size defaults. */
enum class RenderPurpose {
    /** Small list thumbnail. */
    THUMBNAIL,

    /** On-screen detail view. */
    DETAIL,

    /** Saved or shared file, rendered at higher fidelity. */
    EXPORT,
}

/** Quarter-turn rotations. Long linear symbols are far easier to read rotated. */
enum class SymbolRotation(val degrees: Int) {
    NONE(0),
    CLOCKWISE_90(90),
    HALF_TURN(180),
    COUNTER_CLOCKWISE_90(270),
    ;

    val swapsAxes: Boolean get() = this == CLOCKWISE_90 || this == COUNTER_CLOCKWISE_90
}

/**
 * How to draw a symbol.
 *
 * Sizes are expressed in pixels per module rather than as an overall target size.
 * That is deliberate: a barcode is only reliably scannable when modules land on
 * whole pixels, and letting the caller ask for "400px wide" invites fractional
 * module widths that blur edges and defeat decoders.
 */
data class RenderSpec(
    /** Pixels per module. The single most important scan-quality parameter. */
    val modulePx: Int = 4,
    /**
     * Quiet zone in modules on each side. Zero means the encoder already added a
     * compliant zone, or the caller does not want one.
     */
    val quietZoneModules: Int = 4,
    /** Opaque ARGB. */
    val foregroundColor: Int = COLOR_BLACK,
    /** May be transparent for overlay use. */
    val backgroundColor: Int = COLOR_WHITE,
    /** Draw the human-readable text beneath the symbol, when the symbology has one. */
    val includeHrt: Boolean = true,
    val rotation: SymbolRotation = SymbolRotation.NONE,
    /**
     * Bar height in modules for linear symbologies whose encoder did not report a
     * row height. Fifty X-dimensions is libzint's own default and a common
     * standards-recommended minimum.
     */
    val linearHeightModules: Int = 50,
    val purpose: RenderPurpose = RenderPurpose.DETAIL,
) {
    init {
        require(modulePx > 0) { "modulePx must be positive, was $modulePx" }
        require(quietZoneModules >= 0) { "quietZoneModules cannot be negative" }
        require(linearHeightModules > 0) { "linearHeightModules must be positive" }
    }

    companion object {
        const val COLOR_BLACK: Int = 0xFF000000.toInt()
        const val COLOR_WHITE: Int = 0xFFFFFFFF.toInt()
        const val COLOR_TRANSPARENT: Int = 0x00000000

        /** Small, cheap, and cached per size by the caller. */
        fun thumbnail(): RenderSpec = RenderSpec(
            modulePx = 2,
            quietZoneModules = 2,
            includeHrt = false,
            linearHeightModules = 24,
            purpose = RenderPurpose.THUMBNAIL,
        )

        /** High fidelity for saved files. */
        fun export(modulePx: Int = 10): RenderSpec = RenderSpec(
            modulePx = modulePx,
            purpose = RenderPurpose.EXPORT,
        )
    }
}
