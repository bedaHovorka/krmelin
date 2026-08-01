package krmelin.cli

import com.github.ajalt.clikt.testing.test
import krmelin.BuildInfo
import krmelin.krmelinCli
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The global `--version` flag from Plan.md §11. The version string is generated from the
 * Gradle `version` property by the `generateBuildInfo` task, so this also guards against a
 * release jar that still calls itself a SNAPSHOT.
 */
class VersionOptionTest {

    @Test
    fun `--version prints the build version and exits 0`() {
        val result = krmelinCli().test("--version")

        assertEquals(0, result.statusCode, "--version should exit 0")
        assertTrue(
            result.stdout.contains(BuildInfo.VERSION),
            "expected the version in the output, got: ${result.stdout}",
        )
        assertTrue(
            result.stdout.contains("krmelin"),
            "expected the binary name in the output, got: ${result.stdout}",
        )
    }

    @Test
    fun `the build version is a plain release version`() {
        assertTrue(
            BuildInfo.VERSION.matches(Regex("""\d+\.\d+\.\d+""")),
            "expected a bare semver, got '${BuildInfo.VERSION}'",
        )
        assertFalse(BuildInfo.VERSION.contains("SNAPSHOT"), "a released jar must not be a SNAPSHOT")
    }

    @Test
    fun `--help still lists every subcommand alongside --version`() {
        val result = krmelinCli().test("--help")

        assertEquals(0, result.statusCode)
        for (name in listOf("compile", "run", "test", "fmt", "repl")) {
            assertTrue(result.stdout.contains(name), "--help should list '$name'")
        }
    }
}
