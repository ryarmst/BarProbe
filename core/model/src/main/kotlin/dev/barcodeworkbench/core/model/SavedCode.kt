package dev.barcodeworkbench.core.model

/** Where an entry came from. Purely informational, but useful when auditing a library. */
enum class CodeSource { GENERATED, SCANNED, IMPORTED, CONFIG_PACK }

/** A user-created collection of codes. */
data class CodeLibrary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int = 0,
    /** Populated by list queries; zero when not requested. */
    val entryCount: Int = 0,
)

/**
 * One saved code.
 *
 * The payload keeps its input mode and escape flag alongside the bytes, so an entry
 * re-renders exactly as authored and stays editable. Storing only the rendered
 * image, or only the expanded bytes, would make both impossible.
 */
data class SavedCode(
    val id: Long,
    val libraryId: Long,
    val symbologyId: SymbologyId,
    val payload: Payload,
    val label: String? = null,
    val notes: String? = null,
    val tags: Set<String> = emptySet(),
    val source: CodeSource = CodeSource.GENERATED,
    val createdAt: Long = 0,
) {
    val spec: SymbologySpec? get() = SymbologyRegistry.find(symbologyId)

    /** Best label for a list row: explicit label, else a payload preview. */
    fun displayTitle(): String = label?.takeIf { it.isNotBlank() } ?: payloadPreview()

    /**
     * A short, safe rendering of the payload.
     *
     * Uses the escaped form rather than raw text, because a payload containing
     * control characters would otherwise render as invisible gaps or mangle the row.
     */
    fun payloadPreview(maxChars: Int = 48): String {
        val escaped = payload.asEscapedAscii()
        return if (escaped.length <= maxChars) escaped else escaped.take(maxChars) + "…"
    }
}

/** How a library listing is ordered. */
enum class CodeSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    LABEL_ASCENDING,
    SYMBOLOGY,
}

/** Narrows a library listing. */
data class CodeFilter(
    val query: String = "",
    val symbologies: Set<SymbologyId> = emptySet(),
    val sources: Set<CodeSource> = emptySet(),
    val tags: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = query.isBlank() && symbologies.isEmpty() && sources.isEmpty() && tags.isEmpty()

    /**
     * Applies the filter in memory.
     *
     * Text matching runs over the label, notes and the escaped payload. The escaped
     * form is used deliberately so a search for `\x1D` finds codes containing a
     * Group Separator, which no plain-text search could locate.
     */
    fun matches(code: SavedCode): Boolean {
        if (symbologies.isNotEmpty() && code.symbologyId !in symbologies) return false
        if (sources.isNotEmpty() && code.source !in sources) return false
        if (tags.isNotEmpty() && tags.none { it in code.tags }) return false
        if (query.isBlank()) return true

        val needle = query.trim()
        return code.label?.contains(needle, ignoreCase = true) == true ||
            code.notes?.contains(needle, ignoreCase = true) == true ||
            code.payload.asEscapedAscii().contains(needle, ignoreCase = true) ||
            code.tags.any { it.contains(needle, ignoreCase = true) }
    }
}

/** Sorts a listing, keeping unlabelled entries after labelled ones. */
fun List<SavedCode>.sortedBy(order: CodeSortOrder): List<SavedCode> = when (order) {
    CodeSortOrder.NEWEST_FIRST -> sortedByDescending { it.createdAt }
    CodeSortOrder.OLDEST_FIRST -> sortedBy { it.createdAt }
    CodeSortOrder.LABEL_ASCENDING -> sortedWith(
        compareBy<SavedCode> { it.label.isNullOrBlank() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle() },
    )
    CodeSortOrder.SYMBOLOGY -> sortedWith(
        compareBy<SavedCode> { it.symbologyId.name }.thenByDescending { it.createdAt },
    )
}
