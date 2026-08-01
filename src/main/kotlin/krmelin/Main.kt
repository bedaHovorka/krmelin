package krmelin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import krmelin.cli.CompileCommand
import krmelin.cli.FmtCommand
import krmelin.cli.ReplCommand
import krmelin.cli.RunCommand
import krmelin.cli.TestCommand

class KrmelinCli : CliktCommand(
    name = "krmelin",
    help = "Krmelin — Kotlin, po našymu. A satirical transpiler for the Ostrava dialect.",
) {
    override fun run() = Unit
}

/**
 * The assembled command tree. Built here rather than inline in [main] so tests can drive
 * the root command — `--version` and `--help` live on it, not on any subcommand.
 */
fun krmelinCli(): CliktCommand =
    KrmelinCli()
        .versionOption(BuildInfo.VERSION)
        .subcommands(
            CompileCommand(),
            RunCommand(),
            TestCommand(),
            FmtCommand(),
            ReplCommand(),
        )

fun main(args: Array<String>) {
    krmelinCli().main(args)
}
