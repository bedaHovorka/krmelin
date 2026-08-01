package krmelin.codegen

/**
 * Derives the JVM main class of an emitted file: Kotlin file facades are named
 * `<CapitalizedFileBase>Kt`, prefixed by the package when one exists
 * (`sachta demo` + `hello.krm` → `demo.HelloKt`).
 */
object MainClassName {
    fun forUnit(packageName: String?, fileBaseName: String): String {
        val facade = fileBaseName.replaceFirstChar { it.uppercase() } + "Kt"
        return if (packageName.isNullOrEmpty()) facade else "$packageName.$facade"
    }

    /** Package name of a compilation unit as a dotted string, or null. */
    fun packageOf(unit: krmelin.ast.Decl.CompilationUnit): String? =
        unit.packageDecl?.name?.joinToString(".")
}
