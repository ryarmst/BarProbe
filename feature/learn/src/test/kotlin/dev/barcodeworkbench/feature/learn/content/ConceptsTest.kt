package dev.barcodeworkbench.feature.learn.content

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.core.model.EscapeCodec
import org.junit.Test

/**
 * Structural checks on the reference content.
 *
 * The point of holding articles as typed data rather than markdown was that they could
 * be asserted. These tests take that up: ids stay stable and unique because they are
 * used for navigation, no block is empty, and any example claiming to produce specific
 * bytes is run through the real escape expander.
 */
class ConceptsTest {

    @Test
    fun `article ids are unique`() {
        assertThat(Concepts.all.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `byId finds every article and rejects unknown ids`() {
        Concepts.all.forEach { article ->
            assertWithMessage("byId(${article.id})")
                .that(Concepts.byId(article.id))
                .isEqualTo(article)
        }
        assertThat(Concepts.byId("no-such-article")).isNull()
    }

    @Test
    fun `ids are navigation-safe slugs`() {
        Concepts.all.forEach { article ->
            assertWithMessage("id '${article.id}' is not a plain slug")
                .that(article.id.matches(Regex("[a-z0-9-]+")))
                .isTrue()
        }
    }

    @Test
    fun `articles have a title, a summary and content`() {
        assertThat(Concepts.all).isNotEmpty()
        Concepts.all.forEach { article ->
            assertWithMessage("title of ${article.id}").that(article.title).isNotEmpty()
            assertWithMessage("summary of ${article.id}").that(article.summary).isNotEmpty()
            assertWithMessage("blocks of ${article.id}").that(article.blocks).isNotEmpty()
        }
    }

    @Test
    fun `no block renders as empty space`() {
        Concepts.all.forEach { article ->
            article.blocks.forEachIndexed { index, block ->
                val where = "${article.id} block $index"
                when (block) {
                    is Block.Paragraph ->
                        assertWithMessage(where).that(block.text.trim()).isNotEmpty()
                    is Block.Heading ->
                        assertWithMessage(where).that(block.text.trim()).isNotEmpty()
                    is Block.Note ->
                        assertWithMessage(where).that(block.text.trim()).isNotEmpty()
                    is Block.Bullets -> {
                        assertWithMessage(where).that(block.items).isNotEmpty()
                        block.items.forEach {
                            assertWithMessage(where).that(it.trim()).isNotEmpty()
                        }
                    }
                    is Block.Definitions -> {
                        assertWithMessage(where).that(block.items).isNotEmpty()
                        block.items.forEach { (term, definition) ->
                            assertWithMessage("$where term").that(term.trim()).isNotEmpty()
                            assertWithMessage("$where definition")
                                .that(definition.trim()).isNotEmpty()
                        }
                    }
                    is Block.Example -> {
                        assertWithMessage(where).that(block.input).isNotEmpty()
                        assertWithMessage(where).that(block.result).isNotEmpty()
                    }
                }
            }
        }
    }

    /**
     * An example that prints a hex byte string is making a checkable claim. Examples
     * that describe their result in words -- GS1 fields, for instance -- are not, and
     * are skipped.
     */
    @Test
    fun `hex examples match what the escape codec produces`() {
        var checked = 0
        Concepts.all.forEach { article ->
            article.blocks.filterIsInstance<Block.Example>().forEach { example ->
                val expected = parseHexOrNull(example.result) ?: return@forEach
                checked++

                val parsed = EscapeCodec.parse(example.input)
                assertWithMessage("${article.id}: '${example.input}' -> ${parsed.errors}")
                    .that(parsed.isValid)
                    .isTrue()
                assertWithMessage("${article.id}: '${example.input}'")
                    .that(parsed.dataBytes().toHex())
                    .isEqualTo(expected.toHex())
            }
        }
        // Guards against the assertion above silently checking nothing.
        assertThat(checked).isAtLeast(2)
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
