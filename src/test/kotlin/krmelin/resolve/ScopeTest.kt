package krmelin.resolve

import krmelin.lexer.SourceSpan
import krmelin.types.KType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScopeTest {
    private val cyslo = KType("Cyslo", "Int", kind = KType.Kind.PRIMITIVE)
    private val span = SourceSpan.point("test.krm", 1, 1)

    private fun variable(name: String, isMutable: Boolean = false) =
        Symbol.Variable(name, span, isMutable = isMutable, type = cyslo)

    @Test
    fun `declare then lookupLocal finds the symbol`() {
        val scope = Scope()
        val sym = variable("hodiny")
        assertNull(scope.declare(sym))
        assertSame(sym, scope.lookupLocal("hodiny"))
    }

    @Test
    fun `lookup walks the parent chain`() {
        val outer = Scope()
        val inner = Scope(parent = outer)
        val sym = variable("hodiny")
        outer.declare(sym)
        assertSame(sym, inner.lookup("hodiny"))
        assertNull(inner.lookupLocal("hodiny"))
    }

    @Test
    fun `declare returns the existing symbol on a duplicate`() {
        val scope = Scope()
        val first = variable("hodiny")
        val second = variable("hodiny")
        scope.declare(first)
        assertSame(first, scope.declare(second))
        assertSame(first, scope.lookupLocal("hodiny"))
    }

    @Test
    fun `shadowing finds the outer symbol an inner declaration hides`() {
        val outer = Scope()
        val inner = Scope(parent = outer)
        val sym = variable("hodiny")
        outer.declare(sym)
        assertSame(sym, inner.shadowedBy("hodiny"))
        assertNull(inner.shadowedBy("ostatni"))
    }

    @Test
    fun `visibleNames contains own and ancestor names`() {
        val outer = Scope()
        outer.declare(variable("rynek"))
        val inner = Scope(parent = outer)
        inner.declare(variable("hodiny"))
        assertEquals(setOf("rynek", "hodiny"), inner.visibleNames())
    }
}
