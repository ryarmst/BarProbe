package dev.barcodeworkbench.tools.docs

import dev.barcodeworkbench.core.model.CharacterPalette
import dev.barcodeworkbench.core.model.Dimension
import dev.barcodeworkbench.core.model.Directive
import dev.barcodeworkbench.core.model.SymbologyRegistry
import java.io.File

/**
 * Emits the reference pages for the web from the same registry and enums the app reads,
 * so the published tables cannot drift from what the encoder actually accepts.
 *
 * Usage: GenerateReference <outputReferenceDir>
 *
 * Run by the Pages workflow, not the app build; the app renders the same data live from
 * Compose. Output files are generated, never committed.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "usage: GenerateReference <outputReferenceDir>" }
    val out = File(args[0]).apply { mkdirs() }

    File(out, "symbologies.md").writeText(symbologiesPage())
    File(out, "escapes.md").writeText(escapesPage())
    System.err.println("generated reference pages in ${out.absolutePath}")
}

private fun symbologiesPage(): String = buildString {
    appendLine("# Supported symbologies")
    appendLine()
    appendLine("Generated from the app's symbology registry, which is verified against")
    appendLine("libzint. Every format the app can produce, with the rules the encoder enforces.")
    appendLine()
    appendLine("| Format | Dim | Characters | Length | Check digit | GS1 | ECI | Scan |")
    appendLine("|---|---|---|---|---|---|---|---|")
    SymbologyRegistry.all.forEach { s ->
        val dim = if (s.dimension == Dimension.LINEAR) "1D" else "2D"
        appendLine(
            "| ${cell(s.displayName)} | $dim | ${cell(s.charsetRule.description)} | " +
                "${cell(s.lengthRule.description)} | ${cell(s.checkDigit.description)} | " +
                "${yn(s.supportsGs1)} | ${yn(s.supportsEci)} | ${yn(s.isReadable)} |",
        )
    }
    appendLine()
    appendLine("_Scan = the app can also read this format, not only generate it._")
}

private fun escapesPage(): String = buildString {
    appendLine("# Escape sequences and directives")
    appendLine()
    appendLine("How to enter bytes and control characters no keyboard offers. The same")
    appendLine("syntax the generator's payload field accepts.")
    appendLine()

    appendLine("## Escape sequences")
    appendLine()
    appendLine("| Syntax | Meaning | Example |")
    appendLine("|---|---|---|")
    listOf(
        Triple("\\xNN", "One byte, hexadecimal", "\\x1D is the Group Separator"),
        Triple("\\dNNN", "One byte, decimal", "\\d029 is also 0x1D"),
        Triple("\\oNNN", "One byte, octal", "\\o035 is also 0x1D"),
        Triple("\\uNNNN", "Unicode code point", "\\u00E9 is é"),
        Triple("\\n \\r \\t", "Newline, return, tab", "The usual control characters"),
        Triple("\\0", "NUL byte", "Survives; not a terminator here"),
        Triple("\\\\", "A literal backslash", "When the data contains one"),
        Triple("\\^^", "A literal \\^", "When the data contains that sequence"),
    ).forEach { (a, b, c) -> appendLine("| ${code(a)} | $b | ${code(c)} |") }
    appendLine()

    appendLine("## Directives, for Code 128 and GS1-128")
    appendLine()
    appendLine("| Escape | Meaning |")
    appendLine("|---|---|")
    Directive.entries.forEach { appendLine("| ${code(it.escape)} | ${cell(it.description)} |") }
    appendLine()

    appendLine("## Control characters")
    appendLine()
    appendLine("All ${CharacterPalette.controlCharacters.size} are available from the Insert")
    appendLine("palette in the generator. The most useful:")
    appendLine()
    appendLine("| Abbr | Escape | Name |")
    appendLine("|---|---|---|")
    val notable = setOf("NUL", "HT", "LF", "CR", "ESC", "FS", "GS", "RS", "US")
    CharacterPalette.controlCharacters.filter { it.label in notable }.forEach {
        appendLine("| ${it.label} | ${code(it.insertText)} | ${cell(it.name)} |")
    }
}

/** Escape a table cell: pipes would break the column, backslashes are shown literally. */
private fun cell(s: String): String = s.replace("|", "\\|")

private fun code(s: String): String = "`${s.replace("|", "\\|")}`"

private fun yn(b: Boolean): String = if (b) "yes" else "—"
