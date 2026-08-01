package krmelin.parser

import krmelin.ast.Decl
import krmelin.ast.FunBody
import krmelin.ast.Stmt
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Panic-mode recovery: a malformed construct must be contained, not allowed to consume
 * whatever follows it. Each case asserts both that the error is reported and that the
 * declarations around it survive with the right parent.
 */
class ParserRecoveryTest {
    private fun parse(source: String): Pair<Decl.CompilationUnit, DiagnosticReporter> {
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "temp.krm", reporter).lex()
        return Parser(tokens, "temp.krm", reporter).parse() to reporter
    }

    private fun bodyOf(decl: Decl): Stmt.Block =
        (assertIs<Decl.FunDecl>(decl).body as FunBody.BlockBody).block

    @Test
    fun `a when branch with no condition is contained within its function`() {
        val (cu, reporter) = parse(
            "robota f() {\n    podle_teho {\n        -> 1\n    }\n    zarvat(\"after\")\n}\n"
        )
        assertTrue(reporter.hasErrors, "expected a diagnostic for the conditionless branch")
        val block = bodyOf(cu.declarations.single())
        assertTrue(
            block.statements.any { it is Stmt.ExprStmt },
            "the statement after the bad 'podle_teho' must still be parsed",
        )
    }

    @Test
    fun `a malformed statement in a plain block does not consume the next statement`() {
        val (cu, reporter) = parse(
            "robota f() {\n    toz x = )\n    zarvat(\"after\")\n}\n"
        )
        assertTrue(reporter.hasErrors, "expected a diagnostic for the stray ')'")
        val block = bodyOf(cu.declarations.single())
        assertTrue(
            block.statements.any { it is Stmt.ExprStmt },
            "recovery should still surface the call after the bad statement",
        )
    }

    @Test
    fun `an assignment continues onto the next line`() {
        // Characterization of existing behaviour, recorded because it is surprising:
        // parseExpression skips leading newlines, so an initializer may start on the
        // following line and 'toz x =' silently takes the next statement as its value.
        val (cu, reporter) = parse(
            "robota f() {\n    toz x =\n    zarvat(\"after\")\n}\n"
        )
        assertTrue(!reporter.hasErrors, reporter.render())
        val block = bodyOf(cu.declarations.single())
        assertEquals(1, block.statements.size, "the call becomes the initializer, not a statement")
    }

    @Test
    fun `an unclosed when body keeps the branches already parsed`() {
        val (cu, reporter) = parse("robota f() {\n    podle_teho (x) {\n        boinak -> 1\n")
        assertTrue(reporter.hasErrors, "expected a diagnostic for the missing '}'")
        val block = bodyOf(cu.declarations.single())
        val whenStmt = block.statements.filterIsInstance<Stmt.WhenStmt>().singleOrNull()
        assertTrue(whenStmt != null, "the podle_teho statement must survive a missing brace")
        assertEquals(1, whenStmt.branches.size, "the parsed branch must survive")
    }

    @Test
    fun `an unclosed lambda body keeps the statements already parsed`() {
        val (cu, reporter) = parse("robota f() {\n    toz g = { x ->\n        davaj x\n")
        assertTrue(reporter.hasErrors, "expected a diagnostic for the missing '}'")
        val block = bodyOf(cu.declarations.single())
        assertTrue(
            block.statements.isNotEmpty(),
            "the property holding the lambda must survive a missing brace",
        )
    }

    @Test
    fun `multiple top-level errors are all reported without stopping after the first`() {
        val (cu, reporter) = parse(
            "boinak nonsense\n" +
                "robota f() {}\n" +
                "dalsi nesmysl\n" +
                "robota g() {}\n"
        )
        assertTrue(reporter.errors.size >= 2, "expected at least two errors, got: ${reporter.render()}")
        val names = cu.declarations.filterIsInstance<Decl.FunDecl>().map { it.name }
        assertEquals(listOf("f", "g"), names, "both valid functions must survive multi-error recovery")
    }

    @Test
    fun `multiple errors in function bodies are all collected`() {
        val (_, reporter) = parse(
            "robota rynek() {\n" +
                "    toz x = )\n" +
                "    toz y = )\n" +
                "    toz z = 1\n" +
                "}\n"
        )
        assertTrue(reporter.errors.size >= 2, "both bad statements must produce diagnostics, got: ${reporter.render()}")
    }

    @Test
    fun `stray brace followed by valid declaration both diagnosed and recovered`() {
        val (cu, reporter) = parse(
            "}\n" +
                "robota rynek() {}\n"
        )
        assertTrue(reporter.hasErrors, "stray brace must produce a diagnostic")
        assertEquals(
            listOf("rynek"),
            cu.declarations.filterIsInstance<Decl.FunDecl>().map { it.name },
            "valid function after stray brace must still be parsed",
        )
    }
}
