package dev.barcodeworkbench.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.barcodeworkbench.core.database.dao.EntryDao
import dev.barcodeworkbench.core.database.dao.LibraryDao
import dev.barcodeworkbench.core.database.dao.ConfigEntryDao
import dev.barcodeworkbench.core.database.entity.ConfigEntryEntity
import dev.barcodeworkbench.core.database.entity.ConfigEntryFts
import dev.barcodeworkbench.core.database.entity.ConfigPackEntity
import dev.barcodeworkbench.core.database.entity.EntryEntity
import dev.barcodeworkbench.core.database.entity.EntryTagCrossRef
import dev.barcodeworkbench.core.database.entity.LibraryEntity
import dev.barcodeworkbench.core.database.entity.TagEntity

/**
 * Schema version history:
 *
 *  1. libraries, entries, tags, entry_tags.
 *  2. config_entries and its FTS index, for device programming barcodes.
 *  3. config_packs, so a pack with no entries still exists and can explain itself.
 *
 * Every future version gets an explicit [androidx.room.migration.Migration] and a
 * test that walks the schema forward with real data. Destructive fallback is
 * never enabled -- a user's saved codes are not disposable, and silently dropping
 * a table is the failure mode the prior-art app's hand-rolled SQL upgrades were
 * still cleaning up after years later.
 */
@Database(
    entities = [
        LibraryEntity::class,
        EntryEntity::class,
        TagEntity::class,
        EntryTagCrossRef::class,
        ConfigEntryEntity::class,
        ConfigEntryFts::class,
        ConfigPackEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class WorkbenchDatabase : RoomDatabase() {

    abstract fun libraryDao(): LibraryDao

    abstract fun entryDao(): EntryDao

    abstract fun configEntryDao(): ConfigEntryDao

    companion object {
        const val NAME = "workbench.db"
    }
}
