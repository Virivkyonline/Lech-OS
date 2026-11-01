# ADR-0003: Kompatibilita s Google službami

- **Stav:** Prijaté (2026-11-01)
- **Dátum:** 2026-10-31

## Kontext

Časť Android aplikácií vyžaduje Google Play Services (push notifikácie, poloha, prihlásenie, platby v aplikácii).
Bez riešenia by na LECH OS nefungovala významná časť bežne používaných aplikácií – čo je v rozpore s cieľom
„nechcem, aby polovica aplikácií nefungovala“. Zároveň nechceme, aby Google mal v systéme privilegované postavenie.

## Zvážené možnosti

1. **Žiadna kompatibilita** – najlepšie súkromie, neprijateľná kompatibilita.
2. **microG predinštalované** – dobrá kompatibilita, ale neúplná reimplementácia, ktorá vždy zaostáva; vyžaduje mechanizmus
   podvrhnutia podpisu.
3. **Google Play Services predinštalované s privilégiami** (ako bežný Android) – v rozpore s princípmi; navyše nie je
   možné bez licencie GMS.
4. **Google Play v sandboxe** – originálne Google Play Services si používateľ voliteľne nainštaluje ako **obyčajnú aplikáciu
   bez systémových privilégií**; systém poskytuje tenkú vrstvu kompatibility, aby fungovali v sandboxe.

## Rozhodnutie

- **Predvolený stav: bez Google.**
- Implementujeme vrstvu kompatibility pre **Google Play v sandboxe** (možnosť 4):
  - voľba v sprievodcovi prvým spustením s vysvetlením, čo znamená (aké údaje môže Google vidieť),
  - možnosť zapnúť ju len v **samostatnom profile** (napr. pracovný profil pre banku a firemné aplikácie),
  - Google aplikácie nikdy nie sú predinštalované a nikdy nedostanú systémové privilégiá,
  - kedykoľvek sa dajú úplne odinštalovať.
- microG zostáva alternatívou na vyhodnotenie (napr. ako voliteľný režim pre pokročilých používateľov), nie je predvolený.

## Dôsledky

- ✅ Najvyššia dosiahnuteľná kompatibilita bez kompromisu v systémových privilégiách.
- ✅ Používateľ sa rozhoduje sám a informovane (princíp P5).
- ❌ Vrstva kompatibility je netriviálna inžinierska práca a treba ju udržiavať s každou verziou Google Play Services.
- ❌ **Nerieši Play Integrity** – aplikácie vyžadujúce certifikované zariadenie budú naďalej problém
  ([docs/03](../03-nezavislost-od-google.md#play-integrity--najväčšie-riziko-kompatibility)).
- ⚠️ Potrebné právne posúdenie: spôsob, akým používateľ získa Google Play Services (LECH ich nesmie distribuovať bez
  povolenia; inštalácia musí prebiehať z oficiálneho zdroja), a podmienky používania Google.

## Kedy rozhodnutie prehodnotiť

- Ak Google technicky znemožní beh Play Services bez privilégií.
- Ak sa UnifiedPush a ďalšie otvorené alternatívy rozšíria natoľko, že vrstva kompatibility prestane byť potrebná pre väčšinu aplikácií.
