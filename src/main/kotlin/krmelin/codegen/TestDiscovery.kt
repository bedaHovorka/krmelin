package krmelin.codegen

import krmelin.ast.Decl

/**
 * Walks a compilation unit for `@Sichta` roboty (Plan.md §6) and renders each one as a
 * package-qualified invocation that the generated `KrmelinTestMain` can call.
 *
 * Invocations qualify by *package*, never by file facade: the `<File>Kt` facade class is
 * a bytecode artifact invisible to Kotlin source in the same module (calling it fails
 * with `unresolved reference`), while a top-level function is resolvable by qualified
 * name. Two same-named tests in one package collide at kotlinc level — HAV410, exit 2.
 * Order is the declaration order inside the unit; `krmelin test` sorts the files.
 */
object TestDiscovery {

    /** One runnable sichta: its display name and the Kotlin expression that runs it. */
    data class TestEntry(
        val displayName: String,
        val invocation: String,
    )

    /** The unit's tests in declaration order. */
    fun discover(unit: Decl.CompilationUnit): List<TestEntry> {
        val pkg = MainClassName.packageOf(unit)
        val qualifier = if (pkg.isNullOrEmpty()) "" else "$pkg."
        val entries = mutableListOf<TestEntry>()
        for (decl in unit.declarations) {
            if (decl is Decl.FunDecl && decl.isTest) {
                entries += TestEntry(decl.name, "$qualifier${KotlinPrelude.escapeIdent(decl.name)}()")
            }
            if (decl is Decl.ClassDecl && decl.isParta) {
                val fqClass = "$qualifier${KotlinPrelude.escapeIdent(decl.name)}"
                for (member in decl.members) {
                    if (member is Decl.FunDecl && member.isTest) {
                        entries += TestEntry(
                            "${decl.name}.${member.name}",
                            "$fqClass().${KotlinPrelude.escapeIdent(member.name)}()",
                        )
                    }
                }
            }
        }
        return entries
    }
}
