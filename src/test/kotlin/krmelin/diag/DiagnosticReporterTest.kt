package krmelin.diag

import krmelin.lexer.SourceSpan
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticReporterTest {
    private fun reportN(reporter: DiagnosticReporter, n: Int) {
        repeat(n) { i ->
            reporter.error(code = DiagCode.UNEXPECTED_TOKEN, message = "boom $i", span = SourceSpan.point("t.krm", 1, 1))
        }
    }

    @Test
    fun `the reporter stops accumulating diagnostics past its cap`() {
        // A recovery loop that fails to make progress would otherwise append diagnostics
        // until the JVM dies; the cap turns that into a bounded, readable failure.
        val reporter = DiagnosticReporter()
        reportN(reporter, DiagnosticReporter.MAX_DIAGNOSTICS + 50)
        assertEquals(DiagnosticReporter.MAX_DIAGNOSTICS, reporter.all.size)
        assertTrue(reporter.hasErrors, "capping must not hide the fact that errors occurred")
    }

    @Test
    fun `render says how many diagnostics were suppressed`() {
        val reporter = DiagnosticReporter()
        reportN(reporter, DiagnosticReporter.MAX_DIAGNOSTICS + 7)
        assertTrue(
            reporter.render().contains("a jesce 7 dalsich"),
            "render() should disclose suppressed diagnostics, got:\n${reporter.render().takeLast(200)}",
        )
    }

    @Test
    fun `render follows the Plan section 10 template`() {
        val reporter = DiagnosticReporter()
        reporter.registerSource("t.krm", "tryda Foo {\n    123\n}\n")
        reporter.error(
            code = DiagCode.EXPECTED_MEMBER,
            message = "tu ma byt clen trydy",
            span = SourceSpan("t.krm", 2, 5, 2, 8),
            highlight = "tady se ceka robota nebo toz",
            fix = "zkus 'robota bar() { }'",
            flourish = "trida bez roboty je enem barak bez chlopa",
        )
        assertEquals(
            listOf(
                "hawaryja: tu ma byt clen trydy [HAV102]",
                "  --> t.krm:2:5",
                "   |",
                " 2 |     123",
                "   |     ^^^ tady se ceka robota nebo toz",
                "   = pomoc: zkus 'robota bar() { }'",
                "   = trida bez roboty je enem barak bez chlopa",
            ),
            reporter.render().trimEnd().lines(),
        )
    }

    @Test
    fun `render degrades gracefully when the source is unknown`() {
        val reporter = DiagnosticReporter()
        reporter.error(code = DiagCode.EXPECTED_DECLARATION, message = "boom", span = SourceSpan("gone.krm", 9, 2, 9, 5))
        val out = reporter.render().trimEnd().lines()
        assertEquals("hawaryja: boom [HAV101]", out[0])
        assertEquals("  --> gone.krm:9:2", out[1])
        assertEquals(2, out.size, "no source means no snippet lines, got: $out")
    }

    @Test
    fun `the caret spans multi-line constructs to the end of the first line`() {
        val reporter = DiagnosticReporter()
        reporter.registerSource("t.krm", "robota f() {\n}\n")
        reporter.error(code = DiagCode.EXPECTED_DECLARATION, message = "boom", span = SourceSpan("t.krm", 1, 8, 2, 2))
        val caret = reporter.render().lines()[4]
        assertEquals("   |        ^^^^^", caret, "caret must stop at the end of line 1")
    }

    @Test
    fun `render includes the fix and flourish lines when present`() {
        val reporter = DiagnosticReporter()
        reporter.error(
            code = DiagCode.UNEXPECTED_CHAR,
            message = "neco je spatne",
            span = SourceSpan.point("t.krm", 2, 3),
            highlight = "^^^",
            fix = "zkus tohle",
            flourish = "bez pultiku neni bitka",
        )
        val rendered = reporter.render()
        assertTrue(rendered.contains("hawaryja: neco je spatne [HAV001]"), rendered)
        assertTrue(rendered.contains("--> t.krm:2:3"), rendered)
        assertTrue(rendered.contains("= pomoc: zkus tohle"), rendered)
        assertTrue(rendered.contains("bez pultiku neni bitka"), rendered)
    }

    @Test
    fun `every severity renders its dialect label`() {
        val reporter = DiagnosticReporter()
        for (severity in Severity.entries) {
            reporter.report(
                severity = severity,
                code = DiagCode.UNEXPECTED_TOKEN,
                message = "test ${severity.name}",
                span = SourceSpan.point("t.krm", 1, 1),
            )
        }
        val rendered = reporter.render()
        assertTrue(rendered.contains("hawaryja: test ERROR"), rendered)
        assertTrue(rendered.contains("pozur: test WARNING"), rendered)
        assertTrue(rendered.contains("oznam: test INFO"), rendered)
        assertTrue(rendered.contains("dlubani: test DEBUG"), rendered)
        assertTrue(rendered.contains("sled: test TRACE"), rendered)
    }
}
