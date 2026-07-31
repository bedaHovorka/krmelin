package krmelin

import krmelin.lexer.Keywords
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeywordsTest {
    private val keywordPattern = Regex("^[@A-Za-z_][A-Za-z0-9_]*\$")

    @Test
    fun `every keyword spelling matches the ASCII-only identifier pattern`() {
        for (spelling in Keywords.ALL) {
            assertTrue(
                keywordPattern.matches(spelling),
                "Keyword '$spelling' must match ^[@A-Za-z_][A-Za-z0-9_]*\$ (ASCII-only, no diacritics)",
            )
        }
    }

    @Test
    fun `chuj and nic both map to NULL`() {
        assertTrue(Keywords.lookup("chuj") == krmelin.lexer.TokenType.NULL)
        assertTrue(Keywords.lookup("nic") == krmelin.lexer.TokenType.NULL)
    }

    @Test
    fun `lookup returns null for non keywords`() {
        assertFalse(krmelin.lexer.TokenType.IDENTIFIER == Keywords.lookup("Robota"))
        assertTrue(Keywords.lookup("Robota") == null)
    }
}
