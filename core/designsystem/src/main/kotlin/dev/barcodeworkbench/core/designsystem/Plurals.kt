package dev.barcodeworkbench.core.designsystem

/**
 * Minimal English pluralisation for counted nouns.
 *
 * Android's plurals resources would be the general answer, but they live per-module
 * and these counts are assembled inside sentences built in Kotlin. This keeps
 * "1 code" from rendering as "1 codes", which reads as a bug even though nothing is
 * broken.
 */
fun plural(count: Int, singular: String, plural: String = "${singular}s"): String =
    if (count == 1) singular else plural

/** "1 code" / "3 codes" */
fun counted(count: Int, singular: String, pluralForm: String = "${singular}s"): String =
    "$count ${plural(count, singular, pluralForm)}"
