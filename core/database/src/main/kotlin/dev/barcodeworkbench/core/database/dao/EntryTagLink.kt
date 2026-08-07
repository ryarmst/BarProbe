package dev.barcodeworkbench.core.database.dao

/** Projection row joining an entry to one of its tag names. */
data class EntryTagLink(
    val entryId: Long,
    val tagName: String,
)
