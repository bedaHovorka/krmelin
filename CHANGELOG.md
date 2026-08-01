# Changelog

All notable changes to Krmelin are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing yet.

## [0.1.0] — 2026-08-01

First public release. Krmelin compiles a Kotlin-shaped language whose entire keyword
surface is Ostrava dialect, and runs it on the JVM.

### Added

- **Compiler pipeline** — hand-rolled lexer, recursive-descent parser with a Pratt
  expression parser, typed AST with source spans, lexical resolver, minimal type checker,
  lowering pass, and a deterministic Kotlin source emitter.
- **Ostrava keyword surface** — `robota`, `tryda`, `toz`/`mozej`, `kaj`/`boinak`,
  `rubaj`, `davaj`, `fajne`/`nyt`, `zarvat`, and the rest. Every reserved word is ASCII-only;
  identifiers, strings and comments stay full Unicode. See `docs/keywords.md`.
- **Exceptions, the pub-brawl hierarchy** — `pultik`/`bitka`/`fajront`/`dostanes` mapping to
  `try`/`catch`/`finally`/`throw`, over a `Flakanec` base type. `rozdava` is parsed and
  emitted as Kotlin `@Throws`, deliberately without check-exception enforcement.
- **PorubaUnit** — a self-contained test framework with no JUnit dependency. Tests are
  discovered through `@Sichta` and `@Parta` annotations and run by `krmelin test`.
- **CLI** — `compile` (`-o`, `--jar`, `--emit-only`), `run` (`--args`, `--keep`) and
  `test` (`--filter`, `--verbose`), with the exit codes specified in `Plan.md` §11.
- **`--version`** — global flag reporting the build version.
- **Embedded Kotlin compiler** — `kotlin-compiler-embeddable` is bundled, so `krmelin run`
  and `krmelin compile --jar` need no `kotlinc` on `PATH`. A JDK 21 runtime is the only
  prerequisite.
- **`cli/krmelin` launcher** — locates the fat jar, honours a `KRMELIN_JAR` override, and
  prefers `$JAVA_HOME`'s JVM.
- **Diagnostics** — dialect-voiced errors with a caret-marked span and a concrete fix
  suggestion, under stable `HAV***` codes; the parser recovers to report several per run.
- **Documentation** — language specification with EBNF, keyword reference with dialect
  sources, worked examples, and the PorubaUnit reference, all under `docs/`.
- **Tests** — 500+ compiler tests, golden `.kt` output files, a 39-case diagnostic corpus,
  and end-to-end tests that drive the real fat jar. CI additionally compiles every example
  and every fenced Krmelin snippet in the docs.

### Known limitations

- `fmt` and `repl` are stubs: they parse their arguments and report that they are not yet
  implemented.
- The global `--no-color` and `-v/--verbose` flags from `Plan.md` §11 are not implemented
  (`-v/--verbose` exists on `krmelin test`).
- The type checker is intentionally minimal — primitives, declared types, nullability,
  arity. Generic inference, overload resolution and other hard cases are emitted as-is and
  left for the Kotlin compiler to judge.
- JVM only. Native binaries and a JavaScript target are ambitions, not v0.1 features.

[Unreleased]: https://github.com/bedaHovorka/krmelin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/bedaHovorka/krmelin/releases/tag/v0.1.0
