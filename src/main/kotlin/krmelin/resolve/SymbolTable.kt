package krmelin.resolve

import kotlin.math.min

/**
 * The per-compilation symbol environment: the prelude root plus file scopes built under it.
 *
 * Also owns the did-you-mean machinery used by undeclared-name diagnostics (HAV220),
 * matching Plan.md §10's `nemyslel si 'hodinySpanku'?` fix line.
 */
class SymbolTable(val prelude: Scope) {
    /** A fresh file scope chained under the prelude. */
    fun newFileScope(): Scope = Scope(parent = prelude, kind = Scope.Kind.FILE)

    /**
     * The closest visible name to [name], case-sensitively, if any is within edit
     * distance [MAX_DISTANCE]. Exact matches are not suggestions — the caller only asks
     * when lookup has already failed.
     */
    fun suggest(name: String, scope: Scope): String? =
        scope.visibleNames()
            .filter { it != name }
            .map { it to levenshtein(name, it) }
            .filter { (_, d) -> d <= thresholdFor(name) }
            .minByOrNull { (_, d) -> d }
            ?.first

    private fun thresholdFor(name: String): Int =
        min(MAX_DISTANCE, (name.length / 3).coerceAtLeast(1))

    /** Standard Levenshtein distance, case-sensitive (matching is case-sensitive, §5.1). */
    internal fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private companion object {
        const val MAX_DISTANCE = 2
    }
}
