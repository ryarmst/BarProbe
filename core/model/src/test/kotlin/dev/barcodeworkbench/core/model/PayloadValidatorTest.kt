package dev.barcodeworkbench.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PayloadValidatorTest {

    private val code128 = SymbologyRegistry[SymbologyId.CODE_128]
    private val code39 = SymbologyRegistry[SymbologyId.CODE_39]
    private val ean13 = SymbologyRegistry[SymbologyId.EAN_13]
    private val itf = SymbologyRegistry[SymbologyId.ITF]
    private val qr = SymbologyRegistry[SymbologyId.QR_CODE]
    private val dataMatrix = SymbologyRegistry[SymbologyId.DATA_MATRIX]

    @Test
    fun `valid Code 128 payload passes`() {
        val result = PayloadValidator.validate(code128, "ABC123", InputMode.UNICODE)
        assertThat(result.isValid).isTrue()
        assertThat(result.effectiveBytes).isEqualTo("ABC123".toByteArray())
    }

    @Test
    fun `empty payload is reported`() {
        val result = PayloadValidator.validate(code128, "", InputMode.UNICODE)
        assertThat(result.issues).contains(ValidationIssue.Empty)
    }

    @Test
    fun `Code 39 rejects lower case with a specific message`() {
        val result = PayloadValidator.validate(code39, "abc", InputMode.UNICODE)
        assertThat(result.isValid).isFalse()
        val issue = result.issues.filterIsInstance<ValidationIssue.UnsupportedCharacters>().single()
        assertThat(issue.message).contains("'a'")
        assertThat(issue.message).contains("A-Z uppercase")
    }

    @Test
    fun `Code 39 rejects an inserted control character`() {
        // The palette lets a GS be inserted anywhere, so validation has to catch
        // it on symbologies that cannot represent one.
        val result = PayloadValidator.validate(code39, "AB\\x1D", InputMode.UNICODE)
        assertThat(result.isValid).isFalse()
        val issue = result.issues.filterIsInstance<ValidationIssue.UnsupportedCharacters>().single()
        assertThat(issue.message).contains("0x1D")
    }

    @Test
    fun `Code 128 accepts an inserted control character`() {
        val result = PayloadValidator.validate(code128, "AB\\x1DCD", InputMode.UNICODE)
        assertThat(result.isValid).isTrue()
        assertThat(result.effectiveBytes.map { it.toInt() and 0xFF })
            .containsExactly(0x41, 0x42, 0x1D, 0x43, 0x44).inOrder()
    }

    @Test
    fun `EAN-13 rejects wrong length`() {
        val result = PayloadValidator.validate(ean13, "12345", InputMode.UNICODE)
        assertThat(result.isValid).isFalse()
        assertThat(result.issues.filterIsInstance<ValidationIssue.WrongLength>()).isNotEmpty()
    }

    @Test
    fun `EAN-13 accepts twelve or thirteen digits`() {
        assertThat(PayloadValidator.validate(ean13, "012345678901", InputMode.UNICODE).isValid)
            .isTrue()
        assertThat(PayloadValidator.validate(ean13, "0123456789012", InputMode.UNICODE).isValid)
            .isTrue()
    }

    @Test
    fun `EAN-13 rejects letters`() {
        val result = PayloadValidator.validate(ean13, "01234567890X", InputMode.UNICODE)
        assertThat(result.isValid).isFalse()
    }

    @Test
    fun `ITF requires an even digit count`() {
        val odd = PayloadValidator.validate(itf, "12345", InputMode.UNICODE)
        assertThat(odd.isValid).isFalse()
        assertThat(odd.issues.map { it.message }).contains("Needs an even number of digits")

        assertThat(PayloadValidator.validate(itf, "123456", InputMode.UNICODE).isValid).isTrue()
    }

    @Test
    fun `directives are rejected on symbologies that do not support them`() {
        val result = PayloadValidator.validate(qr, "\\^AABC", InputMode.UNICODE)
        assertThat(result.isValid).isFalse()
        val issue = result.issues.filterIsInstance<ValidationIssue.UnsupportedDirective>().single()
        assertThat(issue.message).contains("Code 128")
    }

    @Test
    fun `directives are accepted on Code 128`() {
        val result = PayloadValidator.validate(code128, "\\^A001", InputMode.UNICODE)
        assertThat(result.isValid).isTrue()
        assertThat(result.directives).containsExactly(Directive.CODESET_A)
    }

    @Test
    fun `bad escape suppresses charset and length noise`() {
        // Reporting "unsupported character" on top of "malformed escape" would
        // bury the actual problem.
        val result = PayloadValidator.validate(ean13, "\\xZZ", InputMode.UNICODE)
        assertThat(result.issues.filterIsInstance<ValidationIssue.BadEscape>()).hasSize(1)
        assertThat(result.issues.filterIsInstance<ValidationIssue.WrongLength>()).isEmpty()
        assertThat(result.issues.filterIsInstance<ValidationIssue.UnsupportedCharacters>())
            .isEmpty()
    }

    @Test
    fun `multi-byte characters are validated as code points not bytes`() {
        // In Unicode mode the UTF-8 bytes of an accented character must not each
        // be tested against the charset rule, or every non-ASCII input would fail.
        val result = PayloadValidator.validate(qr, "café", InputMode.UNICODE)
        assertThat(result.isValid).isTrue()
        assertThat(result.effectiveBytes).hasLength(5)
    }

    @Test
    fun `length is counted in code points not bytes in unicode mode`() {
        val result = PayloadValidator.validate(qr, "café", InputMode.UNICODE)
        assertThat(result.isValid).isTrue()
    }

    @Test
    fun `binary mode accepts every byte for Data Matrix`() {
        val allBytes = EscapeCodec.toEscapeSource(ByteArray(256) { it.toByte() })
        val result = PayloadValidator.validate(dataMatrix, allBytes, InputMode.BINARY)
        assertThat(result.issues.filterIsInstance<ValidationIssue.UnsupportedCharacters>())
            .isEmpty()
        assertThat(result.effectiveBytes).hasLength(256)
    }

    @Test
    fun `GS1 bracketed input skips the raw length check`() {
        // The encoder rewrites bracketed AIs into FNC1 separators, so the source
        // length does not correspond to what is encoded.
        val gs1 = SymbologyRegistry[SymbologyId.GS1_128]
        val result = PayloadValidator.validate(gs1, "[01]09501101530003", InputMode.GS1)
        assertThat(result.issues.filterIsInstance<ValidationIssue.WrongLength>()).isEmpty()
    }

    @Test
    fun `every registry sample value validates against its own symbology`() {
        // Sample values are offered to the user as a starting point, so any that
        // failed validation would be actively misleading.
        SymbologyRegistry.all.forEach { spec ->
            val mode = if (spec.supportsGs1 && spec.sampleValue.startsWith("[")) {
                InputMode.GS1
            } else {
                InputMode.UNICODE
            }
            val result = PayloadValidator.validate(spec, spec.sampleValue, mode)
            assertThat(result.issues.map { "${spec.displayName}: ${it.message}" }).isEmpty()
        }
    }
}
