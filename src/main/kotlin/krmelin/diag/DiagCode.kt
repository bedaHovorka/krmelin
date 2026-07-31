package krmelin.diag

/**
 * Diagnostic code catalogue.
 *
 * Codes are stable identifiers users can search for, so an entry is never reused for a
 * different meaning — retire it instead. Ranges are reserved per compiler phase so later
 * milestones can add codes without renumbering:
 *
 * | Range | Phase |
 * |---|---|
 * | E001–E099 | lexer |
 * | E100–E199 | parser |
 * | E200–E299 | resolver (M3) |
 * | E300–E399 | type checker (M3) |
 *
 * Keep [docs/language-spec.md](../../../../../../docs/language-spec.md) in step with this
 * list; Plan.md §10 requires a published index.
 */
object DiagCode {
    // ── Lexer ────────────────────────────────────────────────────────────────
    const val UNEXPECTED_CHAR = "E001"
    const val UNTERMINATED_STRING = "E002"
    const val UNTERMINATED_TEMPLATE = "E003"
    const val TEMPLATE_TOO_DEEP = "E004"
    const val INVALID_ESCAPE = "E005"
    const val UNKNOWN_ANNOTATION = "E006"
    const val BAD_NUMBER = "E007"
    const val UNTERMINATED_COMMENT = "E008"

    // ── Parser ───────────────────────────────────────────────────────────────
    /** Generic "the grammar expected something else here". */
    const val UNEXPECTED_TOKEN = "E100"
    const val EXPECTED_DECLARATION = "E101"
    const val EXPECTED_MEMBER = "E102"
    const val UNEXPECTED_BRACE = "E103"
    const val MISSING_BRACE = "E104"
    const val MISSING_FUN_BODY = "E105"
    const val PARAMS_NOT_ALLOWED = "E106"
    const val BAD_LAMBDA_PARAM = "E107"
    const val TEMPLATE_LEFTOVER = "E108"
    const val EXPECTED_EXPRESSION = "E109"
}
