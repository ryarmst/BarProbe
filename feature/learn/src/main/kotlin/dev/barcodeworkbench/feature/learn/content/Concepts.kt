package dev.barcodeworkbench.feature.learn.content

/**
 * The long-form reference.
 *
 * Written for someone who has to make a barcode work, not for someone studying the
 * standards. Where the app's own numbers are advisory rather than normative, the text
 * says so, because a tutorial that quietly presents an approximation as a spec figure
 * is worse than one that admits the limit.
 */
object Concepts {

    val payloadsAreBytes = Article(
        id = "payloads-are-bytes",
        title = "Payloads are bytes, not text",
        summary = "The single idea that explains most barcode surprises.",
        blocks = listOf(
            Block.Paragraph(
                "A barcode stores a sequence of bytes. Text is one interpretation of " +
                    "those bytes, not what is actually encoded. Most confusing barcode " +
                    "behaviour comes from somewhere in the chain treating the payload as " +
                    "a string when it is not.",
            ),
            Block.Paragraph(
                "This matters in practice because plenty of legitimate payloads are not " +
                    "text at all. A GS1 label separates its variable-length fields with " +
                    "a Group Separator, byte 0x1D, which has no printable form. Some " +
                    "industrial labels carry raw binary. A payload can even contain a " +
                    "NUL byte, which will silently truncate anything that treats it as a " +
                    "C string.",
            ),
            Block.Heading("How this app handles it"),
            Block.Bullets(
                listOf(
                    "Payloads are stored and compared as bytes everywhere, including in " +
                        "the database and in backup files.",
                    "The byte inspector shows exactly what will be encoded, in hex, " +
                        "before you encode it.",
                    "Scan results offer a hex view alongside text, because a decoded " +
                        "symbol is bytes too.",
                ),
            ),
            Block.Example(
                input = "AB\\x1DCD",
                result = "41 42 1D 43 44",
                comment = "Five bytes. The Group Separator is one byte, not four characters.",
            ),
            Block.Note(
                "If a scanner appears to drop part of a payload, check the hex view " +
                    "first. A truncation at a NUL byte, or a missing Group Separator, is " +
                    "far more common than a genuine decode failure.",
            ),
        ),
    )

    val symbologies = Article(
        id = "symbologies",
        title = "Symbologies: choosing a barcode type",
        summary = "What linear and matrix codes are for, and how to pick one.",
        blocks = listOf(
            Block.Paragraph(
                "A symbology is a set of rules for turning data into a pattern. Each one " +
                    "fixes which characters it can carry, how much data fits, and how " +
                    "errors are handled. Choosing the wrong one is the most common reason " +
                    "a barcode cannot be produced at all.",
            ),
            Block.Heading("Linear, or 1D"),
            Block.Paragraph(
                "Data is encoded in the widths of bars and spaces, and read along a " +
                    "single line across the symbol. Linear codes are compact, print " +
                    "reliably at small sizes, and are what almost all retail and " +
                    "warehouse scanning uses.",
            ),
            Block.Paragraph(
                "Because a linear symbol is read on one scan line, a smudge, fold or " +
                    "motion blur can produce a wrong-but-valid reading from a single " +
                    "frame. That is why this app requires a linear result to be seen " +
                    "twice consistently before accepting it.",
            ),
            Block.Heading("Matrix, or 2D"),
            Block.Paragraph(
                "Data is encoded across an area. Matrix codes hold far more, carry their " +
                    "own error correction, and can usually be read even when partly " +
                    "damaged or obscured. They need a camera or imager rather than a " +
                    "laser scanner.",
            ),
            Block.Heading("Practical guidance"),
            Block.Definitions(
                listOf(
                    "Retail products" to
                        "EAN-13, EAN-8, UPC-A or UPC-E. Fixed-length numeric, with a " +
                        "check digit. Not a free choice: the number identifies the product.",
                    "General purpose text or mixed data" to
                        "Code 128 for linear, QR Code or Data Matrix for matrix. All " +
                        "three handle the full ASCII range.",
                    "Supply chain with structured fields" to
                        "GS1-128 or GS1 DataBar, which carry Application Identifiers.",
                    "Small parts and direct marking" to
                        "Data Matrix. It stays readable at very small sizes and is the " +
                        "usual choice for etched or laser-marked parts.",
                    "Documents and logistics labels" to
                        "PDF417. Wide and flat, high capacity, used on shipping labels " +
                        "and identity documents.",
                ),
            ),
            Block.Note(
                "The symbology reference in this app lists every supported format with " +
                    "its character set, length rules and capabilities, taken directly " +
                    "from the encoder's own definitions.",
            ),
        ),
    )

    val characterEncoding = Article(
        id = "character-encoding",
        title = "Character encodings and ECI",
        summary = "Why non-English text sometimes decodes as nonsense, and how to fix it.",
        blocks = listOf(
            Block.Paragraph(
                "If a payload is bytes, then any text in it must have been converted to " +
                    "bytes by some encoding. The reader has to apply the same encoding to " +
                    "get the text back. When the two disagree, the symbol still scans " +
                    "perfectly and returns the wrong characters.",
            ),
            Block.Paragraph(
                "For plain unaccented English this never comes up, because every common " +
                    "encoding agrees on the ASCII range. It appears as soon as the " +
                    "content includes an accent, a currency symbol, or any non-Latin " +
                    "script.",
            ),
            Block.Heading("What ECI does"),
            Block.Paragraph(
                "Extended Channel Interpretation is a marker placed inside the symbol " +
                    "that names the encoding of the data that follows. A reader that " +
                    "honours it knows exactly how to interpret the bytes instead of " +
                    "guessing.",
            ),
            Block.Example(
                input = "café",
                result = "63 61 66 C3 A9",
                comment = "The e-acute is two bytes in UTF-8. Set ECI 26 so the reader " +
                    "knows that, rather than guessing and returning \"cafÃ©\".",
            ),
            Block.Heading("Which to use"),
            Block.Definitions(
                listOf(
                    "ECI 26, UTF-8" to
                        "The safe default for anything modern, and the only sensible " +
                        "choice for mixed scripts.",
                    "ECI 3, ISO-8859-1" to
                        "Latin-1. Common in older systems and western European data.",
                    "ECI 20, Shift JIS" to "Japanese.",
                    "ECI 899" to
                        "Explicitly uninterpreted binary, for data that is not text.",
                ),
            ),
            Block.Note(
                "Not every reader acts on ECI, and some ignore it entirely. If you " +
                    "control both ends, test the round trip before committing to a large " +
                    "print run. Leaving ECI unset lets the encoder decide, which is " +
                    "right for pure ASCII and a gamble for anything else.",
            ),
            Block.Note(
                "ECI is carried in the symbol's own encoding modes, which linear " +
                    "symbologies do not have. It is available on matrix formats only, and " +
                    "the app hides the control when it does not apply.",
            ),
        ),
    )

    val gs1 = Article(
        id = "gs1",
        title = "GS1, Application Identifiers and FNC1",
        summary = "How supply-chain barcodes pack several fields into one symbol.",
        blocks = listOf(
            Block.Paragraph(
                "A GS1 barcode carries several labelled fields in a single symbol. Each " +
                    "field starts with an Application Identifier, a two- to four-digit " +
                    "code saying what the value means: 01 is a GTIN, 10 is a batch " +
                    "number, 17 an expiry date, and so on.",
            ),
            Block.Heading("Writing GS1 data"),
            Block.Paragraph(
                "In this app you write AIs in brackets and the encoder does the rest. " +
                    "The brackets are notation, not data; they never appear in the " +
                    "encoded bytes.",
            ),
            Block.Example(
                input = "[01]09501101530003[10]LOT123",
                result = "GTIN 09501101530003, batch LOT123",
                comment = "Two fields in one symbol.",
            ),
            Block.Heading("Fixed and variable length"),
            Block.Paragraph(
                "Some AIs have a fixed length defined by the standard, so a reader knows " +
                    "where the field ends. Others are variable and must be terminated " +
                    "explicitly, which is done with a Group Separator, byte 0x1D.",
            ),
            Block.Paragraph(
                "This is the single most common source of GS1 problems. If a variable " +
                    "field is not separated, everything after it is absorbed into that " +
                    "field, and the symbol scans cleanly while carrying wrong data.",
            ),
            Block.Heading("FNC1"),
            Block.Paragraph(
                "FNC1 is a special character with two jobs. In the first position it " +
                    "marks the symbol as GS1 data. Later on it acts as the field " +
                    "separator, and is what the Group Separator byte represents when the " +
                    "data is transmitted.",
            ),
            Block.Note(
                "The self-test pack includes a Code 128 containing a Group Separator. " +
                    "Scanning it tells you whether a given scanner transmits GS or " +
                    "silently drops it, which is worth knowing before blaming your " +
                    "parsing code.",
            ),
        ),
    )

    val reliability = Article(
        id = "reliability",
        title = "Making barcodes that actually scan",
        summary = "Quiet zones, module size, error correction and contrast.",
        blocks = listOf(
            Block.Heading("Quiet zone"),
            Block.Paragraph(
                "The blank margin around a symbol is part of the symbol. Without it a " +
                    "reader cannot tell where the code begins. Cropping tightly to the " +
                    "bars is one of the most common reasons a printed barcode fails.",
            ),
            Block.Heading("Module size"),
            Block.Paragraph(
                "A module is the narrowest bar or the smallest square. Everything scales " +
                    "from it. Export in this app is specified in pixels per module rather " +
                    "than an overall size, because a barcode only scans reliably when " +
                    "modules land on whole pixels; scaling an image to a target width " +
                    "produces fractional modules and blurred edges.",
            ),
            Block.Heading("Error correction"),
            Block.Paragraph(
                "Matrix codes reserve part of their capacity for recovery data, so a " +
                    "damaged or partly covered symbol still reads. Higher correction " +
                    "means a larger symbol for the same payload. QR Code offers four " +
                    "levels; the lowest is usually fine for a clean screen or printed " +
                    "label, and higher levels earn their size on surfaces that get worn " +
                    "or dirty.",
            ),
            Block.Heading("Contrast"),
            Block.Paragraph(
                "Readers threshold the image, so they need genuine dark-on-light " +
                    "contrast. Inverted symbols, low-contrast colour pairs and glossy " +
                    "laminates all reduce the margin. The full-screen viewer here forces " +
                    "maximum brightness and a true white background for exactly this " +
                    "reason, since reading a code off a phone screen is otherwise " +
                    "needlessly marginal.",
            ),
            Block.Note(
                "Avoid lossy formats. JPEG compression artefacts fall on the " +
                    "high-contrast edges a decoder depends on. Use PNG for raster or SVG " +
                    "for anything that will be resized or printed.",
            ),
        ),
    )

    val checkDigits = Article(
        id = "check-digits",
        title = "Check digits and why a valid-looking number is rejected",
        summary = "Retail codes carry a computed digit that must be correct.",
        blocks = listOf(
            Block.Paragraph(
                "Several symbologies append a digit derived arithmetically from the rest " +
                    "of the value. A reader recomputes it and rejects the scan if it does " +
                    "not match, which catches most misreads.",
            ),
            Block.Paragraph(
                "This is why a thirteen-digit number can be refused by an EAN-13 " +
                    "encoder even though the length and character set look right. The " +
                    "final digit is not free.",
            ),
            Block.Heading("The practical rule"),
            Block.Bullets(
                listOf(
                    "Supply twelve digits to EAN-13 and the check digit is computed for " +
                        "you. This is almost always what you want.",
                    "Supply thirteen and the encoder validates the one you provided.",
                    "The same pattern applies to EAN-8, UPC-A and UPC-E.",
                ),
            ),
            Block.Note(
                "Length and character rules can be checked as you type, but a check " +
                    "digit cannot be judged from the shape of the input. This app runs a " +
                    "real trial encode before allowing a save, so the encoder itself has " +
                    "the final word rather than an approximation of its rules.",
            ),
        ),
    )

    /** Ordered for reading, most foundational first. */
    val all: List<Article> = listOf(
        payloadsAreBytes,
        symbologies,
        characterEncoding,
        gs1,
        checkDigits,
        reliability,
    )

    fun byId(id: String): Article? = all.firstOrNull { it.id == id }
}
