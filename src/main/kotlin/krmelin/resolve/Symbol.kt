package krmelin.resolve

import krmelin.ast.Decl
import krmelin.lexer.SourceSpan
import krmelin.types.KType

/**
 * A named entity the resolver can bind a [krmelin.ast.Expr.NameExpr] to.
 *
 * Symbols of user code hold a reference back to their AST declaration ([decl]); prelude
 * symbols are synthetic, carry [SourceSpan.NONE], and have a `null` decl.
 */
sealed class Symbol {
    abstract val name: String
    abstract val span: SourceSpan

    /** A `toz`/`mozej` binding — property, local variable, or loop variable. */
    data class Variable(
        override val name: String,
        override val span: SourceSpan,
        val isMutable: Boolean,
        /** Declared or inferred type; filled in by the type checker when not annotated. */
        var type: KType? = null,
        val decl: Decl.PropertyDecl? = null,
    ) : Symbol()

    /** A function parameter. */
    data class Parameter(
        override val name: String,
        override val span: SourceSpan,
        val isMutable: Boolean,
        val type: KType?,
        val hasDefault: Boolean,
        val decl: Decl.Param? = null,
    ) : Symbol()

    /** A `robota` — user function or prelude function/method. */
    data class Function(
        override val name: String,
        override val span: SourceSpan,
        val params: List<ParamSig>,
        /** Declared return type; `null` in source means the unit type [KType.NIC]. */
        val returnType: KType?,
        /** The AST function declaration, or null for prelude symbols. */
        val decl: Decl.FunDecl? = null,
    ) : Symbol()

    /**
     * A type name: prelude alias (`Dryst` → `String`), prelude class (`Flakanec`), or a
     * user `tryda`/`zapisnik`/`jedynak`/`predpis`. [members] powers `.`-access checking
     * (exact matches only) for both user classes and prelude types.
     */
    data class TypeName(
        override val name: String,
        override val span: SourceSpan,
        /** The type this name denotes (aliases point at their target's [KType]). */
        val type: KType,
        /** Number of type parameters the name takes (`Halda` 1, `Kupa` 2, plain types 0). */
        val typeArity: Int = 0,
        val decl: Decl.ClassDecl? = null,
        val members: Map<String, Symbol> = emptyMap(),
        /** Constructor signature for callable type names (`Flakanec("…")`), if any. */
        val constructor: Function? = null,
    ) : Symbol()
}

/** Parameter signature as the checker sees it at a call site. */
data class ParamSig(val name: String, val type: KType?, val hasDefault: Boolean = false)
