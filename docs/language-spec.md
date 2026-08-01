# Krmelin language specification

Krmelin is a source-to-source transpiler: valid Krmelin source compiles to equivalent
Kotlin. This document is the definitive language reference. Every section maps Krmelin
constructs back to their Kotlin equivalents so you always know what your code means.

---

## 1. Lexical structure

### 1.1 ASCII-only keyword rule

Every Krmelin **reserved word** must match `^[@A-Za-z_][A-Za-z0-9_]*$`. No diacritics
appear in keywords, even where the underlying dialect word carries them. Dialect spelling
is preserved phonetically: `sachta` not *šachta*, `pultik` not *pultík*.

The rule covers reserved words only. **Identifiers, string literals, and comments are
full Unicode** — user code may freely name things `příkopa` or print `"fajront, chlapi"`.

An automated test (`KeywordsTest`) asserts every entry in `Keywords.kt` matches the
pattern; the constraint cannot drift as contributors add keywords.

### 1.2 Identifiers

An identifier starts with a Unicode letter or `_`, followed by zero or more Unicode
letters, digits, or `_`. Keywords shadow identifiers — `robota` cannot be used as a
variable name.

### 1.3 Comments

Line comments start with `//` and run to end of line. Block comments are delimited by
`/*` and `*/` and may span multiple lines. Block comments do not nest.

### 1.4 String literals and templates

String literals are delimited by `"`. Supported escape sequences: `\n`, `\t`, `\r`,
`\\`, `\"`, `\$`.

String templates embed expressions with `${expr}`:

```krmelin
toz jmeno = "Franta"
zarvat("Nazdar, ${jmeno}!")
```

### 1.5 Numeric literals

- Integer: `42`, `0`, `-1`
- Floating-point: `3.14`, `0.5`

### 1.6 Statement termination

Statements are terminated by a **newline** (or `;` as an explicit separator). Krmelin
is newline-significant like Kotlin — not OSTRAJava's `pyco` terminator.

---

## 2. Types and prelude

### 2.1 Primitive type aliases

The prelude provides Krmelin names for Kotlin's built-in types. These are not keywords,
but they are reserved by the prelude and should not be redefined.

| Krmelin | Kotlin | Description |
|---|---|---|
| `Dryst` | `String` | "drivel/prattle" — a string of characters |
| `Cyslo` | `Int` | "number" — a 32-bit integer |
| `CysloDesetinne` | `Double` | "decimal number" — a 64-bit float |
| `Bul` | `Boolean` | truth value: `fajne` or `nyt` |
| `Chachar` | `Char` | "rascal" — a single Unicode character |

### 2.2 Collection type aliases

| Krmelin | Kotlin | Description |
|---|---|---|
| `Halda<T>` | `List<T>` | "a heap/pile" |
| `Kupa<K,V>` | `Map<K,V>` | "a heap/store" |

### 2.3 Prelude functions

| Krmelin | Kotlin | Description |
|---|---|---|
| `pravit(x)` | `print(x)` | Print without newline |
| `zarvat(x)` | `println(x)` | Print with newline |
| `x.naDryst()` | `x.toString()` | Convert to string |
| `x.dylka` | `x.length` / `x.size` | Length of string or collection |

### 2.4 Nullable types

Append `?` to any type to make it nullable: `Dryst?`, `Cyslo?`. The canonical null
literal is `chuj`; `nic` is an accepted milder synonym. Both lex to `TokenType.NULL`
and emit identical Kotlin `null`. See [docs/keywords.md](keywords.md) for the register
note.

---

## 3. Declarations

### 3.1 Package declaration — `sachta`

```krmelin
sachta moj.projekt
```

Optional; one per file. Maps to Kotlin `package`.

### 3.2 Import — `privezt`

```krmelin
privezt krmelin.baza.*
privezt java.util.Date
```

Maps to Kotlin `import`.

### 3.3 Function — `robota`

```krmelin
robota jmeno(param1: Typ1, param2: Typ2 = default) : NavratovyTyp {
    davaj hodnota
}
```

- `robota` maps to Kotlin `fun`.
- The entry point is a top-level `robota rynek()` (no parameters, no return type).
- `davaj` maps to Kotlin `return`.
- Parameters may have default values.
- `rozdava Typ` on the signature maps to Kotlin `@Throws(Typ::class)` and is **never
  enforced** — it is documentation only.

### 3.4 Immutable binding — `toz`

```krmelin
toz x: Cyslo = 42
toz y = "Ostrava"    // type inferred
```

Maps to Kotlin `val`. Cannot be reassigned after initialization.

### 3.5 Mutable binding — `mozej`

```krmelin
mozej i = 0
i = i + 1
```

Maps to Kotlin `var`.

### 3.6 Class — `tryda`

```krmelin
tryda Haviř(toz mejno: Dryst) {
    robota predstav() {
        zarvat("Haviř: ${mejno}")
    }
}
```

Maps to Kotlin `class`. A class body holds functions and properties.

### 3.7 Data class — `zapisnik tryda`

```krmelin
zapisnik tryda Haviř(toz mejno: Dryst, mozej odrubano: Cyslo)
```

Maps to Kotlin `data class`. Generates `equals`, `hashCode`, `toString`, and `copy`.

### 3.8 Singleton — `jedynak`

```krmelin
jedynak MujSingleton {
    toz hodnota = 42
}
```

Maps to Kotlin `object`. One instance, no constructor parameters.

### 3.9 Interface — `predpis`

```krmelin
predpis Tisknutelny {
    robota tiskni()
}
```

Maps to Kotlin `interface`. Abstract function bodies are omitted (bare newline after the
parameter list).

---

## 4. Expressions and precedence

### 4.1 Literals

| Krmelin | Meaning |
|---|---|
| `fajne` | `true` |
| `nyt` | `false` |
| `chuj` / `nic` | `null` |
| `42` | integer literal |
| `3.14` | float literal |
| `"text"` | string literal |
| `"${expr}"` | string template |

### 4.2 Operators (highest to lowest precedence)

| Level | Operators | Notes |
|---|---|---|
| Unary | `!`, `-` | Logical not, numeric negation |
| Postfix | `.member`, `?.member`, `()` | Member access, safe-call, call |
| Multiplicative | `*`, `/`, `%` | |
| Additive | `+`, `-` | |
| Elvis | `?:` | Returns left if non-null, else right |
| Comparison | `<`, `>`, `<=`, `>=` | |
| Equality | `==`, `!=` | |
| Logical and | `aj` | `&&` |
| Logical or | `ci` | `\|\|` |
| Assignment | `=` | Right-associative |

### 4.3 Safe-call and Elvis

```krmelin
toz d = s?.dylka ?: 0     // 0 if s is chuj
```

---

## 5. Statements and control flow

### 5.1 `kaj` / `kajtez` / `boinak` — if / else if / else

```krmelin
kaj (podminka) {
    // ...
} kajtez (jina_podminka) {
    // ...
} boinak {
    // ...
}
```

Conditions must be `Bul`.

### 5.2 `podle_teho` — when

As a statement, without a subject:

```krmelin
podle_teho {
    x == 0    -> zarvat("nula")
    x > 0     -> zarvat("kladne")
    boinak    -> zarvat("zaporne")
}
```

With a subject expression:

```krmelin
podle_teho (x) {
    0       -> zarvat("nula")
    1, 2    -> zarvat("male")
    boinak  -> zarvat("velke")
}
```

Maps to Kotlin `when`.

### 5.3 `prokazdy` — for

```krmelin
prokazdy (x v zoznam) {
    zarvat(x.naDryst())
}
```

`v` maps to Kotlin `in`. Maps to Kotlin `for`.

### 5.4 `rubaj` — while

```krmelin
rubaj (i < 10) {
    i = i + 1
}
```

Maps to Kotlin `while`.

### 5.5 `zdybat` — break

```krmelin
zdybat
```

Maps to Kotlin `break`. Exits the innermost loop.

### 5.6 `dalej` — continue

```krmelin
dalej
```

Maps to Kotlin `continue`. Skips to the next iteration.

### 5.7 `davaj` — return

```krmelin
davaj vysledek
davaj            // Unit return
```

Maps to Kotlin `return`.

---

## 6. Exceptions

### 6.1 The pub brawl metaphor

Exception handling in Krmelin follows the pub brawl metaphor: you stand at the bar
counter (`pultik`), someone dishes out a whack (`rozdava`), you get one (`dostanes`), a
brawl breaks out (`bitka`), and eventually the shift ends regardless (`fajront`).

### 6.2 `pultik` / `bitka` / `fajront` — try / catch / finally

```krmelin
pultik {
    riskantniOperace()
} bitka (f: DelenoNulou) {
    zarvat("Deleni nulou: ${f.zprava}")
} bitka (f: Flakanec) {
    zarvat("Obecna chyba: ${f.zprava}")
} fajront {
    zarvat("Vzdy se spusti.")
}
```

- Multiple `bitka` clauses are tested in order; first matching type wins.
- `fajront` runs on every path: normal exit, caught exception, and rethrow.

### 6.3 `dostanes` — throw

```krmelin
dostanes Flakanec("neco se posralo")
```

Maps to Kotlin `throw`.

### 6.4 `rozdava` — throws (documentation only)

```krmelin
robota riskuj() rozdava Flakanec {
    // ...
}
```

Maps to Kotlin `@Throws(Flakanec::class)`. **Never enforced** — calling a `rozdava`
function without a `pultik` is legal. It is documentation only.

### 6.5 Exception hierarchy

All Krmelin exceptions extend `Flakanec` ("a whack/slap"):

| Krmelin type | Extends | Analogous to |
|---|---|---|
| `Flakanec(zprava: Dryst)` | `RuntimeException` | `Exception` |
| `ChujovyFlakanec` | `Flakanec` | `NullPointerException` |
| `MimoBarak` | `Flakanec` | `IndexOutOfBoundsException` |
| `DelenoNulou` | `Flakanec` | `ArithmeticException` |
| `ZlyDryst` | `Flakanec` | `NumberFormatException` |

Every exception exposes `.zprava` (message) and `.odkud` (source span, when available).

---

## 7. Null safety

Krmelin has Kotlin-style null safety:

- A non-nullable type `Dryst` cannot hold `chuj`.
- A nullable type `Dryst?` can.
- Safe-call `x?.member` returns `chuj` if `x` is `chuj`.
- Elvis `x ?: default` returns `default` if `x` is `chuj`.

```krmelin
robota delka(s: Dryst?) : Cyslo {
    davaj s?.dylka ?: 0
}
```

---

## 8. Functions

### 8.1 Top-level functions

```krmelin
robota secteni(a: Cyslo, b: Cyslo) : Cyslo {
    davaj a + b
}
```

### 8.2 Member functions

```krmelin
tryda Pocitadlo {
    mozej pocet: Cyslo = 0

    robota pridej() {
        pocet = pocet + 1
    }

    robota hodnota() : Cyslo {
        davaj pocet
    }
}
```

### 8.3 Default parameters

```krmelin
robota pozdrav(mejno: Dryst = "cype") {
    zarvat("Nazdar, ${mejno}!")
}
```

### 8.4 `joch` — this

Inside a class body, `joch` refers to the current instance, analogous to Kotlin `this`.

### 8.5 Expression body

A function with a single-expression body may use `=`:

```krmelin
robota dvojnasobek(x: Cyslo) : Cyslo = x * 2
```

---

## 9. Full EBNF grammar

```ebnf
program        = { topLevelDecl } ;
topLevelDecl   = packageDecl | importDecl | classDecl | funDecl | propertyDecl ;
packageDecl    = "sachta" qualifiedName NL ;
importDecl     = "privezt" qualifiedName [ "." "*" ] NL ;

classDecl      = { annotation } ( [ "zapisnik" ] "tryda" IDENT [ paramList ] [ classBody ]
               | "jedynak" IDENT [ classBody ]
               | "predpis" IDENT [ classBody ] ) ;
classBody      = "{" { member } "}" ;
member         = funDecl | propertyDecl ;

funDecl        = { annotation } "robota" IDENT paramList
                 [ ":" type ] [ "rozdava" type { "," type } ] funBody ;
annotation     = "@Sichta" | "@Parta" ;
funBody        = block | "=" expr NL | NL ;
paramList      = "(" [ param { "," param } ] ")" ;
param          = [ "toz" | "mozej" ] IDENT ":" type [ "=" expr ] ;

propertyDecl   = ( "toz" | "mozej" ) IDENT [ ":" type ] [ "=" expr ] NL ;

block          = "{" { statement } "}" ;
statement      = propertyDecl
               | ifStmt | whenStmt | forStmt | whileStmt
               | returnStmt | breakStmt | continueStmt
               | tryStmt | throwStmt
               | exprStmt ;

ifStmt         = "kaj" "(" expr ")" block
                 { "kajtez" "(" expr ")" block }
                 [ "boinak" block ] ;
whenStmt       = "podle_teho" [ "(" [ expr ] ")" ] "{" { whenBranch } "}" ;
whenBranch     = ( exprList | "boinak" ) "->" ( expr | block ) ;
forStmt        = "prokazdy" "(" IDENT "v" expr ")" block ;
whileStmt      = "rubaj" "(" expr ")" block ;
returnStmt     = "davaj" [ expr ] NL ;
breakStmt      = "zdybat" NL ;
continueStmt   = "dalej" NL ;
tryStmt        = "pultik" block { "bitka" "(" param ")" block } [ "fajront" block ] ;
throwStmt      = "dostanes" expr NL ;
exprStmt       = expr NL ;

type           = IDENT [ "<" type { "," type } ">" ] [ "?" ] ;

expr           = assignment ;
assignment     = logicalOr [ "=" assignment ] ;
logicalOr      = logicalAnd { "ci" logicalAnd } ;
logicalAnd     = equality { "aj" equality } ;
equality       = comparison { ("==" | "!=") comparison } ;
comparison     = elvis { ("<" | ">" | "<=" | ">=") elvis } ;
elvis          = additive [ "?:" elvis ] ;
additive       = multiplicative { ("+" | "-") multiplicative } ;
multiplicative = unary { ("*" | "/" | "%") unary } ;
unary          = ("!" | "-") unary | postfix ;
postfix        = primary { callSuffix | "." IDENT | "?." IDENT } ;
callSuffix     = "(" [ argList ] ")" ;
primary        = literal | IDENT | "(" expr ")" | stringTemplate | lambda ;
literal        = INT | FLOAT | STRING | "fajne" | "nyt" | "chuj" | "nic" ;
NL             = ? newline or ';' ? ;
```

---

## 10. Mapping to Kotlin

| Krmelin | Kotlin |
|---|---|
| `sachta` | `package` |
| `privezt` | `import` |
| `tryda` | `class` |
| `zapisnik tryda` | `data class` |
| `jedynak` | `object` |
| `predpis` | `interface` |
| `robota` | `fun` |
| `toz` | `val` |
| `mozej` | `var` |
| `davaj` | `return` |
| `kaj` / `kajtez` / `boinak` | `if` / `else if` / `else` |
| `podle_teho` | `when` |
| `prokazdy (x v xs)` | `for (x in xs)` |
| `rubaj` | `while` |
| `zdybat` | `break` |
| `dalej` | `continue` |
| `fajne` / `nyt` | `true` / `false` |
| `chuj` / `nic` | `null` |
| `aj` / `ci` | `&&` / `\|\|` |
| `pultik` / `bitka` / `fajront` | `try` / `catch` / `finally` |
| `dostanes` | `throw` |
| `rozdava` | `@Throws(...)` (annotation, not enforced) |
| `joch` | `this` |
| `pravit(x)` | `print(x)` |
| `zarvat(x)` | `println(x)` |
| `Dryst` / `Cyslo` / `Bul` | `String` / `Int` / `Boolean` |
| `rynek` (top-level, no params) | `main` |

---

## 11. Diagnostics and error codes

### Severity labels

| Severity | Label | Dialect gloss |
|---|---|---|
| error | `hawaryja` | a breakdown, an accident (Silesian, from German *Havarie*) |
| warning | `pozur` | "dej pozur" — watch out |
| info | `oznam` | a notice, an announcement |
| debug | `dlubani` | tinkering, picking at something |
| trace | `sled` | a track being followed |

### Diagnostic format

```
hawaryja: tu ma byt clen trydy [HAV102]
  --> demo.krm:2:5
   |
 2 |     123
   |     ^^^ v tele trydy su enem roboty a hodnoty
   = pomoc: zacni s 'robota', 'toz' abo 'mozej'
```

### Code ranges

| Range | Phase |
|---|---|
| HAV001–HAV099 | Lexer |
| HAV100–HAV199 | Parser |
| HAV200–HAV299 | Resolver |
| HAV300–HAV399 | Type checker |
| HAV400–HAV499 | Codegen / Kotlin backend |

### Lexer (HAV001–HAV099)

| Code | Meaning |
|---|---|
| HAV001 | Unexpected character |
| HAV002 | Unterminated string literal |
| HAV003 | Unterminated `${...}` interpolation |
| HAV004 | String templates nested beyond supported depth |
| HAV005 | Invalid escape sequence |
| HAV006 | Unknown annotation |
| HAV007 | Malformed numeric literal |
| HAV008 | Unterminated block comment |

### Parser (HAV100–HAV199)

| Code | Meaning |
|---|---|
| HAV100 | Generic syntax error |
| HAV101 | Expected a top-level declaration |
| HAV102 | Expected a class member |
| HAV103 | Unexpected `}` |
| HAV104 | Expected `}` — block still open |
| HAV105 | Function needs a body |
| HAV106 | `jedynak`/`predpis` cannot have constructor parameters |
| HAV107 | Lambda parameter must be an identifier |
| HAV108 | Leftover token inside `${...}` |
| HAV109 | Expected an expression |

### Resolver (HAV200–HAV299)

| Code | Meaning |
|---|---|
| HAV200 | Internal resolver failure |
| HAV201 | Duplicate declaration |
| HAV210 | Warning: shadowing an outer name |
| HAV220 | Undeclared name |
| HAV221 | Undeclared type name |
| HAV222 | Wrong number of type arguments |
| HAV230 | `@Sichta` on a non-`robota` |
| HAV231 | `@Sichta robota` has parameters or no body |
| HAV232 | `@Sichta` on `rynek` |
| HAV233 | `@Parta` on a non-`tryda` |
| HAV234 | `@Parta` class not constructible with no arguments |
| HAV235 | Warning: `@Sichta` member of a non-`@Parta` class |

### Type checker (HAV300–HAV399)

| Code | Meaning |
|---|---|
| HAV300 | Type mismatch |
| HAV330 | Assignment to immutable `toz` |
| HAV331 | Condition must be `Bul` |
| HAV332 | Assignment used where a value is expected |
| HAV333 | Top-level property has no initial value |
| HAV334 | Lambda parameter types cannot be inferred |
| HAV341 | `davaj <expr>` in a function with no declared return type |
| HAV342 | `davaj` type does not match declared return type |
| HAV350 | Wrong number of arguments at call site |

### Codegen / Kotlin backend (HAV400–HAV499)

| Code | Meaning |
|---|---|
| HAV400 | Internal codegen failure |
| HAV401 | No entry point for `krmelin run` |
| HAV410 | Kotlin compiler rejected the emitted `.kt` |
| HAV411 | Runtime file already exists at the output path |
| HAV412 | Two test files map to the same facade class |

---

## 12. Known limitations

The following Kotlin features are deliberately outside Krmelin v0.1 scope:

- **No coroutines** — `suspend`, `async`, `await`, `Flow`.
- **No full generic inference** — generics pass through to kotlinc; complex inference is
  left for kotlinc to handle.
- **No operator overloading**.
- **No delegation** (`by` keyword).
- **No reflection** or annotation processing beyond `@Sichta`/`@Parta`.
- **No multiplatform** — targets JVM only.
- **No checked exception enforcement** — `rozdava` is documentation only (§6.4).
- **No IDE tooling** (LSP, debugger) in v0.1.
- **Not backward-compatible with OSTRAJava** syntax (`pyco` terminator,
  case-insensitive keywords, `.tryda` files).

Anything not covered by the Krmelin compiler is emitted as-is and validated by kotlinc.
