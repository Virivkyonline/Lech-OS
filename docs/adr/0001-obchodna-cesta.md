# ADR-0001: Obchodná cesta – najprv vlastný OS (A), architektúra pripravená na vlastný telefón (B)

- **Stav:** Navrhnuté – čaká na potvrdenie vlastníkom projektu
- **Dátum:** 2026-10-31

## Kontext

Zvažované sú dve obchodné cesty:

- **A. Vlastná značka operačného systému** – LECH OS ako samostatný produkt inštalovateľný na podporované telefóny
  rôznych výrobcov.
- **B. Vlastný operačný systém a neskôr vlastné telefóny** – LECH OS a v ďalšej fáze vlastný mobil pod rovnakou značkou.

Požiadavka: architektúra má umožniť obidve cesty a projekt nesmie byť závislý od Google, Apple ani jedného
poskytovateľa AI.

## Zvážené možnosti

### Iba A – OS pre telefóny iných výrobcov

- ✅ Nízke počiatočné náklady, žiadne riziko skladových zásob a výroby.
- ✅ Rýchle overenie záujmu trhu.
- ❌ Inštalácia vlastného systému je pre bežného používateľa bariéra → menší trh.
- ❌ Závislosť od výrobcov: firmvér, zamknutie bootloadera, dĺžka podpory, zmeny politiky (napr. koniec device trees Pixel v AOSP).
- ❌ Bez partnerstva s výrobcom nemáme prístup k embargovaným bezpečnostným záplatám.

### Hneď B – vlastný telefón

- ✅ Plná kontrola nad hardvérom, firmvérom a dĺžkou podpory; silná značka.
- ❌ Vysoké náklady (vývoj s ODM výrobcom, certifikácie CE/RED, ekodizajn, zásoby, servis, záruky) – rádovo vyššie
  ako vývoj samotného systému.
- ❌ Obrovské riziko: ak systém ešte nie je vyladený, zlé recenzie zasiahnu značku aj hardvér.
- ❌ Ani vlastný telefón nerieši Play Integrity (to by vyriešila len certifikácia GMS, ktorá je v rozpore s princípmi).

### A → A+ → B (postupná cesta)

- **A:** LECH OS pre malý zoznam podporovaných telefónov (začíname Pixelom), webový inštalátor.
- **A+:** predaj telefónov s **predinštalovaným LECH OS** pod značkou LECH – v spolupráci s partnerským výrobcom
  (napr. Fairphone) alebo s repasovanými telefónmi. Takto už funguje napr. Murena s /e/OS.
- **B:** vlastný telefón s ODM výrobcom, až keď máme overený produkt, zákazníkov a financovanie.

## Rozhodnutie

**Volíme postupnú cestu A → A+ → B.**

Architektúra je od začiatku navrhnutá tak, aby prechod medzi fázami nevyžadoval prepisovanie systému:

1. Všetko, čo závisí od konkrétneho telefónu, je v samostatnom **Device Support Package**
   ([docs/02](../02-architektura.md#device-support-package--kľúč-k-cestám-a-aj-b)). Vlastný telefón je pre systém
   „len ďalší DSP“.
2. **Značka, kľúče a infraštruktúra** (aktualizácie, Store, Cloud) sú od začiatku naše – nezávislé od výrobcu.
3. Od prvého zariadenia platia **rovnaké požiadavky**, ktoré budeme klásť na vlastný telefón (zamknuteľný bootloader
   s vlastnými kľúčmi, hardvérové úložisko kľúčov, dlhodobý firmvér).

## Dôsledky

- ✅ Prvé investície idú do softvéru, ktorý je skutočným odlíšením (súkromie + integrovaná AI).
- ✅ Fáza A+ prináša príjem a skúsenosti s predajom, podporou a reguláciou zariadení ešte pred vlastným hardvérom.
- ✅ Partnerstvo s výrobcom vo fáze A+ môže priniesť skorší prístup k bezpečnostným záplatám a firmvéru.
- ❌ Vo fáze A sme závislí od politiky Google voči Pixel (riziko R3 v [docs/10](../10-rizika-a-otvorene-otazky.md)) –
  preto druhá platforma (Fairphone) v etape 7.
- ❌ Vo fáze A+ sa stávame distribútorom alebo výrobcom → regulačné povinnosti ([docs/09](../09-pravo-a-licencie.md)).
- Ďalší krok: začať rozhovory s potenciálnymi partnermi pre A+ najneskôr počas etapy 7.

## Kedy rozhodnutie prehodnotiť

- Ak sa objaví investor alebo strategický partner s výrobnou kapacitou ochotný financovať cestu B skôr.
- Ak Google ďalej obmedzí možnosť prevádzkovať vlastný systém na telefónoch Pixel.
- Ak sa ukáže, že pre prvý trh (napr. B2B) je vlastné zariadenie podmienkou predaja.
