package dev.barcodeworkbench.feature.learn.content

/**
 * Reference content as structured data rather than markdown.
 *
 * Keeping it typed means the renderer controls typography consistently, examples get
 * monospaced treatment automatically, and the content can be asserted by test -- a
 * blob of markdown could silently lose a section and nothing would notice.
 */
sealed interface Block {

    data class Paragraph(val text: String) : Block

    data class Heading(val text: String) : Block

    data class Bullets(val items: List<String>) : Block

    /**
     * A worked example. [input] is what the user types, [result] what it produces.
     * Rendered monospaced with the two clearly separated.
     */
    data class Example(
        val input: String,
        val result: String,
        val comment: String? = null,
    ) : Block

    /** A caveat worth interrupting the flow for. */
    data class Note(val text: String) : Block

    /** Term-and-definition pairs, for compact reference material. */
    data class Definitions(val items: List<Pair<String, String>>) : Block
}

data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val blocks: List<Block>,
)
