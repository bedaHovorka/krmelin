package krmelin.parser

import krmelin.ast.Expr
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import krmelin.lexer.TokenType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class ExprParserTest {
    private fun expr(source: String): Expr {
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "expr.krm", reporter).lex()
        val parser = Parser(tokens, "expr.krm", reporter)
        val e = parser.parseExpression()
        assertFalse(reporter.hasErrors, reporter.render())
        return e
    }

    @Test
    fun `multiplicative binds tighter than additive`() {
        val e = assertIs<Expr.BinaryExpr>(expr("1 + 2 * 3"))
        assertEquals(TokenType.PLUS, e.op)
        assertIs<Expr.IntLit>(e.left)
        val right = assertIs<Expr.BinaryExpr>(e.right)
        assertEquals(TokenType.STAR, right.op)
    }

    @Test
    fun `logical and binds tighter than logical or`() {
        val e = assertIs<Expr.BinaryExpr>(expr("fajne ci nyt aj nyt"))
        assertEquals(TokenType.CI, e.op)
        assertIs<Expr.BoolLit>(e.left)
        assertIs<Expr.BinaryExpr>(e.right)
    }

    @Test
    fun `elvis is right associative`() {
        val e = assertIs<Expr.ElvisExpr>(expr("a ?: b ?: c"))
        assertIs<Expr.NameExpr>(e.left)
        assertIs<Expr.ElvisExpr>(e.right)
    }

    @Test
    fun `member access binds tighter than comparison`() {
        val e = assertIs<Expr.BinaryExpr>(expr("a.dylka > 0"))
        assertEquals(TokenType.GT, e.op)
        assertIs<Expr.MemberExpr>(e.left)
        assertIs<Expr.IntLit>(e.right)
    }

    @Test
    fun `assignment is right associative`() {
        val e = assertIs<Expr.AssignExpr>(expr("a = b = 1"))
        assertIs<Expr.NameExpr>(e.target)
        assertIs<Expr.AssignExpr>(e.value)
    }
}
