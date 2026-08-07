package dev.barcodeworkbench.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-created, named collection of codes. */
@Entity(
    tableName = "libraries",
    indices = [Index(value = ["name"], unique = true)],
)
data class LibraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

/** Where an entry came from. */
enum class EntrySource { GENERATED, SCANNED, IMPORTED, CONFIG_PACK }

/**
 * One saved code.
 *
 * The payload is a BLOB rather than TEXT, deliberately. Control characters,
 * embedded NUL and arbitrary high bytes are all valid barcode content and would
 * be corrupted by a text column. Storing the encode options alongside means any
 * entry can be re-rendered later at any size or format without loss.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("library_id"),
        Index("symbology_id"),
        Index("created_at"),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "library_id") val libraryId: Long,
    /** [dev.barcodeworkbench.core.model.SymbologyId] name. */
    @ColumnInfo(name = "symbology_id") val symbologyId: String,
    @ColumnInfo(name = "payload", typeAffinity = ColumnInfo.BLOB) val payload: ByteArray,
    /** [dev.barcodeworkbench.core.model.InputMode] name. */
    @ColumnInfo(name = "payload_mode") val payloadMode: String,
    val eci: Int? = null,
    @ColumnInfo(name = "escapes_enabled") val escapesEnabled: Boolean = false,
    /** Serialised encode options, so the symbol re-renders identically. */
    @ColumnInfo(name = "options_json") val optionsJson: String? = null,
    val label: String? = null,
    val notes: String? = null,
    val source: String = EntrySource.GENERATED.name,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    // ByteArray needs structural equality written out by hand.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntryEntity) return false
        return id == other.id &&
            libraryId == other.libraryId &&
            symbologyId == other.symbologyId &&
            payload.contentEquals(other.payload) &&
            payloadMode == other.payloadMode &&
            eci == other.eci &&
            escapesEnabled == other.escapesEnabled &&
            optionsJson == other.optionsJson &&
            label == other.label &&
            notes == other.notes &&
            source == other.source &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + libraryId.hashCode()
        result = 31 * result + symbologyId.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + payloadMode.hashCode()
        result = 31 * result + (eci ?: 0)
        result = 31 * result + escapesEnabled.hashCode()
        result = 31 * result + (optionsJson?.hashCode() ?: 0)
        result = 31 * result + (label?.hashCode() ?: 0)
        result = 31 * result + (notes?.hashCode() ?: 0)
        result = 31 * result + source.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entry_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class EntryTagCrossRef(
    @ColumnInfo(name = "entry_id") val entryId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
