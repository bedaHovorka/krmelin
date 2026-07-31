package krmelin.ast

import krmelin.lexer.SourceSpan

/** Base of every AST node. */
sealed class Node(open val span: SourceSpan)
