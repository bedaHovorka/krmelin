package krmelin.errors

import krmelin.diag.DiagCode
import krmelin.diag.DiagnosticReporter
import krmelin.diag.Severity
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
        // Every reported diagnostic must use the Ostravština severity label (never English)
        for (diag in reporter.all) {
            assertTrue(
                diag.severity.label.matches(Regex("^[a-z]+$")),
                "severity label '${diag.severity.label}' must be lowercase ASCII (Ostravština), " +
                    "got: ${diag.severity} in ${input.name}",
            )
        }
    }

    private companion object {
        val ERRORS_DIR = File("tests/errors")
    }
}
