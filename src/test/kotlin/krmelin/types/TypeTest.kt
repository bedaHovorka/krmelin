package krmelin.types

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypeTest {
    private val cyslo = KType("Cyslo", "Int", kind = KType.Kind.PRIMITIVE)
    private val desetinne = KType("CysloDesetinne", "Double", kind = KType.Kind.PRIMITIVE)
    private val dryst = KType("Dryst", "String", kind = KType.Kind.PRIMITIVE)
    private val flakanec = KType("Flakanec", "Flakanec", kind = KType.Kind.EXCEPTION)
    private val chujovy = KType(
        "ChujovyFlakanec", "ChujovyFlakanec",
        kind = KType.Kind.EXCEPTION, parent = flakanec,
    )

    @Test
    fun `a type is assignable to itself`() {
        assertTrue(cyslo.isAssignableTo(cyslo))
    }

    @Test
    fun `distinct primitives are not assignable`() {
        assertFalse(cyslo.isAssignableTo(dryst))
        assertFalse(cyslo.isAssignableTo(desetinne))
    }

    @Test
    fun `non-nullable is assignable to nullable of the same type`() {
        assertTrue(cyslo.isAssignableTo(cyslo.copy(nullable = true)))
    }

    @Test
    fun `nullable is not assignable to non-nullable`() {
        assertFalse(cyslo.copy(nullable = true).isAssignableTo(cyslo))
    }

    @Test
    fun `UNKNOWN is assignable in both directions`() {
        assertTrue(KType.UNKNOWN.isAssignableTo(cyslo))
        assertTrue(cyslo.isAssignableTo(KType.UNKNOWN))
        assertTrue(KType.UNKNOWN.copy(nullable = true).isAssignableTo(cyslo))
    }

    @Test
    fun `NULA is assignable only to nullable targets`() {
        assertTrue(KType.NULA.isAssignableTo(dryst.copy(nullable = true)))
        assertFalse(KType.NULA.isAssignableTo(dryst))
        assertTrue(KType.NULA.isAssignableTo(KType.UNKNOWN))
    }

    @Test
    fun `an exception subtype is assignable to its ancestor`() {
        assertTrue(chujovy.isAssignableTo(flakanec))
        assertFalse(flakanec.isAssignableTo(chujovy))
    }

    @Test
    fun `nullable subtype is not assignable to non-nullable ancestor`() {
        assertFalse(chujovy.copy(nullable = true).isAssignableTo(flakanec))
    }

    @Test
    fun `generic arity must match`() {
        val haldaCysel = KType("Halda", "List", typeArgs = listOf(cyslo))
        val haldaBare = KType("Halda", "List")
        assertFalse(haldaCysel.isAssignableTo(haldaBare))
        assertTrue(haldaCysel.isAssignableTo(haldaCysel.copy(nullable = true)))
    }

    @Test
    fun `nullable() flips the nullability flag`() {
        assertTrue(cyslo.nullable().nullable)
        assertFalse(cyslo.nullable().copy(nullable = false).nullable)
    }

    @Test
    fun `NIC is the unit type and only fits itself`() {
        assertTrue(KType.NIC.isAssignableTo(KType.NIC))
        assertFalse(KType.NIC.isAssignableTo(cyslo))
        assertFalse(cyslo.isAssignableTo(KType.NIC))
    }
}
