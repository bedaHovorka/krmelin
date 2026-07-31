package krmelin.ast

import krmelin.lexer.SourceSpan

/** A part of a string template. */
sealed class TemplatePart {
    data class Text(val text: String) : TemplatePart()
    data class Interpolation(val expr: Expr) : TemplatePart()
}

/** Expression hierarchy. */
sealed class Expr(override val span: SourceSpan) : Node(span) {
    data class IntLit(val value: Long, override val span: SourceSpan) : Expr(span)
    data class FloatLit(val value: Double, override val span: SourceSpan) : Expr(span)
    data class StringLit(val value: String, override val span: SourceSpan) : Expr(span)
    data class StringTemplate(val parts: List<TemplatePart>, override val span: SourceSpan) : Expr(span)
    data class BoolLit(val value: Boolean, override val span: SourceSpan) : Expr(span)
    data class NullLit(override val span: SourceSpan) : Expr(span)

    data class NameExpr(val name: String, override val span: SourceSpan) : Expr(span)
    data class BinaryExpr(
        val op: krmelin.lexer.TokenType,
        val left: Expr,
        val right: Expr,
        override val span: SourceSpan,
    ) : Expr(span)

    data class UnaryExpr(
        val op: krmelin.lexer.TokenType,
        val operand: Expr,
        override val span: SourceSpan,
    ) : Expr(span)

    data class CallExpr(val callee: Expr, val args: List<Expr>, override val span: SourceSpan) : Expr(span)
    data class MemberExpr(val receiver: Expr, val name: String, override val span: SourceSpan) : Expr(span)
    data class SafeMemberExpr(val receiver: Expr, val name: String, override val span: SourceSpan) : Expr(span)
    data class ElvisExpr(val left: Expr, val right: Expr, override val span: SourceSpan) : Expr(span)
    data class AssignExpr(val target: Expr, val value: Expr, override val span: SourceSpan) : Expr(span)

    data class LambdaExpr(
        val params: List<String>,
        val body: LambdaBody,
        override val span: SourceSpan,
    ) : Expr(span)

    data class ParenExpr(val expr: Expr, override val span: SourceSpan) : Expr(span)
}

sealed class LambdaBody {
    data class ExprBody(val expr: Expr) : LambdaBody()
    data class BlockBody(val block: Stmt.Block) : LambdaBody()
}
