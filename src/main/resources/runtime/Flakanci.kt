package krmelin.runtime

/**
 * The Flakanci exception hierarchy (Plan.md §7) — Krmelin's take on exceptions, on the
 * pub-brawl metaphor: someone dishes one out (`rozdava`), you get one (`dostanes`),
 * a brawl breaks out (`bitka`), and the shift ends regardless (`fajront`).
 *
 * This file is Kotlin *source* shipped inside the compiler jar. The compiler extracts
 * it next to the emitted `.kt` whenever the program references the hierarchy, so the
 * embedded kotlinc compiles it together with the user's code. It is never part of the
 * compiler itself.
 *
 * `Flakanec` = a whack, a slap. It is `open`, not abstract: user code constructs it
 * directly (`dostanes Flakanec("...")`).
 */
open class Flakanec(
    val zprava: String,
    cause: Throwable? = null,
) : RuntimeException(zprava, cause) {
    /**
     * Where the whack landed, e.g. `Hello.kt:12`, or null when unknown. M4 derives it
     * from the JVM stack trace; mapping back to `.krm` source spans is a later refinement
     * (Plan.md §7 treats .odkud as "when available").
     */
    open val odkud: String?
        get() = stackTrace.firstOrNull()?.let { "${it.fileName}:${it.lineNumber}" }
}

/** The null whack — `chujovy` also reads as "botched/worthless", both meanings intended. */
class ChujovyFlakanec(zprava: String) : Flakanec(zprava)

/** "Outside the building" — index out of bounds. */
class MimoBarak(zprava: String) : Flakanec(zprava)

/** Division by zero. */
class DelenoNulou(zprava: String) : Flakanec(zprava)

/** Bad string — number parse and conversion errors. */
class ZlyDryst(zprava: String) : Flakanec(zprava)
