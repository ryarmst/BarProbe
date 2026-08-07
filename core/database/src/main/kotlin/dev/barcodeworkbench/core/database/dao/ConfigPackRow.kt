package dev.barcodeworkbench.core.database.dao

/** A pack with its entry count, joined in one query. */
data class ConfigPackRow(
    val packId: String,
    val vendor: String,
    val description: String?,
    val formatVersion: Int,
    val bundled: Boolean,
    val entryCount: Int,
)
