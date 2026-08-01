package krmelin.e2e

import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * M5 DoD end-to-end over the built fat jar (Plan.md §8): `krmelin test` runs the demo,
 * prints the §6 console format, and returns 0/1/2 for pass, failure/error, and
 * compile-error. Timing lines are nondeterministic — asserted by regex.
 */
@Tag("e2e")
class TestE2eTest {

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
        return ProcessBuilder(listOf("java", "-jar", jar.absolutePath) + argv)
            .redirectErrorStream(true)
            .start()
    }

    private data class CliResult(val exitCode: Int, val output: String)

    private fun cli(vararg argv: String): CliResult {
        val proc = runCli(*argv)
        val output = proc.inputStream.bufferedReader().readText()
        return CliResult(proc.waitFor(), output)
    }

    @Test
    fun `the demo runs, prints the specified format and exits 0`() {
        val result = cli("test", "examples/porubaunit_demo.krm")
        assertEquals(0, result.exitCode, result.output)
        assertTrue(result.output.startsWith("PorubaUnit — šichta začíná"), result.output)
        assertTrue(Regex("  ✓ [^ ]+ +\\(\\d+ ms\\)").containsMatchIn(result.output), result.output)
        assertTrue(
            Regex("\n\nFajront: \\d+ prošl\\w*, 0 spadlo, 0 chyb — za \\d+ ms").containsMatchIn(result.output),
            result.output,
        )
    }

    @Test
    fun `a failing test exits 1 and quotes the krm span`() {
        val result = cli("test", "src/test/fixtures/porubaunit/fail")
        assertEquals(1, result.exitCode, result.output)
        assertTrue("✗" in result.output, result.output)
        assertTrue(
            "spadlo v src/test/fixtures/porubaunit/fail/spadna_sichta.krm" in result.output,
            result.output,
        )
        assertTrue("čekal sem:  0" in result.output && "dostal sem: 3" in result.output, result.output)
        assertTrue(", 1 spadla, 0 chyb" in result.output, result.output)
    }

    @Test
    fun `an errored test exits 1 with the bang marker and the flakanec name`() {
        val result = cli("test", "src/test/fixtures/porubaunit/error")
        assertEquals(1, result.exitCode, result.output)
        assertTrue("!" in result.output, result.output)
        assertTrue("dostal ju: DelenoNulou" in result.output, result.output)
        assertTrue(", 0 spadlo, 1 chyba" in result.output, result.output)
    }

    @Test
    fun `uncompilable test sources exit 2`() {
        val result = cli("test", "src/test/fixtures/porubaunit/broken")
        assertEquals(2, result.exitCode, result.output)
        assertTrue("hawaryja" in result.output, result.output)
    }

    @Test
    fun `the passing fixture exits 0`() {
        val result = cli("test", "src/test/fixtures/porubaunit/pass")
        assertEquals(0, result.exitCode, result.output)
        assertTrue("PorubaUnit — šichta začíná" in result.output, result.output)
    }
}
