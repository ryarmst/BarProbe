package dev.barcodeworkbench.barcode.reader

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import org.junit.Test

/**
 * Guards the format mapping, which is many-to-one and therefore easy to collapse.
 *
 * The original implementation used `associate`, which is last-wins: because both
 * CODE_128 and GS1_128 declare the same reader format, every plain Code 128 scan was
 * silently reported as GS1-128. That is worse than a display glitch, since saving such
 * a scan persists the wrong symbology and re-encoding it would prepend an FNC1 and
 * change the data.
 */
class ZxingFormatMappingTest {

    @Test
    fun `plain Code 128 is not mislabelled as GS1`() {
        assertThat(ZxingFormatMapping.refine(SymbologyId.CODE_128, null))
            .isEqualTo(SymbologyId.CODE_128)
        assertThat(ZxingFormatMapping.refine(SymbologyId.CODE_128, "]C0"))
            .isEqualTo(SymbologyId.CODE_128)
        assertThat(ZxingFormatMapping.refine(SymbologyId.CODE_128, "]C2"))
            .isEqualTo(SymbologyId.CODE_128)
    }

    @Test
    fun `AIM identifier C1 identifies GS1-128`() {
        assertThat(ZxingFormatMapping.refine(SymbologyId.CODE_128, "]C1"))
            .isEqualTo(SymbologyId.GS1_128)
    }

    @Test
    fun `refinement depends on the identifier, not the starting point`() {
        assertThat(ZxingFormatMapping.refine(SymbologyId.GS1_128, "]C1"))
            .isEqualTo(SymbologyId.GS1_128)
        assertThat(ZxingFormatMapping.refine(SymbologyId.GS1_128, "]C0"))
            .isEqualTo(SymbologyId.CODE_128)
    }

    @Test
    fun `unambiguous symbologies pass through untouched`() {
        listOf(SymbologyId.QR_CODE, SymbologyId.EAN_13, SymbologyId.DATA_MATRIX).forEach { id ->
            assertThat(ZxingFormatMapping.refine(id, "]Q1")).isEqualTo(id)
            assertThat(ZxingFormatMapping.refine(id, null)).isEqualTo(id)
        }
    }

    @Test
    fun `null base stays null`() {
        assertThat(ZxingFormatMapping.refine(null, "]C1")).isNull()
    }

    @Test
    fun `the base mapping prefers the non-GS1 reading`() {
        assertThat(ZxingFormatMapping.symbologyFor("CODE_128")).isEqualTo(SymbologyId.CODE_128)
    }

    @Test
    fun `every ambiguous format has a disambiguation rule`() {
        // Fails if a future registry entry introduces a new many-to-one mapping without
        // teaching refine() how to resolve it, which is how this bug arose.
        assertWithMessage(
            "these formats are claimed by more than one registry entry and need a " +
                "rule in ZxingFormatMapping.refine()",
        ).that(ZxingFormatMapping.ambiguousFormatNames).isEqualTo(setOf("CODE_128"))
    }

    @Test
    fun `every readable registry entry resolves to an engine format`() {
        SymbologyRegistry.readable.forEach { spec ->
            assertWithMessage("${spec.displayName} declares readerFormat ${spec.readerFormat}")
                .that(ZxingFormatMapping.formatFor(spec.id)).isNotNull()
        }
    }

    @Test
    fun `generate-only formats are absent from the readable set`() {
        listOf(SymbologyId.DOTCODE, SymbologyId.CODE_11, SymbologyId.MSI_PLESSEY).forEach { id ->
            assertThat(ZxingFormatMapping.readableSymbologies).doesNotContain(id)
        }
    }
}
