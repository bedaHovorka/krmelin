# Krmelin language spec — diagnostics

This document is the published index of Krmelin diagnostic codes, required by Plan.md §10.
The compiler's catalogue lives in `src/main/kotlin/krmelin/diag/DiagCode.kt`, and
`DiagCodeTest` fails the build if the two ever drift apart.

## Diagnostic format

Every diagnostic follows the Plan.md §10 template: what is wrong, exactly where with a
caret, and how to fix it — then, optionally, one line of dialect flourish. The joke never
replaces the information.

```
error: expected a class member [E102]
  --> demo.krm:2:5
   |
 2 |     123
   |     ^^^ a class body holds only functions and properties
   = pomoc: start the member with 'robota', 'toz' or 'mozej'
```

The source line and caret are shown when the compiler knows the file's text. Anything that
constructs a `DiagnosticReporter` directly, without lexing a file, gets the header alone.

## Code ranges

Codes are stable: a code is never reused for a different diagnostic. Retire it instead.
Ranges are reserved per phase so later milestones can add codes without renumbering.

| Range | Phase | Status |
|---|---|---|
| E001–E099 | lexer | in use |
| E100–E199 | parser | in use |
| E200–E299 | resolver | reserved for M3 |
| E300–E399 | type checker | reserved for M3 |

## Lexer (E001–E099)

| Code | Meaning |
|---|---|
| E001 | Unexpected character — the byte has no meaning in Krmelin source. |
| E002 | Unterminated string literal; the closing `"` is missing. |
| E003 | Unterminated `${...}` interpolation; the closing `}` is missing. |
| E004 | String templates nested beyond the supported depth. |
| E005 | Invalid escape sequence. Valid escapes are `\n` `\t` `\r` `\\` `\"` `\$`. |
| E006 | Unknown annotation; only `@Sichta` and `@Parta` exist. |
| E007 | Numeric literal is malformed or out of range for its type. |
| E008 | Unterminated block comment; the closing `*/` is missing. |

## Parser (E100–E199)

| Code | Meaning |
|---|---|
| E100 | Generic syntax error — the grammar expected a different token here. |
| E101 | Expected a top-level declaration. |
| E102 | Expected a class member; a class body holds only functions and properties. |
| E103 | Unexpected `}` — no block is open at this point. |
| E104 | Expected `}`; a block, class body or `podle_teho` body is still open. |
| E105 | A function needs a body (`{ ... }` or `= expr`); only `predpis` may omit one. |
| E106 | `jedynak` and `predpis` cannot take constructor parameters. |
| E107 | A lambda parameter must be an identifier. |
| E108 | Leftover token inside a `${...}`; an interpolation holds exactly one expression. |
| E109 | Expected an expression; nothing valid can start here. |

## Notes on wording

Diagnostic prose is currently English while the surrounding vocabulary (`pomoc`, the
keywords themselves) is dialect. Rendering the messages in ostravština is a deliberate
follow-up: it is an editorial pass over the whole catalogue and wants the author's voice
rather than a mechanical translation.
