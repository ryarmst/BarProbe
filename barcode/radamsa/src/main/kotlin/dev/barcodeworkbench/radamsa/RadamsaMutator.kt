package dev.barcodeworkbench.radamsa

import dev.barcodeworkbench.barcode.engine.Mutator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [Mutator] backed by libradamsa through JNI.
 *
 * Owns the two rules the native layer imposes: initialise once per process, and
 * never call [RadamsaNative.mutate] concurrently. Initialisation is lazy so the
 * ~1 MB native heap is not paid for unless the fuzz feature is actually opened.
 */
@Singleton
class RadamsaMutator @Inject constructor() : Mutator {

    // radamsa() is not re-entrant, so every mutation runs under this lock. Cost is
    // irrelevant: one mutation is sub-millisecond and the feature is button-driven.
    private val lock = Any()

    override fun mutate(input: ByteArray, seed: Int, maxLength: Int): ByteArray =
        synchronized(lock) {
            RadamsaNative.ensureInitialised()
            RadamsaNative.mutate(input, seed, maxLength)
        }

    override fun isAvailable(): Boolean = RadamsaNative.isAvailable()

    // libradamsa exposes no version symbol, so this is the pinned vendor version
    // recorded in third_party/radamsa/PINNED_VERSION.txt.
    override fun engineVersion(): String = "radamsa 0.7"
}
