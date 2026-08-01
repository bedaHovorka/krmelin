package krmelin.resolve

/**
 * A lexical scope: block → function → class → file → prelude (Plan.md §5).
 *
 * Scopes form a parent chain; lookup walks outward. Declaration order within one scope is
 * irrelevant — the resolver declares everything first, then binds, so forward references
 * work at any level that the grammar treats as unordered (file, class).
 */
class Scope(
    val parent: Scope? = null,
    val kind: Kind = Kind.BLOCK,
) {
    enum class Kind { PRELUDE, FILE, CLASS, FUNCTION, BLOCK }

    private val symbols = linkedMapOf<String, Symbol>()

    /**
     * Installs [symbol]; on a name collision the existing symbol is returned and the new
     * one is dropped, so the caller can report the duplicate against the first declaration.
     */
    fun declare(symbol: Symbol): Symbol? {
        val existing = symbols[symbol.name]
        if (existing != null) return existing
        symbols[symbol.name] = symbol
        return null
    }

    fun lookupLocal(name: String): Symbol? = symbols[name]

    fun lookup(name: String): Symbol? = lookupLocal(name) ?: parent?.lookup(name)

    /**
     * The outer symbol a new declaration named [name] would hide, for shadowing warnings
     * (HAV210). Only ancestors are consulted — a same-scope collision is a duplicate, not
     * shadowing.
     */
    fun shadowedBy(name: String): Symbol? = parent?.lookup(name)

    /** All names visible from here, own scope first; used by did-you-mean suggestions. */
    fun visibleNames(): Set<String> =
        symbols.keys + (parent?.visibleNames() ?: emptySet())
}
