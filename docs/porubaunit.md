# PorubaUnit — the Krmelin test framework

**PorubaUnit** is Krmelin's built-in xUnit-style testing framework. It is named after
**Poruba**, a large western district of Ostrava (~62,000 residents) — a unit of testing
named after a unit of the city. It is inspired by the community proposal in OSTRAJava
issue #15 but reimplemented clean-room with a simpler API and full dialect voice.

PorubaUnit has **no runtime dependency on JUnit**. It is self-contained so the output
format and dialect tone remain fully under Krmelin's control.

---

## Quick start

Annotate a parameterless `robota` with `@Sichta` and run `krmelin test`:

```
@Sichta robota secteni_funguje() {
    musi_byt(1 + 1, 2)
}
```

```sh
krmelin test
```

Output:

```
PorubaUnit — šichta začíná
  ✓ secteni_funguje          (0 ms)

Fajront: 1 prošlo, 0 spadlo, 0 chyb — za 3 ms
```

---

## Marking tests

### `@Sichta` — a single test

Place `@Sichta` on any parameterless top-level or class-member `robota`. The function
becomes a test case. Functions with parameters cannot be tests (the runner has no way to
supply arguments).

```
@Sichta robota pozdrav_vypise_jmeno() {
    // ...
}
```

### `@Parta` — a test suite

Place `@Parta` on a `tryda` or `zapisnik tryda` to group related tests. The class must
be constructible with no arguments (no constructor parameters). All `@Sichta` members of
a `@Parta` class are discovered and run as a suite.

```
@Parta tryda MathParty {
    @Sichta robota soucet() { ... }
    @Sichta robota rozdil() { ... }
}
```

### Discovery rules

- `krmelin test` scans `tests/**/*.krm` by default; pass a path to restrict scope.
- Discovery happens at code-generation time — no runtime reflection.
- A `@Sichta robota` inside a `tryda` that is not `@Parta` is never discovered (the
  resolver raises warning `HAV235`).
- `@Sichta` on `rynek` (the program entry point) is forbidden (`HAV232`).

---

## Assertion API

All assertions accept an optional trailing message (`zprava`) that appears in the failure
output.

### `musi_byt(actual, expected)`

Passes when `actual == expected`. Use for equality checks.

```
musi_byt(2 * 2, 4)
musi_byt(pozdrav("banik"), "Nazdar, banik!", "pozdrav by mel pozdravit")
```

### `nesmi_byt(actual, expected)`

Passes when `actual != expected`. Use to assert two values differ.

```
nesmi_byt(2, 3)
```

### `je_fajne(podminka)`

Passes when `podminka` is `fajne` (`true`). Use for boolean conditions.

```
je_fajne(2 > 1)
je_fajne(mejno.dylka > 0)
```

### `je_nyt(podminka)`

Passes when `podminka` is `nyt` (`false`). The negated twin of `je_fajne`.

```
je_nyt(1 > 2)
je_nyt(seznam.dylka == 0)
```

### `je_chuj(x)`

Passes when `x` is `chuj` (null). Use to assert absence of a value.

```
je_chuj(hledej("neni_tu"))
```

### `neni_chuj(x)`

Passes when `x` is not `chuj`. Use to assert presence of a value.

```
neni_chuj(hledej("je_tu"))
```

### `ma_dostat { ... }`

Passes when the lambda body throws any `Flakanec`. Use to test that error conditions
raise exceptions.

```
ma_dostat { dostanes DelenoNulou("bum") }
ma_dostat { parsuj("ne_cyslo") }
```

### `blizko(a, b, tolerance)`

Passes when `|a - b| <= tolerance`. Use for floating-point comparisons where exact
equality is fragile.

```
blizko(3.14, 3.14159, 0.01)
blizko(vysledek, 0.333, 0.001)
```

---

## Complete example

```
@Parta tryda StringParty {
    @Sichta robota dylka_prazdneho_je_nula() {
        musi_byt("".dylka, 0)
    }

    @Sichta robota naDryst_cysla() {
        musi_byt(42.naDryst(), "42")
    }
}

@Sichta robota bul_algebra() {
    je_fajne(fajne aj fajne)
    je_nyt(fajne aj nyt)
    je_nyt(nyt ci nyt)
    je_fajne(nyt ci fajne)
}

@Sichta robota chuj_bezpecnost() {
    toz s: Dryst? = chuj
    je_chuj(s)
    musi_byt(s?.dylka ?: 0, 0)
}
```

---

## Running tests

```sh
# Run all tests under tests/
krmelin test

# Run a specific file
krmelin test tests/math_test.krm

# Run tests matching a glob
krmelin test --filter "*Math*"

# Verbose — show each @Sichta name even when passing
krmelin test --verbose
```

---

## Output format

### All tests pass

```
PorubaUnit — šichta začíná
  ✓ dylka_prazdneho_je_nula          (0 ms)
  ✓ naDryst_cysla                    (1 ms)
  ✓ bul_algebra                      (0 ms)
  ✓ chuj_bezpecnost                  (0 ms)

Fajront: 4 prošly, 0 spadlo, 0 chyb — za 11 ms
```

### A test fails (assertion failure)

```
PorubaUnit — šichta začíná
  ✓ dylka_prazdneho_je_nula          (0 ms)
  ✗ naDryst_cysla                    (1 ms)
      musi_byt spadlo v tests/math_test.krm:8:9
      čekal sem:  "42"
      dostal sem: "43"

Fajront: 1 prošla, 1 spadla, 0 chyb — za 8 ms
```

### A test errors (uncaught exception)

An **error** (`!`) is distinct from a **failure** (`✗`): an error means the test threw
an unexpected `Flakanec`, not a deliberate assertion failure.

```
PorubaUnit — šichta začíná
  ! deleni_nulou_vraci_neco          (0 ms)
      dostal ju: DelenoNulou v tests/math_test.krm:4

Fajront: 0 prošlo, 0 spadlo, 1 chyba — za 4 ms
```

### Exit codes

| Exit code | Meaning |
|---|---|
| `0` | All tests passed |
| `1` | At least one failure or errored test |
| `2` | Test sources failed to compile |

---

## Isolation and ordering

- Each `@Sichta` runs in isolation — one failed assertion fails only that test and the
  runner continues.
- An uncaught `Flakanec` (or any JVM throwable) in a test body is counted as an
  **error**, not a failure.
- Test order is **deterministic**: sorted by file path, then declaration order within
  a file.

---

## Backend note

`krmelin test` transpiles the test sources to Kotlin, generates a `KrmelinTestMain.kt`
registry of all discovered `@Sichta` functions, compiles the bundle, and runs it. The
PorubaUnit runtime is included in the output directory alongside the transpiled sources.
No JUnit installation is required.
