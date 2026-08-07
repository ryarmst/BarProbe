package dev.barcodeworkbench.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The guide exists to stop users guessing how to type a payload. Documentation that is
 * wrong is worse than none, so the examples are checked against the real escape
 * expander rather than merely reviewed.
 */
class InputModeGuideTest {

    @Test
    fun `every input mode has a guide`() {
        InputMode.entries.forEach { mode ->
            assertWithMessage("no guide for $mode")
                .that(InputModeGuide.all.map { it.mode })
                .contains(mode)
        }
    }

    @Test
    fun `no duplicate guides`() {
        assertThat(InputModeGuide.all.map { it.mode })
            .containsNoDuplicates()
    }

    @Test
    fun `forMode returns the matching guide`() {
        InputMode.entries.forEach { mode ->
            assertThat(InputModeGuide.forMode(mode).mode).isEqualTo(mode)
        }
    }

    @Test
    fun `text is populated and the summary stays on one line`() {
        InputModeGuide.all.forEach { guide ->
            assertWithMessage("label for ${guide.mode}").that(guide.label).isNotEmpty()
            assertWithMessage("detail for ${guide.mode}").that(guide.detail).isNotEmpty()
            assertWithMessage("examples for ${guide.mode}").that(guide.examples).isNotEmpty()

            // Shown inline under the mode chips, where a long string wraps badly.
            assertWithMessage("summary for ${guide.mode} is too long to display inline")
                .that(guide.summary.length)
                .isAtMost(80)
        }
    }

    /**
     * Where an example claims to produce specific bytes, it has to actually produce
     * them. GS1 examples describe fields rather than bytes and are skipped by the
     * hex-shape check below.
     */
    @Test
    fun `hex examples match what the escape codec produces`() {
        var checked = 0
        InputModeGuide.all.forEach { guide ->
            guide.examples.forEach { example ->
                val expected = parseHexOrNull(example.produces) ?: return@forEach
                checked++

                val result = EscapeCodec.parse(example.input)
                assertWithMessage("'${example.input}' failed to parse: ${result.errors}")
                    .that(result.isValid)
                    .isTrue()
                assertWithMessage("'${example.input}' in ${guide.mode}")
                    .that(result.dataBytes().toHex())
                    .isEqualTo(expected.toHex())
            }
        }
        // Guards against the assertion above silently checking nothing.
        assertThat(checked).isAtLeast(6)
    }

    private fun parseHexOrNull(text: String): ByteArray? {
        val parts = text.split(" ")
        if (parts.isEmpty()) return null
        if (!parts.all { it.length == 2 && it.all { c -> c in "0123456789ABCDEF" } }) return null
        return ByteArray(parts.size) { parts[it].toInt(16).toByte() }
    }

    private fun ByteArray.toHex(): String =
        joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
