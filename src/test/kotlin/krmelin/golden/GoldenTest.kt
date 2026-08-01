package krmelin.golden

import krmelin.TestSupport.transpileFile
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Golden codegen tests (Plan.md §9): every `tests/golden/foo.krm` is transpiled through
 * the full pipeline and diffed against `foo.kt.expected` sitting next to it.
 *
 * Both sides are normalized before comparison (LF endings, trailing per-line whitespace
 * stripped, exactly one trailing newline) so the diff captures formatting the emitter
 * owns, not how the expected file was last saved. Emitter output itself is
 * deterministic — 4-space indents, fixed blank-line rules.
 *
 * Regenerate expectations after an intentional emitter change with:
 *
 *     ./gradlew test --tests "krmelin.golden.*" -Dupdate.golden=true
 *
 * …then review the diff carefully — the expected files ARE the specification.
 */
class GoldenTest {

    @TestFactory
    fun `golden pairs transpile to their expected kotlin`(): List<DynamicTest> {
        val inputs = GOLDEN_DIR.listFiles { f -> f.extension == "krm" }?.sortedBy { it.name }
            ?: emptyList()
        if (inputs.isEmpty()) fail("no golden inputs under ${GOLDEN_DIR.path}")
        return inputs.map { input ->
            DynamicTest.dynamicTest(input.nameWithoutExtension) { check(input) }
        }
    }

    private fun check(input: File) {
        val expected = input.resolveSibling("${input.nameWithoutExtension}.kt.expected")
        val actual = normalize(transpileFile(input))

        if (update) {
            expected.writeText(actual)
            return
        }

        if (!expected.exists()) {
            fail("missing ${expected.path}; generate it with -Dupdate.golden=true and review the result")
        }
        assertEquals(
            normalize(expected.readText()),
            actual,
            "golden mismatch for ${input.path}; if the change is intentional, regenerate " +
                "with -Dupdate.golden=true and review the diff",
        )
    }

    private fun normalize(text: String): String =
        text.replace("\r\n", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trimEnd('\n') + "\n"

    private companion object {
        val GOLDEN_DIR = File("tests/golden")
        val update: Boolean = System.getProperty("update.golden")?.toBooleanStrictOrNull() == true
    }
}
