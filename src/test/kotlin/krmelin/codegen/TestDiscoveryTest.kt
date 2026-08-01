package krmelin.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import krmelin.TestSupport

/**
 * Discovery walks a compilation unit for `@Sichta` roboty and renders each as a fully
 * qualified invocation the generated KrmelinTestMain can call — top-level tests by their
 * package-qualified *function* name (the `<File>Kt` facade is a bytecode artifact invisible
 * to Kotlin source in the same module), `@Parta` member tests through a fresh class instance.
 */
class TestDiscoveryTest {

    @Test
    fun `a top-level sichta in the root package is invoked by name`() {
        // Not through the file facade: `<File>Kt` is a bytecode artifact invisible to
        // Kotlin source in the same module — qualified *function* names resolve, facades don't.
        val analyzed = TestSupport.analyze("@Sichta robota delka_chuj_vraci_nulu() { }")
        val entries = TestDiscovery.discover(analyzed.unit)
        assertEquals(
            listOf(TestDiscovery.TestEntry("delka_chuj_vraci_nulu", "delka_chuj_vraci_nulu()")),
            entries,
        )
    }

    @Test
    fun `a packaged unit qualifies the invocation with the package`() {
        val analyzed = TestSupport.analyze("sachta demo\n@Sichta robota sedi() { }")
        val entries = TestDiscovery.discover(analyzed.unit)
        assertEquals(listOf(TestDiscovery.TestEntry("sedi", "demo.sedi()")), entries)
    }

    @Test
    fun `parta member tests construct a fresh instance per test`() {
        val source = """
            @Parta tryda HierarchieParta {
                @Sichta robota deleni_nulou_rozdava() { }
            }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        val entries = TestDiscovery.discover(analyzed.unit)
        assertEquals(
            listOf(
                TestDiscovery.TestEntry(
                    "HierarchieParta.deleni_nulou_rozdava",
                    "HierarchieParta().deleni_nulou_rozdava()",
                ),
            ),
            entries,
        )
    }

    @Test
    fun `sichta members of a plain class are not discovered`() {
        val analyzed = TestSupport.analyze("tryda T { @Sichta robota f() { } }")
        assertTrue(TestDiscovery.discover(analyzed.unit).isEmpty())
    }

    @Test
    fun `entries keep declaration order`() {
        val source = """
            @Sichta robota prvni() { }
            @Sichta robota druhy() { }
            @Parta tryda Parta {
                @Sichta robota treti() { }
            }
            @Sichta robota ctvrty() { }
        """.trimIndent()
        val analyzed = TestSupport.analyze(source)
        val entries = TestDiscovery.discover(analyzed.unit)
        assertEquals(
            listOf("prvni", "druhy", "Parta.treti", "ctvrty"),
            entries.map { it.displayName },
        )
    }

    @Test
    fun `a test named after a Kotlin keyword is escaped`() {
        val analyzed = TestSupport.analyze("@Sichta robota when() { }")
        val entries = TestDiscovery.discover(analyzed.unit)
        assertEquals(listOf(TestDiscovery.TestEntry("when", "`when`()")), entries)
    }
}
