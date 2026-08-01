package krmelin.resolve

import krmelin.lexer.SourceSpan
import krmelin.types.KType

/**
 * The root scope every file scope chains under (Plan.md §4.6, §7).
 *
 * Maps dialect type names to Kotlin types, provides the `pravit`/`zarvat` output functions,
 * the member surface the dialect promises (`naDryst`, `dylka`), and the Flakanec exception
 * hierarchy. Everything here is synthetic: spans are [SourceSpan.NONE], so a diagnostic that
 * mentions a prelude symbol never quotes fake source.
 */
object Prelude {
    private val span = SourceSpan.NONE

    private fun primitive(krmelin: String, kotlin: String) =
        KType(krmelin, kotlin, kind = KType.Kind.PRIMITIVE)

    val DRYST = primitive("Dryst", "String")
    val CYSLO = primitive("Cyslo", "Int")
    val CYSLO_DESETINNE = primitive("CysloDesetinne", "Double")
    val BUL = primitive("Bul", "Boolean")
    val CHACHAR = primitive("Chachar", "Char")
    val HALDA = KType("Halda", "List", kind = KType.Kind.DECLARED)
    val KUPA = KType("Kupa", "Map", kind = KType.Kind.DECLARED)

    val FLAKANEC = KType("Flakanec", "Flakanec", kind = KType.Kind.EXCEPTION)

    private fun fn(name: String, params: List<ParamSig>, returnType: KType?) =
        Symbol.Function(name, span, params, returnType)

    private fun prop(name: String, type: KType) =
        Symbol.Variable(name, span, isMutable = false, type = type)

    private fun naDryst() = fn("naDryst", emptyList(), DRYST)

    private fun primitiveMembers() = mapOf("naDryst" to naDryst())

    /** Alias type name with the members the dialect promises on it. */
    private fun alias(name: String, type: KType, arity: Int, members: Map<String, Symbol>) =
        Symbol.TypeName(name, span, type, typeArity = arity, members = members)

    private fun exception(name: String, parent: KType?): Symbol.TypeName {
        val type = KType(name, name, kind = KType.Kind.EXCEPTION, parent = parent)
        return Symbol.TypeName(
            name, span, type,
            members = mapOf(
                "zprava" to prop("zprava", DRYST),
                "odkud" to prop("odkud", DRYST.nullable()),
                "naDryst" to naDryst(),
            ),
            constructor = fn(name, listOf(ParamSig("zprava", DRYST)), null),
        )
    }

    val scope: Scope = Scope(kind = Scope.Kind.PRELUDE).apply {
        declare(alias("Dryst", DRYST, 0, primitiveMembers() + ("dylka" to prop("dylka", CYSLO))))
        declare(alias("Cyslo", CYSLO, 0, primitiveMembers()))
        declare(alias("CysloDesetinne", CYSLO_DESETINNE, 0, primitiveMembers()))
        declare(alias("Bul", BUL, 0, primitiveMembers()))
        declare(alias("Chachar", CHACHAR, 0, primitiveMembers()))
        declare(
            alias(
                "Halda", HALDA, 1,
                primitiveMembers() + ("dylka" to prop("dylka", CYSLO)),
            ),
        )
        declare(
            alias(
                "Kupa", KUPA, 2,
                primitiveMembers() + ("dylka" to prop("dylka", CYSLO)),
            ),
        )

        declare(fn("pravit", listOf(ParamSig("x", KType.UNKNOWN.nullable())), null))
        declare(fn("zarvat", listOf(ParamSig("x", KType.UNKNOWN.nullable())), null))

        val flakanec = exception("Flakanec", null)
        declare(flakanec)
        for (child in listOf("ChujovyFlakanec", "MimoBarak", "DelenoNulou", "ZlyDryst")) {
            declare(exception(child, flakanec.type))
        }
    }
}
