package dev.barcodeworkbench.core.database

import dev.barcodeworkbench.core.database.dao.EntryDao
import dev.barcodeworkbench.core.database.dao.EntryTagLink
import dev.barcodeworkbench.core.database.dao.LibraryDao
import dev.barcodeworkbench.core.database.entity.EntryEntity
import dev.barcodeworkbench.core.database.entity.LibraryEntity
import dev.barcodeworkbench.core.model.CodeFilter
import dev.barcodeworkbench.core.model.CodeLibrary
import dev.barcodeworkbench.core.model.CodeRepository
import dev.barcodeworkbench.core.model.CodeSortOrder
import dev.barcodeworkbench.core.model.CodeSource
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.SavedCode
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.backup.BackupCodec
import dev.barcodeworkbench.core.model.sortedBy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Room-backed [CodeRepository].
 *
 * Tags live in a cross-reference table, so a listing joins them in a second query
 * and combines the two flows. That keeps a library listing at two queries no matter
 * how many entries it contains, rather than one per entry.
 */
@Singleton
class RoomCodeRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val entryDao: EntryDao,
) : CodeRepository {

    override fun observeLibraries(): Flow<List<CodeLibrary>> =
        libraryDao.observeAllWithCounts().map { rows ->
            rows.map { row ->
                CodeLibrary(
                    id = row.id,
                    name = row.name,
                    createdAt = row.createdAt,
                    sortOrder = row.sortOrder,
                    entryCount = row.entryCount,
                )
            }
        }

    override fun observeLibrary(libraryId: Long): Flow<CodeLibrary?> =
        libraryDao.observe(libraryId).map { it?.toDomain() }

    override fun observeCodes(
        libraryId: Long,
        filter: CodeFilter,
        order: CodeSortOrder,
    ): Flow<List<SavedCode>> {
        val entries = when (order) {
            // Ordering that SQL can express is pushed down; label ordering needs the
            // domain's fallback-to-payload rule, so it is applied after mapping.
            CodeSortOrder.OLDEST_FIRST -> entryDao.observeByLibraryOldestFirst(libraryId)
            else -> entryDao.observeByLibraryNewestFirst(libraryId)
        }
        val tagLinks = entryDao.observeTagLinksForLibrary(libraryId)

        return combine(entries, tagLinks) { rows, links ->
            val tagsByEntry = links.groupTags()
            rows
                .mapNotNull { it.toDomain(tagsByEntry[it.id].orEmpty()) }
                .filter(filter::matches)
                .let { if (order == CodeSortOrder.NEWEST_FIRST) it else it.sortedBy(order) }
        }
    }

    override fun observeCode(codeId: Long): Flow<SavedCode?> =
        combine(
            entryDao.observe(codeId),
            entryDao.observeTagLinksForEntry(codeId),
        ) { entry, links ->
            entry?.toDomain(links.map { it.tagName }.toSet())
        }

    override fun observeAllTags(): Flow<List<String>> =
        entryDao.observeAllTags().map { tags -> tags.map { it.name } }

    override suspend fun createLibrary(name: String): Long =
        libraryDao.insert(
            LibraryEntity(name = name, createdAt = System.currentTimeMillis()),
        )

    override suspend fun renameLibrary(libraryId: Long, name: String) {
        val existing = libraryDao.get(libraryId) ?: return
        libraryDao.update(existing.copy(name = name))
    }

    override suspend fun deleteLibrary(libraryId: Long) {
        // Entries cascade from the foreign key; tags may be left unreferenced.
        libraryDao.deleteById(libraryId)
        entryDao.pruneOrphanTags()
    }

    override suspend fun reorderLibraries(orderedIds: List<Long>) {
        val byId = libraryDao.getAll().associateBy { it.id }
        orderedIds.forEachIndexed { index, id ->
            byId[id]?.let { libraryDao.update(it.copy(sortOrder = index)) }
        }
    }

    override suspend fun libraryIdFor(name: String): Long =
        libraryDao.findByName(name)?.id ?: createLibrary(name)

    override suspend fun getCode(codeId: Long): SavedCode? {
        val entry = entryDao.get(codeId) ?: return null
        val tags = entryDao.getAllTagLinks()
            .filter { it.entryId == codeId }
            .map { it.tagName }
            .toSet()
        return entry.toDomain(tags)
    }

    override suspend fun save(code: SavedCode): Long {
        val id = entryDao.insert(code.toEntity())
        code.tags.forEach { entryDao.attachTag(id, it) }
        return id
    }

    override suspend fun saveAll(codes: List<SavedCode>): List<Long> {
        val ids = entryDao.insertAll(codes.map { it.toEntity() })
        // Tag links need the generated ids, so they are attached after insertion.
        ids.forEachIndexed { index, id ->
            codes[index].tags.forEach { entryDao.attachTag(id, it) }
        }
        return ids
    }

    override suspend fun update(code: SavedCode) {
        entryDao.update(code.toEntity(includeId = true))
        // Replaced wholesale rather than diffed: the set is small and a diff would be
        // more code for no measurable gain.
        entryDao.clearTagsFor(code.id)
        code.tags.forEach { entryDao.attachTag(code.id, it) }
        entryDao.pruneOrphanTags()
    }

    override suspend fun delete(codeId: Long) {
        entryDao.deleteById(codeId)
        entryDao.pruneOrphanTags()
    }

    override suspend fun move(codeId: Long, toLibraryId: Long) {
        entryDao.moveToLibrary(codeId, toLibraryId)
    }

    override suspend fun copy(codeId: Long, toLibraryId: Long): Long {
        val original = getCode(codeId) ?: return -1
        return save(
            original.copy(
                id = 0,
                libraryId = toLibraryId,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun allFingerprints(): Set<String> {
        val libraryNames = libraryDao.getAll().associate { it.id to it.name }
        val tagsByEntry = entryDao.getAllTagLinks().groupTags()
        return entryDao.getAll().mapNotNull { entry ->
            val libraryName = libraryNames[entry.libraryId] ?: return@mapNotNull null
            val code = entry.toDomain(tagsByEntry[entry.id].orEmpty()) ?: return@mapNotNull null
            BackupCodec.fingerprintOf(libraryName, code)
        }.toSet()
    }

    override suspend fun snapshot(): Map<String, List<SavedCode>> {
        val tagsByEntry = entryDao.getAllTagLinks().groupTags()
        return libraryDao.getAll().associate { library ->
            library.name to entryDao.getByLibrary(library.id)
                .mapNotNull { it.toDomain(tagsByEntry[it.id].orEmpty()) }
        }
    }

    // ---- mapping ----

    private fun List<EntryTagLink>.groupTags(): Map<Long, Set<String>> =
        groupBy { it.entryId }.mapValues { (_, links) -> links.map { it.tagName }.toSet() }

    private fun LibraryEntity.toDomain() = CodeLibrary(
        id = id,
        name = name,
        createdAt = createdAt,
        sortOrder = sortOrder,
    )

    /**
     * Maps a row to the domain type.
     *
     * Returns null when the stored symbology name no longer resolves, which happens
     * if a format is removed from the registry. Dropping the row from a listing is
     * preferable to crashing the screen, and the data itself is left untouched so a
     * later release can still read it.
     */
    private fun EntryEntity.toDomain(tags: Set<String>): SavedCode? {
        val symbology = runCatching { SymbologyId.valueOf(symbologyId) }.getOrNull()
            ?: return null
        val mode = runCatching { InputMode.valueOf(payloadMode) }
            .getOrDefault(InputMode.UNICODE)
        val entrySource = runCatching { CodeSource.valueOf(source) }
            .getOrDefault(CodeSource.GENERATED)
        return SavedCode(
            id = id,
            libraryId = libraryId,
            symbologyId = symbology,
            payload = Payload(payload, mode, eci, escapesEnabled),
            label = label,
            notes = notes,
            tags = tags,
            source = entrySource,
            createdAt = createdAt,
        )
    }

    private fun SavedCode.toEntity(includeId: Boolean = false) = EntryEntity(
        id = if (includeId) id else 0,
        libraryId = libraryId,
        symbologyId = symbologyId.name,
        payload = payload.bytes,
        payloadMode = payload.mode.name,
        eci = payload.eci,
        escapesEnabled = payload.escapesEnabled,
        label = label,
        notes = notes,
        source = source.name,
        createdAt = if (createdAt > 0) createdAt else System.currentTimeMillis(),
    )
}
