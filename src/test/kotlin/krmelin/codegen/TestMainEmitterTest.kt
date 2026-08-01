package krmelin.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import krmelin.codegen.TestDiscovery.TestEntry

/**
 * The generated registry. Deterministic character-for-character output: it is reviewable
 * by eye in diffs, and the runner argv contract (`args[0]` glob, `args[1]` verbose flag)
 * is pinned here where it can only change intentionally.
 */
class TestMainEmitterTest {

    @Test
    fun `two entries render the full registry source`() {
        val entries = listOf(
            TestEntry("delka_chuj_vraci_nulu", "delka_chuj_vraci_nulu()"),
            TestEntry(
                "HierarchieParta.deleni_nulou_rozdava",
                "HierarchieParta().deleni_nulou_rozdava()",
            ),
        )
        val expected = """
            import krmelin.runtime.Sichta
            import krmelin.runtime.spustSichty

            fun main(args: Array<String>) {
                val sichty = listOf(
                    Sichta("delka_chuj_vraci_nulu") { delka_chuj_vraci_nulu() },
                    Sichta("HierarchieParta.deleni_nulou_rozdava") { HierarchieParta().deleni_nulou_rozdava() },
                )
                kotlin.system.exitProcess(
                    spustSichty(
                        sichty,
                        filter = args.getOrNull(0)?.takeIf { it.isNotEmpty() },
                        verbose = args.getOrNull(1) == "v",
                    ),
                )
            }
        """.trimIndent() + "\n"
        assertEquals(expected, TestMainEmitter.emit(entries))
    }

    @Test
    fun `no entries render an empty typed registry that still compiles and runs`() {
        val expected = """
            import krmelin.runtime.Sichta
            import krmelin.runtime.spustSichty

            fun main(args: Array<String>) {
                val sichty = listOf<Sichta>()
                kotlin.system.exitProcess(
                    spustSichty(
                        sichty,
                        filter = args.getOrNull(0)?.takeIf { it.isNotEmpty() },
                        verbose = args.getOrNull(1) == "v",
                    ),
                )
            }
        """.trimIndent() + "\n"
        assertEquals(expected, TestMainEmitter.emit(emptyList()))
    }
}
