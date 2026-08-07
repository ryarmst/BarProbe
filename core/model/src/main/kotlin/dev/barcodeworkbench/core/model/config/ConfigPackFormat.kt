package dev.barcodeworkbench.core.model.config

import dev.barcodeworkbench.core.model.SymbologyId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Serialised form of one entry in a pack file. */
@Serializable
data class ConfigEntryDto(
    val name: String,
    val description: String? = null,
    val category: String,
    val subcategory: String? = null,
    val symbology: String = "CODE_128",
    val data: String,
    @SerialName("escapes_enabled") val escapesEnabled: Boolean = false,
    val provenance: String,
    val verification: String = "UNSPECIFIED",
    val warning: String? = null,
    val destructive: Boolean = false,
    @SerialName("restores_defaults") val restoresDefaults: Boolean = false,
)

/** Serialised form of a whole pack file. */
@Serializable
data class ConfigPackDto(
    @SerialName("format_version") val formatVersion: Int = ConfigPackFormat.FORMAT_VERSION,
    @SerialName("pack_id") val packId: String,
    val vendor: String,
    val description: String? = null,
    val entries: List<ConfigEntryDto> = emptyList(),
)

/** Why a pack could not be loaded. */
sealed class ConfigPackError(message: String) : Exception(message) {
    class Malformed(detail: String) : ConfigPackError("Pack could not be parsed: $detail")
    class UnsupportedVersion(val found: Int) :
        ConfigPackError("Pack format version $found is not supported")
    class Invalid(val problems: List<String>) :
        ConfigPackError("Pack contains ${problems.size} invalid entries")
}

/**
 * Parses and validates pack files.
 *
 * Validation is strict on import. A pack is executable content in the sense that it
 * produces barcodes a user will scan at real hardware, so a malformed or ambiguous
 * entry is rejected rather than best-guessed.
 */
object ConfigPackFormat {

    const val FORMAT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun parse(text: String): ConfigPackDto {
        val dto = try {
            json.decodeFromString<ConfigPackDto>(text)
        } catch (e: Exception) {
            throw ConfigPackError.Malformed(e.message ?: e::class.simpleName ?: "unknown")
        }
        if (dto.formatVersion != FORMAT_VERSION) {
            throw ConfigPackError.UnsupportedVersion(dto.formatVersion)
        }
        val problems = validate(dto)
        if (problems.isNotEmpty()) {
            throw ConfigPackError.Invalid(problems)
        }
        return dto
    }

    fun encode(pack: ConfigPackDto): String = json.encodeToString(pack)

    /** Collects every problem so an author sees them all at once. */
    fun validate(dto: ConfigPackDto): List<String> {
        val problems = mutableListOf<String>()
        if (dto.packId.isBlank()) problems += "pack_id is required"
        if (dto.vendor.isBlank()) problems += "vendor is required"

        dto.entries.forEachIndexed { index, entry ->
            val where = "entry $index (${entry.name.ifBlank { "unnamed" }})"
            if (entry.name.isBlank()) problems += "$where: name is required"
            if (entry.category.isBlank()) problems += "$where: category is required"
            if (entry.data.isBlank()) problems += "$where: data is required"
            // Provenance is mandatory: an entry nobody can trace is one nobody can
            // check, and unverifiable programming codes are the failure mode this
            // whole design guards against.
            if (entry.provenance.isBlank()) problems += "$where: provenance is required"
            if (resolveSymbology(entry.symbology) == null) {
                problems += "$where: unknown symbology '${entry.symbology}'"
            }
            if (resolveVerification(entry.verification) == null) {
                problems += "$where: unknown verification status '${entry.verification}'"
            }
        }
        return problems
    }

    fun toDomain(dto: ConfigPackDto, bundled: Boolean): List<ConfigEntry> =
        dto.entries.mapNotNull { entry ->
            val symbology = resolveSymbology(entry.symbology) ?: return@mapNotNull null
            ConfigEntry(
                packId = dto.packId,
                vendor = dto.vendor,
                category = entry.category,
                subcategory = entry.subcategory,
                name = entry.name,
                description = entry.description,
                symbologyId = symbology,
                data = entry.data,
                escapesEnabled = entry.escapesEnabled,
                provenance = entry.provenance,
                verification = resolveVerification(entry.verification)
                    ?: VerificationStatus.UNSPECIFIED,
                warning = entry.warning,
                destructive = entry.destructive,
                restoresDefaults = entry.restoresDefaults,
                bundled = bundled,
            )
        }

    private fun resolveSymbology(name: String): SymbologyId? =
        runCatching { SymbologyId.valueOf(name) }.getOrNull()

    private fun resolveVerification(name: String): VerificationStatus? =
        runCatching { VerificationStatus.valueOf(name) }.getOrNull()
}
