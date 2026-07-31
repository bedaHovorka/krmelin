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
    fun `nullable generic type parses type arguments before the nullable marker`() {
        val cu = parse("robota f(x: Zoznam<Cyslo>?) { }")
        val fn = singleFun(cu)
        val type = assertIs<krmelin.ast.TypeNode.NamedType>(fn.params[0].type)
        assertEquals("Zoznam", type.name)
        assertTrue(type.nullable)
        assertEquals(1, type.typeArgs.size)
        assertEquals("Cyslo", (type.typeArgs[0] as krmelin.ast.TypeNode.NamedType).name)
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
    fun `if elseif else chain AST captures every branch`() {
        val cu = parse("""
            robota f(x: Cyslo) {
                kaj (x > 0) {
                    davaj 1
                } kajtez (x < 0) {
                    davaj -1
                } boinak {
                    davaj 0
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val ifStmt = assertIs<Stmt.IfStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertEquals(1, ifStmt.elseIfs.size)
        assertNotNull(ifStmt.elseBlock)
    }

    @Test
    fun `when with a subject and multi-value branch`() {
        val cu = parse("""
            robota f(x: Cyslo) {
                podle_teho (x) {
                    1, 2 -> "small"
                    boinak -> "big"
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val whenStmt = assertIs<Stmt.WhenStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertNotNull(whenStmt.subject)
        assertEquals(2, whenStmt.branches[0].conditions.size)
    }

    @Test
    fun `when branch may have a block body`() {
        val cu = parse("""
            robota f() {
                podle_teho {
                    fajne -> {
                        davaj 1
                    }
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val whenStmt = assertIs<Stmt.WhenStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertIs<krmelin.ast.WhenBody.BlockBody>(whenStmt.branches[0].body)
    }

    @Test
    fun `for loop AST has variable name and iterable`() {
        val cu = parse("""
            robota f(xs: Zoznam) {
                prokazdy (x v xs) {
                    zarvat(x)
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val forStmt = assertIs<Stmt.ForStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertEquals("x", forStmt.name)
        assertIs<Expr.NameExpr>(forStmt.iterable)
    }

    @Test
    fun `while loop AST has condition and body`() {
        val cu = parse("""
            robota f() {
                rubaj (fajne) {
                    zdybat
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val whileStmt = assertIs<Stmt.WhileStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertIs<Expr.BoolLit>(whileStmt.condition)
        assertIs<Stmt.BreakStmt>(whileStmt.body.statements[0])
    }

    @Test
    fun `continue statement parses inside a loop`() {
        val cu = parse("""
            robota f() {
                rubaj (fajne) {
                    dalej
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val whileStmt = assertIs<Stmt.WhileStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertIs<Stmt.ContinueStmt>(whileStmt.body.statements[0])
    }

    @Test
    fun `bare davaj with no value parses as a null-valued return`() {
        val cu = parse("robota f() { davaj }")
        val fn = singleFun(cu)
        val ret = assertIs<Stmt.ReturnStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertNull(ret.value)
    }

    @Test
    fun `try with only a catch and no finally`() {
        val cu = parse("""
            robota f() {
                pultik {
                    zarvat(1)
                } bitka (e: Flakanec) {
                    zarvat(2)
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val tryStmt = assertIs<Stmt.TryStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertEquals(1, tryStmt.catches.size)
        assertNull(tryStmt.finallyBlock)
    }

    @Test
    fun `try with only a finally and no catch`() {
        val cu = parse("""
            robota f() {
                pultik {
                    zarvat(1)
                } fajront {
                    zarvat(2)
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val tryStmt = assertIs<Stmt.TryStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertTrue(tryStmt.catches.isEmpty())
        assertNotNull(tryStmt.finallyBlock)
    }

    @Test
    fun `try with multiple catch clauses`() {
        val cu = parse("""
            robota f() {
                pultik {
                    zarvat(1)
                } bitka (a: Flakanec) {
                    zarvat(2)
                } bitka (b: Flakanec) {
                    zarvat(3)
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val tryStmt = assertIs<Stmt.TryStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertEquals(2, tryStmt.catches.size)
        assertEquals("a", tryStmt.catches[0].param.name)
        assertEquals("b", tryStmt.catches[1].param.name)
    }

    @Test
    fun `parameter default value is parsed`() {
        val cu = parse("robota f(x: Cyslo = 1) { }")
        val fn = singleFun(cu)
        assertNotNull(fn.params[0].defaultValue)
        assertIs<Expr.IntLit>(fn.params[0].defaultValue)
    }

    @Test
    fun `rozdava with multiple thrown types`() {
        val cu = parse("robota f() rozdava A, B { }")
        val fn = singleFun(cu)
        assertEquals(listOf("A", "B"), fn.throwsTypes.map { (it as krmelin.ast.TypeNode.NamedType).name })
    }

    @Test
    fun `top level property declaration is parsed`() {
        val cu = parse("toz x: Cyslo = 1")
        assertEquals(1, cu.declarations.size)
        val prop = assertIs<Decl.PropertyDecl>(cu.declarations[0])
        assertEquals("x", prop.name)
        assertFalse(prop.isMutable)
    }

    @Test
    fun `class body may declare a mutable property member`() {
        val cu = parse("""
            tryda Foo {
                mozej x: Cyslo = 1
            }
        """.trimIndent())
        val cls = assertIs<Decl.ClassDecl>(cu.declarations[0])
        assertEquals(1, cls.members.size)
        val prop = assertIs<Decl.PropertyDecl>(cls.members[0])
        assertTrue(prop.isMutable)
    }

    @Test
    fun `class body may declare multiple members`() {
        val cu = parse("""
            tryda Foo {
                toz x: Cyslo = 1
                robota bar() { }
            }
        """.trimIndent())
        val cls = assertIs<Decl.ClassDecl>(cu.declarations[0])
        assertEquals(2, cls.members.size)
        assertIs<Decl.PropertyDecl>(cls.members[0])
        assertIs<Decl.FunDecl>(cls.members[1])
    }

    @Test
    fun `jedynak declares a singleton object`() {
        val cu = parse("jedynak Foo { }")
        val cls = assertIs<Decl.ClassDecl>(cu.declarations[0])
        assertTrue(cls.isObject)
        assertFalse(cls.isData)
        assertFalse(cls.isInterface)
    }

    @Test
    fun `predpis declares an interface`() {
        val cu = parse("predpis Foo { }")
        val cls = assertIs<Decl.ClassDecl>(cu.declarations[0])
        assertTrue(cls.isInterface)
        assertFalse(cls.isData)
        assertFalse(cls.isObject)
    }

    @Test
    fun `non wildcard import parses a plain qualified name`() {
        val cu = parse("privezt krmelin.baza.Vec")
        val imp = cu.imports[0]
        assertEquals(listOf("krmelin", "baza", "Vec"), imp.name)
        assertFalse(imp.wildcard)
    }

    @Test
    fun `expression bodied function is parsed`() {
        val cu = parse("robota f() = 1")
        val fn = singleFun(cu)
        val body = assertIs<krmelin.ast.FunBody.ExprBody>(fn.body)
        assertIs<Expr.IntLit>(body.expr)
    }

    @Test
    fun `top level property without a type annotation infers from the initializer`() {
        val cu = parse("toz x = 1")
        val prop = assertIs<Decl.PropertyDecl>(cu.declarations[0])
        assertNull(prop.type)
        assertNotNull(prop.initializer)
    }

    @Test
    fun `parser tolerates an empty token list`() {
        val reporter = DiagnosticReporter()
        val cu = Parser(emptyList(), "empty.krm", reporter).parse()
        assertFalse(reporter.hasErrors)
        assertTrue(cu.declarations.isEmpty())
    }

    @Test
    fun `when subject may use redundant empty parens`() {
        val cu = parse("""
            robota f() {
                podle_teho () {
                    boinak -> 1
                }
            }
        """.trimIndent())
        val fn = singleFun(cu)
        val whenStmt = assertIs<Stmt.WhenStmt>((fn.body as krmelin.ast.FunBody.BlockBody).block.statements[0])
        assertNull(whenStmt.subject)
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
