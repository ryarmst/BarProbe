package dev.barcodeworkbench.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class EciRegistryTest {

    @Test
    fun `values are unique`() {
        val values = EciRegistry.common.map { it.value }
        assertThat(values).containsNoDuplicates()
    }

    @Test
    fun `labels are unique and non-blank`() {
        val labels = EciRegistry.common.map { it.label }
        assertThat(labels).containsNoDuplicates()
        labels.forEach { assertThat(it).isNotEmpty() }
    }

    @Test
    fun `well known assignments are present with the expected numbers`() {
        // These four are the ones that come up constantly, and a transposed number
        // would produce a symbol that decodes to the wrong characters rather than
        // failing visibly.
        assertThat(EciRegistry.find(3)?.label).isEqualTo("ISO-8859-1")
        assertThat(EciRegistry.find(26)?.label).isEqualTo("UTF-8")
        assertThat(EciRegistry.find(20)?.label).isEqualTo("Shift JIS")
        assertThat(EciRegistry.find(899)?.label).isEqualTo("8-bit binary")
    }

    @Test
    fun `values are positive`() {
        EciRegistry.common.forEach { option ->
            assertWithMessage("${option.label} value").that(option.value).isGreaterThan(0)
        }
    }

    @Test
    fun `lookup returns null for an unlisted value`() {
        assertThat(EciRegistry.find(12345)).isNull()
    }

    @Test
    fun `label falls back to the bare number for unlisted values`() {
        // The encoder accepts any numeric ECI, so the UI must render one it does not
        // have a name for rather than showing a blank.
        assertThat(EciRegistry.labelFor(12345)).isEqualTo("ECI 12345")
        assertThat(EciRegistry.labelFor(26)).contains("UTF-8")
    }

    @Test
    fun `every symbology claiming ECI support is a matrix format`() {
        // ECI is carried in the symbol's own encoding modes, which linear symbologies
        // do not have. A linear entry claiming support would be a registry error.
        SymbologyRegistry.all.filter { it.supportsEci }.forEach { spec ->
            assertWithMessage("${spec.displayName} claims ECI support")
                .that(spec.dimension).isEqualTo(Dimension.MATRIX)
        }
    }
}
