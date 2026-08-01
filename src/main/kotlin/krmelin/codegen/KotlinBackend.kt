package krmelin.codegen

import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.File
import java.io.PrintStream

/**
 * Runs the embedded Kotlin compiler in-process (Plan.md §5, last arrow). No `kotlinc`
 * on PATH is needed — this supersedes the §15 "detect missing kotlinc" risk note: the
 * fat jar carries the compiler, the same philosophy that keeps PorubaUnit self-contained.
 *
 * The caller's own classpath is passed to kotlinc, so kotlin-stdlib (bundled in the fat
 * jar / present on the Gradle test classpath) is always available; `-no-stdlib` stops
 * kotlinc from also probing its bundled copy.
 */
object KotlinBackend {

    private const val FLAKANCI_RESOURCE = "runtime/Flakanci.kt"

    /**
     * Compiles [sources] into .class files under [outDir]. Returns the compiler exit
     * code (`0` = OK); compiler messages go to [errOut].
     */
    fun compileToDir(sources: List<File>, outDir: File, errOut: PrintStream = System.err): Int {
        outDir.mkdirs()
        val code = K2JVMCompiler().exec(
            errOut,
            "-classpath", System.getProperty("java.class.path"),
            "-no-stdlib",
            "-d", outDir.absolutePath,
            *sources.map { it.absolutePath }.toTypedArray(),
        )
        return code.code
    }

    /**
     * Extracts the Flakanci runtime source from the jar into [destDir] so it can be
     * compiled alongside the emitted `.kt`. Returns the written file.
     */
    fun extractFlakanci(destDir: File): File {
        val stream = javaClass.classLoader.getResourceAsStream(FLAKANCI_RESOURCE)
            ?: error("$FLAKANCI_RESOURCE missing from the jar — compiler packaging is broken")
        val target = File(destDir, "Flakanci.kt")
        target.writeBytes(stream.readBytes())
        return target
    }
}
