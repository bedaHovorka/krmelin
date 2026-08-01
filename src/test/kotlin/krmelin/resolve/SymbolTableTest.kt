package krmelin.resolve

import krmelin.lexer.SourceSpan
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SymbolTableTest {
    private val span = SourceSpan.point("test.krm", 1, 1)

    private fun scopeWith(vararg names: String): Scope {
        val scope = Scope()
        names.forEach { scope.declare(Symbol.Variable(it, span, isMutable = false)) }
        return scope
    }

    @Test
    fun `newFileScope is chained under the prelude`() {
        val prelude = scopeWith("zarvat")
        val table = SymbolTable(prelude)
        val file = table.newFileScope()
        assertEquals(Scope.Kind.FILE, file.kind)
        assertSame(prelude, file.parent)
    }

    @Test
    fun `suggest finds a close name across the scope chain`() {
        val table = SymbolTable(Scope(kind = Scope.Kind.PRELUDE))
        val scope = scopeWith("hodinySpanku", "odrubano")
        assertEquals("hodinySpanku", table.suggest("hodinyspanku", scope))
    }

    @Test
    fun `suggest returns null when nothing is close enough`() {
        val table = SymbolTable(Scope(kind = Scope.Kind.PRELUDE))
        val scope = scopeWith("hodinySpanku", "odrubano")
        assertNull(table.suggest("kalendar", scope))
    }

    @Test
    fun `suggest does not offer the exact match`() {
        val table = SymbolTable(Scope(kind = Scope.Kind.PRELUDE))
        val scope = scopeWith("hodinySpanku")
        assertNull(table.suggest("hodinySpanku", scope))
    }
}
