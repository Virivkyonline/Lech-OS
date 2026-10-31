# 10 – Riziká a otvorené otázky

## Hlavné riziká

| # | Riziko | Pravdepodobnosť | Dopad | Zmiernenie |
|---|--------|-----------------|-------|------------|
| R1 | **Bankové aplikácie a platby nefungujú** kvôli Play Integrity | Vysoká | Vysoký | Hardvérová atestácia s našimi kľúčmi, rokovania s bankami SK/CZ od etapy 1, verejná databáza kompatibility, transparentná komunikácia pred kúpou ([docs/03](03-nezavislost-od-google.md#play-integrity--najväčšie-riziko-kompatibility)) |
| R2 | **Údržba je drahšia ako vývoj** – mesačné záplaty, nové verzie AOSP, WebView, firmvér | Vysoká | Vysoký | Minimalizovať rozsah úprav AOSP (malý „patch set“), automatizácia, malý zoznam podporovaných zariadení, rozpočet na údržbu od začiatku |
| R3 | **Google ďalej obmedzí otvorenosť AOSP** (už v roku 2025 prestal zverejňovať device trees Pixel; od roku 2026 zverejňuje zdrojové kódy len dvakrát ročne) | Stredná | Vysoký | Device Support Package vo vlastnej réžii, druhá platforma (Fairphone), partnerstvo s výrobcom, sledovanie zmien |
| R4 | **Lokálna AI je príliš pomalá alebo slabá v slovenčine** | Stredná | Stredný | Malé špecializované modely, doladenie na SK/CZ, voliteľná cloudová vrstva na vlastných serveroch, merania už v prototype |
| R5 | **Konkurencia** (GrapheneOS, /e/OS a ďalší, plus partnerstvá s veľkými výrobcami) | Vysoká | Stredný | Jasné odlíšenie: integrovaná súkromná AI, slovenčina a čeština, európska infraštruktúra, produkt s podporou; prípadne zameranie na B2B |
| R6 | **Oneskorenie bezpečnostných záplat** oproti výrobcom s prístupom k embargovaným záplatám | Vysoká | Stredný | Merať a zverejňovať oneskorenie; partnerstvo s výrobcom (A+ / B) |
| R7 | **Právne riziká** – ochranné známky, distribúcia binárnych súborov výrobcov, Google Play v sandboxe, licencie modelov | Stredná | Vysoký | Právne posúdenie pred etapou 8 ([docs/09](09-pravo-a-licencie.md)) |
| R8 | **Financovanie a tím** – projekt vyžaduje špecialistov, ktorých je na trhu málo | Vysoká | Vysoký | Etapovanie s jasnými míľnikmi pre investorov, otvorený kód ako spôsob získania komunity a talentov |
| R9 | **Koniec podpory firmvéru výrobcom** skôr, ako sme sľúbili zákazníkom | Stredná | Stredný | Sľubovať dobu podpory len do konca podpory firmvéru výrobcom; zverejniť dátum vopred |
| R10 | **Bezpečnostný incident** (podvrhnutá aktualizácia, únik kľúča) zničí dôveru | Nízka | Kritický | Offline kľúče v HSM, dve osoby pri podpise, reprodukovateľné zostavy, nezávislé audity |

## Rozhodnutia, ktoré čakajú na vlastníka projektu

| # | Otázka | Možnosti | Odporúčanie | Prečo je dôležitá teraz |
|---|--------|----------|-------------|-------------------------|
| O1 | **Obchodná cesta** | A / B / A → A+ → B | **A → A+ → B** ([ADR-0001](adr/0001-obchodna-cesta.md)) | Ovplyvňuje rozpočet, tím aj výber prvého zariadenia |
| O2 | **Licencia kódu** | Otvorený / čiastočne otvorený / uzavretý | Otvorený kód systému a klientskych aplikácií | Ovplyvňuje štruktúru repozitárov, obchodný model aj dôveru používateľov |
| O3 | **Prvý cieľový trh** | Spotrebitelia (B2C) / firmy a verejná správa (B2B) / oboje | Začať s **B2B pilotom** (menší počet zariadení, vyššia marža, ochota platiť za súkromie a podporu) + komunitná verzia pre jednotlivcov | Ovplyvňuje priority funkcií (napr. správa zariadení vs. obchod s aplikáciami) |
| O4 | **Financovanie** | Vlastné zdroje / granty EÚ / investori | Kombinácia: granty EÚ na výskum (súkromie, AI, digitálna suverenita) + investor po verejnom prototype | Určuje veľkosť tímu a tempo |
| O5 | **Finálny názov** | „LECH OS“ alebo iný | Overiť ochrannú známku pred investíciou do značky | Premenovanie po spustení je drahé |
| O6 | **Jazyky pri spustení** | SK / SK + CZ / SK + CZ + EN | SK + CZ + EN | Ovplyvňuje výber a doladenie AI modelov |
| O7 | **Režim Google pri prvom spustení** | Ponúknuť „Google Play v sandboxe“ priamo v sprievodcovi / len v nastaveniach | V sprievodcovi, s jasným vysvetlením ([ADR-0003](adr/0003-kompatibilita-google-sluzieb.md)) | Zásadne mení prvý dojem a kompatibilitu |

## Čo ďalej (najbližšie kroky)

1. Potvrdiť alebo upraviť ADR 0001–0005.
2. Rozhodnúť otázky O2 a O3.
3. Zostaviť zoznam 200 cieľových aplikácií pre SK a CZ a zoznam bánk na oslovenie.
4. Overiť ochrannú známku „LECH OS“.
5. Pripraviť zostavovacie prostredie a začať etapu 2 na Cuttlefish; kúpiť 2× Pixel.
6. Začať prototyp LECH AI ako bežnú aplikáciu na overenie lokálnych modelov v slovenčine.
