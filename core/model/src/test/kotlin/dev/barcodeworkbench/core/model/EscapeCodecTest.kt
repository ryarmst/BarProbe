package dev.barcodeworkbench.core.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class EscapeCodecTest {

    private fun bytesOf(source: String): List<Int> =
        EscapeCodec.parse(source).dataBytes().map { it.toInt() and 0xFF }

    @Test
    fun `plain ascii passes through unchanged`() {
        assertThat(bytesOf("ABC123")).containsExactly(0x41, 0x42, 0x43, 0x31, 0x32, 0x33).inOrder()
    }

    @Test
    fun `hex escape yields the byte`() {
        assertThat(bytesOf("A\\x1DB")).containsExactly(0x41, 0x1D, 0x42).inOrder()
    }

    @Test
    fun `hex escape accepts lower case digits`() {
        assertThat(bytesOf("\\x1d")).containsExactly(0x1D)
    }

    @Test
    fun `NUL byte survives, which a string based API would truncate`() {
        // The specific failure mode the byte-oriented payload type exists to avoid.
        assertThat(bytesOf("A\\x00B")).containsExactly(0x41, 0x00, 0x42).inOrder()
    }

    @Test
    fun `decimal and octal escapes yield the byte`() {
        assertThat(bytesOf("\\d029")).containsExactly(0x1D)
        assertThat(bytesOf("\\o035")).containsExactly(0x1D)
    }

    @Test
    fun `simple escapes map to control bytes`() {
        assertThat(bytesOf("\\n")).containsExactly(0x0A)
        assertThat(bytesOf("\\r")).containsExactly(0x0D)
        assertThat(bytesOf("\\t")).containsExactly(0x09)
        assertThat(bytesOf("\\e")).containsExactly(0x1B)
        assertThat(bytesOf("\\0")).containsExactly(0x00)
    }

    @Test
    fun `double backslash yields one literal backslash`() {
        assertThat(bytesOf("\\\\")).containsExactly(0x5C)
    }

    @Test
    fun `unicode escape becomes UTF-8 bytes`() {
        // U+00E9 is two bytes in UTF-8, so one escape yields two data bytes.
        assertThat(bytesOf("\\u00E9")).containsExactly(0xC3, 0xA9).inOrder()
    }

    @Test
    fun `extended unicode escape becomes UTF-8 bytes`() {
        // U+1F600 is four bytes in UTF-8.
        assertThat(bytesOf("\\U01F600")).hasSize(4)
    }

    @Test
    fun `literal non-ascii text becomes UTF-8 bytes`() {
        assertThat(bytesOf("café")).containsExactly(0x63, 0x61, 0x66, 0xC3, 0xA9).inOrder()
    }

    @Test
    fun `directives are instructions and contribute no data bytes`() {
        val result = EscapeCodec.parse("\\^A001\\^BABC")
        assertThat(result.isValid).isTrue()
        assertThat(result.instructions)
            .containsExactly(Directive.CODESET_A, Directive.CODESET_B).inOrder()
        // Only the literal characters are data.
        assertThat(result.dataBytes().map { it.toInt() and 0xFF })
            .containsExactly(0x30, 0x30, 0x31, 0x41, 0x42, 0x43).inOrder()
    }

    @Test
    fun `FNC1 is recognised as a directive`() {
        val result = EscapeCodec.parse("\\^1010123")
        assertThat(result.instructions).containsExactly(Directive.FNC1)
    }

    @Test
    fun `caret caret escapes a literal backslash caret`() {
        assertThat(bytesOf("\\^^")).containsExactly(0x5C, 0x5E).inOrder()
    }

    @Test
    fun `incomplete hex escape is reported with its position`() {
        val result = EscapeCodec.parse("AB\\x1")
        assertThat(result.isValid).isFalse()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first().position).isEqualTo(2)
        assertThat(result.errors.first().message).contains("two hex digits")
    }

    @Test
    fun `invalid hex digits are reported`() {
        val result = EscapeCodec.parse("\\xZZ")
        assertThat(result.isValid).isFalse()
        assertThat(result.errors.first().message).contains("not valid hex")
    }

    @Test
    fun `unknown escape is reported`() {
        val result = EscapeCodec.parse("\\q")
        assertThat(result.isValid).isFalse()
        assertThat(result.errors.first().message).contains("Unknown escape")
    }

    @Test
    fun `unknown directive is reported`() {
        val result = EscapeCodec.parse("\\^Z")
        assertThat(result.isValid).isFalse()
        assertThat(result.errors.first().message).contains("Unknown directive")
    }

    @Test
    fun `trailing backslash is reported`() {
        val result = EscapeCodec.parse("AB\\")
        assertThat(result.isValid).isFalse()
        assertThat(result.errors.first().message).contains("Trailing backslash")
    }

    @Test
    fun `all errors are collected rather than stopping at the first`() {
        // Showing every problem at once is more useful than revealing them one by one.
        val result = EscapeCodec.parse("\\q\\xZZ\\^Z")
        assertThat(result.errors).hasSize(3)
    }

    @Test
    fun `toEscapeSource round-trips through parse for every byte value`() {
        val original = ByteArray(256) { it.toByte() }
        val source = EscapeCodec.toEscapeSource(original)
        val reparsed = EscapeCodec.parse(source)
        assertWithMessage("round trip of all 256 byte values").that(reparsed.isValid).isTrue()
        assertThat(reparsed.dataBytes()).isEqualTo(original)
    }

    @Test
    fun `toEscapeSource leaves printable ascii readable`() {
        assertThat(EscapeCodec.toEscapeSource("ABC".toByteArray())).isEqualTo("ABC")
    }

    @Test
    fun `toEscapeSource escapes backslash so the result reparses`() {
        val source = EscapeCodec.toEscapeSource("a\\b".toByteArray())
        assertThat(source).isEqualTo("a\\\\b")
        assertThat(EscapeCodec.parse(source).dataBytes()).isEqualTo("a\\b".toByteArray())
    }

    @Test
    fun `token source text is preserved for cursor mapping`() {
        val result = EscapeCodec.parse("A\\x1D")
        assertThat(result.tokens).hasSize(2)
        assertThat(result.tokens[0].source).isEqualTo("A")
        assertThat(result.tokens[1].source).isEqualTo("\\x1D")
    }

    @Test
    fun `containsEscapes detects a backslash`() {
        assertThat(EscapeCodec.containsEscapes("plain")).isFalse()
        assertThat(EscapeCodec.containsEscapes("a\\x1D")).isTrue()
    }

    @Test
    fun `every palette insert text parses cleanly`() {
        // A palette key that produced invalid escape source would be a trap, so
        // this checks the whole catalogue rather than a sample.
        val all = CharacterPalette.controlCharacters +
            CharacterPalette.directives +
            CharacterPalette.highBytes
        all.forEach { item ->
            val result = EscapeCodec.parse(item.insertText)
            assertWithMessage("palette item ${item.label} inserts '${item.insertText}'")
                .that(result.isValid).isTrue()
        }
    }

    @Test
    fun `control palette items yield exactly the byte they advertise`() {
        CharacterPalette.controlCharacters.forEach { item ->
            val bytes = EscapeCodec.parse(item.insertText).dataBytes()
            assertWithMessage("palette item ${item.label}").that(bytes).hasLength(1)
            assertWithMessage("palette item ${item.label} byte value")
                .that(bytes[0].toInt() and 0xFF).isEqualTo(item.byteValue)
        }
    }

    @Test
    fun `high byte palette items yield exactly the byte they advertise`() {
        CharacterPalette.highBytes.forEach { item ->
            val bytes = EscapeCodec.parse(item.insertText).dataBytes()
            assertWithMessage("palette item ${item.label}").that(bytes).hasLength(1)
            assertWithMessage("palette item ${item.label} byte value")
                .that(bytes[0].toInt() and 0xFF).isEqualTo(item.byteValue)
        }
    }
}
