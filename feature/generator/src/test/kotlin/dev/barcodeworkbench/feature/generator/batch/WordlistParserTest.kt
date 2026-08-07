package dev.barcodeworkbench.feature.generator.batch

import com.google.common.truth.Truth.assertThat
import dev.barcodeworkbench.core.model.SymbologyId
import org.junit.Test

class WordlistParserTest {

    @Test
    fun `plain text yields one entry per line`() {
        val result = WordlistParser.parseText("ABC\nDEF\nGHI")
        assertThat(result.entries.map { it.payload }).containsExactly("ABC", "DEF", "GHI").inOrder()
    }

    @Test
    fun `line numbers reflect the source file, not the entry index`() {
        // Reported failures have to point at the line the user can actually find.
        val result = WordlistParser.parseText("ABC\n\n# comment\nDEF")
        assertThat(result.entries).hasSize(2)
        assertThat(result.entries[0].lineNumber).isEqualTo(1)
        assertThat(result.entries[1].lineNumber).isEqualTo(4)
    }

    @Test
    fun `blank lines and comments are skipped`() {
        val result = WordlistParser.parseText("ABC\n\n   \n# note\nDEF")
        assertThat(result.entries.map { it.payload }).containsExactly("ABC", "DEF")
    }

    @Test
    fun `carriage returns from windows files are stripped`() {
        val result = WordlistParser.parseText("ABC\r\nDEF\r\n")
        assertThat(result.entries.map { it.payload }).containsExactly("ABC", "DEF")
    }

    @Test
    fun `csv reads payload symbology and label`() {
        val result = WordlistParser.parseCsv("012345678901,EAN_13,Retail item")
        val entry = result.entries.single()
        assertThat(entry.payload).isEqualTo("012345678901")
        assertThat(entry.symbologyId).isEqualTo(SymbologyId.EAN_13)
        assertThat(entry.label).isEqualTo("Retail item")
    }

    @Test
    fun `csv symbology accepts display names and punctuation variants`() {
        assertThat(WordlistParser.parseCsv("A,Code 128").entries.single().symbologyId)
            .isEqualTo(SymbologyId.CODE_128)
        assertThat(WordlistParser.parseCsv("A,code-128").entries.single().symbologyId)
            .isEqualTo(SymbologyId.CODE_128)
        assertThat(WordlistParser.parseCsv("A,CODE_128").entries.single().symbologyId)
            .isEqualTo(SymbologyId.CODE_128)
    }

    @Test
    fun `unknown csv symbology is reported rather than silently defaulted`() {
        // Quietly falling back to the default format would produce a batch of
        // wrong barcodes with no indication anything went awry.
        val result = WordlistParser.parseCsv("ABC,NotARealFormat")
        assertThat(result.entries).isEmpty()
        assertThat(result.skippedLines).hasSize(1)
        assertThat(result.skippedLines.single().second).contains("Unknown symbology")
    }

    @Test
    fun `quoted csv field can contain a comma`() {
        val result = WordlistParser.parseCsv("\"ABC,DEF\",CODE_128")
        assertThat(result.entries.single().payload).isEqualTo("ABC,DEF")
    }

    @Test
    fun `doubled quotes inside a quoted field become one quote`() {
        val result = WordlistParser.parseCsv("\"say \"\"hi\"\"\",CODE_128")
        assertThat(result.entries.single().payload).isEqualTo("say \"hi\"")
    }

    @Test
    fun `csv row with no payload is skipped with a reason`() {
        val result = WordlistParser.parseCsv(",CODE_128")
        assertThat(result.entries).isEmpty()
        assertThat(result.skippedLines.single().second).contains("No payload")
    }

    @Test
    fun `csv without a symbology column leaves it unset for the default`() {
        val result = WordlistParser.parseCsv("ABC")
        assertThat(result.entries.single().symbologyId).isNull()
    }

    @Test
    fun `header row is skipped when present`() {
        val result = WordlistParser.parseCsv("payload,symbology\nABC,CODE_128", hasHeader = true)
        assertThat(result.entries).hasSize(1)
        assertThat(result.entries.single().payload).isEqualTo("ABC")
    }

    @Test
    fun `parse picks csv by file extension`() {
        val csv = WordlistParser.parse("codes.csv", "ABC,CODE_128")
        assertThat(csv.entries.single().symbologyId).isEqualTo(SymbologyId.CODE_128)

        // As plain text the same content is one payload including the comma.
        val text = WordlistParser.parse("codes.txt", "ABC,CODE_128")
        assertThat(text.entries.single().payload).isEqualTo("ABC,CODE_128")
        assertThat(text.entries.single().symbologyId).isNull()
    }

    @Test
    fun `header is auto-detected from a payload column name`() {
        val result = WordlistParser.parse("codes.csv", "payload,symbology\nABC,CODE_128")
        assertThat(result.entries).hasSize(1)
    }

    @Test
    fun `escape sequences in a wordlist are preserved verbatim for the encoder`() {
        // The parser must not expand escapes; that is the encoder's job, and doing
        // it here would double-process them.
        val result = WordlistParser.parseText("AB\\x1DCD")
        assertThat(result.entries.single().payload).isEqualTo("AB\\x1DCD")
    }

    @Test
    fun `empty input yields no entries`() {
        assertThat(WordlistParser.parseText("").entries).isEmpty()
        assertThat(WordlistParser.parseCsv("").entries).isEmpty()
    }
}
