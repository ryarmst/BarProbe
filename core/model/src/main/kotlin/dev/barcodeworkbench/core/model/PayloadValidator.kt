package dev.barcodeworkbench.core.model

/** A validation problem, ordered so the most actionable message can be shown first. */
sealed interface ValidationIssue {

    val message: String

    /** Escape source could not be parsed. */
    data class BadEscape(val error: EscapeError) : ValidationIssue {
        override val message: String get() = error.message
    }

    /** Characters outside what the symbology can encode. */
    data class UnsupportedCharacters(
        val codePoints: List<Int>,
        val rule: CharsetRule,
    ) : ValidationIssue {
        override val message: String
            get() {
                val rendered = codePoints.take(5).joinToString(", ") { describeCodePoint(it) }
                val more = if (codePoints.size > 5) " and ${codePoints.size - 5} more" else ""
                return "Cannot encode $rendered$more. Allowed: ${rule.description}"
            }
    }

    data class WrongLength(val verdict: LengthVerdict, val rule: LengthRule) : ValidationIssue {
        override val message: String
            get() = when (verdict) {
                is LengthVerdict.TooShort -> "Too short, needs at least ${verdict.minimum}"
                is LengthVerdict.TooLong -> "Too long, maximum is ${verdict.maximum}"
                is LengthVerdict.WrongLength ->
                    "Wrong length, needs ${rule.description}"
                LengthVerdict.MustBeEven -> "Needs an even number of digits"
                LengthVerdict.Ok -> "Valid"
            }
    }

    /** Directives used on a symbology that does not support them. */
    data class UnsupportedDirective(val directive: Directive) : ValidationIssue {
        override val message: String
            get() = "${directive.label} is only available on Code 128 and GS1-128"
    }

    data object Empty : ValidationIssue {
        override val message: String get() = "Enter something to encode"
    }
}

private fun describeCodePoint(codePoint: Int): String = when {
    codePoint in 0x20..0x7E -> "'${codePoint.toChar()}'"
    codePoint < 0x20 || codePoint == 0x7F -> "0x%02X".format(codePoint)
    else -> "U+%04X".format(codePoint)
}

data class ValidationResult(
    val issues: List<ValidationIssue>,
    /** Bytes that would be handed to the encoder, for the inspector. */
    val effectiveBytes: ByteArray,
    val directives: List<Directive>,
) {
    val isValid: Boolean get() = issues.isEmpty()
    val firstMessage: String? get() = issues.firstOrNull()?.message

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValidationResult) return false
        return issues == other.issues &&
            effectiveBytes.contentEquals(other.effectiveBytes) &&
            directives == other.directives
    }

    override fun hashCode(): Int {
        var result = issues.hashCode()
        result = 31 * result + effectiveBytes.contentHashCode()
        result = 31 * result + directives.hashCode()
        return result
    }
}

/**
 * Front-line payload validation.
 *
 * This covers the two checks that can be made instantly while the user types.
 * It deliberately does not attempt to be authoritative: only a trial encode can
 * settle check digits and mode-dependent capacity, so the generator always runs
 * one before allowing a save. The division exists because a fast, specific
 * message on every keystroke is worth more than a slow, complete one.
 */
object PayloadValidator {

    fun validate(
        spec: SymbologySpec,
        escapeSource: String,
        mode: InputMode,
    ): ValidationResult {
        val parsed = EscapeCodec.parse(escapeSource)
        val bytes = parsed.dataBytes()
        val issues = mutableListOf<ValidationIssue>()

        parsed.errors.forEach { issues += ValidationIssue.BadEscape(it) }

        parsed.instructions
            .filterNot { spec.supportsCodesetEscapes }
            .distinct()
            .forEach { issues += ValidationIssue.UnsupportedDirective(it) }

        if (escapeSource.isEmpty()) {
            issues += ValidationIssue.Empty
            return ValidationResult(issues, bytes, parsed.instructions)
        }

        // Escape errors make the byte stream unreliable, so charset and length
        // checks would produce misleading messages on top of the real problem.
        if (parsed.errors.isEmpty()) {
            issues += charsetIssues(spec, bytes, mode)
            issues += lengthIssues(spec, escapeSource, bytes, mode)
        }

        return ValidationResult(issues, bytes, parsed.instructions)
    }

    private fun charsetIssues(
        spec: SymbologySpec,
        bytes: ByteArray,
        mode: InputMode,
    ): List<ValidationIssue> {
        val rejected = when (mode) {
            // In binary mode each byte stands alone.
            InputMode.BINARY -> bytes
                .map { it.toInt() and 0xFF }
                .filterNot { spec.charsetRule.allows(it) }
                .distinct()

            // Otherwise the bytes are UTF-8, so they must be decoded back to code
            // points before being checked -- testing raw bytes would wrongly reject
            // any multi-byte character.
            InputMode.UNICODE, InputMode.GS1 -> {
                val text = String(bytes, Charsets.UTF_8)
                spec.charsetRule.rejectedCodePoints(text)
            }
        }
        return if (rejected.isEmpty()) {
            emptyList()
        } else {
            listOf(ValidationIssue.UnsupportedCharacters(rejected, spec.charsetRule))
        }
    }

    private fun lengthIssues(
        spec: SymbologySpec,
        escapeSource: String,
        bytes: ByteArray,
        mode: InputMode,
    ): List<ValidationIssue> {
        // GS1 bracketed AIs are rewritten by the encoder, so the source length
        // here does not correspond to what gets encoded.
        if (spec.supportsGs1 && escapeSource.contains('[')) return emptyList()

        val length = when (mode) {
            InputMode.BINARY -> bytes.size
            InputMode.UNICODE, InputMode.GS1 ->
                String(bytes, Charsets.UTF_8).codePointCount(0, String(bytes, Charsets.UTF_8).length)
        }

        val verdict = spec.lengthRule.check(length)
        return if (verdict.isOk) {
            emptyList()
        } else {
            listOf(ValidationIssue.WrongLength(verdict, spec.lengthRule))
        }
    }
}
