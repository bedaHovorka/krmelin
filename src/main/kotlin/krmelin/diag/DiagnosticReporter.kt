package krmelin.diag

/**
 * Collects diagnostics produced during compilation.
 *
 * Reporters are intended to be created per compilation unit so that callers
 * can decide whether to fail fast or accumulate every diagnostic.
 */
class DiagnosticReporter {
    private val diagnostics = mutableListOf<Diagnostic>()

    val all: List<Diagnostic> get() = diagnostics
    val errors: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.ERROR }
    val warnings: List<Diagnostic> get() = diagnostics.filter { it.severity == Severity.WARNING }

    val hasErrors: Boolean get() = diagnostics.any { it.severity == Severity.ERROR }

    fun report(diagnostic: Diagnostic) {
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
    }
}
