package krmelin.types

import krmelin.TestSupport
import krmelin.diag.DiagCode
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regressions for the type-checker defects found in the M3 review of PR #12.
 *
 * One test per confirmed defect, plus a guard test wherever the fix could plausibly
 * over-correct into a false positive.
 */
class TypeCheckerRegressionTest {

    // ── An unresolvable return type must not cascade ────────────────────────

    @Test
    fun `an unresolvable return type reports only the unknown type`() {
        val source = "robota f() : Hruska {\n    davaj 1\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.UNKNOWN_TYPE_NAME, fragment = "Hruska")
        assertTrue(
            analyzed.reporter.all.none { it.code == DiagCode.UNEXPECTED_RETURN_VALUE },
            "an unknown return type must not be read as 'no return type':\n" +
                analyzed.reporter.render(),
        )
    }

    @Test
    fun `a function with no declared return type still rejects a returned value`() {
        val source = "robota f() {\n    davaj 1\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.UNEXPECTED_RETURN_VALUE)
    }

    // ── Diagnostics render nullability and type arguments ───────────────────

    @Test
    fun `a nullability mismatch names the nullable side with a question mark`() {
        val source = "robota f(a: Dryst?) {\n    toz b: Dryst = a\n}"
        val analyzed = TestSupport.analyze(source)
        val diag = TestSupport.expectDiag(analyzed.reporter, DiagCode.TYPE_MISMATCH)
        val text = listOfNotNull(diag.message, diag.highlight, diag.fix).joinToString("\n")
        assertTrue("Dryst?" in text, "message must distinguish the two sides:\n$text")
    }

    @Test
    fun `a non-Bul condition names a nullable Bul with a question mark`() {
        val source = "zapisnik tryda Mikrofon(mozej zapnuty: Bul)\n\n" +
            "robota f(m: Mikrofon?) {\n    kaj (m?.zapnuty) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        val diag = TestSupport.expectDiag(analyzed.reporter, DiagCode.CONDITION_NOT_BUL)
        val text = listOfNotNull(diag.message, diag.highlight, diag.fix).joinToString("\n")
        assertTrue("Bul?" in text, "message must distinguish the two sides:\n$text")
    }

    // ── Type identity is per declaration, not per emitted Kotlin name ───────

    @Test
    fun `a user class named like a prelude Kotlin target is a distinct type`() {
        val source = "zapisnik tryda String(toz x: Cyslo)\n\nrobota f() : String {\n    davaj \"ahoj\"\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.RETURN_TYPE_MISMATCH)
    }

    @Test
    fun `a prelude type still matches itself`() {
        val source = "robota f() : Dryst {\n    davaj \"ahoj\"\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── Property inference is order-independent ─────────────────────────────

    @Test
    fun `a property referring to a later property still infers its type`() {
        val source = "toz a = b\ntoz b = 5\n\nrobota f() : Dryst {\n    davaj a\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.RETURN_TYPE_MISMATCH, fragment = "Cyslo")
    }

    @Test
    fun `mutually referring properties do not recurse forever`() {
        // Guard for the on-demand property inference: without a cycle break this overflows.
        val source = "toz a = b\ntoz b = a\n"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── Call arguments are type-checked ─────────────────────────────────────

    @Test
    fun `a wrongly typed call argument is reported`() {
        val source = "robota f(x: Cyslo) {\n}\n\nrobota rynek() {\n    f(\"dryst\")\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.TYPE_MISMATCH, fragment = "Cyslo")
    }

    @Test
    fun `a correctly typed call argument is clean`() {
        val source = "robota f(x: Cyslo) {\n}\n\nrobota rynek() {\n    f(1)\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── Boolean operators check their operands ──────────────────────────────

    @Test
    fun `aj with non-Bul operands is reported`() {
        val source = "robota rynek(i: Cyslo, j: Cyslo) {\n    kaj (i aj j) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        assertTrue(
            analyzed.reporter.all.any { it.code == DiagCode.CONDITION_NOT_BUL },
            "wrapping non-Bul operands in 'aj' must not defeat the check:\n" +
                analyzed.reporter.render(),
        )
    }

    @Test
    fun `negating a non-Bul is reported`() {
        val source = "robota rynek(i: Cyslo) {\n    kaj (!i) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        assertTrue(
            analyzed.reporter.all.any { it.code == DiagCode.CONDITION_NOT_BUL },
            "negation must not launder a non-Bul operand:\n${analyzed.reporter.render()}",
        )
    }

    @Test
    fun `aj over real Bul operands is clean`() {
        val source = "zapisnik tryda Mikrofon(mozej zapnuty: Bul)\n\n" +
            "robota f(m: Mikrofon) {\n    kaj (m.zapnuty aj !m.zapnuty) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── Constructor and safe-call arity ─────────────────────────────────────

    @Test
    fun `a user class constructor call is arity-checked`() {
        val source = "zapisnik tryda Havir(toz mejno: Dryst)\n\n" +
            "robota rynek() {\n    zarvat(Havir(\"a\", \"b\", \"c\").mejno)\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.ARITY_MISMATCH, fragment = "Havir")
    }

    @Test
    fun `a correct user class constructor call is clean`() {
        val source = "zapisnik tryda Havir(toz mejno: Dryst)\n\n" +
            "robota rynek() {\n    zarvat(Havir(\"a\").mejno)\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `a safe call is arity-checked like an unsafe one`() {
        val source = "robota f(s: Dryst?) {\n    zarvat(s?.naDryst(\"moc\", \"argumentu\"))\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.ARITY_MISMATCH, fragment = "naDryst")
    }

    @Test
    fun `a correct safe call is clean`() {
        val source = "robota f(s: Dryst?) {\n    zarvat(s?.naDryst())\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── Writes through a mozej parameter are type-checked ───────────────────

    @Test
    fun `assigning a wrong type to a mozej parameter is reported`() {
        val source = "robota r(mozej x: Cyslo) {\n    x = \"dryst\"\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.TYPE_MISMATCH, fragment = "Cyslo")
    }

    @Test
    fun `assigning a correct type to a mozej parameter is clean`() {
        val source = "robota r(mozej x: Cyslo) {\n    x = 3\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }
}
