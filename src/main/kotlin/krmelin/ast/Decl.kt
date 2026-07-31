package krmelin.ast

import krmelin.lexer.SourceSpan

/** Declaration hierarchy. */
sealed class Decl(override val span: SourceSpan) : Node(span) {
    data class CompilationUnit(
        val packageDecl: PackageDecl?,
        val imports: List<ImportDecl>,
        val declarations: List<Decl>,
        override val span: SourceSpan,
    ) : Decl(span)

    data class PackageDecl(
        val name: List<String>,
        override val span: SourceSpan,
    ) : Decl(span)

    data class ImportDecl(
        val name: List<String>,
        val wildcard: Boolean,
        override val span: SourceSpan,
    ) : Decl(span)

    data class ClassDecl(
        val name: String,
        val params: List<Param>,
        val members: List<Decl>,
        val isData: Boolean = false,
        val isObject: Boolean = false,
        val isInterface: Boolean = false,
        val annotations: List<String> = emptyList(),
        override val span: SourceSpan,
    ) : Decl(span) {
        /** `@Parta` marks a test-suite group for PorubaUnit discovery. */
        val isParta: Boolean get() = annotations.contains("Parta")
    }

    data class FunDecl(
        val annotations: List<String>,
        val name: String,
        val params: List<Param>,
        val returnType: TypeNode?,
        val throwsTypes: List<TypeNode>,
        val body: FunBody,
        override val span: SourceSpan,
    ) : Decl(span) {
        val isTest: Boolean get() = annotations.contains("Sichta")
    }

    data class PropertyDecl(
        val isMutable: Boolean,
        val name: String,
        val type: TypeNode?,
        val initializer: Expr?,
        val annotations: List<String> = emptyList(),
        override val span: SourceSpan,
    ) : Decl(span)

    data class Param(
        val name: String,
        val type: TypeNode,
        val defaultValue: Expr?,
        val isMutable: Boolean = false,
        override val span: SourceSpan,
    ) : Decl(span)
}

sealed class FunBody {
    data class BlockBody(val block: Stmt.Block) : FunBody()
    data class ExprBody(val expr: Expr) : FunBody()
}
