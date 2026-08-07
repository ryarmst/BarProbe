package dev.barcodeworkbench.core.model

/** A worked example of how to type something in a given input mode. */
data class InputExample(
    val input: String,
    val produces: String,
    val note: String? = null,
)

/** Everything the UI needs to explain one input mode. */
data class InputModeGuide(
    val mode: InputMode,
    val label: String,
    /** One line, shown inline under the mode selector. */
    val summary: String,
    val examples: List<InputExample>,
    /** Longer explanation for the reference page. */
    val detail: String,
) {
    companion object {

        private val unicode = InputModeGuide(
            mode = InputMode.UNICODE,
            label = "Text",
            summary = "Type normally. Text becomes UTF-8 bytes; use escapes for the rest.",
            examples = listOf(
                InputExample("HELLO", "48 45 4C 4C 4F", "Plain ASCII is one byte per character."),
                InputExample("café", "63 61 66 C3 A9", "Accented characters take two bytes in UTF-8."),
                InputExample("AB\\x1DCD", "41 42 1D 43 44", "\\xNN inserts any byte by hex value."),
            ),
            detail = "The default. What you type is converted to UTF-8. Escape sequences " +
                "let you add characters no keyboard offers, such as control codes. If " +
                "the content is not plain ASCII, set an ECI so the reader knows how to " +
                "interpret it.",
        )

        private val binary = InputModeGuide(
            mode = InputMode.BINARY,
            label = "Raw bytes",
            summary = "Every value is one byte. Nothing is interpreted as text.",
            examples = listOf(
                InputExample("\\x00\\x01\\xFF", "00 01 FF", "Values no text encoding can carry."),
                InputExample("A\\x00B", "41 00 42", "A NUL byte in the middle, preserved."),
                InputExample("\\d065\\o101", "41 41", "Decimal and octal escapes also work."),
            ),
            detail = "Use when the payload is data rather than text: firmware blobs, " +
                "packed structures, or anything with bytes above 0x7F that must not be " +
                "reinterpreted. No character encoding is applied, so what you specify is " +
                "exactly what is encoded.",
        )

        private val gs1 = InputModeGuide(
            mode = InputMode.GS1,
            label = "GS1",
            summary = "Wrap Application Identifiers in brackets; separators are automatic.",
            examples = listOf(
                InputExample(
                    "[01]09501101530003",
                    "GTIN field",
                    "AI 01 is fixed length, so no separator is needed after it.",
                ),
                InputExample(
                    "[01]09501101530003[10]LOT123",
                    "GTIN + batch",
                    "AI 10 is variable length and is terminated automatically.",
                ),
                InputExample(
                    "[17]261231[10]ABC",
                    "Expiry + batch",
                    "Dates are YYMMDD, six digits.",
                ),
            ),
            detail = "For supply-chain data. Brackets are notation and never appear in " +
                "the encoded bytes; the encoder converts them to FNC1 markers and " +
                "inserts field separators where the standard requires them. Available " +
                "only on symbologies that support GS1.",
        )

        val all: List<InputModeGuide> = listOf(unicode, binary, gs1)

        fun forMode(mode: InputMode): InputModeGuide = all.first { it.mode == mode }
    }
}
