package krmelin.cli

import com.github.ajalt.clikt.testing.test
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `krmelin run` non-execution paths (the subprocess itself is covered by the e2e test,
 * which runs the built jar end to end).
 */
class RunCommandTest {

    private fun tempDir(): File = Files.createTempDirectory("krmelin-run-test").toFile()

    @Test
    fun `file without a parameterless rynek fails with HAV401`() {
        val src = File(tempDir(), "norynek.krm")
        src.writeText("robota pomocna() {\n    zarvat(\"pomoc\")\n}\n")

        val result = RunCommand().test(src.absolutePath)

        assertEquals(1, result.statusCode)
        assertTrue("HAV401" in result.stderr, result.stderr)
    }

    @Test
    fun `rynek with parameters is not an entry point`() {
        val src = File(tempDir(), "norynek.krm")
        src.writeText("robota rynek(x: Cyslo) {\n    zarvat(x)\n}\n")

        val result = RunCommand().test(src.absolutePath)

        assertEquals(1, result.statusCode)
        assertTrue("HAV401" in result.stderr, result.stderr)
    }

    @Test
    fun `front-end diagnostics exit 1 before any compilation`() {
        val src = File(tempDir(), "spatny.krm")
        src.writeText("robota rynek() {\n    zarvat(cizi)\n}\n")

        val result = RunCommand().test(src.absolutePath)

        assertEquals(1, result.statusCode)
        assertTrue("hawaryja" in result.stderr, result.stderr)
    }

    @Test
    fun `missing input file exits 2`() {
        assertEquals(2, RunCommand().test("neexistuje.krm").statusCode)
    }

    @Test
    fun `a program named Flakanci is not overwritten by the extracted runtime`() {
        // The runtime source is always called Flakanci.kt; extracting it into the same work
        // directory as the emitted program clobbered a user file of that name, and `run` then
        // died on a main class that no longer existed.
        val src = File(tempDir(), "Flakanci.krm")
        src.writeText(
            "robota rynek() {\n" +
                "    pultik {\n" +
                "        dostanes MimoBarak(\"ven\")\n" +
                "    } bitka (f: Flakanec) {\n" +
                "        zarvat(\"chyceno\")\n" +
                "    }\n" +
                "}\n",
        )

        val result = RunCommand().test(src.absolutePath)

        assertEquals(0, result.statusCode, result.output)
    }
}
