package krmelin.lexer

import krmelin.diag.DiagnosticReporter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LexerTest {
    private fun lex(source: String): Pair<List<Token>, DiagnosticReporter> {
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "test.krm", reporter).lex()
        return tokens to reporter
    }

    private fun types(source: String): List<TokenType> {
        val (tokens, _) = lex(source)
        return tokens.map { it.type }
    }

    @Test
    fun `lexes simple hello program`() {
        val (tokens, reporter) = lex("""
            sachta demo

            robota rynek() {
                zarvat("Toz vitaj, Krmelin!")
            }
        """.trimIndent())
        assertFalse(reporter.hasErrors, reporter.render())
        val expected = listOf(
            TokenType.SACHTA, TokenType.IDENTIFIER, TokenType.NEWLINE,
            TokenType.ROBOTA, TokenType.IDENTIFIER,
            TokenType.LPAREN, TokenType.RPAREN,
            TokenType.LBRACE, TokenType.NEWLINE,
            TokenType.IDENTIFIER, TokenType.LPAREN, TokenType.STRING_LITERAL, TokenType.RPAREN, TokenType.NEWLINE,
            TokenType.RBRACE, TokenType.EOF,
        )
        assertEquals(expected, tokens.map { it.type })
    }

    @Test
    fun `chuj and nic both lex to NULL`() {
        val (tokens, reporter) = lex("chuj\nnic")
        assertFalse(reporter.hasErrors)
        assertEquals(TokenType.NULL, tokens[0].type)
        assertEquals("chuj", tokens[0].text)
        assertEquals(TokenType.NULL, tokens[2].type)
        assertEquals("nic", tokens[2].text)
    }

    @Test
    fun `lexes operators and punctuation`() {
        val source = "+ - * / % = == != < > <= >= ! ?: . ?. ? ( ) { } < > , ->"
        val (tokens, reporter) = lex(source)
        assertFalse(reporter.hasErrors, reporter.render())
        val expected = listOf(
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT,
            TokenType.ASSIGN, TokenType.EQ, TokenType.NEQ,
            TokenType.LT, TokenType.GT, TokenType.LE, TokenType.GE,
            TokenType.BANG, TokenType.ELVIS, TokenType.DOT, TokenType.SAFE_DOT, TokenType.QUESTION,
            TokenType.LPAREN, TokenType.RPAREN,
            TokenType.LBRACE, TokenType.RBRACE,
            TokenType.LT, TokenType.GT, TokenType.COMMA, TokenType.ARROW,
            TokenType.EOF,
        )
        assertEquals(expected, tokens.map { it.type })
    }

    @Test
    fun `string template is parsed into parts`() {
        val (tokens, reporter) = lex("\"nazdar, \$mejno a \${foo.bar}\"")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(TokenType.STRING_LITERAL, tokens[0].type)
        val value = tokens[0].value as StringValue.Template
        assertEquals(4, value.parts.size)
        assertTrue(value.parts[0] is StringPart.Text)
        assertTrue(value.parts[1] is StringPart.Name)
        assertTrue(value.parts[2] is StringPart.Text)
        assertTrue(value.parts[3] is StringPart.Expr)
        assertEquals("mejno", (value.parts[1] as StringPart.Name).name)
        assertEquals("foo.bar", (value.parts[3] as StringPart.Expr).source)
    }

    @Test
    fun `comments are skipped`() {
        val (tokens, reporter) = lex("""
            // line comment
            robota foo() {
                /* block comment */
                davaj 1
            }
        """.trimIndent())
        assertFalse(reporter.hasErrors, reporter.render())
        assertTrue(tokens.any { it.type == TokenType.ROBOTA })
        assertTrue(tokens.any { it.type == TokenType.DAVAJ })
    }

    @Test
    fun `numeric literals carry their value`() {
        val (tokens, reporter) = lex("42 3.14 1e3")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(42L, tokens[0].value)
        assertEquals(3.14, tokens[1].value)
        assertEquals(1000.0, tokens[2].value)
    }

    @Test
    fun `annotations lex as single tokens`() {
        val (tokens, reporter) = lex("@Sichta @Parta")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(TokenType.AT_SICHTA, tokens[0].type)
        assertEquals(TokenType.AT_PARTA, tokens[1].type)
    }

    @Test
    fun `token spans track line and column across lines`() {
        val (tokens, _) = lex("ab\ncd")
        // "ab" at line 1, cols 1..3 (end exclusive)
        assertEquals(1, tokens[0].span.startLine)
        assertEquals(1, tokens[0].span.startCol)
        assertEquals(1, tokens[0].span.endLine)
        assertEquals(3, tokens[0].span.endCol)
        // "cd" at line 2, cols 1..3
        assertEquals(2, tokens[2].span.startLine)
        assertEquals(1, tokens[2].span.startCol)
        assertEquals(2, tokens[2].span.endLine)
        assertEquals(3, tokens[2].span.endCol)
    }

    @Test
    fun `oversized integer literal reports a diagnostic and recovers`() {
        val (tokens, reporter) = lex("99999999999999999999999")
        assertTrue(reporter.hasErrors, "expected an overflow diagnostic")
        val intTok = tokens.first { it.type == TokenType.INTEGER_LITERAL }
        assertEquals(0L, intTok.value)
        assertEquals(TokenType.EOF, tokens.last().type)
    }

    @Test
    fun `nested block comments do not leak trailing tokens`() {
        val (tokens, reporter) = lex("/* a /* b */ c */")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(listOf(TokenType.EOF), tokens.map { it.type })
    }

    @Test
    fun `empty string lexes as a plain literal`() {
        val (tokens, reporter) = lex("\"\"")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(TokenType.STRING_LITERAL, tokens[0].type)
        val value = tokens[0].value as StringValue.Plain
        assertEquals("", value.text)
    }
}
