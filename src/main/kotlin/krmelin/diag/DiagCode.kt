package krmelin.diag

/**
 * Diagnostic code catalogue.
 *
 * Codes are stable identifiers users can search for, so an entry is never reused for a
 * different meaning — retire it instead. The `HAV` prefix is short for *hawaryja*, the
 * dialect word for a breakdown that also labels error output (see [Severity]). Ranges
 * are reserved per compiler phase so later milestones can add codes without renumbering:
 *
 * | Range | Phase |
 * |---|---|
 * | HAV001–HAV099 | lexer |
 * | HAV100–HAV199 | parser |
 * | HAV200–HAV299 | resolver (M3) |
 * | HAV300–HAV399 | type checker (M3) |
 *
 * Keep [docs/language-spec.md](../../../../../../docs/language-spec.md) in step with this
 * list; Plan.md §10 requires a published index.
 */
enum class DiagCode(val code: String) {
    // ── Lexer ────────────────────────────────────────────────────────────────
    UNEXPECTED_CHAR("HAV001"),
    UNTERMINATED_STRING("HAV002"),
    UNTERMINATED_TEMPLATE("HAV003"),
    TEMPLATE_TOO_DEEP("HAV004"),
    INVALID_ESCAPE("HAV005"),
    UNKNOWN_ANNOTATION("HAV006"),
    BAD_NUMBER("HAV007"),
    UNTERMINATED_COMMENT("HAV008"),

    // ── Parser ───────────────────────────────────────────────────────────────
    /** Generic "the grammar expected something else here". */
    UNEXPECTED_TOKEN("HAV100"),
    EXPECTED_DECLARATION("HAV101"),
    EXPECTED_MEMBER("HAV102"),
    UNEXPECTED_BRACE("HAV103"),
    MISSING_BRACE("HAV104"),
    MISSING_FUN_BODY("HAV105"),
    PARAMS_NOT_ALLOWED("HAV106"),
    BAD_LAMBDA_PARAM("HAV107"),
    TEMPLATE_LEFTOVER("HAV108"),
    EXPECTED_EXPRESSION("HAV109"),
}
