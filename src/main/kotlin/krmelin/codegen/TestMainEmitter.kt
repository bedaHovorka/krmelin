package krmelin.codegen

import krmelin.codegen.TestDiscovery.TestEntry

/**
 * Renders the generated `KrmelinTestMain.kt` registry (Plan.md §6 backend mapping):
 * a `main` that hands the discovered sichty — in file order, then declaration order —
 * to PorubaUnit's runner and propagates its exit code. Deterministic, no timestamps.
 *
 * Runner argv contract (also pinned by TestMainEmitterTest):
 * `args[0]` = a `--filter` glob or empty, `args[1]` = `"v"` when verbose.
 */
object TestMainEmitter {

    fun emit(entries: List<TestEntry>): String = buildString {
        appendLine("import krmelin.runtime.Sichta")
        appendLine("import krmelin.runtime.spustSichty")
        appendLine()
        appendLine("fun main(args: Array<String>) {")
        if (entries.isEmpty()) {
            // Typed empty list: kotlinc refuses to infer the element type of bare listOf().
            appendLine("    val sichty = listOf<Sichta>()")
        } else {
            appendLine("    val sichty = listOf(")
            for (entry in entries) {
                appendLine("        Sichta(\"${entry.displayName}\") { ${entry.invocation} },")
            }
            appendLine("    )")
        }
        appendLine("    kotlin.system.exitProcess(")
        appendLine("        spustSichty(")
        appendLine("            sichty,")
        appendLine("            filter = args.getOrNull(0)?.takeIf { it.isNotEmpty() },")
        appendLine("            verbose = args.getOrNull(1) == \"v\",")
        appendLine("        ),")
        appendLine("    )")
        appendLine("}")
    }
}
