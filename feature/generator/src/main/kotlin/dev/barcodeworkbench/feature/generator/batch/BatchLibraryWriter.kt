package dev.barcodeworkbench.feature.generator.batch

import dev.barcodeworkbench.core.model.CodeRepository
import dev.barcodeworkbench.core.model.CodeSource
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SavedCode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a batch into a named catalogue library.
 *
 * Now goes through [CodeRepository] rather than the DAOs directly, which was the
 * Phase 3 deferral. No data migration was needed, because the escape source was
 * already being stored rather than expanded bytes.
 */
@Singleton
class BatchLibraryWriter @Inject constructor(
    private val repository: CodeRepository,
) {

    /**
     * Appends every encoded item to [libraryName], creating the library if absent.
     *
     * @return the number of entries written
     */
    suspend fun write(
        libraryName: String,
        encoded: List<BatchItemResult.Encoded>,
        inputMode: InputMode,
    ): Int {
        if (encoded.isEmpty()) return 0
        val libraryId = repository.libraryIdFor(libraryName)
        val now = System.currentTimeMillis()

        val codes = encoded.map { item ->
            SavedCode(
                id = 0,
                libraryId = libraryId,
                symbologyId = item.symbologyId,
                // Stores the authored escape source, so the entry stays editable and
                // re-renders exactly as it was written.
                payload = Payload(
                    bytes = item.entry.payload.toByteArray(Charsets.UTF_8),
                    mode = inputMode,
                    escapesEnabled = item.entry.payload.contains('\\'),
                ),
                label = item.entry.label,
                notes = "Imported from wordlist line ${item.entry.lineNumber}",
                source = CodeSource.IMPORTED,
                createdAt = now,
            )
        }
        return repository.saveAll(codes).size
    }
}
