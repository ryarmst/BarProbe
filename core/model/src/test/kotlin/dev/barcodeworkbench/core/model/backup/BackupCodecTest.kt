package dev.barcodeworkbench.core.model.backup

import com.google.common.truth.Truth.assertThat
import dev.barcodeworkbench.core.model.CodeSource
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.core.model.SymbologyId
import org.junit.Test

class BackupCodecTest {

    private fun code(
        payload: ByteArray = "ABC123".toByteArray(),
        symbology: SymbologyId = SymbologyId.CODE_128,
        label: String? = "Test",
        notes: String? = null,
        tags: Set<String> = emptySet(),
        mode: InputMode = InputMode.UNICODE,
        eci: Int? = null,
    ) = SavedCode(
        id = 1,
        libraryId = 1,
        symbologyId = symbology,
        payload = Payload(payload, mode, eci),
        label = label,
        notes = notes,
        tags = tags,
        source = CodeSource.GENERATED,
        createdAt = 1_700_000_000_000,
    )

    private fun encode(libraries: Map<String, List<SavedCode>>) =
        BackupCodec.encode(libraries, exportedAt = "2026-01-01T00:00:00Z", appVersion = "0.1.0")

    // ---- round trip ----

    @Test
    fun `round trip preserves payload bytes exactly`() {
        val original = ByteArray(256) { it.toByte() }
        val text = encode(mapOf("Main" to listOf(code(payload = original))))
        val envelope = BackupCodec.decode(text)
        val restored = BackupCodec.toSavedCode(envelope.libraries.single().entries.single(), 7)
        assertThat(restored).isNotNull()
        assertThat(restored!!.payload.bytes).isEqualTo(original)
    }

    @Test
    fun `payload containing NUL and invalid UTF-8 survives`() {
        // This is precisely why the payload is base64 in the file rather than a JSON
        // string: these bytes have no valid text representation.
        val awkward = byteArrayOf(0x00, 0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00)
        val text = encode(mapOf("Main" to listOf(code(payload = awkward))))
        val restored = BackupCodec.toSavedCode(
            BackupCodec.decode(text).libraries.single().entries.single(),
            1,
        )
        assertThat(restored!!.payload.bytes).isEqualTo(awkward)
    }

    @Test
    fun `round trip preserves metadata`() {
        val text = encode(
            mapOf(
                "Shipping" to listOf(
                    code(label = "Box A", notes = "handle with care", tags = setOf("b", "a")),
                ),
            ),
        )
        val entry = BackupCodec.decode(text).libraries.single().entries.single()
        val restored = BackupCodec.toSavedCode(entry, 3)!!
        assertThat(restored.label).isEqualTo("Box A")
        assertThat(restored.notes).isEqualTo("handle with care")
        assertThat(restored.tags).containsExactly("a", "b")
        assertThat(restored.libraryId).isEqualTo(3)
    }

    @Test
    fun `round trip preserves input mode and eci`() {
        val text = encode(
            mapOf("Main" to listOf(code(mode = InputMode.BINARY, eci = 26))),
        )
        val restored = BackupCodec.toSavedCode(
            BackupCodec.decode(text).libraries.single().entries.single(),
            1,
        )!!
        assertThat(restored.payload.mode).isEqualTo(InputMode.BINARY)
        assertThat(restored.payload.eci).isEqualTo(26)
    }

    @Test
    fun `multiple libraries round trip`() {
        val text = encode(
            mapOf(
                "One" to listOf(code(label = "a"), code(label = "b")),
                "Two" to listOf(code(label = "c")),
            ),
        )
        val envelope = BackupCodec.decode(text)
        assertThat(envelope.libraryCount).isEqualTo(2)
        assertThat(envelope.codeCount).isEqualTo(3)
        assertThat(envelope.libraries.map { it.name }).containsExactly("One", "Two")
    }

    @Test
    fun `empty backup round trips`() {
        val envelope = BackupCodec.decode(encode(emptyMap()))
        assertThat(envelope.codeCount).isEqualTo(0)
        assertThat(envelope.libraries).isEmpty()
    }

    // ---- envelope and integrity ----

    @Test
    fun `envelope records schema and fingerprint versions separately`() {
        // They are versioned independently so a change to the fingerprint algorithm
        // does not have to masquerade as a schema change.
        val envelope = BackupCodec.decode(encode(mapOf("Main" to listOf(code()))))
        assertThat(envelope.schemaVersion).isEqualTo(BackupCodec.SCHEMA_VERSION)
        assertThat(envelope.fingerprintVersion).isEqualTo(BackupCodec.FINGERPRINT_VERSION)
    }

    @Test
    fun `tampered payload fails the checksum`() {
        // The check exists so a damaged file is rejected before anything is written,
        // rather than half-populating a library.
        val text = encode(mapOf("Main" to listOf(code())))
        val tampered = text.replace("\"label\": \"Test\"", "\"label\": \"Tampered\"")
        assertThat(tampered).isNotEqualTo(text)
        val failure = runCatching { BackupCodec.decode(tampered) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(BackupError.ChecksumMismatch::class.java)
    }

    @Test
    fun `unsupported schema version is rejected with the version found`() {
        val text = encode(mapOf("Main" to listOf(code())))
            .replace("\"schema_version\": 1", "\"schema_version\": 99")
        val failure = runCatching { BackupCodec.decode(text) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(BackupError.UnsupportedSchema::class.java)
        assertThat((failure as BackupError.UnsupportedSchema).found).isEqualTo(99)
    }

    @Test
    fun `malformed json is rejected`() {
        val failure = runCatching { BackupCodec.decode("{not json") }.exceptionOrNull()
        assertThat(failure).isInstanceOf(BackupError.Malformed::class.java)
    }

    @Test
    fun `checksum is stable across tag ordering`() {
        // Tags are a set in the domain, so iteration order must not change the file.
        val a = encode(mapOf("M" to listOf(code(tags = setOf("x", "y", "z")))))
        val b = encode(mapOf("M" to listOf(code(tags = setOf("z", "y", "x")))))
        assertThat(BackupCodec.decode(a).checksum).isEqualTo(BackupCodec.decode(b).checksum)
    }

    // ---- de-duplication ----

    @Test
    fun `re-importing the same backup skips everything as duplicate`() {
        val saved = code(label = "dup")
        val envelope = BackupCodec.decode(encode(mapOf("Main" to listOf(saved))))
        val existing = setOf(BackupCodec.fingerprintOf("Main", saved))

        val plan = BackupCodec.plan(envelope, existing)
        assertThat(plan.toImport).isEmpty()
        assertThat(plan.duplicates).isEqualTo(1)
    }

    @Test
    fun `import into an empty database takes everything`() {
        val envelope = BackupCodec.decode(
            encode(mapOf("Main" to listOf(code(label = "a"), code(label = "b")))),
        )
        val plan = BackupCodec.plan(envelope, emptySet())
        assertThat(plan.toImport).hasSize(2)
        assertThat(plan.duplicates).isEqualTo(0)
    }

    @Test
    fun `duplicates within a single backup file are caught`() {
        // Fingerprints accumulate while planning, so a file containing the same
        // entry twice imports it once.
        val identical = code(label = "same")
        val envelope = BackupCodec.decode(
            encode(mapOf("Main" to listOf(identical, identical))),
        )
        val plan = BackupCodec.plan(envelope, emptySet())
        assertThat(plan.toImport).hasSize(1)
        assertThat(plan.duplicates).isEqualTo(1)
    }

    @Test
    fun `de-duplication can be turned off`() {
        val identical = code(label = "same")
        val envelope = BackupCodec.decode(
            encode(mapOf("Main" to listOf(identical, identical))),
        )
        val plan = BackupCodec.plan(envelope, emptySet(), deduplicate = false)
        assertThat(plan.toImport).hasSize(2)
    }

    @Test
    fun `the same code in different libraries is not a duplicate`() {
        val shared = code(label = "shared")
        val envelope = BackupCodec.decode(
            encode(mapOf("One" to listOf(shared), "Two" to listOf(shared))),
        )
        val plan = BackupCodec.plan(envelope, emptySet())
        assertThat(plan.toImport).hasSize(2)
    }

    @Test
    fun `unknown symbology is skipped rather than failing the import`() {
        // A backup from a newer release may name a format this build does not have.
        // Dropping that entry is better than rejecting the whole file.
        val text = encode(mapOf("Main" to listOf(code())))
            .replace("\"symbology\": \"CODE_128\"", "\"symbology\": \"FUTURE_FORMAT\"")
        // Checksum covers the symbology field, so recompute by decoding leniently.
        val envelope = BackupEnvelope(
            exportedAt = "x",
            appVersion = "x",
            libraryCount = 1,
            codeCount = 1,
            checksum = "ignored",
            libraries = listOf(
                BackupLibrary(
                    "Main",
                    listOf(
                        BackupEntry(
                            symbology = "FUTURE_FORMAT",
                            payloadBase64 = "QUJD",
                            payloadMode = "UNICODE",
                        ),
                    ),
                ),
            ),
        )
        assertThat(text).contains("FUTURE_FORMAT")
        val plan = BackupCodec.plan(envelope, emptySet())
        assertThat(plan.toImport).isEmpty()
        assertThat(plan.unknown).isEqualTo(1)
    }

    @Test
    fun `toSavedCode returns null for an unknown symbology`() {
        val entry = BackupEntry(
            symbology = "NOT_A_FORMAT",
            payloadBase64 = "QUJD",
            payloadMode = "UNICODE",
        )
        assertThat(BackupCodec.toSavedCode(entry, 1)).isNull()
    }

    @Test
    fun `toSavedCode returns null for undecodable base64`() {
        val entry = BackupEntry(
            symbology = "CODE_128",
            payloadBase64 = "!!!not base64!!!",
            payloadMode = "UNICODE",
        )
        assertThat(BackupCodec.toSavedCode(entry, 1)).isNull()
    }

    @Test
    fun `unknown input mode falls back rather than failing`() {
        val entry = BackupEntry(
            symbology = "CODE_128",
            payloadBase64 = "QUJD",
            payloadMode = "SOMETHING_NEW",
        )
        val restored = BackupCodec.toSavedCode(entry, 1)
        assertThat(restored).isNotNull()
        assertThat(restored!!.payload.mode).isEqualTo(InputMode.UNICODE)
    }

    // ---- fingerprint construction ----

    @Test
    fun `fingerprint is length-prefixed so fields cannot bleed together`() {
        // Without length prefixes, label "a" plus notes "bc" would hash the same as
        // label "ab" plus notes "c".
        val first = BackupCodec.fingerprintOf("L", code(label = "a", notes = "bc"))
        val second = BackupCodec.fingerprintOf("L", code(label = "ab", notes = "c"))
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `fingerprint ignores tag ordering`() {
        val a = BackupCodec.fingerprintOf("L", code(tags = setOf("p", "q")))
        val b = BackupCodec.fingerprintOf("L", code(tags = setOf("q", "p")))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `fingerprint distinguishes payloads that differ only in a NUL byte`() {
        val a = BackupCodec.fingerprintOf("L", code(payload = byteArrayOf(0x41)))
        val b = BackupCodec.fingerprintOf("L", code(payload = byteArrayOf(0x41, 0x00)))
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `fingerprint distinguishes input modes`() {
        val a = BackupCodec.fingerprintOf("L", code(mode = InputMode.UNICODE))
        val b = BackupCodec.fingerprintOf("L", code(mode = InputMode.BINARY))
        assertThat(a).isNotEqualTo(b)
    }
}
