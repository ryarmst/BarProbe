package dev.barcodeworkbench.tools.docs

/**
 * The article model and the Markdown parser that produces it.
 *
 * This is intentionally a small, strict parser for the exact conventions the Learn
 * Markdown files use, not a general Markdown implementation. Anything it does not
 * recognise becomes a paragraph, and malformed structure throws rather than being
 * silently dropped -- the whole point of compiling the docs is that a mistake fails the
 * build instead of shipping.
 */

sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Heading(val text: String) : Block
    data class Bullets(val items: List<String>) : Block
    data class Definitions(val items: List<Pair<String, String>>) : Block
    data class Note(val text: String) : Block
    data class Example(val input: String, val result: String, val comment: String?) : Block
}

data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val order: Int,
    val blocks: List<Block>,
)

object MarkdownParser {

    /** Parse one article file. [name] is used only in error messages. */
    fun parse(name: String, text: String): Article {
        val (front, body) = splitFrontMatter(name, text)
        return Article(
            id = front.require("id", name),
            title = front.require("title", name),
            summary = front.require("summary", name),
            order = front.require("order", name).toIntOrNull()
                ?: error("$name: 'order' must be an integer"),
            blocks = parseBlocks(name, body),
        )
    }

    private fun Map<String, String>.require(key: String, name: String): String =
        this[key] ?: error("$name: missing front-matter key '$key'")

    private fun splitFrontMatter(name: String, text: String): Pair<Map<String, String>, String> {
        val lines = text.replace("\r\n", "\n").split("\n")
        require(lines.firstOrNull()?.trim() == "---") { "$name: must start with a --- front-matter block" }
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        require(end >= 0) { "$name: unterminated front-matter block" }
        val front = lines.subList(1, end + 1).associate { line ->
            val i = line.indexOf(':')
            require(i > 0) { "$name: bad front-matter line '$line'" }
            line.substring(0, i).trim() to unquote(line.substring(i + 1).trim())
        }
        return front to lines.subList(end + 2, lines.size).joinToString("\n")
    }

    private fun unquote(s: String): String =
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') s.substring(1, s.length - 1) else s

    private fun parseBlocks(name: String, body: String): List<Block> {
        val lines = body.split("\n")
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++

                line.startsWith("## ") -> {
                    blocks += Block.Heading(line.removePrefix("## ").trim())
                    i++
                }

                line.startsWith("```example") -> {
                    val (block, next) = parseExample(name, lines, i)
                    blocks += block
                    i = next
                }

                line.startsWith("> ") -> {
                    val (block, next) = parseNote(name, lines, i)
                    blocks += block
                    i = next
                }

                line.startsWith("- ") -> {
                    val (block, next) = parseList(lines, i)
                    blocks += block
                    i = next
                }

                else -> {
                    val (block, next) = parseParagraph(lines, i)
                    blocks += block
                    i = next
                }
            }
        }
        return blocks
    }

    private fun parseExample(name: String, lines: List<String>, start: Int): Pair<Block, Int> {
        var i = start + 1
        val fields = mutableMapOf<String, String>()
        while (i < lines.size && !lines[i].startsWith("```")) {
            val line = lines[i]
            if (line.isNotBlank()) {
                val c = line.indexOf(':')
                require(c > 0) { "$name: bad example line '$line'" }
                fields[line.substring(0, c).trim()] = line.substring(c + 1).trim()
            }
            i++
        }
        require(i < lines.size) { "$name: unterminated ```example block" }
        val input = fields["input"] ?: error("$name: example missing 'input'")
        val result = fields["result"] ?: error("$name: example missing 'result'")
        return Block.Example(input, result, fields["comment"]) to i + 1
    }

    private fun parseNote(name: String, lines: List<String>, start: Int): Pair<Block, Int> {
        var i = start
        val quoted = mutableListOf<String>()
        while (i < lines.size && lines[i].startsWith(">")) {
            quoted += lines[i].removePrefix(">").trim()
            i++
        }
        // Drop the leading admonition marker if present; the rest is the note text.
        val text = quoted
            .filterNot { it == "[!NOTE]" }
            .joinToString(" ")
            .trim()
        require(text.isNotEmpty()) { "$name: empty note block" }
        return Block.Note(text) to i
    }

    private val definitionRegex = Regex("""^\*\*(.+?)\*\*\s+—\s+(.+)$""")

    private fun parseList(lines: List<String>, start: Int): Pair<Block, Int> {
        var i = start
        val items = mutableListOf<String>()
        while (i < lines.size && lines[i].startsWith("- ")) {
            items += lines[i].removePrefix("- ").trim()
            i++
        }
        // A list whose every item is "**Term** — definition" is a definition list;
        // any other list is plain bullets.
        val defs = items.map { definitionRegex.matchEntire(it) }
        return if (defs.all { it != null }) {
            Block.Definitions(defs.map { it!!.groupValues[1].trim() to it.groupValues[2].trim() })
        } else {
            Block.Bullets(items)
        } to i
    }

    private fun parseParagraph(lines: List<String>, start: Int): Pair<Block, Int> {
        var i = start
        val parts = mutableListOf<String>()
        while (i < lines.size && lines[i].isNotBlank() &&
            !lines[i].startsWith("## ") && !lines[i].startsWith("- ") &&
            !lines[i].startsWith("> ") && !lines[i].startsWith("```")
        ) {
            parts += lines[i].trim()
            i++
        }
        return Block.Paragraph(parts.joinToString(" ")) to i
    }
}

/** Emits the parsed articles as the app's typed `Concepts.kt`. */
object ConceptsEmitter {

    fun emit(articles: List<Article>): String {
        val sorted = articles.sortedBy { it.order }
        return buildString {
            appendLine("// GENERATED FILE — do not edit.")
            appendLine("// Source: docs/learn/*.md. Regenerate via the :tools:docs generator")
            appendLine("// (run automatically by :feature:learn before compilation).")
            appendLine("package dev.barcodeworkbench.feature.learn.content")
            appendLine()
            appendLine("object Concepts {")
            appendLine()
            appendLine("    val all: List<Article> = listOf(")
            sorted.forEach { article ->
                appendLine("        Article(")
                appendLine("            id = ${kotlin(article.id)},")
                appendLine("            title = ${kotlin(article.title)},")
                appendLine("            summary = ${kotlin(article.summary)},")
                appendLine("            blocks = listOf(")
                article.blocks.forEach { appendLine("                ${block(it)},") }
                appendLine("            ),")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine()
            appendLine("    fun byId(id: String): Article? = all.firstOrNull { it.id == id }")
            appendLine("}")
        }
    }

    private fun block(b: Block): String = when (b) {
        is Block.Paragraph -> "Block.Paragraph(${kotlin(b.text)})"
        is Block.Heading -> "Block.Heading(${kotlin(b.text)})"
        is Block.Note -> "Block.Note(${kotlin(b.text)})"
        is Block.Bullets ->
            "Block.Bullets(listOf(${b.items.joinToString(", ") { kotlin(it) }}))"
        is Block.Definitions ->
            "Block.Definitions(listOf(${
                b.items.joinToString(", ") { "${kotlin(it.first)} to ${kotlin(it.second)}" }
            }))"
        is Block.Example -> buildString {
            append("Block.Example(input = ${kotlin(b.input)}, result = ${kotlin(b.result)}")
            if (b.comment != null) append(", comment = ${kotlin(b.comment)}")
            append(")")
        }
    }

    /** A Kotlin string literal for [s], escaping the four characters that matter. */
    private fun kotlin(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '$' -> sb.append("\\$")
                '\n' -> sb.append("\\n")
                else -> sb.append(c)
            }
        }
        return sb.append("\"").toString()
    }
}
