package dev.barcodeworkbench.feature.configpacks.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.core.database.WorkbenchDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Loads the bundled packs the way the app does and checks what actually lands in the
 * database.
 *
 * This exists because the host tests cannot see the failure that matters. Packs are
 * upserted into a table with a unique index on (pack_id, category, name) using
 * REPLACE, so entries that collide are not rejected -- they overwrite each other and
 * vanish, with no error anywhere. The Zebra pack lost 37 entries that way before the
 * naming was fixed, and only a real insert would have shown it.
 */
@RunWith(AndroidJUnit4::class)
class BundledPackLoadTest {

    private lateinit var db: WorkbenchDatabase
    private lateinit var repository: ConfigPackRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // In-memory, but the same schema and the same indices as the shipped database.
        db = Room.inMemoryDatabaseBuilder(context, WorkbenchDatabase::class.java).build()
        repository = ConfigPackRepository(context, db.configEntryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun everyBundledEntryReachesTheDatabase() = runTest {
        val results = repository.syncBundledPacks()
        assertThat(results).isNotEmpty()

        results.forEach { result ->
            val vendorEntries = repository.observePacks().first()
                .first { it.packId == result.packId }
            // entryCount is what the loader believed it wrote. If the index dropped
            // rows, the count observed back from the database is lower.
            assertWithMessage(
                "pack '${result.packId}' reported ${result.entryCount} entries but the " +
                    "database holds ${vendorEntries.entryCount}; a (category, name) " +
                    "collision has silently replaced rows",
            ).that(vendorEntries.entryCount).isEqualTo(result.entryCount)
        }
    }

    @Test
    fun zebraPackLoadsCompletely() = runTest {
        repository.syncBundledPacks()
        val zebra = repository.observePacks().first().first { it.packId == "zebra" }
        assertThat(zebra.entryCount).isEqualTo(546)
    }

    @Test
    fun zebraCategoriesReachTheUiInConsequenceOrder() = runTest {
        // The pack orders categories by consequence, but the query sorts them
        // alphabetically and has no column carrying a pack's intended order. The
        // numeric prefixes are what make the two agree, so assert the order the user
        // actually sees rather than the order in the JSON.
        repository.syncBundledPacks()
        val names = repository.observeCategories(ZEBRA_VENDOR).first().map { it.name }

        assertThat(names.first()).isEqualTo("01 Recovery & Defaults")
        assertWithMessage("the programming lockout group must not be buried")
            .that(names[1]).isEqualTo("02 Programming Lock")
        assertThat(names[2]).isEqualTo("03 Host Output & Injection")

        val ranks = names.map { it.substringBefore(' ').toInt() }
        assertWithMessage("categories arrived out of order: $names")
            .that(ranks).isInOrder()

        val total = repository.observeCategories(ZEBRA_VENDOR).first().sumOf { it.entryCount }
        assertWithMessage("category counts should account for every entry")
            .that(total).isEqualTo(546)
    }

    @Test
    fun theWayBackIsPresentAndReachable() = runTest {
        // A pack that can lock a scanner out of programming has to carry the recovery
        // code too, and it has to be findable without already knowing its name.
        repository.syncBundledPacks()
        val defaults = repository.observeDefaults(ZEBRA_VENDOR).first()
        assertThat(defaults.map { it.name }).contains("Restore Defaults")
    }

    @Test
    fun searchFindsAnInjectionRelevantSetting() = runTest {
        repository.syncBundledPacks()
        val hits = repository.search("suffix").first()
        assertWithMessage("FTS should reach the prefix/suffix settings")
            .that(hits).isNotEmpty()
    }

    private companion object {
        const val ZEBRA_VENDOR = "Zebra / Symbol"
    }
}
