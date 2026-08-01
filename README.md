# Krmelin

> **Kotlin, po našymu.** A satirical transpiler that speaks Ostrava dialect.

Krmelin is a programming language that looks like Kotlin, but every keyword has
been replaced with authentic **ostravština** — the short-voweled, penultimate-stress,
German/Polish-flavoured miners' slang of the Moravian-Silesian region. It is a
spiritual successor to Tomáš Kohout's
[OSTRAJava](https://github.com/tkohout/OSTRAJava), reimplemented clean-room,
targeting Kotlin instead of a custom VM.

The joke lives in the surface syntax. The compiler is real.

---

## Quick start

Requires JDK 21. Build from source:

```sh
./gradlew build
```

Write your first Krmelin program, `hello.krm`:

```krmelin
sachta demo

robota rynek() {
    zarvat("Toz vitaj, Krmelin!")
}
```

Run it:

```sh
java -jar build/libs/krmelin-*.jar run hello.krm
```

Output:

```
Toz vitaj, Krmelin!
```

Inspect the generated Kotlin:

```sh
java -jar build/libs/krmelin-*.jar compile hello.krm -o hello.kt
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

Run all tests under `tests/`:

```sh
java -jar build/libs/krmelin-*.jar test
```

Output:

```
PorubaUnit — šichta začíná
  ✓ secteni_funguje          (0 ms)

Fajront: 1 prošlo, 0 spadlo, 0 chyb — za 3 ms
```

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
krmelin fmt <file|dir>      Format source          (--check, --write)
krmelin repl                Interactive REPL       (--load)
```

Global flags: `--version`, `--help`, `--no-color`, `-v/--verbose`.

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/language-spec.md`](docs/language-spec.md) | Full language reference, EBNF, diagnostic codes |
| [`docs/keywords.md`](docs/keywords.md) | All keywords with dialect sources and register notes |
| [`docs/examples.md`](docs/examples.md) | Real-life Ostrava-themed example programs |
| [`docs/porubaunit.md`](docs/porubaunit.md) | PorubaUnit test framework reference |

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
| M8        | Tests and error-message polish      | 🚧 planned  |
| M9        | Release                             | 🚧 planned  |

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
