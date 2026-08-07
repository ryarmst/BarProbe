package dev.barcodeworkbench.feature.learn

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.core.model.CharacterPalette
import org.junit.Test

/**
 * The cheat sheet picks a subset of the palette by label. That lookup fails silently,
 * so it is asserted here rather than left to be noticed on screen.
 */
class QuickReferenceTest {

    @Test
    fun `every notable control character resolves in the palette`() {
        val available = CharacterPalette.controlCharacters.map { it.label }
        notableControlLabels.forEach { label ->
            assertWithMessage("'$label' is no longer in the control palette")
                .that(available)
                .contains(label)
        }
    }

    @Test
    fun `the rendered subset matches the requested labels`() {
        assertThat(notableControls.map { it.label })
            .containsExactlyElementsIn(notableControlLabels)
    }

    @Test
    fun `notable control characters carry insertable escape text`() {
        notableControls.forEach { item ->
            assertWithMessage("insert text for ${item.label}")
                .that(item.insertText)
                .startsWith("\\")
        }
    }
}
