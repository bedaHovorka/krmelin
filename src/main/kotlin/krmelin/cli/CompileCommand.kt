package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import krmelin.codegen.KotlinBackend
import krmelin.codegen.MainClassName
import krmelin.diag.DiagCode
import krmelin.diag.Diagnostic
import krmelin.diag.Severity
import krmelin.lexer.SourceSpan
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class CompileCommand : CliktCommand(
    name = "compile",
    help = "Transpile a .krm source file to Kotlin. Exit codes: 0 ok, 1 compile error, 2 usage/IO.",
) {
    val file: String by argument(help = "Path to the .krm source file.")
    val out: String? by option("-o", "--out", help = "Output path for the generated .kt file.")
    val jar: Boolean by option("--jar", help = "Also compile the emitted Kotlin to a runnable jar (kotlin-stdlib is NOT bundled).").flag()
    val emitOnly: Boolean by option("--emit-only", help = "Only emit Kotlin source; do not invoke the Kotlin backend.").flag()

    override fun run() {
        if (jar && emitOnly) {
            echo("hawaryja: --jar a --emit-only se vylucuji — jar znamena sestaveni, emit-only znamena jen zdroj", err = true)
            throw ProgramResult(2)
        }

        when (val outcome = CompilePipeline.transpile(file)) {
            is CompilePipeline.Outcome.IoFailure -> {
                echo(outcome.message, err = true)
                throw ProgramResult(2)
            }
            is CompilePipeline.Outcome.Diagnostics -> {
                echo(outcome.rendered, err = true)
                throw ProgramResult(1)
            }
            is CompilePipeline.Outcome.Success -> {
                echoWarnings(outcome)
                val ktFile = File(out ?: file.withKtExtension())
                ktFile.writeText(outcome.kt)
                if (jar) buildJar(outcome, ktFile)
            }
        }
    }

    private fun echoWarnings(outcome: CompilePipeline.Outcome.Success) {
        if (outcome.reporter.all.isNotEmpty()) echo(outcome.reporter.render(), err = true)
    }

    private fun buildJar(outcome: CompilePipeline.Outcome.Success, ktFile: File) {
        val workDir = Files.createTempDirectory("krmelin-jar").toFile()
        try {
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
            val jarFile = File(ktFile.absolutePath.removeSuffix(".kt") + ".jar")
            writeJar(jarFile, classesDir, mainClass)
            echo("jar: ${jarFile.path}")
        } finally {
            workDir.deleteRecursively()
        }
    }
}

internal fun String.withKtExtension(): String = removeSuffix(".krm") + ".kt"

internal fun writeJar(target: File, classesDir: File, mainClass: String) {
    val manifest = Manifest().apply {
        mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass
    }
    JarOutputStream(target.outputStream(), manifest).use { jar ->
        classesDir.walkTopDown().filter { it.isFile }.forEach { f ->
            jar.putNextEntry(JarEntry(f.relativeTo(classesDir).invariantSeparatorsPath))
            f.inputStream().use { it.copyTo(jar) }
            jar.closeEntry()
        }
    }
}
