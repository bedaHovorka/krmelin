package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class FmtCommand : CliktCommand(
    name = "fmt",
    help = "Format a .krm source file or directory canonically.",
) {
    val target: String by argument(help = "Path to the .krm file or directory to format.")
    val check: Boolean by option("--check", help = "Exit with code 1 if formatting is needed (no writes).").flag()

    override fun run() {
        echo("fmt: not yet implemented (target=$target)")
    }
}
