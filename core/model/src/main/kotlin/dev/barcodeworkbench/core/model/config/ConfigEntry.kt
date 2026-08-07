package dev.barcodeworkbench.core.model.config

import dev.barcodeworkbench.core.model.SymbologyId

/**
 * How much confidence there is that an entry's data is correct.
 *
 * This is a required field rather than optional metadata, and it is surfaced in the
 * UI on every entry. A programming barcode reconfigures real hardware, so the
 * difference between "checked against the vendor manual" and "someone posted this on
 * a forum" is something the user must be able to see before scanning, not something
 * for the app to quietly average away.
 */
enum class VerificationStatus {
    /** Cross-checked against the vendor's own published documentation. */
    VERIFIED,

    /** Reported to work but not confirmed against primary documentation. */
    COMMUNITY_REPORTED,

    /**
     * Structural example only. Not a real parameter code and must never be presented
     * as one; exists to demonstrate the pack format.
     */
    EXAMPLE_ONLY,

    /** Imported from a user pack that did not state a status. */
    UNSPECIFIED,
    ;

    /** Whether scanning this at a device could reasonably be expected to work. */
    val isTrustworthy: Boolean
        get() = this == VERIFIED || this == COMMUNITY_REPORTED
}

/**
 * One programming barcode.
 *
 * [data] holds escape source rather than expanded bytes, exactly as the generator
 * does, because parameter strings frequently contain control characters that cannot
 * be written literally.
 */
data class ConfigEntry(
    val id: Long = 0,
    /** Pack that supplied this entry, e.g. "zebra". */
    val packId: String,
    val vendor: String,
    val category: String,
    val subcategory: String? = null,
    val name: String,
    val description: String? = null,
    val symbologyId: SymbologyId,
    val data: String,
    val escapesEnabled: Boolean = false,
    /** Where the value came from; shown so the user can cross-check it. */
    val provenance: String,
    val verification: VerificationStatus,
    /** Extra caution shown before the symbol is displayed. */
    val warning: String? = null,
    /**
     * Changes device state in a way that is disruptive or hard to undo: a factory
     * reset, an interface change, a communications setting.
     */
    val destructive: Boolean = false,
    /** Returns a device to its defaults. Surfaced first, as the recovery path. */
    val restoresDefaults: Boolean = false,
    /** True when the pack shipped with the app rather than being imported. */
    val bundled: Boolean = true,
) {
    /**
     * Whether the UI must interrupt before rendering a scannable symbol.
     *
     * Untrustworthy entries are gated for the same reason destructive ones are: a
     * wrong parameter string and a disruptive one both leave the user with a
     * misconfigured device.
     */
    val requiresConfirmation: Boolean
        get() = destructive || !verification.isTrustworthy

    val path: String
        get() = listOfNotNull(vendor, category, subcategory).joinToString(" / ")
}

/** A folder within a vendor's pack. */
data class ConfigCategory(
    val vendor: String,
    val name: String,
    val entryCount: Int,
    /** True when the category holds the vendor's restore-to-defaults entries. */
    val isDefaults: Boolean = false,
)

/** A vendor's pack as a whole. */
data class ConfigPackInfo(
    val packId: String,
    val vendor: String,
    val description: String? = null,
    val entryCount: Int = 0,
    val bundled: Boolean = true,
    /** Pack format version, so an incompatible import can be rejected clearly. */
    val formatVersion: Int = 1,
)
