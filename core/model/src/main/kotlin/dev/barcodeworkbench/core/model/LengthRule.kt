package dev.barcodeworkbench.core.model

/**
 * Permitted payload lengths for a symbology, measured in characters of input
 * (not in encoded modules).
 *
 * These are advisory front-stops that give an immediate, specific message as the
 * user types. They never replace the trial encode, which is the only authority
 * on whether a payload is actually encodable -- check digits and mode-dependent
 * capacity cannot be expressed as a length range.
 */
sealed interface LengthRule {

    val description: String

    fun check(length: Int): LengthVerdict

    data class Range(val min: Int, val max: Int) : LengthRule {
        override val description = "$min to $max characters"
        override fun check(length: Int) = when {
            length < min -> LengthVerdict.TooShort(min)
            length > max -> LengthVerdict.TooLong(max)
            else -> LengthVerdict.Ok
        }
    }

    data class UpTo(val max: Int) : LengthRule {
        override val description = "up to $max characters"
        override fun check(length: Int) = when {
            length == 0 -> LengthVerdict.TooShort(1)
            length > max -> LengthVerdict.TooLong(max)
            else -> LengthVerdict.Ok
        }
    }

    /** A fixed set of acceptable lengths, as used by EAN/UPC. */
    data class Exact(val lengths: Set<Int>) : LengthRule {
        override val description = "exactly ${lengths.sorted().joinToString(" or ")} characters"
        override fun check(length: Int) = if (length in lengths) {
            LengthVerdict.Ok
        } else {
            LengthVerdict.WrongLength(lengths)
        }
    }

    /** Interleaved 2 of 5 encodes digit pairs, so the length must be even. */
    data class EvenPairs(val minPairs: Int, val maxPairs: Int) : LengthRule {
        override val description =
            "an even number of digits, ${minPairs * 2} to ${maxPairs * 2}"

        override fun check(length: Int) = when {
            length % 2 != 0 -> LengthVerdict.MustBeEven
            length < minPairs * 2 -> LengthVerdict.TooShort(minPairs * 2)
            length > maxPairs * 2 -> LengthVerdict.TooLong(maxPairs * 2)
            else -> LengthVerdict.Ok
        }
    }
}

sealed interface LengthVerdict {
    data object Ok : LengthVerdict
    data class TooShort(val minimum: Int) : LengthVerdict
    data class TooLong(val maximum: Int) : LengthVerdict
    data class WrongLength(val permitted: Set<Int>) : LengthVerdict
    data object MustBeEven : LengthVerdict

    val isOk: Boolean get() = this is Ok
}
