package dev.barcodeworkbench.barcode.engine

import androidx.camera.core.ImageProxy

/**
 * Decodes live camera frames.
 *
 * Separate from [BarcodeDecoder] because the two have genuinely different
 * performance characteristics and option defaults. A still image can afford
 * rotation, inversion and downscale retries; a preview frame arriving thirty times
 * a second cannot, and spending that budget would drop the frame rate without
 * improving the odds -- the next frame is usually a better bet than working harder
 * on this one.
 *
 * Implementations must not close the [ImageProxy]; ownership stays with the caller
 * so it can be released once every consumer of the frame is finished.
 */
interface CameraFrameDecoder {

    fun decodeFrame(image: ImageProxy, options: DecodeOptions): List<DecodedBarcode>

    /** Milliseconds the last decode took, for surfacing scanner performance. */
    fun lastFrameDecodeMillis(): Int
}

/** Option defaults tuned for live frames rather than stills. */
fun DecodeOptions.Companion.forLiveFrames(
    symbologies: Set<dev.barcodeworkbench.core.model.SymbologyId>,
): DecodeOptions = DecodeOptions(
    symbologies = symbologies,
    // All four retries are off: each multiplies per-frame cost, and the camera
    // supplies a fresh frame far sooner than a retry would pay off.
    tryHarder = false,
    tryRotate = false,
    tryInvert = false,
    tryDownscale = false,
    maxSymbols = 1,
    minLineCount = 2,
)

/** Option defaults for a single still image, where latency does not matter. */
fun DecodeOptions.Companion.forStillImage(
    symbologies: Set<dev.barcodeworkbench.core.model.SymbologyId>,
    maxSymbols: Int = 10,
): DecodeOptions = DecodeOptions(
    symbologies = symbologies,
    // A still gets one chance, so it is worth every retry the engine offers.
    tryHarder = true,
    tryRotate = true,
    tryInvert = true,
    tryDownscale = true,
    maxSymbols = maxSymbols,
    minLineCount = 2,
)
