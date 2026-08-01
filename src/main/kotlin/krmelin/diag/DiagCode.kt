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
 * | HAV400–HAV499 | codegen / Kotlin backend (M4) |
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

    // ── Resolver ─────────────────────────────────────────────────────────────
    /** Catch-all for resolver failures that have no dedicated code. */
    RESOLVER_INTERNAL("HAV200"),
    DUPLICATE_DECLARATION("HAV201"),
    /** Warning only: an inner declaration hides an outer one. */
    SHADOWED_DECLARATION("HAV210"),
    UNDECLARED_NAME("HAV220"),
    UNKNOWN_TYPE_NAME("HAV221"),
    /**
     * A generic type name was written with the wrong number of type arguments.
     */
    TYPE_ARITY_MISMATCH("HAV222"),
    /** PorubaUnit: `@Sichta` on anything but a `robota`. */
    SICHTA_NOT_ON_ROBOTA("HAV230"),
    /** PorubaUnit: a `@Sichta` `robota` has parameters or no body — only parameterless ones run. */
    SICHTA_WITH_PARAMS("HAV231"),
    /** PorubaUnit: `rynek` is the program entry point and never runs as a `@Sichta`. */
    SICHTA_ON_RYNEK("HAV232"),
    /** PorubaUnit: `@Parta` on anything but a plain `tryda` / `zapisnik tryda`. */
    PARTA_NOT_ON_TRYDA("HAV233"),
    /** PorubaUnit: a `@Parta` class holding member tests must be constructible with no arguments. */
    PARTA_CTOR_PARAM_REQUIRED("HAV234"),
    /** Warning only: a `@Sichta` member of a class that is not `@Parta` is never discovered. */
    SICHTA_OUTSIDE_PARTA("HAV235"),

    // ── Type checker ─────────────────────────────────────────────────────────
    TYPE_MISMATCH("HAV300"),
    /** Assignment to an immutable `toz` binding. */
    ASSIGN_TO_IMMUTABLE("HAV330"),
    CONDITION_NOT_BUL("HAV331"),
    /**
     * Assignment used where a value is expected. Kotlin has no assignment expressions, so
     * `pridej(a = 7)` would silently become a *named argument* — see Plan.md §15.
     */
    ASSIGN_NOT_EXPRESSION("HAV332"),
    /**
     * A property declared outside a function body with no initializer. Legal for a local
     * (Kotlin defers the assignment) but not at top level or in a `tryda` body.
     */
    UNINITIALIZED_PROPERTY("HAV333"),
    /**
     * A lambda with parameters in a position that gives it no expected type. Krmelin has no
     * function-type syntax, so nothing can annotate the parameters and kotlinc cannot infer them.
     */
    LAMBDA_NEEDS_CONTEXT("HAV334"),
    /** `davaj <expr>` inside a function with no declared return type. */
    UNEXPECTED_RETURN_VALUE("HAV341"),
    RETURN_TYPE_MISMATCH("HAV342"),
    ARITY_MISMATCH("HAV350"),

    // ── Codegen / Kotlin backend ─────────────────────────────────────────────
    /** Catch-all for codegen failures that have no dedicated code. */
    CODEGEN_INTERNAL("HAV400"),
    /** `krmelin run --args` on a file with no top-level, parameterless `rynek`. */
    NO_ENTRY_POINT("HAV401"),
    /** The embedded Kotlin compiler rejected the emitted `.kt` (its own messages follow). */
    BACKEND_FAILED("HAV410"),
    /**
     * Warning only: a different `Flakanci.kt` already sits beside the output, so the runtime
     * source was not written there and the emitted `.kt` will not compile on its own.
     */
    RUNTIME_NOT_WRITTEN("HAV411"),
    /** PorubaUnit: two test files map to the same Kotlin facade class (`KrmelinTestMain` included). */
    DUPLICATE_TEST_FACADE("HAV412"),
}
