# Krmelin keyword reference

All reserved words in Krmelin are drawn from **ostravština** — the Lach/Silesian dialect
of Czech spoken in Ostrava and its surroundings. This document explains every keyword, its
dialect origin, and how it maps to its Kotlin equivalent.

## ASCII-only rule

Every Krmelin keyword matches `^[@A-Za-z_][A-Za-z0-9_]*$`. No diacritics appear in
reserved words, even where the underlying dialect word carries them. This makes every
keyword typeable on a standard US keyboard without any special input method. Dialect
spelling is preserved phonetically instead — `sachta` (not *šachta*), `pultik` (not
*pultík*), `prokazdy` (not *pro každý*). Identifiers, string literals, and comments
remain full Unicode; only keywords are constrained.

This convention is enforced by an automated test in `src/test/kotlin/krmelin/KeywordsTest.kt`
that asserts every entry in `Keywords.kt` matches the regex. Contributors adding keywords
must satisfy the test; the constraint cannot drift.

## Dialect background

Ostravština belongs to the **Lach dialect group** of Moravian dialects, historically shaped
by Czech, Polish, German, and Silesian. Markers that make the vocabulary feel authentic:

- **Clipped vowels** — no long vowels where standard Czech has them ("ostravština nema
  dluhe samohlasky … na vic neni čas").
- **Penultimate stress** — documented Lach feature: stress always on the second-to-last
  syllable.
- **v → f shift** — e.g. *sfeter*, *fčela*; gives *fajny* ("fine"), *fajront* ("end of
  shift").
- **German mining borrowings** — *fajront* (Feierabend — end of working day), *šichta*
  (Schicht — shift), *cymerhajer*, *dratban*.
- **Polish borrowings** — *rubat* (Polish *rąbać* — to hew), *jedynak* (only child),
  *chuj* (see register note below).
- Locals call it **"po našymu"** — "in our way."

## Full keyword table

| Kotlin construct | Krmelin keyword | Dialect meaning | Notes |
|---|---|---|---|
| `package` | `sachta` | "mine shaft" — the pit your code lives in | One per file, optional |
| `import` | `privezt` | "to haul in" | `privezt krmelin.baza.*` |
| `class` | `tryda` | Dialect form of *třída* (class/classroom) | Primary declaration |
| `data class` | `zapisnik tryda` | "notebook/ledger" — a record | Generates `equals`/`hashCode`/`toString`/`copy` |
| `object` (singleton) | `jedynak` | "only child" — the single instance | Polish-influenced Lach form |
| `interface` | `predpis` | "regulation/spec" | |
| `fun` | `robota` | "work/job" — a unit of work | `robota mejno(...) : Typ { }` |
| `val` | `toz` | Discourse marker "so/well then"; a settled binding | Immutable |
| `var` | `mozej` | "may (change)" | Mutable |
| `return` | `davaj` | "give it here" | |
| `if` | `kaj` | "which/where/if" | |
| `else if` | `kajtez` | "and if also" | Chained branches |
| `else` | `boinak` | "otherwise" (*bo* + *inak*) | |
| `when` | `podle_teho` | "according to that" | Expression or statement |
| `for` | `prokazdy` | "for each" | `prokazdy (x v xs)` |
| `while` | `rubaj` | "keep hewing" — mining as looping | |
| `break` | `zdybat` | "to catch/stop" | |
| `continue` | `dalej` | "further on" | |
| `true` | `fajne` | "fine/good" | |
| `false` | `nyt` | "nope" | |
| `null` | `chuj` (alias `nic`) | Canonical null literal | See register note below |
| `&&` | `aj` | "and/also" | |
| `\|\|` | `ci` | "or" | |
| `in` | `v` | "in" — used in `prokazdy` loops | `prokazdy (x v zoznam)` |
| `try` | `pultik` | "the little bar counter" — where it all kicks off | |
| `catch` | `bitka` | "a brawl" — you caught one | `bitka (f: Flakanec) { }` |
| `finally` | `fajront` | "end of shift" — happens no matter what | |
| `throw` | `dostanes` | "you'll get one" | `dostanes Flakanec("...")` |
| `throws` | `rozdava` | "dishes them out" | On signature only; **not enforced** |
| test marker | `@Sichta` | "a shift" — a unit of testing work | Annotation on `robota` |
| test suite | `@Parta` | "a crew/work gang" | Annotation on `tryda` |

> `pravit` (`print`) and `zarvat` (`println`) are **prelude functions**, not reserved
> words — see the prelude table below. `this` is planned as `joch` but is **not in
> v0.1** (see `docs/language-spec.md` §12).

## Exception vocabulary

The exception keywords follow the **pub brawl metaphor** proposed in OSTRAJava issue #14:
you stand at the bar counter (*pultík*), someone dishes out a whack (*rozdává*), you get
one (*dostaneš*), a brawl breaks out (*bitka*), and eventually the shift ends regardless
(*fajront*). Krmelin adds `fajront` for `finally`, which the original proposal did not
cover.

| Kotlin | Krmelin | Metaphor |
|---|---|---|
| `Exception` | `Flakanec` | "a whack/slap" — the thing that hits you |
| `try` | `pultik` | standing at the bar counter |
| `catch` | `bitka` | a brawl breaking out |
| `finally` | `fajront` | end of shift — happens regardless |
| `throw` | `dostanes` | "you'll get one" |
| `throws` | `rozdava` | "dishes them out" |
| `NullPointerException` | `ChujovyFlakanec` | "the null whack" / "the botched whack" |
| `IndexOutOfBoundsException` | `MimoBarak` | "outside the building" |
| `ArithmeticException` | `DelenoNulou` | "divided by zero" |
| `NumberFormatException` | `ZlyDryst` | "bad drivel/parse error" |

## Prelude type aliases

The generated Kotlin prelude maps Krmelin type names to Kotlin. These are not keywords —
they are identifiers — but they are reserved for the prelude and should not be redefined.

| Krmelin | Kotlin | Notes |
|---|---|---|
| `Dryst` | `String` | "drivel/prattle" — a string of words |
| `Cyslo` | `Int` | "number" (dialect *číslo*) |
| `CysloDesetinne` | `Double` | "decimal number" |
| `Bul` | `Boolean` | shortened form |
| `Chachar` | `Char` | "rascal" — a single character |
| `Halda` | `List` | "a heap/pile" — a list |
| `Kupa` | `Map` | "a heap" — a map |
| `.naDryst()` | `.toString()` | "turn into drivel" |
| `.dylka` | `.length` / `.size` | "length" (dialect *délka*) |
| `pravit(x)` | `print(x)` | "to say" — no newline |
| `zarvat(x)` | `println(x)` | "to yell" — with newline |

## Register note on `chuj` and `nic`

`chuj` is the **canonical null literal** in Krmelin — it is the form used in the spec,
examples, error messages, and generated code. It is a Polish borrowing (related to the
Old Slavic root *xujь*) whose register in contemporary usage is harsh — nearer the
strongest English four-letter word than to "crap."

Krmelin makes this choice deliberately, in the tradition of OSTRAJava, which embraced the
same register. The vocabulary reflects the real dialect as spoken in Silesian mining
communities, not a sanitised version of it. `null` is structural rather than decorative
in any language — it appears in null checks, safe-call examples, and nullability
diagnostics — so making `chuj` canonical sets the register of the project's public face.
That is an intentional choice.

`nic` ("nothing" — a milder synonym, also genuine ostravština) is accepted everywhere
`chuj` is. The lexer maps both spellings to `TokenType.NULL` and emits identical Kotlin
`null`. Neither form is second-class. Use `nic` if you want the code to be appropriate
in a wider range of contexts; use `chuj` if you want the full ostravština register.

This register note appears once, here, and is not repeated elsewhere in the documentation.
