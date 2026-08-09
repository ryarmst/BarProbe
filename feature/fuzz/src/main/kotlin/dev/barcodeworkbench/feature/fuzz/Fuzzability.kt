package dev.barcodeworkbench.feature.fuzz

import dev.barcodeworkbench.core.model.CharsetRule
import dev.barcodeworkbench.core.model.SymbologySpec

/**
 * How well a symbology suits byte-level fuzzing.
 *
 * radamsa mutates bytes freely, but a symbology only encodes the bytes its charset
 * allows. For the matrix formats that take any byte, almost every mutation encodes
 * and fuzzing is productive. For fixed numeric formats, essentially nothing radamsa
 * produces is a valid payload, so the user would watch the retry loop skip forever.
 * Rather than let that happen silently, the UI states the expectation up front.
 */
enum class Fuzzability {
    /** Any byte encodes; mutations rarely skip. The matrix formats. */
    GOOD,

    /** ASCII-range only; a fair share of mutations skip but plenty get through. */
    LIMITED,

    /** Fixed numeric or tightly restricted; byte fuzzing almost never encodes. */
    POOR,
    ;

    companion object {
        fun of(spec: SymbologySpec): Fuzzability = when (spec.charsetRule) {
            is CharsetRule.AnyByte, is CharsetRule.Unicode -> GOOD
            is CharsetRule.FullAscii, is CharsetRule.PrintableAscii -> LIMITED
            // Numeric, Chars, Ranges: a restricted alphabet, usually paired with a
            // fixed length and a check digit, which random bytes will not satisfy.
            else -> POOR
        }
    }

    val hint: String
        get() = when (this) {
            GOOD -> "Any byte encodes here, so almost every mutation produces a symbol."
            LIMITED ->
                "Only ASCII encodes here, so a good share of mutations will be skipped. " +
                    "Matrix formats such as QR or Data Matrix fuzz more densely."
            POOR ->
                "This format takes a fixed, restricted payload, so random mutations " +
                    "almost never encode. Pick a matrix format such as QR, Data Matrix " +
                    "or PDF417 to fuzz effectively."
        }
}
