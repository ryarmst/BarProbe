package dev.barcodeworkbench.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A configuration pack, stored independently of its entries.
 *
 * Separate from [ConfigEntryEntity] because a pack can legitimately be empty. The
 * vendor list was previously derived with `SELECT DISTINCT vendor FROM config_entries`,
 * which meant an empty pack contributed no rows and vanished entirely -- taking with it
 * the explanation of why it ships empty and how to populate it. Pack identity has to
 * exist whether or not any entry does.
 */
@Entity(
    tableName = "config_packs",
    indices = [Index("vendor")],
)
data class ConfigPackEntity(
    @PrimaryKey @ColumnInfo(name = "pack_id") val packId: String,
    val vendor: String,
    val description: String? = null,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    val bundled: Boolean = true,
)
