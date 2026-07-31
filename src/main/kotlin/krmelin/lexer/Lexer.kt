package krmelin.lexer

import krmelin.diag.DiagnosticReporter
import krmelin.diag.Severity

/**
 * Hand-rolled lexer for Krmelin.
 *
 * Produces a stream of [Token]s including [TokenType.NEWLINE] tokens for
 * newline/semicolon statement termination. Comments are skipped; block
 * comments that span lines emit a single [TokenType.NEWLINE] so statements
 * separated only by a multi-line comment remain separated.
 *
 * Lexical errors are reported to the supplied [DiagnosticReporter] and recovered
 * by skipping the offending input when possible.
 */
class Lexer(
    private val source: String,
    private val file: String = "",
    private val reporter: DiagnosticReporter = DiagnosticReporter(),
    startLine: Int = 1,
    startCol: Int = 1,
) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = startLine
    private var col = startCol
    private var startLine = startLine
    private var startCol = startCol

    fun lex(): List<Token> {
        while (!isAtEnd()) {
            start = current
            startLine = line
            startCol = col
            scanToken()
        }
        tokens += Token(TokenType.EOF, "", SourceSpan.point(file, line, col))
        return tokens
    }

    private fun isAtEnd(): Boolean = current >= source.length

    private fun scanToken() {
        when (val c = advance()) {
            ' ', '\t', '\r', '' -> Unit // skip horizontal whitespace
            '\n' -> addToken(TokenType.NEWLINE)

            '(' -> addToken(TokenType.LPAREN)
            ')' -> addToken(TokenType.RPAREN)
            '{' -> addToken(TokenType.LBRACE)
            '}' -> addToken(TokenType.RBRACE)
            ',' -> addToken(TokenType.COMMA)
            ':' -> addToken(TokenType.COLON)
            ';' -> addToken(TokenType.NEWLINE)

            '+' -> addToken(TokenType.PLUS)
            '*' -> addToken(TokenType.STAR)
            '%' -> addToken(TokenType.PERCENT)
            '/' -> when {
                match('/') -> lineComment()
                match('*') -> blockComment()
                else -> addToken(TokenType.SLASH)
            }

            '-' -> if (match('>')) addToken(TokenType.ARROW) else addToken(TokenType.MINUS)
            '.' -> addToken(TokenType.DOT)
            '?' -> when {
                match(':') -> addToken(TokenType.ELVIS)
                match('.') -> addToken(TokenType.SAFE_DOT)
                else -> addToken(TokenType.QUESTION)
            }

            '!' -> if (match('=')) addToken(TokenType.NEQ) else addToken(TokenType.BANG)
            '=' -> if (match('=')) addToken(TokenType.EQ) else addToken(TokenType.ASSIGN)
            '<' -> if (match('=')) addToken(TokenType.LE) else addToken(TokenType.LT)
            '>' -> if (match('=')) addToken(TokenType.GE) else addToken(TokenType.GT)

            '"' -> string()
            '@' -> annotation()

            in '0'..'9' -> number()

            else -> when {
                isIdentifierStart(c) -> identifier()
                else -> {
                    reportError("unexpected character '$c'")
                    // Skip the bad character so lexing can continue.
                }
            }
        }
    }

    // ── Identifiers and keywords ─────────────────────────────────────────

    private fun identifier() {
        while (isIdentifierPart(peek())) advance()
        val text = source.substring(start, current)
        val type = Keywords.lookup(text) ?: TokenType.IDENTIFIER
        addToken(type)
    }

    private fun isIdentifierStart(c: Char): Boolean =
        c.isLetter() || c == '_'

    private fun isIdentifierPart(c: Char): Boolean =
        c.isLetterOrDigit() || c == '_'

    // ── Annotations ────────────────────────────────────────────────────────

    private fun annotation() {
        val atSpan = SourceSpan.point(file, line, col - 1) // '@' already consumed
        while (isIdentifierPart(peek())) advance()
        val text = source.substring(start, current)
        val type = Keywords.lookup(text)
        if (type == TokenType.AT_SICHTA || type == TokenType.AT_PARTA) {
            addToken(type)
        } else {
            reportError(
                "unknown annotation '$text'; only @Sichta and @Parta are supported",
                span = atSpan,
            )
        }
    }

    // ── Numeric literals ─────────────────────────────────────────────────

    private fun number() {
        while (peek().isDigit()) advance()
        var isFloat = false
        if (peek() == '.' && peekNext().isDigit()) {
            isFloat = true
            advance() // consume '.'
            while (peek().isDigit()) advance()
        }
        if (peek() == 'e' || peek() == 'E') {
            val save = current
            advance()
            if (peek() == '+' || peek() == '-') advance()
            if (peek().isDigit()) {
                isFloat = true
                while (peek().isDigit()) advance()
            } else {
                current = save // backtrack; 'e' is not part of the number
            }
        }
        val text = source.substring(start, current)
        if (isFloat) {
            val value = try {
                text.toDouble()
            } catch (_: NumberFormatException) {
                reportError("invalid floating-point literal '$text'")
                Double.NaN
            }
            addToken(TokenType.FLOAT_LITERAL, value)
        } else {
            val value = try {
                text.toLong()
            } catch (_: NumberFormatException) {
                reportError("integer literal too large: '$text'")
                0L
            }
            addToken(TokenType.INTEGER_LITERAL, value)
        }
    }

    // ── String literals and templates ──────────────────────────────────────

    private fun string() {
        val parts = mutableListOf<StringPart>()
        val textBuilder = StringBuilder()

        fun flushText() {
            if (textBuilder.isNotEmpty()) {
                parts += StringPart.Text(textBuilder.toString())
                textBuilder.clear()
            }
        }

        while (!isAtEnd() && peek() != '"') {
            when (val c = peek()) {
                '\n' -> {
                    reportError("unterminated string literal")
                    flushText()
                    addToken(TokenType.STRING_LITERAL, StringValue.Template(parts))
                    return
                }
                '$' -> {
                    advance() // consume '$'
                    flushText()
                    when {
                        peek() == '{' -> {
                            advance() // consume '{'
                            val exprStart = current
                            val exprStartLine = line
                            val exprStartCol = col
                            var depth = 1
                            while (!isAtEnd() && depth > 0) {
                                when (advance()) {
                                    '{' -> depth++
                                    '}' -> depth--
                                    '\n' -> {
                                        reportError("unterminated string template expression")
                                        flushText()
                                        addToken(TokenType.STRING_LITERAL, StringValue.Template(parts))
                                        return
                                    }
                                }
                            }
                            if (depth != 0) {
                                reportError("unterminated string template expression")
                                flushText()
                                addToken(TokenType.STRING_LITERAL, StringValue.Template(parts))
                                return
                            }
                            val exprText = source.substring(exprStart, current - 1)
                            parts += StringPart.Expr(exprText, exprStartLine, exprStartCol)
                        }
                        isIdentifierStart(peek()) -> {
                            val nameStart = current
                            val nameStartLine = line
                            val nameStartCol = col
                            advance()
                            while (isIdentifierPart(peek())) advance()
                            val name = source.substring(nameStart, current)
                            parts += StringPart.Name(name, SourceSpan(file, nameStartLine, nameStartCol, line, col))
                        }
                        else -> {
                            // '$' not followed by a template; keep it as literal text.
                            textBuilder.append('$')
                        }
                    }
                }
                '\\' -> {
                    advance()
                    textBuilder.append(escape())
                }
                else -> {
                    textBuilder.append(c)
                    advance()
                }
            }
        }

        if (isAtEnd()) {
            reportError("unterminated string literal")
            flushText()
            addToken(TokenType.STRING_LITERAL, StringValue.Template(parts))
            return
        }
        advance() // closing "
        flushText()

        val value = if (parts.isEmpty()) {
            StringValue.Plain("")
        } else if (parts.size == 1 && parts[0] is StringPart.Text) {
            StringValue.Plain((parts[0] as StringPart.Text).text)
        } else {
            StringValue.Template(parts)
        }
        addToken(TokenType.STRING_LITERAL, value)
    }

    private fun escape(): Char {
        return when (val c = advance()) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            '\\' -> '\\'
            '"' -> '"'
            '$' -> '$'
            else -> {
                reportError("invalid escape sequence '\\$c'")
                c
            }
        }
    }

    // ── Comments ─────────────────────────────────────────────────────────

    private fun lineComment() {
        while (!isAtEnd() && peek() != '\n') advance()
        // Do not consume the newline here; the next scanToken call will emit it.
    }

    private fun blockComment() {
        var sawNewline = false
        var depth = 1
        while (!isAtEnd()) {
            when (advance()) {
                '\n' -> sawNewline = true
                '/' -> if (match('*')) depth++
                '*' -> if (match('/')) {
                    depth--
                    if (depth == 0) {
                        if (sawNewline) {
                            // Emit a single newline token so multi-line comments still
                            // terminate statements that precede them.
                            start = current
                            startLine = line
                            startCol = col
                            addToken(TokenType.NEWLINE)
                        }
                        return
                    }
                }
            }
        }
        reportError("unterminated block comment")
    }

    // ── Token emission and helpers ─────────────────────────────────────────

    private fun addToken(type: TokenType, value: Any? = null) {
        val text = source.substring(start, current)
        // Coalesce adjacent newlines so the parser sees a single separator.
        if (type == TokenType.NEWLINE && tokens.lastOrNull()?.type == TokenType.NEWLINE) {
            return
        }
        tokens += Token(type, text, currentSpan(), value)
    }

    private fun reportError(message: String, span: SourceSpan = currentSpan()) {
        reporter.error(
            code = "E001",
            message = message,
            span = span,
        )
    }

    private fun currentSpan(): SourceSpan =
        SourceSpan(file, startLine, startCol, line, col)

    private fun advance(): Char {
        val c = source[current]
        current++
        if (c == '\n') {
            line++
            col = 1
        } else {
            col++
        }
        return c
    }

    private fun peek(): Char = if (isAtEnd()) ' ' else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) ' ' else source[current + 1]

    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        advance()
        return true
    }
}

/** Value carried by a [TokenType.STRING_LITERAL] token. */
sealed class StringValue {
    data class Plain(val text: String) : StringValue()
    data class Template(val parts: List<StringPart>) : StringValue()
}

/** Part of a string template. */
sealed class StringPart {
    data class Text(val text: String) : StringPart()
    data class Name(val name: String, val span: SourceSpan) : StringPart()
    /** [startLine]/[startCol] locate the first char of [source] in the original file. */
    data class Expr(val source: String, val startLine: Int, val startCol: Int) : StringPart()
}
