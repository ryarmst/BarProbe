package dev.barcodeworkbench.barcode.render

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.core.model.ModuleMatrix
import org.junit.Test

class SymbolGeometryTest {

    /** A checkerboard of the requested size, with optional per-row heights. */
    private fun matrix(
        width: Int,
        rows: Int,
        rowHeights: FloatArray? = null,
        hrt: String? = null,
    ): ModuleMatrix {
        val data = ByteArray(width * rows) { i -> if (i % 2 == 0) 1 else 0 }
        return ModuleMatrix(width, rows, data, rowHeights, hrt)
    }

    @Test
    fun `canvas includes quiet zone on both sides`() {
        val g = SymbolGeometry.compute(
            matrix(10, 10, FloatArray(10) { 1f }),
            RenderSpec(modulePx = 3, quietZoneModules = 4, includeHrt = false),
        )
        assertThat(g.symbolWidthPx).isEqualTo(30)
        assertThat(g.quietZonePx).isEqualTo(12)
        assertThat(g.unrotatedWidthPx).isEqualTo(30 + 24)
    }

    @Test
    fun `zero quiet zone produces no padding`() {
        val g = SymbolGeometry.compute(
            matrix(10, 10, FloatArray(10) { 1f }),
            RenderSpec(modulePx = 2, quietZoneModules = 0, includeHrt = false),
        )
        assertThat(g.quietZonePx).isEqualTo(0)
        assertThat(g.unrotatedWidthPx).isEqualTo(20)
    }

    @Test
    fun `single row linear symbol uses the configured bar height`() {
        // A linear symbology whose encoder reports no useful row height would
        // otherwise render one module tall and be unscannable.
        val g = SymbolGeometry.compute(
            matrix(width = 100, rows = 1, rowHeights = floatArrayOf(0f)),
            RenderSpec(modulePx = 2, quietZoneModules = 0, includeHrt = false, linearHeightModules = 50),
        )
        assertThat(g.symbolHeightPx).isEqualTo(100)
    }

    @Test
    fun `reported row heights are honoured over the linear default`() {
        val g = SymbolGeometry.compute(
            matrix(width = 20, rows = 1, rowHeights = floatArrayOf(12f)),
            RenderSpec(modulePx = 2, quietZoneModules = 0, includeHrt = false, linearHeightModules = 50),
        )
        assertThat(g.symbolHeightPx).isEqualTo(24)
    }

    @Test
    fun `row heights sum exactly to the symbol height`() {
        // Rounding each row independently lets error accumulate and leaves visible
        // seams between rows or a symbol taller than its canvas.
        val heights = floatArrayOf(1.3f, 2.7f, 1.1f, 4.9f, 0.6f)
        val g = SymbolGeometry.compute(
            matrix(width = 8, rows = 5, rowHeights = heights),
            RenderSpec(modulePx = 3, quietZoneModules = 0, includeHrt = false),
        )
        assertThat(g.rowHeightsPx.sum()).isEqualTo(g.symbolHeightPx)
    }

    @Test
    fun `rows are contiguous with no gaps or overlaps`() {
        val heights = floatArrayOf(2.4f, 3.6f, 1.5f, 2.5f)
        val g = SymbolGeometry.compute(
            matrix(width = 8, rows = 4, rowHeights = heights),
            RenderSpec(modulePx = 5, quietZoneModules = 2, includeHrt = false),
        )
        for (y in 0 until g.rowCount - 1) {
            val bottomOfRow = g.rowTopsPx[y] + g.rowHeightsPx[y]
            assertWithMessage("row $y bottom should meet row ${y + 1} top")
                .that(bottomOfRow).isEqualTo(g.rowTopsPx[y + 1])
        }
    }

    @Test
    fun `every row is at least one pixel tall`() {
        // Very small reported heights must not collapse a row to nothing.
        val heights = FloatArray(20) { 0.05f }
        val g = SymbolGeometry.compute(
            matrix(width = 8, rows = 20, rowHeights = heights),
            RenderSpec(modulePx = 1, quietZoneModules = 0, includeHrt = false),
        )
        g.rowHeightsPx.forEachIndexed { i, h ->
            assertWithMessage("row $i height").that(h).isAtLeast(1)
        }
    }

    @Test
    fun `HRT space is reserved only when text exists and is requested`() {
        val withText = SymbolGeometry.compute(
            matrix(10, 1, floatArrayOf(10f), hrt = "012345678901"),
            RenderSpec(modulePx = 2, quietZoneModules = 0, includeHrt = true),
        )
        assertThat(withText.hrtHeightPx).isGreaterThan(0)

        val suppressed = SymbolGeometry.compute(
            matrix(10, 1, floatArrayOf(10f), hrt = "012345678901"),
            RenderSpec(modulePx = 2, quietZoneModules = 0, includeHrt = false),
        )
        assertThat(suppressed.hrtHeightPx).isEqualTo(0)

        val noText = SymbolGeometry.compute(
            matrix(10, 1, floatArrayOf(10f), hrt = null),
            RenderSpec(modulePx = 2, quietZoneModules = 0, includeHrt = true),
        )
        assertThat(noText.hrtHeightPx).isEqualTo(0)
    }

    @Test
    fun `quarter turns swap the canvas dimensions`() {
        val upright = RenderSpec(modulePx = 2, quietZoneModules = 1, includeHrt = false)
        val rotated = upright.copy(rotation = SymbolRotation.CLOCKWISE_90)
        val m = matrix(width = 40, rows = 4, rowHeights = FloatArray(4) { 1f })

        val a = SymbolGeometry.compute(m, upright)
        val b = SymbolGeometry.compute(m, rotated)

        assertThat(b.canvasWidthPx).isEqualTo(a.canvasHeightPx)
        assertThat(b.canvasHeightPx).isEqualTo(a.canvasWidthPx)
    }

    @Test
    fun `half turn keeps the canvas dimensions`() {
        val m = matrix(width = 40, rows = 4, rowHeights = FloatArray(4) { 1f })
        val a = SymbolGeometry.compute(m, RenderSpec(includeHrt = false))
        val b = SymbolGeometry.compute(
            m,
            RenderSpec(includeHrt = false, rotation = SymbolRotation.HALF_TURN),
        )
        assertThat(b.canvasWidthPx).isEqualTo(a.canvasWidthPx)
        assertThat(b.canvasHeightPx).isEqualTo(a.canvasHeightPx)
    }

    @Test
    fun `column positions advance by one module`() {
        val g = SymbolGeometry.compute(
            matrix(5, 1, floatArrayOf(1f)),
            RenderSpec(modulePx = 7, quietZoneModules = 3, includeHrt = false),
        )
        assertThat(g.columnLeftPx(0)).isEqualTo(21)
        assertThat(g.columnLeftPx(1)).isEqualTo(28)
        assertThat(g.columnLeftPx(4)).isEqualTo(49)
    }

    @Test
    fun `horizontal runs merge adjacent set modules`() {
        // Emitting one rect per run rather than per module is what keeps SVG and
        // PDF output small.
        val data = byteArrayOf(1, 1, 1, 0, 0, 1, 0, 1, 1)
        val m = ModuleMatrix(width = 9, rows = 1, modules = data)
        val runs = m.horizontalRuns(0)
        assertThat(runs).hasSize(3)
        assertThat(runs[0]).isEqualTo(0 until 3)
        assertThat(runs[1]).isEqualTo(5 until 6)
        assertThat(runs[2]).isEqualTo(7 until 9)
    }

    @Test
    fun `horizontal runs handle a fully set row`() {
        val m = ModuleMatrix(width = 4, rows = 1, modules = byteArrayOf(1, 1, 1, 1))
        assertThat(m.horizontalRuns(0)).containsExactly(0 until 4)
    }

    @Test
    fun `horizontal runs handle an empty row`() {
        val m = ModuleMatrix(width = 4, rows = 1, modules = byteArrayOf(0, 0, 0, 0))
        assertThat(m.horizontalRuns(0)).isEmpty()
    }

    @Test
    fun `module size must be positive`() {
        runCatching { RenderSpec(modulePx = 0) }
            .also { assertThat(it.isFailure).isTrue() }
    }
}
