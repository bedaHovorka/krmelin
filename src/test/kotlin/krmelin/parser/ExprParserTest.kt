package krmelin.parser

import krmelin.ast.Expr
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import krmelin.lexer.TokenType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun `elvis binds tighter than comparison`() {
        // Per Plan.md's EBNF, `comparison = elvis {...}`, so "a ?: b < c" must parse
        // as "(a ?: b) < c", not "a ?: (b < c)".
        val e = assertIs<Expr.BinaryExpr>(expr("a ?: b < c"))
        assertEquals(TokenType.LT, e.op)
        assertIs<Expr.ElvisExpr>(e.left)
        assertIs<Expr.NameExpr>(e.right)
    }

    @Test
    fun `elvis binds tighter than logical or`() {
        // Per Plan.md's EBNF, `logicalOr = logicalAnd {...}` sits above elvis, so
        // "a ci b ?: c" must parse as "a ci (b ?: c)", not "(a ci b) ?: c".
        val e = assertIs<Expr.BinaryExpr>(expr("a ci b ?: c"))
        assertEquals(TokenType.CI, e.op)
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

    @Test
    fun `float literal expression is parsed`() {
        val e = assertIs<Expr.FloatLit>(expr("3.14"))
        assertEquals(3.14, e.value)
    }

    @Test
    fun `unary minus negates its operand`() {
        val e = assertIs<Expr.UnaryExpr>(expr("-a"))
        assertEquals(TokenType.MINUS, e.op)
        assertIs<Expr.NameExpr>(e.operand)
    }

    @Test
    fun `unary bang negates a boolean`() {
        val e = assertIs<Expr.UnaryExpr>(expr("!fajne"))
        assertEquals(TokenType.BANG, e.op)
        assertIs<Expr.BoolLit>(e.operand)
    }

    @Test
    fun `unary binds tighter than multiplicative`() {
        val e = assertIs<Expr.BinaryExpr>(expr("-a * b"))
        assertEquals(TokenType.STAR, e.op)
        assertIs<Expr.UnaryExpr>(e.left)
    }

    @Test
    fun `equality and inequality are parsed`() {
        assertEquals(TokenType.EQ, assertIs<Expr.BinaryExpr>(expr("a == b")).op)
        assertEquals(TokenType.NEQ, assertIs<Expr.BinaryExpr>(expr("a != b")).op)
    }

    @Test
    fun `le and ge comparisons are parsed`() {
        assertEquals(TokenType.LE, assertIs<Expr.BinaryExpr>(expr("a <= b")).op)
        assertEquals(TokenType.GE, assertIs<Expr.BinaryExpr>(expr("a >= b")).op)
    }

    @Test
    fun `call with no arguments`() {
        val e = assertIs<Expr.CallExpr>(expr("foo()"))
        assertTrue(e.args.isEmpty())
    }

    @Test
    fun `call with multiple arguments`() {
        val e = assertIs<Expr.CallExpr>(expr("foo(1, 2, 3)"))
        assertEquals(3, e.args.size)
    }

    @Test
    fun `member access chains left associatively`() {
        val e = assertIs<Expr.MemberExpr>(expr("a.b.c"))
        assertEquals("c", e.name)
        val inner = assertIs<Expr.MemberExpr>(e.receiver)
        assertEquals("b", inner.name)
        assertIs<Expr.NameExpr>(inner.receiver)
    }

    @Test
    fun `safe member access chains`() {
        val e = assertIs<Expr.SafeMemberExpr>(expr("a?.b?.c"))
        assertEquals("c", e.name)
        assertIs<Expr.SafeMemberExpr>(e.receiver)
    }

    @Test
    fun `call then member access chains as postfix`() {
        val e = assertIs<Expr.MemberExpr>(expr("foo().bar"))
        assertEquals("bar", e.name)
        assertIs<Expr.CallExpr>(e.receiver)
    }

    @Test
    fun `parenthesized expression overrides precedence`() {
        val e = assertIs<Expr.BinaryExpr>(expr("(1 + 2) * 3"))
        assertEquals(TokenType.STAR, e.op)
        val left = assertIs<Expr.ParenExpr>(e.left)
        assertIs<Expr.BinaryExpr>(left.expr)
    }

    @Test
    fun `assignment target may be a member expression`() {
        val e = assertIs<Expr.AssignExpr>(expr("a.b = 1"))
        assertIs<Expr.MemberExpr>(e.target)
    }

    @Test
    fun `lambda with an explicit parameter list`() {
        val e = assertIs<Expr.LambdaExpr>(expr("{ x -> x }"))
        assertEquals(listOf("x"), e.params)
        val body = assertIs<krmelin.ast.LambdaBody.BlockBody>(e.body)
        assertEquals(1, body.block.statements.size)
    }

    @Test
    fun `lambda without parameters`() {
        val e = assertIs<Expr.LambdaExpr>(expr("{ zarvat(1) }"))
        assertTrue(e.params.isEmpty())
    }

    @Test
    fun `lambda with multiple parameters`() {
        val e = assertIs<Expr.LambdaExpr>(expr("{ a, b -> a }"))
        assertEquals(listOf("a", "b"), e.params)
    }

    @Test
    fun `parenthesized expression allows leading and trailing newlines`() {
        val e = assertIs<Expr.ParenExpr>(expr("(\n1\n)"))
        assertIs<Expr.IntLit>(e.expr)
    }

    @Test
    fun `call arguments may span multiple lines`() {
        val e = assertIs<Expr.CallExpr>(expr("foo(\n1,\n2\n)"))
        assertEquals(2, e.args.size)
    }
}
