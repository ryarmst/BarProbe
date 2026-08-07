package dev.barcodeworkbench.feature.catalogue.backup

import dev.barcodeworkbench.core.model.CodeRepository
import dev.barcodeworkbench.core.model.backup.BackupCodec
import dev.barcodeworkbench.core.model.backup.BackupEnvelope
import dev.barcodeworkbench.core.model.backup.ImportDecision
import dev.barcodeworkbench.core.model.backup.ImportPlan
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ImportOutcome(
    val imported: Int,
    val skippedDuplicates: Int,
    val skippedUnknown: Int,
)

/**
 * Reads and writes backup files.
 *
 * The codec handles the format; this adds gzip framing, file IO and the database
 * work. The split keeps the format rules in a pure module where they are
 * host-testable, which is where the interesting logic lives.
 */
@Singleton
class BackupManager @Inject constructor(
    private val repository: CodeRepository,
) {

    suspend fun export(out: OutputStream, appVersion: String) {
        val snapshot = repository.snapshot()
        val json = BackupCodec.encode(
            libraries = snapshot,
            exportedAt = utcTimestamp(),
            appVersion = appVersion,
        )
        // Gzip because the JSON is highly repetitive and a library of a few thousand
        // codes compresses to a small fraction of its size.
        GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Parses and verifies a backup without writing anything.
     *
     * Separate from [applyPlan] on purpose: the user sees what an import will do, and
     * a damaged file is rejected, before the database is touched.
     */
    suspend fun preview(input: InputStream, deduplicate: Boolean = true): Pair<BackupEnvelope, ImportPlan> {
        val json = GZIPInputStream(input).use { it.readBytes().toString(Charsets.UTF_8) }
        val envelope = BackupCodec.decode(json)
        val existing = if (deduplicate) repository.allFingerprints() else emptySet()
        return envelope to BackupCodec.plan(envelope, existing, deduplicate)
    }

    /**
     * Writes the entries a plan approved.
     *
     * Cancellation is checked between entries, so a large import can be abandoned
     * partway without waiting for it to finish.
     */
    suspend fun applyPlan(
        plan: ImportPlan,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportOutcome {
        val approved = plan.toImport
        val libraryIds = mutableMapOf<String, Long>()
        var written = 0

        approved.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            val libraryId = libraryIds.getOrPut(item.libraryName) {
                repository.libraryIdFor(item.libraryName)
            }
            val code = BackupCodec.toSavedCode(item.entry, libraryId)
            if (code != null) {
                repository.save(code)
                written++
            }
            onProgress(index + 1, approved.size)
        }

        return ImportOutcome(
            imported = written,
            skippedDuplicates = plan.items.count { it.decision == ImportDecision.SKIP_DUPLICATE },
            skippedUnknown = plan.items.count {
                it.decision == ImportDecision.SKIP_UNKNOWN_SYMBOLOGY
            },
        )
    }

    fun suggestedFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        return "barcode-workbench-$stamp.bkp"
    }

    private fun utcTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    companion object {
        const val MIME_TYPE = "application/octet-stream"
    }
}
