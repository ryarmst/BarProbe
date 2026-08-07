package dev.barcodeworkbench.core.database.dao

/** Projection of a library plus how many entries it holds. */
data class LibraryWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val sortOrder: Int,
    val entryCount: Int,
)
