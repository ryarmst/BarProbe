package dev.barcodeworkbench.tools.docs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownParserTest {

    private val sample = """
        ---
        id: sample
        title: A sample
        summary: One line.
        order: 10
        ---

        First paragraph that
        wraps across two lines.

        ## A heading

        - plain one
        - plain two

        - **Term A** — meaning A
        - **Term B** — meaning B

        > [!NOTE]
        > A caution worth reading.

        ```example
        input: AB\x1DCD
        result: 41 42 1D 43 44
        comment: Five bytes.
        ```
    """.trimIndent()

    private val article = MarkdownParser.parse("sample.md", sample)

    @Test
    fun `front matter is read`() {
        assertThat(article.id).isEqualTo("sample")
        assertThat(article.title).isEqualTo("A sample")
        assertThat(article.summary).isEqualTo("One line.")
        assertThat(article.order).isEqualTo(10)
    }

    @Test
    fun `wrapped paragraph joins into one`() {
        val p = article.blocks.filterIsInstance<Block.Paragraph>().first()
        assertThat(p.text).isEqualTo("First paragraph that wraps across two lines.")
    }

    @Test
    fun `heading parsed`() {
        assertThat(article.blocks.filterIsInstance<Block.Heading>().single().text)
            .isEqualTo("A heading")
    }

    @Test
    fun `plain list is bullets, term-dash list is definitions`() {
        val bullets = article.blocks.filterIsInstance<Block.Bullets>().single()
        assertThat(bullets.items).containsExactly("plain one", "plain two").inOrder()

        val defs = article.blocks.filterIsInstance<Block.Definitions>().single()
        assertThat(defs.items).containsExactly(
            "Term A" to "meaning A",
            "Term B" to "meaning B",
        ).inOrder()
    }

    @Test
    fun `note strips the admonition marker`() {
        assertThat(article.blocks.filterIsInstance<Block.Note>().single().text)
            .isEqualTo("A caution worth reading.")
    }

    @Test
    fun `example keeps the backslash escape verbatim`() {
        val ex = article.blocks.filterIsInstance<Block.Example>().single()
        assertThat(ex.input).isEqualTo("""AB\x1DCD""")
        assertThat(ex.result).isEqualTo("41 42 1D 43 44")
        assertThat(ex.comment).isEqualTo("Five bytes.")
    }

    @Test
    fun `emitter escapes the backslash so the literal survives`() {
        val kt = ConceptsEmitter.emit(listOf(article))
        // The runtime string must be AB\x1DCD, so the source must contain AB\\x1DCD.
        assertThat(kt).contains("""AB\\x1DCD""")
        assertThat(kt).contains("object Concepts")
        assertThat(kt).contains("fun byId")
    }

    @Test
    fun `missing front-matter key fails loudly`() {
        val bad = "---\nid: x\ntitle: y\norder: 1\n---\nbody"
        val e = runCatching { MarkdownParser.parse("bad.md", bad) }.exceptionOrNull()
        assertThat(e).isNotNull()
        assertThat(e!!).hasMessageThat().contains("summary")
    }
}
