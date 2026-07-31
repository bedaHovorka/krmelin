package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option

class ReplCommand : CliktCommand(
    name = "repl",
    help = "Start an interactive Krmelin REPL. Exit with Ctrl-D or :fajront.",
) {
    val load: String? by option("--load", help = "Load and evaluate a .krm file before starting the REPL.")

    override fun run() {
        echo("repl: not yet implemented")
    }
}
