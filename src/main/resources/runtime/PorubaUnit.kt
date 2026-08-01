/**
 * PorubaUnit — Krmelin's self-contained test runtime (Plan.md §6). Named after Poruba,
 * the big Ostrava district.
 *
 * This is Kotlin *source* shipped inside the compiler jar as the classpath resource
 * `runtime/PorubaUnit.kt`; the backend extracts and compiles it next to the emitted
 * `.kt` whenever tests run. It is never part of the compiler itself — and it depends on
 * nothing but the stdlib and Flakanci (same package), so there is no xUnit library to
 * drag in and the console voice stays fully ours.
 *
 * Console format is pinned by PorubaUnitRuntimeTest and Plan.md §6:
 *
 * ```
 * PorubaUnit — šichta začíná
 *   ✓ sedi_to                        (0 ms)
 *   ✗ spadlo_to                      (1 ms)
 *       musi_byt spadlo v test.krm:3:5
 *       čekal sem:  0
 *       dostal sem: 3
 *
 * Fajront: 1 prošla, 1 spadla, 0 chyb — za 41 ms
 * ```
 *
 * Exit code: 0 when everything passed, 1 when anything failed or errored.
 */
package krmelin.runtime

import kotlin.math.abs
import kotlin.system.measureTimeMillis

/** One discovered `@Sichta`: display name plus the invocation that runs it. */
class Sichta(val jmeno: String, val telo: () -> Unit)

/**
 * An assertion failure, carried as an exception so it unwinds straight out of the test
 * body. Deliberately NOT a [Flakanec] — a failing assertion is not a brawl thrown at
 * the user, and [ma_dostat] must never accept one as "the expected whack".
 */
internal class SichtaSpadla(
    /** Name of the robota whose assertion failed ("musi_byt"). */
    val volani: String,
    /** `.krm` call-site span injected by the emitter ("file:krm line:col"), or "" if none. */
    val odkud: String,
    /** Pre-rendered detail lines, without the leading indent. */
    val detaily: List<String>,
    /** The optional trailing message. */
    val vzkaz: String?,
) : RuntimeException()

private fun spadla(volani: String, odkud: String, detaily: List<String>, vzkaz: String?): Nothing =
    throw SichtaSpadla(volani, odkud, detaily, vzkaz)

/** Value rendering for detail lines; `chuj` is how Krmelin spells null. */
private fun render(x: Any?): String = when (x) {
    null -> "chuj"
    else -> x.toString()
}

/** "must be" — assertEquals. */
fun musi_byt(actual: Any?, expected: Any?, zprava: String? = null, odkud: String = "") {
    if (actual != expected) {
        spadla(
            "musi_byt", odkud,
            listOf("čekal sem:  ${render(expected)}", "dostal sem: ${render(actual)}"),
            zprava,
        )
    }
}

/** "must not be" — assertNotEquals. */
fun nesmi_byt(actual: Any?, expected: Any?, zprava: String? = null, odkud: String = "") {
    if (actual == expected) {
        spadla(
            "nesmi_byt", odkud,
            listOf("nemelo byt: ${render(expected)}", "dostal sem: ${render(actual)}"),
            zprava,
        )
    }
}

/** "is fine" — assertTrue. */
fun je_fajne(podminka: Boolean, zprava: String? = null, odkud: String = "") {
    if (!podminka) spadla("je_fajne", odkud, listOf("dostal sem: nyt"), zprava)
}

/** "is nope" — assertFalse. */
fun je_nyt(podminka: Boolean, zprava: String? = null, odkud: String = "") {
    if (podminka) spadla("je_nyt", odkud, listOf("dostal sem: fajne"), zprava)
}

/** "is rubbish" — assertNull; chuj is the dialect null. */
fun je_chuj(x: Any?, zprava: String? = null, odkud: String = "") {
    if (x != null) spadla("je_chuj", odkud, listOf("dostal sem: ${render(x)} (ale chuj malo byt)"), zprava)
}

/** "is not rubbish" — assertNotNull. */
fun neni_chuj(x: Any?, zprava: String? = null, odkud: String = "") {
    if (x == null) spadla("neni_chuj", odkud, listOf("dostal sem: chuj"), zprava)
}

/**
 * "should get one" — the block is expected to throw. Any throwable satisfies it except a
 * [SichtaSpadla]: framework failures bubble through so a broken test cannot hide inside
 * its own `ma_dostat`.
 */
fun ma_dostat(telo: () -> Unit, zprava: String? = null, odkud: String = "") {
    try {
        telo()
    } catch (s: SichtaSpadla) {
        throw s
    } catch (f: Throwable) {
        return
    }
    spadla("ma_dostat", odkud, listOf("dostal sem: zadny flakanec"), zprava)
}

/** "close" — approximate equality of doubles within [tolerance]. */
fun blizko(a: Double, b: Double, tolerance: Double, zprava: String? = null, odkud: String = "") {
    if (abs(a - b) > tolerance) {
        spadla(
            "blizko", odkud,
            listOf("čekal sem:  ${render(b)} ± ${render(tolerance)}", "dostal sem: ${render(a)}"),
            zprava,
        )
    }
}

// ── Runner ─────────────────────────────────────────────────────────────────

private sealed class VysledekSichty {
    data object Sedi : VysledekSichty()
    data class Spadla(val spadla: SichtaSpadla) : VysledekSichty()
    data class Chyba(val cause: Throwable) : VysledekSichty()
}

/** Czech count inflection: 1 → one, 2–4 → few, 0 and 5+ → many. */
private fun sklon(n: Int, one: String, few: String, many: String): String =
    "$n " + when {
        n == 1 -> one
        n in 2..4 -> few
        else -> many
    }

/** Case-sensitive glob where `*` spans any run and `?` one character, fully anchored. */
private fun globRegex(glob: String): Regex = buildString {
    for (c in glob) {
        when (c) {
            '*' -> append(".*")
            '?' -> append(".")
            else -> append(Regex.escape(c.toString()))
        }
    }
}.toRegex()

private fun renderChyba(cause: Throwable): String {
    val jmeno = cause.javaClass.simpleName.ifEmpty { cause.javaClass.name }
    if (cause is Flakanec) {
        val od = cause.odkud
        return if (od != null) "$jmeno v $od" else "$jmeno: ${cause.zprava}"
    }
    return "$jmeno: ${cause.message}"
}

/**
 * Runs the registry in order (failures are isolated — one spadla sichta takes down only
 * itself), prints the §6 console format, and returns the exit code: 0 when everything
 * passed, 1 otherwise. [filter] is a glob on display names (`Sichta.*` matches all
 * member tests of the Sichta class), [verbose] announces each test before it runs.
 */
fun spustSichty(sichty: List<Sichta>, filter: String? = null, verbose: Boolean = false): Int {
    val regex = filter?.takeIf { it.isNotEmpty() }?.let(::globRegex)
    val run = if (regex == null) sichty else sichty.filter { regex.matches(it.jmeno) }

    println("PorubaUnit — šichta začíná")
    val nameWidth = run.maxOfOrNull { it.jmeno.length } ?: 0
    var passed = 0
    var failed = 0
    var errors = 0

    val startNanos = System.nanoTime()
    for (sichta in run) {
        if (verbose) println("  » ${sichta.jmeno}")
        var vysledek: VysledekSichty = VysledekSichty.Sedi
        val ms = measureTimeMillis {
            vysledek = try {
                sichta.telo()
                VysledekSichty.Sedi
            } catch (s: SichtaSpadla) {
                VysledekSichty.Spadla(s)
            } catch (t: Throwable) {
                VysledekSichty.Chyba(t)
            }
        }
        val jmeno = sichta.jmeno.padEnd(nameWidth)
        when (val v = vysledek) {
            VysledekSichty.Sedi -> {
                passed++
                println("  ✓ $jmeno  ($ms ms)")
            }
            is VysledekSichty.Spadla -> {
                failed++
                println("  ✗ $jmeno  ($ms ms)")
                if (v.spadla.odkud.isNotEmpty()) {
                    println("      ${v.spadla.volani} spadlo v ${v.spadla.odkud}")
                } else {
                    println("      ${v.spadla.volani} spadlo")
                }
                v.spadla.detaily.forEach { println("      $it") }
                v.spadla.vzkaz?.let { println("      vzkaz: $it") }
            }
            is VysledekSichty.Chyba -> {
                errors++
                println("  ! $jmeno  ($ms ms)")
                println("      dostal ju: ${renderChyba(v.cause)}")
            }
        }
    }
    val totalMs = (System.nanoTime() - startNanos) / 1_000_000

    println()
    println(
        "Fajront: ${sklon(passed, "prošla", "prošly", "prošlo")}, " +
            "${sklon(failed, "spadla", "spadly", "spadlo")}, " +
            "${sklon(errors, "chyba", "chyby", "chyb")} — za $totalMs ms",
    )
    return if (failed == 0 && errors == 0) 0 else 1
}
