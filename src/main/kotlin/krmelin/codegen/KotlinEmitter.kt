package krmelin.codegen

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.Stmt
import krmelin.ast.TypeNode
import krmelin.lower.Lowering
import krmelin.resolve.Resolution
import krmelin.resolve.Symbol

/**
 * Visits the lowered AST and produces Kotlin source (Plan.md §5.7).
 *
 * Formatting is deterministic — 4-space indents, LF endings, exactly one blank line
 * after the package clause, one after the import block, one between top-level
 * declarations, a single trailing newline, no timestamps — so the golden tests in
 * tests/golden/ are stable diffs.
 *
 * Operator precedence is trusted to the AST shape: Krmelin's §4.3 precedence table is
 * Kotlin's, and the parser preserves source parens as [Expr.ParenExpr], so no synthetic
 * parenthesization happens here.
 *
 * The emitter never reports diagnostics. Anything it cannot map renders as the source
 * spelling and is left for kotlinc to judge, matching the type checker's UNKNOWN policy.
 */
class KotlinEmitter(private val resolution: Resolution) {

    private val out = StringBuilder()
    private var indent = 0

    fun emit(lowered: Lowering.Lowered): String {
        out.clear()
        indent = 0
        val unit = lowered.unit

        unit.packageDecl?.let {
            line("package ${it.name.joinToString(".")}")
            line()
        }

        val importLines = buildList {
            if (lowered.usesFlakanci) add(KotlinPrelude.RUNTIME_IMPORT_LINE)
            unit.imports.forEach {
                val base = it.name.joinToString(".")
                add(if (it.wildcard) "import $base.*" else "import $base")
            }
        }
        if (importLines.isNotEmpty()) {
            importLines.forEach(::line)
            line()
        }

        unit.declarations.forEachIndexed { index, decl ->
            if (index > 0) line()
            emitDecl(decl, topLevel = true)
        }

        return out.toString()
    }

    // ── Writers ────────────────────────────────────────────────────────────

    private fun line(text: String = "") {
        if (text.isEmpty()) out.append('\n') else {
            out.append("    ".repeat(indent)).append(text).append('\n')
        }
    }

    private inline fun nest(block: () -> Unit) {
        indent++
        block()
        indent--
    }

    // ── Types ──────────────────────────────────────────────────────────────

    /**
     * Renders a written type as its Kotlin name. Aliases resolve through the scope
     * chain to their `KType.kotlinName`; names the resolver could not resolve pass
     * through verbatim (the resolver already diagnosed them; kotlinc gets the last word).
     */
    internal fun renderType(type: TypeNode): String = when (type) {
        is TypeNode.NamedType -> buildString {
            val symbol = resolution.fileScope.lookup(type.name)
            append(if (symbol is Symbol.TypeName) symbol.type.kotlinName else type.name)
            if (type.typeArgs.isNotEmpty()) {
                type.typeArgs.joinTo(this, ", ", "<", ">") { renderType(it) }
            }
            if (type.nullable) append('?')
        }
    }

    // ── Declarations ───────────────────────────────────────────────────────

    private fun emitDecl(decl: Decl, topLevel: Boolean) {
        when (decl) {
            is Decl.FunDecl -> emitFun(decl, topLevel)
            is Decl.ClassDecl -> emitClass(decl, topLevel)
            is Decl.PropertyDecl -> line(renderProperty(decl))
            is Decl.CompilationUnit, is Decl.PackageDecl, is Decl.ImportDecl, is Decl.Param ->
                error("unreachable: ${decl::class.simpleName} is unit-level syntax")
        }
    }

    private fun emitFun(decl: Decl.FunDecl, topLevel: Boolean) {
        // @Sichta/@Parta are intentionally dropped in M4 — PorubaUnit (M5) consumes
        // them before emission; the generated Kotlin carries no test annotations.
        if (decl.throwsTypes.isNotEmpty()) {
            line("@Throws(${decl.throwsTypes.joinToString(", ") { "${renderType(it)}::class" }})")
        }

        val isEntryPoint = topLevel && KotlinPrelude.isEntryPoint(decl)
        val name = if (isEntryPoint) KotlinPrelude.ENTRY_POINT_KOTLIN else decl.name

        val signature = buildString {
            append("fun ").append(name).append('(')
            decl.params.joinTo(this, ", ") { renderParam(it, withMutability = false) }
            append(')')
            decl.returnType?.let { append(": ").append(renderType(it)) }
        }

        when (val body = decl.body) {
            null -> line(signature)
            is FunBody.ExprBody -> line("$signature = ${emitExpr(body.expr)}")
            is FunBody.BlockBody -> {
                line("$signature {")
                nest { emitBlock(body.block) }
                line("}")
            }
        }
    }

    private fun emitClass(decl: Decl.ClassDecl, topLevel: Boolean) {
        val kind = when {
            decl.isData -> "data class"
            decl.isObject -> "object"
            decl.isInterface -> "interface"
            else -> "class"
        }
        val header = buildString {
            append(kind).append(' ').append(decl.name)
            if (!decl.isObject && !decl.isInterface) {
                decl.params.joinTo(this, ", ", "(", ")") { renderParam(it, withMutability = true) }
            }
        }
        if (decl.members.isEmpty()) {
            line(header)
        } else {
            line("$header {")
            nest { decl.members.forEach { emitDecl(it, topLevel = false) } }
            line("}")
        }
    }

    private fun renderParam(param: Decl.Param, withMutability: Boolean): String = buildString {
        if (withMutability) append(if (param.isMutable) "var " else "val ")
        append(param.name).append(": ").append(renderType(param.type))
        param.defaultValue?.let { append(" = ").append(emitExpr(it)) }
    }

    private fun renderProperty(decl: Decl.PropertyDecl): String = buildString {
        append(if (decl.isMutable) "var " else "val ").append(decl.name)
        decl.type?.let { append(": ").append(renderType(it)) }
        decl.initializer?.let { append(" = ").append(emitExpr(it)) }
    }

    // ── Statements ─────────────────────────────────────────────────────────

    private fun emitBlock(block: Stmt.Block) = block.statements.forEach(::emitStmt)

    private fun emitStmt(stmt: Stmt) {
        when (stmt) {
            // The grammar has no standalone block statement — a bare `{ ... }` parses
            // as a lambda literal — so Stmt.Block never reaches this dispatch.
            is Stmt.Block -> error("unreachable: standalone blocks are lambdas in statement position")
            is Stmt.PropertyStmt -> line(renderProperty(stmt.decl))
            is Stmt.ReturnStmt ->
                line(if (stmt.value != null) "return ${emitExpr(stmt.value)}" else "return")
            is Stmt.ExprStmt -> line(emitExpr(stmt.expr))
            is Stmt.IfStmt -> emitIf(stmt)
            is Stmt.WhileStmt -> {
                line("while (${emitExpr(stmt.condition)}) {")
                nest { emitBlock(stmt.body) }
                line("}")
            }
            is Stmt.ForStmt -> {
                line("for (${stmt.name} in ${emitExpr(stmt.iterable)}) {")
                nest { emitBlock(stmt.body) }
                line("}")
            }
            is Stmt.BreakStmt -> line("break")
            is Stmt.ContinueStmt -> line("continue")
            is Stmt.ThrowStmt -> line("throw ${emitExpr(stmt.expr)}")
            is Stmt.WhenStmt -> emitWhen(stmt)
            is Stmt.TryStmt -> emitTry(stmt)
        }
    }

    private fun emitIf(stmt: Stmt.IfStmt) {
        line("if (${emitExpr(stmt.condition)}) {")
        nest { emitBlock(stmt.thenBlock) }
        for (elseIf in stmt.elseIfs) {
            line("} else if (${emitExpr(elseIf.condition)}) {")
            nest { emitBlock(elseIf.block) }
        }
        if (stmt.elseBlock != null) {
            line("} else {")
            nest { emitBlock(stmt.elseBlock) }
        }
        line("}")
    }

    private fun emitWhen(stmt: Stmt.WhenStmt) {
        val header = if (stmt.subject != null) "when (${emitExpr(stmt.subject)}) {" else "when {"
        line(header)
        nest {
            for (branch in stmt.branches) {
                val conditions = if (branch.isElse) {
                    "else"
                } else {
                    branch.conditions.joinToString(", ") { emitExpr(it) }
                }
                when (val body = branch.body) {
                    is krmelin.ast.WhenBody.ExprBody -> line("$conditions -> ${emitExpr(body.expr)}")
                    is krmelin.ast.WhenBody.BlockBody -> {
                        line("$conditions -> {")
                        nest { emitBlock(body.block) }
                        line("}")
                    }
                }
            }
        }
        line("}")
    }

    private fun emitTry(stmt: Stmt.TryStmt) {
        line("try {")
        nest { emitBlock(stmt.block) }
        for (c in stmt.catches) {
            line("} catch (${renderParam(c.param, withMutability = false)}) {")
            nest { emitBlock(c.block) }
        }
        if (stmt.finallyBlock != null) {
            line("} finally {")
            nest { emitBlock(stmt.finallyBlock) }
        }
        line("}")
    }

    // ── Expressions ────────────────────────────────────────────────────────

    private fun emitExpr(expr: Expr): String = when (expr) {
        is Expr.IntLit -> expr.value.toString()
        is Expr.FloatLit -> expr.value.toString()
        is Expr.StringLit -> "\"${escape(expr.value)}\""
        is Expr.StringTemplate -> emitTemplate(expr)
        is Expr.BoolLit -> expr.value.toString()
        is Expr.NullLit -> "null"
        is Expr.NameExpr -> emitName(expr)
        is Expr.BinaryExpr -> "${emitExpr(expr.left)} ${BINARY_OPS[expr.op]} ${emitExpr(expr.right)}"
        is Expr.UnaryExpr -> "${UNARY_OPS[expr.op]}${emitParenthized(expr.operand)}"
        is Expr.CallExpr -> "${emitExpr(expr.callee)}(${expr.args.joinToString(", ") { emitExpr(it) }})"
        is Expr.MemberExpr -> "${emitExpr(expr.receiver)}.${expr.name}"
        is Expr.SafeMemberExpr -> "${emitExpr(expr.receiver)}?.${expr.name}"
        is Expr.ElvisExpr -> "${emitExpr(expr.left)} ?: ${emitExpr(expr.right)}"
        is Expr.AssignExpr -> "${emitExpr(expr.target)} = ${emitExpr(expr.value)}"
        is Expr.LambdaExpr -> emitLambda(expr)
        is Expr.ParenExpr -> "(${emitExpr(expr.expr)})"
    }

    /** Renames prelude `pravit`/`zarvat` — only when the binding is the prelude itself. */
    private fun emitName(expr: Expr.NameExpr): String {
        val symbol = resolution.bindings[expr]
        if (symbol is Symbol.Function && symbol.decl == null) {
            KotlinPrelude.PRINT_FUNCTION_NAMES[expr.name]?.let { return it }
        }
        return expr.name
    }

    /** Unary operators parenthesize composite operands so `-a * b` cannot misbind. */
    private fun emitParenthized(operand: Expr): String = when (operand) {
        is Expr.NameExpr, is Expr.IntLit, is Expr.FloatLit, is Expr.StringLit,
        is Expr.StringTemplate, is Expr.BoolLit, is Expr.MemberExpr, is Expr.SafeMemberExpr,
        is Expr.CallExpr, is Expr.ParenExpr,
        -> emitExpr(operand)
        else -> "(${emitExpr(operand)})"
    }

    private fun emitTemplate(expr: Expr.StringTemplate): String = buildString {
        append('"')
        for (part in expr.parts) {
            when (part) {
                is krmelin.ast.TemplatePart.Text -> append(escape(part.text))
                is krmelin.ast.TemplatePart.Interpolation -> {
                    // `$name` in source arrives as Interpolation(NameExpr); keep the short form.
                    if (part.expr is Expr.NameExpr) append("$").append((part.expr as Expr.NameExpr).name)
                    else append("\${").append(emitExpr(part.expr)).append('}')
                }
            }
        }
        append('"')
    }

    private fun emitLambda(expr: Expr.LambdaExpr): String = buildString {
        append("{ ")
        if (expr.params.isNotEmpty()) append(expr.params.joinToString(", ")).append(" -> ")
        when (val body = expr.body) {
            is krmelin.ast.LambdaBody.ExprBody -> append(emitExpr(body.expr))
            is krmelin.ast.LambdaBody.BlockBody ->
                append(body.block.statements.joinToString("; ") { renderInlineStmt(it) })
        }
        append(" }")
    }

    private fun renderInlineStmt(stmt: Stmt): String = when (stmt) {
        is Stmt.ExprStmt -> emitExpr(stmt.expr)
        is Stmt.ReturnStmt -> if (stmt.value != null) "return ${emitExpr(stmt.value)}" else "return"
        is Stmt.PropertyStmt -> renderProperty(stmt.decl)
        is Stmt.ThrowStmt -> "throw ${emitExpr(stmt.expr)}"
        is Stmt.BreakStmt -> "break"
        is Stmt.ContinueStmt -> "continue"
        // Lambda block bodies carry full statements (ExprParser.parseLambda parses them
        // in a loop), so control flow has to render too. Kotlin accepts these as
        // single-line forms with `;`-separated bodies and nested braces, keeping the
        // emitter's string-returning contract without multi-line emission.
        is Stmt.IfStmt -> buildString {
            append("if (${emitExpr(stmt.condition)}) ${braced(stmt.thenBlock)}")
            for (e in stmt.elseIfs) append(" else if (${emitExpr(e.condition)}) ${braced(e.block)}")
            if (stmt.elseBlock != null) append(" else ${braced(stmt.elseBlock)}")
        }
        is Stmt.WhileStmt -> "while (${emitExpr(stmt.condition)}) ${braced(stmt.body)}"
        is Stmt.ForStmt -> "for (${stmt.name} in ${emitExpr(stmt.iterable)}) ${braced(stmt.body)}"
        is Stmt.TryStmt -> buildString {
            append("try ${braced(stmt.block)}")
            for (c in stmt.catches) append(" catch (${renderParam(c.param, withMutability = false)}) ${braced(c.block)}")
            if (stmt.finallyBlock != null) append(" finally ${braced(stmt.finallyBlock)}")
        }
        is Stmt.WhenStmt -> buildString {
            if (stmt.subject != null) append("when (${emitExpr(stmt.subject)}) { ")
            else append("when { ")
            append(stmt.branches.joinToString("; ") { br ->
                val conds = if (br.isElse) "else" else br.conditions.joinToString(", ") { emitExpr(it) }
                val body = when (val b = br.body) {
                    is krmelin.ast.WhenBody.ExprBody -> emitExpr(b.expr)
                    is krmelin.ast.WhenBody.BlockBody -> "{ ${inlineBlock(b.block)} }"
                }
                "$conds -> $body"
            })
            append(" }")
        }
        is Stmt.Block -> braced(stmt)
    }

    /** A block rendered as a `;`-joined single line — the inline form used inside lambdas. */
    private fun inlineBlock(block: Stmt.Block): String =
        block.statements.joinToString("; ") { renderInlineStmt(it) }

    /** A block wrapped in braces, collapsed to `{}` when empty so spacing stays tidy. */
    private fun braced(block: Stmt.Block): String =
        inlineBlock(block).let { if (it.isEmpty()) "{}" else "{ $it }" }

    private fun escape(text: String): String = buildString {
        for (c in text) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
    }

    companion object {
        private val BINARY_OPS = mapOf(
            krmelin.lexer.TokenType.AJ to "&&",
            krmelin.lexer.TokenType.CI to "||",
            krmelin.lexer.TokenType.PLUS to "+",
            krmelin.lexer.TokenType.MINUS to "-",
            krmelin.lexer.TokenType.STAR to "*",
            krmelin.lexer.TokenType.SLASH to "/",
            krmelin.lexer.TokenType.PERCENT to "%",
            krmelin.lexer.TokenType.EQ to "==",
            krmelin.lexer.TokenType.NEQ to "!=",
            krmelin.lexer.TokenType.LT to "<",
            krmelin.lexer.TokenType.GT to ">",
            krmelin.lexer.TokenType.LE to "<=",
            krmelin.lexer.TokenType.GE to ">=",
        )

        private val UNARY_OPS = mapOf(
            krmelin.lexer.TokenType.BANG to "!",
            krmelin.lexer.TokenType.MINUS to "-",
            krmelin.lexer.TokenType.PLUS to "+",
        )
    }
}
