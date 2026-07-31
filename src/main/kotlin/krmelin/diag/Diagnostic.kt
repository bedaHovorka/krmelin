package krmelin.diag

import krmelin.lexer.SourceSpan

enum class Severity {
    ERROR,
    WARNING,
}

/**
 * A compiler diagnostic: what is wrong, where, and how to fix it.
 *
 * The message should be plain-language and helpful first; a dialect flourish,
 * if present, lives in [flourish].
 */
data class Diagnostic(
    val severity: Severity,
    val code: String,
    val message: String,
    val span: SourceSpan,
    val highlight: String? = null,
    val fix: String? = null,
    val flourish: String? = null,
)
