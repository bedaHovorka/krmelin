package krmelin.resolve

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.LambdaBody
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.ast.TypeNode
import krmelin.diag.DiagCode
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.SourceSpan
import krmelin.types.KType

/**
 * Binds names to declarations (Plan.md §5).
 *
 * Each scope level is processed in two passes — declare everything, then bind bodies — so
 * forward references between top-level declarations and between class members resolve just
 * like in Kotlin. Undeclared names are reported (HAV220, with a did-you-mean hint) but never
 * abort the walk: the type checker treats an unbound name as [KType.UNKNOWN] and stays quiet.
 */
class Resolver(private val reporter: DiagnosticReporter) {
    private val table = SymbolTable(Prelude.scope)
    private lateinit var resolution: Resolution

    /** One resolution per type node — the declare and bind passes revisit the same nodes. */
    private val typeCache = java.util.IdentityHashMap<TypeNode, KType?>()

    fun resolve(unit: Decl.CompilationUnit): Resolution {
        val fileScope = table.newFileScope()
        resolution = Resolution(fileScope)
        declareAll(unit.declarations, fileScope)
        for (decl in unit.declarations) bindDecl(decl, fileScope)
        return resolution
    }

    // ── Declaration pass ─────────────────────────────────────────────────────

    private fun declareAll(decls: List<Decl>, scope: Scope) {
        // Classes first so function signatures can name them in any order.
        for (decl in decls.filterIsInstance<Decl.ClassDecl>()) declareClass(decl, scope)
        for (decl in decls) when (decl) {
            is Decl.ClassDecl -> Unit // already declared
            is Decl.FunDecl -> declare(functionSymbol(decl, scope), scope)
            is Decl.PropertyDecl -> declare(variableSymbol(decl, scope), scope)
            else -> Unit
        }
    }

    private fun declareClass(decl: Decl.ClassDecl, scope: Scope) {
        val classScope = Scope(parent = scope, kind = Scope.Kind.CLASS)
        val members = linkedMapOf<String, Symbol>()
        fun addMember(symbol: Symbol) {
            val declNode = when (symbol) {
                is Symbol.Variable -> symbol.decl
                is Symbol.Parameter -> symbol.decl
                is Symbol.Function -> symbol.decl
                is Symbol.TypeName -> symbol.decl
            }
            if (declNode != null) resolution.declarations[declNode] = symbol
            val existing = members.putIfAbsent(symbol.name, symbol) ?: classScope.declare(symbol)
            if (existing != null) reportDuplicate(symbol)
        }
        // Constructor params double as immutable/mutable members (zapisnik/tryda).
        for (param in decl.params) addMember(paramSymbol(param, scope))
        for (member in decl.members) when (member) {
            is Decl.FunDecl -> addMember(functionSymbol(member, classScope))
            is Decl.PropertyDecl -> addMember(variableSymbol(member, classScope))
            else -> Unit
        }
        val symbol = Symbol.TypeName(
            decl.name, decl.span,
            KType(decl.name, decl.name, kind = KType.Kind.DECLARED),
            decl = decl,
            members = members,
        )
        declare(symbol, scope)
    }

    private fun functionSymbol(decl: Decl.FunDecl, scope: Scope) = Symbol.Function(
        decl.name, decl.span,
        params = decl.params.map { ParamSig(it.name, typeFor(it.type, scope), it.defaultValue != null) },
        returnType = decl.returnType?.let { typeFor(it, scope) },
        decl = decl,
    )

    private fun variableSymbol(decl: Decl.PropertyDecl, scope: Scope) = Symbol.Variable(
        decl.name, decl.span,
        isMutable = decl.isMutable,
        type = decl.type?.let { typeFor(it, scope) },
        decl = decl,
    )

    private fun paramSymbol(param: Decl.Param, scope: Scope) = Symbol.Parameter(
        param.name, param.span,
        isMutable = param.isMutable,
        type = typeFor(param.type, scope),
        hasDefault = param.defaultValue != null,
        decl = param,
    )

    private fun declare(symbol: Symbol, scope: Scope) {
        val declNode = when (symbol) {
            is Symbol.Variable -> symbol.decl
            is Symbol.Parameter -> symbol.decl
            is Symbol.Function -> symbol.decl
            is Symbol.TypeName -> symbol.decl
        }
        if (declNode != null) resolution.declarations[declNode] = symbol
        val existing = scope.declare(symbol)
        if (existing != null) reportDuplicate(symbol, existing)
        else warnShadowing(symbol, scope)
    }

    private fun reportDuplicate(symbol: Symbol, existing: Symbol? = null) {
        reporter.error(
            DiagCode.DUPLICATE_DECLARATION,
            "'${symbol.name}' je tu deklarovany podruhy",
            symbol.span,
            highlight = "tohle jme no tu u z je",
            fix = existing?.let { "prejmenuj jedno z nich; prvni deklarace: ${it.span.startLine}:${it.span.startCol}" },
            flourish = "dva krale na jednym trunu nesedza",
        )
    }

    private fun warnShadowing(symbol: Symbol, scope: Scope) {
        val outer = scope.shadowedBy(symbol.name) ?: return
        if (outer.span == SourceSpan.NONE) return // shadowing a prelude name is fine
        reporter.warning(
            DiagCode.SHADOWED_DECLARATION,
            "'${symbol.name}' zastira vnejsi deklaraci z ${outer.span.startLine}:${outer.span.startCol}",
            symbol.span,
            highlight = "tohle jme no uz vnejsi deklarace ma",
            fix = "prejmenuj vnitrni '${symbol.name}', at je jasne, co je co",
        )
    }

    // ── Type names ──────────────────────────────────────────────────────────

    fun typeFor(node: TypeNode, scope: Scope): KType? =
        if (typeCache.containsKey(node)) typeCache[node] else resolveType(node, scope)
            .also { typeCache[node] = it }

    private fun resolveType(node: TypeNode, scope: Scope): KType? = when (node) {
        is TypeNode.NamedType -> {
            val symbol = scope.lookup(node.name)
            if (symbol !is Symbol.TypeName) {
                reporter.error(
                    DiagCode.UNKNOWN_TYPE_NAME,
                    "typ '${node.name}' neni deklarovany",
                    node.span,
                    highlight = "takovy typ tu neni",
                    fix = "myslel si treba 'Dryst', 'Cyslo' abo 'Bul'?",
                )
                null
            } else {
                val args = node.typeArgs.mapNotNull { typeFor(it, scope) }
                if (args.size != node.typeArgs.size) null
                else symbol.type.copy(nullable = node.nullable, typeArgs = args)
            }
        }
    }

    // ── Binding pass ─────────────────────────────────────────────────────────

    private fun bindDecl(decl: Decl, scope: Scope) {
        when (decl) {
            is Decl.ClassDecl -> {
                val classScope = Scope(parent = scope, kind = Scope.Kind.CLASS)
                for (param in decl.params) classScope.declare(paramSymbol(param, scope))
                for (member in decl.members) when (member) {
                    is Decl.FunDecl -> classScope.declare(functionSymbol(member, classScope))
                    is Decl.PropertyDecl -> classScope.declare(variableSymbol(member, classScope))
                    else -> Unit
                }
                for (member in decl.members) bindDecl(member, classScope)
            }
            is Decl.FunDecl -> bindFunction(decl, scope)
            is Decl.PropertyDecl -> decl.initializer?.let { bindExpr(it, scope) }
            else -> Unit
        }
    }

    private fun bindFunction(decl: Decl.FunDecl, scope: Scope) {
        val funScope = Scope(parent = scope, kind = Scope.Kind.FUNCTION)
        for (param in decl.params) {
            declare(paramSymbol(param, scope), funScope)
            param.defaultValue?.let { bindExpr(it, funScope) }
        }
        when (val body = decl.body) {
            is FunBody.BlockBody -> bindBlock(body.block, funScope)
            is FunBody.ExprBody -> bindExpr(body.expr, funScope)
            null -> Unit // abstract — nothing to bind
        }
    }

    private fun bindBlock(block: Stmt.Block, scope: Scope) {
        val blockScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
        // Locals are visible only after their declaration — declare as we go, statement
        // by statement, rather than hoisting the whole block.
        for (stmt in block.statements) bindStmt(stmt, blockScope)
    }

    private fun bindStmt(stmt: Stmt, scope: Scope) {
        when (stmt) {
            is Stmt.PropertyStmt -> {
                stmt.decl.initializer?.let { bindExpr(it, scope) }
                declare(variableSymbol(stmt.decl, scope), scope)
            }
            is Stmt.IfStmt -> {
                bindExpr(stmt.condition, scope)
                bindBlock(stmt.thenBlock, scope)
                for (elseIf in stmt.elseIfs) {
                    bindExpr(elseIf.condition, scope)
                    bindBlock(elseIf.block, scope)
                }
                stmt.elseBlock?.let { bindBlock(it, scope) }
            }
            is Stmt.WhenStmt -> {
                stmt.subject?.let { bindExpr(it, scope) }
                for (branch in stmt.branches) {
                    branch.conditions.forEach { bindExpr(it, scope) }
                    when (val body = branch.body) {
                        is krmelin.ast.WhenBody.ExprBody -> bindExpr(body.expr, scope)
                        is krmelin.ast.WhenBody.BlockBody -> bindBlock(body.block, scope)
                    }
                }
            }
            is Stmt.ForStmt -> {
                bindExpr(stmt.iterable, scope)
                val loopScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
                declare(
                    Symbol.Variable(stmt.name, stmt.span, isMutable = false, type = KType.UNKNOWN),
                    loopScope,
                )
                bindBlock(stmt.body, loopScope)
            }
            is Stmt.WhileStmt -> {
                bindExpr(stmt.condition, scope)
                bindBlock(stmt.body, scope)
            }
            is Stmt.ReturnStmt -> stmt.value?.let { bindExpr(it, scope) }
            is Stmt.TryStmt -> {
                bindBlock(stmt.block, scope)
                for (catch in stmt.catches) {
                    val catchScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
                    declare(paramSymbol(catch.param, scope), catchScope)
                    bindBlock(catch.block, catchScope)
                }
                stmt.finallyBlock?.let { bindBlock(it, scope) }
            }
            is Stmt.ThrowStmt -> bindExpr(stmt.expr, scope)
            is Stmt.ExprStmt -> bindExpr(stmt.expr, scope)
            is Stmt.BreakStmt, is Stmt.ContinueStmt -> Unit
            is Stmt.Block -> bindBlock(stmt, scope)
        }
    }

    private fun bindExpr(expr: Expr, scope: Scope) {
        when (expr) {
            is Expr.NameExpr -> bindName(expr, scope)
            is Expr.BinaryExpr -> { bindExpr(expr.left, scope); bindExpr(expr.right, scope) }
            is Expr.UnaryExpr -> bindExpr(expr.operand, scope)
            is Expr.CallExpr -> {
                bindExpr(expr.callee, scope)
                expr.args.forEach { bindExpr(it, scope) }
            }
            is Expr.MemberExpr -> bindExpr(expr.receiver, scope)
            is Expr.SafeMemberExpr -> bindExpr(expr.receiver, scope)
            is Expr.ElvisExpr -> { bindExpr(expr.left, scope); bindExpr(expr.right, scope) }
            is Expr.AssignExpr -> { bindExpr(expr.target, scope); bindExpr(expr.value, scope) }
            is Expr.ParenExpr -> bindExpr(expr.expr, scope)
            is Expr.StringTemplate -> expr.parts.forEach { part ->
                if (part is TemplatePart.Interpolation) bindExpr(part.expr, scope)
            }
            is Expr.LambdaExpr -> {
                val lambdaScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
                for (param in expr.params) {
                    declare(
                        Symbol.Variable(param, expr.span, isMutable = false, type = KType.UNKNOWN),
                        lambdaScope,
                    )
                }
                when (val body = expr.body) {
                    is LambdaBody.ExprBody -> bindExpr(body.expr, lambdaScope)
                    is LambdaBody.BlockBody -> bindBlock(body.block, lambdaScope)
                }
            }
            is Expr.IntLit, is Expr.FloatLit, is Expr.StringLit,
            is Expr.BoolLit, is Expr.NullLit -> Unit
        }
    }

    private fun bindName(expr: Expr.NameExpr, scope: Scope) {
        val symbol = scope.lookup(expr.name)
        if (symbol != null) {
            resolution.bindings[expr] = symbol
            return
        }
        val suggestion = table.suggest(expr.name, scope)
        reporter.error(
            DiagCode.UNDECLARED_NAME,
            "jme no '${expr.name}' neni deklarovane",
            expr.span,
            highlight = "takove jme no tu neni",
            fix = if (suggestion != null) {
                "nemyslel si '$suggestion'? velka pismena hraju roli"
            } else {
                "deklaruj '${expr.name}' driv, nez ho pouzijes"
            },
        )
    }
}
