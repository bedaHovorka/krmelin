package krmelin.errors

import krmelin.diag.DiagCode
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import krmelin.parser.Parser
import krmelin.resolve.Resolver
import krmelin.types.TypeChecker
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Errors-corpus test (Plan.md §8 / §9): every `tests/errors/foo.krm` is compiled through
 * the full front-end pipeline and every HAV code listed in the companion `foo.diag` file
 * must appear in the diagnostics.
 *
 * Format of a `.diag` file — one HAV code per line, blank lines and `//` comments ignored:
 *
 * ```
 * HAV220
 * HAV331
 * // optional comment
 * ```
 *
 * The test asserts that all listed codes are present; it does not require that *only* those
 * codes appear, so a single fixture can cover multiple related codes without needing to
 * enumerate every secondary diagnostic caused by recovery.
 */
class ErrorCorpusTest {

    @TestFactory
    fun `errors corpus fixtures trigger their expected diagnostics`(): List<DynamicTest> {
        val inputs = ERRORS_DIR.listFiles { f -> f.extension == "krm" }?.sortedBy { f -> f.name }
            ?: emptyList()
        if (inputs.isEmpty()) fail("no error fixtures under ${ERRORS_DIR.path}")
        return inputs.map { input ->
            DynamicTest.dynamicTest(input.nameWithoutExtension) { check(input) }
        }
    }

    private fun check(input: File) {
        val diagFile = input.resolveSibling("${input.nameWithoutExtension}.diag")
        if (!diagFile.exists()) fail("missing ${diagFile.path} for ${input.name}")

        val expected: List<DiagCode> = diagFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .map { line ->
                DiagCode.entries.find { it.code == line }
                    ?: fail("unknown code '$line' in ${diagFile.name}")
            }
        assertTrue(expected.isNotEmpty(), "empty .diag for ${input.name} — list at least one HAV code")

        val reporter = DiagnosticReporter()
        val source = input.readText()
        reporter.registerSource(input.name, source)
        val tokens = Lexer(source, input.name, reporter).lex()
        val unit = Parser(tokens, input.name, reporter).parse()
        val resolution = Resolver(reporter).resolve(unit)
        TypeChecker(reporter, resolution).check(unit)

        val actualCodes = reporter.all.map { it.code }.toSet()
        for (code in expected) {
            assertTrue(
                code in actualCodes,
                "expected $code in diagnostics for ${input.name}, got:\n${reporter.render()}",
            )
        }
        assertTrue(
            reporter.all.isNotEmpty(),
            "expected at least one diagnostic for ${input.name} but got none",
        )
        // Every reported diagnostic must carry a dialect severity label, not an English one.
        // The labels are hardcoded Ostravština words, so this regex is a guard against an
        // accidental English label slipping in — it checks "lowercase ASCII", not dialect.
        for (diag in reporter.all) {
            assertTrue(
                diag.severity.label.matches(Regex("^[a-z]+$")),
                "severity label '${diag.severity.label}' must be lowercase ASCII " +
                    "(guard against accidental English labels), got: ${diag.severity} in ${input.name}",
            )
        }
    }

    private companion object {
        val ERRORS_DIR = File("tests/errors")
    }
}
