package krmelin.parser

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.Node
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.ast.TypeNode
import krmelin.ast.WhenBody
import krmelin.diag.DiagCode
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.SourceSpan
import krmelin.lexer.StringPart
import krmelin.lexer.Token
import krmelin.lexer.TokenType

/**
 * Recursive-descent parser for Krmelin declarations and statements.
 *
 * Expressions are delegated to [ExprParser]. Recovery is panic-mode:
 * unexpected tokens record a diagnostic, then the parser synchronizes on
 * statement/declaration boundaries so a single malformed construct does not
 * drown the whole file.
 */
class Parser(
    private val tokens: List<Token>,
    private val file: String,
    private val reporter: DiagnosticReporter,
) {
    private var current = 0
    private val exprParser = ExprParser(this)

    fun parse(): Decl.CompilationUnit {
        skipNewlines()
        var packageDecl: Decl.PackageDecl? = null
        if (check(TokenType.SACHTA)) {
            try {
                packageDecl = parsePackageDecl()
            } catch (e: ParseError) {
                synchronize()
            }
        }
        skipNewlines()
        val imports = mutableListOf<Decl.ImportDecl>()
        while (check(TokenType.PRIVEZT)) {
            try {
                imports += parseImportDecl()
            } catch (e: ParseError) {
                synchronize()
            }
            skipNewlines()
        }
        val declarations = mutableListOf<Decl>()
        while (!isAtEnd()) {
            // There is no enclosing brace at file scope, so a '}' here is a typo rather
            // than a terminator. Report and step over it: stopping the loop instead would
            // silently drop every remaining declaration without a single diagnostic.
            if (check(TokenType.RBRACE)) {
                error(
                    peek(),
                    "unexpected '}' — no block is open here",
                    code = DiagCode.UNEXPECTED_BRACE,
                    note = "there is nothing to close",
                    fix = "delete this '}', or add the '{' it was meant to close",
                )
                advance()
                skipNewlines()
                continue
            }
            val before = currentPosition()
            try {
                declarations += parseTopLevelDecl()
            } catch (e: ParseError) {
                synchronizeFrom(before)
            }
            skipNewlines()
        }
        val span = when {
            tokens.isEmpty() -> SourceSpan.point(file, 1, 1)
            else -> tokens.first().span.union(tokens.last().span)
        }
        return Decl.CompilationUnit(packageDecl, imports, declarations, span)
    }

    // ── Top-level declarations ─────────────────────────────────────────

    private fun parseTopLevelDecl(): Decl {
        val annotations = parseAnnotations()
        return when (peek().type) {
            TokenType.ZAPISNIK,
            TokenType.TRYDA,
            TokenType.JEDYNAK,
            TokenType.PREDPIS -> parseClassDecl(annotations)
            TokenType.ROBOTA -> parseFunDecl(annotations)
            TokenType.TOZ,
            TokenType.MOZEJ -> {
                val prop = parsePropertyDecl(annotations)
                expectNewlineOrSemi("property declaration must end with a newline")
                prop
            }
            else -> throw error(
                peek(),
                "expected a top-level declaration",
                code = DiagCode.EXPECTED_DECLARATION,
                note = "this cannot start a declaration",
                fix = "at file scope Krmelin expects 'sachta', 'privezt', 'tryda', " +
                    "'jedynak', 'predpis', 'robota', 'toz' or 'mozej'",
            )
        }
    }

    private fun parseAnnotations(): List<String> {
        val annotations = mutableListOf<String>()
        while (match(TokenType.AT_SICHTA, TokenType.AT_PARTA)) {
            annotations += when (previous().type) {
                TokenType.AT_SICHTA -> "Sichta"
                TokenType.AT_PARTA -> "Parta"
                else -> ""
            }
            // An annotation is a prefix of the declaration below it, not a statement of
            // its own, so the line break after it is formatting rather than a separator.
            skipNewlines()
        }
        return annotations
    }

    private fun parsePackageDecl(): Decl.PackageDecl {
        val start = expect(TokenType.SACHTA, "expected 'sachta'")
        val name = parseQualifiedName()
        expectNewlineOrSemi("package declaration must end with a newline")
        return Decl.PackageDecl(name, span(start, previous()))
    }

    private fun parseImportDecl(): Decl.ImportDecl {
        val start = expect(TokenType.PRIVEZT, "expected 'privezt'")
        val name = parseQualifiedName()
        // parseQualifiedName leaves a trailing ".*" unconsumed; consume it here.
        val wildcard = match(TokenType.DOT) && match(TokenType.STAR)
        expectNewlineOrSemi("import declaration must end with a newline")
        return Decl.ImportDecl(name, wildcard, span(start, previous()))
    }

    private fun parseQualifiedName(): List<String> {
        val parts = mutableListOf<String>()
        parts += expectIdentifier("expected an identifier")
        while (check(TokenType.DOT)) {
            val saved = currentPosition()
            advance() // consume '.'
            if (check(TokenType.STAR)) {
                // Leave ".*" for the wildcard handler in the caller.
                restorePosition(saved)
                break
            }
            parts += expectIdentifier("expected an identifier after '.'")
        }
        return parts
    }

    private fun parseClassDecl(annotations: List<String>): Decl.ClassDecl {
        val start = peek()
        val isData = match(TokenType.ZAPISNIK)
        val isObject = if (!isData) match(TokenType.JEDYNAK) else false
        val isInterface = if (!isData && !isObject) match(TokenType.PREDPIS) else false
        if (!isObject && !isInterface) {
            expect(TokenType.TRYDA, "expected 'tryda', 'jedynak', or 'predpis'")
        }
        val name = expectIdentifier("expected a class name")
        val params = when {
            check(TokenType.LPAREN) && (isObject || isInterface) -> {
                // Report, then consume and discard the list anyway. Leaving the '(' in
                // place would make the '{' below invisible, so the whole body was parsed
                // as top-level declarations and every member silently changed scope.
                val what = if (isObject) "jedynak" else "predpis"
                error(
                    peek(),
                    "'$what' cannot have constructor parameters",
                    code = DiagCode.PARAMS_NOT_ALLOWED,
                    note = "only 'tryda' and 'zapisnik tryda' take parameters",
                    fix = if (isObject) {
                        "a 'jedynak' is a single instance — declare the values as properties instead"
                    } else {
                        "a 'predpis' has no constructor — declare the values as properties instead"
                    },
                )
                parseParamList()
                emptyList()
            }
            check(TokenType.LPAREN) -> parseParamList()
            else -> emptyList()
        }
        // The body may open on the next line — either by choice, or because a multi-line
        // block comment in the header emitted a synthetic NEWLINE.
        val body = if (checkAfterNewlines(TokenType.LBRACE)) parseClassBody(allowAbstract = isInterface) else emptyList()
        return Decl.ClassDecl(
            name = name,
            params = params,
            members = body,
            isData = isData,
            isObject = isObject,
            isInterface = isInterface,
            annotations = annotations,
            span = span(start, previous()),
        )
    }

    private fun parseClassBody(allowAbstract: Boolean): List<Decl> {
        expect(TokenType.LBRACE, "expected '{' before class body")
        skipNewlines()
        val members = mutableListOf<Decl>()
        while (!isAtEnd() && !check(TokenType.RBRACE)) {
            val before = currentPosition()
            try {
                members += parseMember(allowAbstract)
            } catch (e: ParseError) {
                synchronizeFrom(before)
            }
            skipNewlines()
        }
        // Report but do not throw: throwing here would discard the ClassDecl along with
        // every member already parsed, and every declaration after it, for one typo.
        if (!match(TokenType.RBRACE)) {
            error(
                peek(),
                "expected '}' after class body",
                code = DiagCode.MISSING_BRACE,
                note = "the class body is still open here",
                fix = "close the class with '}'",
            )
        }
        return members
    }

    private fun parseMember(allowAbstract: Boolean): Decl {
        val annotations = parseAnnotations()
        return when (peek().type) {
            TokenType.ROBOTA -> parseFunDecl(annotations, allowAbstract)
            TokenType.TOZ,
            TokenType.MOZEJ -> {
                val prop = parsePropertyDecl(annotations)
                expectNewlineOrSemi("property declaration must end with a newline")
                prop
            }
            else -> throw error(
                peek(),
                "expected a class member",
                code = DiagCode.EXPECTED_MEMBER,
                note = "a class body holds only functions and properties",
                fix = "start the member with 'robota', 'toz' or 'mozej'",
            )
        }
    }

    private fun parseFunDecl(annotations: List<String>, allowAbstract: Boolean = false): Decl.FunDecl {
        val start = expect(TokenType.ROBOTA, "expected 'robota'")
        val name = expectIdentifier("expected a function name")
        val params = parseParamList()
        val returnType = if (match(TokenType.COLON)) parseType() else null
        val throwsTypes = mutableListOf<TypeNode>()
        if (match(TokenType.ROZDAVA)) {
            do {
                throwsTypes += parseType()
            } while (match(TokenType.COMMA))
        }
        val body = parseFunBody(allowAbstract)
        return Decl.FunDecl(
            annotations = annotations,
            name = name,
            params = params,
            returnType = returnType,
            throwsTypes = throwsTypes,
            body = body,
            span = span(start, previous()),
        )
    }

    private fun parseParamList(): List<Decl.Param> {
        val start = expect(TokenType.LPAREN, "expected '('")
        skipNewlines()
        val params = mutableListOf<Decl.Param>()
        if (!check(TokenType.RPAREN)) {
            do {
                skipNewlines()
                params += parseParam()
                skipNewlines()
            } while (match(TokenType.COMMA))
        }
        expect(TokenType.RPAREN, "expected ')' after parameters")
        return params
    }

    private fun parseParam(): Decl.Param {
        val start = peek()
        val isMutable = when {
            match(TokenType.TOZ) -> false
            match(TokenType.MOZEJ) -> true
            else -> false
        }
        val name = expectIdentifier("expected a parameter name")
        expect(TokenType.COLON, "expected ':' after parameter name")
        val type = parseType()
        val default = if (match(TokenType.ASSIGN)) parseExpression() else null
        return Decl.Param(name, type, default, isMutable, span(start, previous()))
    }

    private fun parsePropertyDecl(annotations: List<String> = emptyList()): Decl.PropertyDecl {
        val start = peek()
        val isMutable = when {
            match(TokenType.TOZ) -> false
            match(TokenType.MOZEJ) -> true
            else -> throw error(peek(), "expected 'toz' or 'mozej'")
        }
        val name = expectIdentifier("expected a property name")
        val type = if (match(TokenType.COLON)) parseType() else null
        val initializer = if (match(TokenType.ASSIGN)) parseExpression() else null
        return Decl.PropertyDecl(isMutable, name, type, initializer, annotations, span(start, previous()))
    }

    private fun parseType(): TypeNode {
        val start = peek()
        val name = expectIdentifier("expected a type name")
        val typeArgs = if (match(TokenType.LT)) parseTypeArguments() else emptyList()
        val nullable = match(TokenType.QUESTION)
        return TypeNode.NamedType(name, nullable, typeArgs, span(start, previous()))
    }

    private fun parseTypeArguments(): List<TypeNode> {
        val args = mutableListOf<TypeNode>()
        skipNewlines()
        do {
            skipNewlines()
            args += parseType()
            skipNewlines()
        } while (match(TokenType.COMMA))
        expect(TokenType.GT, "expected '>' after type arguments")
        return args
    }

    private fun parseFunBody(allowAbstract: Boolean): FunBody? {
        // Check before skipping newlines: the line break after the return type is what
        // marks the declaration as bodyless, so consuming it first would hide it.
        if (check(TokenType.NEWLINE, TokenType.RBRACE) || isAtEnd()) {
            if (allowAbstract) return null
            throw error(
                peek(),
                "expected a function body ('{' or '='); only 'predpis' may omit it",
                code = DiagCode.MISSING_FUN_BODY,
                note = "no body follows this declaration",
                fix = "write '{ ... }', or '= vyraz', or move the declaration into a 'predpis'",
            )
        }
        skipNewlines()
        return when {
            match(TokenType.ASSIGN) -> {
                val expr = parseExpression()
                expectNewlineOrSemi("function body expression must end with a newline")
                FunBody.ExprBody(expr)
            }
            else -> FunBody.BlockBody(parseBlock())
        }
    }

    // ── Statements ─────────────────────────────────────────────────────────

    internal fun parseBlock(): Stmt.Block {
        val start = expect(TokenType.LBRACE, "expected '{'")
        skipNewlines()
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd() && !check(TokenType.RBRACE)) {
            val before = currentPosition()
            try {
                val stmt = parseStatement()
                // 'break' must leave the loop without running recovery: a null statement
                // means '}' or EOF, which the loop condition handles.
                if (stmt != null) statements += stmt else break
            } catch (e: ParseError) {
                synchronizeFrom(before)
            }
            skipNewlines()
        }
        // As in parseClassBody: keep the statements we have rather than losing the whole
        // enclosing function to a single missing brace.
        if (!match(TokenType.RBRACE)) {
            error(
                peek(),
                "expected '}' after block",
                code = DiagCode.MISSING_BRACE,
                note = "the block is still open here",
                fix = "close the block with '}'",
            )
        }
        return Stmt.Block(statements, span(start, previous()))
    }

    internal fun parseStatement(): Stmt? {
        skipNewlines()
        return when (peek().type) {
            TokenType.RBRACE,
            TokenType.EOF -> null
            TokenType.TOZ,
            TokenType.MOZEJ -> {
                val decl = parsePropertyDecl()
                expectNewlineOrSemi("property declaration must end with a newline")
                Stmt.PropertyStmt(decl, decl.span)
            }
            TokenType.KAJ -> parseIfStmt()
            TokenType.PODLE_TEHO -> parseWhenStmt()
            TokenType.PROKAZDY -> parseForStmt()
            TokenType.RUBAJ -> parseWhileStmt()
            TokenType.DAVAJ -> parseReturnStmt()
            TokenType.ZDYBAT -> parseBreakStmt()
            TokenType.DALEJ -> parseContinueStmt()
            TokenType.PULTIK -> parseTryStmt()
            TokenType.DOSTANES -> parseThrowStmt()
            else -> parseExprStmt()
        }
    }

    private fun parseIfStmt(): Stmt.IfStmt {
        val start = expect(TokenType.KAJ, "expected 'kaj'")
        expect(TokenType.LPAREN, "expected '(' after 'kaj'")
        skipNewlines()
        val condition = parseExpression()
        skipNewlines()
        expect(TokenType.RPAREN, "expected ')' after condition")
        val thenBlock = parseBlock()
        val elseIfs = mutableListOf<Stmt.ElseIf>()
        while (matchAfterNewlines(TokenType.KAJTEZ)) {
            expect(TokenType.LPAREN, "expected '(' after 'kajtez'")
            skipNewlines()
            val elifCond = parseExpression()
            skipNewlines()
            expect(TokenType.RPAREN, "expected ')' after 'kajtez' condition")
            val elifBlock = parseBlock()
            elseIfs += Stmt.ElseIf(elifCond, elifBlock)
        }
        val elseBlock = if (matchAfterNewlines(TokenType.BOINAK)) parseBlock() else null
        return Stmt.IfStmt(condition, thenBlock, elseIfs, elseBlock, span(start, previous()))
    }

    private fun parseWhenStmt(): Stmt.WhenStmt {
        val start = expect(TokenType.PODLE_TEHO, "expected 'podle_teho'")
        val subject = if (check(TokenType.LBRACE)) {
            null
        } else {
            expect(TokenType.LPAREN, "expected '(' after 'podle_teho'")
            skipNewlines()
            val s = if (check(TokenType.RPAREN)) null else parseExpression()
            skipNewlines()
            expect(TokenType.RPAREN, "expected ')' after 'podle_teho' subject")
            s
        }
        expect(TokenType.LBRACE, "expected '{' after 'podle_teho'")
        val branches = mutableListOf<Stmt.WhenBranch>()
        while (!isAtEnd() && !check(TokenType.RBRACE)) {
            skipNewlines()
            if (check(TokenType.RBRACE)) break
            // Capture the position *after* the newline skip: taking it at the top of the
            // iteration would leave `before` behind a consumed newline, so the first
            // failing branch would look like it made progress and emit a second
            // diagnostic before converging.
            val before = currentPosition()
            try {
                branches += parseWhenBranch()
            } catch (e: ParseError) {
                synchronizeFrom(before)
            }
        }
        // As in parseBlock/parseClassBody: report rather than throw, so the branches
        // already parsed are not discarded along with the statement.
        if (!match(TokenType.RBRACE)) {
            error(
                peek(),
                "expected '}' after 'podle_teho' body",
                code = DiagCode.MISSING_BRACE,
                note = "the podle_teho body is still open here",
                fix = "close the podle_teho with '}'",
            )
        }
        return Stmt.WhenStmt(subject, branches, span(start, previous()))
    }

    private fun parseWhenBranch(): Stmt.WhenBranch {
        val conditions: List<Expr>
        val isElse: Boolean
        if (match(TokenType.BOINAK)) {
            conditions = emptyList()
            isElse = true
        } else {
            val first = parseExpression()
            val rest = mutableListOf<Expr>(first)
            while (match(TokenType.COMMA)) {
                skipNewlines()
                rest += parseExpression()
            }
            conditions = rest
            isElse = false
        }
        expect(TokenType.ARROW, "expected '->' after when branch condition")
        val body = parseWhenBody()
        if (body is WhenBody.ExprBody) {
            // Consume a terminating newline if present, but do not require it.
            if (match(TokenType.NEWLINE)) Unit
        }
        return Stmt.WhenBranch(conditions, isElse, body)
    }

    private fun parseWhenBody(): WhenBody {
        skipNewlines()
        return if (check(TokenType.LBRACE)) {
            WhenBody.BlockBody(parseBlock())
        } else {
            WhenBody.ExprBody(parseExpression())
        }
    }

    private fun parseForStmt(): Stmt.ForStmt {
        val start = expect(TokenType.PROKAZDY, "expected 'prokazdy'")
        expect(TokenType.LPAREN, "expected '(' after 'prokazdy'")
        val name = expectIdentifier("expected a loop variable")
        expect(TokenType.V, "expected 'v' after loop variable")
        val iterable = parseExpression()
        expect(TokenType.RPAREN, "expected ')' after 'prokazdy' iterable")
        val body = parseBlock()
        return Stmt.ForStmt(name, iterable, body, span(start, previous()))
    }

    private fun parseWhileStmt(): Stmt.WhileStmt {
        val start = expect(TokenType.RUBAJ, "expected 'rubaj'")
        expect(TokenType.LPAREN, "expected '(' after 'rubaj'")
        skipNewlines()
        val condition = parseExpression()
        skipNewlines()
        expect(TokenType.RPAREN, "expected ')' after 'rubaj' condition")
        val body = parseBlock()
        return Stmt.WhileStmt(condition, body, span(start, previous()))
    }

    private fun parseReturnStmt(): Stmt.ReturnStmt {
        val start = expect(TokenType.DAVAJ, "expected 'davaj'")
        // isAtEnd(), not check(EOF): check() short-circuits on isAtEnd(), so it can never
        // match the EOF token and a trailing 'davaj' would look like it had a value.
        val value = if (isAtEnd() || check(TokenType.NEWLINE, TokenType.RBRACE)) {
            null
        } else {
            parseExpression()
        }
        expectNewlineOrSemi("'davaj' must end with a newline")
        return Stmt.ReturnStmt(value, span(start, previous()))
    }

    private fun parseBreakStmt(): Stmt.BreakStmt {
        val start = expect(TokenType.ZDYBAT, "expected 'zdybat'")
        expectNewlineOrSemi("'zdybat' must end with a newline")
        return Stmt.BreakStmt(span(start, previous()))
    }

    private fun parseContinueStmt(): Stmt.ContinueStmt {
        val start = expect(TokenType.DALEJ, "expected 'dalej'")
        expectNewlineOrSemi("'dalej' must end with a newline")
        return Stmt.ContinueStmt(span(start, previous()))
    }

    private fun parseTryStmt(): Stmt.TryStmt {
        val start = expect(TokenType.PULTIK, "expected 'pultik'")
        val block = parseBlock()
        val catches = mutableListOf<Stmt.Catch>()
        while (matchAfterNewlines(TokenType.BITKA)) {
            expect(TokenType.LPAREN, "expected '(' after 'bitka'")
            val param = parseParam()
            expect(TokenType.RPAREN, "expected ')' after 'bitka' parameter")
            val catchBlock = parseBlock()
            catches += Stmt.Catch(param, catchBlock)
        }
        val finallyBlock = if (matchAfterNewlines(TokenType.FAJRONT)) parseBlock() else null
        return Stmt.TryStmt(block, catches, finallyBlock, span(start, previous()))
    }

    private fun parseThrowStmt(): Stmt.ThrowStmt {
        val start = expect(TokenType.DOSTANES, "expected 'dostanes'")
        val expr = parseExpression()
        expectNewlineOrSemi("'dostanes' must end with a newline")
        return Stmt.ThrowStmt(expr, span(start, previous()))
    }

    private fun parseExprStmt(): Stmt.ExprStmt {
        val expr = parseExpression()
        expectNewlineOrSemi("expression statement must end with a newline")
        return Stmt.ExprStmt(expr, expr.span)
    }

    // ── Expressions (delegated to Pratt parser) ───────────────────────────

    internal fun parseExpression(): Expr = exprParser.parseExpression()

    // ── Helpers ──────────────────────────────────────────────────────────

    internal fun peek(): Token = tokens[current]
    internal fun previous(): Token = tokens[current - 1]
    internal fun isAtEnd(): Boolean = current >= tokens.size || tokens[current].type == TokenType.EOF

    internal fun check(vararg types: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type in types
    }

    internal fun match(vararg types: TokenType): Boolean {
        if (check(*types)) {
            advance()
            return true
        }
        return false
    }

    internal fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    internal fun expect(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    internal fun expectIdentifier(message: String): String {
        if (check(TokenType.IDENTIFIER)) {
            return advance().text
        }
        throw error(peek(), message)
    }

    internal fun expectNewlineOrSemi(message: String) {
        if (match(TokenType.NEWLINE)) return
        if (isAtEnd() || check(TokenType.RBRACE)) return
        throw error(peek(), message)
    }

    internal fun skipNewlines() {
        while (check(TokenType.NEWLINE)) advance()
    }

    /**
     * Skips newlines and reports whether one of [types] follows, restoring the cursor
     * when it does not.
     *
     * Statement termination is newline-significant (Plan.md §4.3), so newlines cannot be
     * skipped unconditionally — doing so would let an unrelated following block be
     * mistaken for a continuation. This is for the positions where a line break is a
     * formatting choice rather than a separator: a `boinak`/`bitka` clause after the
     * closing brace of its block, or a class body opened on the next line.
     */
    private fun checkAfterNewlines(vararg types: TokenType): Boolean {
        val saved = currentPosition()
        skipNewlines()
        if (check(*types)) return true
        restorePosition(saved)
        return false
    }

    /** [checkAfterNewlines], consuming the matched token. */
    private fun matchAfterNewlines(vararg types: TokenType): Boolean {
        if (!checkAfterNewlines(*types)) return false
        advance()
        return true
    }

    internal fun currentPosition(): Int = current

    internal fun restorePosition(pos: Int) {
        current = pos
    }

    /**
     * Panic-mode recovery that is guaranteed to make progress.
     *
     * [synchronize] deliberately stops *at* a restart token without consuming it, so a
     * caller that can parse that token resumes cleanly. When the caller cannot parse it
     * — every token in `syncTokens` that the enclosing dispatcher has no case for — the
     * cursor would not move and the caller would re-enter the identical failing parse
     * forever, appending a diagnostic each pass until the JVM died. Recovery loops must
     * therefore call this, not [synchronize] directly, passing the cursor position they
     * started the failed parse from.
     *
     * `<=` rather than `==` because [restorePosition] can move the cursor backwards
     * (see `parseQualifiedName` and `ExprParser.parseLambda`); neither can currently
     * restore past a loop start, but `==` would silently reopen the hang if that changed.
     */
    internal fun synchronizeFrom(from: Int) {
        synchronize()
        if (current <= from) {
            restorePosition(from)
            advance()
        }
    }

    /**
     * Panic-mode recovery: skip tokens until we reach a likely boundary.
     * Stops at newline, '}', or at the start of a fresh declaration/statement.
     *
     * Callers in a retry loop must use [synchronizeFrom] instead — this can return
     * without consuming anything.
     */
    internal fun synchronize() {
        if (isAtEnd()) return
        if (peek().type == TokenType.NEWLINE) {
            advance()
            return
        }
        if (peek().type == TokenType.RBRACE) return
        // Kept deliberately minimal. KAJTEZ/BOINAK/BITKA/FAJRONT are continuation-only
        // keywords with no dispatch case of their own, and they always follow a block's
        // closing '}' in this grammar, so the RBRACE check above already stops before
        // them; adding them back would buy nothing while making the progress invariant
        // in synchronizeFrom load-bearing in four more places.
        //
        // No entry here is required to be one the caller can dispatch on: stopping at a
        // token the caller cannot parse is safe because synchronizeFrom guarantees the
        // cursor moves anyway.
        val syncTokens = setOf(
            TokenType.ROBOTA, TokenType.TRYDA, TokenType.ZAPISNIK,
            TokenType.JEDYNAK, TokenType.PREDPIS, TokenType.KAJ,
            TokenType.PODLE_TEHO, TokenType.PROKAZDY,
            TokenType.RUBAJ, TokenType.PULTIK,
            TokenType.TOZ, TokenType.MOZEJ, TokenType.DAVAJ, TokenType.ZDYBAT,
            TokenType.DALEJ, TokenType.DOSTANES,
        )
        while (!isAtEnd()) {
            if (peek().type in syncTokens) return
            advance()
            if (peek().type == TokenType.NEWLINE || peek().type == TokenType.RBRACE) return
        }
    }

    /**
     * Reports a syntax error and returns the [ParseError] to throw.
     *
     * [note] is the short remark printed after the caret and [fix] the `= pomoc:` line;
     * both follow Plan.md §10, which asks every diagnostic to say what is wrong and how
     * to repair it. The offending token is not repeated as a note — [render] already
     * quotes the source line.
     */
    internal fun error(
        token: Token,
        message: String,
        code: String = DiagCode.UNEXPECTED_TOKEN,
        note: String? = null,
        fix: String? = null,
        flourish: String? = null,
    ): ParseError {
        reporter.error(
            code = code,
            message = message,
            span = token.span,
            highlight = note,
            fix = fix,
            flourish = flourish,
        )
        return ParseError(token, message)
    }

    internal fun span(start: Token, end: Token): SourceSpan =
        start.span.union(end.span)

    internal fun span(start: Node, end: Node): SourceSpan = start.span.union(end.span)
    internal fun span(start: Node, end: Token): SourceSpan = start.span.union(end.span)
    internal fun span(start: Token, end: Node): SourceSpan = start.span.union(end.span)

    // String template conversion used by ExprParser.
    internal fun stringPartsToTemplate(parts: List<StringPart>): List<TemplatePart> {
        return parts.map { part ->
            when (part) {
                is StringPart.Text -> TemplatePart.Text(part.text)
                is StringPart.Name -> TemplatePart.Interpolation(Expr.NameExpr(part.name, part.span))
                is StringPart.Expr -> {
                    // Seed the sub-lexer at the expression's original file position so
                    // sub-expression spans are absolute, not relative to the substring.
                    val subLexer = krmelin.lexer.Lexer(part.source, file, reporter, part.startLine, part.startCol)
                    val subTokens = subLexer.lex()
                    val subParser = Parser(subTokens, file, reporter)
                    val expr = subParser.parseExpression()
                    // The sub-expression must consume the whole interpolation. Anything
                    // left over would be dropped silently, so the emitted Kotlin would
                    // print something the source never said.
                    subParser.skipNewlines()
                    if (!subParser.isAtEnd()) {
                        subParser.error(
                            subParser.peek(),
                            "unexpected token in string template",
                            code = DiagCode.TEMPLATE_LEFTOVER,
                            note = "this is left over after the interpolated expression",
                            fix = "a '\${...}' holds exactly one expression; split it or remove the extra token",
                        )
                    }
                    TemplatePart.Interpolation(expr)
                }
            }
        }
    }
}
