package krmelin

import krmelin.TestSupport.transpile
import krmelin.codegen.MainClassName
import krmelin.diag.DiagCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One test per defect confirmed by the M4 code review of the translation-and-runtime branch,
 * plus a guard wherever a fix could over-correct into a false positive.
 *
 * Every case here was reproduced against the built compiler before the fix landed: each either
 * produced silently wrong output or handed kotlinc a `.kt` naming identifiers the user never
 * wrote. The CLI-level findings (main-class derivation, jar contents, runtime placement) live in
 * `cli/CompileCommandTest` and `cli/RunCommandTest`, next to the commands they exercise.
 */
class M4ReviewRegressionTest {

    private fun diagnostics(source: String) = TestSupport.analyze(source).reporter

    // ── String templates ───────────────────────────────────────────────────

    @Test
    fun `an interpolation followed by identifier text keeps its braces`() {
        // `"${a}b"` emitted as `"$ab"` reads a *different* variable — silently, when one
        // happens to exist. The parser discards the source's braces, so the emitter must
        // re-derive them from what follows.
        val kt = transpile("robota rynek() {\n    toz a = \"x\"\n    zarvat(\"${'$'}{a}b\")\n}\n")
        assertEquals("fun main() {\n    val a = \"x\"\n    println(\"\${a}b\")\n}\n", kt)
    }

    @Test
    fun `an interpolation followed by a digit keeps its braces`() {
        val kt = transpile("robota rynek() {\n    toz a = \"x\"\n    zarvat(\"${'$'}{a}1\")\n}\n")
        assertEquals("fun main() {\n    val a = \"x\"\n    println(\"\${a}1\")\n}\n", kt)
    }

    @Test
    fun `an interpolation followed by punctuation keeps the short form`() {
        val kt = transpile("robota rynek() {\n    toz a = \"x\"\n    zarvat(\"${'$'}{a}!\")\n}\n")
        assertEquals("fun main() {\n    val a = \"x\"\n    println(\"\$a!\")\n}\n", kt)
    }

    @Test
    fun `adjacent interpolations keep the short form`() {
        val kt = transpile(
            "robota rynek() {\n    toz a = \"x\"\n    toz b = \"y\"\n    zarvat(\"${'$'}{a}${'$'}{b}\")\n}\n",
        )
        assertEquals("fun main() {\n    val a = \"x\"\n    val b = \"y\"\n    println(\"\$a\$b\")\n}\n", kt)
    }

    // ── Name capture and renaming ──────────────────────────────────────────

    @Test
    fun `a user function named println does not capture zarvat`() {
        // The user's `fun println` would win over kotlin.io's inside the same file, swallowing
        // every `zarvat` argument with exit code 0 and no diagnostic anywhere.
        val kt = transpile(
            "robota println(x: Dryst) {\n    pravit(\"[z] \")\n}\n\nrobota rynek() {\n    zarvat(\"ahoj\")\n}\n",
        )
        assertTrue("kotlin.io.println(\"ahoj\")" in kt, kt)
    }

    @Test
    fun `zarvat stays unqualified when nothing shadows it`() {
        val kt = transpile("robota rynek() {\n    zarvat(\"ahoj\")\n}\n")
        assertEquals("fun main() {\n    println(\"ahoj\")\n}\n", kt)
    }

    @Test
    fun `a call to rynek is renamed to main just like its declaration`() {
        val kt = transpile(
            "robota pomoc() {\n    rynek()\n}\n\nrobota rynek() {\n    zarvat(\"ahoj\")\n}\n",
        )
        assertEquals(
            "fun pomoc() {\n    main()\n}\n\nfun main() {\n    println(\"ahoj\")\n}\n",
            kt,
        )
    }

    @Test
    fun `a member named rynek is left alone`() {
        val kt = transpile("tryda Sichta {\n    robota rynek() {\n        zarvat(\"a\")\n    }\n}\n")
        assertTrue("fun rynek()" in kt, kt)
    }

    // ── Kotlin keyword collisions ──────────────────────────────────────────

    @Test
    fun `identifiers that are kotlin hard keywords are backticked`() {
        val kt = transpile("robota rynek() {\n    toz object = \"x\"\n    zarvat(object)\n}\n")
        assertEquals("fun main() {\n    val `object` = \"x\"\n    println(`object`)\n}\n", kt)
    }

    @Test
    fun `keyword parameters are backticked at declaration and at use`() {
        // `val` is an ordinary identifier in Krmelin — the dialect keywords are elsewhere.
        val kt = transpile("robota f(val: Cyslo) {\n    zarvat(val)\n}\n")
        assertEquals("fun f(`val`: Int) {\n    println(`val`)\n}\n", kt)
    }

    @Test
    fun `a keyword name inside a template is braced, not left bare`() {
        val kt = transpile("robota f(val: Cyslo) {\n    zarvat(\"${'$'}{val}\")\n}\n")
        assertEquals("fun f(`val`: Int) {\n    println(\"\${`val`}\")\n}\n", kt)
    }

    @Test
    fun `a keyword loop variable is backticked`() {
        val kt = transpile("robota f(xs: Halda<Cyslo>) {\n    prokazdy (is v xs) {\n        zarvat(is)\n    }\n}\n")
        assertTrue("for (`is` in xs)" in kt, kt)
    }

    @Test
    fun `soft keywords are not backticked`() {
        // `get`, `by`, `where`… are legal Kotlin identifiers; escaping them would be noise.
        val kt = transpile("robota rynek() {\n    toz get = 1\n    zarvat(get)\n}\n")
        assertEquals("fun main() {\n    val get = 1\n    println(get)\n}\n", kt)
    }

    // ── Subject-less `podle_teho` ──────────────────────────────────────────

    @Test
    fun `comma conditions in a subject-less when become or`() {
        val kt = transpile(
            """
            robota rynek() {
                toz a = fajne
                toz b = fajne
                podle_teho {
                    a, b -> zarvat("x")
                    boinak -> zarvat("y")
                }
            }
            """.trimIndent() + "\n",
        )
        assertTrue("a || b -> println(\"x\")" in kt, kt)
    }

    @Test
    fun `comma conditions with a subject stay commas`() {
        val kt = transpile(
            """
            robota rynek() {
                toz n = 1
                podle_teho (n) {
                    1, 2 -> zarvat("x")
                    boinak -> zarvat("y")
                }
            }
            """.trimIndent() + "\n",
        )
        assertTrue("1, 2 -> println(\"x\")" in kt, kt)
    }

    // ── Lowering guards ────────────────────────────────────────────────────

    @Test
    fun `dylka on a user class keeps the user's member name`() {
        val kt = transpile(
            "tryda Bedna(toz dylka: Cyslo)\n\nrobota f(b: Bedna) : Cyslo {\n    davaj b.dylka\n}\n",
        )
        assertTrue("return b.dylka" in kt, kt)
    }

    @Test
    fun `dylka on a type that has none is not rewritten to length`() {
        val kt = transpile("robota f(n: Cyslo) : Cyslo {\n    davaj n.dylka\n}\n")
        assertTrue("return n.dylka" in kt, kt)
    }

    @Test
    fun `naDryst on a safe call is lowered like a plain call`() {
        val kt = transpile("robota f(s: Dryst?) : Dryst? {\n    davaj s?.naDryst()\n}\n")
        assertEquals("fun f(s: String?): String? {\n    return s?.toString()\n}\n", kt)
    }

    // ── New front-end diagnostics ──────────────────────────────────────────

    @Test
    fun `an assignment in argument position is rejected`() {
        // Kotlin has no assignment expressions: `pridej(a = 7)` is a *named argument* there,
        // so the write silently vanishes.
        val reporter = diagnostics(
            "mozej a = 1\n\nrobota pridej(a: Cyslo) {\n    zarvat(a)\n}\n\nrobota rynek() {\n    pridej(a = 7)\n}\n",
        )
        TestSupport.expectDiag(reporter, DiagCode.ASSIGN_NOT_EXPRESSION)
    }

    @Test
    fun `an assignment as a whole statement is fine`() {
        val kt = transpile("robota rynek() {\n    mozej a = 1\n    a = 2\n}\n")
        assertEquals("fun main() {\n    var a = 1\n    a = 2\n}\n", kt)
    }

    @Test
    fun `an assignment as a when branch body is fine`() {
        // Kotlin's `when` entry body is a control-structure body, which admits an assignment.
        val kt = transpile(
            """
            robota rynek() {
                mozej a = 1
                toz c = fajne
                podle_teho {
                    c -> a = 2
                    boinak -> a = 3
                }
            }
            """.trimIndent() + "\n",
        )
        assertTrue("c -> a = 2" in kt, kt)
    }

    @Test
    fun `a top-level property with no value is rejected`() {
        TestSupport.expectDiag(diagnostics("toz pole: Halda<Cyslo>\n"), DiagCode.UNINITIALIZED_PROPERTY)
    }

    @Test
    fun `a class member with no value is rejected`() {
        TestSupport.expectDiag(
            diagnostics("tryda Bedna {\n    toz x: Cyslo\n}\n"),
            DiagCode.UNINITIALIZED_PROPERTY,
        )
    }

    @Test
    fun `an interface member with no value is allowed`() {
        // A `predpis` property is abstract, which is legal Kotlin.
        val reporter = diagnostics("predpis Merne {\n    toz x: Cyslo\n}\n")
        assertTrue(reporter.all.isEmpty(), reporter.render())
    }

    @Test
    fun `a local with no value is allowed`() {
        // Kotlin defers a local's initialization; only *use* before assignment is an error.
        val reporter = diagnostics("robota rynek() {\n    toz c: Dryst\n}\n")
        assertTrue(reporter.all.isEmpty(), reporter.render())
    }

    @Test
    fun `a parameterized lambda with no expected type is rejected`() {
        TestSupport.expectDiag(
            diagnostics("robota rynek() {\n    toz dvakrat = { x -> x * 2 }\n}\n"),
            DiagCode.LAMBDA_NEEDS_CONTEXT,
        )
    }

    @Test
    fun `a parameterless lambda needs no expected type`() {
        val reporter = diagnostics("robota rynek() {\n    toz f = { zarvat(\"hej\") }\n}\n")
        assertTrue(reporter.all.isEmpty(), reporter.render())
    }

    // ── Main-class derivation ──────────────────────────────────────────────

    @Test
    fun `main class names are sanitized the way kotlinc sanitizes them`() {
        // kotlinc sanitizes *then* capitalizes, mapping anything that is not a letter or an
        // ASCII digit to '_' and prefixing '_' when the result cannot start an identifier.
        assertEquals("My_progKt", MainClassName.forUnit(null, "my-prog"))
        assertEquals("_2fastKt", MainClassName.forUnit(null, "2fast"))
        assertEquals("_xKt", MainClassName.forUnit(null, "-x"))
        assertEquals("A_bKt", MainClassName.forUnit(null, "a.b"))
        assertEquals("_Kt", MainClassName.forUnit(null, ""))
        assertEquals("demo.HelloKt", MainClassName.forUnit("demo", "hello"))
    }
}
