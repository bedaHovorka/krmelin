package krmelin.codegen

/**
 * Derives the JVM main class of an emitted file: Kotlin file facades are named
 * `<CapitalizedFileBase>Kt`, prefixed by the package when one exists
 * (`sachta demo` + `hello.krm` → `demo.HelloKt`).
 *
 * The derivation has to match kotlinc's *exactly* — we only ever name a class it already
 * emitted, so any divergence is a `ClassNotFoundException` at run time with no diagnostic.
 * [facadeOf] therefore mirrors `PackagePartClassUtils.getFilePartShortName`, which sanitizes
 * the base name before capitalizing it: `my-prog.krm` → `My_progKt`, `2fast.krm` → `_2fastKt`.
 */
object MainClassName {
    fun forUnit(packageName: String?, fileBaseName: String): String {
        val facade = facadeOf(fileBaseName)
        return if (packageName.isNullOrEmpty()) facade else "$packageName.$facade"
    }

    /**
     * The file-facade class name kotlinc generates for a file with this base name.
     *
     * Mirrors `NameUtils.getPackagePartClassNamePrefix` + `"Kt"`: sanitize every character that
     * is not a letter or an ASCII digit to `_`, then capitalize — prefixing `_` instead when the
     * first character cannot start a Java identifier. Capitalization goes through
     * [String.uppercase], not [Char.uppercaseChar], because kotlinc uppercases a one-character
     * *string* and that can expand (`ß` → `SS`).
     */
    internal fun facadeOf(fileBaseName: String): String {
        if (fileBaseName.isEmpty()) return "_Kt"
        val sanitized = buildString {
            for (c in fileBaseName) append(if (c.isLetter() || c in '0'..'9') c else '_')
        }
        val capitalized =
            if (Character.isJavaIdentifierStart(sanitized[0])) {
                sanitized[0].toString().uppercase() + sanitized.substring(1)
            } else {
                "_$sanitized"
            }
        return capitalized + "Kt"
    }

    /** Package name of a compilation unit as a dotted string, or null. */
    fun packageOf(unit: krmelin.ast.Decl.CompilationUnit): String? =
        unit.packageDecl?.name?.joinToString(".")
}
