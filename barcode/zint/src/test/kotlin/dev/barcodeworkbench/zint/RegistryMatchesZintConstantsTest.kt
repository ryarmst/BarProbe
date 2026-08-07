package dev.barcodeworkbench.zint

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import org.junit.Test

/**
 * Closes the verification loop between the registry and libzint, without needing
 * a device or the native library loaded.
 *
 * The chain has two links:
 *
 *  1. `tools/verify-zint-goldens.sh` asserts that every [ZintConstants]
 *     symbology id matches the name libzint itself reports for that id, using a
 *     host build of the real library.
 *  2. This test asserts that [SymbologyRegistry] agrees with [ZintConstants].
 *
 * Together they establish that the registry's ids are correct, which is what
 * caught EAN-13 being recorded as 11 (the 2-digit add-on symbology) instead of 15.
 */
class RegistryMatchesZintConstantsTest {

    /** Registry entry to the constant it must equal. */
    private val expected: Map<SymbologyId, Int> = mapOf(
        SymbologyId.CODE_11 to ZintConstants.BARCODE_CODE11,
        SymbologyId.ITF to ZintConstants.BARCODE_C25INTER,
        SymbologyId.CODE_39 to ZintConstants.BARCODE_CODE39,
        SymbologyId.EAN_8 to ZintConstants.BARCODE_EAN8,
        SymbologyId.EAN_13 to ZintConstants.BARCODE_EAN13,
        SymbologyId.GS1_128 to ZintConstants.BARCODE_GS1_128,
        SymbologyId.CODABAR to ZintConstants.BARCODE_CODABAR,
        SymbologyId.CODE_128 to ZintConstants.BARCODE_CODE128,
        SymbologyId.CODE_93 to ZintConstants.BARCODE_CODE93,
        SymbologyId.DATABAR_OMNI to ZintConstants.BARCODE_DBAR_OMN,
        SymbologyId.DATABAR_LIMITED to ZintConstants.BARCODE_DBAR_LTD,
        SymbologyId.DATABAR_EXPANDED to ZintConstants.BARCODE_DBAR_EXP,
        SymbologyId.TELEPEN to ZintConstants.BARCODE_TELEPEN,
        SymbologyId.UPC_A to ZintConstants.BARCODE_UPCA,
        SymbologyId.UPC_E to ZintConstants.BARCODE_UPCE,
        SymbologyId.MSI_PLESSEY to ZintConstants.BARCODE_MSI_PLESSEY,
        SymbologyId.PDF417 to ZintConstants.BARCODE_PDF417,
        SymbologyId.MAXICODE to ZintConstants.BARCODE_MAXICODE,
        SymbologyId.QR_CODE to ZintConstants.BARCODE_QRCODE,
        SymbologyId.DATA_MATRIX to ZintConstants.BARCODE_DATAMATRIX,
        SymbologyId.MICRO_PDF417 to ZintConstants.BARCODE_MICROPDF417,
        SymbologyId.ITF_14 to ZintConstants.BARCODE_ITF14,
        SymbologyId.AZTEC to ZintConstants.BARCODE_AZTEC,
        SymbologyId.MICRO_QR to ZintConstants.BARCODE_MICROQR,
        SymbologyId.DOTCODE to ZintConstants.BARCODE_DOTCODE,
        SymbologyId.RMQR to ZintConstants.BARCODE_RMQR,
    )

    @Test
    fun `registry zint ids match the native constants`() {
        expected.forEach { (id, constant) ->
            assertWithMessage("$id zint symbol id")
                .that(SymbologyRegistry[id].zintSymbolId)
                .isEqualTo(constant)
        }
    }

    @Test
    fun `every registry entry is covered by this test`() {
        // Prevents a newly added symbology from slipping past the id check.
        assertThat(expected.keys).containsExactlyElementsIn(SymbologyId.entries)
    }

    @Test
    fun `EAN-13 is 15 and not the 2-digit add-on`() {
        // Regression guard for the specific defect found during the Phase 1 spike.
        assertThat(SymbologyRegistry[SymbologyId.EAN_13].zintSymbolId).isEqualTo(15)
        assertThat(SymbologyRegistry[SymbologyId.EAN_13].zintSymbolId).isNotEqualTo(11)
    }

    @Test
    fun `input mode flags match the documented zint bit values`() {
        assertThat(ZintConstants.DATA_MODE).isEqualTo(0)
        assertThat(ZintConstants.UNICODE_MODE).isEqualTo(1)
        assertThat(ZintConstants.GS1_MODE).isEqualTo(2)
        assertThat(ZintConstants.ESCAPE_MODE).isEqualTo(0x0008)
        assertThat(ZintConstants.EXTRA_ESCAPE_MODE).isEqualTo(0x0100)
    }

    @Test
    fun `warnings sort below the error marker`() {
        // Return codes below ZINT_ERROR still yield a usable symbol, which is why
        // the encoder extracts a matrix for them rather than treating them as
        // failures.
        assertThat(ZintConstants.ZINT_WARN_NONCOMPLIANT).isLessThan(ZintConstants.ZINT_ERROR)
        assertThat(ZintConstants.ZINT_ERROR_INVALID_DATA)
            .isAtLeast(ZintConstants.ZINT_ERROR)
    }
}
