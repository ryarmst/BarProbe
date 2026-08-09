package dev.barcodeworkbench.feature.fuzz

import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.barcode.engine.EncodeRequest
import dev.barcodeworkbench.barcode.engine.EncodeResult
import dev.barcodeworkbench.barcode.engine.Mutator
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import dev.barcodeworkbench.core.model.SymbologySpec
import javax.inject.Inject

/** One fuzz case that encoded into a scannable symbol. */
class FuzzCase(
    /** The mutated bytes that were encoded. This is the reproducible artifact: the
     *  case is saved and replayed by these bytes, not by a seed. */
    val payload: ByteArray,
    val matrix: ModuleMatrix,
    val warning: String?,
    /** The radamsa seed that produced this payload. Shown for interest; it does not
     *  by itself reproduce the case (see [Mutator]). */
    val seed: Int,
    /** Mutations that failed to encode before this one was accepted. */
    val skipped: Int,
)

/** Outcome of asking for the next case. */
sealed interface FuzzOutcome {

    /** A case encoded. [nextSeed] is where the following request should resume. */
    data class Produced(val case: FuzzCase, val nextSeed: Int) : FuzzOutcome

    /**
     * No mutation encoded within the attempt budget. Expected for POOR-fuzzability
     * symbologies; [lastError] carries the encoder's final diagnostic so the UI can
     * explain why (e.g. "needs 12 or 13 digits").
     */
    data class NoneEncodable(
        val attempts: Int,
        val lastError: String?,
        val nextSeed: Int,
    ) : FuzzOutcome
}

/**
 * Turns a base payload into a stream of encodable mutations.
 *
 * Intent: keep every produced barcode valid so the target under test is the system
 * that *reads* the symbol, not the encoder. A mutation that the chosen symbology
 * cannot encode is skipped, and the next seed is tried, up to [defaultMaxAttempts]
 * per request. The count of skips is reported rather than hidden, because for a
 * restricted symbology it is the honest signal that this is the wrong format to fuzz.
 *
 * The trial encode is authoritative, but a cheap charset pre-check rejects the
 * obviously-doomed mutations first so a POOR symbology does not run the encoder 64
 * times per request for nothing.
 */
class FuzzEngine @Inject constructor(
    private val mutator: Mutator,
    private val encoder: BarcodeEncoder,
) {

    fun next(
        base: ByteArray,
        symbology: SymbologyId,
        seed: Int,
        maxAttempts: Int = defaultMaxAttempts,
    ): FuzzOutcome {
        val spec = SymbologyRegistry[symbology]
        var lastError: String? = null

        for (offset in 0 until maxAttempts) {
            val attemptSeed = seed + offset
            val mutated = mutator.mutate(base, attemptSeed)

            if (!charsetAdmits(spec, mutated)) {
                lastError = "contains bytes ${spec.displayName} cannot encode"
                continue
            }

            val request = EncodeRequest(
                symbology = symbology,
                // Mutated bytes are data, not text: BINARY mode hands them to the
                // encoder verbatim with no escape or charset reinterpretation.
                payload = Payload(mutated, mode = InputMode.BINARY),
            )
            when (val result = encoder.encode(request)) {
                is EncodeResult.Success ->
                    return FuzzOutcome.Produced(
                        case = FuzzCase(
                            payload = mutated,
                            matrix = result.matrix,
                            warning = result.warning,
                            seed = attemptSeed,
                            skipped = offset,
                        ),
                        nextSeed = seed + offset + 1,
                    )

                is EncodeResult.Failure -> lastError = result.message
            }
        }

        return FuzzOutcome.NoneEncodable(
            attempts = maxAttempts,
            lastError = lastError,
            nextSeed = seed + maxAttempts,
        )
    }

    /**
     * Fast reject: if any byte is outside the symbology's charset the encode cannot
     * succeed, so there is no point running it. In BINARY terms each byte stands for
     * one code point. A charset pass does not guarantee an encode -- length and check
     * digits are still the encoder's call -- so this only ever skips work, never
     * accepts anything the encoder would reject.
     */
    private fun charsetAdmits(spec: SymbologySpec, bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        return bytes.all { spec.charsetRule.allows(it.toInt() and 0xFF) }
    }

    companion object {
        /**
         * Enough that a LIMITED symbology usually lands a case per request, but small
         * enough that a POOR one returns quickly with its "nothing encoded" signal
         * rather than stalling the UI.
         */
        const val defaultMaxAttempts = 64
    }
}
