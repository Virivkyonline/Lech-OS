# 01 – Vízia a princípy

## Poslanie

**LECH OS je mobilný operačný systém, v ktorom telefón patrí používateľovi.** Používateľ vie, aké údaje
z telefónu odchádzajú, kam a prečo – a bez jeho súhlasu neodchádzajú žiadne. Inteligentný asistent je súčasťou
systému, rozumie funkciám telefónu a pracuje primárne lokálne.

## Pre koho

| Skupina | Čo potrebuje | Čo im LECH OS dá |
|---------|--------------|------------------|
| Ľudia, ktorým záleží na súkromí | Telefón bez sledovania, ktorý „normálne funguje“ | Súkromie ako predvolené nastavenie, nie ako expertný režim |
| Firmy a organizácie | Kontrola nad firemnými údajmi, súlad s GDPR, nezávislosť od amerických cloudov | Vlastná alebo európska infraštruktúra, AI bez odosielania dát tretím stranám |
| Verejná správa a citlivé profesie | Overiteľná bezpečnosť, audit, dlhodobá podpora | Zamknutý bootloader s vlastnými kľúčmi, nezávislý audit, transparentné aktualizácie |

> **Rozhodnuté ([ADR-0007](adr/0007-prvy-trh-firmy.md)):** prvým komerčným produktom je pilot pre firmy a organizácie.
> Jednotlivci dostanú paralelne bezplatnú komunitnú verziu s rovnakým kódom.

## Princípy ako overiteľné požiadavky

Princíp, ktorý sa nedá zmerať, sa časom nenápadne vytratí. Preto má každý princíp konkrétnu požiadavku
a spôsob overenia, ktorý pobeží automaticky v CI alebo pri každom vydaní.

| # | Princíp | Požiadavka v praxi | Ako to overíme |
|---|---------|--------------------|----------------|
| P1 | Súkromie na prvom mieste | Čerstvo nainštalované zariadenie neodošle bez súhlasu žiadne osobné údaje. Povolené sú len nevyhnutné spojenia (kontrola aktualizácií bez identifikátora zariadenia, synchronizácia času, kontrola pripojenia) – všetky na naše alebo používateľom zvolené servery. | Automatický **audit sieťovej prevádzky pri prvom spustení** (emulátor + zachytávanie prevádzky) pri každom vydaní. Akékoľvek nové spojenie zastaví vydanie. |
| P2 | Žiadne povinné konto | Sprievodca prvým spustením sa dá prejsť bez vytvorenia akéhokoľvek konta. Všetky funkcie systému fungujú bez konta; konto (LECH ID) je potrebné len pre LECH Cloud. | Automatický test sprievodcu „bez konta“. |
| P3 | Žiadne údaje na reklamné účely | V systéme neexistuje reklamný identifikátor. Systémové aplikácie neobsahujú reklamné ani analytické knižnice tretích strán. Zmluvy s partnermi reklamné využitie zakazujú. | Skener knižníc (detekcia známych trackerov) nad všetkými systémovými APK. |
| P4 | Žiadne marketingové sledovanie | Telemetria je predvolene vypnutá. Ak ju používateľ zapne, je anonymná a jej schéma je verejne zdokumentovaná. | Revízia kódu + verejná dokumentácia schémy telemetrie. |
| P5 | Používateľ rozhoduje o odchode údajov | Každá aplikácia má povolenie „Sieť“ (dá sa vypnúť). Panel **„Odchod údajov“** ukazuje, ktoré systémové služby a AI požiadavky poslali údaje mimo telefón. | Testy povolení + kontrola, že každá sieťová funkcia systémových služieb sa zapisuje do panelu. |
| P6 | Integrovaná AI | LECH AI je systémová služba, nie len aplikácia. Základné funkcie fungujú bez internetu. Citlivé akcie vyžadujú súhlas. | Testovacia sada príkazov v režime lietadlo; testy pravidiel súhlasu (pozri [spec/ai-akcie.yaml](../spec/ai-akcie.yaml)). |
| P7 | Kompatibilita s Android aplikáciami | Aplikácie bežia na natívnom Android runtime (ART). Kompatibilitu meriame na zozname najpoužívanejších aplikácií v SK/CZ. | Automatizovaný kompatibilitný test (inštalácia, spustenie, základné scenáre) – výsledky verejne. |
| P8 | Dlhodobá podpora | Mesačné bezpečnostné aktualizácie. Každé podporované zariadenie má **vopred zverejnený dátum konca podpory**. | Sledovanie oneskorenia záplat (počet dní od zverejnenia v AOSP po vydanie v LECH OS). |
| P9 | Nezávislosť od Google, Apple a jedného AI poskytovateľa | Žiadna časť systému nesmie mať jediného nenahraditeľného dodávateľa. Každá externá závislosť má zdokumentovaný „plán odchodu“. | Register závislostí v repozitári; revízia pri každom ADR. |

## Čo LECH OS nie je

- **Nie je to systém písaný od nuly.** Stavia na AOSP a jadre Linux – to je jediná realistická cesta ku kompatibilite
  s Android aplikáciami.
- **Nie je to „anti-Google“ systém, ktorý používateľovi niečo zakazuje.** Ak si používateľ vedome chce nainštalovať
  aplikácie Google, môže – ale v obmedzenom režime, bez systémových privilégií (pozri [ADR-0003](adr/0003-kompatibilita-google-sluzieb.md)).
- **Nie je to nástroj na úplnú anonymitu.** Chráni pred komerčným sledovaním a bežnými útokmi. Ochrana pred
  cieleným štátnym útočníkom je náročnejší cieľ, ku ktorému sa môžeme približovať, ale nesmieme ho sľubovať.
- **Nie je to systém pre stovky modelov telefónov.** Bezpečnosť vyžaduje hlbokú podporu každého zariadenia,
  preto podporujeme malý, starostlivo vybraný zoznam.

## Postavenie na trhu

Súkromiu zameraných Android systémov už existuje niekoľko. To je dobrá správa (trh existuje), ale znamená to,
že LECH OS musí mať jasné odlíšenie.

| Projekt | Zameranie | Poznámka |
|---------|-----------|----------|
| GrapheneOS | Maximálna bezpečnosť, telefóny Pixel | Na MWC 2026 oznámené partnerstvo s Motorolou, prvý telefón sa očakáva v roku 2027 |
| /e/OS (Murena) | De-Google pre široký okruh zariadení, predaj telefónov | Ukážka obchodného modelu „systém + predinštalované telefóny“ |
| LineageOS | Komunitný systém pre množstvo zariadení | Nie je komerčný produkt, dôraz na šírku podpory |
| iodéOS, CalyxOS | Súkromie, menšie projekty | – |

**Odlíšenie LECH OS:**

1. **AI integrovaná do systému so súkromím ako architektonickou vlastnosťou** – nie chatbot, ale asistent,
   ktorý ovláda telefón, beží lokálne a nikdy neobíde súhlas používateľa.
2. **Slovenčina a čeština ako prvotriedne jazyky** – vrátane hlasového ovládania a lokálnych modelov.
3. **Európska infraštruktúra** – cloud, AI servery a aktualizácie prevádzkované v EÚ, so súladom s GDPR a CRA.
4. **Produkt, nie projekt** – podpora, záruka, jasné termíny podpory zariadení, cesta k vlastnému telefónu.

## Zdroje

- Partnerstvo Motorola a GrapheneOS: [9to5Google, 1. 3. 2026](https://9to5google.com/2026/03/01/motorola-confirms-grapheneos-partnership-for-a-future-smartphone-porting-features/),
  [Beebom](https://gadgets.beebom.com/news/motorola-grapheneos-partnership-announced-at-mwc-2026-secure-phones-coming-in-2027) (overené 31. 10. 2026)
