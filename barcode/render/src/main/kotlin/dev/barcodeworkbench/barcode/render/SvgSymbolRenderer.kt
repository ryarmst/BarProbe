package dev.barcodeworkbench.barcode.render

import dev.barcodeworkbench.core.model.ModuleMatrix
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders a symbol as SVG.
 *
 * Vector output matters for printing: a raster barcode enlarged for a label sheet
 * softens its module edges, while SVG stays exact at any size. It is also the one
 * format that survives being scaled by whatever tool the user pastes it into.
 */
@Singleton
class SvgSymbolRenderer @Inject constructor() {

    fun render(matrix: ModuleMatrix, spec: RenderSpec): String {
        val geometry = SymbolGeometry.compute(matrix, spec)
        val builder = StringBuilder(1024)

        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        builder.append("width=\"").append(geometry.canvasWidthPx).append("\" ")
        builder.append("height=\"").append(geometry.canvasHeightPx).append("\" ")
        builder.append("viewBox=\"0 0 ")
            .append(geometry.canvasWidthPx).append(' ')
            .append(geometry.canvasHeightPx).append("\">\n")

        appendBackground(builder, geometry, spec)

        // Rotation is a group transform rather than recomputed coordinates, so the
        // module geometry below is identical in every orientation.
        val transform = rotationTransform(geometry, spec.rotation)
        if (transform != null) {
            builder.append("<g transform=\"").append(transform).append("\">\n")
        }

        if (matrix.renderAsDots) {
            appendDots(builder, matrix, geometry, spec)
        } else {
            appendModuleRects(builder, matrix, geometry, spec)
        }

        if (transform != null) {
            builder.append("</g>\n")
        }

        appendHrt(builder, matrix, geometry, spec)

        builder.append("</svg>\n")
        return builder.toString()
    }

    private fun appendBackground(sb: StringBuilder, geometry: SymbolGeometry, spec: RenderSpec) {
        if (alphaOf(spec.backgroundColor) == 0) return
        sb.append("<rect x=\"0\" y=\"0\" width=\"").append(geometry.canvasWidthPx)
            .append("\" height=\"").append(geometry.canvasHeightPx).append('"')
        appendFill(sb, spec.backgroundColor)
        sb.append("/>\n")
    }

    private fun appendModuleRects(
        sb: StringBuilder,
        matrix: ModuleMatrix,
        geometry: SymbolGeometry,
        spec: RenderSpec,
    ) {
        for (y in 0 until matrix.rows) {
            val top = geometry.rowTopsPx[y]
            val height = geometry.rowHeightsPx[y]
            if (height <= 0) continue
            for (run in matrix.horizontalRuns(y)) {
                val left = geometry.columnLeftPx(run.first)
                val width = (run.last - run.first + 1) * geometry.moduleSizePx
                sb.append("<rect x=\"").append(left)
                    .append("\" y=\"").append(top)
                    .append("\" width=\"").append(width)
                    .append("\" height=\"").append(height).append('"')
                appendFill(sb, spec.foregroundColor)
                sb.append("/>\n")
            }
        }
    }

    /** DotCode is specified as dots, not squares, and decoders expect that. */
    private fun appendDots(
        sb: StringBuilder,
        matrix: ModuleMatrix,
        geometry: SymbolGeometry,
        spec: RenderSpec,
    ) {
        val radius = geometry.moduleSizePx / 2.0
        for (y in 0 until matrix.rows) {
            val centreY = geometry.rowTopsPx[y] + geometry.rowHeightsPx[y] / 2.0
            for (x in 0 until matrix.width) {
                if (!matrix[x, y]) continue
                val centreX = geometry.columnLeftPx(x) + radius
                sb.append("<circle cx=\"").append(format(centreX))
                    .append("\" cy=\"").append(format(centreY))
                    .append("\" r=\"").append(format(radius)).append('"')
                appendFill(sb, spec.foregroundColor)
                sb.append("/>\n")
            }
        }
    }

    private fun appendHrt(
        sb: StringBuilder,
        matrix: ModuleMatrix,
        geometry: SymbolGeometry,
        spec: RenderSpec,
    ) {
        if (geometry.hrtHeightPx <= 0) return
        val hrt = matrix.hrt ?: return
        // Placed against the unrotated canvas: rotating the caption with the
        // symbol would leave it sideways and unreadable.
        val fontSize = geometry.hrtHeightPx * 0.7
        val baseline = geometry.canvasHeightPx - geometry.quietZonePx
        sb.append("<text x=\"").append(geometry.canvasWidthPx / 2)
            .append("\" y=\"").append(baseline)
            .append("\" font-family=\"monospace\" font-size=\"").append(format(fontSize))
            .append("\" text-anchor=\"middle\"")
        appendFill(sb, spec.foregroundColor)
        sb.append('>').append(escapeXml(hrt)).append("</text>\n")
    }

    private fun rotationTransform(geometry: SymbolGeometry, rotation: SymbolRotation): String? =
        when (rotation) {
            SymbolRotation.NONE -> null
            SymbolRotation.CLOCKWISE_90 ->
                "translate(${geometry.canvasWidthPx},0) rotate(90)"
            SymbolRotation.HALF_TURN ->
                "translate(${geometry.canvasWidthPx},${geometry.canvasHeightPx}) rotate(180)"
            SymbolRotation.COUNTER_CLOCKWISE_90 ->
                "translate(0,${geometry.canvasHeightPx}) rotate(-90)"
        }

    private fun appendFill(sb: StringBuilder, color: Int) {
        val alpha = alphaOf(color)
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        sb.append(" fill=\"#").append(String.format(Locale.US, "%02X%02X%02X", r, g, b)).append('"')
        if (alpha < 255) {
            sb.append(" fill-opacity=\"")
                .append(String.format(Locale.US, "%.3f", alpha / 255f))
                .append('"')
        }
    }

    private fun alphaOf(color: Int): Int = (color shr 24) and 0xFF

    private fun format(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }

    /** HRT is encoder-derived text and can legitimately contain XML metacharacters. */
    private fun escapeXml(text: String): String = buildString(text.length) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }
}
