# Krmelin language spec — diagnostics

This document is the published index of Krmelin diagnostic codes, required by Plan.md §10.
The compiler's catalogue lives in `src/main/kotlin/krmelin/diag/DiagCode.kt`, and
`DiagCodeTest` fails the build if the two ever drift apart.

## Severities

Output labels are ostravština, diacritics-stripped like the rest of the surface:

| Severity | Label | Dialect gloss |
|---|---|---|
| error | `hawaryja` | a breakdown, an accident (Silesian, from German *Havarie*) |
| warning | `pozur` | "dej pozur" — watch out (po naszymu *dować pozōr*) |
| info | `oznam` | a notice, an announcement |
| debug | `dlubani` | tinkering, picking at something (Polish *dłubać*) |
| trace | `sled` | a track being followed |

The compiler currently raises only `hawaryja` and `pozur`; `oznam`, `dlubani` and `sled`
are the verbosity levels reserved for CLI output (M5).

## Diagnostic format

Every diagnostic follows the Plan.md §10 template: what is wrong, exactly where with a
caret, and how to fix it — then, optionally, one line of dialect flourish. The joke never
replaces the information.

```
hawaryja: tu ma byt clen trydy [HAV102]
  --> demo.krm:2:5
   |
 2 |     123
   |     ^^^ v tele trydy su enem roboty a hodnoty
   = pomoc: zacni s 'robota', 'toz' abo 'mozej'
```

The source line and caret are shown when the compiler knows the file's text. Anything that
constructs a `DiagnosticReporter` directly, without lexing a file, gets the header alone.

## Code ranges

Codes are stable: a code is never reused for a different diagnostic. Retire it instead.
The `HAV` prefix is short for *hawaryja*. Ranges are reserved per phase so later
milestones can add codes without renumbering.

| Range | Phase | Status |
|---|---|---|
| HAV001–HAV099 | lexer | in use |
| HAV100–HAV199 | parser | in use |
| HAV200–HAV299 | resolver | in use |
| HAV300–HAV399 | type checker | in use |
| HAV400–HAV499 | codegen / Kotlin backend | in use |

## Lexer (HAV001–HAV099)

| Code | Meaning |
|---|---|
| HAV001 | Unexpected character — the byte has no meaning in Krmelin source. |
| HAV002 | Unterminated string literal; the closing `"` is missing. |
| HAV003 | Unterminated `${...}` interpolation; the closing `}` is missing. |
| HAV004 | String templates nested beyond the supported depth. |
| HAV005 | Invalid escape sequence. Valid escapes are `\n` `\t` `\r` `\\` `\"` `\$`. |
| HAV006 | Unknown annotation; only `@Sichta` and `@Parta` exist. |
| HAV007 | Numeric literal is malformed or out of range for its type. |
| HAV008 | Unterminated block comment; the closing `*/` is missing. |

## Parser (HAV100–HAV199)

| Code | Meaning |
|---|---|
| HAV100 | Generic syntax error — the grammar expected a different token here. |
| HAV101 | Expected a top-level declaration. |
| HAV102 | Expected a class member; a class body holds only functions and properties. |
| HAV103 | Unexpected `}` — no block is open at this point. |
| HAV104 | Expected `}`; a block, class body or `podle_teho` body is still open. |
| HAV105 | A function needs a body (`{ ... }` or `= expr`); only `predpis` may omit one. |
| HAV106 | `jedynak` and `predpis` cannot take constructor parameters. |
| HAV107 | A lambda parameter must be an identifier. |
| HAV108 | Leftover token inside a `${...}`; an interpolation holds exactly one expression. |
| HAV109 | Expected an expression; nothing valid can start here. |

## Resolver (HAV200–HAV299)

| Code | Meaning |
|---|---|
| HAV200 | Internal resolver failure; no more specific code applies. |
| HAV201 | Duplicate declaration — the name already exists in this scope. |
| HAV210 | Warning only: an inner declaration shadows an outer one with the same name. |
| HAV220 | Undeclared name — no symbol with that name is visible here. |
| HAV221 | Undeclared type name — not a prelude alias and not a declared `tryda`. |
| HAV222 | Wrong number of type arguments — e.g. `Halda<Cyslo, Dryst>`, where `Halda` takes one. |

## Type checker (HAV300–HAV399)

| Code | Meaning |
|---|---|
| HAV300 | Type mismatch — the value's type does not fit the declared type. |
| HAV330 | Assignment to an immutable `toz` binding. |
| HAV331 | Condition must be `Bul` — `kaj`/`kajtez`/`rubaj`/`podle_teho` conditions. |
| HAV341 | `davaj <expr>` returns a value but the function declares no return type. |
| HAV342 | `davaj` does not match the function's declared return type. |
| HAV350 | Wrong number of arguments at a call site. |

## Codegen / Kotlin backend (HAV400–HAV499)

| Code | Meaning |
|---|---|
| HAV400 | Internal codegen failure; no more specific code applies. |
| HAV401 | No entry point — `krmelin run` needs a top-level, parameterless `rynek`. |
| HAV410 | The embedded Kotlin compiler rejected the emitted `.kt`; its own messages follow. |

## Notes on wording

Diagnostic prose is ostravština, diacritics-stripped, in the same register as the
keywords: short and brisk ("tu ma byt ...", "za 'kaj' ma byt '('"), with `bo`, `enem`,
`abo`, `furt` doing the dialect work. The Meaning column above stays English on purpose —
this index is documentation, and a reader debugging a build should not need the dialect
to look up a code.
