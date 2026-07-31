package krmelin.parser

import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParserTest {
    private fun parseResource(name: String): Pair<String, DiagnosticReporter> {
        val file = File("tests/parser/$name")
        val source = file.readText()
        val reporter = DiagnosticReporter()
        val lexer = Lexer(source, file.path, reporter)
        val tokens = lexer.lex()
        val parser = Parser(tokens, file.path, reporter)
        parser.parse()
        return source to reporter
    }

    @Test
    fun `hello sample parses without errors`() {
        val (_, reporter) = parseResource("hello.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `fizzbuzz sample parses without errors`() {
        val (_, reporter) = parseResource("fizzbuzz.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `data class sample parses without errors`() {
        val (_, reporter) = parseResource("data_class.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `chuj safety sample parses without errors`() {
        val (_, reporter) = parseResource("chuj_safety.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `exceptions sample parses without errors`() {
        val (_, reporter) = parseResource("exceptions.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `parser reports multiple errors on malformed input and recovers`() {
        val source = """
            robota foo() {
                toz x =
            }

            robota bar() {
                davaj
                davaj
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val parser = Parser(tokens, "bad.krm", reporter)
        parser.parse()
        assertTrue(reporter.errors.isNotEmpty(), "expected at least one diagnostic")
        assertTrue(reporter.errors.size >= 2, "expected recovery to surface multiple errors, got ${reporter.errors.size}")
    }
}
