package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option

class TestCommand : CliktCommand(
    name = "test",
    help = "Run @Sichta tests discovered in .krm source files.",
) {
    val path: String? by argument(help = "Path to search for tests (default: tests/).").optional()
    val filter: String? by option("--filter", help = "Glob pattern to filter test names.")
    val verbose: Boolean by option("-v", "--verbose", help = "Print each test as it runs.").flag()

    override fun run() {
        echo("test: not yet implemented (path=${path ?: "tests/"})")
    }
}
