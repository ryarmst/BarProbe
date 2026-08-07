package dev.barcodeworkbench.barcode.reader

import android.graphics.Bitmap
import android.graphics.Rect
import dev.barcodeworkbench.barcode.engine.BarcodeDecoder
import dev.barcodeworkbench.barcode.engine.DecodeOptions
import dev.barcodeworkbench.barcode.engine.DecodedBarcode
import dev.barcodeworkbench.core.model.SymbologyId
import javax.inject.Inject
import javax.inject.Singleton
import zxingcpp.BarcodeReader

/**
 * Still-image decoding through zxing-cpp.
 *
 * The engine performs rotation, inversion and downscale retries itself, so this
 * configures them rather than reimplementing a retry loop. That is a genuine
 * simplification over the prior-art app, which hand-rolled rotate-and-invert
 * passes in application code.
 */
@Singleton
class ZxingCppDecoder @Inject constructor() : BarcodeDecoder {

    override fun decode(
        bitmap: Bitmap,
        options: DecodeOptions,
        cropRect: Rect?,
        rotationDegrees: Int,
    ): List<DecodedBarcode> {
        val reader = BarcodeReader(buildOptions(options))
        // The engine requires a non-null region, so an absent crop becomes the whole
        // bitmap.
        val region = cropRect ?: Rect(0, 0, bitmap.width, bitmap.height)
        return reader.read(bitmap, region, rotationDegrees).mapNotNull { it.toDomain() }
    }

    override fun engineVersion(): String = "zxing-cpp $ENGINE_VERSION"

    override fun supportedSymbologies(): Set<SymbologyId> =
        ZxingFormatMapping.readableSymbologies

    private fun buildOptions(options: DecodeOptions): BarcodeReader.Options {
        val requestedFormats = options.symbologies
            .mapNotNull { ZxingFormatMapping.formatFor(it) }
            .toSet()
        return BarcodeReader.Options().apply {
            // An empty set would mean "all formats", which is slower and could
            // return a symbology the user deliberately disabled.
            if (requestedFormats.isNotEmpty()) {
                formats = requestedFormats
            }
            tryHarder = options.tryHarder
            tryRotate = options.tryRotate
            tryInvert = options.tryInvert
            tryDownscale = options.tryDownscale
            maxNumberOfSymbols = options.maxSymbols
            validateOptionalChecksum = options.validateOptionalChecksum
            minLineCount = options.minLineCount
        }
    }

    private companion object {
        const val ENGINE_VERSION = "3.1.1"
    }
}
