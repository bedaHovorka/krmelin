package krmelin.resolve

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krmelin.TestSupport
import krmelin.TestSupport.expectDiag
import krmelin.diag.DiagCode

/**
 * PorubaUnit annotation validation (Plan.md §6): `@Sichta` belongs on a parameterless
 * `robota` (never `rynek`), `@Parta` on a plain `tryda`; misuses get their own codes so
 * a misplaced annotation fails loudly instead of being silently dropped.
 */
class AnnotationValidationTest {

    @Test
    fun `@Sichta on a property is HAV230`() {
        val analyzed = TestSupport.analyze("@Sichta toz x: Cyslo = 1")
        expectDiag(analyzed.reporter, DiagCode.SICHTA_NOT_ON_ROBOTA, fragment = "@Sichta")
    }

    @Test
    fun `test robota with parameters is HAV231`() {
        val analyzed = TestSupport.analyze("@Sichta robota f(a: Cyslo) { }")
        expectDiag(analyzed.reporter, DiagCode.SICHTA_WITH_PARAMS, fragment = "parametry")
    }

    @Test
    fun `bodiless test robota in a predpis is HAV231`() {
        val analyzed = TestSupport.analyze("predpis P { @Sichta robota f() }")
        expectDiag(analyzed.reporter, DiagCode.SICHTA_WITH_PARAMS)
    }

    @Test
    fun `@Sichta on rynek is HAV232`() {
        val analyzed = TestSupport.analyze("@Sichta robota rynek() { }")
        expectDiag(analyzed.reporter, DiagCode.SICHTA_ON_RYNEK, fragment = "rynek")
    }

    @Test
    fun `@Sichta member named rynek is clean — only top-level rynek is the entry point`() {
        val analyzed = TestSupport.analyze("@Parta tryda T { @Sichta robota rynek() { } }")
        assertTrue(analyzed.reporter.all.isEmpty(), "expected clean front end, got:\n${analyzed.reporter.render()}")
    }

    @Test
    fun `@Parta on a robota is HAV233`() {
        val analyzed = TestSupport.analyze("@Parta robota f() { }")
        expectDiag(analyzed.reporter, DiagCode.PARTA_NOT_ON_TRYDA, fragment = "@Parta")
    }

    @Test
    fun `@Parta on a property is HAV233`() {
        val analyzed = TestSupport.analyze("@Parta toz x: Cyslo = 1")
        expectDiag(analyzed.reporter, DiagCode.PARTA_NOT_ON_TRYDA)
    }

    @Test
    fun `@Parta on a jedynak is HAV233`() {
        val analyzed = TestSupport.analyze("@Parta jedynak J { }")
        expectDiag(analyzed.reporter, DiagCode.PARTA_NOT_ON_TRYDA)
    }

    @Test
    fun `@Parta on a predpis is HAV233`() {
        val analyzed = TestSupport.analyze("@Parta predpis P { }")
        expectDiag(analyzed.reporter, DiagCode.PARTA_NOT_ON_TRYDA)
    }

    @Test
    fun `@Parta class with a required ctor param and a member test is HAV234`() {
        val analyzed = TestSupport.analyze("@Parta tryda T(a: Cyslo) { @Sichta robota f() { } }")
        expectDiag(analyzed.reporter, DiagCode.PARTA_CTOR_PARAM_REQUIRED, fragment = "a")
    }

    @Test
    fun `@Parta class with all-default ctor params and a member test is clean`() {
        val analyzed = TestSupport.analyze("@Parta tryda T(a: Cyslo = 1) { @Sichta robota f() { } }")
        assertTrue(analyzed.reporter.all.isEmpty(), "expected clean front end, got:\n${analyzed.reporter.render()}")
    }

    @Test
    fun `@Parta class with a required ctor param but no member tests is clean`() {
        val analyzed = TestSupport.analyze("@Parta tryda T(a: Cyslo) { }")
        assertTrue(analyzed.reporter.all.isEmpty(), "expected clean front end, got:\n${analyzed.reporter.render()}")
    }

    @Test
    fun `@Sichta member of a plain class warns HAV235`() {
        val analyzed = TestSupport.analyze("tryda T { @Sichta robota f() { } }")
        val matches = analyzed.reporter.warnings.filter { it.code == DiagCode.SICHTA_OUTSIDE_PARTA }
        assertEquals(1, matches.size, "expected exactly one HAV235 warning, got:\n${analyzed.reporter.render()}")
        assertTrue(analyzed.reporter.errors.isEmpty(), "expected no errors, got:\n${analyzed.reporter.render()}")
    }

    @Test
    fun `top-level @Sichta and @Parta member tests pass clean`() {
        val source = """
            @Sichta robota vrchni() { }
            @Parta tryda Sichty {
                @Sichta robota vnorena() { }
            }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        assertTrue(analyzed.reporter.all.isEmpty(), "expected clean front end, got:\n${analyzed.reporter.render()}")
    }
}
