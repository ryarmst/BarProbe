package dev.barcodeworkbench.core.model

/**
 * Which characters a symbology can encode.
 *
 * Membership is testable per code point rather than only as a whole-string
 * regex, because the payload composer greys out palette entries that the
 * selected symbology cannot represent. That is the difference between a palette
 * that guides the user and one that lets them build an unencodable payload.
 */
sealed interface CharsetRule {

    /** Human-readable summary shown next to the input field. */
    val description: String

    fun allows(codePoint: Int): Boolean

    /** Any single byte, including control characters and NUL. */
    data object AnyByte : CharsetRule {
        override val description = "Any byte value 0-255, including control characters"
        override fun allows(codePoint: Int) = codePoint in 0..255
    }

    /** Digits only. */
    data object Numeric : CharsetRule {
        override val description = "Digits 0-9 only"
        override fun allows(codePoint: Int) = codePoint in 0x30..0x39
    }

    /** Printable ASCII, 0x20 to 0x7E. */
    data object PrintableAscii : CharsetRule {
        override val description = "Printable ASCII (space to ~)"
        override fun allows(codePoint: Int) = codePoint in 0x20..0x7E
    }

    /** Full ASCII including control characters. */
    data object FullAscii : CharsetRule {
        override val description = "Full ASCII 0-127, including control characters"
        override fun allows(codePoint: Int) = codePoint in 0x00..0x7F
    }

    /** Any Unicode code point, reachable via UTF-8 with an appropriate ECI. */
    data object Unicode : CharsetRule {
        override val description = "Any Unicode character (via ECI)"
        override fun allows(codePoint: Int) = codePoint in 0x00..0x10FFFF
    }

    /** An explicit set of permitted characters. */
    data class Chars(
        val allowed: String,
        override val description: String,
    ) : CharsetRule {
        private val set: Set<Int> = allowed.codePoints().toArray().toSet()
        override fun allows(codePoint: Int) = codePoint in set
    }

    /** One or more code point ranges. */
    data class Ranges(
        val ranges: List<IntRange>,
        override val description: String,
    ) : CharsetRule {
        override fun allows(codePoint: Int) = ranges.any { codePoint in it }
    }

    /** Returns the code points in [text] that this rule rejects. */
    fun rejectedCodePoints(text: String): List<Int> =
        text.codePoints().toArray().filterNot { allows(it) }.distinct()
}
