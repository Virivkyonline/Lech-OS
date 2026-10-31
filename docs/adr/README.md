# Záznamy architektonických rozhodnutí (ADR)

Každé zásadné rozhodnutie zapisujeme ako samostatný súbor. ADR sa nemaže ani neprepisuje – ak sa rozhodnutie
zmení, vznikne nový ADR, ktorý pôvodný **nahrádza**, a pôvodný dostane stav *Nahradené ADR-XXXX*.

## Stavy

- **Navrhnuté** – čaká na pripomienky a potvrdenie vlastníkom projektu.
- **Prijaté** – platí.
- **Zamietnuté** – zvážené a odmietnuté (ostáva ako záznam, prečo).
- **Nahradené ADR-XXXX** – už neplatí.

## Zoznam

| ADR | Názov | Stav |
|-----|-------|------|
| [0001](0001-obchodna-cesta.md) | Obchodná cesta: A → A+ → B | Navrhnuté |
| [0002](0002-zaklad-systemu-aosp.md) | Základ systému: AOSP bez GMS | Navrhnuté |
| [0003](0003-kompatibilita-google-sluzieb.md) | Kompatibilita s Google službami | Navrhnuté |
| [0004](0004-nezavislost-od-ai-poskytovatela.md) | Nezávislosť od poskytovateľa AI | Navrhnuté |
| [0005](0005-vyvojove-zariadenie.md) | Vývojové zariadenia | Navrhnuté |

## Šablóna

```markdown
# ADR-XXXX: Názov rozhodnutia

- **Stav:** Navrhnuté
- **Dátum:** RRRR-MM-DD
- **Rozhoduje:** meno

## Kontext
Aký problém riešime a aké sú obmedzenia.

## Zvážené možnosti
1. Možnosť A – výhody, nevýhody
2. Možnosť B – výhody, nevýhody

## Rozhodnutie
Čo sme sa rozhodli urobiť.

## Dôsledky
Čo z rozhodnutia vyplýva – pozitívne aj negatívne. Čo musíme urobiť ďalej.

## Kedy rozhodnutie prehodnotiť
Podmienky, za ktorých sa k rozhodnutiu vrátime.
```
