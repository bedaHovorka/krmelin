package krmelin.resolve

import java.util.IdentityHashMap
import krmelin.ast.Expr
import krmelin.ast.Node
import krmelin.types.KType

/**
 * The semantic side-tables for one compilation unit (Plan.md §5: "AST + SymbolTable").
 *
 * AST nodes stay immutable; what the resolver and type checker learn about them lives here:
 *
 * - [bindings] — which [Symbol] each name occurrence refers to (identity-keyed, because
 *   AST data classes compare by value and two identical `NameExpr("x")` nodes in different
 *   scopes must not collapse into one binding).
 * - [exprTypes] — the type each expression was inferred to have; the type checker fills
 *   this in and M4's emitter reads it back (e.g. for `.dylka` lowering).
 */
class Resolution(val fileScope: Scope) {
    val bindings: IdentityHashMap<Expr.NameExpr, Symbol> = IdentityHashMap()
    val exprTypes: IdentityHashMap<Expr, KType> = IdentityHashMap()

    /** Which [Symbol] each declaration node was declared as (properties, functions, params…). */
    val declarations: IdentityHashMap<Node, Symbol> = IdentityHashMap()

    fun typeOf(expr: Expr): KType = exprTypes[expr] ?: KType.UNKNOWN
}
