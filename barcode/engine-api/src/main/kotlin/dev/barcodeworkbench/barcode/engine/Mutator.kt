package dev.barcodeworkbench.barcode.engine

/**
 * Produces mutated variants of a byte payload, for fuzzing.
 *
 * The fuzz feature depends only on this interface, exactly as the generator
 * depends only on [BarcodeEncoder]. Swapping the mutation engine is then a change
 * to one Hilt binding.
 *
 * Contract notes that come from the backing engine (radamsa) and matter to callers:
 *
 *  - **Stateful stream, not a pure function.** A single call's output depends on
 *    every call before it in the process, not on [seed] alone. The engine's
 *    reproducible unit is the whole post-initialisation sequence, not one
 *    `(input, seed)` pair, so a mutated payload is reproducible by *keeping its
 *    bytes*, not by remembering a seed. [seed] varies the stream; it does not
 *    address into it.
 *  - **Serialised.** Implementations are not required to be thread-safe and may
 *    serialise internally; treat calls as ordered.
 */
interface Mutator {

    /**
     * Returns one mutation of [input].
     *
     * @param input the base bytes to mutate
     * @param seed  entropy fed into this mutation; changing it changes the output
     * @param maxLength hard cap on the returned length; output is truncated to it
     * @return the mutated bytes, which may be shorter or longer than [input]
     */
    fun mutate(input: ByteArray, seed: Int, maxLength: Int = DEFAULT_MAX_LENGTH): ByteArray

    /** Whether the native engine loaded for this ABI. */
    fun isAvailable(): Boolean

    /** Engine identity for the attribution screen. */
    fun engineVersion(): String

    companion object {
        /**
         * Generous relative to every symbology's own capacity (a few KB at most),
         * so the cap never silently shapes a mutation the encoder would have
         * accepted; over-capacity output is rejected by the encoder, not here.
         */
        const val DEFAULT_MAX_LENGTH: Int = 65536
    }
}
