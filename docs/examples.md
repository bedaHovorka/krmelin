# Krmelin examples

Real programs in Krmelin — inspired by everyday life in Ostrava and the
Moravian-Silesian Region.

---

## Hello, Krmelin!

The obligatory first program. `rynek` is the entry point (Kotlin `main`), `zarvat`
prints a line.

```krmelin
sachta demo

robota rynek() {
    zarvat("Toz vitaj, Krmelin!")
}
```

Compiled Kotlin:

```kotlin
package demo

fun main() {
    println("Toz vitaj, Krmelin!")
}
```

---

## Šichta na dole — a miner's shift counter

Miners in Ostrava work in shifts (*šichty*). This program counts how many shifts a
miner has worked and whether they've earned their weekend.

```krmelin
sachta doly

zapisnik tryda Haviř(toz mejno: Dryst, mozej sichty: Cyslo)

robota zhodnotSichtu(haviř: Haviř) {
    haviř.sichty = haviř.sichty + 1
    zarvat("${haviř.mejno} ma ted ${haviř.sichty} sichtu.")
    kaj (haviř.sichty >= 5) {
        zarvat("${haviř.mejno}: fajront, chlapi — vikend zaslouzeny!")
    } boinak {
        toz zbyva = 5 - haviř.sichty
        zarvat("${haviř.mejno}: furt jeste ${zbyva} do fajrontu.")
    }
}

robota rynek() {
    mozej franta = Haviř("Franta", 3)
    zhodnotSichtu(franta)
    zhodnotSichtu(franta)
    zhodnotSichtu(franta)
}
```

---

## FizzBuzz — KobzoleBuzz

The classic puzzle, re-cast in ostravština. *Kobzola* = potato (a local staple).

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

Expected output:

```
1
2
Kobzole
4
Buzz
Kobzole
7
8
Kobzole
Buzz
11
Kobzole
13
14
KobzoleBuzz
```

---

## Data class — zapisnik tryda

`zapisnik tryda` maps to Kotlin `data class`: it generates `equals`, `hashCode`,
`toString`, and `copy` automatically.

```krmelin
zapisnik tryda Haviř(toz mejno: Dryst, mozej odrubano: Cyslo)

robota pozdrav(mejno: Dryst = "cype") {
    zarvat("Nazdar, $mejno!")
}
```

Compiled Kotlin:

```kotlin
data class Haviř(val mejno: String, var odrubano: Int)

fun pozdrav(mejno: String = "cype") {
    println("Nazdar, $mejno!")
}
```

---

## Null safety — chuj a safe-call

`chuj` is the canonical null literal. Safe-call (`?.`) and Elvis (`?:`) work as in
Kotlin.

```krmelin
robota delka(s: Dryst?) : Cyslo {
    davaj s?.dylka ?: 0
}
```

Compiled Kotlin:

```kotlin
fun delka(s: String?): Int {
    return s?.length ?: 0
}
```

Usage:

```krmelin
robota rynek() {
    musi_byt(delka("Ostrava"), 7)
    musi_byt(delka(chuj), 0)
}
```

---

## Výpočet mzdy — computing a miner's wage

Demonstrates `mozej`, arithmetic, `kaj`/`kajtez`/`boinak`, and `davaj`.

```krmelin
sachta mzda

robota vypocetMzdy(hodiny: Cyslo, hodinovaSazba: Cyslo) : Cyslo {
    kaj (hodiny > 40) {
        toz precase = hodiny - 40
        davaj (40 * hodinovaSazba) + (precase * hodinovaSazba * 2)
    } kajtez (hodiny > 0) {
        davaj hodiny * hodinovaSazba
    } boinak {
        davaj 0
    }
}

robota rynek() {
    toz mzda = vypocetMzdy(45, 120)
    zarvat("Mzda za 45 hodin: ${mzda} Kč")
}
```

---

## Hierarchie Flakanci — the pub brawl

Full exception demo: nested `pultik`, multiple `bitka` clauses, rethrow, and `fajront`
on every path. Based on `examples/flakanci.krm`.

```krmelin
robota hazej(kolo: Cyslo) rozdava Flakanec {
    kaj (kolo == 1) {
        dostanes DelenoNulou("deleni nulou v kole 1")
    } kajtez (kolo == 2) {
        dostanes MimoBarak("mimo barak v kole 2")
    } kajtez (kolo == 3) {
        dostanes ZlyDryst("zly dryst v kole 3")
    }
}

robota jednoKolo(kolo: Cyslo) {
    pultik {
        hazej(kolo)
    } bitka (f: DelenoNulou) {
        zarvat("zluknul sem DelenoNulou — ${f.zprava}")
    } bitka (f: MimoBarak) {
        zarvat("zluknul sem MimoBarak — ${f.zprava}")
        dostanes f
    } bitka (f: Flakanec) {
        zarvat("obecny Flakanec — ${f.zprava}")
    } fajront {
        zarvat("Kolo ${kolo}: fajront")
    }
}

robota rynek() {
    pultik {
        jednoKolo(1)
        jednoKolo(2)
    } bitka (f: MimoBarak) {
        zarvat("Vnejsi bitka: chytil sem ${f.zprava}")
    } fajront {
        zarvat("Vnejsi fajront")
    }
    jednoKolo(3)
}
```

`rozdava Flakanec` annotates the function signature as potentially throwing (it maps to
Kotlin `@Throws(Flakanec::class)`). It is **never enforced** — calling a `rozdava`
function without a `pultik` is legal.

---

## PorubaUnit tests — @Sichta a @Parta

```krmelin
@Parta tryda MathParty {
    @Sichta robota scitani_funguje() {
        musi_byt(2 + 2, 4)
        musi_byt(10 + (-3), 7)
    }

    @Sichta robota odcitani_funguje() {
        nesmi_byt(5 - 3, 3)
        musi_byt(5 - 3, 2)
    }

    @Sichta robota deleni_nulou_rozdava() {
        ma_dostat { dostanes DelenoNulou("bum") }
    }
}

@Sichta robota pravda_je_fajne() {
    je_fajne(fajne)
    je_nyt(nyt)
    je_chuj(chuj)
    neni_chuj("neco")
}
```

Run with:

```sh
krmelin test
```

---

## FC Baník Ostrava — jedynak singleton

`jedynak` declares a **singleton** (Kotlin `object`). Here it models the legendary
Ostrava football club.

```krmelin
jedynak FCBanik {
    toz nazev: Dryst = "FC Baník Ostrava"
    toz zalozen: Cyslo = 1922
    mozej tituly: Cyslo = 4

    robota predstav() {
        zarvat("${nazev}, zalozeny ${zalozen}, ${tituly}x mistri Ceskoslovenska.")
    }
}

robota rynek() {
    FCBanik.predstav()
    zarvat("Dalsi titul pribude, uz brzo!")
}
```

---

## Pivnice — a Ostrava pub interface

`predpis` declares an interface. This models the essential services of an Ostrava pub.

```krmelin
predpis Pivnice {
    robota natocPivo(druh: Dryst) : Dryst
    robota zaplatit(castka: Cyslo) : Bul
}

tryda UKrameriho {
    robota natocPivo(druh: Dryst) : Dryst {
        davaj "Jedno ${druh}, prosim!"
    }

    robota zaplatit(castka: Cyslo) : Bul {
        zarvat("Platim ${castka} Kc.")
        davaj fajne
    }
}

robota rynek() {
    toz hospoda = UKrameriho()
    toz objednavka = hospoda.natocPivo("Radegast")
    zarvat(objednavka)
    hospoda.zaplatit(35)
}
```

---

## Pozdrav se jmenem — default parameters

Default parameter values let callers omit arguments they do not need to override.

```krmelin
robota pozdrav(mejno: Dryst = "chlape", mesta: Dryst = "Ostrave") {
    zarvat("Ahoj, ${mejno}! Vitaj v ${mesta}!")
}

robota rynek() {
    pozdrav()
    pozdrav("Franta")
    pozdrav("Franta", "Porube")
}
```

Expected output:

```
Ahoj, chlape! Vitaj v Ostrave!
Ahoj, Franta! Vitaj v Ostrave!
Ahoj, Franta! Vitaj v Porube!
```

---

## Nullable dispatch — zpracuj zpravu

Safe-call and elvis together, showing how nullable types flow through functions.

```krmelin
robota zpracujZpravu(zprava: Dryst?) {
    toz text = zprava ?: "zadna zprava"
    zarvat("Zprava: ${text}")
}

robota rynek() {
    zpracujZpravu(chuj)
    zpracujZpravu("Toz vitaj z Krmelin!")
}
```

Expected output:

```
Zprava: zadna zprava
Zprava: Toz vitaj z Krmelin!
```

---

## Loop with break and continue — zdybat a dalej

`zdybat` breaks out of a loop; `dalej` continues to the next iteration.

```krmelin
robota rynek() {
    mozej i = 0
    rubaj (i < 10) {
        i = i + 1
        kaj (i % 2 == 0) {
            dalej
        }
        kaj (i > 7) {
            zdybat
        }
        zarvat(i.naDryst())
    }
}
```

Expected output (odd numbers up to 7):

```
1
3
5
7
```

---

See [`examples/`](../examples/) for the full source files, and
[`tests/golden/`](../tests/golden/) for the corresponding Kotlin output.
