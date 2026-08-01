package krmelin.resolve

import krmelin.TestSupport
import krmelin.diag.DiagCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regressions for the resolver defects found in the M3 review of PR #12.
 *
 * One test per confirmed defect, plus a guard test wherever the fix could plausibly
 * over-correct (e.g. suppressing a warning class wholesale).
 */
class ResolverRegressionTest {

    // ── A class name is visible inside its own body and to earlier classes ───

    @Test
    fun `a class can name itself in a constructor parameter`() {
        val analyzed = TestSupport.analyze("zapisnik tryda Uzel(toz dalsi: Uzel?)")
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `a class can name itself in a member property type`() {
        val source = "tryda Uzel {\n    mozej dalsi: Uzel? = chuj\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `a method can declare its own class as the return type`() {
        val source = "tryda Havir {\n    robota kopie() : Havir? {\n        davaj chuj\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `a class body can name a class declared later in the file`() {
        val source = "tryda Prvni {\n    robota dej() : Druhy? {\n        davaj chuj\n    }\n}\n\n" +
            "tryda Druhy(toz x: Cyslo)"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `an genuinely unknown type in a class body is still reported`() {
        val source = "tryda Uzel {\n    mozej dalsi: Hruska? = chuj\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.UNKNOWN_TYPE_NAME, fragment = "Hruska")
    }

    // ── privezt imports ─────────────────────────────────────────────────────

    @Test
    fun `an imported name is declared into the file scope`() {
        val source = "privezt kotlin.math.abs\n\nrobota rynek() {\n    zarvat(abs(0).naDryst())\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `a wildcard import suppresses undeclared-name errors`() {
        val source = "privezt kotlin.math.*\n\nrobota rynek() {\n    zarvat(sqrt(2.0).naDryst())\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `without a matching import an unknown name is still reported`() {
        val source = "robota rynek() {\n    zarvat(abs(0).naDryst())\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.UNDECLARED_NAME, fragment = "abs")
    }

    @Test
    fun `a local declaration wins over an import of the same name`() {
        val source = "privezt kotlin.math.abs\n\nrobota abs(x: Cyslo) : Cyslo {\n    davaj x\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── Duplicate declarations are reported in source order ─────────────────

    @Test
    fun `a class duplicating an earlier function reports on the class`() {
        val source = "robota Foo() {\n}\n\ntryda Foo(toz x: Cyslo)"
        val analyzed = TestSupport.analyze(source)
        val diag = TestSupport.expectDiag(analyzed.reporter, DiagCode.DUPLICATE_DECLARATION)
        assertEquals(4, diag.span.startLine, "caret belongs on the redeclaration")
        assertTrue("1:1" in (diag.fix ?: ""), "first declaration is the line-1 robota: ${diag.fix}")
    }

    @Test
    fun `a function duplicating an earlier class reports on the function`() {
        val source = "tryda Foo(toz x: Cyslo)\n\nrobota Foo() {\n}"
        val analyzed = TestSupport.analyze(source)
        val diag = TestSupport.expectDiag(analyzed.reporter, DiagCode.DUPLICATE_DECLARATION)
        assertEquals(3, diag.span.startLine, "caret belongs on the redeclaration")
        assertTrue("1:1" in (diag.fix ?: ""), "first declaration is the line-1 tryda: ${diag.fix}")
    }

    // ── Constructor parameter defaults are bound ────────────────────────────

    @Test
    fun `an undeclared name in a constructor parameter default is reported`() {
        val source = "zapisnik tryda Havir(toz mejno: Dryst = neexistujeVubec)"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.UNDECLARED_NAME, fragment = "neexistujeVubec")
    }

    @Test
    fun `a valid constructor parameter default binds cleanly`() {
        val source = "toz vychozi = \"cype\"\n\nzapisnik tryda Havir(toz mejno: Dryst = vychozi)"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── Shadowing warnings ──────────────────────────────────────────────────

    @Test
    fun `a method parameter named like a constructor property does not warn`() {
        val source = "tryda Havir(toz mejno: Dryst) {\n" +
            "    robota prejmenuj(mejno: Dryst) : Dryst {\n        davaj mejno\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        assertTrue(analyzed.reporter.warnings.isEmpty(), analyzed.reporter.render())
    }

    @Test
    fun `a local shadowing an outer local still warns`() {
        val source = "robota rynek() {\n    toz x = 1\n    kaj (x > 0) {\n" +
            "        toz x = 2\n        zarvat(x.naDryst())\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        assertTrue(
            analyzed.reporter.warnings.any { it.code == DiagCode.SHADOWED_DECLARATION },
            "shadowing warnings must not be disabled wholesale:\n${analyzed.reporter.render()}",
        )
    }

    // ── Generic type-argument arity ─────────────────────────────────────────

    @Test
    fun `too many type arguments are reported`() {
        val source = "robota f(xs: Halda<Cyslo, Dryst>) {\n    zarvat(xs.dylka.naDryst())\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.TYPE_ARITY_MISMATCH, fragment = "Halda")
    }

    @Test
    fun `type arguments on a non-generic type are reported`() {
        val source = "robota f(s: Dryst<Cyslo>) : Dryst {\n    davaj s\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.TYPE_ARITY_MISMATCH, fragment = "Dryst")
        assertTrue(
            analyzed.reporter.all.none { it.code == DiagCode.RETURN_TYPE_MISMATCH },
            "a bogus type argument must not cascade into a return-type error:\n" +
                analyzed.reporter.render(),
        )
    }

    @Test
    fun `a correct type-argument count is clean`() {
        val source = "robota f(xs: Halda<Cyslo>) {\n    zarvat(xs.dylka.naDryst())\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }
}
