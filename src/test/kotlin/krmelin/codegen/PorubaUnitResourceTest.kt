package krmelin.codegen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import krmelin.resolve.Prelude

/**
 * PorubaUnit (Plan.md §6) ships as Kotlin *source* inside the compiler jar, self-contained
 * so its console voice stays fully controlled — no JUnit anywhere at run time. This pins
 * the resource's shape so accidental edits are loud.
 */
class PorubaUnitResourceTest {

    private val resource: String by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("runtime/PorubaUnit.kt")
        assertNotNull(stream, "runtime/PorubaUnit.kt must be packaged as a classpath resource")
        stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `resource lives in the krmelin runtime package`() {
        assertTrue(resource.contains("package ${KotlinPrelude.RUNTIME_PACKAGE}"), resource)
    }

    @Test
    fun `resource declares every assertion robota`() {
        for (name in Prelude.ASSERTION_NAMES) {
            assertTrue(Regex("fun \\b$name\\b").containsMatchIn(resource), "missing $name in:\n$resource")
        }
    }

    @Test
    fun `resource declares the registry record and the runner`() {
        assertTrue(Regex("class \\bSichta\\b").containsMatchIn(resource), resource)
        assertTrue(Regex("fun \\bspustSichty\\b").containsMatchIn(resource), resource)
    }

    @Test
    fun `resource never mentions JUnit`() {
        assertFalse(resource.contains("org.junit"), resource)
        assertFalse(Regex("\\bjunit\\b", RegexOption.IGNORE_CASE).containsMatchIn(resource), resource)
    }
}
