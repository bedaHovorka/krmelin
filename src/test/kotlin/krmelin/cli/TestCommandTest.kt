package krmelin.cli

import com.github.ajalt.clikt.testing.test
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `krmelin test` behaviour (Plan.md §6, §11): discovery under a path, exit codes
 * 0 pass / 1 failure-or-error / 2 compile-error, all-or-nothing front end. The few tests
 * that exercise embedded kotlinc + a spawned JVM are marked — each pays a real compile.
 */
class TestCommandTest {

    private fun tempDir(): File = Files.createTempDirectory("krmelin-test-test").toFile()

    private fun krm(dir: File, name: String, source: String): File =
        File(dir, name).apply { parentFile?.mkdirs(); writeText(source.trimIndent() + "\n") }

    private val onePassingTest = """
        @Sichta robota sedi_to() {
            musi_byt(2, 2)
        }
    """

    @Test
    fun `a missing path exits 2`() {
        val result = TestCommand().test("tady/nic/neni")
        assertEquals(2, result.statusCode, result.stderr)
        assertTrue("hawaryja" in result.stderr, result.stderr)
    }

    @Test
    fun `a path that is not a krm file exits 2`() {
        val dir = tempDir()
        val notKrm = File(dir, "poznamky.txt").apply { writeText("cau") }
        val result = TestCommand().test(notKrm.absolutePath)
        assertEquals(2, result.statusCode, result.stderr)
    }

    @Test
    fun `a directory without krm files announces itself and exits 0`() {
        val result = TestCommand().test(tempDir().absolutePath)
        assertEquals(0, result.statusCode, result.output + result.stderr)
        assertTrue("oznam" in result.output + result.stderr, result.output + result.stderr)
    }

    @Test
    fun `front-end diagnostics in any file kill the whole run with exit 2`() {
        val dir = tempDir()
        krm(dir, "sedi.krm", onePassingTest)
        krm(dir, "rozbita.krm", "robota rynek() { pravit(cizi) }")

        val result = TestCommand().test(dir.absolutePath)
        assertEquals(2, result.statusCode, result.stderr)
        assertTrue("hawaryja" in result.stderr, result.stderr)
        assertTrue("HAV220" in result.stderr, result.stderr)
    }

    @Test
    fun `two files mapping to the same facade class exit 2 with HAV412`() {
        val dir = tempDir()
        krm(dir, "alfa/sichty.krm", onePassingTest)
        krm(dir, "beta/sichty.krm", onePassingTest)

        val result = TestCommand().test(dir.absolutePath)
        assertEquals(2, result.statusCode, result.stderr)
        assertTrue("HAV412" in result.stderr, result.stderr)
    }

    @Test
    fun `a root-package file named KrmelinTestMain clashes with the generated registry (HAV412)`() {
        val dir = tempDir()
        krm(dir, "KrmelinTestMain.krm", onePassingTest)

        val result = TestCommand().test(dir.absolutePath)
        assertEquals(2, result.statusCode, result.stderr)
        assertTrue("HAV412" in result.stderr, result.stderr)
        assertTrue("KrmelinTestMain" in result.stderr, result.stderr)
    }

    @Test
    fun `a single passing test file exits 0`() {
        val dir = tempDir()
        val src = krm(dir, "sichty.krm", onePassingTest)
        val result = TestCommand().test(src.absolutePath)
        assertEquals(0, result.statusCode, result.output + result.stderr)
    }

    @Test
    fun `a failing test exits 1 and a filter that excludes it exits 0`() {
        val dir = tempDir()
        krm(dir, "sichty.krm", """
            @Sichta robota spadla() {
                musi_byt(1, 2)
            }
        """)
        val without = TestCommand().test(dir.absolutePath)
        assertEquals(1, without.statusCode, without.output + without.stderr)

        val filtered = TestCommand().test(dir.absolutePath, "--filter", "nic_co_sedi*")
        assertEquals(0, filtered.statusCode, filtered.output + filtered.stderr)
    }

    @Test
    fun `verbose mode still exits 0 for a passing run`() {
        val dir = tempDir()
        val src = krm(dir, "sichty.krm", onePassingTest)
        val result = TestCommand().test(src.absolutePath, "-v")
        assertEquals(0, result.statusCode, result.output + result.stderr)
    }

    @Test
    fun `a resolver warning echoes but does not fail the run`() {
        val dir = tempDir()
        krm(dir, "varovaci.krm", """
            tryda BezeStrachu {
                @Sichta robota nikdy_ji_nespustis() {
                    musi_byt(1, 1)
                }
            }
        """)
        val result = TestCommand().test(dir.absolutePath)
        assertEquals(0, result.statusCode, result.output + result.stderr)
        assertTrue("HAV235" in result.stderr, result.stderr)
    }
}
