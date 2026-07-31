package krmelin.ast

import krmelin.lexer.SourceSpan

/** Statement hierarchy. */
sealed class Stmt(override val span: SourceSpan) : Node(span) {
    data class Block(val statements: List<Stmt>, override val span: SourceSpan) : Stmt(span)
    data class PropertyStmt(val decl: Decl.PropertyDecl, override val span: SourceSpan) : Stmt(span)

    data class IfStmt(
        val condition: Expr,
        val thenBlock: Block,
        val elseIfs: List<ElseIf> = emptyList(),
        val elseBlock: Block? = null,
        override val span: SourceSpan,
    ) : Stmt(span)

    data class ElseIf(val condition: Expr, val block: Block)

    data class WhenStmt(
        val subject: Expr?,
        val branches: List<WhenBranch>,
        override val span: SourceSpan,
    ) : Stmt(span)

    data class WhenBranch(
        val conditions: List<Expr>,
        val isElse: Boolean,
        val body: WhenBody,
    )

    data class ForStmt(
        val name: String,
        val iterable: Expr,
        val body: Block,
        override val span: SourceSpan,
    ) : Stmt(span)

    data class WhileStmt(
        val condition: Expr,
        val body: Block,
        override val span: SourceSpan,
    ) : Stmt(span)

    data class ReturnStmt(val value: Expr?, override val span: SourceSpan) : Stmt(span)
    data class BreakStmt(override val span: SourceSpan) : Stmt(span)
    data class ContinueStmt(override val span: SourceSpan) : Stmt(span)

    data class TryStmt(
        val block: Block,
        val catches: List<Catch>,
        val finallyBlock: Block?,
        override val span: SourceSpan,
    ) : Stmt(span)

    data class Catch(val param: Decl.Param, val block: Block)

    data class ThrowStmt(val expr: Expr, override val span: SourceSpan) : Stmt(span)
    data class ExprStmt(val expr: Expr, override val span: SourceSpan) : Stmt(span)
}

sealed class WhenBody {
    data class ExprBody(val expr: Expr) : WhenBody()
    data class BlockBody(val block: Stmt.Block) : WhenBody()
}
