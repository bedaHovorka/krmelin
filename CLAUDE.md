# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This repository is currently a **pre-implementation scaffold**: the only content is `Plan.md` at the repo root (no source code, build files, README, or LICENSE exist yet, and there are no commits). `Plan.md` is the authoritative, detailed spec — mission statement, tech stack rationale, full repo layout, EBNF grammar, keyword mapping table, error-message templates, and a milestone-by-milestone (M1–M9) execution plan with acceptance criteria. Consult it directly for anything not summarized below.

## What Krmelin is

Krmelin is a satirical programming language: Kotlin, but with source keywords replaced by authentic Ostrava-dialect ("ostravština") vocabulary, sitting on top of a genuine transpiler (lexer, recursive-descent + Pratt parser, typed AST, resolver, minimal type checker, source-to-source codegen emitting Kotlin). It is a **clean-room reimplementation** inspired by the concept of Tomáš Kohout's OSTRAJava — not a fork, no shared code — targeting Kotlin instead of a custom VM. Licensed MIT.

## Planned tech stack & commands

Per Plan.md §2/§8/§11 (not yet scaffolded — M1 creates these):

- **Language/build**: Kotlin (JVM, Kotlin 2.x) on JDK 21, Gradle with Kotlin DSL. Build: `./gradlew build`.
- **CLI**: Clikt-based `krmelin` binary with subcommands `compile <file>`, `run <file>`, `test [path]`, `fmt <file|dir>`, `repl` (see §11 for flags and exit codes per command).
- **Tests**: JUnit 5 for the compiler itself; a self-built, JUnit-independent test framework ("PorubaUnit") for Krmelin-language programs, discovered via `@Sichta`/`@Parta` annotations and run through `krmelin test`.

## Architecture: compiler pipeline

```
.krm source → Lexer → Parser → Resolver → TypeChecker → Lowering → KotlinEmitter → .kt source → kotlinc
```

Planned directory layout under `src/main/kotlin/krmelin/` (see Plan.md §3 for the full tree):

- `lexer/` — hand-rolled lexer; `Keywords.kt` is the **single source of truth** mapping keyword strings → `TokenType`.
- `parser/` — recursive-descent for declarations/statements, Pratt parser (`ExprParser.kt`) for expressions, panic-mode error recovery.
- `ast/` — sealed node hierarchies (for exhaustive `when`), every node carries a `SourceSpan`.
- `resolve/` — lexical scoping (`Scope`, `SymbolTable`), binds names, reports undeclared/duplicate/shadowing.
- `types/` — intentionally minimal type checker (primitives, declared types, nullability); no generic inference or overload resolution — harder cases are left to `kotlinc`.
- `lower/` — desugars constructs (e.g. subject-less `when`, `for`-loop sugar, `rozdava` → `@Throws`) so codegen stays simple.
- `codegen/` — `KotlinEmitter.kt` visits the lowered AST to produce deterministically-formatted `.kt` source (no timestamps, fixed indentation — required for stable golden tests).
- `runtime/` — `PorubaUnit.kt` (test runtime) and `Flakanci.kt` (exception hierarchy), both emitted/depended-on by generated code, not by the compiler itself.
- `diag/` — diagnostics, following the template in Plan.md §10 (what's wrong, exact span with caret, concrete fix, optional dialect flourish).

**Key invariant**: every reserved keyword must match `^[@A-Za-z_][A-Za-z0-9_]*$` (ASCII-only, no diacritics) — enforced by a lexer test asserting every `Keywords.kt` entry matches the pattern. Identifiers, string literals, comments, and diagnostic prose remain full Unicode; only the keyword surface is constrained.

**Testing convention**: codegen correctness is verified with golden files — `tests/golden/foo.krm` + `foo.kt.expected`, diffed after transpiling (regenerate with `-Dupdate.golden=true` for intentional changes).

## Design decisions worth knowing

- **Legal posture**: clean-room only — no OSTRAJava source, grammar, or generated parser may be copied; only the dialect vocabulary and some keyword-mapping ideas (from OSTRAJava issue #14/#15 discussions) are reused as design homage. MIT license; required attribution text lives in README/NOTICE (Plan.md §1).
- **`rozdava` (`throws`)** is parsed, stored on `FunDecl`, and emitted as Kotlin `@Throws` — but deliberately **never type-checked**. Calling a `rozdava` function without a `pultik` (`try`) is legal. This is intentional, not a gap.
- **`chuj`** is the canonical null keyword (a Polish-derived word with a harsher register than "null"); `nic` is an accepted milder synonym. Both must lex to `TokenType.NULL` and emit identical Kotlin `null` — covered by a golden test. The register choice is deliberate and documented once in `docs/keywords.md`, not repeated elsewhere.
- **Exception hierarchy** (`runtime/Flakanci.kt`) follows a "pub brawl" metaphor: `pultik`/`bitka`/`fajront`/`dostanes` map to `try`/`catch`/`finally`/`throw`. `Flakanec` is the base exception type (not "slacker" — it means "a whack/slap").
- **PorubaUnit has no runtime dependency on JUnit** — it's self-contained so its console output format and dialect voice stay fully controlled.
- **Milestone ordering**: M6 (Flakanci examples) depends on M4 delivering exception codegen first — it is not an independently-buildable example.
- **Explicit non-goals** (Plan.md §15): no custom bytecode VM/interpreter, no coroutines/full generic inference/operator overloading/reflection/multiplatform, no enforcement of checked exceptions, no IDE tooling (LSP/debugger) in v0.1, no backward compatibility with OSTRAJava syntax.

For the full keyword mapping table, EBNF grammar, sample program translations, error-message catalogue, and milestone acceptance criteria, read `Plan.md` directly.
