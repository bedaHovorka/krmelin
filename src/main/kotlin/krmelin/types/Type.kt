package krmelin.types

/**
 * A semantic type: what the checker knows about a value, distinct from [krmelin.ast.TypeNode]
 * which is merely how the type was written in source.
 *
 * The model is deliberately minimal (Plan.md §5): primitives, declared types, a nullability
 * flag, and exception ancestry. There is no variance and no generic inference beyond
 * pass-through. [UNKNOWN] is the escape hatch for everything the checker intentionally defers
 * to `kotlinc` (lambdas, overloads, generic inference); it is assignable in both directions
 * so that unsupported constructs never produce false-positive diagnostics.
 */
data class KType(
    /** Krmelin surface name (e.g. `Cyslo`), used in diagnostic messages. */
    val name: String,
    /** The Kotlin type it maps to (e.g. `Int`). */
    val kotlinName: String,
    val nullable: Boolean = false,
    val typeArgs: List<KType> = emptyList(),
    val kind: Kind = Kind.DECLARED,
    /** Direct ancestor for exception subtyping (`bitka` matching). */
    val parent: KType? = null,
    /**
     * Name of the user `tryda`/`zapisnik` that declared this type; `null` for prelude types.
     * Identity compares this alongside [kotlinName], so a user class named `String` stays
     * distinct from the `Dryst` alias even though both emit Kotlin `String`.
     */
    val declId: String? = null,
) {
    enum class Kind {
        /** Prelude primitive (`Cyslo`, `Dryst`, `Bul`, …). */
        PRIMITIVE,

        /** A user-declared or prelude class type. */
        DECLARED,

        /** `Flakanec` and its descendants. */
        EXCEPTION,

        /** The type of the `chuj`/`nic` literal — assignable to anything nullable. */
        NULA,

        /** Type not known to the checker; compatible with everything. */
        UNKNOWN,
    }

    fun nullable(nullable: Boolean = true): KType = copy(nullable = nullable)

    /**
     * The type as a Krmelin user wrote it — type arguments and `?` included.
     *
     * Diagnostics must use this rather than [name]: two types that differ only in nullability
     * or type arguments share a [name], so a message built from [name] reads "Dryst is not
     * Dryst" and tells the reader nothing.
     */
    val display: String
        get() = buildString {
            append(name)
            if (typeArgs.isNotEmpty()) typeArgs.joinTo(this, ", ", "<", ">") { it.display }
            if (nullable && kind != Kind.NULA) append('?')
        }

    fun isAssignableTo(target: KType): Boolean = when {
        kind == Kind.UNKNOWN || target.kind == Kind.UNKNOWN -> true
        kind == Kind.NULA -> target.nullable
        isSameTypeAs(target) -> matchesShape(target) && nullabilityOk(target)
        isSubtypeOf(target) -> nullabilityOk(target)
        else -> false
    }

    /** Same emitted Kotlin type *and* same declaration — see [declId]. */
    private fun isSameTypeAs(target: KType): Boolean =
        kotlinName == target.kotlinName && declId == target.declId

    /** Generics are compared by arity only; element-type variance is left to kotlinc. */
    private fun matchesShape(target: KType): Boolean = typeArgs.size == target.typeArgs.size

    private fun nullabilityOk(target: KType): Boolean = !nullable || target.nullable

    private fun isSubtypeOf(target: KType): Boolean =
        generateSequence(parent) { it.parent }.any { it.kotlinName == target.kotlinName }

    companion object {
        /** Unknown/unsupported type — the permissive default. */
        val UNKNOWN = KType("<neznamy>", "<unknown>", kind = Kind.UNKNOWN)

        /** The `chuj`/`nic` literal type. */
        val NULA = KType("chuj", "Nothing", nullable = true, kind = Kind.NULA)

        /** The unit type of functions without a declared return type. Internal only. */
        val NIC = KType("Nic", "Unit", kind = Kind.PRIMITIVE)
    }
}
