package krmelin.codegen

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Flakanci exception hierarchy (Plan.md §7) ships as Kotlin *source* inside the
 * compiler jar; the backend extracts it next to the emitted `.kt` whenever the program
 * touches exceptions. This test pins the resource's shape so accidental edits are loud.
 */
class FlakanciResourceTest {

    private val resource: String by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("runtime/Flakanci.kt")
        assertNotNull(stream, "runtime/Flakanci.kt must be packaged as a classpath resource")
        stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `resource lives in the krmelin runtime package`() {
        assertTrue(resource.contains("package ${KotlinPrelude.RUNTIME_PACKAGE}"), resource)
    }

    @Test
    fun `resource declares the full hierarchy`() {
        for (name in KotlinPrelude.FLAKANCI_TYPE_NAMES) {
            assertTrue(Regex("class \\b$name\\b").containsMatchIn(resource), "missing $name in:\n$resource")
        }
        assertTrue(resource.contains("RuntimeException"), resource)
        for (name in KotlinPrelude.FLAKANCI_TYPE_NAMES - "Flakanec") {
            assertTrue(Regex("class \\b$name\\b[^\\n]*Flakanec").containsMatchIn(resource), "$name must extend Flakanec")
        }
    }

    @Test
    fun `every instance exposes zprava and odkud`() {
        assertTrue(resource.contains("zprava"), resource)
        assertTrue(resource.contains("odkud"), resource)
    }

    @Test
    fun `resource is open for extension at the base`() {
        // User code constructs Flakanec directly (exceptions.krm), so it is open, not sealed.
        assertTrue(resource.contains("open class Flakanec"), resource)
    }
}
