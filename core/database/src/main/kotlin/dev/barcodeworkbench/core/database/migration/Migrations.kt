package dev.barcodeworkbench.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the device configuration tables.
 *
 * The SQL is copied verbatim from Room's exported `2.json` schema rather than written
 * by hand. Room validates the resulting schema against an identity hash at runtime,
 * so an approximation would fail on the first launch after upgrade -- and a
 * hand-written approximation of an FTS table is exactly the kind of thing that looks
 * right and is not.
 *
 * The three triggers keep the FTS index synchronised with its content table. Room
 * generates these automatically for a freshly created database but a migration has to
 * create them explicitly, and without them search silently returns stale results.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `config_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pack_id` TEXT NOT NULL,
                `vendor` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `subcategory` TEXT,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `symbology_id` TEXT NOT NULL,
                `data` TEXT NOT NULL,
                `escapes_enabled` INTEGER NOT NULL,
                `provenance` TEXT NOT NULL,
                `verification` TEXT NOT NULL,
                `warning` TEXT,
                `destructive` INTEGER NOT NULL,
                `restores_defaults` INTEGER NOT NULL,
                `bundled` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_config_entries_pack_id_category_name` " +
                "ON `config_entries` (`pack_id`, `category`, `name`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_config_entries_vendor` " +
                "ON `config_entries` (`vendor`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_config_entries_category` " +
                "ON `config_entries` (`category`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_config_entries_restores_defaults` " +
                "ON `config_entries` (`restores_defaults`)",
        )
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `config_entries_fts` USING FTS4(" +
                "`vendor` TEXT NOT NULL, `category` TEXT NOT NULL, `subcategory` TEXT, " +
                "`name` TEXT NOT NULL, `description` TEXT, `data` TEXT NOT NULL, " +
                "content=`config_entries`)",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_config_entries_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `config_entries` BEGIN " +
                "DELETE FROM `config_entries_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_config_entries_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `config_entries` BEGIN " +
                "DELETE FROM `config_entries_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_config_entries_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `config_entries` BEGIN " +
                "INSERT INTO `config_entries_fts`(`docid`, `vendor`, `category`, `subcategory`, " +
                "`name`, `description`, `data`) VALUES (NEW.`rowid`, NEW.`vendor`, " +
                "NEW.`category`, NEW.`subcategory`, NEW.`name`, NEW.`description`, NEW.`data`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_config_entries_fts_AFTER_INSERT " +
                "AFTER INSERT ON `config_entries` BEGIN " +
                "INSERT INTO `config_entries_fts`(`docid`, `vendor`, `category`, `subcategory`, " +
                "`name`, `description`, `data`) VALUES (NEW.`rowid`, NEW.`vendor`, " +
                "NEW.`category`, NEW.`subcategory`, NEW.`name`, NEW.`description`, NEW.`data`); END",
        )
    }
}

/**
 * Adds the config_packs table.
 *
 * Existing rows are back-filled from the entries already present, so an upgrade does
 * not lose the packs a user has imported. Bundled packs are re-read from assets on
 * every launch and will correct their own rows, but an imported pack exists only in
 * the database -- inferring it from its entries here is the only way to preserve it.
 *
 * format_version defaults to 1, which is the only version that can exist in a v2
 * database, and bundled is inferred from the entries' own flag.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `config_packs` (
                `pack_id` TEXT NOT NULL,
                `vendor` TEXT NOT NULL,
                `description` TEXT,
                `format_version` INTEGER NOT NULL,
                `bundled` INTEGER NOT NULL,
                PRIMARY KEY(`pack_id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_config_packs_vendor` " +
                "ON `config_packs` (`vendor`)",
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO `config_packs`
                (`pack_id`, `vendor`, `description`, `format_version`, `bundled`)
            SELECT DISTINCT e.`pack_id`, e.`vendor`, NULL, 1, e.`bundled`
            FROM `config_entries` e
            """.trimIndent(),
        )
    }
}

/** Every migration, in order. Registered with the database builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
