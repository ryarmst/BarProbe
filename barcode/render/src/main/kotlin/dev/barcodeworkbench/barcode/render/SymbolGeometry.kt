package dev.barcodeworkbench.barcode.render

import dev.barcodeworkbench.core.model.ModuleMatrix
import kotlin.math.roundToInt

/**
 * Pixel layout for a symbol, computed once and shared by every renderer.
 *
 * All three output formats consume this, which is what guarantees a PNG, an SVG
 * and a PDF of the same symbol have identical geometry rather than three
 * near-miss implementations that drift apart.
 */
class SymbolGeometry private constructor(
    val moduleSizePx: Int,
    val quietZonePx: Int,
    /** Symbol area excluding quiet zone and HRT. */
    val symbolWidthPx: Int,
    val symbolHeightPx: Int,
    val hrtHeightPx: Int,
    /** Full canvas before rotation. */
    val unrotatedWidthPx: Int,
    val unrotatedHeightPx: Int,
    /** Canvas after rotation is applied. */
    val canvasWidthPx: Int,
    val canvasHeightPx: Int,
    /** Top edge of each module row, relative to the canvas, before rotation. */
    val rowTopsPx: IntArray,
    /** Height of each module row in pixels. */
    val rowHeightsPx: IntArray,
    val rotation: SymbolRotation,
) {
    /** Left edge of module column [x], before rotation. */
    fun columnLeftPx(x: Int): Int = quietZonePx + x * moduleSizePx

    val rowCount: Int get() = rowHeightsPx.size

    companion object {

        /** Space reserved for human-readable text, as a multiple of module size. */
        private const val HRT_HEIGHT_MODULES = 10

        fun compute(matrix: ModuleMatrix, spec: RenderSpec): SymbolGeometry {
            val module = spec.modulePx
            val quietZone = spec.quietZoneModules * module

            val symbolWidth = matrix.width * module

            // Row heights come from the encoder in X-dimension units. A linear
            // symbology often reports no useful height, in which case the spec's
            // default applies -- rendering a Code 128 one module tall would be
            // technically correct and completely unscannable.
            val unitHeights = FloatArray(matrix.rows) { y ->
                val reported = matrix.rowHeight(y)
                if (reported > 1f) {
                    reported
                } else if (matrix.rows == 1) {
                    spec.linearHeightModules.toFloat()
                } else {
                    reported
                }
            }

            // Accumulate in float space and round only at row boundaries, so the
            // row heights always sum exactly to the total. Rounding each row
            // independently lets error accumulate and leaves seams or overruns.
            val tops = IntArray(matrix.rows)
            val heights = IntArray(matrix.rows)
            var cumulativeUnits = 0f
            var previousEdge = 0
            for (y in 0 until matrix.rows) {
                tops[y] = quietZone + previousEdge
                cumulativeUnits += unitHeights[y]
                val edge = (cumulativeUnits * module).roundToInt()
                heights[y] = (edge - previousEdge).coerceAtLeast(1)
                previousEdge = edge
            }
            val symbolHeight = previousEdge

            val hrtHeight = if (spec.includeHrt && !matrix.hrt.isNullOrEmpty()) {
                HRT_HEIGHT_MODULES * module
            } else {
                0
            }

            val unrotatedWidth = symbolWidth + 2 * quietZone
            val unrotatedHeight = symbolHeight + 2 * quietZone + hrtHeight

            val canvasWidth = if (spec.rotation.swapsAxes) unrotatedHeight else unrotatedWidth
            val canvasHeight = if (spec.rotation.swapsAxes) unrotatedWidth else unrotatedHeight

            return SymbolGeometry(
                moduleSizePx = module,
                quietZonePx = quietZone,
                symbolWidthPx = symbolWidth,
                symbolHeightPx = symbolHeight,
                hrtHeightPx = hrtHeight,
                unrotatedWidthPx = unrotatedWidth,
                unrotatedHeightPx = unrotatedHeight,
                canvasWidthPx = canvasWidth,
                canvasHeightPx = canvasHeight,
                rowTopsPx = tops,
                rowHeightsPx = heights,
                rotation = spec.rotation,
            )
        }
    }
}

/**
 * Horizontal runs of set modules within one row.
 *
 * Emitting one rectangle per run instead of one per module is what keeps SVG and
 * PDF output small: a linear barcode collapses from hundreds of rects to a few
 * dozen, and a wide 2D symbol shrinks substantially too.
 */
internal fun ModuleMatrix.horizontalRuns(row: Int): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    for (x in 0 until width) {
        val set = this[x, row]
        if (set && start < 0) {
            start = x
        } else if (!set && start >= 0) {
            runs += start until x
            start = -1
        }
    }
    if (start >= 0) {
        runs += start until width
    }
    return runs
}
