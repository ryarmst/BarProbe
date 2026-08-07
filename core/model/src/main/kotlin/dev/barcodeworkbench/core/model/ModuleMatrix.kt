package dev.barcodeworkbench.core.model

/**
 * A symbology-agnostic grid of set/unset modules, plus what a renderer needs to
 * lay it out.
 *
 * Keeping this between the encoder and the renderers is what lets one renderer
 * set serve screen, PNG, SVG and PDF with identical geometry, and what allows the
 * encoder to be swapped without touching rendering.
 */
class ModuleMatrix(
    val width: Int,
    val rows: Int,
    modules: ByteArray,
    /** Per-row heights in X-dimensions; stacked symbologies vary row to row. */
    rowHeights: FloatArray? = null,
    /** Human Readable Text, when the symbology defines it. */
    val hrt: String? = null,
    /** Dots rather than squares, as DotCode requires. */
    val renderAsDots: Boolean = false,
    /** Hexagonal grid with a central finder, as MaxiCode requires. */
    val renderAsHexGrid: Boolean = false,
) {
    init {
        require(width > 0 && rows > 0) { "Empty matrix: ${width}x$rows" }
        require(modules.size == width * rows) {
            "Matrix data is ${modules.size} bytes, expected ${width * rows}"
        }
    }

    private val _modules = modules.copyOf()
    private val _rowHeights = rowHeights?.copyOf()

    operator fun get(x: Int, y: Int): Boolean {
        require(x in 0 until width && y in 0 until rows) {
            "($x,$y) outside ${width}x$rows"
        }
        return _modules[y * width + x] != 0.toByte()
    }

    fun rowHeight(y: Int): Float = _rowHeights?.getOrNull(y)?.takeIf { it > 0f } ?: 1f

    /** Total height in X-dimensions, summing per-row heights. */
    val totalHeightUnits: Float
        get() = (0 until rows).fold(0f) { acc, y -> acc + rowHeight(y) }

    /** Set modules, useful for sanity checks and tests. */
    fun countSetModules(): Int = _modules.count { it != 0.toByte() }

    /**
     * Reproduces libzint's own hex dump format, which is what makes byte-level
     * comparison against the reference implementation possible.
     */
    fun toReferenceDump(): String = buildString {
        for (y in 0 until rows) {
            var space = 0
            var byt = 0
            for (x in 0 until width) {
                byt = byt shl 1
                if (get(x, y)) byt++
                if (((x + 1) and 0x3) == 0) {
                    append(HEX[byt])
                    space++
                    byt = 0
                }
                if (space == 2 && x + 1 < width) {
                    append(' ')
                    space = 0
                }
            }
            if ((width and 0x03) != 0) {
                byt = byt shl (4 - (width and 0x03))
                append(HEX[byt])
            }
            append('\n')
        }
    }

    private companion object {
        val HEX = "0123456789ABCDEF".toCharArray()
    }
}
