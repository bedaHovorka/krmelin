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
    val jar: Boolean by option("--jar", help = "Also compile the emitted Kotlin to a self-contained runnable jar (kotlin-stdlib bundled).").flag()
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
                // The emitted .kt carries `import krmelin.runtime.*`, so without the runtime
                // source beside it the primary output of the primary command cannot be compiled
                // by hand. `--jar` builds it into the jar instead and needs no stray copy.
                if (!jar) for (name in runtimeFileNames(outcome)) writeRuntimeBeside(ktFile, name)
                if (jar) buildJar(outcome, ktFile)
            }
        }
    }

    private fun echoWarnings(outcome: CompilePipeline.Outcome.Success) {
        if (outcome.reporter.all.isNotEmpty()) echo(outcome.reporter.render(), err = true)
    }

    /**
     * Writes the runtime source named [fileName] (`Flakanci.kt` / `PorubaUnit.kt`) next to
     * the emitted `.kt` so the pair compiles standalone.
     *
     * Never overwrites: an existing file with different bytes is reported and left alone —
     * it may well be the user's own program, e.g. when compiling `Flakanci.krm`.
     */
    private fun writeRuntimeBeside(ktFile: File, fileName: String) {
        val dir = ktFile.absoluteFile.parentFile ?: File(".")
        val target = File(dir, fileName)
        val runtime = runtimeSource(fileName)
        if (target.exists() && target.readText() != runtime) {
            echo(
                CompilePipeline.renderStandalone(
                    Diagnostic(
                        Severity.WARNING,
                        DiagCode.RUNTIME_NOT_WRITTEN,
                        "'${target.path}' uz existuje a je iny — runtime $fileName sem nezapisuju",
                        SourceSpan.NONE,
                        fix = "prelozte s '-o' do jineho adresare, abo si ten subor odloz stranou",
                    ),
                ),
                err = true,
            )
            return
        }
        target.writeText(runtime)
        echo("runtime: ${target.path}")
    }

    private fun buildJar(outcome: CompilePipeline.Outcome.Success, ktFile: File) {
        // `--jar` advertises a runnable jar; without an entry point the manifest's
        // Main-Class resolves to a file facade with no main(), so java -jar would
        // fail with a confusing JVM error. Fail early with HAV401 instead, like `run`.
        val hasEntryPoint = outcome.unit.declarations
            .filterIsInstance<Decl.FunDecl>()
            .any(KotlinPrelude::isEntryPoint)
        if (!hasEntryPoint) {
            echo(
                CompilePipeline.renderStandalone(
                    Diagnostic(
                        Severity.ERROR,
                        DiagCode.NO_ENTRY_POINT,
                        "subor '$file' nema zavadeci funkci — jar se neda spustit",
                        SourceSpan.NONE,
                        fix = "doplň 'robota rynek() { ... }', aby mel jar co spustit",
                    ),
                ),
                err = true,
            )
            throw ProgramResult(1)
        }

        val workDir = Files.createTempDirectory("krmelin-jar").toFile()
        try {
            val sources = mutableListOf(ktFile)
            // Subdirectory, so a `-o` target that happens to be named Flakanci.kt is not the
            // same path the runtime extraction writes to. See RunCommand for the same guard.
            if (outcome.usesFlakanci || outcome.usesPorubaUnit) {
                val runtimeDir = File(workDir, "runtime").apply { mkdirs() }
                sources += KotlinBackend.extractFlakanci(runtimeDir)
                if (outcome.usesPorubaUnit) sources += KotlinBackend.extractPorubaUnit(runtimeDir)
            }

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

            // From ktFile, not from the .krm source: kotlinc derives the facade class from the
            // file it was handed, so with `-o jine.kt` the .krm name names a class that is not
            // in the jar.
            val mainClass = MainClassName.forUnit(
                MainClassName.packageOf(outcome.unit),
                ktFile.nameWithoutExtension,
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

/**
 * The runtime sources the emitted program needs, in write order. PorubaUnit always drags
 * Flakanci along — it classifies `Flakanec` throwables for error reporting, so its source
 * does not compile alone.
 */
internal fun runtimeFileNames(outcome: CompilePipeline.Outcome.Success): List<String> = buildList {
    if (outcome.usesFlakanci || outcome.usesPorubaUnit) add("Flakanci.kt")
    if (outcome.usesPorubaUnit) add("PorubaUnit.kt")
}

/** Runtime source text by shipped file name. */
internal fun runtimeSource(fileName: String): String = when (fileName) {
    "Flakanci.kt" -> KotlinBackend.flakanciSource()
    "PorubaUnit.kt" -> KotlinBackend.porubaUnitSource()
    else -> error("no runtime source named $fileName")
}

/**
 * Packages [classesDir] into a runnable jar.
 *
 * kotlin-stdlib is bundled, because `--jar` advertises a jar you can `java -jar` and even a
 * hello-world pulls in `kotlin.jvm.internal.Intrinsics` and `kotlin.io.ConsoleKt`. Without it the
 * command reported success for a jar that died with `NoClassDefFoundError`.
 */
internal fun writeJar(target: File, classesDir: File, mainClass: String) {
    val manifest = Manifest().apply {
        mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainAttributes[Attributes.Name.MAIN_CLASS] = mainClass
    }
    // JarOutputStream writes META-INF/MANIFEST.MF itself; a second one is a duplicate-entry
    // ZipException, as is any name repeated between the classes and the stdlib.
    val seen = mutableSetOf("META-INF/MANIFEST.MF")
    JarOutputStream(target.outputStream(), manifest).use { jar ->
        classesDir.walkTopDown().filter { it.isFile }.forEach { f ->
            val name = f.relativeTo(classesDir).invariantSeparatorsPath
            if (!seen.add(name)) return@forEach
            jar.putNextEntry(JarEntry(name))
            f.inputStream().use { it.copyTo(jar) }
            jar.closeEntry()
        }
        KotlinBackend.stdlibJar()?.let { copyStdlibEntries(it, jar, seen) }
    }
}

/** Copies the runtime half of the Kotlin standard library out of [source] into [jar]. */
private fun copyStdlibEntries(source: File, jar: JarOutputStream, seen: MutableSet<String>) {
    java.util.zip.ZipFile(source).use { zip ->
        for (entry in zip.entries()) {
            val name = entry.name
            val wanted = name.startsWith("kotlin/") ||
                (name.startsWith("META-INF/kotlin") && name.endsWith(".kotlin_module"))
            // kotlin-compiler-embeddable drags kotlin-reflect in behind the stdlib when the
            // source is our own fat jar; nothing a generated program does needs it.
            val bulk = name.startsWith("kotlin/reflect/jvm/internal/") || name.startsWith("kotlin/script/")
            if (entry.isDirectory || !wanted || bulk || !seen.add(name)) continue
            jar.putNextEntry(JarEntry(name))
            zip.getInputStream(entry).use { it.copyTo(jar) }
            jar.closeEntry()
        }
    }
}
