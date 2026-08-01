package krmelin.types

import krmelin.TestSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

/**
 * Behaviors of the checker that the focused DoD tests did not reach: inference of every
 * literal/expression shape, subject-ful `podle_teho`, loops with `zdybat`/`dalej`,
 * arity message variants, member access edge cases. Each one asserts the pipeline outcome,
 * never the internals.
 */
class TypeCheckerCoverageTest {
    private fun clean(source: String) {
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `abstract function in a predpis passes unchecked`() = clean(
        "predpis Prace {\n    robota odpracuj() : Cyslo\n}",
    )

    @Test
    fun `property without initializer is accepted`() = clean(
        "robota rynek() {\n    mozej pozdeji: Cyslo\n    pozdeji = 1\n}",
    )

    @Test
    fun `podle_teho with a subject checks branch values without requiring Bul`() = clean(
        """
        robota rynek(i: Cyslo) {
            podle_teho (i) {
                1 -> zarvat("jedna")
                2, 3 -> {
                    zarvat("dva tri")
                }
                boinak -> zarvat("jine")
            }
        }
        """.trimIndent(),
    )

    @Test
    fun `loops with zdybat and dalej pass`() = clean(
        """
        robota rynek() {
            mozej i = 0
            rubaj (i < 10) {
                kaj (i == 5) {
                    zdybat
                }
                i = i + 1
                dalej
            }
        }
        """.trimIndent(),
    )

    @Test
    fun `condition of unknown type is not flagged`() = clean(
        "robota rynek(s: Dryst) {\n    kaj (s.necoNeznameho()) {\n    }\n}",
    )

    @Test
    fun `all literal shapes infer and pass`() = clean(
        """
        robota rynek() {
            toz a = 1
            toz b = 1.5
            toz c = fajne
            toz d = "dryst"
            toz f = 1.5 + 2.5
        }
        """.trimIndent(),
    )

    @Test
    fun `parens, unary minus and bang infer like their operands`() {
        clean("robota rynek() {\n    toz i = -(1 + 2)\n    kaj (!(i > 0)) {\n    }\n}")
    }

    @Test
    fun `string concatenation yields Dryst`() = clean(
        "robota rynek(mejno: Dryst) : Dryst {\n    davaj \"nazdar \" + mejno\n}",
    )

    @Test
    fun `arithmetic on non-numerics falls back to unknown without complaints`() = clean(
        "robota rynek() {\n    toz x = \"a\" - \"b\"\n}",
    )

    @Test
    fun `elvis with mismatched fallback is unknown, not an error`() = clean(
        "robota rynek(i: Cyslo?) {\n    toz x = i ?: \"jine\"\n}",
    )

    @Test
    fun `lambdas pass through unchecked`() = clean(
        // A parameterless lambda infers as `() -> Unit`, so it needs no context; the body is
        // still walked but nothing in it is checked.
        "robota rynek() {\n    toz f = { zarvat(\"hej\") }\n}",
    )

    @Test
    fun `assignment to a member access is not checked beyond inference`() {
        clean(
            """
            zapisnik tryda Havir(mozej odrubano: Cyslo)
            robota rynek(h: Havir) {
                h.odrubano = 5
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `class instantiation yields the class type`() = clean(
        "zapisnik tryda Havir(toz mejno: Dryst)\nrobota rynek() {\n    toz h = Havir(\"cype\")\n}",
    )

    @Test
    fun `safe member call passes receiver inference`() = clean(
        "robota rynek(s: Dryst?) {\n    toz d = s?.naDryst()\n}",
    )

    @Test
    fun `call of an arbitrary expression callee skips arity`() = clean(
        "robota rynek() {\n    (pravit)(\"x\")\n}",
    )

    @Test
    fun `arity message variants are dialect-shaped`() {
        // zero-param function called with one arg
        TestSupport.expectDiag(
            TestSupport.analyze("robota f() {\n}\nrobota r() {\n    f(1)\n}").reporter,
            krmelin.diag.DiagCode.ARITY_MISMATCH,
            fragment = "bere 0 argumentu, dal si 1",
        )
        // two required params called with none
        TestSupport.expectDiag(
            TestSupport.analyze(
                "robota f(a: Cyslo, b: Cyslo) {\n}\nrobota r() {\n    f()\n}",
            ).reporter,
            krmelin.diag.DiagCode.ARITY_MISMATCH,
            fragment = "bere 2 argumenty, dal si 0",
        )
        // five required params
        TestSupport.expectDiag(
            TestSupport.analyze(
                "robota f(a: Cyslo, b: Cyslo, c: Cyslo, d: Cyslo, e: Cyslo) {\n}\nrobota r() {\n    f()\n}",
            ).reporter,
            krmelin.diag.DiagCode.ARITY_MISMATCH,
            fragment = "bere 5 argumentu",
        )
        // only-defaults function called with too many
        TestSupport.expectDiag(
            TestSupport.analyze(
                "robota f(a: Cyslo = 1) {\n}\nrobota r() {\n    f(1, 2, 3)\n}",
            ).reporter,
            krmelin.diag.DiagCode.ARITY_MISMATCH,
            fragment = "bere 0 az 1, dal si 3",
        )
    }

    @Test
    fun `unknown member access leaves the checker silent`() {
        // Undeclared receiver name is HAV220's job; unknown member on a known type defers to kotlinc.
        TestSupport.expectDiag(
            TestSupport.analyze("robota rynek() {\n    toz x = neco.foo\n}").reporter,
            krmelin.diag.DiagCode.UNDECLARED_NAME,
        )
        clean("robota rynek(s: Dryst) {\n    toz x = s.neco\n}")
    }

    @Test
    fun `method reference without a call is unknown-typed, not an error`() = clean(
        "robota rynek(s: Dryst) {\n    toz f = s.naDryst\n}",
    )

    @Test
    fun `member access on chuj finds no type`() {
        val analyzed = TestSupport.analyze("robota rynek() {\n    toz x = chuj.dylka\n}")
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }
}
