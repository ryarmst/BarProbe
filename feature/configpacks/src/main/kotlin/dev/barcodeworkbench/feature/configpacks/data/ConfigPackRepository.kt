package dev.barcodeworkbench.feature.configpacks.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.barcodeworkbench.core.database.dao.ConfigCategoryRow
import dev.barcodeworkbench.core.database.dao.ConfigEntryDao
import dev.barcodeworkbench.core.database.dao.ConfigPackRow
import dev.barcodeworkbench.core.database.entity.ConfigEntryEntity
import dev.barcodeworkbench.core.database.entity.ConfigPackEntity
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.config.ConfigCategory
import dev.barcodeworkbench.core.model.config.ConfigEntry
import dev.barcodeworkbench.core.model.config.ConfigPackDto
import dev.barcodeworkbench.core.model.config.ConfigPackFormat
import dev.barcodeworkbench.core.model.config.ConfigPackInfo
import dev.barcodeworkbench.core.model.config.VerificationStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
private data class PackIndex(val packs: List<String> = emptyList())

/** Result of importing a user-supplied pack. */
data class PackImportResult(
    val packId: String,
    val vendor: String,
    val entryCount: Int,
)

/**
 * Loads and queries configuration packs.
 *
 * Bundled packs are re-read from assets on every launch and upserted. That is
 * deliberate rather than a one-time seed: it means correcting a bundled entry in a
 * release actually reaches existing installs, instead of being permanently shadowed
 * by whatever was written on first run.
 */
@Singleton
class ConfigPackRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: ConfigEntryDao,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Every pack, including those with no entries.
     *
     * Read from `config_packs` rather than derived from entries. Deriving it meant an
     * empty pack produced no rows and disappeared from the UI entirely, taking with it
     * the explanation of why it ships empty -- so the code that explained it could
     * never run.
     */
    fun observePacks(): Flow<List<ConfigPackInfo>> =
        dao.observePacks().map { rows -> rows.map { it.toDomain() } }

    fun observeVendors(): Flow<List<String>> =
        dao.observePacks().map { rows -> rows.map { it.vendor }.distinct() }

    fun observeCategories(vendor: String): Flow<List<ConfigCategory>> =
        dao.observeCategories(vendor).map { rows -> rows.map { it.toDomain() } }

    fun observeEntries(vendor: String, category: String): Flow<List<ConfigEntry>> =
        dao.observeEntries(vendor, category).map { rows -> rows.map { it.toDomain() } }

    fun observeDefaults(vendor: String): Flow<List<ConfigEntry>> =
        dao.observeDefaults(vendor).map { rows -> rows.map { it.toDomain() } }

    /**
     * Full-text search.
     *
     * The query is rewritten into a prefix MATCH expression, and FTS metacharacters
     * are stripped rather than escaped. A user typing a quote or a hyphen into a
     * search box means it literally, but SQLite would read it as syntax and throw,
     * which would surface as the search screen crashing mid-keystroke.
     */
    fun search(rawQuery: String): Flow<List<ConfigEntry>> {
        val sanitised = sanitiseFtsQuery(rawQuery)
        return dao.search(sanitised).map { rows -> rows.map { it.toDomain() } }
    }

    /** True when a query has enough content to be worth running. */
    fun isSearchable(rawQuery: String): Boolean = sanitiseFtsQuery(rawQuery).isNotEmpty()

    /** Reloads every bundled pack from assets. Safe to call on each launch. */
    suspend fun syncBundledPacks(): List<PackImportResult> {
        val index = runCatching {
            context.assets.open("$ASSET_DIR/$INDEX_FILE").use { stream ->
                json.decodeFromString<PackIndex>(stream.readBytes().toString(Charsets.UTF_8))
            }
        }.getOrNull() ?: return emptyList()

        return index.packs.mapNotNull { fileName ->
            runCatching {
                val text = context.assets.open("$ASSET_DIR/$fileName").use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                val pack = ConfigPackFormat.parse(text)
                writePack(pack, bundled = true)
            }.getOrNull()
        }
    }

    /**
     * Imports a user pack.
     *
     * Throws [dev.barcodeworkbench.core.model.config.ConfigPackError] on any problem,
     * so the caller can report exactly what is wrong rather than partially importing.
     */
    suspend fun importPack(text: String): PackImportResult {
        val pack = ConfigPackFormat.parse(text)
        return writePack(pack, bundled = false)
    }

    suspend fun deletePack(packId: String) {
        dao.deletePack(packId)
        dao.deletePackRow(packId)
    }

    suspend fun userPackIds(): List<String> = dao.userPackIds()

    suspend fun getEntry(id: Long): ConfigEntry? = dao.get(id)?.toDomain()

    private suspend fun writePack(pack: ConfigPackDto, bundled: Boolean): PackImportResult {
        val entries = ConfigPackFormat.toDomain(pack, bundled)
        // Replaced wholesale so an entry removed from a pack disappears rather than
        // lingering from a previous version.
        dao.deletePack(pack.packId)
        dao.upsertPack(
            ConfigPackEntity(
                packId = pack.packId,
                vendor = pack.vendor,
                description = pack.description,
                formatVersion = pack.formatVersion,
                bundled = bundled,
            ),
        )
        dao.upsertAll(entries.map { it.toEntity() })
        return PackImportResult(
            packId = pack.packId,
            vendor = pack.vendor,
            entryCount = entries.size,
        )
    }

    private fun ConfigPackRow.toDomain() = ConfigPackInfo(
        packId = packId,
        vendor = vendor,
        description = description,
        entryCount = entryCount,
        bundled = bundled,
        formatVersion = formatVersion,
    )

    private fun ConfigCategoryRow.toDomain() = ConfigCategory(
        vendor = vendor,
        name = category,
        entryCount = entryCount,
        isDefaults = hasDefaults == 1,
    )

    private fun ConfigEntryEntity.toDomain() = ConfigEntry(
        id = id,
        packId = packId,
        vendor = vendor,
        category = category,
        subcategory = subcategory,
        name = name,
        description = description,
        symbologyId = runCatching { SymbologyId.valueOf(symbologyId) }
            .getOrDefault(SymbologyId.CODE_128),
        data = data,
        escapesEnabled = escapesEnabled,
        provenance = provenance,
        verification = runCatching { VerificationStatus.valueOf(verification) }
            .getOrDefault(VerificationStatus.UNSPECIFIED),
        warning = warning,
        destructive = destructive,
        restoresDefaults = restoresDefaults,
        bundled = bundled,
    )

    private fun ConfigEntry.toEntity() = ConfigEntryEntity(
        packId = packId,
        vendor = vendor,
        category = category,
        subcategory = subcategory,
        name = name,
        description = description,
        symbologyId = symbologyId.name,
        data = data,
        escapesEnabled = escapesEnabled,
        provenance = provenance,
        verification = verification.name,
        warning = warning,
        destructive = destructive,
        restoresDefaults = restoresDefaults,
        bundled = bundled,
    )

    internal companion object {
        const val ASSET_DIR = "configpacks"
        const val INDEX_FILE = "index.json"

        /**
         * Turns free text into a safe FTS4 MATCH expression.
         *
         * Everything outside letters, digits and underscore becomes a separator, and
         * each surviving token gets a prefix wildcard so results narrow as the user
         * types.
         */
        fun sanitiseFtsQuery(raw: String): String =
            raw.split(Regex("[^\\p{L}\\p{N}_]+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { "$it*" }
    }
}
