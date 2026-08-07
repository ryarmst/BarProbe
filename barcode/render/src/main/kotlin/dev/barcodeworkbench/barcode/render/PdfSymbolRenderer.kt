package dev.barcodeworkbench.barcode.render

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import dev.barcodeworkbench.core.model.ModuleMatrix
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** A page of symbols with captions, for printing physical label or test sheets. */
data class SheetItem(
    val matrix: ModuleMatrix,
    val caption: String?,
)

/**
 * Layout for a printable sheet, in PostScript points (1/72 inch).
 *
 * A4 is the default rather than Letter simply because it is the more widely used
 * of the two; both are offered.
 */
data class SheetLayout(
    val pageWidthPt: Int = A4_WIDTH_PT,
    val pageHeightPt: Int = A4_HEIGHT_PT,
    val marginPt: Int = 36,
    val columns: Int = 2,
    val rows: Int = 5,
    val captionHeightPt: Int = 14,
    val gutterPt: Int = 12,
) {
    init {
        require(columns > 0 && rows > 0) { "Sheet needs at least one cell" }
    }

    val perPage: Int get() = columns * rows

    companion object {
        const val A4_WIDTH_PT = 595
        const val A4_HEIGHT_PT = 842
        const val LETTER_WIDTH_PT = 612
        const val LETTER_HEIGHT_PT = 792

        fun letter(): SheetLayout =
            SheetLayout(pageWidthPt = LETTER_WIDTH_PT, pageHeightPt = LETTER_HEIGHT_PT)
    }
}

/**
 * Renders symbols into a PDF.
 *
 * Uses the platform's own [PdfDocument] rather than a third-party PDF library,
 * which avoids both an extra dependency and its licensing.
 */
@Singleton
class PdfSymbolRenderer @Inject constructor(
    private val bitmapRenderer: BitmapSymbolRenderer,
) {

    /** A single symbol on its own page, sized to fit within the margins. */
    fun renderSingle(
        matrix: ModuleMatrix,
        spec: RenderSpec,
        layout: SheetLayout = SheetLayout(),
        out: OutputStream,
    ) {
        val document = PdfDocument()
        try {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(layout.pageWidthPt, layout.pageHeightPt, 1).create(),
            )
            val available = android.graphics.Rect(
                layout.marginPt,
                layout.marginPt,
                layout.pageWidthPt - layout.marginPt,
                layout.pageHeightPt - layout.marginPt,
            )
            drawFitted(page.canvas, matrix, spec, available)
            document.finishPage(page)
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** A grid of symbols with captions, paginated. */
    fun renderSheet(
        items: List<SheetItem>,
        spec: RenderSpec,
        layout: SheetLayout = SheetLayout(),
        out: OutputStream,
    ) {
        val document = PdfDocument()
        try {
            val cellWidth =
                (layout.pageWidthPt - 2 * layout.marginPt - (layout.columns - 1) * layout.gutterPt) /
                    layout.columns
            val cellHeight =
                (layout.pageHeightPt - 2 * layout.marginPt - (layout.rows - 1) * layout.gutterPt) /
                    layout.rows

            items.chunked(layout.perPage).forEachIndexed { pageIndex, pageItems ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(
                        layout.pageWidthPt,
                        layout.pageHeightPt,
                        pageIndex + 1,
                    ).create(),
                )
                pageItems.forEachIndexed { index, item ->
                    val column = index % layout.columns
                    val row = index / layout.columns
                    val left = layout.marginPt + column * (cellWidth + layout.gutterPt)
                    val top = layout.marginPt + row * (cellHeight + layout.gutterPt)

                    val captionSpace = if (item.caption != null) layout.captionHeightPt else 0
                    val symbolArea = android.graphics.Rect(
                        left,
                        top,
                        left + cellWidth,
                        top + cellHeight - captionSpace,
                    )
                    drawFitted(page.canvas, item.matrix, spec, symbolArea)

                    item.caption?.let { caption ->
                        drawCaption(
                            page.canvas,
                            caption,
                            left + cellWidth / 2f,
                            (top + cellHeight).toFloat(),
                            cellWidth,
                            layout.captionHeightPt,
                        )
                    }
                }
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    fun renderSheetToBytes(
        items: List<SheetItem>,
        spec: RenderSpec,
        layout: SheetLayout = SheetLayout(),
    ): ByteArray = ByteArrayOutputStream().also { renderSheet(items, spec, layout, it) }.toByteArray()

    /**
     * Draws the symbol scaled to fit [area] while preserving its aspect ratio.
     *
     * Rendering through a bitmap keeps geometry identical to on-screen and raster
     * output. The bitmap is produced at a high module size and then scaled down,
     * so print quality does not depend on the display density.
     */
    private fun drawFitted(
        canvas: android.graphics.Canvas,
        matrix: ModuleMatrix,
        spec: RenderSpec,
        area: android.graphics.Rect,
    ) {
        if (area.width() <= 0 || area.height() <= 0) return

        val printSpec = spec.copy(
            modulePx = spec.modulePx.coerceAtLeast(PRINT_MODULE_PX),
            purpose = RenderPurpose.EXPORT,
        )
        val bitmap = bitmapRenderer.render(matrix, printSpec)
        try {
            val scale = minOf(
                area.width().toFloat() / bitmap.width,
                area.height().toFloat() / bitmap.height,
            )
            val drawWidth = (bitmap.width * scale).roundToInt()
            val drawHeight = (bitmap.height * scale).roundToInt()
            val destination = android.graphics.Rect(
                area.left + (area.width() - drawWidth) / 2,
                area.top + (area.height() - drawHeight) / 2,
                area.left + (area.width() - drawWidth) / 2 + drawWidth,
                area.top + (area.height() - drawHeight) / 2 + drawHeight,
            )
            val paint = Paint().apply {
                isFilterBitmap = true
                isAntiAlias = false
            }
            canvas.drawBitmap(bitmap, null, destination, paint)
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawCaption(
        canvas: android.graphics.Canvas,
        caption: String,
        centreX: Float,
        baselineY: Float,
        maxWidth: Int,
        heightPt: Int,
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = heightPt * 0.72f
            textAlign = Paint.Align.CENTER
            color = RenderSpec.COLOR_BLACK
        }
        // Captions are payloads, which can be long; truncate rather than overlap
        // the neighbouring cell.
        var text = caption
        while (text.isNotEmpty() && paint.measureText(text) > maxWidth) {
            text = text.dropLast(1)
        }
        if (text.length < caption.length && text.length > 1) {
            text = text.dropLast(1) + "…"
        }
        canvas.drawText(text, centreX, baselineY - heightPt * 0.2f, paint)
    }

    private companion object {
        /** Module size used for the intermediate bitmap before scaling to the page. */
        const val PRINT_MODULE_PX = 8
    }
}
