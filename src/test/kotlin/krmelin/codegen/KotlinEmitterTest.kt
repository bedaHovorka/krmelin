package krmelin.codegen

import krmelin.TestSupport.transpile
import krmelin.ast.TypeNode
import krmelin.lexer.SourceSpan
import krmelin.resolve.Resolution
import krmelin.resolve.Scope
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden-shape unit tests for [KotlinEmitter], family by family. The end-to-end golden
 * files in tests/golden/ cover the full samples; these pin down one behaviour each.
 */
class KotlinEmitterTest {

    // ── Units, packages, entry point, type rendering ───────────────────────

    @Test
    fun `package plus empty rynek emits package, blank line, fun main`() {
        val kt = transpile(
            """
            sachta demo

            robota rynek() {
            }
            """.trimIndent(),
        )
        assertEquals("package demo\n\nfun main() {\n}\n", kt)
    }

    @Test
    fun `non-entry rynek with parameters keeps its name`() {
        val kt = transpile("robota rynek(n: Cyslo) {\n}\n")
        assertEquals("fun rynek(n: Int) {\n}\n", kt)
    }

    @Test
    fun `member rynek keeps its name`() {
        val kt = transpile(
            """
            tryda Vrch {
                robota rynek() {
                }
            }
            """.trimIndent(),
        )
        assertEquals("class Vrch() {\n    fun rynek() {\n    }\n}\n", kt)
    }

    @Test
    fun `declared types render via kotlin names`() {
        val kt = transpile(
            """
            robota f(a: Dryst?, b: Halda<Cyslo>, c: Kupa<Cyslo, Dryst?>) : Bul {
                davaj fajne
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun f(a: String?, b: List<Int>, c: Map<Int, String?>): Boolean {\n    return true\n}\n",
            kt,
        )
    }

    @Test
    fun `unresolvable written type name passes through verbatim`() {
        // The resolver reports HAV221 for such names; the emitter must still emit the
        // source spelling and let kotlinc be the final judge (no false codegen errors).
        val scope = Scope(kind = Scope.Kind.FILE)
        val named = TypeNode.NamedType("Cizina", nullable = true, span = SourceSpan.NONE)
        assertEquals("Cizina?", KotlinEmitter(Resolution(scope)).renderType(named))
    }

    @Test
    fun `rynek with rozdava keeps its name`() {
        val kt = transpile(
            """
            robota rynek() rozdava Flakanec {
            }
            """.trimIndent(),
        )
        assertEquals("import krmelin.runtime.*\n\n@Throws(Flakanec::class)\nfun rynek() {\n}\n", kt)
    }

    // ── Literals, calls, simple statements ─────────────────────────────────

    @Test
    fun `zarvat and pravit rename to println and print`() {
        val kt = transpile(
            """
            robota rynek() {
                zarvat("Toz vitaj, Krmelin!")
                pravit(42)
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun main() {\n    println(\"Toz vitaj, Krmelin!\")\n    print(42)\n}\n",
            kt,
        )
    }

    @Test
    fun `user-declared pravit is never renamed`() {
        // The rename keys off the binding: only the prelude symbol maps to print.
        val kt = transpile(
            """
            robota pravit(x: Dryst) {
                zarvat(x)
            }

            robota rynek() {
                pravit("ahoj")
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun pravit(x: String) {\n    println(x)\n}\n\nfun main() {\n    pravit(\"ahoj\")\n}\n",
            kt,
        )
    }

    @Test
    fun `toz and mozej render as val and var`() {
        val kt = transpile(
            """
            robota rynek() {
                toz a: Cyslo = 1
                mozej b = 2
                toz c: Dryst
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun main() {\n    val a: Int = 1\n    var b = 2\n    val c: String\n}\n",
            kt,
        )
    }

    @Test
    fun `chuj and nic emit byte-identical Kotlin`() {
        // §4.6: nic is a milder synonym of chuj; both compile identically and neither
        // is second-class. Equality of output is the only observable proof left after lexing.
        val chuj = transpile("robota rynek() {\n    toz s: Dryst? = chuj\n}\n")
        val nic = transpile("robota rynek() {\n    toz s: Dryst? = nic\n}\n")
        assertEquals("fun main() {\n    val s: String? = null\n}\n", chuj)
        assertEquals(chuj, nic)
    }

    @Test
    fun `string literals re-escape quotes, dollars, and backslashes`() {
        val kt = transpile(
            """
            robota rynek() {
                zarvat("rekl \"cau\" a ${'$'}100\t!")
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun main() {\n    println(\"rekl \\\"cau\\\" a \\\$100\\t!\")\n}\n",
            kt,
        )
    }

    // ── String templates ───────────────────────────────────────────────────

    @Test
    fun `short name interpolation keeps the dollar-name form`() {
        val kt = transpile(
            """
            robota pozdrav(mejno: Dryst) {
                zarvat("Nazdar, ${'$'}mejno!")
            }
            """.trimIndent(),
        )
        assertEquals("fun pozdrav(mejno: String) {\n    println(\"Nazdar, \$mejno!\")\n}\n", kt)
    }

    @Test
    fun `expression interpolation keeps braces`() {
        val kt = transpile(
            """
            robota rynek() {
                toz f = 1
                zarvat("vysledek: ${'$'}{f + 1}")
            }
            """.trimIndent(),
        )
        assertEquals("fun main() {\n    val f = 1\n    println(\"vysledek: \${f + 1}\")\n}\n", kt)
    }

    // ── Operators, precedence, assignment ──────────────────────────────────

    @Test
    fun `binary operators map one to one and trust AST shape`() {
        // §4.3 precedence == Kotlin's, so no synthetic parens; source parens arrive as ParenExpr.
        val kt = transpile("robota rynek() {\n    zarvat(1 + 2 * 3 - 4 / 5 % 6)\n}\n")
        assertEquals("fun main() {\n    println(1 + 2 * 3 - 4 / 5 % 6)\n}\n", kt)
    }

    @Test
    fun `precedence of comparisons and boolean ops matches Kotlin`() {
        val kt = transpile("robota rynek() {\n    zarvat(1 < 2 aj 3 != 4 ci 5 >= 6)\n}\n")
        assertEquals("fun main() {\n    println(1 < 2 && 3 != 4 || 5 >= 6)\n}\n", kt)
    }

    @Test
    fun `source parens survive as parens `() {
        val kt = transpile("robota rynek() {\n    zarvat(2 * (3 + 4))\n}\n")
        assertEquals("fun main() {\n    println(2 * (3 + 4))\n}\n", kt)
    }

    @Test
    fun `comparisons, boolean ops, elvis and unary map correctly`() {
        val kt = transpile(
            """
            robota f(a: Cyslo, b: Cyslo, s: Dryst?) : Dryst {
                kaj (a == b ci a != b aj nyt ci a <= b ci a >= b ci a < b ci a > b) {
                    davaj s ?: "nic"
                } boinak {
                    davaj "x"
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun f(a: Int, b: Int, s: String?): String {\n" +
                "    if (a == b || a != b && false || a <= b || a >= b || a < b || a > b) {\n" +
                "        return s ?: \"nic\"\n" +
                "    } else {\n" +
                "        return \"x\"\n" +
                "    }\n" +
                "}\n",
            kt,
        )
    }

    @Test
    fun `unary bang and minus, assignment`() {
        val kt = transpile(
            """
            robota rynek() {
                mozej i = 0
                i = -i + 1
                kaj (nyt) {
                    zarvat(!fajne)
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun main() {\n    var i = 0\n    i = -i + 1\n    if (false) {\n        println(!true)\n    }\n}\n",
            kt,
        )
    }

    @Test
    fun `safe member and member chains`() {
        val kt = transpile(
            """
            robota delka(s: Dryst?) : Cyslo {
                davaj s?.dylka ?: 0
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun delka(s: String?): Int {\n    return s?.length ?: 0\n}\n",
            kt,
        )
    }

    // ── Control flow ───────────────────────────────────────────────────────

    @Test
    fun `kaj kajtez boinak chain`() {
        val kt = transpile(
            """
            robota f(x: Cyslo) {
                kaj (x < 0) {
                    zarvat("pod")
                } kajtez (x == 0) {
                    zarvat("nula")
                } boinak {
                    zarvat("nad")
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun f(x: Int) {\n" +
                "    if (x < 0) {\n        println(\"pod\")\n" +
                "    } else if (x == 0) {\n        println(\"nula\")\n" +
                "    } else {\n        println(\"nad\")\n    }\n" +
                "}\n",
            kt,
        )
    }

    @Test
    fun `rubaj prokazdy zdybat dalej`() {
        val kt = transpile(
            """
            robota rynek() {
                mozej i = 0
                rubaj (i < 10) {
                    i = i + 1
                    kaj (i == 3) {
                        dalej
                    }
                    kaj (i == 8) {
                        zdybat
                    }
                }
                prokazdy (x v pole) {
                    zarvat(x)
                }
            }
            """.trimIndent().let { "toz pole: Halda<Cyslo>\n\n$it" },
        )
        assertEquals(
            "val pole: List<Int>\n\n" +
                "fun main() {\n" +
                "    var i = 0\n" +
                "    while (i < 10) {\n" +
                "        i = i + 1\n" +
                "        if (i == 3) {\n            continue\n        }\n" +
                "        if (i == 8) {\n            break\n        }\n" +
                "    }\n" +
                "    for (x in pole) {\n        println(x)\n    }\n" +
                "}\n",
            kt,
        )
    }

    @Test
    fun `subject-less podle_teho with expression bodies and boinak`() {
        val kt = transpile(
            """
            robota rynek() {
                mozej i = 1
                podle_teho {
                    i % 15 == 0 -> zarvat("KobzoleBuzz")
                    boinak -> zarvat(i.naDryst())
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun main() {\n" +
                "    var i = 1\n" +
                "    when {\n" +
                "        i % 15 == 0 -> println(\"KobzoleBuzz\")\n" +
                "        else -> println(i.toString())\n" +
                "    }\n" +
                "}\n",
            kt,
        )
    }

    @Test
    fun `podle_teho with subject, comma conditions and block bodies`() {
        val kt = transpile(
            """
            robota f(x: Cyslo) {
                podle_teho (x) {
                    1, 2 -> {
                        zarvat("malo")
                        zarvat("fakt")
                    }
                    boinak -> zarvat("jiny")
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun f(x: Int) {\n" +
                "    when (x) {\n" +
                "        1, 2 -> {\n            println(\"malo\")\n            println(\"fakt\")\n        }\n" +
                "        else -> println(\"jiny\")\n" +
                "    }\n" +
                "}\n",
            kt,
        )
    }

    // ── Exceptions ─────────────────────────────────────────────────────────

    @Test
    fun `pultik bitka fajront emit try catch finally, dostanes emits throw`() {
        val kt = transpile(
            """
            robota rynek() {
                pultik {
                    dostanes Flakanec("bum")
                } bitka (f: Flakanec) {
                    zarvat(f.zprava)
                } bitka (d: DelenoNulou) {
                    zarvat("deleni")
                } fajront {
                    zarvat("fajront")
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "import krmelin.runtime.*\n\n" +
                "fun main() {\n" +
                "    try {\n        throw Flakanec(\"bum\")\n    }" +
                " catch (f: Flakanec) {\n        println(f.zprava)\n    }" +
                " catch (d: DelenoNulou) {\n        println(\"deleni\")\n    }" +
                " finally {\n        println(\"fajront\")\n    }\n" +
                "}\n",
            kt,
        )
    }

    @Test
    fun `no flakanci reference means no runtime import`() {
        val kt = transpile("robota rynek() {\n    zarvat(\"ahoj\")\n}\n")
        assertEquals("fun main() {\n    println(\"ahoj\")\n}\n", kt)
    }

    @Test
    fun `catch param type is the only flakanci reference needed for the import`() {
        val kt = transpile(
            """
            robota rynek() {
                pultik {
                    zarvat("ok")
                } bitka (f: ChujovyFlakanec) {
                    zarvat("zachyceno")
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "import krmelin.runtime.*\n\n" +
                "fun main() {\n" +
                "    try {\n        println(\"ok\")\n    }" +
                " catch (f: ChujovyFlakanec) {\n        println(\"zachyceno\")\n    }\n" +
                "}\n",
            kt,
        )
    }

    // ── Classes and imports ────────────────────────────────────────────────

    @Test
    fun `zapisnik tryda without members emits a one-line data class`() {
        val kt = transpile("zapisnik tryda Haviř(toz mejno: Dryst, mozej odrubano: Cyslo)\n")
        assertEquals("data class Haviř(val mejno: String, var odrubano: Int)\n", kt)
    }

    @Test
    fun `jedynak and predpis`() {
        val kt = transpile(
            """
            jedynak Sroubek

            predpis Stroj {
                robota bezi() : Bul
            }
            """.trimIndent(),
        )
        assertEquals(
            "object Sroubek\n\ninterface Stroj {\n    fun bezi(): Boolean\n}\n",
            kt,
        )
    }

    @Test
    fun `tryda with members wraps them in braces`() {
        val kt = transpile(
            """
            tryda Stroj(mozej bezi: Bul) {
                robota start() {
                    zarvat("start")
                }
            }
            """.trimIndent(),
        )
        assertEquals(
            "class Stroj(var bezi: Boolean) {\n    fun start() {\n        println(\"start\")\n    }\n}\n",
            kt,
        )
    }

    @Test
    fun `privezt imports including wildcard`() {
        val kt = transpile(
            """
            sachta demo
            privezt krmelin.baza.Vrtak
            privezt kotlin.math.*

            robota rynek() {
            }
            """.trimIndent(),
        )
        assertEquals(
            "package demo\n\nimport krmelin.baza.Vrtak\nimport kotlin.math.*\n\nfun main() {\n}\n",
            kt,
        )
    }

    @Test
    fun `top-level toz and expression-body robota`() {
        val kt = transpile(
            """
            toz cislo: Cyslo = 42

            robota dveXN(x: Cyslo) : Cyslo = x * 2
            """.trimIndent(),
        )
        assertEquals(
            "val cislo: Int = 42\n\nfun dveXN(x: Int): Int = x * 2\n",
            kt,
        )
    }

    // ── Lambdas and remaining literal shapes ───────────────────────────────

    @Test
    fun `lambda with expression body`() {
        val kt = transpile(
            """
            robota rynek() {
                toz dvakrat = { x -> x * 2 }
                zarvat(dvakrat(21))
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun main() {\n    val dvakrat = { x -> x * 2 }\n    println(dvakrat(21))\n}\n",
            kt,
        )
    }

    @Test
    fun `float and char-shaped literals and remaining escapes`() {
        val kt = transpile(
            "robota rynek() {\n    zarvat(1.5)\n    zarvat(\"a\\\\b\\r\\n\")\n}\n",
        )
        assertEquals(
            "fun main() {\n    println(1.5)\n    println(\"a\\\\b\\r\\n\")\n}\n",
            kt,
        )
    }

    @Test
    fun `bare braces parse as a lambda with a block body`() {
        // The grammar has no standalone block statement: a bare `{ ... }` in expression
        // position is a lambda literal, and its statements flatten with `; `.
        val kt = transpile(
            """
            robota rynek() {
                toz pozdrav = { zarvat("cau") }
            }
            """.trimIndent(),
        )
        assertEquals(
            "fun main() {\n    val pozdrav = { println(\"cau\") }\n}\n",
            kt,
        )
    }
}
