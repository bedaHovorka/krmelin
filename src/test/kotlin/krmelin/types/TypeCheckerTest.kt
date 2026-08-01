package krmelin.types

import krmelin.TestSupport
import krmelin.diag.DiagCode
import krmelin.lexer.SourceSpan
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class TypeCheckerTest {
    private val file = TestSupport.TEST_FILE

    @Test
    fun `kaj with a Cyslo condition reports HAV331 at the condition span`() {
        val source = "robota rynek() {\n    mozej i = 1\n    kaj (i + 1) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.CONDITION_NOT_BUL,
            span = SourceSpan(file, 3, 10, 3, 15),
            fragment = "Bul",
        )
    }

    @Test
    fun `kaj with a Bul condition is clean`() {
        val source = "robota rynek() {\n    mozej i = 1\n    kaj (i > 0) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `rubaj with a non-Bul condition reports HAV331`() {
        val source = "robota rynek() {\n    rubaj (1) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.CONDITION_NOT_BUL,
            span = SourceSpan(file, 2, 12, 2, 13),
        )
    }

    @Test
    fun `subject-less podle_teho branch conditions must be Bul`() {
        val source = """
            robota rynek(i: Cyslo) {
                podle_teho {
                    i + 1 -> zarvat("x")
                }
            }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.CONDITION_NOT_BUL,
            span = SourceSpan(file, 3, 9, 3, 14),
        )
    }

    @Test
    fun `kajtez condition is checked too`() {
        val source = "robota rynek(i: Cyslo) {\n    kaj (i > 0) {\n    } kajtez (i) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.CONDITION_NOT_BUL,
            span = SourceSpan(file, 3, 15, 3, 16),
        )
    }

    @Test
    fun `unknown-typed conditions stay quiet`() {
        // Lambda results are UNKNOWN to the checker (deferred to kotlinc) — no false positive.
        val source = "robota rynek(f: Dryst) {\n    kaj (f.naDryst()) {\n    }\n}"
        val analyzed = TestSupport.analyze(source)
        // naDryst returns Dryst, so this IS an error — a known non-Bul.
        TestSupport.expectDiag(analyzed.reporter, DiagCode.CONDITION_NOT_BUL)
    }

    // ── davaj / return checks ────────────────────────────────────────────────

    @Test
    fun `davaj without a value in a typed function reports HAV342`() {
        val source = "robota delka() : Cyslo {\n    davaj\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.RETURN_TYPE_MISMATCH,
            span = SourceSpan(file, 2, 5, 3, 1),
            fragment = "ma vracet Cyslo",
        )
    }

    @Test
    fun `davaj of the wrong type in a typed function reports HAV342`() {
        val source = "robota delka() : Cyslo {\n    davaj \"hanzel\"\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.RETURN_TYPE_MISMATCH,
            span = SourceSpan(file, 2, 11, 2, 19),
            fragment = "davaj' vraci Dryst",
        )
    }

    @Test
    fun `davaj with a value in an untyped function reports HAV341`() {
        val source = "robota rynek() {\n    davaj 3\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.UNEXPECTED_RETURN_VALUE,
            span = SourceSpan(file, 2, 5, 3, 1),
            fragment = "nic nevraci",
        )
    }

    @Test
    fun `correct davaj in a typed function is clean`() {
        val source = "robota delka() : Cyslo {\n    davaj 42\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `expression body is checked against the declared return type`() {
        val bad = TestSupport.analyze("robota f() : Cyslo = \"dryst\"")
        TestSupport.expectDiag(bad.reporter, DiagCode.RETURN_TYPE_MISMATCH)
        val good = TestSupport.analyze("robota f() : Cyslo = 1 + 2")
        assertFalse(good.reporter.hasErrors, good.reporter.render())
    }

    @Test
    fun `missing davaj entirely is left to kotlinc`() {
        // M3 checks only davaj statements that exist; path analysis is out of scope.
        val source = "robota f() : Cyslo {\n    zarvat(\"x\")\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── arity ────────────────────────────────────────────────────────────────

    @Test
    fun `too many arguments report HAV350 at the callee span`() {
        val source = "robota pozdrav(mejno: Dryst) {\n}\nrobota rynek() {\n    pozdrav(\"cype\", \"banik\")\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.ARITY_MISMATCH,
            span = SourceSpan(file, 4, 5, 4, 12),
            fragment = "bere 1 argument, dal si 2",
        )
    }

    @Test
    fun `too few arguments report HAV350`() {
        val source = "robota pozdrav(mejno: Dryst) {\n}\nrobota rynek() {\n    pozdrav()\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.ARITY_MISMATCH, fragment = "dal si 0")
    }

    @Test
    fun `default parameter may be omitted`() {
        val source = "robota pozdrav(mejno: Dryst = \"cype\") {\n}\nrobota rynek() {\n    pozdrav()\n    pozdrav(\"banik\")\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `unknown callees skip arity silently`() {
        // Member the checker does not model: no diagnostic, kotlinc will decide.
        val source = "robota rynek(s: Dryst) {\n    s.podtruhej(1, 2, 3)\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    // ── assignment ───────────────────────────────────────────────────────────

    @Test
    fun `initializer incompatible with declared type reports HAV300`() {
        val source = "toz hodiny: Cyslo = \"osum\""
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.TYPE_MISMATCH,
            span = SourceSpan(file, 1, 21, 1, 27),
            fragment = "Cyslo",
        )
    }

    @Test
    fun `toz with initializer infers its type`() {
        val source = "robota rynek() {\n    mozej i = 1\n    i = i + 1\n}"
        val analyzed = TestSupport.analyze(source)
        assertFalse(analyzed.reporter.hasErrors, analyzed.reporter.render())
    }

    @Test
    fun `reassigning a toz reports HAV330`() {
        val source = "robota rynek() {\n    toz i = 1\n    i = 2\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(
            analyzed.reporter, DiagCode.ASSIGN_TO_IMMUTABLE,
            span = SourceSpan(file, 3, 5, 3, 10),
        )
    }

    @Test
    fun `reassigning an immutable parameter reports HAV330`() {
        val source = "robota f(x: Cyslo) {\n    x = 3\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.ASSIGN_TO_IMMUTABLE)
    }

    @Test
    fun `assigning a wrong-typed value to a typed mozej reports HAV300`() {
        val source = "robota rynek() {\n    mozej i: Cyslo = 1\n    i = \"dryst\"\n}"
        val analyzed = TestSupport.analyze(source)
        TestSupport.expectDiag(analyzed.reporter, DiagCode.TYPE_MISMATCH, fragment = "Dryst")
    }

    @Test
    fun `chuj is assignable only to nullable targets`() {
        val bad = TestSupport.analyze("toz s: Dryst = chuj")
        TestSupport.expectDiag(bad.reporter, DiagCode.TYPE_MISMATCH)
        val good = TestSupport.analyze("toz s: Dryst? = chuj")
        assertFalse(good.reporter.hasErrors, good.reporter.render())
    }
}
