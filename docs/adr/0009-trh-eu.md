# ADR-0009: Cieľový trh je celá EÚ

- **Stav:** Prijaté (2026-11-04) – rozhodnutie vlastníka projektu
- **Dátum:** 2026-11-04
- **Dopĺňa:** [ADR-0007](0007-prvy-trh-firmy.md) (prvý trh: firmy a organizácie) o geografický rozsah

## Kontext

Doterajšie dokumenty počítali najmä so Slovenskom a Českom – jazyky, zoznam aplikácií, banky, výpočet trhu.
Vlastník projektu chce, aby LECH OS mieril na **celú Európsku úniu**, nielen na Slovensko.

Pre EÚ hovorí aj samotný produkt: hlavné argumenty LECH OS – GDPR, Akt o kybernetickej odolnosti (CRA), AI Act,
digitálna suverenita a infraštruktúra v EÚ – platia rovnako vo všetkých členských štátoch.

## Rozhodnutie

1. **Cieľový trh je EÚ od začiatku.** Produkt, ceny, zmluvy, dokumentácia a infraštruktúra sa navrhujú pre celú EÚ:
   angličtina ako spoločný jazyk produktu a zmlúv, ceny v eurách, servery v EÚ, súlad s pravidlami EÚ.
2. **Uvádzanie na trh v poradí krajín** (začať tam, kde vieme najrýchlejšie získať referencie, potom rozširovať):

   | Vlna | Krajiny | Prečo |
   |------|---------|-------|
   | 1 – pilot | Slovensko, Česko | Domáci trh, jazyk, rýchla spätná väzba, prvé referencie |
   | 2 – uvedenie v EÚ | Nemecko, Rakúsko, Benelux | Veľký trh firiem, vysoké povedomie o súkromí, prísne uplatňovanie GDPR |
   | 3 – rozšírenie | Francúzsko, Poľsko, severské krajiny, ďalšie | Podľa partnerov a výsledkov vlny 2 |

   Poradie vĺn 2 a 3 je odporúčanie, nie pevné rozhodnutie – môže sa zmeniť podľa partnerov alebo pilotných zákazníkov
   z iných krajín.
3. **Jazyky** (systém, LECH AI vrátane hlasu, podpora):

   | Vlna | Jazyky |
   |------|--------|
   | 1 | angličtina, slovenčina, čeština |
   | 2 | nemčina, francúzština, poľština |
   | 3 | ďalšie úradné jazyky EÚ podľa trhov |

   **Odlíšenie:** veľkí poskytovatelia AI obsluhujú menšie jazyky EÚ slabšie. Kvalitná lokálna AI v menších jazykoch
   (slovenčina, čeština, poľština, maďarčina, …) je pre LECH OS príležitosť v celej EÚ.
4. **Kompatibilita a banky po krajinách:** zoznam cieľových aplikácií = spoločné celoeurópske aplikácie +
   najpoužívanejšie aplikácie v každej krajine aktuálnej vlny; banky sa mapujú pre každú krajinu zvlášť.

## Dôsledky

- ✅ Väčší trh pre investorov aj granty EÚ; argumenty produktu platia v celej EÚ.
- ✅ Spoločná regulácia (GDPR, CRA, AI Act) – jeden súlad pre všetky krajiny.
- ❌ Viac jazykov pre systém aj AI – vyššie náklady na lokalizáciu, testovanie a podporu.
- ❌ Banky a obľúbené aplikácie sa líšia v každej krajine – kompatibilitné testy a rokovania sa násobia.
- ❌ Ochrana spotrebiteľa, jazykové požiadavky na informácie o produkte a predaj zariadení sa riešia aj podľa
  národných pravidiel; DPH pri predaji na diaľku (OSS).
- Ďalšie kroky: výber krajín vlny 2 podľa prvých rozhovorov s firmami; ochranná známka na úrovni EÚ (EUIPO).

## Kedy rozhodnutie prehodnotiť

- Ak sa objaví partner alebo zákazník, ktorý odôvodní iné poradie krajín.
- Ak náklady na jazyky a kompatibilitu prevýšia prínos – vtedy zúžiť vlnu 2.
