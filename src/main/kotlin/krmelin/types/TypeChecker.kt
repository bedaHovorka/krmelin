package krmelin.types

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.LambdaBody
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.ast.WhenBody
import krmelin.diag.DiagCode
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.TokenType
import krmelin.resolve.Prelude
import krmelin.resolve.Resolution
import krmelin.resolve.Symbol

/**
 * The intentionally minimal type checker (Plan.md §5).
 *
 * Checks: assignment compatibility, `davaj` against the declared return type, `Bul`
 * conditions, call arity, and reassignment of `toz`. Inference exists only for `toz`/`mozej`
 * with an initializer and for expressions built strictly from known types. Everything else —
 * lambdas, overloads, generic inference — comes out as [KType.UNKNOWN], is compatible with
 * everything, and is left for `kotlinc` to judge (so this checker never false-positives).
 */
class TypeChecker(private val reporter: DiagnosticReporter, private val resolution: Resolution) {
    /** Return-type context for the function being checked; [KType.NIC] = no declared type. */
    private val returnContext = ArrayDeque<Pair<String, KType>>()

    fun check(unit: Decl.CompilationUnit) {
        for (decl in unit.declarations) checkDecl(decl)
    }

    // ── Declarations ────────────────────────────────────────────────────────

    private fun checkDecl(decl: Decl) {
        when (decl) {
            is Decl.ClassDecl -> decl.members.forEach(::checkDecl)
            is Decl.FunDecl -> checkFunction(decl)
            is Decl.PropertyDecl -> checkProperty(decl)
            else -> Unit
        }
    }

    private fun checkFunction(decl: Decl.FunDecl) {
        val declared = (resolution.declarations[decl] as? Symbol.Function)?.returnType
        returnContext.addLast(decl.name to (declared ?: KType.NIC))
        try {
            when (val body = decl.body) {
                is FunBody.BlockBody -> checkBlock(body.block)
                is FunBody.ExprBody -> {
                    val inferred = infer(body.expr)
                    if (declared != null && !inferred.isAssignableTo(declared)) {
                        reportReturnMismatch(body.expr.span, decl.name, declared, inferred)
                    }
                }
                null -> Unit // abstract
            }
        } finally {
            returnContext.removeLast()
        }
    }

    private fun checkProperty(decl: Decl.PropertyDecl) {
        val symbol = resolution.declarations[decl] as? Symbol.Variable
        val declared = symbol?.type
        val initializer = decl.initializer ?: return
        val inferred = infer(initializer)
        if (symbol != null && declared == null) symbol.type = inferred
        if (declared != null && !inferred.isAssignableTo(declared)) {
            reporter.error(
                DiagCode.TYPE_MISMATCH,
                "'${decl.name}' ma byt ${declared.name}, ale dostava ${inferred.name}",
                initializer.span,
                highlight = "tohle je ${inferred.name}, ne ${declared.name}",
                fix = "zmen typ na '${declared.name}', abo uprav hodnotu",
            )
        }
    }

    private fun checkBlock(block: Stmt.Block) {
        block.statements.forEach(::checkStmt)
    }

    // ── Statements ──────────────────────────────────────────────────────────

    private fun checkStmt(stmt: Stmt) {
        when (stmt) {
            is Stmt.Block -> checkBlock(stmt)
            is Stmt.PropertyStmt -> checkProperty(stmt.decl)
            is Stmt.IfStmt -> {
                requireBul(stmt.condition, "kaj")
                checkBlock(stmt.thenBlock)
                for (elseIf in stmt.elseIfs) {
                    requireBul(elseIf.condition, "kajtez")
                    checkBlock(elseIf.block)
                }
                stmt.elseBlock?.let(::checkBlock)
            }
            is Stmt.WhenStmt -> {
                val subjectType = stmt.subject?.let(::infer)
                for (branch in stmt.branches) {
                    for (condition in branch.conditions) {
                        if (subjectType == null) {
                            requireBul(condition, "podle_teho")
                        } else {
                            infer(condition)
                        }
                    }
                    when (val body = branch.body) {
                        is WhenBody.ExprBody -> infer(body.expr)
                        is WhenBody.BlockBody -> checkBlock(body.block)
                    }
                }
            }
            is Stmt.ForStmt -> {
                infer(stmt.iterable)
                checkBlock(stmt.body)
            }
            is Stmt.WhileStmt -> {
                requireBul(stmt.condition, "rubaj")
                checkBlock(stmt.body)
            }
            is Stmt.ReturnStmt -> checkReturn(stmt)
            is Stmt.TryStmt -> {
                checkBlock(stmt.block)
                stmt.catches.forEach { checkBlock(it.block) }
                stmt.finallyBlock?.let(::checkBlock)
            }
            is Stmt.ThrowStmt -> infer(stmt.expr)
            is Stmt.ExprStmt -> infer(stmt.expr)
            is Stmt.BreakStmt, is Stmt.ContinueStmt -> Unit
        }
    }

    private fun requireBul(condition: Expr, construct: String) {
        val type = infer(condition)
        if (type.kind == KType.Kind.UNKNOWN) return
        if (type.kotlinName != "Boolean" || type.nullable) {
            reporter.error(
                DiagCode.CONDITION_NOT_BUL,
                "podminka v '$construct' musi byt Bul, ale je ${type.name}",
                condition.span,
                highlight = "tohle je ${type.name}, ne fajne/nyt",
                fix = if (type.kotlinName == "Int") {
                    "chtel si '$construct (… > 0)'?"
                } else {
                    "udelej z toho Bul, treba porovnanim"
                },
                flourish = "fajne abo nyt, nic mezi",
            )
        }
    }

    private fun checkReturn(stmt: Stmt.ReturnStmt) {
        val (funName, expected) = returnContext.lastOrNull() ?: return
        val value = stmt.value
        when {
            expected == KType.NIC && value == null -> Unit
            expected == KType.NIC && value != null -> {
                infer(value)
                reporter.error(
                    DiagCode.UNEXPECTED_RETURN_VALUE,
                    "robota '$funName' nic nevraci, ale 'davaj' hodnotu ma",
                    stmt.span,
                    highlight = "'davaj' ma byt bez hodnoty",
                    fix = "zrus hodnotu za 'davaj', abo dopis navratovy typ roboty",
                )
            }
            expected != KType.NIC && value == null ->
                reporter.error(
                    DiagCode.RETURN_TYPE_MISMATCH,
                    "robota '$funName' ma vracet ${expected.name}, ale 'davaj' nic nevraci",
                    stmt.span,
                    highlight = "chybi hodnota za 'davaj'",
                    fix = "napis treba 'davaj <hodnota typu ${expected.name}>', abo zrus navratovy typ",
                )
            else -> {
                val inferred = infer(value!!)
                if (!inferred.isAssignableTo(expected)) {
                    reportReturnMismatch(value.span, funName, expected, inferred)
                }
            }
        }
    }

    private fun reportReturnMismatch(span: krmelin.lexer.SourceSpan, funName: String, expected: KType, inferred: KType) {
        reporter.error(
            DiagCode.RETURN_TYPE_MISMATCH,
            "robota '$funName' ma vracet ${expected.name}, ale 'davaj' vraci ${inferred.name}",
            span,
            highlight = "tohle je ${inferred.name}, ne ${expected.name}",
            fix = "vrat ${expected.name}, abo oprav navratovy typ roboty",
        )
    }

    // ── Expressions ─────────────────────────────────────────────────────────

    private val numericNames = setOf("Int", "Double", "Char")

    private fun infer(expr: Expr): KType {
        resolution.exprTypes[expr]?.let { return it }
        val type = inferUncached(expr)
        resolution.exprTypes[expr] = type
        return type
    }

    private fun inferUncached(expr: Expr): KType = when (expr) {
        is Expr.IntLit -> Prelude.CYSLO
        is Expr.FloatLit -> Prelude.CYslO_DESETINNE
        is Expr.StringLit -> Prelude.DRYST
        is Expr.StringTemplate -> {
            expr.parts.forEach { if (it is TemplatePart.Interpolation) infer(it.expr) }
            Prelude.DRYST
        }
        is Expr.BoolLit -> Prelude.BUL
        is Expr.NullLit -> KType.NULA

        is Expr.NameExpr -> when (val symbol = resolution.bindings[expr]) {
            is Symbol.Variable -> symbol.type ?: KType.UNKNOWN
            is Symbol.Parameter -> symbol.type ?: KType.UNKNOWN
            is Symbol.Function -> KType.UNKNOWN // function values are not modelled
            is Symbol.TypeName -> KType.UNKNOWN // a bare type name used as a value
            null -> KType.UNKNOWN // undeclared; the resolver already reported it
        }

        is Expr.ParenExpr -> infer(expr.expr)

        is Expr.UnaryExpr -> {
            val operand = infer(expr.operand)
            when (expr.op) {
                TokenType.BANG -> if (operand.kotlinName == "Boolean") Prelude.BUL else KType.UNKNOWN
                TokenType.MINUS -> operand
                else -> KType.UNKNOWN
            }
        }

        is Expr.BinaryExpr -> {
            val left = infer(expr.left)
            val right = infer(expr.right)
            when (expr.op) {
                TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.GT,
                TokenType.LE, TokenType.GE, TokenType.AJ, TokenType.CI -> Prelude.BUL
                TokenType.PLUS -> when {
                    left.kotlinName == "String" || right.kotlinName == "String" -> Prelude.DRYST
                    left.kind != KType.Kind.UNKNOWN && left.kotlinName == right.kotlinName &&
                        left.kotlinName in numericNames -> left
                    else -> KType.UNKNOWN
                }
                TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT ->
                    if (left.kind != KType.Kind.UNKNOWN && left.kotlinName == right.kotlinName &&
                        left.kotlinName in numericNames
                    ) {
                        left
                    } else {
                        KType.UNKNOWN
                    }
                else -> KType.UNKNOWN
            }
        }

        is Expr.ElvisExpr -> {
            val left = infer(expr.left)
            val right = infer(expr.right)
            val unwrapped = left.copy(nullable = false)
            if (right.isAssignableTo(unwrapped)) unwrapped else KType.UNKNOWN
        }

        is Expr.AssignExpr -> checkAssign(expr)

        is Expr.CallExpr -> inferCall(expr)

        is Expr.MemberExpr -> inferMember(expr.receiver, expr.name, safe = false)
        is Expr.SafeMemberExpr -> inferMember(expr.receiver, expr.name, safe = true)

        is Expr.LambdaExpr -> {
            when (val body = expr.body) {
                is LambdaBody.ExprBody -> infer(body.expr)
                is LambdaBody.BlockBody -> checkBlock(body.block)
            }
            KType.UNKNOWN
        }
    }

    private fun checkAssign(expr: Expr.AssignExpr): KType {
        val value = infer(expr.value)
        val target = expr.target
        if (target is Expr.NameExpr) {
            when (val symbol = resolution.bindings[target]) {
                is Symbol.Variable -> {
                    if (!symbol.isMutable) {
                        reporter.error(
                            DiagCode.ASSIGN_TO_IMMUTABLE,
                            "'${symbol.name}' je 'toz' — to se nemeni",
                            expr.span,
                            highlight = "nepujde prepisat",
                            fix = "deklaruj ho jako 'mozej ${symbol.name}', abo neprepisuj",
                            flourish = "toz je toz",
                        )
                    } else {
                        val declared = symbol.type
                        if (declared != null && !value.isAssignableTo(declared)) {
                            reporter.error(
                                DiagCode.TYPE_MISMATCH,
                                "'${symbol.name}' je ${
                                    declared.name
                                }, ale dostava ${value.name}",
                                expr.value.span,
                                highlight = "tohle je ${value.name}, ne ${declared.name}",
                                fix = "prirad hodnotu typu ${declared.name}",
                            )
                        }
                    }
                }
                is Symbol.Parameter -> if (!symbol.isMutable) {
                    reporter.error(
                        DiagCode.ASSIGN_TO_IMMUTABLE,
                        "'${symbol.name}' je parametr bez 'mozej' — to se nemeni",
                        expr.span,
                        fix = "oznacte parametr jako 'mozej ${symbol.name}'",
                    )
                }
                else -> Unit
            }
        } else {
            infer(target)
        }
        return value
    }

    private fun inferCall(expr: Expr.CallExpr): KType {
        expr.args.forEach(::infer)
        return when (val callee = expr.callee) {
            is Expr.NameExpr -> when (val symbol = resolution.bindings[callee]) {
                is Symbol.Function -> callFunction(expr, callee, symbol)
                is Symbol.TypeName -> symbol.constructor?.let { callFunction(expr, callee, it) }
                    ?: symbol.type
                else -> KType.UNKNOWN
            }
            is Expr.MemberExpr -> {
                infer(callee.receiver)
                val member = memberSymbol(callee.receiver, callee.name)
                if (member is Symbol.Function) callFunction(expr, callee, member) else KType.UNKNOWN
            }
            is Expr.SafeMemberExpr -> KType.UNKNOWN.also { infer(callee.receiver) }
            else -> infer(callee).let { KType.UNKNOWN }
        }
    }

    private fun callFunction(call: Expr.CallExpr, calleeExpr: Expr, fn: Symbol.Function): KType {
        val required = fn.params.count { !it.hasDefault }
        val total = fn.params.size
        val args = call.args.size
        if (args < required || args > total) {
            reporter.error(
                DiagCode.ARITY_MISMATCH,
                "'${fn.name}' bere ${describeCount(required, total)}, dal si $args",
                calleeExpr.span,
                highlight = "spatny pocet argumentu",
                fix = "'${fn.name}' ceka (${fn.params.joinToString(", ") { "${it.name}: ${it.type?.name ?: "?"}" }})",
            )
        }
        return fn.returnType ?: KType.NIC
    }

    private fun describeCount(required: Int, total: Int): String =
        if (required == total) countWord(total) else "$required az $total"

    private fun countWord(n: Int): String = when (n) {
        0 -> "0 argumentu"
        1 -> "1 argument"
        2, 3, 4 -> "$n argumenty"
        else -> "$n argumentu"
    }

    private fun memberSymbol(receiver: Expr, name: String): Symbol? {
        val receiverType = resolution.typeOf(receiver)
        if (receiverType.kind == KType.Kind.UNKNOWN) return null
        return typeNameFor(receiverType)?.members?.get(name)
    }

    private fun inferMember(receiver: Expr, name: String, safe: Boolean): KType {
        val receiverType = infer(receiver)
        if (receiverType.kind == KType.Kind.UNKNOWN) return KType.UNKNOWN
        val member = typeNameFor(receiverType)?.members?.get(name) ?: return KType.UNKNOWN
        val memberType = when (member) {
            is Symbol.Variable -> member.type
            is Symbol.Parameter -> member.type
            is Symbol.Function -> KType.UNKNOWN // a method reference — not modelled
            is Symbol.TypeName -> KType.UNKNOWN
        } ?: KType.UNKNOWN
        return if (safe) memberType.nullable() else memberType
    }

    /** Finds the [Symbol.TypeName] backing [type] (prelude or user class), by Kotlin name. */
    private fun typeNameFor(type: KType): Symbol.TypeName? {
        var scope: krmelin.resolve.Scope? = resolution.fileScope
        while (scope != null) {
            for (name in scope.visibleNames()) {
                val symbol = scope.lookupLocal(name)
                if (symbol is Symbol.TypeName && symbol.type.kotlinName == type.kotlinName) {
                    return symbol
                }
            }
            scope = scope.parent
        }
        return null
    }
}
