package krmelin.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * The embedded kotlinc backend and the main-class resolver used by `run`/`--jar`.
 */
class KotlinBackendTest {

    private fun tempDir(): File = Files.createTempDirectory("krmelin-backend-test").toFile()

    @Test
    fun `valid kotlin compiles to class files`() {
        val dir = tempDir()
        val src = File(dir, "hello.kt")
        src.writeText("package demo\n\nfun main() {\n    println(\"ok\")\n}\n")
        val out = File(dir, "out")

        val code = KotlinBackend.compileToDir(listOf(src), out)

        assertEquals(0, code)
        assertTrue(File(out, "demo/HelloKt.class").exists(), "class files under $out: ${out.walkTopDown().toList()}")
    }

    @Test
    fun `broken kotlin yields a nonzero exit code`() {
        val dir = tempDir()
        val src = File(dir, "broken.kt")
        src.writeText("fun main() {\n    nope()\n}\n")

        assertNotEquals(0, KotlinBackend.compileToDir(listOf(src), File(dir, "out")))
    }

    @Test
    fun `emitted exceptions golden plus extracted runtime compiles`() {
        val dir = tempDir()
        val emitted = File("tests/golden/exceptions.kt.expected")
            .also { assertTrue(it.exists(), "generate goldens first") }
        val src = File(dir, "exceptions.kt")
        src.writeText(emitted.readText())
        val flakanci = KotlinBackend.extractFlakanci(dir)

        val code = KotlinBackend.compileToDir(listOf(src, flakanci), File(dir, "out"))

        assertEquals(0, code)
    }

    @Test
    fun `extractFlakanci writes the runtime source next to emitted code`() {
        val dir = tempDir()
        val file = KotlinBackend.extractFlakanci(dir)
        assertTrue(file.exists())
        assertTrue(file.readText().contains("package ${KotlinPrelude.RUNTIME_PACKAGE}"))
    }

    @Test
    fun `main class name includes package and capitalizes the file base`() {
        assertEquals("demo.HelloKt", MainClassName.forUnit("demo", "hello"))
        assertEquals("HelloKt", MainClassName.forUnit(null, "hello"))
        assertEquals("demo.Vitrni_kloboukKt", MainClassName.forUnit("demo", "vitrni_klobouk"))
    }
}
