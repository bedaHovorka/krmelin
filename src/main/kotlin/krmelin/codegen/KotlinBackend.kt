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
        val target = File(destDir, "Flakanci.kt")
        target.writeText(flakanciSource())
        return target
    }

    /** The Flakanci runtime source as text, for callers that place it themselves. */
    fun flakanciSource(): String =
        javaClass.classLoader.getResourceAsStream(FLAKANCI_RESOURCE)
            ?.use { it.readBytes().decodeToString() }
            ?: error("$FLAKANCI_RESOURCE missing from the jar — compiler packaging is broken")

    /**
     * The jar holding the Kotlin standard library, for bundling into a `--jar` build.
     *
     * Prefers a real `kotlin-stdlib-*.jar` on the classpath (a Gradle run has one, and it is
     * exactly the stdlib). Falls back to whatever jar `kotlin.Unit` was loaded from — our own
     * shadow fat jar when running as `java -jar`, where the stdlib is shaded in and the caller
     * filters it back out by entry name. `null` when neither is a jar (e.g. loose class dirs).
     */
    fun stdlibJar(): File? {
        val onClasspath = System.getProperty("java.class.path").orEmpty()
            .split(File.pathSeparator)
            .map(::File)
            .firstOrNull { it.isFile && STDLIB_JAR_NAME.matches(it.name) }
        if (onClasspath != null) return onClasspath
        val location = Unit::class.java.protectionDomain?.codeSource?.location ?: return null
        return runCatching { File(location.toURI()) }.getOrNull()?.takeIf { it.isFile }
    }

    private val STDLIB_JAR_NAME = Regex("""^kotlin-stdlib(-[\d.]+)?\.jar$""")
}
