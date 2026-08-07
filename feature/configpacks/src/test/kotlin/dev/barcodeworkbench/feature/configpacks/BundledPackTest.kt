package dev.barcodeworkbench.feature.configpacks

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.barcodeworkbench.core.model.EscapeCodec
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.PayloadValidator
import dev.barcodeworkbench.core.model.SymbologyRegistry
import dev.barcodeworkbench.core.model.config.ConfigPackFormat
import dev.barcodeworkbench.core.model.config.VerificationStatus
import java.io.File
import org.junit.Test

/**
 * Validates the packs that ship with the app.
 *
 * A broken bundled pack would fail silently at runtime, because the loader skips packs
 * it cannot parse rather than crashing the screen. These assertions are what make that
 * forgiving behaviour safe.
 *
 * Reads the asset files from the source tree so it runs on the host with no device.
 */
class BundledPackTest {

    private val assetDir: File = sequenceOf(
        File("src/main/assets/configpacks"),
        File("feature/configpacks/src/main/assets/configpacks"),
    ).firstOrNull { it.isDirectory }
        ?: error("Could not locate bundled packs from ${File(".").absolutePath}")

    private fun packFiles(): List<File> =
        assetDir.listFiles { f: File -> f.extension == "json" && f.name != "index.json" }
            ?.sortedBy { it.name }
            ?: emptyList()

    @Test
    fun `index lists every pack file and nothing more`() {
        // A pack present but unlisted would never load; a listed pack that is missing
        // would be skipped silently.
        val index = File(assetDir, "index.json").readText()
        val listed = Regex("\"([A-Za-z0-9_-]+\\.json)\"").findAll(index)
            .map { it.groupValues[1] }.toSet()
        val present = packFiles().map { it.name }.toSet()
        assertThat(listed).isEqualTo(present)
    }

    @Test
    fun `every bundled pack parses and validates`() {
        val files = packFiles()
        assertThat(files).isNotEmpty()
        files.forEach { file ->
            val result = runCatching { ConfigPackFormat.parse(file.readText()) }
            assertWithMessage("${file.name} failed to parse: ${result.exceptionOrNull()?.message}")
                .that(result.isSuccess).isTrue()
        }
    }

    @Test
    fun `every entry names a symbology in the registry`() {
        packFiles().forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            ConfigPackFormat.toDomain(pack, bundled = true).forEach { entry ->
                assertWithMessage("${file.name}: ${entry.name}")
                    .that(SymbologyRegistry.find(entry.symbologyId)).isNotNull()
            }
        }
    }

    @Test
    fun `every entry's data passes validation for its own symbology`() {
        // Catches the mistake that matters most in seed data: a payload the chosen
        // symbology cannot represent, which would render nothing at runtime.
        packFiles().forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            ConfigPackFormat.toDomain(pack, bundled = true).forEach { entry ->
                val spec = SymbologyRegistry[entry.symbologyId]
                val mode = if (spec.supportsGs1 && entry.data.startsWith("[")) {
                    InputMode.GS1
                } else {
                    InputMode.UNICODE
                }
                val result = PayloadValidator.validate(spec, entry.data, mode)
                assertWithMessage(
                    "${file.name}: '${entry.name}' data '${entry.data}' -> " +
                        result.issues.joinToString { it.message },
                ).that(result.isValid).isTrue()
            }
        }
    }

    @Test
    fun `entries declaring escapes contain parseable escape sequences`() {
        packFiles().forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            ConfigPackFormat.toDomain(pack, bundled = true)
                .filter { it.escapesEnabled }
                .forEach { entry ->
                    val parsed = EscapeCodec.parse(entry.data)
                    assertWithMessage(
                        "${file.name}: '${entry.name}' escape errors: " +
                            parsed.errors.joinToString { it.message },
                    ).that(parsed.isValid).isTrue()
                }
        }
    }

    @Test
    fun `every entry states its provenance`() {
        // Enforced by the validator too, but asserted here so the bundled data is held
        // to the same standard imports are.
        packFiles().forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            pack.entries.forEach { entry ->
                assertWithMessage("${file.name}: ${entry.name}")
                    .that(entry.provenance).isNotEmpty()
            }
        }
    }

    @Test
    fun `the self-test pack contains no device commands`() {
        // The pack exists to check what a scanner decodes. If anything in it were
        // marked destructive or claimed to restore defaults, that would mean a real
        // programming code had been mixed in with the safe test patterns.
        val pack = ConfigPackFormat.parse(File(assetDir, "selftest.json").readText())
        pack.entries.forEach { entry ->
            assertWithMessage("${entry.name} must not be destructive")
                .that(entry.destructive).isFalse()
            assertWithMessage("${entry.name} must not claim to restore defaults")
                .that(entry.restoresDefaults).isFalse()
        }
    }

    @Test
    fun `self-test entries are all verified, since they are constructed payloads`() {
        val pack = ConfigPackFormat.parse(File(assetDir, "selftest.json").readText())
        ConfigPackFormat.toDomain(pack, bundled = true).forEach { entry ->
            assertThat(entry.verification).isEqualTo(VerificationStatus.VERIFIED)
            // Verified plus non-destructive means no confirmation gate, which is right
            // for a barcode that cannot change anything.
            assertThat(entry.requiresConfirmation).isFalse()
        }
    }

    @Test
    fun `no pack has two entries sharing a category and name`() {
        // config_entries carries a unique index on (pack_id, category, name) and the
        // DAO inserts with REPLACE, so a duplicate pair does not fail loudly -- the
        // second entry evicts the first and simply goes missing from the UI. The
        // Zebra guide reuses wording like "Inverse Autodetect" across five
        // symbologies, which silently cost 37 entries before this was caught.
        packFiles().forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            val duplicates = pack.entries
                .groupBy { it.category to it.name }
                .filterValues { it.size > 1 }
            assertWithMessage(
                "${file.name} would lose entries on load; duplicate (category, name): " +
                    duplicates.keys.joinToString { "${it.first}/${it.second}" },
            ).that(duplicates).isEmpty()
        }
    }

    @Test
    fun `entry payloads are unique within a pack for a given symbology`() {
        // The same payload twice in one symbology is the same barcode listed twice,
        // which usually means an extractor picked up a page where the guide reprints
        // a code in a summary table.
        //
        // Keyed on symbology as well as data because one payload in two symbologies
        // is a different barcode and a legitimate thing to ship: the self-test pack
        // carries the same GTIN as both GS1-128 and DataBar Expanded precisely so a
        // scanner's handling of the two can be compared.
        packFiles().forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            val repeated = pack.entries
                .groupBy { it.data to it.symbology }
                .filterValues { it.size > 1 }
            assertWithMessage(
                "${file.name} repeats payloads: " +
                    repeated.entries.joinToString { "${it.key.first}/${it.key.second}" },
            ).that(repeated).isEmpty()
        }
    }

    @Test
    fun `a pack with destructive entries also ships a recovery path`() {
        // Anything that can strand a device needs a documented way back in the same
        // pack. The Zebra lockout code is the case that matters: shipping Lock with
        // no route to defaults would leave a scanner unprogrammable.
        packFiles().forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            val entries = ConfigPackFormat.toDomain(pack, bundled = true)
            if (entries.none { it.destructive }) return@forEach
            assertWithMessage("${file.name} has destructive entries but no recovery path")
                .that(entries.any { it.restoresDefaults })
                .isTrue()
        }
    }

    @Test
    fun `vendor packs ship no unverified parameter codes`() {
        // Vendor packs may only carry entries whose data was checked against the
        // vendor's own documentation. Anything weaker stays out; a plausible-looking
        // parameter string that cannot be traced is worse than an empty pack.
        val vendorPacks = packFiles().filter { it.name != "selftest.json" }
        assertThat(vendorPacks).isNotEmpty()
        vendorPacks.forEach { file ->
            val pack = ConfigPackFormat.parse(file.readText())
            val untrustworthy = ConfigPackFormat.toDomain(pack, bundled = true)
                .filterNot { it.verification.isTrustworthy }
            assertWithMessage(
                "${file.name} ships ${untrustworthy.size} entries that are not verified",
            ).that(untrustworthy).isEmpty()
            assertWithMessage("${file.name} needs a description, whether or not it is empty")
                .that(pack.description).isNotNull()
        }
    }

    @Test
    fun `a pack missing provenance is rejected`() {
        val bad = """
            {
              "format_version": 1,
              "pack_id": "bad",
              "vendor": "Bad",
              "entries": [
                {"name": "X", "category": "c", "data": "D", "provenance": ""}
              ]
            }
        """.trimIndent()
        val failure = runCatching { ConfigPackFormat.parse(bad) }.exceptionOrNull()
        assertThat(failure).isNotNull()
    }

    @Test
    fun `a pack with an unsupported format version is rejected`() {
        val future = """
            {"format_version": 99, "pack_id": "f", "vendor": "F", "entries": []}
        """.trimIndent()
        val failure = runCatching { ConfigPackFormat.parse(future) }.exceptionOrNull()
        assertThat(failure).isNotNull()
    }
}
