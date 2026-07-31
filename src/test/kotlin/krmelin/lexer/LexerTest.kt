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
    fun `invalid exponent backtracks without corrupting subsequent spans`() {
        val (tokens, reporter) = lex("1e+")
        assertFalse(reporter.hasErrors, reporter.render())
        val expected = listOf(TokenType.INTEGER_LITERAL, TokenType.IDENTIFIER, TokenType.PLUS, TokenType.EOF)
        assertEquals(expected, tokens.map { it.type })
        assertEquals(1L, tokens[0].value)
        // "1" spans cols 1..2; the backtracked 'e' must not leak extra columns onto it.
        assertEquals(1, tokens[0].span.startCol)
        assertEquals(2, tokens[0].span.endCol)
        // "e" (re-lexed as an identifier) spans cols 2..3.
        assertEquals(2, tokens[1].span.startCol)
        assertEquals(3, tokens[1].span.endCol)
        // "+" spans cols 3..4.
        assertEquals(3, tokens[2].span.startCol)
        assertEquals(4, tokens[2].span.endCol)
    }

    @Test
    fun `empty string lexes as a plain literal`() {
        val (tokens, reporter) = lex("\"\"")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(TokenType.STRING_LITERAL, tokens[0].type)
        val value = tokens[0].value as StringValue.Plain
        assertEquals("", value.text)
    }

    @Test
    fun `every keyword spelling lexes to its mapped token type`() {
        for ((spelling, type) in Keywords.MAP) {
            val (tokens, reporter) = lex(spelling)
            assertFalse(reporter.hasErrors, "lexing '$spelling': ${reporter.render()}")
            assertEquals(type, tokens[0].type, "spelling '$spelling' should lex to $type")
        }
    }

    @Test
    fun `an uppercase keyword spelling lexes as an identifier`() {
        val (tokens, reporter) = lex("Robota")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
    }

    @Test
    fun `string escape sequences decode to their control characters`() {
        val (tokens, reporter) = lex("\"a\\nb\\tc\\rd\\\\e\\\"f\\\$g\"")
        assertFalse(reporter.hasErrors, reporter.render())
        val value = tokens[0].value as StringValue.Plain
        assertEquals("a\nb\tc\rd\\e\"f\$g", value.text)
    }

    @Test
    fun `invalid escape sequence reports a diagnostic and keeps the literal character`() {
        val (tokens, reporter) = lex("\"a\\qb\"")
        assertTrue(reporter.hasErrors, "expected a diagnostic for the unknown escape")
        val value = tokens[0].value as StringValue.Plain
        assertEquals("aqb", value.text)
    }

    @Test
    fun `unterminated string literal reports a diagnostic and recovers`() {
        val (tokens, reporter) = lex("\"unterminated")
        assertTrue(reporter.hasErrors, "expected an unterminated-string diagnostic")
        assertEquals(TokenType.STRING_LITERAL, tokens[0].type)
        assertEquals(TokenType.EOF, tokens.last().type)
    }

    @Test
    fun `unterminated string template expression reports a diagnostic and recovers`() {
        val (tokens, reporter) = lex("\"\${1 + \"")
        assertTrue(reporter.hasErrors, "expected an unterminated-template diagnostic")
        assertEquals(TokenType.EOF, tokens.last().type)
    }

    @Test
    fun `unterminated block comment reports a diagnostic and recovers`() {
        val (tokens, reporter) = lex("/* never closed")
        assertTrue(reporter.hasErrors, "expected an unterminated-comment diagnostic")
        assertEquals(listOf(TokenType.EOF), tokens.map { it.type })
    }

    @Test
    fun `unknown annotation reports a diagnostic`() {
        val (_, reporter) = lex("@Nope")
        assertTrue(reporter.hasErrors, "expected a diagnostic for an unsupported annotation")
    }

    @Test
    fun `unexpected character reports a diagnostic and is skipped`() {
        val (tokens, reporter) = lex("a # b")
        assertTrue(reporter.hasErrors, "expected a diagnostic for the stray '#'")
        assertEquals(listOf(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF), tokens.map { it.type })
    }

    @Test
    fun `dollar not followed by identifier or brace is kept as literal text`() {
        val (tokens, reporter) = lex("\"price: \$5\"")
        assertFalse(reporter.hasErrors, reporter.render())
        // The '$' flushes the preceding text into its own part, so this reconstructs
        // as two adjacent Text parts rather than collapsing into a single Plain value.
        val value = tokens[0].value as StringValue.Template
        val reconstructed = value.parts.joinToString("") { (it as StringPart.Text).text }
        assertEquals("price: \$5", reconstructed)
    }

    @Test
    fun `token toString includes type text and span`() {
        val (tokens, _) = lex("foo")
        val text = tokens[0].toString()
        assertTrue(text.contains("IDENTIFIER"))
        assertTrue(text.contains("foo"))
    }

    @Test
    fun `lexer may be constructed with default file and reporter`() {
        val tokens = Lexer("foo").lex()
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
    }

    @Test
    fun `tab and carriage return are skipped as horizontal whitespace`() {
        val (tokens, reporter) = lex("a\tb\rc")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(listOf(TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.IDENTIFIER, TokenType.EOF), tokens.map { it.type })
    }

    @Test
    fun `semicolon lexes as a newline separator`() {
        val (tokens, reporter) = lex("a;b")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(listOf(TokenType.IDENTIFIER, TokenType.NEWLINE, TokenType.IDENTIFIER, TokenType.EOF), tokens.map { it.type })
    }

    @Test
    fun `exponent with an explicit sign is parsed`() {
        val (tokens, reporter) = lex("1e+5 1e-5")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(100000.0, tokens[0].value)
        assertEquals(1e-5, tokens[1].value)
    }

    @Test
    fun `a trailing dot with no fractional digits is not part of the number`() {
        val (tokens, reporter) = lex("5.")
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(listOf(TokenType.INTEGER_LITERAL, TokenType.DOT, TokenType.EOF), tokens.map { it.type })
        assertEquals(5L, tokens[0].value)
    }

    @Test
    fun `an embedded newline inside a string body reports unterminated`() {
        val (tokens, reporter) = lex("\"abc\ndef\"")
        assertTrue(reporter.hasErrors, "expected an unterminated-string diagnostic")
        assertEquals(TokenType.STRING_LITERAL, tokens[0].type)
    }

    @Test
    fun `an embedded newline inside a template expression reports unterminated`() {
        val (tokens, reporter) = lex("\"\${1 +\n2}\"")
        assertTrue(reporter.hasErrors, "expected an unterminated-template diagnostic")
        assertEquals(TokenType.STRING_LITERAL, tokens[0].type)
    }

    @Test
    fun `nested braces inside a template expression are balanced`() {
        val (tokens, reporter) = lex("\"a\${b{c}d}e\"")
        assertFalse(reporter.hasErrors, reporter.render())
        val value = tokens[0].value as StringValue.Template
        val exprPart = value.parts.filterIsInstance<StringPart.Expr>().single()
        assertEquals("b{c}d", exprPart.source)
    }

    @Test
    fun `a block comment spanning multiple lines still separates statements`() {
        val (tokens, reporter) = lex("""
            a
            /* line one
               line two */
            b
        """.trimIndent())
        assertFalse(reporter.hasErrors, reporter.render())
        assertEquals(
            listOf(TokenType.IDENTIFIER, TokenType.NEWLINE, TokenType.IDENTIFIER, TokenType.EOF),
            tokens.map { it.type },
        )
    }
}
