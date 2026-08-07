package dev.barcodeworkbench.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A programming barcode.
 *
 * Bundled packs are re-imported on every launch when their content changes, so
 * [packId] plus [name] plus [category] is unique: re-loading replaces rather than
 * duplicates.
 */
@Entity(
    tableName = "config_entries",
    indices = [
        Index(value = ["pack_id", "category", "name"], unique = true),
        Index("vendor"),
        Index("category"),
        Index("restores_defaults"),
    ],
)
data class ConfigEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pack_id") val packId: String,
    val vendor: String,
    val category: String,
    val subcategory: String? = null,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "symbology_id") val symbologyId: String,
    val data: String,
    @ColumnInfo(name = "escapes_enabled") val escapesEnabled: Boolean = false,
    val provenance: String,
    val verification: String,
    val warning: String? = null,
    val destructive: Boolean = false,
    @ColumnInfo(name = "restores_defaults") val restoresDefaults: Boolean = false,
    val bundled: Boolean = true,
)

/**
 * Full-text index over the searchable columns.
 *
 * Vendors document very large parameter spaces -- Zebra's runs to hundreds of
 * settings -- so hierarchy alone is not enough to find anything. FTS over name,
 * description, category and the data string is what makes the catalogue usable.
 */
@Fts4(contentEntity = ConfigEntryEntity::class)
@Entity(tableName = "config_entries_fts")
data class ConfigEntryFts(
    val vendor: String,
    val category: String,
    val subcategory: String?,
    val name: String,
    val description: String?,
    val data: String,
)
