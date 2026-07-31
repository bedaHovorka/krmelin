package krmelin.parser

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.Node
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.ast.TypeNode
import krmelin.ast.WhenBody
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
        val packageDecl = if (check(TokenType.SACHTA)) parsePackageDecl() else null
        skipNewlines()
        val imports = mutableListOf<Decl.ImportDecl>()
        while (check(TokenType.PRIVEZT)) {
            imports += parseImportDecl()
            skipNewlines()
        }
        val declarations = mutableListOf<Decl>()
        while (!isAtEnd() && !check(TokenType.RBRACE)) {
            try {
                declarations += parseTopLevelDecl()
            } catch (e: ParseError) {
                synchronize()
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
                val prop = parsePropertyDecl()
                expectNewlineOrSemi("property declaration must end with a newline")
                prop
            }
            else -> throw error(peek(), "expected a top-level declaration")
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
        val wildcard = match(TokenType.DOT, TokenType.STAR)
        expectNewlineOrSemi("import declaration must end with a newline")
        return Decl.ImportDecl(name, wildcard, span(start, previous()))
    }

    private fun parseQualifiedName(): List<String> {
        val parts = mutableListOf<String>()
        parts += expectIdentifier("expected an identifier")
        while (match(TokenType.DOT)) {
            parts += expectIdentifier("expected an identifier after '.'")
        }
        return parts
    }

    private fun parseClassDecl(annotations: List<String>): Decl.ClassDecl {
        val start = peek()
        val isData = match(TokenType.ZAPISNIK)
        val isObject = match(TokenType.JEDYNAK)
        val isInterface = match(TokenType.PREDPIS)
        if (!isObject && !isInterface) {
            expect(TokenType.TRYDA, "expected 'tryda', 'jedynak', or 'predpis'")
        }
        val name = expectIdentifier("expected a class name")
        val params = if (isData && check(TokenType.LPAREN)) parseParamList() else emptyList()
        val body = if (check(TokenType.LBRACE)) parseClassBody() else emptyList()
        return Decl.ClassDecl(
            name = name,
            params = params,
            members = body,
            isData = isData,
            isObject = isObject,
            isInterface = isInterface,
            span = span(start, previous()),
        )
    }

    private fun parseClassBody(): List<Decl> {
        val start = expect(TokenType.LBRACE, "expected '{' before class body")
        skipNewlines()
        val members = mutableListOf<Decl>()
        while (!isAtEnd() && !check(TokenType.RBRACE)) {
            try {
                members += parseMember()
            } catch (e: ParseError) {
                synchronize()
            }
            skipNewlines()
        }
        expect(TokenType.RBRACE, "expected '}' after class body")
        return members
    }

    private fun parseMember(): Decl {
        val annotations = parseAnnotations()
        return when (peek().type) {
            TokenType.ROBOTA -> parseFunDecl(annotations)
            TokenType.TOZ,
            TokenType.MOZEJ -> {
                val prop = parsePropertyDecl()
                expectNewlineOrSemi("property declaration must end with a newline")
                prop
            }
            else -> throw error(peek(), "expected a class member")
        }
    }

    private fun parseFunDecl(annotations: List<String>): Decl.FunDecl {
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
        val body = parseFunBody()
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

    private fun parsePropertyDecl(): Decl.PropertyDecl {
        val start = peek()
        val isMutable = when {
            match(TokenType.TOZ) -> false
            match(TokenType.MOZEJ) -> true
            else -> throw error(peek(), "expected 'toz' or 'mozej'")
        }
        val name = expectIdentifier("expected a property name")
        val type = if (match(TokenType.COLON)) parseType() else null
        val initializer = if (match(TokenType.ASSIGN)) parseExpression() else null
        return Decl.PropertyDecl(isMutable, name, type, initializer, span(start, previous()))
    }

    private fun parseType(): TypeNode {
        val start = peek()
        val name = expectIdentifier("expected a type name")
        val nullable = match(TokenType.QUESTION)
        val typeArgs = if (match(TokenType.LT)) parseTypeArguments() else emptyList()
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

    private fun parseFunBody(): FunBody {
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
            try {
                val stmt = parseStatement()
                if (stmt != null) statements += stmt else break
            } catch (e: ParseError) {
                synchronize()
            }
            skipNewlines()
        }
        expect(TokenType.RBRACE, "expected '}' after block")
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
        while (match(TokenType.KAJTEZ)) {
            expect(TokenType.LPAREN, "expected '(' after 'kajtez'")
            skipNewlines()
            val elifCond = parseExpression()
            skipNewlines()
            expect(TokenType.RPAREN, "expected ')' after 'kajtez' condition")
            val elifBlock = parseBlock()
            elseIfs += Stmt.ElseIf(elifCond, elifBlock)
        }
        val elseBlock = if (match(TokenType.BOINAK)) parseBlock() else null
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
            val branch = parseWhenBranch()
            branches += branch
        }
        expect(TokenType.RBRACE, "expected '}' after 'podle_teho' body")
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
        val value = if (check(TokenType.NEWLINE, TokenType.RBRACE, TokenType.EOF)) {
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
        while (match(TokenType.BITKA)) {
            expect(TokenType.LPAREN, "expected '(' after 'bitka'")
            val param = parseParam()
            expect(TokenType.RPAREN, "expected ')' after 'bitka' parameter")
            val catchBlock = parseBlock()
            catches += Stmt.Catch(param, catchBlock)
        }
        val finallyBlock = if (match(TokenType.FAJRONT)) parseBlock() else null
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
        if (check(TokenType.RBRACE, TokenType.EOF)) return
        throw error(peek(), message)
    }

    internal fun skipNewlines() {
        while (check(TokenType.NEWLINE)) advance()
    }

    internal fun currentPosition(): Int = current

    internal fun restorePosition(pos: Int) {
        current = pos
    }

    /**
     * Panic-mode recovery: skip tokens until we reach a likely boundary.
     * Stops at newline, '}', or at the start of a fresh declaration/statement.
     */
    internal fun synchronize() {
        if (isAtEnd()) return
        if (peek().type == TokenType.NEWLINE) {
            advance()
            return
        }
        if (peek().type == TokenType.RBRACE) return
        val syncTokens = setOf(
            TokenType.ROBOTA, TokenType.TRYDA, TokenType.ZAPISNIK,
            TokenType.JEDYNAK, TokenType.PREDPIS, TokenType.KAJ, TokenType.KAJTEZ,
            TokenType.BOINAK, TokenType.PODLE_TEHO, TokenType.PROKAZDY,
            TokenType.RUBAJ, TokenType.PULTIK, TokenType.BITKA, TokenType.FAJRONT,
            TokenType.TOZ, TokenType.MOZEJ, TokenType.DAVAJ, TokenType.ZDYBAT,
            TokenType.DALEJ, TokenType.DOSTANES,
        )
        while (!isAtEnd()) {
            if (peek().type in syncTokens) return
            advance()
            if (peek().type == TokenType.NEWLINE || peek().type == TokenType.RBRACE) return
        }
    }

    internal fun error(token: Token, message: String): ParseError {
        reporter.error(
            code = "E002",
            message = message,
            span = token.span,
            highlight = token.text,
        )
        return ParseError(token, message)
    }

    internal fun span(start: Token, end: Token): SourceSpan =
        start.span.union(end.span)

    internal fun span(token: Token): SourceSpan = token.span

    internal fun span(start: Node, end: Node): SourceSpan = start.span.union(end.span)
    internal fun span(start: Node, end: Token): SourceSpan = start.span.union(end.span)
    internal fun span(start: Token, end: Node): SourceSpan = start.span.union(end.span)

    // String template conversion used by ExprParser.
    internal fun stringPartsToTemplate(parts: List<StringPart>): List<TemplatePart> {
        return parts.map { part ->
            when (part) {
                is StringPart.Text -> TemplatePart.Text(part.text)
                is StringPart.Name -> TemplatePart.Interpolation(Expr.NameExpr(part.name, SourceSpan.NONE))
                is StringPart.Expr -> {
                    val subLexer = krmelin.lexer.Lexer(part.source, file, reporter)
                    val subTokens = subLexer.lex()
                    val subParser = Parser(subTokens, file, reporter)
                    TemplatePart.Interpolation(subParser.parseExpression())
                }
            }
        }
    }
}
