package dev.barcodeworkbench.core.model.backup

import dev.barcodeworkbench.core.model.CodeSource
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.core.model.SymbologyId
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One code inside a backup file. */
@Serializable
data class BackupEntry(
    val symbology: String,
    /**
     * Base64 rather than a JSON string.
     *
     * Payloads are arbitrary bytes: control characters, embedded NUL and invalid
     * UTF-8 sequences are all legitimate. Putting them in a JSON string would either
     * corrupt them or make the file unparseable, so they are encoded.
     */
    @SerialName("payload_b64") val payloadBase64: String,
    @SerialName("payload_mode") val payloadMode: String,
    val eci: Int? = null,
    @SerialName("escapes_enabled") val escapesEnabled: Boolean = false,
    val label: String? = null,
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    val source: String = CodeSource.IMPORTED.name,
    @SerialName("created_at") val createdAt: Long = 0,
)

/** One library and its codes. */
@Serializable
data class BackupLibrary(
    val name: String,
    val entries: List<BackupEntry>,
)

/**
 * The backup envelope.
 *
 * [fingerprintVersion] is versioned separately from [schemaVersion] on purpose: if a
 * future release adds a field to the de-duplication fingerprint, old backups must
 * still de-duplicate correctly against the algorithm they were written with. Bumping
 * only the schema version would silently change what counts as a duplicate.
 */
@Serializable
data class BackupEnvelope(
    @SerialName("schema_version") val schemaVersion: Int = BackupCodec.SCHEMA_VERSION,
    @SerialName("fingerprint_version") val fingerprintVersion: Int = BackupCodec.FINGERPRINT_VERSION,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("library_count") val libraryCount: Int,
    @SerialName("code_count") val codeCount: Int,
    @SerialName("checksum_type") val checksumType: String = "SHA-256",
    val checksum: String,
    val libraries: List<BackupLibrary>,
)

/** Why a backup could not be read. */
sealed class BackupError(message: String) : Exception(message) {
    class Malformed(detail: String) : BackupError("Backup file could not be parsed: $detail")
    class UnsupportedSchema(val found: Int) :
        BackupError("Backup schema version $found is not supported")
    class ChecksumMismatch : BackupError("Backup checksum does not match; the file is damaged")
}

/** What an import decided to do with each entry. */
enum class ImportDecision { IMPORT, SKIP_DUPLICATE, SKIP_UNKNOWN_SYMBOLOGY }

data class ImportPlanItem(
    val libraryName: String,
    val entry: BackupEntry,
    val decision: ImportDecision,
)

data class ImportPlan(val items: List<ImportPlanItem>) {
    val toImport: List<ImportPlanItem> get() = items.filter { it.decision == ImportDecision.IMPORT }
    val duplicates: Int get() = items.count { it.decision == ImportDecision.SKIP_DUPLICATE }
    val unknown: Int get() = items.count { it.decision == ImportDecision.SKIP_UNKNOWN_SYMBOLOGY }
}

/**
 * Reads and writes backup files.
 *
 * The design follows the one from the app we reverse-engineered, which got this
 * right: a versioned envelope, a checksum verified before anything is written, and
 * de-duplication on import. Only the JSON shape and the byte encoding differ.
 *
 * Compression and file IO are deliberately left to the caller so this stays a pure
 * module and its rules remain host-testable.
 */
object BackupCodec {

    const val SCHEMA_VERSION = 1
    const val FINGERPRINT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val base64Encoder: Base64.Encoder = Base64.getEncoder()
    private val base64Decoder: Base64.Decoder = Base64.getDecoder()

    fun encode(
        libraries: Map<String, List<SavedCode>>,
        exportedAt: String,
        appVersion: String,
    ): String {
        val backupLibraries = libraries.map { (name, codes) ->
            BackupLibrary(name = name, entries = codes.map { it.toBackupEntry() })
        }
        val checksum = checksumOf(backupLibraries)
        val envelope = BackupEnvelope(
            exportedAt = exportedAt,
            appVersion = appVersion,
            libraryCount = backupLibraries.size,
            codeCount = backupLibraries.sumOf { it.entries.size },
            checksum = checksum,
            libraries = backupLibraries,
        )
        return json.encodeToString(envelope)
    }

    /**
     * Parses and verifies a backup.
     *
     * The checksum is validated here, before the caller has a chance to write
     * anything, so a damaged file cannot half-populate a library.
     */
    fun decode(text: String): BackupEnvelope {
        val envelope = try {
            json.decodeFromString<BackupEnvelope>(text)
        } catch (e: Exception) {
            throw BackupError.Malformed(e.message ?: e::class.simpleName ?: "unknown")
        }

        if (envelope.schemaVersion != SCHEMA_VERSION) {
            throw BackupError.UnsupportedSchema(envelope.schemaVersion)
        }
        if (checksumOf(envelope.libraries) != envelope.checksum) {
            throw BackupError.ChecksumMismatch()
        }
        return envelope
    }

    /**
     * Decides what to do with each entry, without writing anything.
     *
     * [existingFingerprints] lets the caller pass in what is already stored so a
     * re-import does not duplicate. Fingerprints accumulate as the plan is built, so
     * duplicates *within* one backup file are also caught.
     */
    fun plan(
        envelope: BackupEnvelope,
        existingFingerprints: Set<String>,
        deduplicate: Boolean = true,
    ): ImportPlan {
        val seen = existingFingerprints.toMutableSet()
        val items = mutableListOf<ImportPlanItem>()

        envelope.libraries.forEach { library ->
            library.entries.forEach { entry ->
                val decision = when {
                    resolveSymbology(entry.symbology) == null ->
                        ImportDecision.SKIP_UNKNOWN_SYMBOLOGY

                    deduplicate && !seen.add(fingerprintOf(library.name, entry)) ->
                        ImportDecision.SKIP_DUPLICATE

                    else -> ImportDecision.IMPORT
                }
                items += ImportPlanItem(library.name, entry, decision)
            }
        }
        return ImportPlan(items)
    }

    /** Converts a backup entry to a domain code, or null if its symbology is unknown. */
    fun toSavedCode(entry: BackupEntry, libraryId: Long): SavedCode? {
        val symbology = resolveSymbology(entry.symbology) ?: return null
        val bytes = runCatching { base64Decoder.decode(entry.payloadBase64) }.getOrNull()
            ?: return null
        val mode = runCatching { InputMode.valueOf(entry.payloadMode) }
            .getOrDefault(InputMode.UNICODE)
        val source = runCatching { CodeSource.valueOf(entry.source) }
            .getOrDefault(CodeSource.IMPORTED)
        return SavedCode(
            id = 0,
            libraryId = libraryId,
            symbologyId = symbology,
            payload = Payload(bytes, mode, entry.eci, entry.escapesEnabled),
            label = entry.label,
            notes = entry.notes,
            tags = entry.tags.toSet(),
            source = source,
            createdAt = entry.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
    }

    /**
     * Content fingerprint used for de-duplication.
     *
     * Built from a length-prefixed encoding rather than plain concatenation, so
     * values cannot collide by shifting a delimiter: a label of "a" with notes "bc"
     * must not fingerprint the same as label "ab" with notes "c".
     */
    fun fingerprintOf(libraryName: String, entry: BackupEntry): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun feed(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
            digest.update(':'.code.toByte())
            digest.update(bytes)
            digest.update('\n'.code.toByte())
        }
        feed(FINGERPRINT_VERSION.toString())
        feed(libraryName)
        feed(entry.symbology)
        feed(entry.payloadBase64)
        feed(entry.payloadMode)
        feed(entry.eci?.toString() ?: "")
        feed(entry.label ?: "")
        feed(entry.notes ?: "")
        feed(entry.tags.sorted().joinToString(","))
        return digest.digest().toHexString()
    }

    fun fingerprintOf(libraryName: String, code: SavedCode): String =
        fingerprintOf(libraryName, code.toBackupEntry())

    private fun SavedCode.toBackupEntry() = BackupEntry(
        symbology = symbologyId.name,
        payloadBase64 = base64Encoder.encodeToString(payload.bytes),
        payloadMode = payload.mode.name,
        eci = payload.eci,
        escapesEnabled = payload.escapesEnabled,
        label = label,
        notes = notes,
        // Sorted so the same content always produces the same bytes, which is what
        // makes the checksum and the fingerprint stable.
        tags = tags.sorted(),
        source = source.name,
        createdAt = createdAt,
    )

    private fun resolveSymbology(name: String): SymbologyId? =
        runCatching { SymbologyId.valueOf(name) }.getOrNull()

    /** Checksum over the payload only, so envelope metadata cannot affect it. */
    private fun checksumOf(libraries: List<BackupLibrary>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        libraries.forEach { library ->
            digest.update(library.name.toByteArray(Charsets.UTF_8))
            digest.update(0)
            library.entries.forEach { entry ->
                listOf(
                    entry.symbology,
                    entry.payloadBase64,
                    entry.payloadMode,
                    entry.eci?.toString() ?: "",
                    entry.escapesEnabled.toString(),
                    entry.label ?: "",
                    entry.notes ?: "",
                    entry.tags.joinToString(","),
                ).forEach { field ->
                    digest.update(field.toByteArray(Charsets.UTF_8))
                    digest.update(0)
                }
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
