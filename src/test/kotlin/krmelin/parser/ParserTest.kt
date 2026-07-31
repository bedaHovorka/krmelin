package krmelin.parser

import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
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
                toz y =
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val parser = Parser(tokens, "bad.krm", reporter)
        val cu = parser.parse()
        assertTrue(reporter.errors.isNotEmpty(), "expected at least one diagnostic")
        assertTrue(reporter.errors.size >= 2, "expected recovery to surface multiple errors, got ${reporter.errors.size}")
        // Recovery must not swallow the second declaration into the first errored block.
        assertEquals(2, cu.declarations.size, "expected both functions to survive recovery")
        assertEquals("foo", (cu.declarations[0] as krmelin.ast.Decl.FunDecl).name)
        assertEquals("bar", (cu.declarations[1] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `zapisnik combined with jedynak reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("zapisnik jedynak Foo { }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected 'zapisnik jedynak' to be rejected")
    }

    @Test
    fun `zapisnik combined with predpis reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("zapisnik predpis Foo { }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected 'zapisnik predpis' to be rejected")
    }

    @Test
    fun `predpis combined with jedynak reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("predpis jedynak Foo { }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected 'predpis jedynak' (interface+object without zapisnik) to be rejected")
    }

    @Test
    fun `malformed package declaration recovers and still parses following declarations`() {
        val source = """
            sachta 123
            robota foo() { }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the malformed package name")
        assertEquals(1, cu.declarations.size, "recovery should still surface the following function")
    }

    @Test
    fun `malformed import declaration recovers and still parses following declarations`() {
        val source = """
            privezt 123
            robota foo() { }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the malformed import name")
        assertEquals(1, cu.declarations.size, "recovery should still surface the following function")
    }

    @Test
    fun `malformed class member recovers and still parses the sibling member`() {
        val source = """
            tryda Foo {
                123
                robota bar() { }
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the stray integer literal member")
        val cls = cu.declarations[0] as krmelin.ast.Decl.ClassDecl
        assertEquals(1, cls.members.size, "recovery should still surface 'bar'")
        assertEquals("bar", (cls.members[0] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `missing identifier after dot reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("robota f() { davaj a. }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the missing member name")
    }

    @Test
    fun `missing newline after a statement reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("robota f() { davaj 1 davaj 2 }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic when two statements share a line without a separator")
    }

    @Test
    fun `recovery skips several tokens before reaching a synchronization point`() {
        val source = """
            robota foo() {
                1 2 3 4
            }
            robota bar() { }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors)
        assertEquals(2, cu.declarations.size, "recovery should still surface both functions")
        assertEquals("bar", (cu.declarations[1] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `stray boinak with no matching kaj recovers instead of hanging forever`() {
        // Regression test: 'boinak'/'kajtez'/'bitka'/'fajront' have no parse dispatch
        // of their own. If synchronize() ever returns without consuming one of these
        // when it is itself the offending token, the caller retries the identical
        // failing parse and the compiler hangs forever instead of reporting an error.
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val source = """
                robota f() {
                    boinak
                }
                robota g() { }
            """.trimIndent()
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "bad.krm", reporter).lex()
            val cu = Parser(tokens, "bad.krm", reporter).parse()
            assertTrue(reporter.hasErrors)
            assertEquals(2, cu.declarations.size, "recovery should still surface both functions")
        }
    }

    @Test
    fun `stray bitka with no matching pultik recovers instead of hanging forever`() {
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val source = """
                robota f() {
                    bitka
                }
                robota g() { }
            """.trimIndent()
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "bad.krm", reporter).lex()
            val cu = Parser(tokens, "bad.krm", reporter).parse()
            assertTrue(reporter.hasErrors)
            assertEquals(2, cu.declarations.size, "recovery should still surface both functions")
        }
    }
}
