package dev.barcodeworkbench.core.model

/**
 * A symbology-level instruction rather than data.
 *
 * These are the reason the payload cannot simply be modelled as bytes: a codeset
 * switch or an FNC1 changes how the encoder interprets what follows, but
 * contributes no byte of its own to the data stream.
 */
enum class Directive(val escape: String, val label: String, val description: String) {
    CODESET_A("\\^A", "Code A", "Switch to Code 128 Code Set A"),
    CODESET_B("\\^B", "Code B", "Switch to Code 128 Code Set B"),
    CODESET_C("\\^C", "Code C", "Switch to Code 128 Code Set C (numeric pairs)"),
    CODESET_AUTO("\\^@", "Auto", "Return to automatic codeset selection"),
    FNC1("\\^1", "FNC1", "Function 1, the GS1 Application Identifier marker"),
    FNC2("\\^2", "FNC2", "Function 2, message append"),
    FNC3("\\^3", "FNC3", "Function 3, reader programming"),
    FNC4("\\^4", "FNC4", "Function 4, extended ASCII shift"),
    ;

    companion object {
        private val byTrailing: Map<Char, Directive> =
            entries.associateBy { it.escape.last() }

        fun forTrailingChar(ch: Char): Directive? = byTrailing[ch]
    }
}

/** One element of a parsed payload. */
sealed interface PayloadToken {

    /** The exact source text this token came from, for cursor mapping in the UI. */
    val source: String

    /** A single data byte, 0-255. */
    data class Data(val byte: Int, override val source: String) : PayloadToken

    /** A symbology instruction that produces no data byte. */
    data class Instruction(val directive: Directive, override val source: String) : PayloadToken
}

/** A problem in the escape source, located precisely enough to highlight. */
data class EscapeError(
    val position: Int,
    val length: Int,
    val message: String,
)

data class EscapeParseResult(
    val tokens: List<PayloadToken>,
    val errors: List<EscapeError>,
) {
    val isValid: Boolean get() = errors.isEmpty()

    /** Data bytes only; instructions contribute nothing here. */
    fun dataBytes(): ByteArray {
        val out = ByteArray(tokens.count { it is PayloadToken.Data })
        var i = 0
        for (token in tokens) {
            if (token is PayloadToken.Data) {
                out[i++] = token.byte.toByte()
            }
        }
        return out
    }

    val instructions: List<Directive>
        get() = tokens.filterIsInstance<PayloadToken.Instruction>().map { it.directive }
}

/**
 * Expands and produces libzint-compatible escape sequences.
 *
 * The expansion is implemented here rather than delegated to the encoder for one
 * specific reason: the byte inspector has to show the user exactly which bytes
 * their input will become *before* encoding is attempted, including when the
 * payload is currently invalid. Round-tripping through the encoder to find that
 * out would be both slower and useless in the failure case.
 *
 * The supported set matches libzint's own, so anything accepted here is accepted
 * by the encoder.
 */
object EscapeCodec {

    /** Single-character escapes and the byte each yields. */
    private val SIMPLE: Map<Char, Int> = mapOf(
        '0' to 0x00,
        'a' to 0x07,
        'b' to 0x08,
        't' to 0x09,
        'n' to 0x0A,
        'v' to 0x0B,
        'f' to 0x0C,
        'r' to 0x0D,
        'e' to 0x1B,
        '\\' to 0x5C,
    )

    /**
     * Parses escape source into tokens, collecting every problem rather than
     * stopping at the first so the user can see all of them at once.
     */
    fun parse(source: String): EscapeParseResult {
        val tokens = mutableListOf<PayloadToken>()
        val errors = mutableListOf<EscapeError>()
        var i = 0

        while (i < source.length) {
            val ch = source[i]
            if (ch != '\\') {
                // A literal character contributes its UTF-8 bytes.
                val charCount = Character.charCount(source.codePointAt(i))
                val text = source.substring(i, i + charCount)
                text.toByteArray(Charsets.UTF_8).forEach {
                    tokens += PayloadToken.Data(it.toInt() and 0xFF, text)
                }
                i += charCount
                continue
            }

            if (i + 1 >= source.length) {
                errors += EscapeError(i, 1, "Trailing backslash with nothing to escape")
                i += 1
                continue
            }

            val marker = source[i + 1]
            when {
                marker == 'x' -> i = readHexByte(source, i, tokens, errors)
                marker == 'd' -> i = readNumeric(source, i, 10, 3, tokens, errors)
                marker == 'o' -> i = readNumeric(source, i, 8, 3, tokens, errors)
                marker == 'u' -> i = readUnicode(source, i, 4, tokens, errors)
                marker == 'U' -> i = readUnicode(source, i, 6, tokens, errors)
                marker == '^' -> i = readDirective(source, i, tokens, errors)
                SIMPLE.containsKey(marker) -> {
                    tokens += PayloadToken.Data(SIMPLE.getValue(marker), source.substring(i, i + 2))
                    i += 2
                }
                else -> {
                    errors += EscapeError(i, 2, "Unknown escape sequence \\$marker")
                    i += 2
                }
            }
        }

        return EscapeParseResult(tokens, errors)
    }

    private fun readHexByte(
        source: String,
        start: Int,
        tokens: MutableList<PayloadToken>,
        errors: MutableList<EscapeError>,
    ): Int {
        val digitsStart = start + 2
        val available = source.length - digitsStart
        if (available < 2) {
            errors += EscapeError(
                start,
                source.length - start,
                "\\x needs two hex digits, found ${source.substring(digitsStart)}",
            )
            return source.length
        }
        val text = source.substring(digitsStart, digitsStart + 2)
        val value = text.toIntOrNull(16)
        if (value == null) {
            errors += EscapeError(start, 4, "\\x$text is not valid hex")
            return digitsStart + 2
        }
        tokens += PayloadToken.Data(value, source.substring(start, digitsStart + 2))
        return digitsStart + 2
    }

    private fun readNumeric(
        source: String,
        start: Int,
        radix: Int,
        digits: Int,
        tokens: MutableList<PayloadToken>,
        errors: MutableList<EscapeError>,
    ): Int {
        val marker = source[start + 1]
        val digitsStart = start + 2
        if (source.length - digitsStart < digits) {
            errors += EscapeError(
                start,
                source.length - start,
                "\\$marker needs $digits digits",
            )
            return source.length
        }
        val text = source.substring(digitsStart, digitsStart + digits)
        val value = text.toIntOrNull(radix)
        if (value == null || value > 0xFF) {
            errors += EscapeError(
                start,
                2 + digits,
                "\\$marker$text is not a valid byte value",
            )
            return digitsStart + digits
        }
        tokens += PayloadToken.Data(value, source.substring(start, digitsStart + digits))
        return digitsStart + digits
    }

    private fun readUnicode(
        source: String,
        start: Int,
        digits: Int,
        tokens: MutableList<PayloadToken>,
        errors: MutableList<EscapeError>,
    ): Int {
        val marker = source[start + 1]
        val digitsStart = start + 2
        if (source.length - digitsStart < digits) {
            errors += EscapeError(
                start,
                source.length - start,
                "\\$marker needs $digits hex digits",
            )
            return source.length
        }
        val text = source.substring(digitsStart, digitsStart + digits)
        val codePoint = text.toIntOrNull(16)
        if (codePoint == null || codePoint > Character.MAX_CODE_POINT) {
            errors += EscapeError(
                start,
                2 + digits,
                "\\$marker$text is not a valid code point",
            )
            return digitsStart + digits
        }
        val sourceText = source.substring(start, digitsStart + digits)
        // A code point becomes its UTF-8 bytes, which is what the encoder sees.
        String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).forEach {
            tokens += PayloadToken.Data(it.toInt() and 0xFF, sourceText)
        }
        return digitsStart + digits
    }

    private fun readDirective(
        source: String,
        start: Int,
        tokens: MutableList<PayloadToken>,
        errors: MutableList<EscapeError>,
    ): Int {
        if (start + 2 >= source.length) {
            errors += EscapeError(start, 2, "\\^ needs a following character")
            return source.length
        }
        val trailing = source[start + 2]
        val text = source.substring(start, start + 3)

        // \^^ is the escape for a literal \^ sequence.
        if (trailing == '^') {
            tokens += PayloadToken.Data(0x5C, text)
            tokens += PayloadToken.Data(0x5E, text)
            return start + 3
        }

        val directive = Directive.forTrailingChar(trailing)
        if (directive == null) {
            errors += EscapeError(start, 3, "Unknown directive \\^$trailing")
            return start + 3
        }
        tokens += PayloadToken.Instruction(directive, text)
        return start + 3
    }

    /**
     * Renders bytes as escape source, escaping only what must be escaped so the
     * result stays readable.
     */
    fun toEscapeSource(bytes: ByteArray): String = buildString(bytes.size) {
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            when {
                v == 0x5C -> append("\\\\")
                v in 0x20..0x7E -> append(v.toChar())
                else -> append("\\x%02X".format(v))
            }
        }
    }

    /**
     * True when [source] contains anything the encoder must expand. Lets the
     * caller skip enabling escape mode when it would have no effect.
     */
    fun containsEscapes(source: String): Boolean = source.contains('\\')
}
