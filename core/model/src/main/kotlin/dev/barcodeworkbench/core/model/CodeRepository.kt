package dev.barcodeworkbench.core.model

import kotlinx.coroutines.flow.Flow

/**
 * The single way features reach stored codes.
 *
 * Read operations return [Flow], backed by live database queries, so a screen never
 * has to remember to refresh after a write. That is the deliberate departure from
 * the app we reverse-engineered, which held the entire table in a list and re-sorted
 * it on every mutation.
 *
 * On filtering: library scoping and ordering are pushed into SQL. Text search runs
 * in memory over the scoped rows, because the payload is a BLOB and the search
 * deliberately matches its *escaped* rendering, so that looking for `\x1D` finds
 * codes containing a Group Separator. No SQL LIKE can do that. The cost is bounded
 * by one library rather than the whole database; if libraries ever grow large enough
 * for that to hurt, the fix is a stored searchable projection with an FTS index, not
 * a wider cache.
 */
interface CodeRepository {

    fun observeLibraries(): Flow<List<CodeLibrary>>

    fun observeLibrary(libraryId: Long): Flow<CodeLibrary?>

    fun observeCodes(
        libraryId: Long,
        filter: CodeFilter = CodeFilter(),
        order: CodeSortOrder = CodeSortOrder.NEWEST_FIRST,
    ): Flow<List<SavedCode>>

    fun observeCode(codeId: Long): Flow<SavedCode?>

    fun observeAllTags(): Flow<List<String>>

    suspend fun createLibrary(name: String): Long

    suspend fun renameLibrary(libraryId: Long, name: String)

    suspend fun deleteLibrary(libraryId: Long)

    /**
     * Persists a new library ordering.
     *
     * Takes the full ordered list rather than a single move, so the resulting order is
     * always internally consistent; applying moves one at a time can leave duplicate
     * or gapped sort values if a write fails midway.
     */
    suspend fun reorderLibraries(orderedIds: List<Long>)

    /** Finds a library by name, creating it when absent. */
    suspend fun libraryIdFor(name: String): Long

    suspend fun getCode(codeId: Long): SavedCode?

    suspend fun save(code: SavedCode): Long

    suspend fun saveAll(codes: List<SavedCode>): List<Long>

    suspend fun update(code: SavedCode)

    suspend fun delete(codeId: Long)

    suspend fun move(codeId: Long, toLibraryId: Long)

    /** Duplicates a code into another library, returning the new id. */
    suspend fun copy(codeId: Long, toLibraryId: Long): Long

    /**
     * Content fingerprints of everything stored, for de-duplicating an import.
     *
     * Returned as a set computed once rather than queried per entry, because an
     * import checks every incoming row against it.
     */
    suspend fun allFingerprints(): Set<String>

    /** Every library with its codes, for writing a backup. */
    suspend fun snapshot(): Map<String, List<SavedCode>>
}
