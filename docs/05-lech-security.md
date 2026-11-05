# 05 – LECH Security

## Model hrozieb (skrátene)

| Hrozba | Príklad | Hlavná ochrana |
|--------|---------|----------------|
| Strata alebo krádež telefónu | Útočník sa snaží dostať k údajom | Šifrovanie úložiska viazané na hardvér, zamknutý bootloader, obmedzenie pokusov o PIN v bezpečnostnom čipe |
| Podvrhnutý systém | Úprava systému pri fyzickom prístupe | Overený boot s našimi kľúčmi, ochrana proti návratu na staršiu verziu |
| Škodlivá aplikácia | Krádež údajov, sledovanie, podvod | Sandbox aplikácií, rozšírené oprávnenia, kontrola aplikácií, kontrola v LECH Store |
| Sledovacie a reklamné knižnice | Aplikácia odosiela údaje tretím stranám | Povolenie „Sieť“, panel odchodu údajov, označenie trackerov v Store |
| Sieťový útočník | Odpočúvanie, falošná Wi-Fi | Šifrované DNS, náhodná MAC adresa, TLS, aktualizovaný WebView |
| Zneužitie AI | Skryté pokyny v správe alebo na webe | Modul pravidiel mimo AI, sledovanie pôvodu, súhlasy – [docs/04](04-lech-ai.md#ochrana-proti-zneužitiu-ai-prompt-injection) |
| Útok na našu infraštruktúru | Podvrhnutá aktualizácia | Offline podpisové kľúče, reprodukovateľné zostavy, postupné nasadenie |
| Žiadosti o vydanie údajov | Údaje na našich serveroch | Šifrovanie na strane klienta – údaje nemáme v čitateľnej podobe; minimalizácia logov |

## Vrstvy ochrany

### 1. Zavádzanie systému (boot)

- **Android Verified Boot** s vlastnými kľúčmi LECH a **znovu zamknutý bootloader**.
- Ochrana proti návratu na staršiu verziu (rollback protection).
- Zverejnené otlačky kľúčov overeného bootu – umožňujú hardvérovú atestáciu (pozri [docs/03](03-nezavislost-od-google.md#play-integrity--najväčšie-riziko-kompatibility)).

### 2. Spevnenie systému

- Vychádzame z bezpečnostných mechanizmov AOSP (SELinux v režime enforcing, sandbox aplikácií, seccomp).
- Doplníme spevnenia, ktoré sa osvedčili v open-source projektoch (napr. spevnený alokátor pamäte,
  prísnejšie obmedzenia pre ladenie a natívny kód) – pri každom prebratí overíme licenciu.
- Zostavy typu `user` (nie `userdebug`) pre koncových používateľov.

### 3. Rozšírené oprávnenia

| Oprávnenie | Čo umožňuje používateľovi |
|------------|---------------------------|
| **Sieť** | Úplne zakázať aplikácii prístup na internet |
| **Senzory** | Zakázať prístup k akcelerometru, gyroskopu a ďalším senzorom (dajú sa zneužiť na sledovanie) |
| **Rozsah kontaktov** | Sprístupniť aplikácii len vybrané kontakty namiesto všetkých |
| **Rozsah úložiska** | Sprístupniť len vybrané priečinky alebo súbory |
| **Schránka** | Upozornenie, keď aplikácia číta schránku |
| **Automatické odobratie** | Odobratie oprávnení nepoužívaným aplikáciám |

### 4. Sieť a súkromie

- **LECH Privacy Control** – jeden prehľad: mikrofón, kamera, poloha, senzory, kam a koľko údajov posiela každá aplikácia,
  systémové služby aj AI; prístup na internet sa dá zakázať jedným dotykom. Panel overujeme meraním mimo telefónu
  ([docs/12](12-lech-privacy-control.md)).
- Šifrované DNS predvolene zapnuté, s možnosťou výberu poskytovateľa; voliteľné blokovanie známych trackerov.
- Náhodná MAC adresa pre každú Wi-Fi sieť.
- Žiadne predvolené spojenia na servery Google (pozri [docs/03](03-nezavislost-od-google.md#audit-volaní-na-servery-google-v-aosp)).
- **SIM karta ako samostatný počítač.** Cez SIM Toolkit môže SIM karta sama posielať SMS, otvárať dátové
  spojenia alebo spúšťať prehliadač (tzv. proaktívne príkazy). Chyba v aplikáciách na SIM karte umožnila v roku 2019
  útok Simjacker. LECH Privacy Control má preto zobrazovať aj činnosť, ktorú vyvolala SIM karta (SMS, spojenia).
  Riziko posúdime pri Pixeli so skutočnou SIM kartou (E2-11).

### 5. Ochrana pred škodlivými aplikáciami

Bez Google Play Protect potrebujeme vlastnú ochranu – **bez odosielania aplikácií a údajov používateľa na server**:

1. **Kontrola v LECH Store** – statická a dynamická analýza každej aplikácie pred zverejnením, detekcia trackerov,
   overenie identity vývojára.
2. **Lokálny skener v telefóne** – porovnanie podpisov a odtlačkov aplikácií s databázou známeho škodlivého softvéru,
   ktorú telefón sťahuje (nie naopak).
3. **Upozornenia pri inštalácii** mimo Store – aplikácia s nebezpečnou kombináciou oprávnení dostane výrazné varovanie.
4. Odoslanie podozrivej aplikácie na analýzu **len so súhlasom** používateľa.

> Google od roku 2026 postupne zavádza povinné overenie vývojárov aj pre aplikácie inštalované mimo Play Store
> na certifikovaných zariadeniach (najprv v štyroch krajinách, celosvetovo v roku 2027). LECH OS nie je
> certifikované zariadenie Google a toto obmedzenie nepreberá – používateľ ostáva slobodný pri inštalácii aplikácií.
> Ochranu zabezpečujeme vlastnými mechanizmami, nie centrálnym povoľovaním vývojárov.

### 6. Aktualizácie

- Mesačné bezpečnostné aktualizácie. **Cieľ:** vydanie do 7 dní od zverejnenia záplat v AOSP
  (kritické zraniteľnosti čo najskôr).
- A/B aktualizácie, postupné nasadenie, automatický návrat pri zlyhaní (pozri [docs/02](02-architektura.md#aktualizácie)).
- Sledujeme a zverejňujeme oneskorenie oproti bezpečnostným bulletinom Android.

> **Obmedzenie:** výrobcovia v partnerskom programe Google dostávajú bezpečnostné záplaty pred ich zverejnením
> (embargo). Nezávislý systém ich bez partnerstva dostáva až po zverejnení. Partnerstvo s výrobcom (cesta A+/B)
> môže tento rozdiel zmenšiť – je to jeden z argumentov v [ADR-0001](adr/0001-obchodna-cesta.md).

## Správa kľúčov

| Kľúč | Účel | Uloženie |
|------|------|----------|
| AVB kľúč (pre každé zariadenie zvlášť) | Podpis systémových oddielov pre overený boot | Offline HSM |
| Platformové a systémové kľúče | Podpis systémových aplikácií | Offline HSM |
| APEX kľúče | Podpis aktualizovateľných systémových modulov | Offline HSM |
| OTA kľúč | Podpis balíkov aktualizácií | Offline HSM |
| Kľúče LECH Store | Podpis katalógu a metadát | HSM v infraštruktúre |

Pravidlá:

- Kľúče **nikdy** nie sú v repozitári ani na vývojárskych počítačoch. Vývojové zostavy používajú samostatné testovacie kľúče.
- **Testovacie kľúče LECH OS (od 5. 11. 2026, úloha E2-07):** vytvára ich `tools/etapa2/07-vytvor-kluce.sh` do
  `~/lech-keys/test` na zostavovacom počítači, mimo repozitára. Nahrádzajú verejné testovacie kľúče AOSP
  pri podpise aplikácií a aktualizácií ([docs/11, časť 4d](11-etapa-2-cuttlefish.md#4d-vlastné-testovacie-podpisové-kľúče-úloha-e2-07)).
- Generovanie a použitie produkčných kľúčov prebieha podľa zdokumentovaného postupu za prítomnosti dvoch osôb.
- Pre každý kľúč existuje plán obnovy a plán výmeny pri kompromitácii.

## Bezpečnostné procesy

| Proces | Kedy |
|--------|------|
| Model hrozieb pre každú novú systémovú službu | Pri návrhu (ADR) |
| Revízia kódu dvoma osobami pre zmeny v bezpečnostne citlivých častiach | Stále |
| Automatické testy: audit sieťovej prevádzky, testy oprávnení, testy prompt injection | Každé zostavenie |
| Zoznam softvérových komponentov (SBOM) | Každé vydanie |
| Pravidlá zverejňovania zraniteľností (`security.txt`, bezpečnostný kontakt) | Pred verejným prototypom (etapa 8) |
| Program odmien za nájdené chyby (bug bounty) | Od etapy 8 |
| **Nezávislý bezpečnostný audit** | Pred verejným prototypom (etapa 8) a pred komerčným spustením (etapa 9) |
| Hlásenie aktívne zneužívaných zraniteľností podľa CRA (do 24 h) | Od uvedenia na trh – pozri [docs/09](09-pravo-a-licencie.md) |

## Zdroje

- Overenie vývojárov Android: [Android Authority – časový plán](https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/),
  [Gadget Hacks – zmeny od 30. 9. 2026](https://android.gadgethacks.com/news/google-android-sideloading-restrictions-sept-30-explained/) (overené 31. 10. 2026)
