package krmelin.diag

import krmelin.lexer.SourceSpan

/**
 * Severity levels, each carrying the ostravština label used in rendered output.
 *
 * The labels are diacritics-stripped dialect words (same convention as `pomoc`):
 * *hawaryja* — a breakdown/accident (Silesian, from German *Havarie*); *pozur* —
 * "dej pozur", watch out (po naszymu *dować pozōr*); *oznam* — a notice;
 * *dlubani* — tinkering/picking at something (Polish *dłubać*); *sled* — a track
 * being followed.
 *
 * Only [ERROR] and [WARNING] are raised by the compiler today; INFO/DEBUG/TRACE
 * are the verbosity levels for CLI output (M5).
 */
enum class Severity(val label: String) {
    ERROR("hawaryja"),
    WARNING("pozur"),
    INFO("oznam"),
    DEBUG("dlubani"),
    TRACE("sled"),
}

/**
 * A compiler diagnostic: what is wrong, where, and how to fix it.
 *
 * The message should be plain-language and helpful first; a dialect flourish,
 * if present, lives in [flourish].
 */
data class Diagnostic(
    val severity: Severity,
    val code: DiagCode,
    val message: String,
    val span: SourceSpan,
    val highlight: String? = null,
    val fix: String? = null,
    val flourish: String? = null,
)
