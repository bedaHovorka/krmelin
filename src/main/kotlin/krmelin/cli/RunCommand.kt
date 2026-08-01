package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import krmelin.ast.Decl
import krmelin.codegen.KotlinBackend
import krmelin.codegen.KotlinPrelude
import krmelin.codegen.MainClassName
import krmelin.diag.DiagCode
import krmelin.diag.Diagnostic
import krmelin.diag.Severity
import krmelin.lexer.SourceSpan
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

class RunCommand : CliktCommand(
    name = "run",
    help = "Compile and execute a .krm source file. Exit codes: program's exit; 1 compile error; 2 usage/IO.",
) {
    val file: String by argument(help = "Path to the .krm source file.")
    val args: String? by option("--args", help = "Arguments to pass to the program (whitespace-separated).")
    val keep: Boolean by option("--keep", help = "Keep the working directory with the emitted .kt and class files.").flag()

    override fun run() {
        when (val outcome = CompilePipeline.transpile(file)) {
            is CompilePipeline.Outcome.IoFailure -> {
                echo(outcome.message, err = true)
                throw ProgramResult(2)
            }
            is CompilePipeline.Outcome.Diagnostics -> {
                echo(outcome.rendered, err = true)
                throw ProgramResult(1)
            }
            is CompilePipeline.Outcome.Success -> runProgram(outcome)
        }
    }

    private fun runProgram(outcome: CompilePipeline.Outcome.Success) {
        val hasEntryPoint = outcome.unit.declarations
            .filterIsInstance<Decl.FunDecl>()
            .any(KotlinPrelude::isEntryPoint)
        if (!hasEntryPoint) {
            echo(
                CompilePipeline.renderStandalone(
                    Diagnostic(
                        Severity.ERROR,
                        DiagCode.NO_ENTRY_POINT,
                        "subor '$file' nema zavadeci funkci",
                        SourceSpan.NONE,
                        fix = "doplň 'robota rynek() { ... }' — ta se zkompiluje jako main",
                    ),
                ),
                err = true,
            )
            throw ProgramResult(1)
        }
        if (outcome.reporter.all.isNotEmpty()) echo(outcome.reporter.render(), err = true)

        val workDir = Files.createTempDirectory("krmelin-run").toFile()
        try {
            val ktFile = File(workDir, "${File(file).nameWithoutExtension}.kt")
            ktFile.writeText(outcome.kt)
            val sources = mutableListOf(ktFile)
            if (outcome.usesFlakanci) sources += KotlinBackend.extractFlakanci(workDir)

            val classesDir = File(workDir, "classes")
            val captured = ByteArrayOutputStream()
            val code = KotlinBackend.compileToDir(sources, classesDir, PrintStream(captured))
            if (code != 0) {
                echo(captured.toString().trimEnd(), err = true)
                echo(
                    CompilePipeline.renderStandalone(
                        Diagnostic(
                            Severity.ERROR,
                            DiagCode.BACKEND_FAILED,
                            "kotlinc odmitl vygenerovany Kotlin (kod $code)",
                            SourceSpan.NONE,
                        ),
                    ),
                    err = true,
                )
                throw ProgramResult(1)
            }

            val mainClass = MainClassName.forUnit(
                MainClassName.packageOf(outcome.unit),
                File(file).nameWithoutExtension,
            )
            val programArgs = args?.split(Regex("\\s+"))?.filter { it.isNotBlank() } ?: emptyList()
            val exitCode = ProcessBuilder(
                listOf(
                    "java",
                    "-cp",
                    classesDir.absolutePath + File.pathSeparator + System.getProperty("java.class.path"),
                    mainClass,
                ) + programArgs,
            ).inheritIO().start().waitFor()

            if (keep) {
                echo("ponechano: ${workDir.path}")
            }
            if (exitCode != 0) throw ProgramResult(exitCode)
        } finally {
            if (!keep) workDir.deleteRecursively()
        }
    }
}
