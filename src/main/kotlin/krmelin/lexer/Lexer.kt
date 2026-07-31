package krmelin.lexer

import krmelin.diag.DiagCode
import krmelin.diag.DiagnosticReporter

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

    init {
        // Let the reporter quote the offending line under a caret (Plan.md §10).
        reporter.registerSource(file, source)
    }

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
            ' ', '\t', '\r', '\u000C' -> Unit // skip horizontal whitespace
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
                    reportError(
                        "divny znak '$c'",
                        note = "tyn znak v Krmelinu nic neznamena",
                    )
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
                "neznama anotace '$text'; su enem @Sichta a @Parta",
                span = atSpan,
                code = DiagCode.UNKNOWN_ANNOTATION,
                fix = "daj '@Sichta' na test, abo '@Parta' na partyju testu",
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
            val signOffset = if (peekAt(1) == '+' || peekAt(1) == '-') 1 else 0
            if (peekAt(1 + signOffset).isDigit()) {
                isFloat = true
                advance() // consume 'e'/'E'
                if (peek() == '+' || peek() == '-') advance()
                while (peek().isDigit()) advance()
            }
            // else: 'e' is not part of the number; leave it unconsumed for the next token.
        }
        val text = source.substring(start, current)
        if (isFloat) {
            val value = try {
                text.toDouble()
            } catch (_: NumberFormatException) {
                reportError("vadne desetinne cyslo '$text'", code = DiagCode.BAD_NUMBER)
                Double.NaN
            }
            addToken(TokenType.FLOAT_LITERAL, value)
        } else {
            val value = try {
                text.toLong()
            } catch (_: NumberFormatException) {
                reportError("cyslo je moc velke: '$text'", code = DiagCode.BAD_NUMBER,
                    fix = "do Cysla se vejde nejvyc 9223372036854775807")
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
                    reportError("nedokonceny text", code = DiagCode.UNTERMINATED_STRING,
                        fix = "zavri tyn text s '\"'")
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
                            if (!skipTemplateExprBody(nest = 0)) {
                                // The helper has already reported; bail out the same way
                                // the other unterminated cases in this loop do.
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
                    // A backslash at end of input, or immediately before a line break, is
                    // not an escape. Consuming what follows would read past the end of the
                    // source, or swallow the newline that terminates the statement; leave
                    // it for the '\n' arm and the isAtEnd() handler below, which already
                    // report the unterminated string.
                    if (!isAtEnd() && peek() != '\n') textBuilder.append(escape())
                }
                else -> {
                    textBuilder.append(c)
                    advance()
                }
            }
        }

        if (isAtEnd()) {
            reportError("nedokonceny text", code = DiagCode.UNTERMINATED_STRING,
                        fix = "zavri tyn text s '\"'")
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

    /**
     * Scans the body of a `${...}` interpolation, stopping just past its closing brace.
     *
     * Braces alone are not enough to find that closing brace: a `}` inside a nested
     * string literal is content, not structure, so nested strings are skipped whole.
     * Returns false, having reported, when the interpolation is unterminated or nested
     * too deeply; the caller then bails out of [string].
     */
    private fun skipTemplateExprBody(nest: Int): Boolean {
        if (nest > MAX_TEMPLATE_NESTING) {
            reportError("sablony v texte su moc zanorene", code = DiagCode.TEMPLATE_TOO_DEEP,
                fix = "vytahni vnitrni vyrazy do pojmenovanych hodnot")
            return false
        }
        var depth = 1
        while (!isAtEnd() && depth > 0) {
            when (peek()) {
                '{' -> { advance(); depth++ }
                '}' -> { advance(); depth-- }
                // Do not consume the newline: it still has to terminate the statement.
                '\n' -> {
                    reportError("nedokonceny vyraz v texte", code = DiagCode.UNTERMINATED_TEMPLATE,
                            fix = "zavri tu vlozku s '}'")
                    return false
                }
                '"' -> {
                    advance()
                    if (!skipNestedString(nest + 1)) return false
                }
                else -> advance()
            }
        }
        if (depth != 0) {
            reportError("nedokonceny vyraz v texte", code = DiagCode.UNTERMINATED_TEMPLATE,
                            fix = "zavri tu vlozku s '}'")
            return false
        }
        return true
    }

    /**
     * Skips a string literal nested inside an interpolation, stopping just past its
     * closing quote. The text is not interpreted here — it is re-lexed later by the
     * sub-lexer in `Parser.stringPartsToTemplate`, so escapes are stepped over rather
     * than decoded (calling [escape] would double-report every invalid escape).
     */
    private fun skipNestedString(nest: Int): Boolean {
        if (nest > MAX_TEMPLATE_NESTING) {
            reportError("sablony v texte su moc zanorene", code = DiagCode.TEMPLATE_TOO_DEEP,
                fix = "vytahni vnitrni vyrazy do pojmenovanych hodnot")
            return false
        }
        while (!isAtEnd()) {
            when (peek()) {
                '"' -> { advance(); return true }
                '\n' -> {
                    reportError("nedokonceny text", code = DiagCode.UNTERMINATED_STRING,
                        fix = "zavri tyn text s '\"'")
                    return false
                }
                '\\' -> {
                    advance()
                    if (isAtEnd() || peek() == '\n') {
                        reportError("nedokonceny text", code = DiagCode.UNTERMINATED_STRING,
                        fix = "zavri tyn text s '\"'")
                        return false
                    }
                    advance()
                }
                '$' -> {
                    advance()
                    if (peek() == '{') {
                        advance()
                        if (!skipTemplateExprBody(nest + 1)) return false
                    }
                }
                else -> advance()
            }
        }
        reportError("nedokonceny text", code = DiagCode.UNTERMINATED_STRING,
                        fix = "zavri tyn text s '\"'")
        return false
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
                reportError("vadna escape sekvence '\\$c'", code = DiagCode.INVALID_ESCAPE,
                    fix = "platne escapy su \\n \\t \\r \\\\ \\\" a \\$")
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
        reportError("nedokonceny komentar", code = DiagCode.UNTERMINATED_COMMENT,
            fix = "zavri tyn komentar s '*/'")
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

    private fun reportError(
        message: String,
        span: SourceSpan = currentSpan(),
        code: DiagCode = DiagCode.UNEXPECTED_CHAR,
        note: String? = null,
        fix: String? = null,
    ) {
        reporter.error(
            code = code,
            message = message,
            span = span,
            highlight = note,
            fix = fix,
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

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]

    private fun peekAt(offset: Int): Char = if (current + offset >= source.length) '\u0000' else source[current + offset]

    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        advance()
        return true
    }
}

/**
 * Depth limit for string templates nested inside each other. Interpolation scanning is
 * recursive, so without a bound an adversarial source file is a StackOverflowError —
 * which, unlike a diagnostic, no part of the compiler recovers from.
 */
private const val MAX_TEMPLATE_NESTING = 32

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
