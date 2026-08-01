package krmelin.codegen

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

/**
 * Behavioral tests for runtime/PorubaUnit.kt (Plan.md §6). The resource is Kotlin source,
 * not a compiler class, so there is no shortcut: it is extracted, compiled with a
 * scenario driver through the same embedded kotlinc the CLI uses, and driven reflectively
 * under captured stdout. One compile serves every scenario.
 *
 * Timing lines are nondeterministic, so anything carrying "(N ms)" / "za N ms" is asserted
 * by regex or `contains` on the surrounding text only.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PorubaUnitRuntimeTest {

    // Manual, not @TempDir: with PER_CLASS lifecycle a non-static @TempDir is resolved
    // only after @BeforeAll, exactly when this compile needs the directory.
    private lateinit var workDir: File

    private lateinit var loader: URLClassLoader

    @BeforeAll
    fun compileRuntimeAndDriver() {
        workDir = Files.createTempDirectory("porubaunit-test").toFile()
        workDir.deleteOnExit()
        val driver = File(workDir, "PorubaUnitDriver.kt")
        driver.writeText(DRIVER.trimIndent())
        val runtimeDir = File(workDir, "runtime").apply { mkdirs() }
        val sources = mutableListOf(driver)
        sources += KotlinBackend.extractFlakanci(runtimeDir)
        sources += KotlinBackend.extractPorubaUnit(runtimeDir)
        val classes = File(workDir, "classes")
        val err = ByteArrayOutputStream()
        val code = KotlinBackend.compileToDir(sources, classes, PrintStream(err))
        assertEquals(0, code, "runtime + driver failed to compile:\n$err")
        loader = URLClassLoader(arrayOf(classes.toURI().toURL()), javaClass.classLoader)
    }

    /** Runs one driver scenario with stdout captured; returns captured text + exit marker. */
    private fun scenario(name: String): String {
        val captured = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        try {
            val cls = loader.loadClass("PorubaUnitDriverKt")
            cls.getMethod("main", Array<String>::class.java).invoke(null, arrayOf(name))
        } finally {
            System.setOut(original)
        }
        return captured.toString(Charsets.UTF_8)
    }

    private fun assertExit(out: String, code: Int) {
        assertTrue("EXIT=$code" in out, "expected EXIT=$code in:\n$out")
    }

    @Test
    fun `all-passing run prints header, ticks and the prošly summary`() {
        val out = scenario("pass")
        assertTrue(out.startsWith("PorubaUnit — šichta začíná\n"), out)
        assertTrue(Regex("  ✓ alfa +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue(Regex("  ✓ beta +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue(Regex("\n\nFajront: 2 prošly, 0 spadlo, 0 chyb — za \\d+ ms").containsMatchIn(out), out)
        assertExit(out, 0)
    }

    @Test
    fun `a failing assertion reports its call site, aligned values and does not stop the shift`() {
        val out = scenario("fail")
        assertTrue(Regex("  ✗ spadla +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue("musi_byt spadlo v unit.krm:7:9" in out, out)
        assertTrue("      čekal sem:  0\n      dostal sem: 3" in out, out)
        // Isolation: the sichty before AND after the failing one still ran.
        assertTrue(Regex("  ✓ sedici +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue(Regex("  ✓ za_ni +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue("Fajront: 2 prošly, 1 spadla, 0 chyb" in out, out)
        assertExit(out, 1)
    }

    @Test
    fun `an uncaught throwable is an error, distinct from a failure`() {
        val out = scenario("error")
        assertTrue(Regex("  ! rozbila +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue("dostal ju: DelenoNulou" in out, out)
        assertTrue(Regex("  ✓ po_ni +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue("Fajront: 2 prošly, 0 spadlo, 1 chyba" in out, out)
        assertExit(out, 1)
    }

    @Test
    fun `ma_dostat is satisfied by any flakanec`() {
        val out = scenario("ma_dostat_flakanec")
        assertTrue(Regex("  ✓ dostane +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertExit(out, 0)
    }

    @Test
    fun `ma_dostat never swallows a framework failure inside its block`() {
        val out = scenario("ma_dostat_spadla")
        assertTrue(Regex("  ✗ neukryje +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue("musi_byt spadlo" in out, out)
        assertExit(out, 1)
    }

    @Test
    fun `ma_dostat with nothing thrown fails on purpose`() {
        val out = scenario("ma_dostat_zadny")
        assertTrue(Regex("  ✗ nic +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue("      dostal sem: zadny flakanec" in out, out)
        assertExit(out, 1)
    }

    @Test
    fun `a trailing assertion message prints as vzkaz`() {
        val out = scenario("vzkaz")
        assertTrue("      vzkaz: dvojka ma bejt dvojka" in out, out)
        assertExit(out, 1)
    }

    @Test
    fun `a filter glob skips non-matching tests`() {
        val out = scenario("filter")
        assertFalse("Parta.alfa" in out, out)
        assertTrue(Regex("  ✓ beta +\\(\\d+ ms\\)").containsMatchIn(out), out)
        assertTrue("Fajront: 1 prošla, 0 spadlo, 0 chyb" in out, out)
        assertExit(out, 0)
    }

    @Test
    fun `verbose announces each test before it runs`() {
        val out = scenario("verbose")
        val lines = out.lines()
        val announce = lines.indexOf("  » alfa")
        val tick = lines.indexOfFirst { it.contains("✓ alfa") }
        assertTrue(announce != -1 && tick != -1 && announce < tick, out)
    }

    @Test
    fun `an empty registry still prints the full frame`() {
        val out = scenario("empty")
        assertTrue(out.startsWith("PorubaUnit — šichta začíná\n"), out)
        assertTrue(Regex("\n\nFajront: 0 prošlo, 0 spadlo, 0 chyb — za \\d+ ms").containsMatchIn(out), out)
        assertExit(out, 0)
    }

    @Test
    fun `counts inflect Czech-style across one-few-many`() {
        val five = scenario("sklon5")
        assertTrue("Fajront: 5 prošlo, 0 spadlo, 0 chyb" in five, five)
        val twoWrong = scenario("sklon2f")
        assertTrue("Fajront: 0 prošlo, 2 spadly, 0 chyb" in twoWrong, twoWrong)
        val twoErrors = scenario("sklon2e")
        assertTrue("Fajront: 0 prošlo, 0 spadlo, 2 chyby" in twoErrors, twoErrors)
    }
}

private const val DRIVER = """
import krmelin.runtime.*

fun main(args: Array<String>) {
    val exit = when (args[0]) {
        "pass" -> spustSichty(listOf(
            Sichta("alfa") { musi_byt(2, 2) },
            Sichta("beta") { je_fajne(true) },
        ))
        "fail" -> spustSichty(listOf(
            Sichta("sedici") { musi_byt(1, 1) },
            Sichta("spadla") { musi_byt(3, 0, odkud = "unit.krm:7:9") },
            Sichta("za_ni") { je_nyt(false) },
        ))
        "error" -> spustSichty(listOf(
            Sichta("sedici") { },
            Sichta("rozbila") { throw DelenoNulou("bum") },
            Sichta("po_ni") { },
        ))
        "ma_dostat_flakanec" -> spustSichty(listOf(
            Sichta("dostane") { ma_dostat({ throw DelenoNulou("bum") }) },
        ))
        "ma_dostat_spadla" -> spustSichty(listOf(
            Sichta("neukryje") { ma_dostat({ musi_byt(1, 2) }) },
        ))
        "ma_dostat_zadny" -> spustSichty(listOf(
            Sichta("nic") { ma_dostat({ }) },
        ))
        "vzkaz" -> spustSichty(listOf(
            Sichta("vzkaznik") { musi_byt(1, 2, "dvojka ma bejt dvojka") },
        ))
        "filter" -> spustSichty(
            listOf(
                Sichta("Parta.alfa") { musi_byt(1, 2) },
                Sichta("beta") { },
            ),
            filter = "beta*",
        )
        "verbose" -> spustSichty(listOf(Sichta("alfa") { }), verbose = true)
        "empty" -> spustSichty(emptyList())
        "sklon5" -> spustSichty(List(5) { Sichta("s" + it) { } })
        "sklon2f" -> spustSichty(listOf(
            Sichta("a") { musi_byt(1, 2) },
            Sichta("b") { musi_byt(1, 2) },
        ))
        "sklon2e" -> spustSichty(listOf(
            Sichta("a") { throw Flakanec("bum") },
            Sichta("b") { throw Flakanec("bum") },
        ))
        else -> -1
    }
    println("EXIT=" + exit)
}
"""
