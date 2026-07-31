package krmelin.diag

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagCodeTest {
    private val codes: Map<String, String> =
        DiagCode::class.java.fields
            .filter { it.type == String::class.java }
            .associate { it.name to it.get(null) as String }

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
        val bad = codes.filterValues { !Regex("^E\\d{3}$").matches(it) }
        assertTrue(bad.isEmpty(), "codes must look like E123, got: $bad")
    }

    @Test
    fun `every diagnostic code is documented in the language spec`() {
        // Plan.md §10: "Maintain an error-code index in docs/language-spec.md."
        val spec = File("docs/language-spec.md")
        assertTrue(spec.exists(), "expected the error-code index at ${spec.path}")
        // Only index rows count: "| E102 | ...". The range table lists spans such as
        // "E001–E099", which are not codes.
        val documented = Regex("^\\| (E\\d{3}) \\|", RegexOption.MULTILINE)
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
