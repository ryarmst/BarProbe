package dev.barcodeworkbench.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * Invariants over the symbology table.
 *
 * These run on the host with no device and no native library. They catch the
 * transcription and consistency mistakes that are otherwise silent -- the kind
 * that surface much later as a confusing encoder error.
 */
class SymbologyRegistryTest {

    @Test
    fun `every SymbologyId has exactly one registry entry`() {
        val ids = SymbologyRegistry.all.map { it.id }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids).containsExactlyElementsIn(SymbologyId.entries)
    }

    @Test
    fun `zint symbol ids are unique`() {
        val zintIds = SymbologyRegistry.all.map { it.zintSymbolId }
        assertThat(zintIds).containsNoDuplicates()
    }

    @Test
    fun `zint symbol ids are plausible`() {
        // libzint's BARCODE_* space is small and positive; a zero or negative
        // value means an entry was left unfilled.
        SymbologyRegistry.all.forEach { spec ->
            assertThat(spec.zintSymbolId).isGreaterThan(0)
            assertThat(spec.zintSymbolId).isLessThan(200)
        }
    }

    @Test
    fun `display names are unique and non-blank`() {
        val names = SymbologyRegistry.all.map { it.displayName }
        assertThat(names).containsNoDuplicates()
        names.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun `zint constant names follow the BARCODE_ convention`() {
        SymbologyRegistry.all.forEach { spec ->
            assertThat(spec.zintConstantName).startsWith("BARCODE_")
        }
    }

    @Test
    fun `each sample value satisfies its own charset rule`() {
        SymbologyRegistry.all.forEach { spec ->
            val rejected = spec.charsetRule.rejectedCodePoints(spec.sampleValue)
            assertWithMessage(
                "${spec.displayName} sample '${spec.sampleValue}' has rejected code points",
            ).that(rejected).isEmpty()
        }
    }

    @Test
    fun `each sample value satisfies its own length rule`() {
        SymbologyRegistry.all.forEach { spec ->
            // GS1 samples carry bracketed AIs that the encoder strips, so their
            // raw length is not meaningful against the rule.
            if (spec.supportsGs1 && spec.sampleValue.startsWith("[")) return@forEach
            val verdict = spec.lengthRule.check(spec.sampleValue.length)
            assertWithMessage(
                "${spec.displayName} sample length ${spec.sampleValue.length} " +
                    "violates ${spec.lengthRule.description}",
            ).that(verdict.isOk).isTrue()
        }
    }

    @Test
    fun `generate-only formats are documented as such`() {
        // A format the reader cannot handle must say so, otherwise the scanner UI
        // would offer a toggle that can never match anything.
        SymbologyRegistry.generateOnly.forEach { spec ->
            assertWithMessage("${spec.displayName} notes must explain it is generate-only")
                .that(spec.notes.lowercase()).contains("generate only")
        }
    }

    @Test
    fun `DotCode is generate-only because the reader engine cannot decode it`() {
        assertThat(SymbologyRegistry[SymbologyId.DOTCODE].isReadable).isFalse()
    }

    @Test
    fun `codeset escapes are only claimed by Code 128 family`() {
        val withEscapes = SymbologyRegistry.all
            .filter { it.supportsCodesetEscapes }
            .map { it.id }
        assertThat(withEscapes).containsExactly(SymbologyId.CODE_128, SymbologyId.GS1_128)
    }

    @Test
    fun `GS1 category members support GS1 mode`() {
        SymbologyRegistry.byCategory(Category.GS1).forEach { spec ->
            assertWithMessage("${spec.displayName} is in the GS1 category")
                .that(spec.supportsGs1).isTrue()
        }
    }

    @Test
    fun `readable and generate-only partition the registry`() {
        assertThat(SymbologyRegistry.readable.size + SymbologyRegistry.generateOnly.size)
            .isEqualTo(SymbologyRegistry.all.size)
    }

    @Test
    fun `lookup by id returns the matching spec`() {
        SymbologyId.entries.forEach { id ->
            assertThat(SymbologyRegistry[id].id).isEqualTo(id)
        }
    }
}
