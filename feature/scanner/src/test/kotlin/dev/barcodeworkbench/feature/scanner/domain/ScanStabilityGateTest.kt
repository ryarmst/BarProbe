package dev.barcodeworkbench.feature.scanner.domain

import com.google.common.truth.Truth.assertThat
import dev.barcodeworkbench.core.model.SymbologyId
import org.junit.Test

class ScanStabilityGateTest {

    /** Manually advanced clock so timing behaviour is deterministic. */
    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun gate(clock: FakeClock) = ScanStabilityGate(clock = clock)

    private fun bytes(text: String) = text.toByteArray()

    // ---- 2D symbols are accepted immediately ----

    @Test
    fun `matrix symbol is accepted on first sight`() {
        // A QR code carries error correction across its whole area, so one clean
        // decode is already strong evidence.
        val clock = FakeClock()
        val g = gate(clock)
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("hello")))
            .isEqualTo(GateDecision.ACCEPT)
    }

    @Test
    fun `every matrix symbology is accepted on first sight`() {
        listOf(
            SymbologyId.QR_CODE,
            SymbologyId.DATA_MATRIX,
            SymbologyId.AZTEC,
            SymbologyId.PDF417,
            SymbologyId.MAXICODE,
            SymbologyId.MICRO_QR,
        ).forEach { id ->
            val g = gate(FakeClock())
            assertThat(g.evaluate(id, bytes("payload-$id")))
                .isEqualTo(GateDecision.ACCEPT)
        }
    }

    // ---- 1D symbols need a confirming frame ----

    @Test
    fun `linear symbol is held back on first sight`() {
        // A single scan line can produce a checksum-valid but wrong read.
        val g = gate(FakeClock())
        assertThat(g.evaluate(SymbologyId.CODE_128, bytes("ABC123")))
            .isEqualTo(GateDecision.REJECT_UNSTABLE_LINEAR)
    }

    @Test
    fun `linear symbol is accepted on the confirming frame`() {
        val clock = FakeClock()
        val g = gate(clock)
        g.evaluate(SymbologyId.CODE_128, bytes("ABC123"))
        clock.advance(100)
        assertThat(g.evaluate(SymbologyId.CODE_128, bytes("ABC123")))
            .isEqualTo(GateDecision.ACCEPT)
    }

    @Test
    fun `linear agreement lapses once the window passes`() {
        // Two sightings far apart are not corroboration; they could be two
        // different misreads.
        val clock = FakeClock()
        val g = gate(clock)
        g.evaluate(SymbologyId.EAN_13, bytes("0123456789012"))
        clock.advance(ScanStabilityGate.DEFAULT_LINEAR_WINDOW_MS + 1)
        assertThat(g.evaluate(SymbologyId.EAN_13, bytes("0123456789012")))
            .isEqualTo(GateDecision.REJECT_UNSTABLE_LINEAR)
    }

    @Test
    fun `a differing linear read restarts the agreement count`() {
        // The exact misread scenario the gate exists for: a flickering value must
        // not accumulate agreements across different values.
        val clock = FakeClock()
        val g = gate(clock)
        g.evaluate(SymbologyId.CODE_128, bytes("AAA"))
        clock.advance(50)
        g.evaluate(SymbologyId.CODE_128, bytes("BBB"))
        clock.advance(50)
        // "AAA" has been seen once before but the count restarted on "BBB".
        assertThat(g.evaluate(SymbologyId.CODE_128, bytes("AAA")))
            .isEqualTo(GateDecision.REJECT_UNSTABLE_LINEAR)
    }

    @Test
    fun `alternating linear values are never accepted`() {
        val clock = FakeClock()
        val g = gate(clock)
        repeat(10) { i ->
            val decision = g.evaluate(
                SymbologyId.CODE_128,
                bytes(if (i % 2 == 0) "AAA" else "BBB"),
            )
            assertThat(decision).isEqualTo(GateDecision.REJECT_UNSTABLE_LINEAR)
            clock.advance(50)
        }
        assertThat(g.sessionCount).isEqualTo(0)
    }

    // ---- session de-duplication ----

    @Test
    fun `a captured symbol is not recorded twice in one session`() {
        // While a label stays in frame it decodes on every frame; the session set
        // stops it being counted repeatedly.
        val clock = FakeClock()
        val g = gate(clock)
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("x"))).isEqualTo(GateDecision.ACCEPT)
        clock.advance(5_000)
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("x")))
            .isEqualTo(GateDecision.REJECT_DUPLICATE_SESSION)
    }

    @Test
    fun `session reset allows the same symbol again`() {
        val clock = FakeClock()
        val g = gate(clock)
        g.evaluate(SymbologyId.QR_CODE, bytes("x"))
        g.resetSession()
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("x"))).isEqualTo(GateDecision.ACCEPT)
    }

    @Test
    fun `session count tracks distinct accepted symbols`() {
        val clock = FakeClock()
        val g = gate(clock)
        g.evaluate(SymbologyId.QR_CODE, bytes("a"))
        g.evaluate(SymbologyId.QR_CODE, bytes("b"))
        g.evaluate(SymbologyId.QR_CODE, bytes("a"))
        assertThat(g.sessionCount).isEqualTo(2)
    }

    @Test
    fun `reset clears the session count`() {
        val g = gate(FakeClock())
        g.evaluate(SymbologyId.QR_CODE, bytes("a"))
        g.resetSession()
        assertThat(g.sessionCount).isEqualTo(0)
    }

    // ---- keying ----

    @Test
    fun `the same value under different symbologies is distinct`() {
        val g = gate(FakeClock())
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("dup"))).isEqualTo(GateDecision.ACCEPT)
        assertThat(g.evaluate(SymbologyId.DATA_MATRIX, bytes("dup")))
            .isEqualTo(GateDecision.ACCEPT)
    }

    @Test
    fun `payloads are keyed on bytes not decoded text`() {
        // Two byte sequences can render to the same-looking string; conflating them
        // would silently drop a distinct scan.
        val g = gate(FakeClock())
        assertThat(g.evaluate(SymbologyId.DATA_MATRIX, byteArrayOf(0x41, 0x00, 0x42)))
            .isEqualTo(GateDecision.ACCEPT)
        assertThat(g.evaluate(SymbologyId.DATA_MATRIX, byteArrayOf(0x41)))
            .isEqualTo(GateDecision.ACCEPT)
    }

    @Test
    fun `binary payloads with high bytes are handled`() {
        val g = gate(FakeClock())
        val payload = ByteArray(256) { it.toByte() }
        assertThat(g.evaluate(SymbologyId.DATA_MATRIX, payload)).isEqualTo(GateDecision.ACCEPT)
        assertThat(g.evaluate(SymbologyId.DATA_MATRIX, payload))
            .isEqualTo(GateDecision.REJECT_DUPLICATE_SESSION)
    }

    @Test
    fun `empty payload is keyed consistently`() {
        val g = gate(FakeClock())
        assertThat(g.evaluate(SymbologyId.QR_CODE, ByteArray(0))).isEqualTo(GateDecision.ACCEPT)
        assertThat(g.evaluate(SymbologyId.QR_CODE, ByteArray(0)))
            .isEqualTo(GateDecision.REJECT_DUPLICATE_SESSION)
    }

    // ---- debounce ----

    @Test
    fun `duplicates allowed mode relies on the debounce`() {
        // A counting workflow deliberately re-scans the same symbol, so session
        // de-duplication is off and the debounce is what prevents thirty captures
        // a second.
        val clock = FakeClock()
        val g = ScanStabilityGate(clock = clock, allowDuplicates = true)
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("x"))).isEqualTo(GateDecision.ACCEPT)
        clock.advance(10)
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("x")))
            .isEqualTo(GateDecision.REJECT_DEBOUNCED)
    }

    @Test
    fun `duplicates allowed accepts the same symbol again once the debounce lapses`() {
        val clock = FakeClock()
        val g = ScanStabilityGate(clock = clock, allowDuplicates = true)
        g.evaluate(SymbologyId.QR_CODE, bytes("x"))
        clock.advance(ScanStabilityGate.DEFAULT_DEBOUNCE_MS + 1)
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("x"))).isEqualTo(GateDecision.ACCEPT)
    }

    @Test
    fun `duplicates allowed counts repeats while distinct count stays at one`() {
        val clock = FakeClock()
        val g = ScanStabilityGate(clock = clock, allowDuplicates = true)
        repeat(4) {
            g.evaluate(SymbologyId.QR_CODE, bytes("x"))
            clock.advance(ScanStabilityGate.DEFAULT_DEBOUNCE_MS + 1)
        }
        assertThat(g.totalAccepted).isEqualTo(4)
        assertThat(g.sessionCount).isEqualTo(1)
    }

    @Test
    fun `default mode rejects duplicates rather than debouncing them`() {
        // With de-duplication on, the session check fires first and the debounce is
        // never reached for an already-captured symbol.
        val clock = FakeClock()
        val g = ScanStabilityGate(clock = clock)
        g.evaluate(SymbologyId.QR_CODE, bytes("x"))
        clock.advance(10)
        assertThat(g.evaluate(SymbologyId.QR_CODE, bytes("x")))
            .isEqualTo(GateDecision.REJECT_DUPLICATE_SESSION)
    }

    // ---- unknown formats ----

    @Test
    fun `unknown symbology is treated as linear`() {
        // Caution is the right default: requiring confirmation costs a moment,
        // while accepting a misread gives no signal that anything went wrong.
        val clock = FakeClock()
        val g = gate(clock)
        assertThat(g.evaluate(null, bytes("mystery")))
            .isEqualTo(GateDecision.REJECT_UNSTABLE_LINEAR)
        clock.advance(50)
        assertThat(g.evaluate(null, bytes("mystery"))).isEqualTo(GateDecision.ACCEPT)
    }

    // ---- configurability ----

    @Test
    fun `required linear agreements is configurable`() {
        val clock = FakeClock()
        val g = ScanStabilityGate(clock = clock, requiredLinearAgreements = 3)
        assertThat(g.evaluate(SymbologyId.CODE_128, bytes("A")))
            .isEqualTo(GateDecision.REJECT_UNSTABLE_LINEAR)
        clock.advance(10)
        assertThat(g.evaluate(SymbologyId.CODE_128, bytes("A")))
            .isEqualTo(GateDecision.REJECT_UNSTABLE_LINEAR)
        clock.advance(10)
        assertThat(g.evaluate(SymbologyId.CODE_128, bytes("A"))).isEqualTo(GateDecision.ACCEPT)
    }

    @Test
    fun `single agreement makes linear behave like matrix`() {
        val g = ScanStabilityGate(clock = FakeClock(), requiredLinearAgreements = 1)
        assertThat(g.evaluate(SymbologyId.CODE_128, bytes("A"))).isEqualTo(GateDecision.ACCEPT)
    }
}
