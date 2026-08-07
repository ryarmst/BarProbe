package dev.barcodeworkbench.zint

import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.barcode.engine.EncodeRequest
import dev.barcodeworkbench.barcode.engine.EncodeResult
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BarcodeEncoder] backed by libzint through JNI.
 *
 * Returns a module matrix and never a bitmap, so a single renderer set can
 * produce screen output, PNG, SVG and PDF from identical geometry.
 */
@Singleton
class ZintEncoder @Inject constructor() : BarcodeEncoder {

    override fun encode(request: EncodeRequest): EncodeResult {
        val spec = SymbologyRegistry.find(request.symbology)
            ?: return EncodeResult.Failure(
                code = -1,
                message = "Unknown symbology ${request.symbology}",
            )

        val payload = request.payload
        val options = request.options

        val result = ZintNative.encode(
            spec.zintSymbolId,
            payload.bytes,
            buildInputMode(payload.mode, payload.escapesEnabled, spec.supportsCodesetEscapes),
            payload.eci ?: 0,
            options.errorCorrection ?: LEAVE_ENGINE_DEFAULT,
            options.version ?: LEAVE_ENGINE_DEFAULT,
            options.variant ?: LEAVE_ENGINE_DEFAULT,
            buildOutputOptions(options.quietZones, options.dotty),
            options.heightUnits ?: 0f,
            if (options.strict) WARN_FAIL_ALL else 0,
        )

        if (!result.isSuccess) {
            return EncodeResult.Failure(
                code = result.returnCode,
                // zint's own text names the offending character position, which is
                // considerably more useful than anything generic.
                message = result.errorText ?: "Encoding failed (code ${result.returnCode})",
            )
        }

        val matrix = ModuleMatrix(
            width = result.width,
            rows = result.rows,
            modules = result.modules!!,
            rowHeights = result.rowHeights,
            hrt = result.hrt,
            renderAsDots = options.dotty || request.symbology == SymbologyId.DOTCODE,
            renderAsHexGrid = request.symbology == SymbologyId.MAXICODE,
        )

        return EncodeResult.Success(
            matrix = matrix,
            warning = if (result.isWarning) result.errorText else null,
        )
    }

    override fun engineVersion(): String {
        val raw = ZintNative.version()
        // ZBarcode_Version packs the version as MMmmpp, e.g. 21600 for 2.16.0.
        val major = raw / 10000
        val minor = (raw / 100) % 100
        val patch = raw % 100
        return "libzint $major.$minor.$patch"
    }

    override fun supports(symbology: SymbologyId): Boolean {
        val spec = SymbologyRegistry.find(symbology) ?: return false
        return ZintNative.isValidSymbology(spec.zintSymbolId)
    }

    /**
     * Combines the base data mode with the escape flags.
     *
     * [ZintConstants.EXTRA_ESCAPE_MODE] is what enables Code 128 codeset
     * switching and FNC1 insertion, so it is only set for symbologies that
     * actually support those escapes.
     */
    private fun buildInputMode(
        mode: InputMode,
        escapesEnabled: Boolean,
        supportsCodesetEscapes: Boolean,
    ): Int {
        var value = when (mode) {
            InputMode.BINARY -> ZintConstants.DATA_MODE
            InputMode.UNICODE -> ZintConstants.UNICODE_MODE
            InputMode.GS1 -> ZintConstants.GS1_MODE
        }
        if (escapesEnabled) {
            value = value or ZintConstants.ESCAPE_MODE
            if (supportsCodesetEscapes) {
                value = value or ZintConstants.EXTRA_ESCAPE_MODE
            }
        }
        return value
    }

    private fun buildOutputOptions(quietZones: Boolean, dotty: Boolean): Int {
        var value = 0
        value = value or if (quietZones) {
            ZintConstants.BARCODE_QUIET_ZONES
        } else {
            ZintConstants.BARCODE_NO_QUIET_ZONES
        }
        if (dotty) {
            value = value or ZintConstants.BARCODE_DOTTY_MODE
        }
        return value
    }

    private companion object {
        /**
         * A negative value tells the JNI layer to leave libzint's own default in
         * place. Passing a literal -1 through would be read as an invalid row
         * count or version by PDF417, Aztec and rMQR.
         */
        const val LEAVE_ENGINE_DEFAULT = -1

        /** zint's warn_level that promotes warnings to errors. */
        const val WARN_FAIL_ALL = 2
    }
}
