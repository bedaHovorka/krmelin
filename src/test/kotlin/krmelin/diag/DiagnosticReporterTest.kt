package krmelin.diag

import krmelin.lexer.SourceSpan
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagnosticReporterTest {
    private fun reportN(reporter: DiagnosticReporter, n: Int) {
        repeat(n) { i ->
            reporter.error(code = "E999", message = "boom $i", span = SourceSpan.point("t.krm", 1, 1))
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
            reporter.render().contains("7 more"),
            "render() should disclose suppressed diagnostics, got:\n${reporter.render().takeLast(200)}",
        )
    }

    @Test
    fun `render includes the fix and flourish lines when present`() {
        val reporter = DiagnosticReporter()
        reporter.error(
            code = "E001",
            message = "neco je spatne",
            span = SourceSpan.point("t.krm", 2, 3),
            highlight = "^^^",
            fix = "zkus tohle",
            flourish = "bez pultiku neni bitka",
        )
        val rendered = reporter.render()
        assertTrue(rendered.contains("error: neco je spatne [E001]"), rendered)
        assertTrue(rendered.contains("--> t.krm:2:3"), rendered)
        assertTrue(rendered.contains("= pomoc: zkus tohle"), rendered)
        assertTrue(rendered.contains("bez pultiku neni bitka"), rendered)
    }
}
