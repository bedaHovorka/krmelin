package krmelin

import krmelin.ast.Decl
import krmelin.diag.Diagnostic
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import krmelin.lexer.SourceSpan
import krmelin.parser.Parser
import krmelin.resolve.Resolution
import krmelin.resolve.Resolver
import krmelin.types.TypeChecker
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared pipeline helpers for semantic tests: lexer → parser → resolver → type checker,
 * all feeding one [DiagnosticReporter], mirroring how ParserTest composes the front end.
 */
object TestSupport {
    class Analyzed(
        val unit: Decl.CompilationUnit,
        val reporter: DiagnosticReporter,
        val resolution: Resolution,
    )

    const val TEST_FILE = "test.krm"

    /** Runs the full M3 pipeline over [source]. */
    fun analyze(source: String, file: String = TEST_FILE): Analyzed {
        val reporter = DiagnosticReporter()
        reporter.registerSource(file, source)
        val tokens = Lexer(source, file, reporter).lex()
        val unit = Parser(tokens, file, reporter).parse()
        val resolution = Resolver(reporter).resolve(unit)
        TypeChecker(reporter, resolution).check(unit)
        return Analyzed(unit, reporter, resolution)
    }

    /** Asserts exactly one diagnostic of [code] exists, at [span], mentioning [fragment]. */
    fun expectDiag(
        reporter: DiagnosticReporter,
        code: krmelin.diag.DiagCode,
        span: SourceSpan? = null,
        fragment: String? = null,
    ): Diagnostic {
        val matches = reporter.all.filter { it.code == code }
        assertEquals(1, matches.size, "expected exactly one $code, got:\n${reporter.render()}")
        val diag = matches.single()
        if (span != null) assertEquals(span, diag.span, "span of $code")
        if (fragment != null) {
            val text = listOfNotNull(diag.message, diag.highlight, diag.fix, diag.flourish).joinToString("\n")
            assertTrue(fragment in text, "expected '$fragment' in diagnostic text:\n$text")
        }
        return diag
    }
}
