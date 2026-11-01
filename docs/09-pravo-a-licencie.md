# 09 – Právo, regulácia a licencie

> Tento dokument je technický prehľad, **nie právne poradenstvo**. Pred etapou 8 je potrebné posúdenie
> advokátom so zameraním na IT právo a reguláciu EÚ. Stav regulácie overený k 31. 10. 2026.

## Licencie softvéru

| Komponent | Licencia | Čo to pre nás znamená |
|-----------|----------|-----------------------|
| AOSP (väčšina) | Apache 2.0 | Môžeme upravovať a šíriť, aj komerčne; zachovať upozornenia (NOTICE) a licenčné texty |
| Jadro Linux | GPL v2 | **Zdrojový kód jadra (vrátane našich úprav) musíme zverejniť** pre každú distribuovanú verziu |
| Ďalšie komponenty | Rôzne (MIT, BSD, LGPL, GPL…) | Automatická kontrola licencií v CI a SBOM |
| Binárne súbory výrobcov (ovládače, firmvér) | Licencie výrobcov | Posúdiť právo na ich distribúciu v našich obrazoch systému pre každé zariadenie |
| AI modely | Rôzne, často vlastné licencie | Každý model posúdiť jednotlivo; preferujeme Apache 2.0 / MIT |
| Kód z iných open-source projektov | Podľa projektu | Pred prebratím overiť licenciu konkrétneho repozitára; dodržať povinnosti uvádzania autorstva |

### Licencia kódu LECH OS – rozhodnuté

LECH OS je **otvorený projekt** ([ADR-0006](adr/0006-licencia-otvoreny-kod.md)):

- systém, systémové služby, aplikácie, SDK a nástroje: **Apache 2.0**,
- jadro Linux: **GPL v2**,
- serverové komponenty LECH Cloud a LECH Store: **AGPL v3**,
- názov a logo: chránené ochrannou známkou, nie sú súčasťou licencie kódu,
- externé príspevky: **DCO** (`Signed-off-by`), pozri [CONTRIBUTING.md](../CONTRIBUTING.md).

Obchodný model stojí na službách (LECH Cloud, AI servery), podpore, firemných zákazníkoch a neskôr na zariadeniach.

## Ochranné známky

- **„Android“ je ochranná známka Google.** Nesmie byť súčasťou názvu produktu. Formulácie typu „spúšťa Android aplikácie“
  posúdiť podľa pravidiel Google pre používanie značky.
- Názov **LECH OS** (alebo finálny názov) overiť v databázach EUIPO a ÚPV SR a zaregistrovať pred verejným prototypom.
  Pri otvorenom kóde je ochranná známka hlavný nástroj, ako zabrániť, aby sa odnože vydávali za LECH OS.
- Pripraviť pravidlá používania značky (trademark policy) – kedy smie komunita používať názov LECH.
- Názvy iných projektov (napr. GrapheneOS) nepoužívať v produkte; pri prebratí kódu uviesť autorstvo podľa licencie.

## Ochrana osobných údajov (GDPR)

- LECH Cloud a LECH AI servery spracúvajú osobné údaje → záznamy o spracovateľských činnostiach, posúdenie vplyvu (DPIA)
  pre AI a polohové služby, zmluvy so spracovateľmi.
- Externí AI poskytovatelia = spracovatelia alebo samostatní prevádzkovatelia; prenos mimo EÚ len s primeraným
  právnym základom.
- Navrhovaná architektúra (šifrovanie na strane klienta, lokálna AI, minimum logov) výrazne znižuje rozsah povinností.

## Akt o kybernetickej odolnosti (CRA)

Pre LECH OS je CRA kľúčový predpis:

- **Operačné systémy patria medzi „dôležité produkty“ triedy I** (príloha III CRA). Posúdenie zhody je možné vlastnými
  silami, ak sa použijú harmonizované normy; inak je potrebný notifikovaný orgán.
- **Od 11. 9. 2026 platia ohlasovacie povinnosti:** aktívne zneužívané zraniteľnosti a závažné incidenty treba hlásiť
  cez jednotnú platformu ENISA – včasné varovanie do 24 hodín, oznámenie do 72 hodín, záverečná správa do 14 dní.
- **Od 11. 12. 2027 platí väčšina ostatných povinností:** bezpečnosť už v návrhu, správa zraniteľností počas
  deklarovanej doby podpory, SBOM, technická dokumentácia, označenie CE.
- Pokuty až 15 mil. € alebo 2,5 % celosvetového obratu.

Bezpečnostné procesy v [docs/05](05-lech-security.md) sú navrhnuté tak, aby tieto požiadavky pokryli.

## Akt o umelej inteligencii (AI Act)

- LECH AI je asistent, ktorý komunikuje s ľuďmi → povinnosti transparentnosti (používateľ musí vedieť, že komunikuje s AI;
  označovanie AI generovaného obsahu tam, kde to predpis vyžaduje).
- Pri použití modelov tretích strán sme v úlohe „nasadzujúceho subjektu“ alebo „poskytovateľa systému“ – rozsah povinností
  posúdiť podľa finálnej architektúry. Pri doladení modelov (fine-tuning) posúdiť, či sa nestávame poskytovateľom modelu.
- Termíny uplatňovania jednotlivých častí AI Act sa v rokoch 2025–2026 diskutovali a menili – **overiť aktuálny stav**
  pred etapou 5.

## Predpisy pre cestu A+ a B (predaj telefónov)

Ak začneme predávať telefóny (vlastné alebo s predinštalovaným systémom), môžeme sa stať výrobcom, dovozcom
alebo distribútorom so samostatnými povinnosťami:

| Predpis | Obsah |
|---------|-------|
| Smernica o rádiových zariadeniach (RED) a jej delegovaný akt o kybernetickej bezpečnosti | Bezpečnosť, ochrana údajov a siete pre zariadenia s rádiom |
| Nariadenie o ekodizajne smartfónov | Dostupnosť náhradných dielov, minimálna doba aktualizácií systému, energetický štítok |
| Nariadenie o batériách, smernica WEEE | Batérie, spätný odber elektroodpadu |
| CE, SAR, zhoda s rádiovými normami | Certifikácia hardvéru (pri ceste B cez ODM výrobcu) |
| Ochrana spotrebiteľa | Záruka, reklamácie, informačné povinnosti |

## Akt o digitálnych trhoch (DMA)

Google je pre Android označený ako strážca brány. DMA neukladá povinnosti nám, ale môže nám pomôcť (napr. pri otázkach
interoperability alebo prístupu k službám). Sledovať rozhodnutia Komisie relevantné pre Android a atestáciu.

## Zdroje

- CRA – ohlasovacie povinnosti od 11. 9. 2026: [Európska komisia](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting),
  [Hogan Lovells](https://www.hoganlovells.com/en/publications/eu-cyber-resilience-act-getting-ready-for-cra-compliance-in-2026),
  [Jones Day](https://www.jonesday.com/en/insights/2026/07/eu-cyber-resilience-act-24hour-reporting-duties-start-september-11-2026)
- CRA – operačné systémy v triede I: [eucybersecurity.org](https://www.eucybersecurity.org/en/obligations/obl-art7-01)
  (overiť priamo v texte nariadenia, príloha III)
