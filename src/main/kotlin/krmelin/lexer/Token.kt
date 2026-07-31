package krmelin.lexer

/**
 * A single lexical token with its exact source text, location, and optional
 * decoded literal value.
 */
data class Token(
    val type: TokenType,
    val text: String,
    val span: SourceSpan,
    val value: Any? = null,
) {
    override fun toString(): String =
        "$type(${text.replace("\n", "\\n")})@$span"
}
