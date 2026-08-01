package krmelin.codegen

/**
 * Derives the JVM main class of an emitted file: Kotlin file facades are named
 * `<CapitalizedFileBase>Kt`, prefixed by the package when one exists
 * (`sachta demo` + `hello.krm` → `demo.HelloKt`).
 */
object MainClassName {
    fun forUnit(packageName: String?, fileBaseName: String): String {
        // uppercaseChar() is locale-independent (Unicode data, not the default locale),
        // so the facade name we derive matches kotlinc's regardless of the JVM locale.
        val facade = fileBaseName.replaceFirstChar { it.uppercaseChar() } + "Kt"
        return if (packageName.isNullOrEmpty()) facade else "$packageName.$facade"
    }

    /** Package name of a compilation unit as a dotted string, or null. */
    fun packageOf(unit: krmelin.ast.Decl.CompilationUnit): String? =
        unit.packageDecl?.name?.joinToString(".")
}
