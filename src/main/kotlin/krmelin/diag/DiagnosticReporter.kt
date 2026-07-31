package krmelin.diag

/**
 * Collects diagnostics produced during compilation.
 *
 * Reporters are intended to be created per compilation unit so that callers
 * can decide whether to fail fast or accumulate every diagnostic.
 */
class DiagnosticReporter {
    companion object {
        /** Upper bound on retained diagnostics; see [report]. */
        const val MAX_DIAGNOSTICS = 200
    }

    private val diagnostics = mutableListOf<Diagnostic>()
    private var suppressed = 0
    private val sources = mutableMapOf<String, String>()

    /**
     * Supplies the text of [file] so [render] can quote the offending line under a caret.
     *
     * First registration wins: the sub-lexer that re-lexes a `${...}` interpolation is
     * constructed with the same file name but only a fragment of its text, and must not
     * displace the real source.
     */
    fun registerSource(file: String, text: String) {
        sources.putIfAbsent(file, text)
    }

    val all: List<Diagnostic> get() = diagnostics
    val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.ERROR }
    val warnings: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.WARNING }

    val hasErrors: Boolean get() = diagnostics.any { it.severity == Severity.ERROR }

    /**
     * Records [diagnostic], up to [MAX_DIAGNOSTICS].
     *
     * Beyond the cap only a count is kept. Past a couple of hundred diagnostics the
     * output is unreadable anyway, and the cap bounds the damage if a recovery loop ever
     * stops making progress again: an unbounded list would exhaust the heap and take the
     * test JVM with it, which is a far more confusing failure than a truncated report.
     */
    fun report(diagnostic: Diagnostic) {
        if (diagnostics.size >= MAX_DIAGNOSTICS) {
            suppressed++
            return
        }
        diagnostics += diagnostic
    }

    fun report(
        severity: Severity,
        code: String,
        message: String,
        span: krmelin.lexer.SourceSpan,
        highlight: String? = null,
        fix: String? = null,
        flourish: String? = null,
    ) {
        report(Diagnostic(severity, code, message, span, highlight, fix, flourish))
    }

    fun error(
        code: String,
        message: String,
        span: krmelin.lexer.SourceSpan,
        highlight: String? = null,
        fix: String? = null,
        flourish: String? = null,
    ) = report(Severity.ERROR, code, message, span, highlight, fix, flourish)

    fun warning(
        code: String,
        message: String,
        span: krmelin.lexer.SourceSpan,
        highlight: String? = null,
        fix: String? = null,
        flourish: String? = null,
    ) = report(Severity.WARNING, code, message, span, highlight, fix, flourish)

    /**
     * Renders every diagnostic using the template in Plan.md §10:
     *
     * ```
     * <severity>: <problem> [<code>]
     *   --> <file>:<line>:<col>
     *    |
     *  N |   <source line>
     *    |         ^^^^ <what is wrong here>
     *    = pomoc: <concrete fix>
     *    = <flourish>
     * ```
     *
     * The snippet and caret lines are emitted only when the source of the offending file
     * was supplied via [registerSource]; otherwise the header alone is rendered.
     */
    fun render(): String = buildString {
        for (d in diagnostics) {
            appendLine("${d.severity.name.lowercase()}: ${d.message} [${d.code}]")
            appendLine("  --> ${d.span.file}:${d.span.startLine}:${d.span.startCol}")
            val sourceLine = sourceLineFor(d.span)
            if (sourceLine != null) {
                val number = d.span.startLine.toString()
                val gutter = " ".repeat(number.length + 2)
                appendLine("$gutter|")
                appendLine(" $number | $sourceLine")
                val indent = " ".repeat((d.span.startCol - 1).coerceAtLeast(0))
                val carets = "^".repeat(caretLength(d.span, sourceLine))
                appendLine("$gutter| $indent$carets${d.highlight?.let { " $it" } ?: ""}")
            } else {
                d.highlight?.let { appendLine("   | $it") }
            }
            d.fix?.let { appendLine("   = pomoc: $it") }
            d.flourish?.let { appendLine("   = $it") }
        }
        if (suppressed > 0) appendLine("... a $suppressed more (dalsi hlaseni potlacena)")
    }

    private fun sourceLineFor(span: krmelin.lexer.SourceSpan): String? {
        val text = sources[span.file] ?: return null
        return text.lines().getOrNull(span.startLine - 1)
    }

    /**
     * Caret run length, clamped to at least one so a zero-width span still points
     * somewhere. A span that runs onto later lines is underlined only to the end of the
     * first, which is where the reader is looking.
     */
    private fun caretLength(span: krmelin.lexer.SourceSpan, sourceLine: String): Int {
        val end = if (span.endLine == span.startLine) span.endCol else sourceLine.length + 1
        return (end - span.startCol).coerceAtLeast(1)
    }
}
