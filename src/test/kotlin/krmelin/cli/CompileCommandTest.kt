package krmelin.cli

import com.github.ajalt.clikt.testing.test
import java.io.File
import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `krmelin compile` behaviour: golden output, exit codes 0/1/2 per Plan.md §11,
 * `--jar` packaging. Diagnostics are echoed with `err = true` so Clikt's test()
 * captures them on stderr.
 */
class CompileCommandTest {

    private fun tempDir(): File = Files.createTempDirectory("krmelin-compile-test").toFile()

    @Test
    fun `compile golden input emits the golden output and exits 0`() {
        val out = File(tempDir(), "hello.kt")
        val result = CompileCommand().test("tests/golden/hello.krm", "-o", out.absolutePath)

        assertEquals(0, result.statusCode, result.output)
        assertEquals(File("tests/golden/hello.kt.expected").readText(), out.readText())
    }

    @Test
    fun `default output path is the source path with a kt extension`() {
        val dir = tempDir()
        val src = File(dir, "ukazka.krm")
        src.writeText("robota rynek() {\n    zarvat(\"ahoj\")\n}\n")

        val result = CompileCommand().test(src.absolutePath)

        assertEquals(0, result.statusCode, result.output)
        assertEquals("fun main() {\n    println(\"ahoj\")\n}\n", File(dir, "ukazka.kt").readText())
    }

    @Test
    fun `front-end diagnostics render to stderr and exit 1`() {
        val dir = tempDir()
        val src = File(dir, "spatny.krm")
        src.writeText("robota rynek() {\n    zarvat(cizi)\n}\n")

        val result = CompileCommand().test(src.absolutePath)

        assertEquals(1, result.statusCode)
        assertTrue("hawaryja" in result.stderr, result.stderr)
        assertTrue("HAV220" in result.stderr, result.stderr)
        assertTrue(!File(dir, "spatny.kt").exists(), "no .kt should be written on error")
    }

    @Test
    fun `missing input file exits 2`() {
        val result = CompileCommand().test("neexistuje.krm")

        assertEquals(2, result.statusCode)
        assertTrue("hawaryja" in result.stderr, result.stderr)
    }

    @Test
    fun `jar and emit-only together are a usage error`() {
        val result = CompileCommand().test("tests/golden/hello.krm", "--jar", "--emit-only")

        assertEquals(2, result.statusCode)
        assertTrue(result.stderr.isNotEmpty(), result.output)
    }

    @Test
    fun `jar packaging produces a manifest with the resolved main class`() {
        val dir = tempDir()
        val jarOut = File(dir, "hello.jar")
        val result = CompileCommand().test(
            "tests/golden/hello.krm",
            "-o", File(dir, "hello.kt").absolutePath,
            "--jar",
        )

        assertEquals(0, result.statusCode, result.output)
        JarFile(jarOut).use { jar ->
            assertEquals("demo.HelloKt", jar.manifest.mainAttributes.getValue("Main-Class"))
            assertTrue(jar.getEntry("demo/HelloKt.class") != null)
        }
    }

    @Test
    fun `jar main class follows the output file name, not the source name`() {
        // kotlinc derives the facade from the file it was handed, so with `-o jine.kt` the
        // .krm base name would put a class in the manifest that is not in the jar.
        val dir = tempDir()
        val result = CompileCommand().test(
            "tests/golden/hello.krm",
            "-o", File(dir, "jine.kt").absolutePath,
            "--jar",
        )

        assertEquals(0, result.statusCode, result.output)
        JarFile(File(dir, "jine.jar")).use { jar ->
            assertEquals("demo.JineKt", jar.manifest.mainAttributes.getValue("Main-Class"))
            assertTrue(jar.getEntry("demo/JineKt.class") != null, "facade class missing from the jar")
        }
    }

    @Test
    fun `the produced jar bundles the stdlib and actually runs`() {
        val dir = tempDir()
        val jarOut = File(dir, "hello.jar")
        val result = CompileCommand().test(
            "tests/golden/hello.krm",
            "-o", File(dir, "hello.kt").absolutePath,
            "--jar",
        )
        assertEquals(0, result.statusCode, result.output)

        JarFile(jarOut).use { jar ->
            assertTrue(jar.getEntry("kotlin/jvm/internal/Intrinsics.class") != null, "stdlib not bundled")
        }

        val process = ProcessBuilder("java", "-jar", jarOut.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
        assertEquals("Toz vitaj, Krmelin!", output.trim())
    }

    @Test
    fun `compiling a flakanci program writes the runtime source beside the output`() {
        // The emitted .kt carries `import krmelin.runtime.*`; without Flakanci.kt next to it
        // the primary output of the primary command cannot be compiled by hand.
        val dir = tempDir()
        val result = CompileCommand().test(
            "tests/golden/exceptions.krm",
            "-o", File(dir, "exceptions.kt").absolutePath,
        )

        assertEquals(0, result.statusCode, result.output)
        val runtime = File(dir, "Flakanci.kt")
        assertTrue(runtime.exists(), "Flakanci.kt should be written beside the output")
        assertTrue("package krmelin.runtime" in runtime.readText(), runtime.readText())
    }

    @Test
    fun `compiling a porubaunit program writes both runtime sources beside the output`() {
        val dir = tempDir()
        val result = CompileCommand().test(
            "tests/golden/porubaunit.krm",
            "-o", File(dir, "porubaunit.kt").absolutePath,
        )

        assertEquals(0, result.statusCode, result.output)
        val runtime = File(dir, "PorubaUnit.kt")
        assertTrue(runtime.exists(), "PorubaUnit.kt should be written beside the output")
        assertTrue("package krmelin.runtime" in runtime.readText(), runtime.readText())
        assertTrue(File(dir, "Flakanci.kt").exists(), "PorubaUnit classifies Flakanci — it always travels along")
    }

    @Test
    fun `an existing different Flakanci kt is not overwritten`() {
        val dir = tempDir()
        val squatter = File(dir, "Flakanci.kt")
        squatter.writeText("// moje vlastni\n")

        val result = CompileCommand().test(
            "tests/golden/exceptions.krm",
            "-o", File(dir, "exceptions.kt").absolutePath,
        )

        assertEquals(0, result.statusCode, result.output)
        assertEquals("// moje vlastni\n", squatter.readText())
        assertTrue("HAV411" in result.stderr, result.stderr)
    }

    @Test
    fun `jar on a file with no entry point fails with HAV401 and no jar`() {
        val dir = tempDir()
        val src = File(dir, "knihovna.krm")
        src.writeText("robota pomoz(x: Cyslo) : Cyslo {\n    davaj x\n}\n")

        val jarOut = File(dir, "knihovna.jar")
        val result = CompileCommand().test(src.absolutePath, "-o", File(dir, "knihovna.kt").absolutePath, "--jar")

        assertEquals(1, result.statusCode, result.output)
        assertTrue("HAV401" in result.stderr, result.stderr)
        assertTrue(!jarOut.exists(), "no jar should be produced without an entry point")
    }
}
