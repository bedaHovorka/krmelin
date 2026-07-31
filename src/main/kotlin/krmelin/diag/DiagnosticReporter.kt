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

    /** Renders all diagnostics as plain text, severity first, one per line. */
    fun render(): String = buildString {
        for (d in diagnostics) {
            append(d.severity.name.lowercase())
            append(": ")
            append(d.message)
            append(" [")
            append(d.code)
            append("]")
            append("  --> ")
            append(d.span.file)
            append(":")
            append(d.span.startLine)
            append(":")
            append(d.span.startCol)
            appendLine()
            d.highlight?.let { appendLine("   | $it") }
            d.fix?.let { appendLine("   = pomoc: $it") }
            d.flourish?.let { appendLine("   = $it") }
        }
        if (suppressed > 0) appendLine("... a $suppressed more (dalsi hlaseni potlacena)")
    }
}
