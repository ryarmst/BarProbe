package dev.barcodeworkbench.barcode.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import dev.barcodeworkbench.core.model.ModuleMatrix
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders a symbol to a bitmap for on-screen display and raster export.
 *
 * Anti-aliasing is deliberately off for the modules. Softened edges are exactly
 * what makes a barcode harder for a scanner to threshold, so modules are drawn as
 * hard-edged rectangles on whole-pixel boundaries.
 */
@Singleton
class BitmapSymbolRenderer @Inject constructor() {

    fun render(matrix: ModuleMatrix, spec: RenderSpec): Bitmap {
        val geometry = SymbolGeometry.compute(matrix, spec)

        val config = if (spec.purpose == RenderPurpose.EXPORT) {
            Bitmap.Config.ARGB_8888
        } else {
            // Screen use does not need a full alpha channel per pixel; this halves
            // the memory for large symbols in a scrolling list.
            Bitmap.Config.RGB_565.takeIf { isOpaque(spec) } ?: Bitmap.Config.ARGB_8888
        }

        val bitmap = Bitmap.createBitmap(
            geometry.canvasWidthPx,
            geometry.canvasHeightPx,
            config,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(spec.backgroundColor)

        val modulePaint = Paint().apply {
            color = spec.foregroundColor
            style = Paint.Style.FILL
            isAntiAlias = matrix.renderAsDots
        }

        val restore = canvas.save()
        applyRotation(canvas, geometry, spec.rotation)

        if (matrix.renderAsDots) {
            drawDots(canvas, matrix, geometry, modulePaint)
        } else {
            drawModules(canvas, matrix, geometry, modulePaint)
        }

        canvas.restoreToCount(restore)

        drawHrt(canvas, matrix, geometry, spec)

        return bitmap
    }

    private fun drawModules(
        canvas: Canvas,
        matrix: ModuleMatrix,
        geometry: SymbolGeometry,
        paint: Paint,
    ) {
        for (y in 0 until matrix.rows) {
            val top = geometry.rowTopsPx[y].toFloat()
            val bottom = top + geometry.rowHeightsPx[y]
            for (run in matrix.horizontalRuns(y)) {
                val left = geometry.columnLeftPx(run.first).toFloat()
                val right = left + (run.last - run.first + 1) * geometry.moduleSizePx
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }
    }

    private fun drawDots(
        canvas: Canvas,
        matrix: ModuleMatrix,
        geometry: SymbolGeometry,
        paint: Paint,
    ) {
        val radius = geometry.moduleSizePx / 2f
        for (y in 0 until matrix.rows) {
            val centreY = geometry.rowTopsPx[y] + geometry.rowHeightsPx[y] / 2f
            for (x in 0 until matrix.width) {
                if (!matrix[x, y]) continue
                canvas.drawCircle(geometry.columnLeftPx(x) + radius, centreY, radius, paint)
            }
        }
    }

    private fun drawHrt(
        canvas: Canvas,
        matrix: ModuleMatrix,
        geometry: SymbolGeometry,
        spec: RenderSpec,
    ) {
        if (geometry.hrtHeightPx <= 0) return
        val hrt = matrix.hrt ?: return
        val paint = Paint().apply {
            color = spec.foregroundColor
            isAntiAlias = true
            textSize = geometry.hrtHeightPx * 0.7f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        // Drawn outside the rotation transform so the caption stays upright.
        canvas.drawText(
            hrt,
            geometry.canvasWidthPx / 2f,
            (geometry.canvasHeightPx - geometry.quietZonePx).toFloat(),
            paint,
        )
    }

    private fun applyRotation(
        canvas: Canvas,
        geometry: SymbolGeometry,
        rotation: SymbolRotation,
    ) {
        when (rotation) {
            SymbolRotation.NONE -> Unit
            SymbolRotation.CLOCKWISE_90 -> {
                canvas.translate(geometry.canvasWidthPx.toFloat(), 0f)
                canvas.rotate(90f)
            }
            SymbolRotation.HALF_TURN -> {
                canvas.translate(
                    geometry.canvasWidthPx.toFloat(),
                    geometry.canvasHeightPx.toFloat(),
                )
                canvas.rotate(180f)
            }
            SymbolRotation.COUNTER_CLOCKWISE_90 -> {
                canvas.translate(0f, geometry.canvasHeightPx.toFloat())
                canvas.rotate(-90f)
            }
        }
    }

    private fun isOpaque(spec: RenderSpec): Boolean =
        ((spec.backgroundColor shr 24) and 0xFF) == 0xFF
}
