package krmelin.lexer

/**
 * Token kinds produced by the lexer.
 *
 * Keywords are the dialect words from [Keywords.MAP]. The lexical surface is
 * case-sensitive; `Robota` is an identifier, `robota` is [ROBOTA].
 */
enum class TokenType {
    // ── Dialect keywords ─────────────────────────────────────────────────
    SACHTA,         // package
    PRIVEZT,        // import
    TRYDA,          // class
    ZAPISNIK,       // data (class marker)
    JEDYNAK,        // object
    PREDPIS,        // interface
    ROBOTA,         // fun
    TOZ,            // val
    MOZEJ,          // var
    DAVAJ,          // return
    KAJ,            // if
    KAJTEZ,         // else if
    BOINAK,         // else
    PODLE_TEHO,     // when
    PROKAZDY,       // for
    V,              // in
    RUBAJ,          // while
    ZDYBAT,         // break
    DALEJ,          // continue
    FAJNE,          // true
    NYT,            // false
    NULL,           // chuj / nic
    PULTIK,         // try
    BITKA,          // catch
    FAJRONT,        // finally
    DOSTANES,       // throw
    ROZDAVA,        // throws
    AJ,             // &&
    CI,             // ||

    // ── Annotations ──────────────────────────────────────────────────────
    AT_SICHTA,      // @Sichta
    AT_PARTA,       // @Parta

    // ── Literals ─────────────────────────────────────────────────────────
    IDENTIFIER,
    INTEGER_LITERAL,
    FLOAT_LITERAL,
    STRING_LITERAL,

    // ── Operators and punctuation ───────────────────────────────────────
    PLUS,           // +
    MINUS,          // -
    STAR,           // *
    SLASH,          // /
    PERCENT,        // %
    ASSIGN,         // =
    EQ,             // ==
    NEQ,            // !=
    LT,             // <
    GT,             // >
    LE,             // <=
    GE,             // >=
    BANG,           // !
    ELVIS,          // ?:
    DOT,            // .
    SAFE_DOT,       // ?.
    QUESTION,       // ?
    COLON,          // :
    ARROW,          // ->
    LPAREN,         // (
    RPAREN,         // )
    LBRACE,         // {
    RBRACE,         // }
    COMMA,          // ,
    NEWLINE,        // newline or ;
    EOF,            // end of input
}
