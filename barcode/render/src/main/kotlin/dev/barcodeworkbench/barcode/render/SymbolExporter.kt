package dev.barcodeworkbench.barcode.render

import android.graphics.Bitmap
import dev.barcodeworkbench.core.model.ModuleMatrix
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Output formats.
 *
 * JPEG is offered because some systems demand it, but it is lossy and its
 * artefacts land exactly on the high-contrast module edges a scanner depends on,
 * so it is marked accordingly and never the default.
 */
enum class ExportFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val isVector: Boolean,
    /** Lossy compression can degrade module edges enough to affect decoding. */
    val isLossy: Boolean = false,
) {
    PNG("PNG", "png", "image/png", isVector = false),
    SVG("SVG", "svg", "image/svg+xml", isVector = true),
    PDF("PDF", "pdf", "application/pdf", isVector = true),
    WEBP("WebP", "webp", "image/webp", isVector = false),
    JPEG("JPEG", "jpg", "image/jpeg", isVector = false, isLossy = true),
    ;

    companion object {
        /** Ordered best-first for barcode use. */
        val recommended: List<ExportFormat> = listOf(PNG, SVG, PDF, WEBP, JPEG)
    }
}

/**
 * Serialises a symbol to a chosen format.
 *
 * Every format routes through the same [SymbolGeometry], so switching output type
 * changes the container and not the barcode.
 */
@Singleton
class SymbolExporter @Inject constructor(
    private val bitmapRenderer: BitmapSymbolRenderer,
    private val svgRenderer: SvgSymbolRenderer,
    private val pdfRenderer: PdfSymbolRenderer,
) {

    fun write(
        matrix: ModuleMatrix,
        spec: RenderSpec,
        format: ExportFormat,
        out: OutputStream,
    ) {
        val exportSpec = spec.copy(purpose = RenderPurpose.EXPORT)
        when (format) {
            ExportFormat.SVG -> out.write(
                svgRenderer.render(matrix, exportSpec).toByteArray(Charsets.UTF_8),
            )

            ExportFormat.PDF -> pdfRenderer.renderSingle(matrix, exportSpec, out = out)

            ExportFormat.PNG -> compress(matrix, exportSpec, Bitmap.CompressFormat.PNG, 100, out)

            ExportFormat.WEBP -> compress(
                matrix,
                exportSpec,
                // Lossless WebP keeps module edges exact while still compressing
                // better than PNG for large symbols.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                },
                100,
                out,
            )

            ExportFormat.JPEG -> compress(
                matrix,
                // JPEG cannot represent transparency, so a transparent background
                // would come out black and invert the symbol.
                exportSpec.copy(backgroundColor = RenderSpec.COLOR_WHITE),
                Bitmap.CompressFormat.JPEG,
                JPEG_QUALITY,
                out,
            )
        }
        out.flush()
    }

    /** Suggests a filename from the payload, kept filesystem-safe. */
    fun suggestFileName(
        symbologyName: String,
        payloadHint: String,
        format: ExportFormat,
    ): String {
        val safePayload = payloadHint
            .take(FILENAME_PAYLOAD_CHARS)
            .map { ch -> if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_' }
            .joinToString("")
            .trim('_')
        val safeSymbology = symbologyName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
        val stem = if (safePayload.isEmpty()) safeSymbology else "$safeSymbology-$safePayload"
        return "$stem.${format.extension}"
    }

    private fun compress(
        matrix: ModuleMatrix,
        spec: RenderSpec,
        format: Bitmap.CompressFormat,
        quality: Int,
        out: OutputStream,
    ) {
        val bitmap = bitmapRenderer.render(matrix, spec)
        try {
            bitmap.compress(format, quality, out)
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val JPEG_QUALITY = 95
        const val FILENAME_PAYLOAD_CHARS = 24
    }
}
