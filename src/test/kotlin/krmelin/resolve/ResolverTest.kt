package krmelin.resolve

import krmelin.TestSupport
import krmelin.diag.DiagCode
import krmelin.lexer.SourceSpan
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolverTest {
    @Test
    fun `undeclared name reports HAV220 at the exact name span`() {
        val source = "robota rynek() {\n    davaj blabla\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter,
            DiagCode.UNDECLARED_NAME,
            span = SourceSpan(TestSupport.TEST_FILE, 2, 11, 2, 17),
        )
    }

    @Test
    fun `undeclared name suggests the closest visible name`() {
        val source = "robota rynek() {\n    rynkk()\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter,
            DiagCode.UNDECLARED_NAME,
            fragment = "rynek",
        )
    }

    @Test
    fun `undeclared name mention names the problem plainly`() {
        val source = "robota rynek() {\n    davaj hodinyspanku\n}"
        val analyzed = TestSupport.analyze(source)
        val diag = TestSupport.expectDiag(analyzed.reporter, DiagCode.UNDECLARED_NAME)
        assertTrue(diag.message.contains("hodinyspanku"))
        assertTrue(diag.message.contains("neni"))
    }

    @Test
    fun `duplicate top-level declaration reports HAV201 at the second declaration`() {
        val source = "robota rynek() {\n}\nrobota rynek() {\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter,
            DiagCode.DUPLICATE_DECLARATION,
            span = SourceSpan(TestSupport.TEST_FILE, 3, 1, 4, 2),
        )
    }

    @Test
    fun `duplicate local declaration reports HAV201`() {
        val source = "robota rynek() {\n    toz hodiny = 1\n    mozej hodiny = 2\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.DUPLICATE_DECLARATION)
    }

    @Test
    fun `shadowing an outer name is a HAV210 warning, not an error`() {
        val source = "robota f(x: Cyslo) {\n    toz x = 1\n    zarvat(x)\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
        TestSupport.expectDiag(analyzed.reporter, DiagCode.SHADOWED_DECLARATION, fragment = "x")
    }

    @Test
    fun `forward references between top-level functions resolve clean`() {
        val source = """
            robota prvni() {
                zarvat(druha())
            }
            robota druha() : Dryst {
                davaj "banik"
            }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `name bindings are recorded for every resolvable NameExpr`() {
        val source = "robota rynek() {\n    zarvat(\"cyp\")\n}"
        val analyzed = TestSupport.analyze(source)
        val bound = analyzed.resolution.bindings.values
        assertTrue(bound.any { it.name == "zarvat" }, "expected zarvat binding, got $bound")
    }

    @Test
    fun `class constructor params and members resolve inside the class`() {
        val source = """
            zapisnik tryda Havir(toz mejno: Dryst, mozej odrubano: Cyslo) {
                robota plac() : Cyslo {
                    davaj odrubano
                }
            }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `unknown type name reports HAV221`() {
        val source = "robota f(x: Hruska) {\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.UNKNOWN_TYPE_NAME, fragment = "Hruska")
    }

    @Test
    fun `catch param and loop variable are visible in their blocks`() {
        val source = """
            robota rynek(xs: Halda<Cyslo>) {
                prokazdy (x v xs) {
                    zarvat(x)
                }
                pultik {
                    zarvat("jo")
                } bitka (f: Flakanec) {
                    zarvat(f.zprava)
                }
            }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `prelude names resolve from user code`() {
        val source = "robota rynek() {\n    pravit(\"a\")\n    zarvat(\"b\")\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
        assertEquals(0, analyzed.reporter.warnings.size, analyzed.reporter.render())
    }
}
