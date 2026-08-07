package dev.barcodeworkbench.core.model

/** How the encoder should interpret the payload bytes. */
enum class InputMode {
    /** Raw 8-bit data, no interpretation. */
    BINARY,

    /** UTF-8 text. */
    UNICODE,

    /** GS1 element strings with Application Identifiers. */
    GS1,
}

/**
 * A payload held as bytes rather than as a String.
 *
 * This is deliberate. Control characters, embedded NUL and arbitrary high bytes
 * are all legitimate barcode content, and a String-based representation silently
 * corrupts or truncates them -- the Phase 1 spike confirmed a NUL byte is lost
 * through any NUL-terminated API.
 */
class Payload(
    bytes: ByteArray,
    val mode: InputMode = InputMode.UNICODE,
    /** Extended Channel Interpretation, or null to leave it unset/automatic. */
    val eci: Int? = null,
    /**
     * Whether the bytes contain escape sequences for the encoder to expand
     * (hex byte escapes, Unicode escapes, codeset switches, FNC1).
     */
    val escapesEnabled: Boolean = false,
) {
    private val _bytes: ByteArray = bytes.copyOf()

    /** A defensive copy; the payload itself is immutable. */
    val bytes: ByteArray get() = _bytes.copyOf()

    val size: Int get() = _bytes.size

    val isEmpty: Boolean get() = _bytes.isEmpty()

    /** Best-effort text rendering. Lossy for binary content by definition. */
    fun asText(): String = String(_bytes, Charsets.UTF_8)

    /** Space-separated uppercase hex, for the byte inspector. */
    fun asHex(): String = _bytes.joinToString(" ") { "%02X".format(it) }

    /**
     * Renders bytes as printable text with non-printable values shown as hex
     * escapes, which is how the composer displays a payload in raw mode.
     */
    fun asEscapedAscii(): String = buildString {
        for (b in _bytes) {
            val v = b.toInt() and 0xFF
            when {
                v == 0x5C -> append("\\\\")
                v in 0x20..0x7E -> append(v.toChar())
                else -> append("\\x%02X".format(v))
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Payload) return false
        return _bytes.contentEquals(other._bytes) &&
            mode == other.mode &&
            eci == other.eci &&
            escapesEnabled == other.escapesEnabled
    }

    override fun hashCode(): Int {
        var result = _bytes.contentHashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + (eci ?: 0)
        result = 31 * result + escapesEnabled.hashCode()
        return result
    }

    override fun toString(): String =
        "Payload(size=$size, mode=$mode, eci=$eci, escapes=$escapesEnabled)"

    companion object {
        fun ofText(text: String, mode: InputMode = InputMode.UNICODE, eci: Int? = null): Payload =
            Payload(text.toByteArray(Charsets.UTF_8), mode, eci)

        /** A payload whose escape sequences the encoder should expand. */
        fun ofEscaped(source: String, mode: InputMode = InputMode.UNICODE, eci: Int? = null): Payload =
            Payload(source.toByteArray(Charsets.UTF_8), mode, eci, escapesEnabled = true)
    }
}
