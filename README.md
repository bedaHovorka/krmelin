# Krmelin

> **Kotlin, po našymu.** A satirical transpiler that speaks Ostrava dialect.

> *Protože běžní Ostraváci nebudou mít na drahou AI, bude muset vzniknout jim
> blízký jazyk přeložitelný do JVM bytecodu, binárky i JavaScriptu.*
>
> — Because ordinary Ostravians won't be able to afford expensive AI, a language
> close to them will have to come into being — one translatable to JVM bytecode,
> to a binary, and to JavaScript.

Krmelin is a programming language that looks like Kotlin, but every keyword has
been replaced with authentic **ostravština** — the short-voweled, penultimate-stress,
German/Polish-flavoured miners' slang of the Moravian-Silesian region. It is a
spiritual successor to Tomáš Kohout's
[OSTRAJava](https://github.com/tkohout/OSTRAJava), reimplemented clean-room,
targeting Kotlin instead of a custom VM.

The joke lives in the surface syntax. The compiler is real.

---

## Install

Krmelin needs **JDK 21** and nothing else — the Kotlin compiler is bundled inside the
jar, so there is no `kotlinc` to install.

Download `krmelin-0.1.0.jar` from the
[latest release](https://github.com/bedaHovorka/krmelin/releases/latest) and run it:

```sh
java -jar krmelin-0.1.0.jar run hello.krm
```

Or build from source:

```sh
./gradlew build
```

which produces the same fat jar in `build/libs/`. The `cli/krmelin` launcher finds it for
you, so the rest of this README uses that:

```sh
cli/krmelin run hello.krm
```

(Set `KRMELIN_JAR=/path/to/krmelin-0.1.0.jar` to point the launcher at a downloaded jar
instead of a locally built one.)

---

## Quick start

Write your first Krmelin program, `hello.krm`:

```krmelin
sachta demo

robota rynek() {
    zarvat("Toz vitaj, Krmelin!")
}
```

Run it:

```sh
cli/krmelin run hello.krm
```

Output:

```
Toz vitaj, Krmelin!
```

Inspect the generated Kotlin:

```sh
cli/krmelin compile hello.krm -o hello.kt
cat hello.kt
```

```kotlin
package demo

fun main() {
    println("Toz vitaj, Krmelin!")
}
```

---

## More examples

**KobzoleBuzz** — the FizzBuzz of Ostrava (*kobzola* = potato):

```krmelin
robota rynek() {
    mozej i = 1
    rubaj (i <= 15) {
        podle_teho {
            i % 15 == 0 -> zarvat("KobzoleBuzz")
            i % 3 == 0  -> zarvat("Kobzole")
            i % 5 == 0  -> zarvat("Buzz")
            boinak      -> zarvat(i.naDryst())
        }
        i = i + 1
    }
}
```

**Data class + null safety:**

```krmelin
zapisnik tryda Havir(toz mejno: Dryst, mozej odrubano: Cyslo)

robota delka(s: Dryst?) : Cyslo {
    davaj s?.dylka ?: 0
}
```

**Exceptions — the pub brawl:**

```krmelin
robota rynek() {
    pultik {
        dostanes Flakanec("bum!")
    } bitka (f: Flakanec) {
        zarvat("dostal sem: ${f.zprava}")
    } fajront {
        zarvat("fajront, chlapi")
    }
}
```

See [`docs/examples.md`](docs/examples.md) for a full set of Ostrava-themed programs,
and [`examples/`](examples/) for runnable source files.

---

## Keyword cheat-sheet

| Kotlin     | Krmelin       | Meaning                          |
|------------|---------------|----------------------------------|
| `fun`      | `robota`      | "work/job"                       |
| `class`    | `tryda`       | dialect for *třída*              |
| `val`      | `toz`         | "so/well then" — a settled thing |
| `var`      | `mozej`       | "may change"                     |
| `if`       | `kaj`         | "which/where/if"                 |
| `else`     | `boinak`      | "otherwise"                      |
| `while`    | `rubaj`       | "keep hewing"                    |
| `return`   | `davaj`       | "give it here"                   |
| `true`     | `fajne`       | "fine/good"                      |
| `false`    | `nyt`         | "nope"                           |
| `null`     | `chuj`        | canonical null (also `nic`)      |
| `println`  | `zarvat`      | "to yell"                        |
| `try`      | `pultik`      | "the little bar counter"         |
| `catch`    | `bitka`       | "a brawl"                        |
| `finally`  | `fajront`     | "end of shift"                   |

See [`docs/keywords.md`](docs/keywords.md) for the full table with dialect sources.

---

## Testing with PorubaUnit

PorubaUnit is Krmelin's built-in test framework — named after Poruba, a large
district of Ostrava. Annotate tests with `@Sichta` ("a shift"):

```krmelin
@Sichta robota secteni_funguje() {
    musi_byt(1 + 1, 2)
    je_fajne(2 > 1)
    je_chuj(chuj)
}
```

Point `krmelin test` at a file or a directory of Krmelin sources:

```sh
cli/krmelin test examples/porubaunit_demo.krm
```

Output:

```
PorubaUnit — šichta začíná
  ✓ FlakanciParty.deleni_nulou_rozdava        (1 ms)
  ✓ FlakanciParty.zly_dryst_ma_dostat         (0 ms)
  ✓ FlakanciParty.mimo_barak_ma_dostat        (0 ms)
  ✓ FlakanciParty.chujovy_flakanec_ma_dostat  (0 ms)
  ✓ FlakanciParty.flakanec_sobo_zpravu_nese   (0 ms)
  ✓ dvojka_sedi                               (0 ms)
  ✓ bulovstina_sedi                           (0 ms)
  ✓ blizky_cysla_se_sejzou                    (0 ms)

Fajront: 8 prošlo, 0 spadlo, 0 chyb — za 6 ms
```

With no path it defaults to `tests/`. In *this* repository that directory holds the
compiler's own golden and diagnostic corpora — including sources that are deliberately
uncompilable — so a bare `cli/krmelin test` here exits `2` by design. In your own project,
`tests/` is the natural place for `@Sichta` files.

See [`docs/porubaunit.md`](docs/porubaunit.md) for the full assertion API.

---

## Running compiler tests

```sh
./gradlew test
```

---

## CLI reference

```
krmelin compile <file>      Transpile .krm → .kt  (-o, --jar, --emit-only)
krmelin run <file>          Compile and execute    (--args, --keep)
krmelin test [path]         Run @Sichta tests      (--filter, --verbose)
krmelin fmt <file|dir>      Format source          (--check)
krmelin repl                Interactive REPL       (--load)
```

Global flags: `--version`, `--help`. (`-v/--verbose` is a `test` option; `--no-color` is
not implemented yet.)

`fmt` and `repl` ship as stubs in v0.1 and are in active development.

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/language-spec.md`](docs/language-spec.md) | Full language reference, EBNF, diagnostic codes |
| [`docs/keywords.md`](docs/keywords.md) | All keywords with dialect sources and register notes |
| [`docs/examples.md`](docs/examples.md) | Real-life Ostrava-themed example programs |
| [`docs/porubaunit.md`](docs/porubaunit.md) | PorubaUnit test framework reference |
| [`CHANGELOG.md`](CHANGELOG.md) | Release history and known limitations |

---

## Status

| Milestone | Description                         | Status      |
|-----------|-------------------------------------|-------------|
| M1        | Scaffold + backend decision         | ✅ done     |
| M2        | Lexer and parser                    | ✅ done     |
| M3        | Core language semantics             | ✅ done     |
| M4        | Translation and runtime             | ✅ done     |
| M5        | PorubaUnit test framework           | ✅ done     |
| M6        | Flakanci examples                   | ✅ done     |
| M7        | Docs and samples                    | ✅ done     |
| M8        | Tests and error-message polish      | ✅ done     |
| M9        | Release                             | ✅ done     |

**Non-goals for v0.1:** custom bytecode VM, coroutines, full generics inference,
operator overloading, IDE tooling (LSP/debugger), backward compatibility with
OSTRAJava syntax.

---

## Attribution

Krmelin is an independent, clean-room project inspired by the concept
pioneered by Tomáš Kohout's OSTRAJava (<https://github.com/tkohout/OSTRAJava>).
No source code from OSTRAJava is used, copied, or derived. Some keyword choices
are affectionate nods to design discussions in that project's issue tracker.
Krmelin is not endorsed by its author.

---

## License

MIT — see [LICENSE](LICENSE).
