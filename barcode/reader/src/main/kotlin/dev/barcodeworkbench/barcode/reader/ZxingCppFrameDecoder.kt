package dev.barcodeworkbench.barcode.reader

import androidx.camera.core.ImageProxy
import dev.barcodeworkbench.barcode.engine.CameraFrameDecoder
import dev.barcodeworkbench.barcode.engine.DecodeOptions
import dev.barcodeworkbench.barcode.engine.DecodedBarcode
import javax.inject.Inject
import javax.inject.Singleton
import zxingcpp.BarcodeReader

/**
 * Live-frame decoding through zxing-cpp's native `ImageProxy` path.
 *
 * The reader instance is reused across frames rather than constructed per frame:
 * it holds native state, and rebuilding it thirty times a second would churn
 * allocations for no benefit. Options are only pushed down when they actually
 * change, since assigning them touches the native side.
 */
@Singleton
class ZxingCppFrameDecoder @Inject constructor() : CameraFrameDecoder {

    private val reader = BarcodeReader()
    private var appliedOptions: DecodeOptions? = null

    override fun decodeFrame(image: ImageProxy, options: DecodeOptions): List<DecodedBarcode> {
        applyOptionsIfChanged(options)
        // Deliberately does not close the ImageProxy; the analyzer owns it.
        return reader.read(image).mapNotNull { it.toDomain() }
    }

    override fun lastFrameDecodeMillis(): Int = reader.lastReadTime

    private fun applyOptionsIfChanged(options: DecodeOptions) {
        if (appliedOptions == options) return
        appliedOptions = options

        val formats = options.symbologies
            .mapNotNull { ZxingFormatMapping.formatFor(it) }
            .toSet()

        reader.options = BarcodeReader.Options().apply {
            if (formats.isNotEmpty()) {
                this.formats = formats
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
}
