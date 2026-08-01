package krmelin.lower

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.LambdaBody
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.ast.TypeNode
import krmelin.ast.WhenBody
import krmelin.codegen.KotlinPrelude
import krmelin.resolve.Resolution
import krmelin.types.KType

/**
 * Desugaring pass between the type checker and the emitter (Plan.md §5.6).
 *
 * Much of §5.6's desugaring already happened in the parser: subject-less `podle_teho`
 * parses straight to `WhenStmt(subject = null)`, `prokazdy (x v xs)` to `ForStmt`, and
 * string templates arrive normalized as `StringTemplate` parts. What genuinely remains
 * for a typed pass:
 *
 * 1. `.dylka` member access → `.length` (Dryst receivers) or `.size` (Halda/Kupa
 *    receivers), decided from `Resolution.exprTypes`. UNKNOWN receivers (the checker
 *    deliberately deferred, e.g. wildcard-imported collections) emit `.size` — a
 *    documented fallback, see docs/language-spec.md known limitations.
 * 2. `x.naDryst()` → `x.toString()` when the receiver has a prelude-known type; a
 *    user-defined `naDryst` is never touched.
 * 3. Computes [Lowered.usesFlakanci] — whether the unit references the Flakanci
 *    exception system at all — which gates both the emitted runtime import and the
 *    backend's inclusion of the Flakanci.kt runtime source.
 *
 * Rewrites go through `copy(...)` so untouched children keep object identity and every
 * IdentityHashMap in [Resolution] still resolves for them.
 */
class Lowering(private val resolution: Resolution) {

    /** A lowered compilation unit plus the flags the emitter and backend need. */
    data class Lowered(
        val unit: Decl.CompilationUnit,
        val usesFlakanci: Boolean,
    )

    fun lower(unit: Decl.CompilationUnit): Lowered =
        Lowered(
            unit = transformUnit(unit),
            usesFlakanci = usesFlakanci(unit),
        )

    // ── Flakanci usage detection ───────────────────────────────────────────

    private var foundFlakanci = false

    private fun usesFlakanci(unit: Decl.CompilationUnit): Boolean {
        foundFlakanci = false
        unit.declarations.forEach(::scanDecl)
        return foundFlakanci
    }

    private fun scanType(node: TypeNode) {
        if (node is TypeNode.NamedType) {
            if (node.name in KotlinPrelude.FLAKANCI_TYPE_NAMES) foundFlakanci = true
            node.typeArgs.forEach(::scanType)
        }
    }

    private fun scanExpr(e: Expr) {
        when (e) {
            is Expr.BinaryExpr -> { scanExpr(e.left); scanExpr(e.right) }
            is Expr.UnaryExpr -> scanExpr(e.operand)
            is Expr.CallExpr -> { scanExpr(e.callee); e.args.forEach(::scanExpr) }
            is Expr.MemberExpr -> scanExpr(e.receiver)
            is Expr.SafeMemberExpr -> scanExpr(e.receiver)
            is Expr.ElvisExpr -> { scanExpr(e.left); scanExpr(e.right) }
            is Expr.AssignExpr -> { scanExpr(e.target); scanExpr(e.value) }
            is Expr.LambdaExpr -> when (val b = e.body) {
                is LambdaBody.ExprBody -> scanExpr(b.expr)
                is LambdaBody.BlockBody -> scanBlock(b.block)
            }
            is Expr.ParenExpr -> scanExpr(e.expr)
            is Expr.StringTemplate -> e.parts.forEach {
                if (it is TemplatePart.Interpolation) scanExpr(it.expr)
            }
            else -> {}
        }
    }

    private fun scanBlock(b: Stmt.Block) = b.statements.forEach(::scanStmt)

    private fun scanStmt(s: Stmt) {
        when (s) {
            is Stmt.TryStmt -> {
                foundFlakanci = true
                scanBlock(s.block)
                s.catches.forEach {
                    scanType(it.param.type)
                    it.param.defaultValue?.let(::scanExpr)
                    scanBlock(it.block)
                }
                s.finallyBlock?.let(::scanBlock)
            }
            is Stmt.ThrowStmt -> { foundFlakanci = true; scanExpr(s.expr) }
            is Stmt.Block -> scanBlock(s)
            is Stmt.PropertyStmt -> {
                s.decl.type?.let(::scanType)
                s.decl.initializer?.let(::scanExpr)
            }
            is Stmt.IfStmt -> {
                scanExpr(s.condition); scanBlock(s.thenBlock)
                s.elseIfs.forEach { scanExpr(it.condition); scanBlock(it.block) }
                s.elseBlock?.let(::scanBlock)
            }
            is Stmt.WhenStmt -> {
                s.subject?.let(::scanExpr)
                s.branches.forEach {
                    it.conditions.forEach(::scanExpr)
                    when (val body = it.body) {
                        is WhenBody.ExprBody -> scanExpr(body.expr)
                        is WhenBody.BlockBody -> scanBlock(body.block)
                    }
                }
            }
            is Stmt.ForStmt -> { scanExpr(s.iterable); scanBlock(s.body) }
            is Stmt.WhileStmt -> { scanExpr(s.condition); scanBlock(s.body) }
            is Stmt.ReturnStmt -> s.value?.let(::scanExpr)
            is Stmt.ExprStmt -> scanExpr(s.expr)
            else -> {}
        }
    }

    private fun scanDecl(d: Decl) {
        when (d) {
            is Decl.FunDecl -> {
                d.throwsTypes.forEach(::scanType)
                d.params.forEach { scanType(it.type); it.defaultValue?.let(::scanExpr) }
                d.returnType?.let(::scanType)
                when (val b = d.body) {
                    is FunBody.ExprBody -> scanExpr(b.expr)
                    is FunBody.BlockBody -> scanBlock(b.block)
                    null -> {}
                }
            }
            is Decl.ClassDecl -> {
                d.params.forEach { scanType(it.type); it.defaultValue?.let(::scanExpr) }
                d.members.forEach(::scanDecl)
            }
            is Decl.PropertyDecl -> {
                d.type?.let(::scanType)
                d.initializer?.let(::scanExpr)
            }
            else -> {}
        }
    }

    // ── Member renames ─────────────────────────────────────────────────────

    private fun transformUnit(unit: Decl.CompilationUnit): Decl.CompilationUnit =
        unit.copy(declarations = unit.declarations.map(::transformDecl))

    private fun transformDecl(d: Decl): Decl = when (d) {
        is Decl.FunDecl -> d.copy(
            params = d.params.map { it.copy(defaultValue = it.defaultValue?.let(::transformExpr)) },
            body = when (val b = d.body) {
                is FunBody.ExprBody -> FunBody.ExprBody(transformExpr(b.expr))
                is FunBody.BlockBody -> FunBody.BlockBody(transformBlock(b.block))
                null -> null
            },
        )
        is Decl.ClassDecl -> d.copy(
            params = d.params.map { it.copy(defaultValue = it.defaultValue?.let(::transformExpr)) },
            members = d.members.map(::transformDecl),
        )
        is Decl.PropertyDecl -> d.copy(initializer = d.initializer?.let(::transformExpr))
        else -> d
    }

    private fun transformBlock(b: Stmt.Block): Stmt.Block =
        b.copy(statements = b.statements.map(::transformStmt))

    private fun transformStmt(s: Stmt): Stmt = when (s) {
        is Stmt.Block -> transformBlock(s)
        is Stmt.PropertyStmt -> s.copy(decl = transformDecl(s.decl) as Decl.PropertyDecl)
        is Stmt.IfStmt -> s.copy(
            condition = transformExpr(s.condition),
            thenBlock = transformBlock(s.thenBlock),
            elseIfs = s.elseIfs.map { it.copy(condition = transformExpr(it.condition), block = transformBlock(it.block)) },
            elseBlock = s.elseBlock?.let(::transformBlock),
        )
        is Stmt.WhenStmt -> s.copy(
            subject = s.subject?.let(::transformExpr),
            branches = s.branches.map { branch ->
                branch.copy(
                    conditions = branch.conditions.map(::transformExpr),
                    body = when (val body = branch.body) {
                        is WhenBody.ExprBody -> WhenBody.ExprBody(transformExpr(body.expr))
                        is WhenBody.BlockBody -> WhenBody.BlockBody(transformBlock(body.block))
                    },
                )
            },
        )
        is Stmt.ForStmt -> s.copy(iterable = transformExpr(s.iterable), body = transformBlock(s.body))
        is Stmt.WhileStmt -> s.copy(condition = transformExpr(s.condition), body = transformBlock(s.body))
        is Stmt.ReturnStmt -> s.copy(value = s.value?.let(::transformExpr))
        is Stmt.ExprStmt -> s.copy(expr = transformExpr(s.expr))
        is Stmt.ThrowStmt -> s.copy(expr = transformExpr(s.expr))
        is Stmt.TryStmt -> s.copy(
            block = transformBlock(s.block),
            catches = s.catches.map { it.copy(block = transformBlock(it.block)) },
            finallyBlock = s.finallyBlock?.let(::transformBlock),
        )
        is Stmt.BreakStmt, is Stmt.ContinueStmt -> s
    }

    private fun transformExpr(e: Expr): Expr = when (e) {
        is Expr.MemberExpr ->
            e.copy(receiver = transformExpr(e.receiver), name = dylkaTarget(e)?.let { it } ?: e.name)
        is Expr.SafeMemberExpr ->
            e.copy(receiver = transformExpr(e.receiver), name = dylkaTarget(e)?.let { it } ?: e.name)
        is Expr.CallExpr -> when {
            // `x.naDryst()` → `x.toString()` for prelude-known receivers only.
            e.callee is Expr.MemberExpr &&
                e.callee.name == KotlinPrelude.STRINGIFY_MEMBER &&
                isPreludeKnown(resolution.typeOf(e.callee.receiver)) ->
                e.copy(
                    callee = e.callee.copy(
                        receiver = transformExpr(e.callee.receiver),
                        name = KotlinPrelude.TO_STRING_MEMBER,
                    ),
                    args = e.args.map(::transformExpr),
                )
            else -> e.copy(callee = transformExpr(e.callee), args = e.args.map(::transformExpr))
        }
        is Expr.BinaryExpr -> e.copy(left = transformExpr(e.left), right = transformExpr(e.right))
        is Expr.UnaryExpr -> e.copy(operand = transformExpr(e.operand))
        is Expr.ElvisExpr -> e.copy(left = transformExpr(e.left), right = transformExpr(e.right))
        is Expr.AssignExpr -> e.copy(target = transformExpr(e.target), value = transformExpr(e.value))
        is Expr.LambdaExpr -> e.copy(
            body = when (val b = e.body) {
                is LambdaBody.ExprBody -> LambdaBody.ExprBody(transformExpr(b.expr))
                is LambdaBody.BlockBody -> LambdaBody.BlockBody(transformBlock(b.block))
            },
        )
        is Expr.ParenExpr -> e.copy(expr = transformExpr(e.expr))
        is Expr.StringTemplate -> e.copy(
            parts = e.parts.map {
                if (it is TemplatePart.Interpolation) it.copy(expr = transformExpr(it.expr)) else it
            },
        )
        else -> e
    }

    /**
     * `.dylka` → the Kotlin property for the receiver's type; `null` for any other
     * member name. String-like receivers take `length`, collections take `size`, and
     * UNKNOWN receivers (checker deferred, e.g. wildcard imports) default to `size`.
     */
    private fun dylkaTarget(e: Expr): String? {
        val (receiver, member) = when (e) {
            is Expr.MemberExpr -> e.receiver to e.name
            is Expr.SafeMemberExpr -> e.receiver to e.name
            else -> return null
        }
        if (member != KotlinPrelude.LENGTH_MEMBER) return null
        val t = resolution.typeOf(receiver)
        return when {
            t.kind == KType.Kind.UNKNOWN -> KotlinPrelude.SIZE_PROPERTY
            t.kotlinName == "List" || t.kotlinName == "Map" -> KotlinPrelude.SIZE_PROPERTY
            else -> KotlinPrelude.LENGTH_PROPERTY
        }
    }

    /** Prelude-owned types carry no [KType.declId]; user types are never renamed. */
    private fun isPreludeKnown(t: KType): Boolean =
        t.declId == null && t.kind != KType.Kind.UNKNOWN && t.kind != KType.Kind.NULA
}
