package dev.barcodeworkbench.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.barcodeworkbench.core.database.entity.EntryEntity
import dev.barcodeworkbench.core.database.entity.EntryTagCrossRef
import dev.barcodeworkbench.core.database.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE library_id = :libraryId ORDER BY created_at DESC")
    fun observeByLibraryNewestFirst(libraryId: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE library_id = :libraryId ORDER BY created_at ASC")
    fun observeByLibraryOldestFirst(libraryId: Long): Flow<List<EntryEntity>>

    @Query(
        """
        SELECT * FROM entries
        WHERE library_id = :libraryId
        ORDER BY CASE WHEN label IS NULL OR label = '' THEN 1 ELSE 0 END,
                 label COLLATE NOCASE ASC
        """,
    )
    fun observeByLibraryByLabel(libraryId: Long): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    fun observe(id: Long): Flow<EntryEntity?>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun get(id: Long): EntryEntity?

    /**
     * Searches label, notes and the payload interpreted as text.
     *
     * CAST on a BLOB stops at the first NUL byte, so binary payloads are only
     * partially searchable. That is an accepted limit: text search over binary
     * content is not meaningful, and label and notes remain fully searchable.
     */
    @Query(
        """
        SELECT * FROM entries
        WHERE library_id = :libraryId
          AND (label LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
            OR CAST(payload AS TEXT) LIKE '%' || :query || '%')
        ORDER BY created_at DESC
        """,
    )
    fun search(libraryId: Long, query: String): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE library_id = :libraryId AND symbology_id IN (:symbologyIds) ORDER BY created_at DESC")
    fun observeFilteredBySymbology(libraryId: Long, symbologyIds: List<String>): Flow<List<EntryEntity>>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>): List<Long>

    @Update
    suspend fun update(entry: EntryEntity)

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE entries SET library_id = :toLibraryId WHERE id = :entryId")
    suspend fun moveToLibrary(entryId: Long, toLibraryId: Long)

    // ---- tags ----

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN entry_tags et ON et.tag_id = t.id
        WHERE et.entry_id = :entryId
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun observeTagsFor(entryId: Long): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun findTag(name: String): TagEntity?

    @Insert
    suspend fun insertTag(tag: TagEntity): Long

    @Insert
    suspend fun linkTag(ref: EntryTagCrossRef)

    @Query("DELETE FROM entry_tags WHERE entry_id = :entryId AND tag_id = :tagId")
    suspend fun unlinkTag(entryId: Long, tagId: Long)

    /** Creates the tag if needed, then links it. */
    @Transaction
    suspend fun attachTag(entryId: Long, tagName: String) {
        val existing = findTag(tagName)
        val tagId = existing?.id ?: insertTag(TagEntity(name = tagName))
        linkTag(EntryTagCrossRef(entryId = entryId, tagId = tagId))
    }

    @Query("SELECT * FROM entries")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE library_id = :libraryId")
    suspend fun getByLibrary(libraryId: Long): List<EntryEntity>

    /**
     * All entry-to-tag-name pairs for one library, fetched in a single query.
     *
     * Loading tags per entry would be an N+1 pattern; one join keeps a library
     * listing to two queries regardless of how many entries it holds.
     */
    @Query(
        """
        SELECT et.entry_id AS entryId, t.name AS tagName
        FROM entry_tags et
        INNER JOIN tags t ON t.id = et.tag_id
        INNER JOIN entries e ON e.id = et.entry_id
        WHERE e.library_id = :libraryId
        """,
    )
    fun observeTagLinksForLibrary(libraryId: Long): Flow<List<EntryTagLink>>

    @Query(
        """
        SELECT et.entry_id AS entryId, t.name AS tagName
        FROM entry_tags et
        INNER JOIN tags t ON t.id = et.tag_id
        """,
    )
    suspend fun getAllTagLinks(): List<EntryTagLink>

    @Query(
        """
        SELECT et.entry_id AS entryId, t.name AS tagName
        FROM entry_tags et
        INNER JOIN tags t ON t.id = et.tag_id
        WHERE et.entry_id = :entryId
        """,
    )
    fun observeTagLinksForEntry(entryId: Long): Flow<List<EntryTagLink>>

    @Query("DELETE FROM entry_tags WHERE entry_id = :entryId")
    suspend fun clearTagsFor(entryId: Long)

    /** Removes tags no longer referenced by any entry. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM entry_tags)")
    suspend fun pruneOrphanTags()
}
