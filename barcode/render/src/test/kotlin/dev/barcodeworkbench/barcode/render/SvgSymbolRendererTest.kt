package dev.barcodeworkbench.barcode.render

import com.google.common.truth.Truth.assertThat
import dev.barcodeworkbench.core.model.ModuleMatrix
import org.junit.Test

/**
 * SVG output is a string, so it is fully unit-testable on the host with no
 * device and no image comparison.
 */
class SvgSymbolRendererTest {

    private val renderer = SvgSymbolRenderer()

    private fun matrix(
        width: Int,
        rows: Int,
        data: ByteArray,
        rowHeights: FloatArray? = null,
        hrt: String? = null,
        dots: Boolean = false,
    ) = ModuleMatrix(width, rows, data, rowHeights, hrt, renderAsDots = dots)

    @Test
    fun `output is well formed svg with matching dimensions`() {
        val m = matrix(4, 1, byteArrayOf(1, 0, 1, 0), floatArrayOf(1f))
        val svg = renderer.render(
            m,
            // linearHeightModules is pinned to 1 here so the expected canvas is
            // trivially checkable. A row height of 1f means "unspecified", so the
            // default bar height would otherwise apply, as the next test asserts.
            RenderSpec(
                modulePx = 10,
                quietZoneModules = 0,
                includeHrt = false,
                linearHeightModules = 1,
            ),
        )
        assertThat(svg).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        assertThat(svg).contains("<svg xmlns=\"http://www.w3.org/2000/svg\"")
        assertThat(svg).contains("width=\"40\"")
        assertThat(svg).contains("viewBox=\"0 0 40 10\"")
        assertThat(svg.trim()).endsWith("</svg>")
    }

    @Test
    fun `single row symbol with unspecified height gets the full bar height`() {
        // A linear barcode rendered one module tall would be unscannable, so an
        // unspecified row height must expand to the configured bar height.
        val m = matrix(4, 1, byteArrayOf(1, 0, 1, 0), floatArrayOf(1f))
        val svg = renderer.render(
            m,
            RenderSpec(
                modulePx = 10,
                quietZoneModules = 0,
                includeHrt = false,
                linearHeightModules = 50,
            ),
        )
        assertThat(svg).contains("viewBox=\"0 0 40 500\"")
    }

    @Test
    fun `set modules become rects and unset modules do not`() {
        val m = matrix(3, 1, byteArrayOf(1, 0, 1), floatArrayOf(1f))
        val svg = renderer.render(
            m,
            RenderSpec(modulePx = 1, quietZoneModules = 0, includeHrt = false),
        )
        // Two isolated set modules, plus the background rect.
        assertThat(countOccurrences(svg, "<rect")).isEqualTo(3)
    }

    @Test
    fun `adjacent modules merge into a single rect`() {
        val solid = matrix(8, 1, ByteArray(8) { 1 }, floatArrayOf(1f))
        val svg = renderer.render(
            solid,
            RenderSpec(modulePx = 1, quietZoneModules = 0, includeHrt = false),
        )
        // One background rect plus exactly one merged run, not eight.
        assertThat(countOccurrences(svg, "<rect")).isEqualTo(2)
        assertThat(svg).contains("width=\"8\"")
    }

    @Test
    fun `transparent background emits no background rect`() {
        val m = matrix(2, 1, byteArrayOf(1, 0), floatArrayOf(1f))
        val svg = renderer.render(
            m,
            RenderSpec(
                modulePx = 1,
                quietZoneModules = 0,
                includeHrt = false,
                backgroundColor = RenderSpec.COLOR_TRANSPARENT,
            ),
        )
        assertThat(countOccurrences(svg, "<rect")).isEqualTo(1)
    }

    @Test
    fun `colours are emitted as hex fills`() {
        val m = matrix(1, 1, byteArrayOf(1), floatArrayOf(1f))
        val svg = renderer.render(
            m,
            RenderSpec(
                modulePx = 1,
                quietZoneModules = 0,
                includeHrt = false,
                foregroundColor = 0xFF112233.toInt(),
                backgroundColor = 0xFFAABBCC.toInt(),
            ),
        )
        assertThat(svg).contains("fill=\"#112233\"")
        assertThat(svg).contains("fill=\"#AABBCC\"")
    }

    @Test
    fun `partial alpha becomes fill-opacity`() {
        val m = matrix(1, 1, byteArrayOf(1), floatArrayOf(1f))
        val svg = renderer.render(
            m,
            RenderSpec(
                modulePx = 1,
                quietZoneModules = 0,
                includeHrt = false,
                foregroundColor = 0x80000000.toInt(),
            ),
        )
        assertThat(svg).contains("fill-opacity=")
    }

    @Test
    fun `dotty symbols emit circles instead of rects`() {
        val m = matrix(3, 3, ByteArray(9) { 1 }, FloatArray(3) { 1f }, dots = true)
        val svg = renderer.render(
            m,
            RenderSpec(modulePx = 4, quietZoneModules = 0, includeHrt = false),
        )
        assertThat(countOccurrences(svg, "<circle")).isEqualTo(9)
        // Only the background remains as a rect.
        assertThat(countOccurrences(svg, "<rect")).isEqualTo(1)
    }

    @Test
    fun `HRT is emitted as centred monospace text`() {
        val m = matrix(10, 1, ByteArray(10) { 1 }, floatArrayOf(10f), hrt = "012345678901")
        val svg = renderer.render(
            m,
            RenderSpec(modulePx = 2, quietZoneModules = 2, includeHrt = true),
        )
        assertThat(svg).contains("<text")
        assertThat(svg).contains("font-family=\"monospace\"")
        assertThat(svg).contains("text-anchor=\"middle\"")
        assertThat(svg).contains(">012345678901</text>")
    }

    @Test
    fun `HRT containing XML metacharacters is escaped`() {
        // Encoder-derived text can legitimately contain these, and unescaped they
        // would produce malformed SVG.
        val m = matrix(4, 1, ByteArray(4) { 1 }, floatArrayOf(4f), hrt = "a<b>&\"c\"")
        val svg = renderer.render(
            m,
            RenderSpec(modulePx = 2, quietZoneModules = 1, includeHrt = true),
        )
        assertThat(svg).contains("a&lt;b&gt;&amp;&quot;c&quot;")
        assertThat(svg).doesNotContain("<b>")
    }

    @Test
    fun `rotation is applied as a group transform`() {
        val m = matrix(8, 1, ByteArray(8) { 1 }, floatArrayOf(4f))
        val svg = renderer.render(
            m,
            RenderSpec(
                modulePx = 2,
                quietZoneModules = 0,
                includeHrt = false,
                rotation = SymbolRotation.CLOCKWISE_90,
            ),
        )
        assertThat(svg).contains("rotate(90)")
        assertThat(svg).contains("<g transform=")
        assertThat(svg).contains("</g>")
    }

    @Test
    fun `no transform group when upright`() {
        val m = matrix(4, 1, ByteArray(4) { 1 }, floatArrayOf(1f))
        val svg = renderer.render(
            m,
            RenderSpec(modulePx = 1, quietZoneModules = 0, includeHrt = false),
        )
        assertThat(svg).doesNotContain("<g transform=")
    }

    @Test
    fun `geometry matches the shared calculator`() {
        // The whole point of the shared geometry is that every renderer agrees, so
        // the SVG canvas must equal what the calculator reports.
        val m = matrix(21, 21, ByteArray(441) { 1 }, FloatArray(21) { 1f })
        val spec = RenderSpec(modulePx = 6, quietZoneModules = 4, includeHrt = false)
        val g = SymbolGeometry.compute(m, spec)
        val svg = renderer.render(m, spec)
        assertThat(svg).contains("width=\"${g.canvasWidthPx}\"")
        assertThat(svg).contains("height=\"${g.canvasHeightPx}\"")
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var index = text.indexOf(needle)
        while (index >= 0) {
            count++
            index = text.indexOf(needle, index + needle.length)
        }
        return count
    }
}
