package dev.barcodeworkbench.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.barcodeworkbench.core.database.entity.LibraryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Queries return [Flow] so the UI observes the database directly.
 *
 * This is the deliberate departure from the app we reverse-engineered, which
 * loaded the whole table into an in-memory list and re-sorted it on every
 * mutation. Live queries scale to large libraries and remove the class of bug
 * where a screen forgets to refresh after a write.
 */
@Dao
interface LibraryDao {

    @Query("SELECT * FROM libraries ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM libraries WHERE id = :id")
    fun observe(id: Long): Flow<LibraryEntity?>

    @Query("SELECT * FROM libraries WHERE id = :id")
    suspend fun get(id: Long): LibraryEntity?

    @Query("SELECT * FROM libraries WHERE name = :name")
    suspend fun findByName(name: String): LibraryEntity?

    @Query("SELECT COUNT(*) FROM entries WHERE library_id = :libraryId")
    fun observeEntryCount(libraryId: Long): Flow<Int>

    @Query("SELECT * FROM libraries ORDER BY sort_order ASC, name ASC")
    suspend fun getAll(): List<LibraryEntity>

    /**
     * Libraries with their entry counts in one query.
     *
     * A count per library would be an N+1 pattern on the list screen.
     */
    @Query(
        """
        SELECT l.id AS id, l.name AS name, l.created_at AS createdAt,
               l.sort_order AS sortOrder,
               (SELECT COUNT(*) FROM entries e WHERE e.library_id = l.id) AS entryCount
        FROM libraries l
        ORDER BY l.sort_order ASC, l.name ASC
        """,
    )
    fun observeAllWithCounts(): Flow<List<LibraryWithCount>>

    @Insert
    suspend fun insert(library: LibraryEntity): Long

    @Update
    suspend fun update(library: LibraryEntity)

    @Delete
    suspend fun delete(library: LibraryEntity)

    @Query("DELETE FROM libraries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
