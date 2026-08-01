package krmelin.resolve

import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import krmelin.parser.Parser
import krmelin.types.TypeChecker
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse

/**
 * M3 DoD: the valid fixture programs pass the whole semantic pipeline without a single
 * diagnostic (no errors, and no shadowing warnings either).
 */
class FixturesCleanTest {
    private fun analyzeFixture(name: String): DiagnosticReporter {
        val file = File("tests/parser/$name")
        val source = file.readText()
        val reporter = DiagnosticReporter()
        reporter.registerSource(file.path, source)
        val tokens = Lexer(source, file.path, reporter).lex()
        val unit = Parser(tokens, file.path, reporter).parse()
        val resolution = Resolver(reporter).resolve(unit)
        TypeChecker(reporter, resolution).check(unit)
        return reporter
    }

    @Test
    fun `all parser fixtures resolve and typecheck clean`() {
        val fixtures = File("tests/parser").listFiles { f -> f.extension == "krm" }
            ?.sortedBy { it.name } ?: emptyList()
        assert(fixtures.isNotEmpty()) { "no fixtures found under tests/parser/" }
        for (file in fixtures) {
            val reporter = analyzeFixture(file.name)
            assertFalse(
                reporter.hasErrors,
                "${file.name} should pass clean, got:\n${reporter.render()}",
            )
            assert(
                reporter.warnings.isEmpty(),
            ) { "${file.name} should produce no warnings, got:\n${reporter.render()}" }
        }
    }
}
