package dev.barcodeworkbench.feature.fuzz

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.barcode.engine.EncodeRequest
import dev.barcodeworkbench.barcode.engine.EncodeResult
import dev.barcodeworkbench.barcode.engine.Mutator
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.SymbologyId
import org.junit.Test

/**
 * The engine is pure orchestration, so it is tested against fakes for both the
 * mutator and the encoder. This lets the retry/skip logic be pinned exactly, which
 * matters because on real symbologies the skip behaviour is the whole point of the
 * design.
 */
class FuzzEngineTest {

    private val base = "hello".toByteArray()

    /** Returns the seed value as a single-byte payload, so a test can steer which
     *  "mutations" a fake encoder will accept purely by seed. */
    private class SeedByteMutator : Mutator {
        override fun mutate(input: ByteArray, seed: Int, maxLength: Int) =
            byteArrayOf((seed and 0xFF).toByte())
        override fun isAvailable() = true
        override fun engineVersion() = "fake"
    }

    private val okMatrix = ModuleMatrix(width = 1, rows = 1, modules = byteArrayOf(1))

    /** Accepts a mutation only when [accept] says so. */
    private class PredicateEncoder(val accept: (ByteArray) -> Boolean, val matrix: ModuleMatrix) :
        BarcodeEncoder {
        var encodeCalls = 0
        override fun encode(request: EncodeRequest): EncodeResult {
            encodeCalls++
            val bytes = request.payload.bytes
            return if (accept(bytes)) {
                EncodeResult.Success(matrix)
            } else {
                EncodeResult.Failure(code = 1, message = "rejected")
            }
        }
        override fun engineVersion() = "fake"
        override fun supports(symbology: SymbologyId) = true
    }

    @Test
    fun `produces a case on the first encodable mutation and reports skips`() {
        // Accept only when the single seed-byte is >= 3, so seeds 0,1,2 skip and 3
        // is the first success -> skipped == 3.
        val encoder = PredicateEncoder({ (it[0].toInt() and 0xFF) >= 3 }, okMatrix)
        val engine = FuzzEngine(SeedByteMutator(), encoder)

        val outcome = engine.next(base, SymbologyId.DATA_MATRIX, seed = 0)

        assertThat(outcome).isInstanceOf(FuzzOutcome.Produced::class.java)
        val produced = outcome as FuzzOutcome.Produced
        assertThat(produced.case.skipped).isEqualTo(3)
        assertThat(produced.case.seed).isEqualTo(3)
        // Next request must resume after the accepted seed, not repeat it.
        assertThat(produced.nextSeed).isEqualTo(4)
    }

    @Test
    fun `encodes the mutated bytes in binary mode`() {
        var seenMode: InputMode? = null
        val encoder = object : BarcodeEncoder {
            override fun encode(request: EncodeRequest): EncodeResult {
                seenMode = request.payload.mode
                return EncodeResult.Success(okMatrix)
            }
            override fun engineVersion() = "fake"
            override fun supports(symbology: SymbologyId) = true
        }
        FuzzEngine(SeedByteMutator(), encoder).next(base, SymbologyId.QR_CODE, seed = 0)
        assertThat(seenMode).isEqualTo(InputMode.BINARY)
    }

    @Test
    fun `reports NoneEncodable with the last error when nothing encodes`() {
        val encoder = PredicateEncoder({ false }, okMatrix)
        val engine = FuzzEngine(SeedByteMutator(), encoder)

        val outcome = engine.next(base, SymbologyId.QR_CODE, seed = 0, maxAttempts = 10)

        assertThat(outcome).isInstanceOf(FuzzOutcome.NoneEncodable::class.java)
        val none = outcome as FuzzOutcome.NoneEncodable
        assertThat(none.attempts).isEqualTo(10)
        assertThat(none.lastError).isEqualTo("rejected")
        assertThat(none.nextSeed).isEqualTo(10)
    }

    @Test
    fun `charset pre-check spares the encoder on a numeric symbology`() {
        // EAN-13 is Numeric; the seed-byte mutator emits bytes 0,1,2,... which are
        // control bytes, not ASCII digits, so the charset gate rejects every one and
        // the encoder is never called.
        val encoder = PredicateEncoder({ true }, okMatrix)
        val engine = FuzzEngine(SeedByteMutator(), encoder)

        val outcome = engine.next(base, SymbologyId.EAN_13, seed = 0, maxAttempts = 32)

        assertThat(outcome).isInstanceOf(FuzzOutcome.NoneEncodable::class.java)
        assertWithMessage("charset gate should have skipped the encoder entirely")
            .that(encoder.encodeCalls)
            .isEqualTo(0)
    }

    @Test
    fun `fuzzability classifies matrix formats as good and numeric as poor`() {
        fun f(id: SymbologyId) =
            Fuzzability.of(dev.barcodeworkbench.core.model.SymbologyRegistry[id])
        assertThat(f(SymbologyId.QR_CODE)).isEqualTo(Fuzzability.GOOD)
        assertThat(f(SymbologyId.DATA_MATRIX)).isEqualTo(Fuzzability.GOOD)
        assertThat(f(SymbologyId.CODE_128)).isEqualTo(Fuzzability.LIMITED)
        assertThat(f(SymbologyId.EAN_13)).isEqualTo(Fuzzability.POOR)
        assertThat(f(SymbologyId.CODE_39)).isEqualTo(Fuzzability.POOR)
    }
}
