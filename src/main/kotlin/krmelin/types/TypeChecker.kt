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
        // Keyed on what the user wrote, not on whether the symbol has a type yet: on-demand
        // inference may already have filled one in, and that is not a declared type.
        val declared = if (decl.type != null) symbol?.type else null
        val initializer = decl.initializer ?: return
        val inferred = infer(initializer)
        if (symbol != null && decl.type == null) symbol.type = inferred
        if (declared != null && !inferred.isAssignableTo(declared)) {
            reporter.error(
                DiagCode.TYPE_MISMATCH,
                "'${decl.name}' ma byt ${declared.display}, ale dostava ${inferred.display}",
                initializer.span,
                highlight = "tohle je ${inferred.display}, ne ${declared.display}",
                fix = "zmen typ na '${declared.display}', abo uprav hodnotu",
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
        reportIfNotBul(condition, infer(condition), construct)
    }

    /** Shared by conditions and by the `aj`/`ci`/`!` operands, which are conditions too. */
    private fun reportIfNotBul(expr: Expr, type: KType, construct: String, operator: Boolean = false) {
        if (type.kind == KType.Kind.UNKNOWN) return
        if (type.isBul) return
        reporter.error(
            DiagCode.CONDITION_NOT_BUL,
            "${if (operator) "operand '$construct'" else "podminka v '$construct'"} " +
                "musi byt Bul, ale je ${type.display}",
            expr.span,
            highlight = "tohle je ${type.display}, ne fajne/nyt",
            // The `> 0` nudge only makes sense for a whole condition, not for one operand.
            fix = if (type.kotlinName == "Int" && !operator) {
                "chtel si '$construct (… > 0)'?"
            } else {
                "udelej z toho Bul, treba porovnanim"
            },
            flourish = "fajne abo nyt, nic mezi",
        )
    }

    private val KType.isBul: Boolean
        get() = kotlinName == "Boolean" && declId == null && !nullable

    private fun checkReturn(stmt: Stmt.ReturnStmt) {
        val (funName, expected) = returnContext.lastOrNull() ?: return
        val value = stmt.value
        // A declared return type that failed to resolve arrives as UNKNOWN. The resolver has
        // already reported it; saying anything more here would contradict that diagnostic.
        if (expected.kind == KType.Kind.UNKNOWN) {
            value?.let(::infer)
            return
        }
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
                    "robota '$funName' ma vracet ${expected.display}, ale 'davaj' nic nevraci",
                    stmt.span,
                    highlight = "chybi hodnota za 'davaj'",
                    fix = "napis treba 'davaj <hodnota typu ${expected.display}>', abo zrus navratovy typ",
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
            "robota '$funName' ma vracet ${expected.display}, ale 'davaj' vraci ${inferred.display}",
            span,
            highlight = "tohle je ${inferred.display}, ne ${expected.display}",
            fix = "vrat ${expected.display}, abo oprav navratovy typ roboty",
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
        is Expr.FloatLit -> Prelude.CYSLO_DESETINNE
        is Expr.StringLit -> Prelude.DRYST
        is Expr.StringTemplate -> {
            expr.parts.forEach { if (it is TemplatePart.Interpolation) infer(it.expr) }
            Prelude.DRYST
        }
        is Expr.BoolLit -> Prelude.BUL
        is Expr.NullLit -> KType.NULA

        is Expr.NameExpr -> when (val symbol = resolution.bindings[expr]) {
            is Symbol.Variable -> typeOfVariable(symbol)
            is Symbol.Parameter -> symbol.type ?: KType.UNKNOWN
            is Symbol.Function -> KType.UNKNOWN // function values are not modelled
            is Symbol.TypeName -> KType.UNKNOWN // a bare type name used as a value
            null -> KType.UNKNOWN // undeclared; the resolver already reported it
        }

        is Expr.ParenExpr -> infer(expr.expr)

        is Expr.UnaryExpr -> {
            val operand = infer(expr.operand)
            when (expr.op) {
                // Always Bul, but the operand is checked — otherwise `kaj (!i)` on a Cyslo
                // came out UNKNOWN and slipped past the condition check entirely.
                TokenType.BANG -> Prelude.BUL.also {
                    reportIfNotBul(expr.operand, operand, "!", operator = true)
                }
                TokenType.MINUS -> operand
                else -> KType.UNKNOWN
            }
        }

        is Expr.BinaryExpr -> {
            val left = infer(expr.left)
            val right = infer(expr.right)
            when (expr.op) {
                // Comparisons accept any operands; the boolean connectives do not, and without
                // checking them `kaj (i aj j)` made the whole condition check vacuous.
                TokenType.AJ, TokenType.CI -> {
                    val name = if (expr.op == TokenType.AJ) "aj" else "ci"
                    reportIfNotBul(expr.left, left, name, operator = true)
                    reportIfNotBul(expr.right, right, name, operator = true)
                    Prelude.BUL
                }
                TokenType.EQ, TokenType.NEQ, TokenType.LT, TokenType.GT,
                TokenType.LE, TokenType.GE -> Prelude.BUL
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
                is Symbol.Variable ->
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
                        checkAssignedType(symbol.name, typeOfVariable(symbol), value, expr.value.span)
                    }
                is Symbol.Parameter ->
                    if (!symbol.isMutable) {
                        reporter.error(
                            DiagCode.ASSIGN_TO_IMMUTABLE,
                            "'${symbol.name}' je parametr bez 'mozej' — to se nemeni",
                            expr.span,
                            fix = "oznacte parametr jako 'mozej ${symbol.name}'",
                        )
                    } else {
                        // A `mozej` parameter is the main way a parameter is used at all, so
                        // skipping the type check here left the commonest write unchecked.
                        checkAssignedType(symbol.name, symbol.type, value, expr.value.span)
                    }
                else -> Unit
            }
        } else {
            infer(target)
        }
        return value
    }

    private fun checkAssignedType(name: String, declared: KType?, value: KType, span: krmelin.lexer.SourceSpan) {
        if (declared == null || value.isAssignableTo(declared)) return
        reporter.error(
            DiagCode.TYPE_MISMATCH,
            "'$name' je ${declared.display}, ale dostava ${value.display}",
            span,
            highlight = "tohle je ${value.display}, ne ${declared.display}",
            fix = "prirad hodnotu typu ${declared.display}",
        )
    }

    /**
     * The type of a `toz`/`mozej` binding, inferring its initializer on demand.
     *
     * The resolver deliberately binds forward references between properties, so a `toz a = b`
     * naming a later `toz b = 5` must follow that reference — a single forward pass leaves `a`
     * UNKNOWN and silently swallows every error involving it. The in-progress set breaks
     * reference cycles, which would otherwise recurse forever.
     */
    private val inferringProperties = java.util.IdentityHashMap<Decl.PropertyDecl, Unit>()

    private fun typeOfVariable(symbol: Symbol.Variable): KType {
        symbol.type?.let { return it }
        val decl = symbol.decl ?: return KType.UNKNOWN
        val initializer = decl.initializer ?: return KType.UNKNOWN
        if (inferringProperties.put(decl, Unit) != null) return KType.UNKNOWN
        return try {
            infer(initializer).also { symbol.type = it }
        } finally {
            inferringProperties.remove(decl)
        }
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
            // Adding a `?` to the receiver must not disable checking — it used to.
            is Expr.SafeMemberExpr -> {
                infer(callee.receiver)
                val member = memberSymbol(callee.receiver, callee.name)
                if (member is Symbol.Function) {
                    callFunction(expr, callee, member).nullable()
                } else {
                    KType.UNKNOWN
                }
            }
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
                fix = "'${fn.name}' ceka (${fn.params.joinToString(", ") { "${it.name}: ${it.type?.display ?: "?"}" }})",
            )
        } else {
            checkArguments(call, fn)
        }
        return fn.returnType ?: KType.NIC
    }

    /**
     * Argument passing is the busiest assignment site in any program, and it went unchecked:
     * only the count was compared, never the types the resolver had already worked out.
     */
    private fun checkArguments(call: Expr.CallExpr, fn: Symbol.Function) {
        for ((index, arg) in call.args.withIndex()) {
            val param = fn.params.getOrNull(index) ?: return
            val expected = param.type ?: continue
            val actual = infer(arg)
            if (actual.isAssignableTo(expected)) continue
            reporter.error(
                DiagCode.TYPE_MISMATCH,
                "'${fn.name}' ceka pro '${param.name}' typ ${expected.display}, ale dostava ${actual.display}",
                arg.span,
                highlight = "tohle je ${actual.display}, ne ${expected.display}",
                fix = "predej hodnotu typu ${expected.display}",
            )
        }
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

    /**
     * Finds the [Symbol.TypeName] backing [type] (prelude or user class).
     *
     * Matched on the declaration as well as the Kotlin name — matching the name alone let a
     * user class called `String` or `List` inherit the prelude alias's member table.
     */
    private fun typeNameFor(type: KType): Symbol.TypeName? {
        var scope: krmelin.resolve.Scope? = resolution.fileScope
        while (scope != null) {
            for (name in scope.localNames()) {
                val symbol = scope.lookupLocal(name)
                if (symbol is Symbol.TypeName &&
                    symbol.type.kotlinName == type.kotlinName &&
                    symbol.type.declId == type.declId
                ) {
                    return symbol
                }
            }
            scope = scope.parent
        }
        return null
    }
}
