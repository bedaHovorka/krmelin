package krmelin

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
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

fun main(args: Array<String>) {
    KrmelinCli()
        .subcommands(
            CompileCommand(),
            RunCommand(),
            TestCommand(),
            FmtCommand(),
            ReplCommand(),
        )
        .main(args)
}
