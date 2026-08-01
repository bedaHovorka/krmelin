package krmelin.resolve

import krmelin.types.KType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreludeTest {
    private val scope = Prelude.scope

    private fun typeName(name: String): Symbol.TypeName {
        val sym = scope.lookup(name)
        assertTrue(sym is Symbol.TypeName, "expected a TypeName for '$name', got $sym")
        return sym
    }

    @Test
    fun `type aliases map dialect names to Kotlin types`() {
        val expected = mapOf(
            "Dryst" to "String",
            "Cyslo" to "Int",
            "CysloDesetinne" to "Double",
            "Bul" to "Boolean",
            "Chachar" to "Char",
            "Halda" to "List",
            "Kupa" to "Map",
        )
        for ((krmelin, kotlin) in expected) {
            assertEquals(kotlin, typeName(krmelin).type.kotlinName, "alias $krmelin")
        }
    }

    @Test
    fun `generic alias arities match the Kotlin targets`() {
        assertEquals(1, typeName("Halda").typeArity)
        assertEquals(2, typeName("Kupa").typeArity)
        assertEquals(0, typeName("Dryst").typeArity)
    }

    @Test
    fun `pravit and zarvat take exactly one argument`() {
        for (name in listOf("pravit", "zarvat")) {
            val fn = scope.lookup(name)
            assertTrue(fn is Symbol.Function, "expected a Function for '$name', got $fn")
            assertEquals(1, fn.params.size)
        }
    }

    @Test
    fun `naDryst is available on every prelude type`() {
        for (name in listOf("Dryst", "Cyslo", "CysloDesetinne", "Bul", "Chachar", "Halda", "Kupa")) {
            val member = typeName(name).members["naDryst"]
            assertTrue(member is Symbol.Function, "expected naDryst on $name")
            assertEquals("String", member.returnType?.kotlinName)
        }
    }

    @Test
    fun `dylka is available on Dryst, Halda and Kupa`() {
        for (name in listOf("Dryst", "Halda", "Kupa")) {
            val member = typeName(name).members["dylka"]
            assertTrue(member is Symbol.Variable, "expected dylka on $name")
            assertEquals("Int", member.type?.kotlinName)
        }
    }

    @Test
    fun `Flakanec has zprava and odkud members and a message constructor`() {
        val flakanec = typeName("Flakanec")
        assertEquals(KType.Kind.EXCEPTION, flakanec.type.kind)
        assertNotNull(flakanec.members["zprava"])
        assertNotNull(flakanec.members["odkud"])
        val ctor = flakanec.constructor
        assertNotNull(ctor)
        assertEquals(listOf("String"), ctor.params.map { it.type?.kotlinName })
    }

    @Test
    fun `the exception hierarchy is rooted at Flakanec`() {
        for (name in listOf("ChujovyFlakanec", "MimoBarak", "DelenoNulou", "ZlyDryst")) {
            val child = typeName(name)
            assertEquals("Flakanec", child.type.parent?.kotlinName, "parent of $name")
        }
    }

    @Test
    fun `prelude spans are NONE so renderer never quotes fake source`() {
        assertEquals(krmelin.lexer.SourceSpan.NONE, typeName("Dryst").span)
    }
}
