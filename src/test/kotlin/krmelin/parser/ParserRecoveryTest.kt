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
    fun `a bad when branch inside a method keeps the sibling method in the class`() {
        val (cu, reporter) = parse(
            "tryda C {\n    robota f() {\n        podle_teho {\n            -> 1\n        }\n    }\n\n" +
                "    robota g() {\n        zarvat(\"g\")\n    }\n}\n"
        )
        assertTrue(reporter.hasErrors, "expected a diagnostic for the conditionless branch")
        val cls = assertIs<Decl.ClassDecl>(cu.declarations.single())
        assertEquals(
            listOf("f", "g"),
            cls.members.filterIsInstance<Decl.FunDecl>().map { it.name },
            "'g' must stay a member of C rather than being hoisted to the top level",
        )
    }
}
