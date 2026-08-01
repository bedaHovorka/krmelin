package krmelin.parser

import krmelin.diag.DiagnosticReporter
import krmelin.lexer.Lexer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeoutPreemptively
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParserTest {
    private fun parseResource(name: String): Pair<String, DiagnosticReporter> {
        val file = File("tests/golden/$name")
        val source = file.readText()
        val reporter = DiagnosticReporter()
        val lexer = Lexer(source, file.path, reporter)
        val tokens = lexer.lex()
        val parser = Parser(tokens, file.path, reporter)
        parser.parse()
        return source to reporter
    }

    @Test
    fun `hello sample parses without errors`() {
        val (_, reporter) = parseResource("hello.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `fizzbuzz sample parses without errors`() {
        val (_, reporter) = parseResource("fizzbuzz.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `data class sample parses without errors`() {
        val (_, reporter) = parseResource("data_class.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `chuj safety sample parses without errors`() {
        val (_, reporter) = parseResource("chuj_safety.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `exceptions sample parses without errors`() {
        val (_, reporter) = parseResource("exceptions.krm")
        assertFalse(reporter.hasErrors, reporter.render())
    }

    @Test
    fun `parser reports multiple errors on malformed input and recovers`() {
        val source = """
            robota foo() {
                toz x =
            }

            robota bar() {
                toz y =
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val parser = Parser(tokens, "bad.krm", reporter)
        val cu = parser.parse()
        assertTrue(reporter.errors.isNotEmpty(), "expected at least one diagnostic")
        assertTrue(reporter.errors.size >= 2, "expected recovery to surface multiple errors, got ${reporter.errors.size}")
        // Recovery must not swallow the second declaration into the first errored block.
        assertEquals(2, cu.declarations.size, "expected both functions to survive recovery")
        assertEquals("foo", (cu.declarations[0] as krmelin.ast.Decl.FunDecl).name)
        assertEquals("bar", (cu.declarations[1] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `zapisnik combined with jedynak reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("zapisnik jedynak Foo { }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected 'zapisnik jedynak' to be rejected")
    }

    @Test
    fun `zapisnik combined with predpis reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("zapisnik predpis Foo { }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected 'zapisnik predpis' to be rejected")
    }

    @Test
    fun `predpis combined with jedynak reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("predpis jedynak Foo { }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected 'predpis jedynak' (interface+object without zapisnik) to be rejected")
    }

    @Test
    fun `malformed package declaration recovers and still parses following declarations`() {
        val source = """
            sachta 123
            robota foo() { }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the malformed package name")
        assertEquals(1, cu.declarations.size, "recovery should still surface the following function")
    }

    @Test
    fun `malformed import declaration recovers and still parses following declarations`() {
        val source = """
            privezt 123
            robota foo() { }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the malformed import name")
        assertEquals(1, cu.declarations.size, "recovery should still surface the following function")
    }

    @Test
    fun `malformed class member recovers and still parses the sibling member`() {
        val source = """
            tryda Foo {
                123
                robota bar() { }
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the stray integer literal member")
        val cls = cu.declarations[0] as krmelin.ast.Decl.ClassDecl
        assertEquals(1, cls.members.size, "recovery should still surface 'bar'")
        assertEquals("bar", (cls.members[0] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `missing identifier after dot reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("robota f() { davaj a. }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the missing member name")
    }

    @Test
    fun `missing newline after a statement reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("robota f() { davaj 1 davaj 2 }", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic when two statements share a line without a separator")
    }

    @Test
    fun `recovery skips several tokens before reaching a synchronization point`() {
        val source = """
            robota foo() {
                1 2 3 4
            }
            robota bar() { }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors)
        assertEquals(2, cu.declarations.size, "recovery should still surface both functions")
        assertEquals("bar", (cu.declarations[1] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `stray boinak with no matching kaj recovers instead of hanging forever`() {
        // Regression test: 'boinak'/'kajtez'/'bitka'/'fajront' have no parse dispatch
        // of their own. If synchronize() ever returns without consuming one of these
        // when it is itself the offending token, the caller retries the identical
        // failing parse and the compiler hangs forever instead of reporting an error.
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val source = """
                robota f() {
                    boinak
                }
                robota g() { }
            """.trimIndent()
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "bad.krm", reporter).lex()
            val cu = Parser(tokens, "bad.krm", reporter).parse()
            assertTrue(reporter.hasErrors)
            assertEquals(2, cu.declarations.size, "recovery should still surface both functions")
        }
    }

    @Test
    fun `stray bitka with no matching pultik recovers instead of hanging forever`() {
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val source = """
                robota f() {
                    bitka
                }
                robota g() { }
            """.trimIndent()
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "bad.krm", reporter).lex()
            val cu = Parser(tokens, "bad.krm", reporter).parse()
            assertTrue(reporter.hasErrors)
            assertEquals(2, cu.declarations.size, "recovery should still surface both functions")
        }
    }

    // Recovery must make progress on *every* token that synchronize() stops at but the
    // enclosing dispatcher cannot parse. That is the whole set difference between
    // syncTokens and each dispatcher's cases, not just the continuation keywords above:
    // a nested 'robota' in a block, a nested 'tryda' in a class body, and any statement
    // keyword at file scope each used to spin forever, appending a diagnostic per pass
    // until the JVM died with OutOfMemoryError.

    @Test
    fun `nested robota inside a block recovers instead of hanging forever`() {
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val source = """
                robota main() {
                    robota nested() {
                    }
                }
                robota g() { }
            """.trimIndent()
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "bad.krm", reporter).lex()
            val cu = Parser(tokens, "bad.krm", reporter).parse()
            assertTrue(reporter.hasErrors, "expected a diagnostic for the nested function")
            assertTrue(
                cu.declarations.any { it is krmelin.ast.Decl.FunDecl && it.name == "g" },
                "recovery should still surface the following function",
            )
        }
    }

    @Test
    fun `nested tryda inside a class body recovers instead of hanging forever`() {
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val source = """
                tryda Outer {
                    tryda Inner {
                    }
                }
                robota g() { }
            """.trimIndent()
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "bad.krm", reporter).lex()
            val cu = Parser(tokens, "bad.krm", reporter).parse()
            assertTrue(reporter.hasErrors, "expected a diagnostic for the nested class")
            assertTrue(
                cu.declarations.any { it is krmelin.ast.Decl.FunDecl && it.name == "g" },
                "recovery should still surface the following function",
            )
        }
    }

    @Test
    fun `a real parser diagnostic renders with the offending line and a caret`() {
        val reporter = DiagnosticReporter()
        val source = "tryda Foo {\n    123\n}\n"
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        val out = reporter.render()
        assertTrue(out.contains("  --> bad.krm:2:5"), out)
        assertTrue(out.contains(" 2 |     123"), out)
        assertTrue(out.contains("   |     ^^^"), out)
    }

    @Test
    fun `a bare davaj at end of input is a valueless return`() {
        // check() short-circuits on isAtEnd(), so check(..., EOF) can never be true and
        // the EOF arm was dead: 'davaj' as the last token fell through to parseExpression
        // and reported a bogus "expected expression".
        val reporter = DiagnosticReporter()
        val tokens = Lexer("robota f() {\n    davaj", "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(
            reporter.errors.none { it.code == krmelin.diag.DiagCode.EXPECTED_EXPRESSION },
            "a bare 'davaj' has no value to parse, got: ${reporter.render()}",
        )
        val fn = cu.declarations.filterIsInstance<krmelin.ast.Decl.FunDecl>().single()
        val block = (fn.body as krmelin.ast.FunBody.BlockBody).block
        val ret = block.statements.filterIsInstance<krmelin.ast.Stmt.ReturnStmt>().single()
        assertEquals(null, ret.value, "'davaj' with nothing after it returns no value")
    }

    @Test
    fun `a function with no body outside predpis reports a diagnostic`() {
        val reporter = DiagnosticReporter()
        val tokens = Lexer("tryda A {\n    robota m(): Cyslo\n}", "bad.krm", reporter).lex()
        Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "only 'predpis' may declare a function without a body")
    }

    @Test
    fun `constructor parameters on a jedynak are rejected without losing the body`() {
        val reporter = DiagnosticReporter()
        val source = "jedynak Foo(x: Cyslo) {\n    robota bar() { }\n}"
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "an object cannot take constructor parameters")
        val cls = cu.declarations.filterIsInstance<krmelin.ast.Decl.ClassDecl>().single()
        assertEquals(1, cls.members.size, "the body must still be parsed as the class body")
        assertEquals("bar", (cls.members[0] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `constructor parameters on a predpis are rejected without losing the body`() {
        val reporter = DiagnosticReporter()
        val source = "predpis Foo(x: Cyslo) {\n    robota bar() { }\n}"
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "an interface cannot take constructor parameters")
        val cls = cu.declarations.filterIsInstance<krmelin.ast.Decl.ClassDecl>().single()
        assertEquals(1, cls.members.size, "the body must still be parsed as the class body")
    }

    @Test
    fun `a multi-line block comment terminates a statement exactly like a plain newline`() {
        // Characterization, not a bug: statement termination is newline-significant
        // (Plan.md §4.3), and a comment that spans lines contains a line break. Both
        // forms below stop the property at '1' and then reject a top-level '+'. The
        // comment must not make the two behave differently.
        fun parseIt(source: String): Pair<Int, Int> {
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "t.krm", reporter).lex()
            val cu = Parser(tokens, "t.krm", reporter).parse()
            return cu.declarations.size to reporter.errors.size
        }
        assertEquals(
            parseIt("toz x = 1\n+ 2"),
            parseIt("toz x = 1 /* a\nb */ + 2"),
            "a multi-line comment must terminate the statement just like the newline it contains",
        )
    }

    @Test
    fun `a malformed statement inside a lambda body does not swallow later declarations`() {
        val source = """
            tryda A {
                robota m() {
                    toz f = { x ->
                        toz y =
                        davaj x
                    }
                }
                robota n() { davaj 1 }
            }
            robota top() { davaj 2 }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the incomplete property")
        assertTrue(
            cu.declarations.any { it is krmelin.ast.Decl.FunDecl && it.name == "top" },
            "one bad line in a lambda must not delete every later declaration",
        )
        val cls = cu.declarations.filterIsInstance<krmelin.ast.Decl.ClassDecl>().single()
        assertTrue(
            cls.members.any { it is krmelin.ast.Decl.FunDecl && it.name == "n" },
            "member 'n' must stay inside the class rather than being hoisted out",
        )
    }

    @Test
    fun `a malformed when branch is contained and the surrounding when survives`() {
        val source = """
            robota f() {
                podle_teho (x) {
                    -> 1
                    boinak -> 2
                }
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the branch with no condition")
        val fn = cu.declarations[0] as krmelin.ast.Decl.FunDecl
        val block = (fn.body as krmelin.ast.FunBody.BlockBody).block
        val whenStmt = block.statements.filterIsInstance<krmelin.ast.Stmt.WhenStmt>().singleOrNull()
        assertTrue(whenStmt != null, "the podle_teho statement must survive a bad branch")
        assertTrue(whenStmt.branches.any { it.isElse }, "the well-formed 'boinak' branch must survive")
    }

    @Test
    fun `a missing closing brace keeps the class and its parsed members`() {
        val source = """
            tryda A {
                robota m() {
                }
            robota g() {
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the missing '}'")
        assertTrue(cu.declarations.isNotEmpty(), "one missing brace must not discard the whole file")
        val cls = cu.declarations[0] as krmelin.ast.Decl.ClassDecl
        assertEquals("A", cls.name)
        assertTrue(
            cls.members.any { it is krmelin.ast.Decl.FunDecl && it.name == "m" },
            "the successfully parsed member must survive",
        )
    }

    @Test
    fun `a stray closing brace at file scope is reported and does not truncate the file`() {
        val source = """
            robota a() {
            }
            }
            robota b() {
            }
        """.trimIndent()
        val reporter = DiagnosticReporter()
        val tokens = Lexer(source, "bad.krm", reporter).lex()
        val cu = Parser(tokens, "bad.krm", reporter).parse()
        assertTrue(reporter.hasErrors, "expected a diagnostic for the stray '}'")
        assertEquals(2, cu.declarations.size, "the stray '}' must not swallow the rest of the file")
        assertEquals("b", (cu.declarations[1] as krmelin.ast.Decl.FunDecl).name)
    }

    @Test
    fun `statement keyword at file scope recovers instead of hanging forever`() {
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val source = """
                davaj 1
                robota g() { }
            """.trimIndent()
            val reporter = DiagnosticReporter()
            val tokens = Lexer(source, "bad.krm", reporter).lex()
            val cu = Parser(tokens, "bad.krm", reporter).parse()
            assertTrue(reporter.hasErrors, "expected a diagnostic for the top-level 'davaj'")
            assertTrue(
                cu.declarations.any { it is krmelin.ast.Decl.FunDecl && it.name == "g" },
                "recovery should still surface the following function",
            )
        }
    }
}
