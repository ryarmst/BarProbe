package dev.barcodeworkbench.feature.scanner.domain

import dev.barcodeworkbench.core.model.Dimension
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry

/** Why a candidate read was accepted or held back. */
enum class GateDecision {
    ACCEPT,

    /** A linear read seen only once; awaiting a confirming frame. */
    REJECT_UNSTABLE_LINEAR,

    /** Already captured during this continuous session. */
    REJECT_DUPLICATE_SESSION,

    /** Same value again within the debounce window. */
    REJECT_DEBOUNCED,
    ;

    val isAccepted: Boolean get() = this == ACCEPT
}

/**
 * Filters the stream of candidate reads coming off the camera.
 *
 * The core asymmetry is deliberate and is the single most useful thing carried
 * over from the app we reverse-engineered. A 2D symbol carries its own error
 * correction across a whole area, so a successful decode is strong evidence on its
 * own. A 1D symbol is read along a single scan line, where a smudge, a fold or
 * motion blur can yield a checksum-valid but wrong value from one frame. Requiring
 * a linear result to appear twice consistently within a short window costs a few
 * hundred milliseconds and removes most of that class of misread.
 *
 * On top of that sit two independent filters: session-level de-duplication so
 * continuous scanning does not record the same label repeatedly while it stays in
 * frame, and a debounce so a value re-entering the frame shortly after being
 * captured is not counted twice.
 *
 * Not thread-safe; the analyzer calls it from a single executor.
 */
class ScanStabilityGate(
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val linearStabilityWindowMs: Long = DEFAULT_LINEAR_WINDOW_MS,
    private val requiredLinearAgreements: Int = DEFAULT_LINEAR_AGREEMENTS,
    /**
     * When true the same symbol may be captured repeatedly, which is what a
     * counting workflow needs -- scan one item five times to record five of them.
     * Session de-duplication is skipped in that mode and the debounce becomes the
     * only thing standing between the user and thirty captures a second.
     */
    private val allowDuplicates: Boolean = false,
) {

    private val sessionKeys = mutableSetOf<String>()

    private var lastAcceptedKey: String? = null
    private var lastAcceptedAt: Long = 0

    private var candidateKey: String? = null
    private var candidateAt: Long = 0
    private var candidateCount: Int = 0

    /**
     * Total accepted reads. Distinct from [sessionCount] because in duplicate-
     * allowing mode the same symbol contributes more than once.
     */
    private var acceptedCount: Int = 0

    fun evaluate(symbology: SymbologyId?, bytes: ByteArray): GateDecision {
        val now = clock()
        val key = keyOf(symbology, bytes)

        if (isLinear(symbology) && !hasStabilised(key, now)) {
            return GateDecision.REJECT_UNSTABLE_LINEAR
        }

        if (!allowDuplicates && key in sessionKeys) {
            return GateDecision.REJECT_DUPLICATE_SESSION
        }

        if (key == lastAcceptedKey && now - lastAcceptedAt < debounceMs) {
            return GateDecision.REJECT_DEBOUNCED
        }

        lastAcceptedKey = key
        lastAcceptedAt = now
        sessionKeys += key
        acceptedCount++
        resetCandidate()
        return GateDecision.ACCEPT
    }

    /**
     * Clears session memory. Called when the user starts a new batch, so the same
     * labels can be scanned again intentionally.
     */
    fun resetSession() {
        sessionKeys.clear()
        lastAcceptedKey = null
        lastAcceptedAt = 0
        acceptedCount = 0
        resetCandidate()
    }

    /** Distinct symbols captured this session. */
    val sessionCount: Int get() = sessionKeys.size

    /** Accepted reads this session, counting repeats when duplicates are allowed. */
    val totalAccepted: Int get() = acceptedCount

    /**
     * Tracks repeat sightings of a linear candidate.
     *
     * A different value, or the same value after the window has lapsed, restarts
     * the count rather than accumulating toward acceptance.
     */
    private fun hasStabilised(key: String, now: Long): Boolean {
        val withinWindow = key == candidateKey && now - candidateAt <= linearStabilityWindowMs
        if (withinWindow) {
            candidateCount++
        } else {
            candidateKey = key
            candidateCount = 1
        }
        candidateAt = now
        return candidateCount >= requiredLinearAgreements
    }

    private fun resetCandidate() {
        candidateKey = null
        candidateAt = 0
        candidateCount = 0
    }

    private fun isLinear(symbology: SymbologyId?): Boolean {
        // An unrecognised format is treated as linear, the more cautious choice:
        // requiring confirmation costs a moment, accepting a misread does not
        // announce itself.
        val spec = symbology?.let { SymbologyRegistry.find(it) } ?: return true
        return spec.dimension == Dimension.LINEAR
    }

    /**
     * Keys on the raw bytes rather than decoded text, so two payloads that render
     * identically but differ in their bytes are not conflated.
     */
    private fun keyOf(symbology: SymbologyId?, bytes: ByteArray): String =
        buildString {
            append(symbology?.name ?: "UNKNOWN")
            append(':')
            append(bytes.size)
            append(':')
            bytes.forEach { append("%02x".format(it)) }
        }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 900L
        const val DEFAULT_LINEAR_WINDOW_MS = 800L
        const val DEFAULT_LINEAR_AGREEMENTS = 2
    }
}
