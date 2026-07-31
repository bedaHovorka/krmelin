package krmelin.parser

import krmelin.ast.Expr
import krmelin.ast.LambdaBody
import krmelin.ast.Stmt
import krmelin.lexer.StringValue
import krmelin.lexer.Token
import krmelin.lexer.TokenType

/**
 * Pratt parser for Krmelin expressions.
 *
 * Operator precedence (lowest to highest), matching Plan.md §4.3's EBNF
 * (`comparison = elvis {...}`, `elvis = additive [...]`) — elvis binds
 * tighter than comparison/equality/logical operators but looser than additive:
 * 1. assignment (=)        right-associative
 * 2. logical or (ci)       left-associative
 * 3. logical and (aj)      left-associative
 * 4. equality (==, !=)     left-associative
 * 5. comparison (<, >, <=, >=) left-associative
 * 6. elvis (?:)            right-associative
 * 7. additive (+, -)     left-associative
 * 8. multiplicative (*, /, %) left-associative
 * 9. unary (!, -)          right-associative
 * 10. postfix (call, member, safe member) left-associative
 */
class ExprParser(private val parser: Parser) {

    private data class InfixInfo(
        val precedence: Int,
        val rightAssoc: Boolean,
        val postfix: Boolean = false,
    )

    private fun infixInfo(type: TokenType): InfixInfo? = when (type) {
        TokenType.ASSIGN -> InfixInfo(1, rightAssoc = true)
        TokenType.CI -> InfixInfo(2, rightAssoc = false)
        TokenType.AJ -> InfixInfo(3, rightAssoc = false)
        TokenType.EQ, TokenType.NEQ -> InfixInfo(4, rightAssoc = false)
        TokenType.LT, TokenType.GT, TokenType.LE, TokenType.GE -> InfixInfo(5, rightAssoc = false)
        TokenType.ELVIS -> InfixInfo(6, rightAssoc = true)
        TokenType.PLUS, TokenType.MINUS -> InfixInfo(7, rightAssoc = false)
        TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> InfixInfo(8, rightAssoc = false)
        TokenType.LPAREN, TokenType.DOT, TokenType.SAFE_DOT -> InfixInfo(10, rightAssoc = false, postfix = true)
        else -> null
    }

    fun parseExpression(precedence: Int = 0): Expr {
        parser.skipNewlines()
        // Peek before advancing: if there is no prefix parser for the current token
        // (e.g. a structural '}', ')' or EOF), throw without consuming it so panic-mode
        // recovery can resync at the block boundary instead of swallowing it.
        val token = parser.peek()
        val prefix = prefixParser(token)
            ?: throw parser.error(token, "expected expression")
        parser.advance()
        var left = prefix(token)

        while (true) {
            val next = parser.peek()
            val info = infixInfo(next.type) ?: break
            if (info.precedence < precedence) break

            parser.advance()
            left = if (info.postfix) {
                postfixParser(next, left)
            } else {
                val rightPrecedence = if (info.rightAssoc) info.precedence else info.precedence + 1
                val right = parseExpression(rightPrecedence)
                infixParser(next, left, right)
            }
        }

        return left
    }

    private fun prefixParser(token: Token): ((Token) -> Expr)? = when (token.type) {
        TokenType.INTEGER_LITERAL -> { t -> Expr.IntLit(t.value as Long, t.span) }
        TokenType.FLOAT_LITERAL -> { t -> Expr.FloatLit(t.value as Double, t.span) }
        TokenType.STRING_LITERAL -> { t ->
            when (val v = t.value) {
                is StringValue.Plain -> Expr.StringLit(v.text, t.span)
                is StringValue.Template -> Expr.StringTemplate(parser.stringPartsToTemplate(v.parts), t.span)
                else -> Expr.StringLit("", t.span)
            }
        }
        TokenType.FAJNE -> { t -> Expr.BoolLit(true, t.span) }
        TokenType.NYT -> { t -> Expr.BoolLit(false, t.span) }
        TokenType.NULL -> { t -> Expr.NullLit(t.span) }
        TokenType.IDENTIFIER -> { t -> Expr.NameExpr(t.text, t.span) }
        TokenType.LPAREN -> { t ->
            parser.skipNewlines()
            val expr = parseExpression()
            parser.skipNewlines()
            val close = parser.expect(TokenType.RPAREN, "expected ')' after expression")
            Expr.ParenExpr(expr, parser.span(t, close))
        }
        TokenType.LBRACE -> { t -> parseLambda(t) }
        TokenType.BANG, TokenType.MINUS -> { t ->
            val operand = parseExpression(9)
            Expr.UnaryExpr(t.type, operand, parser.span(t, operand))
        }
        else -> null
    }

    private fun infixParser(operator: Token, left: Expr, right: Expr): Expr {
        val span = parser.span(left, right)
        return when (operator.type) {
            TokenType.ASSIGN -> Expr.AssignExpr(left, right, span)
            TokenType.ELVIS -> Expr.ElvisExpr(left, right, span)
            TokenType.CI,
            TokenType.AJ,
            TokenType.EQ, TokenType.NEQ,
            TokenType.LT, TokenType.GT, TokenType.LE, TokenType.GE,
            TokenType.PLUS, TokenType.MINUS,
            TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> Expr.BinaryExpr(operator.type, left, right, span)
            else -> throw parser.error(operator, "unexpected operator '${operator.text}'")
        }
    }

    private fun postfixParser(operator: Token, left: Expr): Expr = when (operator.type) {
        TokenType.DOT -> {
            val name = parser.expectIdentifier("expected property or function name after '.'")
            Expr.MemberExpr(left, name, parser.span(left, parser.previous()))
        }
        TokenType.SAFE_DOT -> {
            val name = parser.expectIdentifier("expected property or function name after '?.'")
            Expr.SafeMemberExpr(left, name, parser.span(left, parser.previous()))
        }
        TokenType.LPAREN -> {
            val args = parseArguments()
            val close = parser.expect(TokenType.RPAREN, "expected ')' after arguments")
            Expr.CallExpr(left, args, parser.span(left, close))
        }
        else -> throw parser.error(operator, "unexpected postfix operator '${operator.text}'")
    }

    private fun parseArguments(): List<Expr> {
        val args = mutableListOf<Expr>()
        parser.skipNewlines()
        if (!parser.check(TokenType.RPAREN)) {
            do {
                parser.skipNewlines()
                args += parseExpression()
                parser.skipNewlines()
            } while (parser.match(TokenType.COMMA))
        }
        return args
    }

    private fun parseLambda(start: Token): Expr.LambdaExpr {
        val params = mutableListOf<String>()

        // Try to read an explicit parameter list: ident { , ident } ->
        val saved = parser.currentPosition()
        parser.skipNewlines()
        var hasArrow = false
        if (parser.check(TokenType.IDENTIFIER)) {
            while (true) {
                val name = parser.advance().text
                params += name
                parser.skipNewlines()
                when {
                    parser.match(TokenType.ARROW) -> {
                        hasArrow = true
                        break
                    }
                    parser.match(TokenType.COMMA) -> {
                        parser.skipNewlines()
                        // Past a comma this is committed to being a parameter list —
                        // '{ a, b }' is not a valid statement sequence, so there is no
                        // other reading to back off to. Anything but an identifier here
                        // is an error, not a reason to reinterpret the braces as a body.
                        if (!parser.check(TokenType.IDENTIFIER)) {
                            throw parser.error(parser.peek(), "expected a lambda parameter name")
                        }
                        continue
                    }
                    else -> break
                }
            }
        }
        if (!hasArrow) {
            parser.restorePosition(saved)
            params.clear()
        }

        // Parse the body as a block of statements until '}', recovering per statement the
        // way Parser.parseBlock does. Without this a ParseError escapes past the lambda's
        // own '}', so the enclosing block consumes the wrong brace and every declaration
        // after it is reparented or lost.
        val bodyStmts = mutableListOf<Stmt>()
        while (!parser.isAtEnd() && !parser.check(TokenType.RBRACE)) {
            val before = parser.currentPosition()
            try {
                val stmt = parser.parseStatement() ?: break
                bodyStmts += stmt
            } catch (e: ParseError) {
                parser.synchronizeFrom(before)
            }
            parser.skipNewlines()
        }
        val close = parser.expect(TokenType.RBRACE, "expected '}' after lambda body")
        val block = Stmt.Block(bodyStmts, parser.span(start, close))
        return Expr.LambdaExpr(params, LambdaBody.BlockBody(block), parser.span(start, close))
    }
}
