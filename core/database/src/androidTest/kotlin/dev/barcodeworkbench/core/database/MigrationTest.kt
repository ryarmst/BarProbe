package dev.barcodeworkbench.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.barcodeworkbench.core.database.migration.ALL_MIGRATIONS
import dev.barcodeworkbench.core.database.migration.MIGRATION_1_2
import dev.barcodeworkbench.core.database.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the schema forward with real data in place.
 *
 * This is the test that makes "no destructive migration" a promise rather than a
 * hope: it proves a v1 database carrying user codes survives the upgrade intact.
 * Requires a device or emulator, so it is not part of the host-side suite.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkbenchDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesExistingCodes() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO libraries (id, name, created_at, sort_order) " +
                    "VALUES (1, 'Shipping', 100, 0)",
            )
            db.execSQL(
                """
                INSERT INTO entries
                    (id, library_id, symbology_id, payload, payload_mode, eci,
                     escapes_enabled, options_json, label, notes, source, created_at)
                VALUES (1, 1, 'CODE_128', X'41001D42', 'BINARY', NULL, 0, NULL,
                        'Has NUL and GS', NULL, 'GENERATED', 200)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT label, payload FROM entries WHERE id = 1").use { cursor ->
            assertTrue("entry survived the migration", cursor.moveToFirst())
            assertEquals("Has NUL and GS", cursor.getString(0))
            // The bytes matter most: a payload with NUL and GS must come through
            // untouched, which is the whole reason it is a BLOB.
            val payload = cursor.getBlob(1)
            assertEquals(4, payload.size)
            assertEquals(0x41.toByte(), payload[0])
            assertEquals(0x00.toByte(), payload[1])
            assertEquals(0x1D.toByte(), payload[2])
            assertEquals(0x42.toByte(), payload[3])
        }

        db.query("SELECT COUNT(*) FROM config_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate1To2_ftsSearchWorksAfterUpgrade() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Inserting through the content table must populate the FTS index via the
        // triggers the migration creates. Without them search returns nothing and the
        // failure is silent.
        db.execSQL(
            """
            INSERT INTO config_entries
                (pack_id, vendor, category, subcategory, name, description,
                 symbology_id, data, escapes_enabled, provenance, verification,
                 warning, destructive, restores_defaults, bundled)
            VALUES ('demo', 'Demo', 'defaults', NULL, 'Restore factory defaults',
                    'Returns the device to shipped settings', 'CODE_128', 'DEMO',
                    0, 'example', 'EXAMPLE_ONLY', NULL, 1, 1, 1)
            """.trimIndent(),
        )

        db.query(
            "SELECT e.name FROM config_entries e " +
                "JOIN config_entries_fts f ON f.rowid = e.id " +
                "WHERE config_entries_fts MATCH 'factory'",
        ).use { cursor ->
            assertTrue("FTS index was populated by the trigger", cursor.moveToFirst())
            assertEquals("Restore factory defaults", cursor.getString(0))
        }
    }

    @Test
    fun migrate2To3_backfillsPacksFromExistingEntries() {
        // An imported pack exists only in the database. If the back-fill were missing,
        // upgrading would leave its entries orphaned from any pack row and the vendor
        // would vanish from the UI -- the exact defect this table exists to fix.
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                """
                INSERT INTO config_entries
                    (pack_id, vendor, category, subcategory, name, description,
                     symbology_id, data, escapes_enabled, provenance, verification,
                     warning, destructive, restores_defaults, bundled)
                VALUES ('acme-x1', 'Acme', 'defaults', NULL, 'Restore defaults', NULL,
                        'CODE_128', 'ACMEDEF', 0, 'Acme manual p12', 'VERIFIED',
                        NULL, 1, 1, 0)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query(
            "SELECT pack_id, vendor, format_version, bundled FROM config_packs",
        ).use { cursor ->
            assertTrue("pack row was back-filled", cursor.moveToFirst())
            assertEquals("acme-x1", cursor.getString(0))
            assertEquals("Acme", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            // bundled=0 preserved from the entry, so a user pack is not mislabelled
            // as one that ships with the app.
            assertEquals(0, cursor.getInt(3))
            assertEquals("exactly one pack row", 1, cursor.count)
        }

        db.query("SELECT COUNT(*) FROM config_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("entries survived", 1, cursor.getInt(0))
        }
    }

    @Test
    fun migrate2To3_onEmptyDatabaseCreatesNoPacks() {
        helper.createDatabase(TEST_DB, 2).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        db.query("SELECT COUNT(*) FROM config_packs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun migrate1To3_walksTheWholeChain() {
        // Each migration is tested in isolation above; this proves they compose, which
        // is the path an install that skipped a release actually takes.
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO libraries (id, name, created_at, sort_order) " +
                    "VALUES (1, 'Kept', 100, 0)",
            )
            db.execSQL(
                """
                INSERT INTO entries
                    (id, library_id, symbology_id, payload, payload_mode, eci,
                     escapes_enabled, options_json, label, notes, source, created_at)
                VALUES (1, 1, 'QR_CODE', X'DEADBEEF', 'BINARY', NULL, 0, NULL,
                        'Survivor', NULL, 'SCANNED', 200)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, *ALL_MIGRATIONS)

        db.query("SELECT label, payload FROM entries WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Survivor", cursor.getString(0))
            val payload = cursor.getBlob(1)
            assertEquals(4, payload.size)
            assertEquals(0xDE.toByte(), payload[0])
            assertEquals(0xEF.toByte(), payload[3])
        }
        db.query("SELECT COUNT(*) FROM config_packs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
