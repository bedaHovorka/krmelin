package krmelin.codegen

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Locks the emission-time name mappings in [KotlinPrelude]. Type aliases (Dryst→String
 * etc.) deliberately live elsewhere — in `KType.kotlinName` / `resolve/Prelude.kt` —
 * so this object holds only the renames that have no KType home (Plan.md §4.6).
 */
class KotlinPreludeTest {

    @Test
    fun `pravit and zarvat map to print and println`() {
        assertEquals(mapOf("pravit" to "print", "zarvat" to "println"), KotlinPrelude.PRINT_FUNCTION_NAMES)
    }

    @Test
    fun `naDryst renames to toString`() {
        assertEquals("naDryst", KotlinPrelude.STRINGIFY_MEMBER)
        assertEquals("toString", KotlinPrelude.TO_STRING_MEMBER)
    }

    @Test
    fun `dylka lowers to length or size`() {
        assertEquals("dylka", KotlinPrelude.LENGTH_MEMBER)
        assertEquals("length", KotlinPrelude.LENGTH_PROPERTY)
        assertEquals("size", KotlinPrelude.SIZE_PROPERTY)
    }

    @Test
    fun `rynek is the Krmelin entry point, emitted as main`() {
        assertEquals("rynek", KotlinPrelude.ENTRY_POINT_KRMELIN)
        assertEquals("main", KotlinPrelude.ENTRY_POINT_KOTLIN)
    }

    @Test
    fun `flakanci set holds exactly the five exception types`() {
        assertEquals(
            setOf("Flakanec", "ChujovyFlakanec", "MimoBarak", "DelenoNulou", "ZlyDryst"),
            KotlinPrelude.FLAKANCI_TYPE_NAMES,
        )
    }

    @Test
    fun `runtime import line is deterministic`() {
        assertEquals("krmelin.runtime", KotlinPrelude.RUNTIME_PACKAGE)
        assertEquals("import krmelin.runtime.*", KotlinPrelude.RUNTIME_IMPORT_LINE)
    }
}
