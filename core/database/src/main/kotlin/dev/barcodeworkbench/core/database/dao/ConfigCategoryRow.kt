package dev.barcodeworkbench.core.database.dao

/** Aggregated category row for a vendor's folder listing. */
data class ConfigCategoryRow(
    val vendor: String,
    val category: String,
    val entryCount: Int,
    /** 1 when any entry in the category restores defaults. */
    val hasDefaults: Int,
)
