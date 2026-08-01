package krmelin.lower

import krmelin.TestSupport
import krmelin.TestSupport.transpile
import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.Stmt
import krmelin.types.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The three transformations [Lowering] owns (Plan.md §5.6): `.dylka` → `.length`/`.size`,
 * `.naDryst()` → `.toString()`, and the `usesFlakanci` flag. Subject-less `podle_teho`
 * and `prokazdy` desugaring already happen in the parser and are not re-tested here.
 */
class LoweringTest {

    private fun lowered(source: String): TestSupport.Analyzed {
        val analyzed = TestSupport.analyze(source)
        assertTrue(analyzed.reporter.all.isEmpty(), analyzed.reporter.render())
        return analyzed
    }

    private fun exprBodyOf(analyzed: TestSupport.Analyzed, funName: String): Expr {
        val funDecl = analyzed.unit.declarations.filterIsInstance<Decl.FunDecl>().single { it.name == funName }
        val block = (funDecl.body as FunBody.BlockBody).block
        return (block.statements.single() as Stmt.ReturnStmt).value!!
    }

    // ── .dylka ─────────────────────────────────────────────────────────────

    @Test
    fun `dylka on Dryst lowers to length`() {
        val kt = transpile("robota f(s: Dryst) : Cyslo {\n    davaj s.dylka\n}\n")
        assertEquals("fun f(s: String): Int {\n    return s.length\n}\n", kt)
    }

    @Test
    fun `dylka on Halda lowers to size`() {
        val kt = transpile(
            "robota f(xs: Halda<Cyslo>) : Cyslo {\n    davaj xs.dylka\n}\n",
        )
        assertEquals("fun f(xs: List<Int>): Int {\n    return xs.size\n}\n", kt)
    }

    @Test
    fun `dylka on Kupa lowers to size`() {
        val kt = transpile(
            "robota f(m: Kupa<Dryst, Cyslo>) : Cyslo {\n    davaj m.dylka\n}\n",
        )
        assertEquals("fun f(m: Map<String, Int>): Int {\n    return m.size\n}\n", kt)
    }

    @Test
    fun `dylka on a flakanci-typed receiver lowers to length`() {
        // Flakanec exposes .dylka in the semantic prelude as a string-ish property alias;
        // exceptions have no size, so length is the right target.
        val kt = transpile(
            "robota delkaZpravy(fak: Flakanec) : Cyslo {\n    davaj fak.zprava.dylka\n}\n",
        )
        assertEquals(
            "import krmelin.runtime.*\n\nfun delkaZpravy(fak: Flakanec): Int {\n    return fak.zprava.length\n}\n",
            kt,
        )
    }

    @Test
    fun `dylka on an UNKNOWN receiver falls back to size`() {
        // The checker defers wildcard-imported receivers (no HAV220), so the receiver
        // type is UNKNOWN; documented fallback, matching Halda/Kupa, the common case.
        val analyzed = lowered("privezt neco.*\nrobota rynek() {\n    toz n = cizi.dylka\n}\n")
        val lowered = Lowering(analyzed.resolution).lower(analyzed.unit)
        val decl = lowered.unit.declarations.single() as Decl.FunDecl
        val init = ((decl.body as FunBody.BlockBody).block.statements.single() as Stmt.PropertyStmt).decl.initializer!!
        assertTrue(init is Expr.MemberExpr, "expected a member access, got $init")
        assertEquals("size", init.name)
    }

    // ── .naDryst() ─────────────────────────────────────────────────────────

    @Test
    fun `naDryst on a prelude type lowers to toString`() {
        val kt = transpile("robota f(i: Cyslo) : Dryst {\n    davaj i.naDryst()\n}\n")
        assertEquals("fun f(i: Int): String {\n    return i.toString()\n}\n", kt)
    }

    @Test
    fun `naDryst on a receiver inside a larger expression lowers to toString`() {
        val kt = transpile("robota rynek() {\n    zarvat(\"sum: \" + 1.naDryst())\n}\n")
        assertEquals("fun main() {\n    println(\"sum: \" + 1.toString())\n}\n", kt)
    }

    @Test
    fun `member named naDryst on a user class stays untouched`() {
        val kt = transpile(
            """
            tryda Zaznam {
                robota naDryst() : Dryst {
                    davaj "vlastni"
                }
            }

            robota f(z: Zaznam) : Dryst {
                davaj z.naDryst()
            }
            """.trimIndent(),
        )
        assertEquals(
            "class Zaznam() {\n    fun naDryst(): String {\n        return \"vlastni\"\n    }\n}" +
                "\n\nfun f(z: Zaznam): String {\n    return z.naDryst()\n}\n",
            kt,
        )
    }

    @Test
    fun `naDryst as plain member without call is untouched`() {
        // Lowering only rewrites the call shape `x.naDryst()`; a bare member reference
        // passes through (the checker already rejected it if it made no sense).
        val kt = transpile("robota f(i: Cyslo) {\n    zarvat(i.naDryst)\n}\n")
        assertEquals("fun f(i: Int) {\n    println(i.naDryst)\n}\n", kt)
    }

    // ── usesFlakanci ───────────────────────────────────────────────────────

    @Test
    fun `usesFlakanci is only true when the hierarchy is referenced`() {
        val hello = lowered("robota rynek() {\n    zarvat(\"ahoj\")\n}\n")
        assertEquals(false, Lowering(hello.resolution).lower(hello.unit).usesFlakanci)

        val withThrow = lowered("robota rynek() {\n    dostanes Flakanec(\"x\")\n}\n")
        assertEquals(true, Lowering(withThrow.resolution).lower(withThrow.unit).usesFlakanci)

        val withThrows = lowered("robota f() rozdava MimoBarak {\n}\n")
        assertEquals(true, Lowering(withThrows.resolution).lower(withThrows.unit).usesFlakanci)

        // A bare constructor/value reference (no throw, no try, no type annotation) must
        // also trip the flag — otherwise the runtime import + Flakanci.kt are omitted and
        // kotlinc fails on an unresolved name.
        val withBareRef = lowered("robota rynek() {\n    toz e = Flakanec(\"x\")\n    zarvat(e.zprava)\n}\n")
        assertEquals(true, Lowering(withBareRef.resolution).lower(withBareRef.unit).usesFlakanci)
    }

    // ── Identity preservation ──────────────────────────────────────────────

    @Test
    fun `lowering keeps untouched children identical so resolution still applies`() {
        val analyzed = lowered("robota f(i: Cyslo) : Dryst {\n    davaj i.naDryst()\n}\n")
        val before = exprBodyOf(analyzed, "f")
        val receiverBefore = ((before as Expr.CallExpr).callee as Expr.MemberExpr).receiver

        val lowered = Lowering(analyzed.resolution).lower(analyzed.unit)
        val funDecl = lowered.unit.declarations.single() as Decl.FunDecl
        val after = ((funDecl.body as FunBody.BlockBody).block.statements.single() as Stmt.ReturnStmt).value!!
        val receiverAfter = ((after as Expr.CallExpr).callee as Expr.MemberExpr).receiver

        // The receiver node was never copied — its exprTypes entry still resolves.
        assertSame(receiverBefore, receiverAfter)
        assertEquals("Int", analyzed.resolution.typeOf(receiverAfter).kotlinName)
    }
}
