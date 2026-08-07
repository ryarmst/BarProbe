package dev.barcodeworkbench.core.model

enum class PaletteCategory(val title: String, val description: String) {
    CONTROL(
        "Control characters",
        "ASCII 0-31 and 127. GS is the one most often needed, for GS1 field separation.",
    ),
    DIRECTIVE(
        "Function characters",
        "Codeset switches and FNC markers. Available on Code 128 and GS1-128 only.",
    ),
    GS1_AI(
        "GS1 Application Identifiers",
        "Common AIs with their field rules.",
    ),
    HIGH_BYTE(
        "Extended bytes",
        "128-255, reachable in binary mode or through a matching ECI.",
    ),
}

/**
 * Something the composer can insert at the cursor.
 *
 * [insertText] is escape source rather than a raw character, so the payload field
 * stays a plain editable string while still representing bytes no Android keyboard
 * can produce.
 */
data class PaletteItem(
    /** Short label for the key cap, e.g. "GS". */
    val label: String,
    /** Full name, e.g. "Group Separator". */
    val name: String,
    val insertText: String,
    val category: PaletteCategory,
    /** The byte this yields, where it maps to exactly one. */
    val byteValue: Int? = null,
    /** Extra guidance shown on long press. */
    val note: String? = null,
)

/**
 * The insert palette.
 *
 * This exists because the requirement to encode every character a symbology
 * supports is unreachable through a soft keyboard: a Group Separator, an FNC1 or
 * byte 0x8F simply cannot be typed. Rather than expecting users to memorise
 * escape syntax, every such value gets a labelled key.
 */
object CharacterPalette {

    /** ASCII control characters with their standard mnemonics and names. */
    val controlCharacters: List<PaletteItem> = listOf(
        control(0x00, "NUL", "Null"),
        control(0x01, "SOH", "Start of Heading"),
        control(0x02, "STX", "Start of Text"),
        control(0x03, "ETX", "End of Text"),
        control(0x04, "EOT", "End of Transmission"),
        control(0x05, "ENQ", "Enquiry"),
        control(0x06, "ACK", "Acknowledge"),
        control(0x07, "BEL", "Bell"),
        control(0x08, "BS", "Backspace"),
        control(0x09, "HT", "Horizontal Tab"),
        control(0x0A, "LF", "Line Feed"),
        control(0x0B, "VT", "Vertical Tab"),
        control(0x0C, "FF", "Form Feed"),
        control(0x0D, "CR", "Carriage Return"),
        control(0x0E, "SO", "Shift Out"),
        control(0x0F, "SI", "Shift In"),
        control(0x10, "DLE", "Data Link Escape"),
        control(0x11, "DC1", "Device Control 1 (XON)"),
        control(0x12, "DC2", "Device Control 2"),
        control(0x13, "DC3", "Device Control 3 (XOFF)"),
        control(0x14, "DC4", "Device Control 4"),
        control(0x15, "NAK", "Negative Acknowledge"),
        control(0x16, "SYN", "Synchronous Idle"),
        control(0x17, "ETB", "End of Transmission Block"),
        control(0x18, "CAN", "Cancel"),
        control(0x19, "EM", "End of Medium"),
        control(0x1A, "SUB", "Substitute"),
        control(0x1B, "ESC", "Escape"),
        control(0x1C, "FS", "File Separator"),
        control(
            0x1D,
            "GS",
            "Group Separator",
            note = "Separates variable-length GS1 element strings. " +
                "The most frequently needed control character.",
        ),
        control(0x1E, "RS", "Record Separator"),
        control(0x1F, "US", "Unit Separator"),
        control(0x7F, "DEL", "Delete"),
    )

    /** Codeset and function directives. Only valid on the Code 128 family. */
    val directives: List<PaletteItem> = Directive.entries.map { directive ->
        PaletteItem(
            label = directive.label,
            name = directive.description,
            insertText = directive.escape,
            category = PaletteCategory.DIRECTIVE,
            byteValue = null,
            note = when (directive) {
                Directive.CODESET_C ->
                    "Encodes digits two per symbol character, which shortens numeric payloads."
                Directive.FNC1 ->
                    "Equivalent to what GS1 mode inserts automatically before an AI."
                else -> null
            },
        )
    }

    /**
     * Frequently used GS1 Application Identifiers.
     *
     * Bracketed form is what libzint expects; it converts brackets to FNC1
     * separators itself. Fixed-length AIs need no separator after them, which is
     * the distinction the notes call out because getting it wrong silently
     * produces a technically valid but semantically wrong barcode.
     */
    val gs1ApplicationIdentifiers: List<PaletteItem> = listOf(
        ai("00", "SSCC (serial shipping container code)", "18 digits, fixed length"),
        ai("01", "GTIN (global trade item number)", "14 digits, fixed length"),
        ai("02", "GTIN of contained trade items", "14 digits, fixed length"),
        ai("10", "Batch or lot number", "up to 20 characters, variable length"),
        ai("11", "Production date (YYMMDD)", "6 digits, fixed length"),
        ai("13", "Packaging date (YYMMDD)", "6 digits, fixed length"),
        ai("15", "Best before date (YYMMDD)", "6 digits, fixed length"),
        ai("17", "Expiration date (YYMMDD)", "6 digits, fixed length"),
        ai("20", "Product variant", "2 digits, fixed length"),
        ai("21", "Serial number", "up to 20 characters, variable length"),
        ai("30", "Variable count of items", "up to 8 digits, variable length"),
        ai("37", "Count of trade items", "up to 8 digits, variable length"),
        ai("400", "Customer purchase order number", "up to 30 characters, variable length"),
        ai("410", "Ship to / deliver to GLN", "13 digits, fixed length"),
        ai("414", "GLN of a physical location", "13 digits, fixed length"),
        ai("420", "Ship to / deliver to postal code", "up to 20 characters, variable length"),
        ai("3103", "Net weight, kilograms (3 decimals)", "6 digits, fixed length"),
    )

    /** 128-255, presented as hex keys. */
    val highBytes: List<PaletteItem> = (0x80..0xFF).map { value ->
        PaletteItem(
            label = "%02X".format(value),
            name = "Byte 0x%02X (%d)".format(value, value),
            insertText = "\\x%02X".format(value),
            category = PaletteCategory.HIGH_BYTE,
            byteValue = value,
        )
    }

    /**
     * Palette contents for a symbology.
     *
     * Categories the symbology cannot use are omitted rather than shown disabled:
     * offering an FNC1 key on a QR code would imply it does something.
     */
    fun categoriesFor(spec: SymbologySpec): List<PaletteCategory> = buildList {
        add(PaletteCategory.CONTROL)
        if (spec.supportsCodesetEscapes) add(PaletteCategory.DIRECTIVE)
        if (spec.supportsGs1) add(PaletteCategory.GS1_AI)
        if (spec.charsetRule.allows(0x80) || spec.charsetRule == CharsetRule.AnyByte) {
            add(PaletteCategory.HIGH_BYTE)
        }
    }

    fun itemsFor(category: PaletteCategory): List<PaletteItem> = when (category) {
        PaletteCategory.CONTROL -> controlCharacters
        PaletteCategory.DIRECTIVE -> directives
        PaletteCategory.GS1_AI -> gs1ApplicationIdentifiers
        PaletteCategory.HIGH_BYTE -> highBytes
    }

    /**
     * Whether [item] can actually be encoded by [spec], so the UI can dim keys
     * that would make the payload invalid.
     */
    fun isUsable(item: PaletteItem, spec: SymbologySpec): Boolean = when (item.category) {
        PaletteCategory.DIRECTIVE -> spec.supportsCodesetEscapes
        PaletteCategory.GS1_AI -> spec.supportsGs1
        else -> item.byteValue?.let { spec.charsetRule.allows(it) } ?: true
    }

    private fun control(value: Int, mnemonic: String, name: String, note: String? = null) =
        PaletteItem(
            label = mnemonic,
            name = name,
            insertText = "\\x%02X".format(value),
            category = PaletteCategory.CONTROL,
            byteValue = value,
            note = note,
        )

    private fun ai(code: String, name: String, rule: String) = PaletteItem(
        label = "($code)",
        name = name,
        insertText = "[$code]",
        category = PaletteCategory.GS1_AI,
        byteValue = null,
        note = rule,
    )
}
