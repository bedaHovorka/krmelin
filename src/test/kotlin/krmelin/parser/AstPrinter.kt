package krmelin.parser

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.LambdaBody
import krmelin.ast.Node
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.ast.TypeNode
import krmelin.ast.WhenBody
import krmelin.lexer.TokenType

/**
 * Produces a stable, indentation-based textual representation of an AST for
 * use in parser tests. It intentionally omits source spans to keep snapshots
 * readable.
 */
class AstPrinter {
    private val out = StringBuilder()
    private var indent = 0

    fun print(node: Node): String {
        out.clear()
        indent = 0
        when (node) {
            is Decl.CompilationUnit -> compilationUnit(node)
            is Expr -> expr(node)
            is Stmt -> stmt(node)
            is Decl -> decl(node)
            is TypeNode -> type(node)
        }
        return out.toString().trimEnd()
    }

    private fun line(text: String) {
        out.append("    ".repeat(indent))
        out.appendLine(text)
    }

    private fun block(body: () -> Unit) {
        indent++
        body()
        indent--
    }

    private fun compilationUnit(cu: Decl.CompilationUnit) {
        line("CompilationUnit")
        block {
            cu.packageDecl?.let { decl(it) }
            cu.imports.forEach { decl(it) }
            cu.declarations.forEach { decl(it) }
        }
    }

    private fun decl(d: Decl) {
        when (d) {
            is Decl.CompilationUnit -> compilationUnit(d)
            is Decl.PackageDecl -> line("Package ${d.name.joinToString(".")}")
            is Decl.ImportDecl -> line("Import ${d.name.joinToString(".")}${if (d.wildcard) ".*" else ""}")
            is Decl.ClassDecl -> {
                val kind = when {
                    d.isData -> "data class"
                    d.isObject -> "object"
                    d.isInterface -> "interface"
                    else -> "class"
                }
                line("$kind ${d.name}")
                if (d.params.isNotEmpty()) {
                    block {
                        line("ctor")
                        block { d.params.forEach { decl(it) } }
                    }
                }
                block { d.members.forEach { decl(it) } }
            }
            is Decl.FunDecl -> {
                val anns = if (d.annotations.isEmpty()) "" else d.annotations.joinToString(" ", prefix = "[", postfix = "] ")
                val ret = d.returnType?.let { ": ${typeString(it)}" } ?: ""
                val throws = if (d.throwsTypes.isEmpty()) "" else " throws ${d.throwsTypes.joinToString(", ") { typeString(it) }}"
                line("${anns}fun ${d.name}(${(d.params.joinToString(", ") { paramString(it) })}$ret$throws")
                when (val b = d.body) {
                    is FunBody.BlockBody -> block { stmt(b.block) }
                    is FunBody.ExprBody -> block { line("= ${exprString(b.expr)}") }
                }
            }
            is Decl.PropertyDecl -> {
                val kw = if (d.isMutable) "var" else "val"
                val type = d.type?.let { ": ${typeString(it)}" } ?: ""
                val init = d.initializer?.let { " = ${exprString(it)}" } ?: ""
                line("$kw ${d.name}$type$init")
            }
            is Decl.Param -> {
                val type = d.type?.let { ": ${typeString(it)}" } ?: ""
                val default = d.defaultValue?.let { " = ${exprString(it)}" } ?: ""
                line("param ${d.name}$type$default")
            }
        }
    }

    private fun stmt(s: Stmt) {
        when (s) {
            is Stmt.Block -> {
                line("Block")
                block { s.statements.forEach { stmt(it) } }
            }
            is Stmt.PropertyStmt -> decl(s.decl)
            is Stmt.IfStmt -> {
                line("If ${exprString(s.condition)}")
                block { stmt(s.thenBlock) }
                s.elseIfs.forEach {
                    line("ElseIf ${exprString(it.condition)}")
                    block { stmt(it.block) }
                }
                s.elseBlock?.let {
                    line("Else")
                    block { stmt(it) }
                }
            }
            is Stmt.WhenStmt -> {
                line("When${s.subject?.let { " ${exprString(it)}" } ?: ""}")
                block {
                    s.branches.forEach { branch ->
                        if (branch.isElse) {
                            line("else ->")
                        } else {
                            line(branch.conditions.joinToString(", ") { exprString(it) } + " ->")
                        }
                        block {
                            when (val b = branch.body) {
                                is WhenBody.ExprBody -> line(exprString(b.expr))
                                is WhenBody.BlockBody -> stmt(b.block)
                            }
                        }
                    }
                }
            }
            is Stmt.ForStmt -> {
                line("For ${s.name} in ${exprString(s.iterable)}")
                block { stmt(s.body) }
            }
            is Stmt.WhileStmt -> {
                line("While ${exprString(s.condition)}")
                block { stmt(s.body) }
            }
            is Stmt.ReturnStmt -> line("Return${s.value?.let { " ${exprString(it)}" } ?: ""}")
            is Stmt.BreakStmt -> line("Break")
            is Stmt.ContinueStmt -> line("Continue")
            is Stmt.TryStmt -> {
                line("Try")
                block { stmt(s.block) }
                s.catches.forEach {
                    line("Catch ${paramString(it.param)}")
                    block { stmt(it.block) }
                }
                s.finallyBlock?.let {
                    line("Finally")
                    block { stmt(it) }
                }
            }
            is Stmt.ThrowStmt -> line("Throw ${exprString(s.expr)}")
            is Stmt.ExprStmt -> line("Expr ${exprString(s.expr)}")
        }
    }

    private fun expr(e: Expr): Unit = line(exprString(e))

    private fun exprString(e: Expr): String = when (e) {
        is Expr.IntLit -> e.value.toString()
        is Expr.FloatLit -> e.value.toString()
        is Expr.StringLit -> "\"${e.value}\""
        is Expr.StringTemplate -> {
            val parts = e.parts.joinToString("") { part ->
                when (part) {
                    is TemplatePart.Text -> part.text
                    is TemplatePart.Interpolation -> "\${${exprString(part.expr)}}"
                }
            }
            "\"$parts\""
        }
        is Expr.BoolLit -> e.value.toString()
        is Expr.NullLit -> "null"
        is Expr.NameExpr -> e.name
        is Expr.BinaryExpr -> "(${exprString(e.left)} ${opString(e.op)} ${exprString(e.right)})"
        is Expr.UnaryExpr -> "(${opString(e.op)}${exprString(e.operand)})"
        is Expr.CallExpr -> "${exprString(e.callee)}(${e.args.joinToString(", ") { exprString(it) }})"
        is Expr.MemberExpr -> "(${exprString(e.receiver)}.${e.name})"
        is Expr.SafeMemberExpr -> "(${exprString(e.receiver)}?.${e.name})"
        is Expr.ElvisExpr -> "(${exprString(e.left)} ?: ${exprString(e.right)})"
        is Expr.AssignExpr -> "(${exprString(e.target)} = ${exprString(e.value)})"
        is Expr.LambdaExpr -> {
            val params = if (e.params.isEmpty()) "" else e.params.joinToString(", ") + " -> "
            val body = when (val b = e.body) {
                is LambdaBody.ExprBody -> exprString(b.expr)
                is LambdaBody.BlockBody -> "{ ${b.block.statements.size} stmts }"
            }
            "{ $params$body }"
        }
        is Expr.ParenExpr -> "(${exprString(e.expr)})"
    }

    private fun type(t: TypeNode): Unit = line(typeString(t))

    private fun typeString(t: TypeNode): String = when (t) {
        is TypeNode.NamedType -> {
            val args = if (t.typeArgs.isEmpty()) "" else "<${t.typeArgs.joinToString(", ") { typeString(it) }}>"
            "${t.name}$args${if (t.nullable) "?" else ""}"
        }
    }

    private fun paramString(p: Decl.Param): String {
        val type = p.type?.let { ": ${typeString(it)}" } ?: ""
        val default = p.defaultValue?.let { " = ${exprString(it)}" } ?: ""
        return "${p.name}$type$default"
    }

    private fun opString(op: TokenType): String = when (op) {
        TokenType.ASSIGN -> "="
        TokenType.ELVIS -> "?:"
        TokenType.CI -> "||"
        TokenType.AJ -> "&&"
        TokenType.EQ -> "=="
        TokenType.NEQ -> "!="
        TokenType.LT -> "<"
        TokenType.GT -> ">"
        TokenType.LE -> "<="
        TokenType.GE -> ">="
        TokenType.PLUS -> "+"
        TokenType.MINUS -> "-"
        TokenType.STAR -> "*"
        TokenType.SLASH -> "/"
        TokenType.PERCENT -> "%"
        TokenType.BANG -> "!"
        else -> op.name
    }
}
