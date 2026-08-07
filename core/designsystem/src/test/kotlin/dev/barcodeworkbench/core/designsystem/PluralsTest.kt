package dev.barcodeworkbench.core.designsystem

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PluralsTest {

    @Test
    fun `one is singular`() {
        assertThat(counted(1, "code")).isEqualTo("1 code")
    }

    @Test
    fun `zero and many are plural`() {
        // Zero takes the plural in English, which is easy to get wrong by testing
        // only the one-versus-two case.
        assertThat(counted(0, "code")).isEqualTo("0 codes")
        assertThat(counted(2, "code")).isEqualTo("2 codes")
        assertThat(counted(17, "code")).isEqualTo("17 codes")
    }

    @Test
    fun `irregular plurals can be supplied`() {
        assertThat(counted(1, "entry", "entries")).isEqualTo("1 entry")
        assertThat(counted(3, "entry", "entries")).isEqualTo("3 entries")
    }

    @Test
    fun `plural returns the word alone`() {
        assertThat(plural(1, "it", "them")).isEqualTo("it")
        assertThat(plural(2, "it", "them")).isEqualTo("them")
    }
}
