package krmelin.parser

import krmelin.lexer.Token

/**
 * Exception thrown to unwind to the nearest recovery point during panic-mode
 * error recovery. It carries the offending token so callers can synchronize.
 */
class ParseError(val token: Token, message: String) : Exception(message)
