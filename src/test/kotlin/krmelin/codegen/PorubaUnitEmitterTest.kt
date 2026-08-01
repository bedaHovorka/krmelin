package krmelin.codegen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import krmelin.TestSupport

/**
 * PorubaUnit codegen integration (Plan.md §6): calls to the prelude assertion robota
 * carry their `.krm` call-site span in an injected `odkud` argument so failure output
 * can point at real test source, and any unit touching tests or assertions pulls in
 * `import krmelin.runtime.*`.
 */
class PorubaUnitEmitterTest {

    @Test
    fun `an assertion call carries its krm span as odkud`() {
        val kt = TestSupport.transpile(
            """
            robota rynek() {
                toz x = 3
                musi_byt(x, 2)
            }
            """.trimIndent(),
        )
        assertTrue(
            "musi_byt(x, 2, odkud = \"test.krm:3:5\")" in kt,
            "expected injected odkud in:\n$kt",
        )
    }

    @Test
    fun `an assertion call with a message keeps it and still gains odkud`() {
        val kt = TestSupport.transpile(
            """
            robota rynek() {
                musi_byt(2, 2, "dvojka")
            }
            """.trimIndent(),
        )
        assertTrue(
            "musi_byt(2, 2, \"dvojka\", odkud = \"test.krm:2:5\")" in kt,
            "expected injected odkud after the message in:\n$kt",
        )
    }

    @Test
    fun `ma_dostat with a message and a trailing lambda emits the block first`() {
        val kt = TestSupport.transpile(
            """
            robota rynek() {
                ma_dostat("mel flakanec") { dostanes DelenoNulou("bum") }
            }
            """.trimIndent(),
        )
        // The block is `telo` (first param); the message is `zprava`. The trailing lambda
        // parses AFTER the message, so the emitter must move it to the front or kotlinc sees
        // a String where a `() -> Unit` belongs.
        assertTrue(
            "ma_dostat({ throw DelenoNulou(\"bum\") }, \"mel flakanec\", odkud = \"test.krm:2:5\")" in kt,
            "expected block-first, then message, then odkud in:\n$kt",
        )
    }

    @Test
    fun `a user-defined robota with an assertion name is never injected`() {
        val kt = TestSupport.transpile(
            """
            robota musi_byt(a: Cyslo, b: Cyslo) {
                pravit(a + b)
            }
            robota rynek() {
                musi_byt(1, 2)
            }
            """.trimIndent(),
        )
        assertFalse("odkud" in kt, "user robota musi_byt must not be span-injected:\n$kt")
        assertFalse("import krmelin.runtime" in kt, "user shadowing must not pull the runtime in:\n$kt")
    }

    @Test
    fun `a unit calling assertions emits the runtime import`() {
        val kt = TestSupport.transpile("robota rynek() { je_fajne(2 == 2) }")
        assertTrue("import krmelin.runtime.*" in kt, "expected runtime import in:\n$kt")
    }

    @Test
    fun `a unit with only a @Sichta robota emits the runtime import`() {
        val kt = TestSupport.transpile("@Sichta robota zda_se() { }")
        assertTrue("import krmelin.runtime.*" in kt, "expected runtime import in:\n$kt")
    }

    @Test
    fun `a plain program emits no runtime import and no odkud`() {
        val kt = TestSupport.transpile("robota rynek() { pravit(1 + 1) }")
        assertFalse("krmelin.runtime" in kt, "plain program must stay runtime-free:\n$kt")
        assertFalse("odkud" in kt, kt)
    }
}
