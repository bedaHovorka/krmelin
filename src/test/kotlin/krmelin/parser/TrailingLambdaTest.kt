package krmelin.parser

import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.Stmt
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Postfix trailing lambda (Plan.md §6): `ma_dostat { ... }` — the spec's assertion
 * table writes the should-throw assertion with a trailing brace block, so a `{ }`
 * directly behind a call (or a bare callee) binds as its trailing lambda argument.
 * A `{ }` starting a statement keeps its existing reading.
 */
class TrailingLambdaTest {
    private fun parseExprStmt(source: String): Stmt.ExprStmt {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("robota rynek() {\n$source\n}", "test.krm", reporter).lex()
        val parser = Parser(tokens, "test.krm", reporter)
        val cu = parser.parse()
        assertFalse(reporter.hasErrors, reporter.render())
        val block = assertIs<Stmt.Block>((cu.declarations[0] as krmelin.ast.Decl.FunDecl).body
            .let { (it as FunBody.BlockBody).block })
        assertEquals(1, block.statements.size, "expected a single statement in $source")
        return assertIs(block.statements[0])
    }

    @Test
    fun `bare callee followed by braces takes them as a trailing lambda`() {
        val stmt = parseExprStmt("ma_dostat { dostanes Flakanec(\"bum\") }")
        val call = assertIs<Expr.CallExpr>(stmt.expr)
        assertEquals("ma_dostat", (call.callee as Expr.NameExpr).name)
        assertEquals(1, call.args.size)
        assertIs<Expr.LambdaExpr>(call.args[0])
    }

    @Test
    fun `braces after a parenthesized call append a lambda argument`() {
        val stmt = parseExprStmt("ma_dostat(\"vzkaz\") { }")
        val call = assertIs<Expr.CallExpr>(stmt.expr)
        assertEquals(2, call.args.size)
        assertIs<Expr.StringLit>(call.args[0])
        assertIs<Expr.LambdaExpr>(call.args[1])
    }

    @Test
    fun `braces after a safe member call take them as a trailing lambda`() {
        val stmt = parseExprStmt("x?.f { dostanes Flakanec(\"bum\") }")
        val call = assertIs<Expr.CallExpr>(stmt.expr)
        assertIs<Expr.SafeMemberExpr>(call.callee)
        assertEquals(1, call.args.size)
        assertIs<Expr.LambdaExpr>(call.args[0])
    }

    @Test
    fun `a brace block on the next line does NOT attach to the call above`() {
        val reporter = DiagnosticReporter()
        val source = "robota rynek() {\n  f(x)\n  { }\n}"
        val tokens = Lexer(source, "test.krm", reporter).lex()
        val cu = Parser(tokens, "test.krm", reporter).parse()
        assertFalse(reporter.hasErrors, reporter.render())
        val block = assertIs<Stmt.Block>((cu.declarations[0] as krmelin.ast.Decl.FunDecl).body
            .let { (it as FunBody.BlockBody).block })
        assertEquals(2, block.statements.size, "the '{ }' stays a statement of its own")
        val call = assertIs<Expr.CallExpr>((block.statements[0] as Stmt.ExprStmt).expr)
        assertEquals(1, call.args.size)
        val lambda = assertIs<Expr.LambdaExpr>((block.statements[1] as Stmt.ExprStmt).expr)
        assertTrue(lambda.params.isEmpty())
    }

    @Test
    fun `an if condition stays a call and its block stays structural`() {
        val reporter = DiagnosticReporter()
        // The `kaj (  )` parens are structural (eaten by parseIfStmt), so the condition is
        // the bare call; the `{ }` behind them is the then-block, not a trailing lambda.
        val source = "robota rynek() {\n  kaj (f(x)) { pravit(\"a\") }\n}"
        val tokens = Lexer(source, "test.krm", reporter).lex()
        val cu = Parser(tokens, "test.krm", reporter).parse()
        assertFalse(reporter.hasErrors, reporter.render())
        val block = assertIs<Stmt.Block>((cu.declarations[0] as krmelin.ast.Decl.FunDecl).body
            .let { (it as FunBody.BlockBody).block })
        assertEquals(1, block.statements.size)
        val ifStmt = assertIs<Stmt.IfStmt>(block.statements[0])
        val cond = assertIs<Expr.CallExpr>(ifStmt.condition)
        assertEquals(1, cond.args.size)
        assertEquals(1, ifStmt.thenBlock.statements.size)
    }
}
