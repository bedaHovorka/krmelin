package krmelin.diag

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagCodeTest {
    private val codes: Map<String, String> =
        DiagCode.entries.associate { it.name to it.code }

    @Test
    fun `every diagnostic code is unique`() {
        val duplicates = codes.entries.groupBy { it.value }.filterValues { it.size > 1 }
        assertTrue(
            duplicates.isEmpty(),
            "codes must identify one diagnostic each, but these collide: $duplicates",
        )
    }

    @Test
    fun `every diagnostic code matches the documented shape`() {
        val bad = codes.filterValues { !Regex("^HAV\\d{3}$").matches(it) }
        assertTrue(bad.isEmpty(), "codes must look like HAV123, got: $bad")
    }

    @Test
    fun `every severity label is a diacritics-free dialect word`() {
        // Same convention as the keyword surface: ASCII only, so output is greppable
        // and typable everywhere. The dialect lives in the phonetic spelling.
        val bad = Severity.entries.filter { !Regex("^[a-z]+$").matches(it.label) }
        assertTrue(bad.isEmpty(), "severity labels must be lowercase ASCII words, got: $bad")
    }

    @Test
    fun `every diagnostic code is documented in the language spec`() {
        // Plan.md §10: "Maintain an error-code index in docs/language-spec.md."
        val spec = File("docs/language-spec.md")
        assertTrue(spec.exists(), "expected the error-code index at ${spec.path}")
        // Only index rows count: "| HAV102 | ...". The range table lists spans such as
        // "HAV001–HAV099", which are not codes.
        val documented = Regex("^\\| (HAV\\d{3}) \\|", RegexOption.MULTILINE)
            .findAll(spec.readText())
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(
            emptySet(),
            codes.values.toSet() - documented,
            "these codes are raised by the compiler but missing from ${spec.path}",
        )
        assertEquals(
            emptySet(),
            documented - codes.values.toSet(),
            "these codes are documented but no longer exist in DiagCode",
        )
    }
}
