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

## Quick example

```
sachta demo

robota rynek() {
    zarvat("Toz vitaj, Krmelin!")
}
```

compiles to:

```kotlin
package demo

fun main() {
    println("Toz vitaj, Krmelin!")
}
```

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

See [docs/keywords.md](docs/keywords.md) for the full table.

---

## Building from source

Requires JDK 21.

```sh
./gradlew build
```

The shadow jar lands at `build/libs/krmelin-<version>.jar`.

## Running

```sh
# via the launcher script (after build):
cli/krmelin --help

# or directly (the shadow jar, not the plain/-slim.jar one):
java -jar build/libs/krmelin-<version>.jar --help
```

## Running tests

```sh
./gradlew test
```

---

## Status

| Milestone | Description                         | Status      |
|-----------|-------------------------------------|-------------|
| M1        | Scaffold + backend decision         | ✅ done     |
| M2        | Lexer and parser                    | 🚧 planned  |
| M3        | Core language semantics             | 🚧 planned  |
| M4        | Translation and runtime             | 🚧 planned  |
| M5        | PorubaUnit test framework           | 🚧 planned  |
| M6        | Flakanci examples                   | 🚧 planned  |
| M7        | Docs and samples                    | 🚧 planned  |
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
