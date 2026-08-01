package krmelin.resolve

import kotlin.test.Test
import kotlin.test.assertTrue
import krmelin.TestSupport
import krmelin.TestSupport.expectDiag
import krmelin.diag.DiagCode

/**
 * PorubaUnit assertion functions (Plan.md §6) are prelude robota symbols: test sources
 * call them plain, and the emitter later wires them to `krmelin.runtime` via a wildcard
 * import — the resolver only needs their names and shapes.
 */
class PreludeAssertionsTest {

    @Test
    fun `a sichta calling every assertion resolves and typechecks clean`() {
        val source = """
            @Sichta robota vsecko_sedi() {
                musi_byt(2, 2)
                musi_byt(2, 2, "dvojka je dvojka")
                nesmi_byt(2, 3)
                je_fajne(fajne)
                je_nyt(nyt)
                je_chuj(chuj)
                neni_chuj(2)
                ma_dostat({ dostanes DelenoNulou("bum") })
                blizko(2.0, 2.0, 0.001)
            }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        assertTrue(analyzed.reporter.all.isEmpty(), "expected clean front end, got:\n${analyzed.reporter.render()}")
    }

    @Test
    fun `assertion with too few arguments is HAV350`() {
        val analyzed = TestSupport.analyze("robota rynek() { musi_byt(1) }")
        expectDiag(analyzed.reporter, DiagCode.ARITY_MISMATCH)
    }

    @Test
    fun `passing a non-Bul to je_fajne is HAV300`() {
        val analyzed = TestSupport.analyze("robota rynek() { je_fajne(1) }")
        expectDiag(analyzed.reporter, DiagCode.TYPE_MISMATCH)
    }
}
