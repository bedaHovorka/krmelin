package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class RunCommand : CliktCommand(
    name = "run",
    help = "Compile and execute a .krm source file.",
) {
    val file: String by argument(help = "Path to the .krm source file.")
    val args: String? by option("--args", help = "Arguments to pass to the program.")
    val keep: Boolean by option("--keep", help = "Keep intermediate .kt files after running.").flag()

    override fun run() {
        echo("run: not yet implemented (file=$file)")
    }
}
