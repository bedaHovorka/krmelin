package krmelin.lexer

/**
 * A source-location range: 1-based lines and columns.
 *
 * The span is inclusive at the start and exclusive at the end. A zero-width
 * span is allowed (start == end) for synthetic nodes or for pointing at a
 * single character.
 */
data class SourceSpan(
    val file: String,
    val startLine: Int,
    val startCol: Int,
    val endLine: Int,
    val endCol: Int,
) {
    companion object {
        /** A sentinel span for nodes that do not originate from source text. */
        val NONE = SourceSpan("", 0, 0, 0, 0)

        /** A zero-width point span at the given 1-based line/column. */
        fun point(file: String, line: Int, col: Int) =
            SourceSpan(file, line, col, line, col)
    }

    /** Returns a span that covers both this and [other]. */
    fun union(other: SourceSpan): SourceSpan {
        if (this == NONE) return other
        if (other == NONE) return this
        val (sl, sc) = if (startLine < other.startLine ||
            (startLine == other.startLine && startCol <= other.startCol)
        ) {
            startLine to startCol
        } else {
            other.startLine to other.startCol
        }
        val (el, ec) = if (endLine > other.endLine ||
            (endLine == other.endLine && endCol >= other.endCol)
        ) {
            endLine to endCol
        } else {
            other.endLine to other.endCol
        }
        return SourceSpan(file, sl, sc, el, ec)
    }
}
