package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class CompileCommand : CliktCommand(
    name = "compile",
    help = "Transpile a .krm source file to Kotlin.",
) {
    val file: String by argument(help = "Path to the .krm source file.")
    val out: String? by option("-o", "--out", help = "Output path for the generated .kt file.")
    val jar: Boolean by option("--jar", help = "Also compile the emitted Kotlin to a runnable jar.").flag()
    val emitOnly: Boolean by option("--emit-only", help = "Only emit Kotlin source; do not invoke kotlinc.").flag()

    override fun run() {
        echo("compile: not yet implemented (file=$file)")
    }
}
