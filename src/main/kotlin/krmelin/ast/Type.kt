package krmelin.ast

import krmelin.lexer.SourceSpan

/** A type reference as it appears in source. */
sealed class TypeNode(override val span: SourceSpan) : Node(span) {
    data class NamedType(
        val name: String,
        val nullable: Boolean = false,
        val typeArgs: List<TypeNode> = emptyList(),
        override val span: SourceSpan,
    ) : TypeNode(span)
}
