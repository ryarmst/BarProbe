package dev.barcodeworkbench.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CodeFilterTest {

    private fun code(
        id: Long = 1,
        payload: String = "ABC123",
        symbology: SymbologyId = SymbologyId.CODE_128,
        label: String? = null,
        notes: String? = null,
        tags: Set<String> = emptySet(),
        source: CodeSource = CodeSource.GENERATED,
        createdAt: Long = 1000,
    ) = SavedCode(
        id = id,
        libraryId = 1,
        symbologyId = symbology,
        payload = Payload(payload.toByteArray()),
        label = label,
        notes = notes,
        tags = tags,
        source = source,
        createdAt = createdAt,
    )

    @Test
    fun `empty filter matches everything`() {
        assertThat(CodeFilter().isEmpty).isTrue()
        assertThat(CodeFilter().matches(code())).isTrue()
    }

    @Test
    fun `query matches the label case-insensitively`() {
        val c = code(label = "Shipping Box")
        assertThat(CodeFilter(query = "shipping").matches(c)).isTrue()
        assertThat(CodeFilter(query = "BOX").matches(c)).isTrue()
        assertThat(CodeFilter(query = "pallet").matches(c)).isFalse()
    }

    @Test
    fun `query matches notes and tags`() {
        assertThat(CodeFilter(query = "fragile").matches(code(notes = "Fragile item"))).isTrue()
        assertThat(CodeFilter(query = "warehouse").matches(code(tags = setOf("warehouse"))))
            .isTrue()
    }

    @Test
    fun `query matches the payload`() {
        assertThat(CodeFilter(query = "ABC").matches(code(payload = "ABC123"))).isTrue()
    }

    @Test
    fun `query matches an escape sequence in a control-character payload`() {
        // The reason search runs over the escaped rendering: a Group Separator has no
        // searchable text form, and this is the only way to find codes containing one.
        val withGs = SavedCode(
            id = 1,
            libraryId = 1,
            symbologyId = SymbologyId.CODE_128,
            payload = Payload(byteArrayOf(0x41, 0x1D, 0x42)),
        )
        assertThat(CodeFilter(query = "\\x1D").matches(withGs)).isTrue()
        assertThat(CodeFilter(query = "x1d").matches(withGs)).isTrue()
    }

    @Test
    fun `symbology filter narrows by format`() {
        val c = code(symbology = SymbologyId.QR_CODE)
        assertThat(CodeFilter(symbologies = setOf(SymbologyId.QR_CODE)).matches(c)).isTrue()
        assertThat(CodeFilter(symbologies = setOf(SymbologyId.EAN_13)).matches(c)).isFalse()
    }

    @Test
    fun `source filter narrows by origin`() {
        val scanned = code(source = CodeSource.SCANNED)
        assertThat(CodeFilter(sources = setOf(CodeSource.SCANNED)).matches(scanned)).isTrue()
        assertThat(CodeFilter(sources = setOf(CodeSource.GENERATED)).matches(scanned)).isFalse()
    }

    @Test
    fun `tag filter matches any of the selected tags`() {
        // Any rather than all: selecting two tags widens the result set, which is what
        // tapping a second tag chip visibly implies.
        val c = code(tags = setOf("a", "b"))
        assertThat(CodeFilter(tags = setOf("b", "z")).matches(c)).isTrue()
        assertThat(CodeFilter(tags = setOf("y", "z")).matches(c)).isFalse()
    }

    @Test
    fun `filters combine as an intersection`() {
        val c = code(label = "Box", symbology = SymbologyId.QR_CODE)
        assertThat(
            CodeFilter(query = "Box", symbologies = setOf(SymbologyId.QR_CODE)).matches(c),
        ).isTrue()
        assertThat(
            CodeFilter(query = "Box", symbologies = setOf(SymbologyId.EAN_13)).matches(c),
        ).isFalse()
    }

    @Test
    fun `display title falls back to a payload preview`() {
        assertThat(code(label = "Named").displayTitle()).isEqualTo("Named")
        assertThat(code(label = null, payload = "FALLBACK").displayTitle()).isEqualTo("FALLBACK")
        assertThat(code(label = "   ", payload = "BLANKLABEL").displayTitle())
            .isEqualTo("BLANKLABEL")
    }

    @Test
    fun `payload preview escapes control characters rather than rendering them`() {
        val c = SavedCode(
            id = 1,
            libraryId = 1,
            symbologyId = SymbologyId.CODE_128,
            payload = Payload(byteArrayOf(0x41, 0x1D)),
        )
        assertThat(c.payloadPreview()).isEqualTo("A\\x1D")
    }

    @Test
    fun `payload preview truncates long payloads`() {
        val long = code(payload = "X".repeat(200))
        assertThat(long.payloadPreview(maxChars = 10)).hasLength(11)
        assertThat(long.payloadPreview(maxChars = 10)).endsWith("…")
    }

    @Test
    fun `sorting newest first orders by creation time descending`() {
        val list = listOf(code(id = 1, createdAt = 100), code(id = 2, createdAt = 300))
        assertThat(list.sortedBy(CodeSortOrder.NEWEST_FIRST).map { it.id })
            .containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `sorting by label puts unlabelled entries last`() {
        // An unlabelled entry sorts on its payload, so without this rule those rows
        // would interleave unpredictably with real labels.
        val list = listOf(
            code(id = 1, label = null, payload = "AAA"),
            code(id = 2, label = "Zebra"),
            code(id = 3, label = "Alpha"),
        )
        assertThat(list.sortedBy(CodeSortOrder.LABEL_ASCENDING).map { it.id })
            .containsExactly(3L, 2L, 1L).inOrder()
    }

    @Test
    fun `sorting by symbology groups formats then orders newest first`() {
        val list = listOf(
            code(id = 1, symbology = SymbologyId.QR_CODE, createdAt = 100),
            code(id = 2, symbology = SymbologyId.CODE_128, createdAt = 100),
            code(id = 3, symbology = SymbologyId.QR_CODE, createdAt = 200),
        )
        assertThat(list.sortedBy(CodeSortOrder.SYMBOLOGY).map { it.id })
            .containsExactly(2L, 3L, 1L).inOrder()
    }
}
