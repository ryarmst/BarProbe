package dev.barcodeworkbench.radamsa

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real JNI path on a device.
 *
 * Ignored while the fuzz feature is shelved. radamsa's library-mode VM crashes under
 * ART (SIGSEGV in the Owl runtime) even though the identical code runs cleanly as a
 * standalone binary and via dlopen on the same device -- so these tests currently
 * crash the instrumentation process rather than fail. The fix is to run radamsa in a
 * separate process; see TODO-fuzzing.md. Remove @Ignore when that lands.
 */
@Ignore("Fuzz engine crashes under ART; shelved pending the subprocess design. See TODO-fuzzing.md")
@RunWith(AndroidJUnit4::class)
class RadamsaMutatorTest {

    private val mutator = RadamsaMutator()
    private val base = "https://example.com/order?id=42&qty=1".toByteArray()

    @Test
    fun engineLoadsForThisAbi() {
        assertWithMessage("native library failed to load for this ABI")
            .that(mutator.isAvailable())
            .isTrue()
    }

    @Test
    fun mutateProducesOutput() {
        val out = mutator.mutate(base, seed = 1)
        assertThat(out).isNotNull()
        // radamsa can legitimately return an empty mutation occasionally, but not
        // for a non-trivial input on a fixed low seed; this guards a dead bridge.
        assertThat(out.isNotEmpty()).isTrue()
    }

    @Test
    fun distinctSeedsProduceVariety() {
        // A stuck engine would return the same bytes regardless of seed. Over a
        // spread of seeds the large majority should differ.
        val outputs = (0 until 50).map { mutator.mutate(base, seed = it).toList() }
        val distinct = outputs.toSet().size
        assertWithMessage("only $distinct/50 seeds gave distinct output")
            .that(distinct)
            .isAtLeast(40)
    }

    @Test
    fun respectsTheLengthCap() {
        val cap = 8
        repeat(20) { seed ->
            val out = mutator.mutate(base, seed = seed, maxLength = cap)
            assertWithMessage("output exceeded the cap")
                .that(out.size)
                .isAtMost(cap)
        }
    }

    @Test
    fun survivesManyCallsWithoutInitLeak() {
        // The leak was in radamsa_init(); the Mutator calls it once and then only
        // mutates. Ten thousand mutations complete in well under a second when the
        // engine is healthy.
        var total = 0L
        repeat(10_000) { seed ->
            total += mutator.mutate(base, seed = seed).size
        }
        assertThat(total).isGreaterThan(0L)
    }

    @Test
    fun handlesEmptyInput() {
        // Fuzzing from an empty base is a legitimate request; it must not crash.
        val out = mutator.mutate(ByteArray(0), seed = 3)
        assertThat(out).isNotNull()
    }
}
