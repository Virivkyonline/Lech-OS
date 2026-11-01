# ADR-0005: Vývojové zariadenia

- **Stav:** Prijaté (2026-11-01)
- **Dátum:** 2026-10-31

## Kontext

Na začiatok potrebujeme jeden testovací telefón určený výhradne na vývoj. Kandidáti: Google Pixel, Fairphone,
Nothing Phone. Podrobné porovnanie je v [docs/07](../07-zariadenia.md).

## Rozhodnutie

1. **Cuttlefish** (virtuálne zariadenie AOSP) ako prvé „zariadenie“ – od roku 2025 je to oficiálny referenčný cieľ AOSP,
   umožňuje vývoj a automatické testy bez fyzického telefónu.
2. **Google Pixel** ako prvý fyzický telefón – **2 kusy** rovnakého modelu (Pixel 9 alebo novší s 12+ GB RAM,
   verzia bez operátorského zámku, kúpené v EÚ).
3. **Fairphone** ako druhá platforma v etape 7 – overenie prenositeľnosti a začiatok rozhovoru o partnerstve (cesta A+).
4. **Nothing** a ďalší výrobcovia (napr. Motorola) – prehodnotiť neskôr.

## Zdôvodnenie

- Pixel je jediný z kandidátov, na ktorom je **zamknutie bootloadera s vlastnými kľúčmi** dlhodobo overené v praxi
  a ktorý má samostatný bezpečnostný čip a 7 rokov aktualizácií (Pixel 8 a novšie).
- Fairphone je oficiálne otvorený vlastným systémom a spolupracuje so súkromnými projektmi – je to prirodzený kandidát
  na partnera. Zamknutie bootloadera s iným systémom však výrobca neodporúča a komunita hlási problémy na Fairphone 6,
  preto nie je vhodný ako prvé vývojové zariadenie.
- 12+ GB RAM je potrebných kvôli lokálnym AI modelom.

## Dôsledky

- ✅ Najrýchlejšia cesta k bezpečnému prototypu.
- ❌ Device Support Package pre Pixel udržiavame sami (Google od Android 16 nezverejňuje device trees Pixel v AOSP).
- ❌ Pixel nie je vhodný ako partner pre cestu A+ / B – preto musí byť DSP od začiatku oddelený a druhá platforma prísť včas.

## Kedy rozhodnutie prehodnotiť

- Ak Google znemožní zamknutie bootloadera s vlastnými kľúčmi na nových modeloch Pixel.
- Ak niektorý výrobca ponúkne partnerstvo s technickou podporou (firmvér, zamknutie bootloadera, prístup k záplatám).
