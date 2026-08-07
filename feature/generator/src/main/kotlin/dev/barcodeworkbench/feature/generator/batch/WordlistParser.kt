package dev.barcodeworkbench.feature.generator.batch

import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry

/** One line of an imported wordlist, with its source position for reporting. */
data class WordlistEntry(
    val lineNumber: Int,
    val payload: String,
    /** Per-row symbology override from a CSV column, when present. */
    val symbologyId: SymbologyId? = null,
    val label: String? = null,
)

data class WordlistParseResult(
    val entries: List<WordlistEntry>,
    val skippedLines: List<Pair<Int, String>>,
) {
    val total: Int get() = entries.size
}

/**
 * Reads a wordlist.
 *
 * Plain text is one payload per line. CSV additionally allows a symbology and a
 * label per row, which is what makes a mixed-format test set possible from a
 * single file.
 *
 * Blank lines are skipped silently, and lines starting with `#` are treated as
 * comments, so a wordlist can document itself.
 */
object WordlistParser {

    private const val COMMENT_PREFIX = "#"

    fun parseText(content: String): WordlistParseResult {
        val entries = mutableListOf<WordlistEntry>()
        content.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.isBlank() || line.startsWith(COMMENT_PREFIX)) return@forEachIndexed
            entries += WordlistEntry(lineNumber = index + 1, payload = line)
        }
        return WordlistParseResult(entries, emptyList())
    }

    /**
     * Parses CSV of the form `payload[,symbology[,label]]`.
     *
     * Deliberately minimal rather than a full RFC 4180 implementation: it handles
     * double-quoted fields with embedded commas and escaped quotes, which covers
     * payloads containing commas, and nothing more.
     */
    fun parseCsv(content: String, hasHeader: Boolean = false): WordlistParseResult {
        val entries = mutableListOf<WordlistEntry>()
        val skipped = mutableListOf<Pair<Int, String>>()

        content.lineSequence().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trimEnd('\r')
            if (line.isBlank() || line.startsWith(COMMENT_PREFIX)) return@forEachIndexed
            if (hasHeader && index == 0) return@forEachIndexed

            val fields = splitCsvLine(line)
            val payload = fields.getOrNull(0)?.takeIf { it.isNotEmpty() }
            if (payload == null) {
                skipped += lineNumber to "No payload in first column"
                return@forEachIndexed
            }

            val symbologyText = fields.getOrNull(1)?.takeIf { it.isNotEmpty() }
            val symbology = symbologyText?.let { resolveSymbology(it) }
            if (symbologyText != null && symbology == null) {
                skipped += lineNumber to "Unknown symbology '$symbologyText'"
                return@forEachIndexed
            }

            entries += WordlistEntry(
                lineNumber = lineNumber,
                payload = payload,
                symbologyId = symbology,
                label = fields.getOrNull(2)?.takeIf { it.isNotEmpty() },
            )
        }
        return WordlistParseResult(entries, skipped)
    }

    /** Picks the parser from the filename, falling back to plain text. */
    fun parse(fileName: String?, content: String): WordlistParseResult =
        if (fileName?.endsWith(".csv", ignoreCase = true) == true) {
            parseCsv(content, hasHeader = looksLikeHeader(content))
        } else {
            parseText(content)
        }

    /** Matches a registry entry by enum name or display name, case-insensitively. */
    private fun resolveSymbology(text: String): SymbologyId? {
        val normalised = text.trim()
        SymbologyRegistry.all.forEach { spec ->
            if (spec.id.name.equals(normalised, ignoreCase = true) ||
                spec.displayName.equals(normalised, ignoreCase = true)
            ) {
                return spec.id
            }
        }
        // Tolerate common punctuation differences such as "Code-128".
        val collapsed = normalised.replace(Regex("[^A-Za-z0-9]"), "").lowercase()
        return SymbologyRegistry.all.firstOrNull { spec ->
            spec.id.name.replace("_", "").lowercase() == collapsed ||
                spec.displayName.replace(Regex("[^A-Za-z0-9]"), "").lowercase() == collapsed
        }?.id
    }

    private fun looksLikeHeader(content: String): Boolean {
        val first = content.lineSequence().firstOrNull()?.lowercase() ?: return false
        return first.startsWith("payload") || first.startsWith("data") || first.startsWith("value")
    }

    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i += 2
                    continue
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    fields += current.toString().trim()
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        fields += current.toString().trim()
        return fields
    }
}
