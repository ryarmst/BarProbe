package dev.barcodeworkbench.feature.configpacks.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The sanitiser matters more than it looks.
 *
 * FTS4 treats quotes, hyphens, asterisks and parentheses as syntax, and an unbalanced
 * one makes SQLite throw. Since the query comes straight from a search box that runs
 * on every keystroke, an unsanitised character would surface as the screen crashing
 * mid-word.
 */
class FtsQueryTest {

    private fun sanitise(raw: String) = ConfigPackRepository.sanitiseFtsQuery(raw)

    @Test
    fun `words become prefix terms so results narrow while typing`() {
        assertThat(sanitise("factory")).isEqualTo("factory*")
        assertThat(sanitise("restore defaults")).isEqualTo("restore* defaults*")
    }

    @Test
    fun `an unbalanced quote cannot reach sqlite`() {
        assertThat(sanitise("\"factory")).isEqualTo("factory*")
        assertThat(sanitise("say \"what")).isEqualTo("say* what*")
    }

    @Test
    fun `hyphens and punctuation become separators`() {
        assertThat(sanitise("scan-mode")).isEqualTo("scan* mode*")
        assertThat(sanitise("prefix/suffix")).isEqualTo("prefix* suffix*")
        assertThat(sanitise("beeper (loud)")).isEqualTo("beeper* loud*")
    }

    @Test
    fun `fts operators are stripped rather than passed through`() {
        // NEAR, OR and column filters would otherwise change the query's meaning.
        assertThat(sanitise("a OR b")).isEqualTo("a* OR* b*")
        assertThat(sanitise("name:value")).isEqualTo("name* value*")
        assertThat(sanitise("a*b")).isEqualTo("a* b*")
        assertThat(sanitise("^start")).isEqualTo("start*")
    }

    @Test
    fun `empty and punctuation-only queries produce nothing`() {
        assertThat(sanitise("")).isEmpty()
        assertThat(sanitise("   ")).isEmpty()
        assertThat(sanitise("!!!")).isEmpty()
        assertThat(sanitise("\"\"")).isEmpty()
    }

    @Test
    fun `digits and underscores survive`() {
        assertThat(sanitise("code_128")).isEqualTo("code_128*")
        assertThat(sanitise("param 0x1D")).isEqualTo("param* 0x1D*")
    }

    @Test
    fun `non-latin text is preserved`() {
        // The character class uses Unicode properties, not ASCII ranges.
        assertThat(sanitise("café")).isEqualTo("café*")
    }
}
