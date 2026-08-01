package krmelin.e2e

import org.junit.jupiter.api.Tag
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * End-to-end over the built fat jar (Plan.md §9/§11, M4 DoD): `krmelin run
 * examples/hello.krm` must print exactly `Toz vitaj, Krmelin!`, and `krmelin compile`
 * must reproduce the checked-in golden. Depends on the shadow jar, wired as
 * `tasks.test { dependsOn(tasks.shadowJar) }` in build.gradle.kts.
 */
@Tag("e2e")
class RunE2eTest {

    private val jar: File by lazy {
        val candidates =
            File("build/libs").listFiles { f -> f.name.matches(Regex("krmelin-.*\\.jar")) && "slim" !in f.name }
                ?.sortedBy { it.name }.orEmpty()
        if (candidates.isEmpty()) {
            fail("fat jar not found under build/libs — the test task is expected to dependOn shadowJar")
        }
        candidates.last()
    }

    private fun runCli(vararg argv: String): Process {
        val java = "${System.getProperty("java.home")}/bin/java"
        return ProcessBuilder(listOf(java, "-jar", jar.absolutePath) + argv)
            .redirectErrorStream(true)
            .start()
    }

    @Test
    fun `krmelin run examples hello prints the greeting`() {
        val proc = runCli("run", "examples/hello.krm")
        val output = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor(), output)
        assertEquals("Toz vitaj, Krmelin!", output.trim())
    }

    @Test
    fun `krmelin compile examples hello reproduces the golden`() {
        val out = Files.createTempDirectory("krmelin-e2e").resolve("hello.kt").toFile()
        val proc = runCli("compile", "examples/hello.krm", "-o", out.absolutePath)
        val output = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor(), output)
        assertEquals(File("tests/golden/hello.kt.expected").readText(), out.readText())
    }

    @Test
    fun `krmelin run of the exceptions golden exits 0 via pultik fajront`() {
        val proc = runCli("run", "tests/golden/exceptions.krm")
        val output = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor(), output)
        // mikrofon defaults to chuj → the robota throws, bitka catches, fajront runs last.
        assertEquals(
            listOf("dostal sem: mikrofon je vypnuty", "fajront, chlapi"),
            output.trim().lines(),
        )
    }

    @Test
    fun `krmelin run examples flakanci prints the deterministic pub brawl output`() {
        val proc = runCli("run", "examples/flakanci.krm")
        val output = proc.inputStream.bufferedReader().readText()
        assertEquals(0, proc.waitFor(), output)
        // Staged pub brawl: each Flakanci subtype provoked in turn, rethrow via outer pultik,
        // fajront on every path, nullable zpracujZpravu at the end.
        assertEquals(
            listOf(
                "Kolo 1: zluknul sem DelenoNulou \u2014 deleni nulou v kole 1",
                "Kolo 1: fajront",
                "Kolo 2: zluknul sem MimoBarak \u2014 mimo barak v kole 2",
                "Kolo 2: fajront",
                "Vnejsi bitka: chytil sem mimo barak v kole 2",
                "Vnejsi fajront",
                "Kolo 3: obecny Flakanec \u2014 zly dryst v kole 3",
                "Kolo 3: fajront",
                "Kolo 4: obecny Flakanec \u2014 chujovy flakanec v kole 4",
                "Kolo 4: fajront",
                "Kolo 5: fajront",
                "Zprava: zadna zprava",
                "Zprava: Toz vitaj z Flakanci!",
                "Konec sichty",
            ),
            output.trim().lines(),
        )
    }
}
