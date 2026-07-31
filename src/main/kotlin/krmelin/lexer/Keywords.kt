package krmelin.lexer

/**
 * Single source of truth mapping Krmelin reserved spellings to token kinds.
 *
 * Every key must match `^[@A-Za-z_][A-Za-z0-9_]*$` (ASCII-only, no
 * diacritics). The lexer uses this map to turn identifiers into keywords.
 * Case-sensitive: `Robota` is an identifier, `robota` is a keyword.
 */
object Keywords {
    val MAP: Map<String, TokenType> = mapOf(
        "sachta" to TokenType.SACHTA,
        "privezt" to TokenType.PRIVEZT,
        "tryda" to TokenType.TRYDA,
        "zapisnik" to TokenType.ZAPISNIK,
        "jedynak" to TokenType.JEDYNAK,
        "predpis" to TokenType.PREDPIS,
        "robota" to TokenType.ROBOTA,
        "toz" to TokenType.TOZ,
        "mozej" to TokenType.MOZEJ,
        "davaj" to TokenType.DAVAJ,
        "kaj" to TokenType.KAJ,
        "kajtez" to TokenType.KAJTEZ,
        "boinak" to TokenType.BOINAK,
        "podle_teho" to TokenType.PODLE_TEHO,
        "prokazdy" to TokenType.PROKAZDY,
        "v" to TokenType.V,
        "rubaj" to TokenType.RUBAJ,
        "zdybat" to TokenType.ZDYBAT,
        "dalej" to TokenType.DALEJ,
        "fajne" to TokenType.FAJNE,
        "nyt" to TokenType.NYT,
        "chuj" to TokenType.NULL,
        "nic" to TokenType.NULL,
        "pultik" to TokenType.PULTIK,
        "bitka" to TokenType.BITKA,
        "fajront" to TokenType.FAJRONT,
        "dostanes" to TokenType.DOSTANES,
        "rozdava" to TokenType.ROZDAVA,
        "aj" to TokenType.AJ,
        "ci" to TokenType.CI,
        "@Sichta" to TokenType.AT_SICHTA,
        "@Parta" to TokenType.AT_PARTA,
    )

    /** All reserved spellings; handy for completion or diagnostics. */
    val ALL: Set<String> = MAP.keys

    /** Looks up a spelling in the keyword table. */
    fun lookup(text: String): TokenType? = MAP[text]
}
