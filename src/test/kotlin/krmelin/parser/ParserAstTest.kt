package krmelin.parser

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserAstTest {
    private fun parse(source: String): Decl.CompilationUnit {
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "test.krm", reporter).lex()
        val parser = Parser(tokens, "test.krm", reporter)
        val cu = parser.parse()
        assertFalse(reporter.hasErrors, reporter.render())
        return cu
    }

    private fun singleFun(cu: Decl.CompilationUnit): Decl.FunDecl {
        assertEquals(1, cu.declarations.size)
        return assertIs<Decl.FunDecl>(cu.declarations[0])
    }

    @Test
    fun `hello AST has package and main function with println call`() {
        val cu = parse("""
            sachta demo
            robota rynek() {
                zarvat("Toz vitaj, Krmelin!")
            }
        """.trimIndent())
        assertNotNull(cu.packageDecl)
        assertEquals(listOf("demo"), cu.packageDecl!!.name)
        val fn = singleFun(cu)
        assertEquals("rynek", fn.name)
        val block = assertIs<Stmt.Block>((fn.body as krmelin.ast.FunBody.BlockBody).block)
        assertEquals(1, block.statements.size)
        val exprStmt = assertIs<Stmt.ExprStmt>(block.statements[0])
        val call = assertIs<Expr.CallExpr>(exprStmt.expr)
        assertEquals("zarvat", (call.callee as Expr.NameExpr).name)
    }

    @Test
    fun `data class AST has constructor params with mutability`() {
        val cu = parse("zapisnik tryda Haviř(toz mejno: Dryst, mozej odrubano: Cyslo)")
        val cls = assertIs<Decl.ClassDecl>(cu.declarations[0])
        assertTrue(cls.isData)
        assertEquals(2, cls.params.size)
        assertFalse(cls.params[0].isMutable)
        assertTrue(cls.params[1].isMutable)
    }

    @Test
    fun `null safety AST has safe call and elvis`() {
        val cu = parse("""
            robota delka(s: Dryst?) : Cyslo {
                davaj s?.dylka ?: 0
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val ret = assertIs<Stmt.ReturnStmt>(
            (fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0]
        )
        val elvis = assertIs<Expr.ElvisExpr>(ret.value)
        assertIs<Expr.SafeMemberExpr>(elvis.left)
        assertIs<Expr.IntLit>(elvis.right)
    }

    @Test
    fun `rozdava is parsed into throws list`() {
        val cu = parse("robota foo() rozdava Flakanec { }")
        val fn = singleFun(cu)
        assertEquals(1, fn.throwsTypes.size)
        assertEquals("Flakanec", (fn.throwsTypes[0] as krmelin.ast.TypeNode.NamedType).name)
    }

    @Test
    fun `try catch finally AST is built`() {
        val cu = parse("""
            robota rynek() {
                pultik {
                    zarvat(1)
                } bitka (f: Flakanec) {
                    zarvat(2)
                } fajront {
                    zarvat(3)
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val tryStmt = assertIs<Stmt.TryStmt>(
            (fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0]
        )
        assertEquals(1, tryStmt.catches.size)
        assertNotNull(tryStmt.finallyBlock)
    }

    @Test
    fun `subject less when AST has null subject`() {
        val cu = parse("""
            robota rynek() {
                podle_teho {
                    fajne -> 1
                    boinak -> 0
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val whenStmt = assertIs<Stmt.WhenStmt>(
            (fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0]
        )
        assertNull(whenStmt.subject)
        assertEquals(2, whenStmt.branches.size)
        assertTrue(whenStmt.branches[1].isElse)
    }

    @Test
    fun `nic parses as null literal`() {
        val cu = parse("robota rynek() { davaj nic }")
        val fn = singleFun(cu)
        val ret = assertIs<Stmt.ReturnStmt>(
            (fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0]
        )
        assertIs<Expr.NullLit>(ret.value)
    }

    @Test
    fun `wildcard import parses with the dot-star suffix`() {
        val cu = parse("privezt krmelin.baza.*")
        assertEquals(1, cu.imports.size)
        val imp = cu.imports[0]
        assertEquals(listOf("krmelin", "baza"), imp.name)
        assertTrue(imp.wildcard)
    }

    @Test
    fun `Parta annotation on a class is preserved`() {
        val cu = parse("@Parta tryda Foo { }")
        val cls = assertIs<Decl.ClassDecl>(cu.declarations[0])
        assertEquals(listOf("Parta"), cls.annotations)
        assertTrue(cls.isParta)
    }

    @Test
    fun `non-data class may have constructor parameters`() {
        val cu = parse("tryda Foo(x: Cyslo) { }")
        val cls = assertIs<Decl.ClassDecl>(cu.declarations[0])
        assertFalse(cls.isData)
        assertEquals(1, cls.params.size)
        assertEquals("x", cls.params[0].name)
    }

    @Test
    fun `AST nodes carry accurate source spans`() {
        val cu = parse("robota f() { davaj a }")
        val fn = singleFun(cu)
        val ret = assertIs<Stmt.ReturnStmt>(
            (fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0]
        )
        val name = assertIs<Expr.NameExpr>(ret.value)
        assertEquals("a", name.name)
        // "a" sits at column 20 of the single line: robota f() { davaj a }
        assertEquals(1, name.span.startLine)
        assertEquals(20, name.span.startCol)
    }

    @Test
    fun `string template interpolation names carry real spans`() {
        val cu = parse("robota f() { zarvat(\"\$foo\") }")
        val fn = singleFun(cu)
        val exprStmt = assertIs<Stmt.ExprStmt>(
            (fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0]
        )
        val call = assertIs<Expr.CallExpr>(exprStmt.expr)
        val tmpl = assertIs<Expr.StringTemplate>(call.args[0])
        // "$foo" has a single interpolation part (no leading text).
        val interp = assertIs<TemplatePart.Interpolation>(tmpl.parts[0])
        val name = assertIs<Expr.NameExpr>(interp.expr)
        assertEquals("foo", name.name)
        // "foo" starts at column 23: ...zarvat("$foo")  -> $ at 22, f at 23
        assertEquals(1, name.span.startLine)
        assertEquals(23, name.span.startCol)
    }
}
