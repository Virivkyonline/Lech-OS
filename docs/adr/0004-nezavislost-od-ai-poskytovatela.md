# ADR-0004: Nezávislosť od poskytovateľa AI

- **Stav:** Prijaté (2026-11-01)
- **Dátum:** 2026-10-31

## Kontext

LECH AI je hlavná konkurenčná výhoda. Ak by bola postavená na jednom komerčnom AI poskytovateľovi, prevzali by sme
jeho ceny, podmienky, výpadky, rozhodnutia o modeloch aj prístup k údajom používateľov. To je v rozpore s princípom P9.

## Rozhodnutie

1. **Lokálny model je predvolený.** Všetky základné funkcie musia fungovať offline.
2. **Jedno interné rozhranie `AiProvider`** pre všetky modely a poskytovateľov:
   - lokálny model v telefóne,
   - LECH AI servery (vlastná infraštruktúra v EÚ s open-weight modelmi),
   - externí poskytovatelia – voliteľní, zapínaní používateľom jednotlivo.
3. **Kľúčová logika nie je v modeli.** Register akcií, pravidlá súhlasu, triedy údajov a ochrana proti prompt injection
   sú deterministický kód v `lech.ai.policy` ([docs/04](../04-lech-ai.md)). Výmena modelu nemení bezpečnostné správanie.
4. **Žiadne API špecifické pre poskytovateľa v jadre systému.** Prispôsobenie konkrétnemu poskytovateľovi je len
   v adaptéri za rozhraním `AiProvider`.
5. **Vlastná hodnotiaca sada** (SK/CZ príkazy, plánovanie akcií, odolnosť voči prompt injection, latencia) rozhoduje
   o tom, ktorý model použijeme. Každá výmena modelu musí sadou prejsť.
6. **Preferujeme open-weight modely s permisívnou licenciou** (Apache 2.0, MIT), aby sme ich mohli prevádzkovať
   a dolaďovať sami.

## Dôsledky

- ✅ Model aj poskytovateľa vieme vymeniť bez zásahu do systému a bez zmeny správania voči používateľovi.
- ✅ Firmy môžu prevádzkovať AI na vlastnej infraštruktúre (B2B).
- ❌ Lokálne modely sú slabšie ako najväčšie cloudové – kompenzujeme dobrým návrhom akcií, doladením a voliteľnou cloudovou vrstvou.
- ❌ Náklady na prevádzku vlastných AI serverov – riešia sa predplatným LECH Cloud.

## Kedy rozhodnutie prehodnotiť

- Nikdy v princípe (nezávislosť je základný princíp); prehodnocujeme len výber konkrétnych modelov a poskytovateľov.
