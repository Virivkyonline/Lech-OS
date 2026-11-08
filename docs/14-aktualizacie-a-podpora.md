# 14 – Aktualizácie a dlhodobá podpora

## Záväzok

Pravidelné bezpečnostné aktualizácie počas viacerých rokov sú **najväčšia prevádzková povinnosť** výrobcu systému.
Nie je to jednorazový projekt, ale trvalá služba s vlastným tímom a rozpočtom. Preto ju overujeme hneď na začiatku
(brána „Overený základ“, [ADR-0008](adr/0008-postupny-vyvoj-a-brany.md)), nie až pred predajom.

| Záväzok | Hodnota |
|---------|---------|
| Bezpečnostné aktualizácie | **Mesačne** |
| Kritické zraniteľnosti, ktoré sú aktívne zneužívané | Mimoriadna aktualizácia čo najskôr |
| Oneskorenie oproti zverejneniu záplat v AOSP | Cieľ **do 7 dní**, meriame a zverejňujeme |
| Doba podpory zariadenia | Do konca podpory firmvéru od výrobcu zariadenia; **dátum zverejnený pred kúpou** |
| Nové verzie Androidu | Prechod na novú verziu AOSP do niekoľkých mesiacov od jej zverejnenia (Google ich zverejňuje dvakrát ročne) |

### Prečo nesľúbime viac, než dá výrobca

Časti telefónu (modem, bootloader, bezpečnostný čip, ovládače) vie opravovať len výrobca hardvéru. Keď výrobca prestane
vydávať firmvér, telefón už nemôže byť plne bezpečný, nech by sme systém aktualizovali akokoľvek dlho.

| Zariadenie | Podpora od výrobcu |
|------------|--------------------|
| Google Pixel 8 a novšie | 7 rokov od uvedenia na trh (napr. Pixel 9 uvedený v roku 2024 → do roku 2031) |
| Fairphone | Dlhá podpora deklarovaná výrobcom – overiť pre konkrétny model |
| Vlastný telefón (cesta B) | Určíme zmluvou s výrobcom ODM – cieľ aspoň 7 rokov |

## Čo všetko treba mesačne aktualizovať

| Časť | Zdroj zmien | Frekvencia |
|------|-------------|------------|
| Bezpečnostné záplaty AOSP | Bezpečnostný bulletin Android | Mesačne |
| Jadro Linux | Záplaty jadra (GKI) | Mesačne |
| Firmvér zariadenia | Výrobca zariadenia | Podľa výrobcu (Pixel mesačne) |
| **WebView a prehliadač** | Chromium | **Približne každé 4 týždne + mimoriadne opravy** – jedna z najnáročnejších častí |
| Vrstva kompatibility s Google | Nové verzie Google Play Services | Priebežne |
| AI modely a pravidlá | Naše vydania | Podľa potreby |
| Databázy (trackery, škodlivé aplikácie, IP rozsahy) | Naše služby | Týždenne |

## Proces jednej mesačnej aktualizácie

```mermaid
flowchart LR
    A["Zverejnenie<br/>bulletinu"] --> B["Prevzatie záplat<br/>a firmvéru"]
    B --> C["Automatické<br/>zostavenie"]
    C --> D["Automatické testy<br/>(boot, OTA, sieťový audit,<br/>kompatibilita)"]
    D --> E["Podpis<br/>(offline kľúče)"]
    E --> F["Interní testeri<br/>+ 1 % zariadení"]
    F --> G["100 % zariadení"]
```

- Každý krok okrem podpisu je automatizovaný. Cieľ: z 7 dní väčšinu času zaberá testovanie, nie ručná práca.
- **A/B aktualizácie:** ak nová verzia nenabootuje, telefón sa automaticky vráti na predchádzajúcu.
- **Ochrana proti návratu na starú verziu:** útočník nemôže nainštalovať staršiu zraniteľnú verziu.
- **Postupné nasadenie:** pri probléme zastavíme nasadenie skôr, ako zasiahne všetkých.

## Tím a náklady

Aj pre 1–2 podporované zariadenia potrebujeme **trvalo aspoň 1–2 ľudí** len na vydávanie aktualizácií
(release engineering) a k tomu kapacitu na WebView. S každým ďalším zariadením rastie počet testov a firmvérov.
Preto podporujeme **malý zoznam zariadení** a každé nové pridávame až po zhodnotení nákladov na jeho podporu.

Rozpočet na aktualizácie počítame od prvého podporovaného zariadenia **na celú dobu podpory**, nie len na vývoj.

## Povinnosti podľa EÚ

- **Akt o kybernetickej odolnosti (CRA):** výrobca určí dobu podpory produktu a počas nej rieši zraniteľnosti.
  Doba podpory má byť spravidla aspoň 5 rokov (overiť presné znenie nariadenia). Aktívne zneužívané zraniteľnosti
  treba hlásiť do 24 hodín (od 11. 9. 2026). Pozri [docs/09](09-pravo-a-licencie.md#akt-o-kybernetickej-odolnosti-cra).
- **Ekodizajn smartfónov** (pre predaj telefónov – cesta A+ a B): minimálna doba poskytovania aktualizácií systému
  po skončení predaja modelu (overiť presné lehoty v nariadení).

## Čo ak by projekt skončil

Používatelia nesmú zostať s telefónom, o ktorom nevedia, či je bezpečný:

- koniec podpory oznámime vopred s presným dátumom,
- posledná aktualizácia umožní bezpečný prechod na iný systém,
- otvorený kód ([ADR-0006](adr/0006-licencia-otvoreny-kod.md)) umožní komunite alebo partnerovi pokračovať
  pod iným názvom.

## Overenie v etape 2

Pred tým, než začneme pridávať funkcie, si celý cyklus vyskúšame naostro (podmienky brány „Overený základ“):

1. Tri po sebe idúce aktualizácie na Pixeli, z toho jedna zámerne chybná – telefón sa musí sám vrátiť na funkčnú verziu.
   Prvý krok na Cuttlefish (E2-08): aktualizácia A/B z vlastného servera v0, podpísaná kľúčom LECH OS
   ([docs/11, časť 4e](11-etapa-2-cuttlefish.md#4e-aktualizácia-z-vlastného-servera-úloha-e2-08)).
   **Splnené 8. 11. 2026 na Pixeli 9 so zamknutým bootloaderom:** 0.1.1 → 0.1.2 → chybná (sám späť na 0.1.2) → 0.1.3
   ([docs/11, časť 4l](11-etapa-2-cuttlefish.md#4l-aktualizácia-ota-na-pixeli-e2-08-na-telefóne-brána-1)).
2. **Generálka mesačnej aktualizácie:** prevziať jeden skutočný bezpečnostný bulletin a zmerať, koľko dní trvá od
   zverejnenia po nainštalovanú aktualizáciu.
   **Začatá 8. 11. 2026** s bulletinom za november 2026 (zverejnený 2. 11.; kritická `CVE-2026-58868` vo frameworku
   aj pre Android 17). LECH OS je zostavený z AOSP `CP2A.260605` (úroveň zabezpečenia jún 2026) – Google od roku 2026
   vydáva celé AOSP dvakrát ročne, mesačné opravy prichádzajú ako jednotlivé commity v AOSP. Stav opráv zistí
   `tools/etapa2/14-bezpecnost.sh stav 2026-07 … 2026-11`: pre každý bulletin stiahne commity z AOSP a overí, či už sú
   v strome, či sa dajú použiť, alebo či ich treba prispôsobiť; výsledok v `~/lech-work/bezpecnost/<mesiac>/stav.md`.
   Prvý výsledok (8. 11. 2026): júl a august bez opráv pre AOSP; september 172 opráv (67 commitov sa dá použiť,
   4 konflikty, 94 bez odkazu do AOSP – súčasti výrobcov čipov, 10 jadro); október 25 (19 sa dá použiť, 5 konfliktov);
   **november: 0 commitov platformy v AOSP ani 6 dní po zverejnení** (bulletin sľubuje 48 h) – kritická
   `CVE-2026-58868` sa zatiaľ nedá opraviť z AOSP. Výrobcovia telefónov dostávajú opravy mesiace pred AOSP
   (GrapheneOS ich má ako partner); pre LECH OS je to riziko mesačného cyklu. Preberanie opráv: [bezpecnost/](../bezpecnost/README.md).
   Zostava 0.1.4-dev (8. 11. 2026) prevzala opravy zo septembra a októbra: **84 použitých**, 6 už v AOSP,
   6 treba prispôsobiť (Bluetooth CVE-2026-55269, -58880, -49933; NFC CVE-2026-49879; framework CVE-2026-58834;
   telefónia CVE-2026-55270). Všetkých 6 má GrapheneOS upravených pre Android 17 – prevzaté (`bezpecnost/prisposobene/`).
   **GrapheneOS zverejnil novembrové opravy platformy 2. 11.** (deň bulletinu) vo svojich repozitároch; LECH OS ich
   odteraz preberá odtiaľ podľa čísla chyby z bulletinu, kým ich Google nedá do AOSP.
   **Neprevzatá:** kritická `CVE-2026-58868` (framework, spustenie kódu na diaľku bez zásahu používateľa; chyba
   A-486464711) – 8. 11. 2026 nie je ani v AOSP, ani vo verejných repozitároch GrapheneOS (vetva `17`). GrapheneOS
   takéto opravy dodáva vo svojich „security preview“ vydaniach z opráv, ktoré Google vopred posiela partnerom –
   ich zdrojový kód nie je verejný. LECH OS ju dostane až so zverejnením v AOSP: konkrétny príklad, prečo je pre
   mesačný cyklus dôležitý prístup k opravám vopred (partnerstvo s Google alebo dohoda s GrapheneOS).
3. Odhad, koľko ľudí a času mesačný cyklus reálne potrebuje.
   **Prvý odhad z generálky (8. 11. 2026, Pixel 9, nový zostavovací notebook):**

   | Krok | Nástroj | Zmerané / odhad |
   |------|---------|-----------------|
   | Zistiť chýbajúce opravy z bulletinu | `14-bezpecnost.sh stav` | ~15 min (5 bulletinov naraz), bez obsluhy |
   | Prispôsobiť opravy, ktoré sa na strom nedajú použiť | ručne | 6 z 96 za dva mesiace; odhad 0,5–2 h na opravu (s testom) |
   | Jadro | predzostavené jadro GrapheneOS (`manifests/lech-pixel.xml`) | 8. 11. už najnovšie (značka `2026110700`); nová verzia = zmena revízie + zostavenie |
   | Firmvér a súčasti výrobcov čipov (vendor) | adevtool, pôvodný obraz Google | 8. 11. najnovší známy `CP3A.261005.005`; nový obraz = `12-priprav-pixel.sh --len-generovat` (~30 min) |
   | Zostavenie + balík OTA | `12-zostav-pixel.sh --ota` | 2–3 h bez obsluhy (84 opráv vo frameworku: 138 min pri 16 úlohách) |
   | Test v telefóne (aktualizácia, hovory, SMS, dáta, audit) | `11-pixel.sh aktualizovat`, `audit` | ~1 h |

   Práca človeka: pri bežnom mesiaci rádovo **1–2 dni jedného vývojára** (hlavne prispôsobenie opráv a test), zvyšok
   beží sám. **Najväčšie oneskorenie nie je práca, ale zdroj:** opravy platformy prichádzajú do AOSP neskoro
   (november 2026: ani 6 dní po bulletine). Možnosti: prebrať opravy, ktoré zverejní GrapheneOS, alebo partnerstvo
   s Google (prístup k opravám vopred, ako majú výrobcovia) – rozhodnúť do etapy 9.
