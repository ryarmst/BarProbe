package dev.barcodeworkbench.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.barcodeworkbench.core.database.entity.ConfigEntryEntity
import dev.barcodeworkbench.core.database.entity.ConfigPackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigEntryDao {

    /**
     * Packs with their entry counts.
     *
     * A LEFT-style correlated count rather than a join on entries, so a pack with no
     * entries still returns a row. That is the whole point of the table.
     */
    @Query(
        """
        SELECT p.pack_id AS packId, p.vendor AS vendor, p.description AS description,
               p.format_version AS formatVersion, p.bundled AS bundled,
               (SELECT COUNT(*) FROM config_entries e WHERE e.pack_id = p.pack_id)
                   AS entryCount
        FROM config_packs p
        ORDER BY p.vendor COLLATE NOCASE ASC
        """,
    )
    fun observePacks(): Flow<List<ConfigPackRow>>

    @Query("SELECT * FROM config_packs WHERE pack_id = :packId")
    suspend fun getPack(packId: String): ConfigPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: ConfigPackEntity)

    @Query("DELETE FROM config_packs WHERE pack_id = :packId")
    suspend fun deletePackRow(packId: String)

    @Query(
        """
        SELECT vendor, category, COUNT(*) AS entryCount,
               MAX(restores_defaults) AS hasDefaults
        FROM config_entries
        WHERE vendor = :vendor
        GROUP BY vendor, category
        ORDER BY hasDefaults DESC, category COLLATE NOCASE ASC
        """,
    )
    fun observeCategories(vendor: String): Flow<List<ConfigCategoryRow>>

    @Query(
        """
        SELECT * FROM config_entries
        WHERE vendor = :vendor AND category = :category
        ORDER BY restores_defaults DESC, name COLLATE NOCASE ASC
        """,
    )
    fun observeEntries(vendor: String, category: String): Flow<List<ConfigEntryEntity>>

    /** Recovery paths, surfaced ahead of everything else for a vendor. */
    @Query(
        """
        SELECT * FROM config_entries
        WHERE vendor = :vendor AND restores_defaults = 1
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeDefaults(vendor: String): Flow<List<ConfigEntryEntity>>

    /**
     * Full-text search across every pack.
     *
     * Joins the FTS table back to the content table so the full row is returned. The
     * query is passed to MATCH, so callers must sanitise it.
     */
    @Query(
        """
        SELECT e.* FROM config_entries e
        JOIN config_entries_fts f ON f.rowid = e.id
        WHERE config_entries_fts MATCH :query
        ORDER BY e.vendor COLLATE NOCASE ASC, e.category COLLATE NOCASE ASC,
                 e.name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun search(query: String, limit: Int = 200): Flow<List<ConfigEntryEntity>>

    @Query("SELECT * FROM config_entries WHERE id = :id")
    suspend fun get(id: Long): ConfigEntryEntity?

    @Query("SELECT COUNT(*) FROM config_entries WHERE pack_id = :packId")
    suspend fun countForPack(packId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ConfigEntryEntity>)

    @Query("DELETE FROM config_entries WHERE pack_id = :packId")
    suspend fun deletePack(packId: String)

    @Query("SELECT DISTINCT pack_id FROM config_entries WHERE bundled = 0")
    suspend fun userPackIds(): List<String>
}
