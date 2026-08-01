package krmelin.cli

import krmelin.ast.Decl
import krmelin.codegen.KotlinEmitter
import krmelin.diag.Diagnostic
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import krmelin.lower.Lowering
import krmelin.parser.Parser
import krmelin.resolve.Resolver
import krmelin.types.TypeChecker
import java.io.File
import java.io.IOException

/**
 * The shared front of `krmelin compile` and `krmelin run`: file in, full pipeline
 * (lex → parse → resolve → typecheck → lower → emit) or a typed failure. Commands map
 * outcomes to the §11 exit codes and do all user-facing echoing themselves.
 */
internal object CompilePipeline {

    sealed interface Outcome {
        /** Emitted Kotlin plus what the commands need downstream. */
        data class Success(
            val unit: Decl.CompilationUnit,
            val kt: String,
            val usesFlakanci: Boolean,
            val usesPorubaUnit: Boolean,
            val reporter: DiagnosticReporter,
        ) : Outcome

        /** The front end reported errors; [rendered] is ready for stderr. */
        data class Diagnostics(val rendered: String, val reporter: DiagnosticReporter) : Outcome

        /** The input file could not be read; [message] is ready for stderr. */
        data class IoFailure(val message: String) : Outcome
    }

    fun transpile(path: String): Outcome {
        val text = try {
            File(path).readText()
        } catch (e: IOException) {
            return Outcome.IoFailure("hawaryja: subor '$path' sa neda cist (${e.message})")
        }

        val reporter = DiagnosticReporter()
        reporter.registerSource(path, text)
        val tokens = Lexer(text, path, reporter).lex()
        val unit = Parser(tokens, path, reporter).parse()
        if (reporter.hasErrors) return Outcome.Diagnostics(reporter.render(), reporter)

        val resolution = Resolver(reporter).resolve(unit)
        TypeChecker(reporter, resolution).check(unit)
        if (reporter.hasErrors) return Outcome.Diagnostics(reporter.render(), reporter)

        val lowered = Lowering(resolution).lower(unit)
        return Outcome.Success(
            unit, KotlinEmitter(resolution).emit(lowered),
            lowered.usesFlakanci, lowered.usesPorubaUnit, reporter,
        )
    }

    /** Renders one backend-phase diagnostic on its own (header only, no source lines). */
    fun renderStandalone(diag: Diagnostic): String =
        DiagnosticReporter().also { it.report(diag) }.render()

    /**
     * The `java` binary used to launch compiled programs: the one running this process, so a
     * program runs on the same JVM as its compiler even when no `java` is on PATH. Falls back
     * to a bare `java` if `java.home` is unset or does not hold the expected layout.
     */
    fun javaExecutable(): String {
        val home = System.getProperty("java.home") ?: return "java"
        val name = if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "java.exe" else "java"
        val candidate = File(File(home, "bin"), name)
        return if (candidate.canExecute()) candidate.absolutePath else "java"
    }
}
