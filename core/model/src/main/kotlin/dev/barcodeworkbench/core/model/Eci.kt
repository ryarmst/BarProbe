package dev.barcodeworkbench.core.model

/**
 * An Extended Channel Interpretation value.
 *
 * ECI tells a decoder how to interpret the bytes that follow. Without it, a payload
 * containing non-ASCII text is ambiguous: the same bytes mean different characters
 * under different encodings, and the reader has to guess.
 */
data class EciOption(
    val value: Int,
    val label: String,
    val description: String? = null,
)

/**
 * The commonly used ECI assignments.
 *
 * Deliberately a short list rather than the full AIM register. These are the ones that
 * come up in practice; anything more exotic can be entered directly, and the encoder
 * accepts any numeric value.
 */
object EciRegistry {

    val common: List<EciOption> = listOf(
        EciOption(3, "ISO-8859-1", "Latin-1, the usual default for western European text"),
        EciOption(4, "ISO-8859-2", "Latin-2, central European"),
        EciOption(5, "ISO-8859-3", "Latin-3, southern European"),
        EciOption(6, "ISO-8859-4", "Latin-4, northern European"),
        EciOption(7, "ISO-8859-5", "Cyrillic"),
        EciOption(9, "ISO-8859-7", "Greek"),
        EciOption(20, "Shift JIS", "Japanese"),
        EciOption(26, "UTF-8", "Unicode; the safe choice for mixed or unknown scripts"),
        EciOption(27, "US-ASCII", "7-bit ASCII"),
        EciOption(28, "Big5", "Traditional Chinese"),
        EciOption(29, "GB2312", "Simplified Chinese"),
        EciOption(30, "EUC-KR", "Korean"),
        EciOption(899, "8-bit binary", "Uninterpreted bytes"),
    )

    fun find(value: Int): EciOption? = common.firstOrNull { it.value == value }

    /** Label for display, falling back to the bare number for unlisted values. */
    fun labelFor(value: Int): String = find(value)?.let { "$value · ${it.label}" } ?: "ECI $value"
}
