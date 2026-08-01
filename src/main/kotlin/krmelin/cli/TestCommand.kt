package krmelin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import krmelin.codegen.KotlinBackend
import krmelin.codegen.MainClassName
import krmelin.codegen.TestDiscovery
import krmelin.codegen.TestMainEmitter
import krmelin.diag.DiagCode
import krmelin.diag.Diagnostic
import krmelin.diag.Severity
import krmelin.lexer.SourceSpan
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

/**
 * `krmelin test` (Plan.md §6, §11): discovers `*.krm` under a path, transpiles every
 * file (any diagnostic is fatal for the whole run — exit 2), generates the
 * `KrmelinTestMain` registry, compiles the lot together with the PorubaUnit runtime,
 * and runs it. The runner's 0/1 propagates straight through.
 */
class TestCommand : CliktCommand(
    name = "test",
    help = "Run @Sichta tests discovered in .krm source files. " +
        "Exit codes: 0 all passed, 1 failure/error, 2 test sources failed to compile.",
) {
    val path: String? by argument(help = "Path to search for tests (default: tests/).").optional()
    val filter: String? by option("--filter", help = "Glob pattern to filter test names.")
    val verbose: Boolean by option("-v", "--verbose", help = "Print each test as it runs.").flag()

    override fun run() {
        val target = File(path ?: "tests/")
        val files = collectTestFiles(target)
        if (files.isEmpty()) {
            echo("oznam: zadne .krm subory v '${target.path}'")
            return
        }

        val successes = transpileAll(files)
        checkFacadeClashes(files, successes)
        runRegistry(files, successes)
    }

    /** Every `.krm` under [target] in lexicographic path order — the §6 deterministic run order. */
    private fun collectTestFiles(target: File): List<File> {
        if (!target.exists()) {
            echo("hawaryja: cesta '${target.path}' neexistuje", err = true)
            throw ProgramResult(2)
        }
        if (target.isFile) {
            if (target.extension != "krm") {
                echo(
                    "hawaryja: '${target.path}' neni .krm subor — test umie enem .krm (abo adresar)",
                    err = true,
                )
                throw ProgramResult(2)
            }
            return listOf(target)
        }
        return target.walkTopDown()
            .filter { it.isFile && it.extension == "krm" }
            .sortedBy { it.path }
            .toList()
    }

    /**
     * Transpiles [files] and returns their successes. All-or-nothing: the registry links
     * every file into one compile set, so one broken file means there is nothing honest
     * to run — every diagnostic renders, then the command dies with §6's exit 2.
     */
    private fun transpileAll(files: List<File>): Map<File, CompilePipeline.Outcome.Success> {
        val successes = linkedMapOf<File, CompilePipeline.Outcome.Success>()
        var broken = false
        for (file in files) {
            when (val outcome = CompilePipeline.transpile(file.path)) {
                is CompilePipeline.Outcome.Success -> successes[file] = outcome
                is CompilePipeline.Outcome.Diagnostics -> {
                    echo(outcome.rendered, err = true)
                    broken = true
                }
                is CompilePipeline.Outcome.IoFailure -> {
                    echo(outcome.message, err = true)
                    broken = true
                }
            }
        }
        if (broken) throw ProgramResult(2)
        for ((_, outcome) in successes) {
            if (outcome.reporter.all.isNotEmpty()) echo(outcome.reporter.render(), err = true)
        }
        return successes
    }

    /**
     * Two files with the same package and the same base name emit the same Kotlin facade
     * class — kotlinc would dump a raw duplicate-class error, so the clash is named here
     * first. A root-package `KrmelinTestMain` file collides with the generated registry.
     */
    private fun checkFacadeClashes(files: List<File>, successes: Map<File, CompilePipeline.Outcome.Success>) {
        val seen = mutableMapOf<Pair<String?, String>, File>()
        for (file in files) {
            val outcome = successes[file] ?: continue
            val key = MainClassName.packageOf(outcome.unit) to MainClassName.facadeOf(file.nameWithoutExtension)
            val earlier = seen.putIfAbsent(key, file)
            if (earlier != null) {
                echo(
                    CompilePipeline.renderStandalone(
                        Diagnostic(
                            Severity.ERROR,
                            DiagCode.DUPLICATE_TEST_FACADE,
                            "subory '${earlier.path}' a '${file.path}' by oba vyrobily '${key.second}' — takhle se party nedavaj dohromady",
                            SourceSpan.NONE,
                            fix = "prejmenuj jeden subor, abo dej suborum inou 'sachta'",
                        ),
                    ),
                    err = true,
                )
                throw ProgramResult(2)
            }
        }
    }

    private fun runRegistry(files: List<File>, successes: Map<File, CompilePipeline.Outcome.Success>) {
        val workDir = Files.createTempDirectory("krmelin-test").toFile()
        try {
            val sources = mutableListOf<File>()
            val entries = mutableListOf<TestDiscovery.TestEntry>()
            files.forEachIndexed { index, file ->
                val outcome = successes.getValue(file)
                // One subdirectory per input file (indexed, so even duplicate base names
                // land apart — the facade clash for those was already reported above).
                val ktFile = File(workDir, "src/${index}/${file.nameWithoutExtension}.kt")
                ktFile.parentFile.mkdirs()
                ktFile.writeText(outcome.kt)
                sources += ktFile
                entries += TestDiscovery.discover(outcome.unit)
            }

            // The runner itself: registry + both runtimes (PorubaUnit needs Flakanci for
            // error classification), each in its own directory for the same overwrite guard.
            val registryDir = File(workDir, "gen").apply { mkdirs() }
            val registry = File(registryDir, "KrmelinTestMain.kt")
            registry.writeText(TestMainEmitter.emit(entries))
            sources += registry
            val runtimeDir = File(workDir, "runtime").apply { mkdirs() }
            sources += KotlinBackend.extractFlakanci(runtimeDir)
            sources += KotlinBackend.extractPorubaUnit(runtimeDir)

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
                throw ProgramResult(2)
            }

            // Runner argv contract (pinned by TestMainEmitterTest): [glob, "v"].
            val exitCode = ProcessBuilder(
                "java",
                "-cp",
                classesDir.absolutePath + File.pathSeparator + System.getProperty("java.class.path"),
                "KrmelinTestMainKt",
                filter ?: "",
                if (verbose) "v" else "",
            ).inheritIO().start().waitFor()
            if (exitCode != 0) throw ProgramResult(exitCode)
        } finally {
            workDir.deleteRecursively()
        }
    }
}
