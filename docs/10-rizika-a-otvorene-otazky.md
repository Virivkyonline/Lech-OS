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

## Prijaté rozhodnutia

| # | Otázka | Rozhodnutie | Dátum |
|---|--------|-------------|-------|
| O1 | Obchodná cesta | **A → A+ → B** ([ADR-0001](adr/0001-obchodna-cesta.md)) | 2026-11-01 |
| O2 | Licencia kódu | **Otvorený kód** – Apache 2.0 pre systém, AGPL v3 pre servery ([ADR-0006](adr/0006-licencia-otvoreny-kod.md)) | 2026-11-01 |
| O3 | Prvý cieľový trh | **Pilot pre firmy a organizácie** + komunitná verzia ([ADR-0007](adr/0007-prvy-trh-firmy.md)) | 2026-11-01 |
| – | Vývojové zariadenia | **Cuttlefish**, potom Pixel ([ADR-0005](adr/0005-vyvojove-zariadenie.md)) | 2026-11-01 |
| – | Základ systému, Google služby, AI | ADR [0002](adr/0002-zaklad-systemu-aosp.md), [0003](adr/0003-kompatibilita-google-sluzieb.md), [0004](adr/0004-nezavislost-od-ai-poskytovatela.md) | 2026-11-01 |

## Rozhodnutia, ktoré ešte čakajú na vlastníka projektu

| # | Otázka | Možnosti | Odporúčanie | Prečo je dôležitá teraz |
|---|--------|----------|-------------|-------------------------|
| O4 | **Financovanie** | Vlastné zdroje / granty EÚ / investori | Kombinácia: granty EÚ na výskum (súkromie, AI, digitálna suverenita) + investor po verejnom prototype | Určuje veľkosť tímu a tempo |
| O5 | **Finálny názov** | „LECH OS“ alebo iný | Overiť ochrannú známku pred investíciou do značky | Premenovanie po spustení je drahé |
| O6 | **Jazyky pri spustení** | SK / SK + CZ / SK + CZ + EN | SK + CZ + EN | Ovplyvňuje výber a doladenie AI modelov |
| O7 | **Režim Google pri prvom spustení** | Ponúknuť „Google Play v sandboxe“ priamo v sprievodcovi / len v nastaveniach | V sprievodcovi, s jasným vysvetlením ([ADR-0003](adr/0003-kompatibilita-google-sluzieb.md)) | Zásadne mení prvý dojem a kompatibilitu |

## Čo ďalej (najbližšie kroky)

1. **Zabezpečiť zostavovací server** (64+ GB RAM, 400+ GB disk, KVM) – bez neho etapa 2 nezačne
   ([docs/11](11-etapa-2-cuttlefish.md#1-zostavovací-server)).
2. Zostaviť a spustiť čisté AOSP na Cuttlefish a urobiť sieťový audit (úlohy E2-01 až E2-03).
3. Zostaviť zoznam 200 cieľových aplikácií pre SK a CZ – vrátane firemných aplikácií ([ADR-0007](adr/0007-prvy-trh-firmy.md)) –
   a zoznam bánk na oslovenie.
4. Overiť a zaregistrovať ochrannú známku „LECH OS“ (pri otvorenom kóde je to hlavná ochrana značky).
5. Začať hľadať 1–3 pilotné organizácie.
6. Začať prototyp LECH AI ako bežnú aplikáciu na overenie lokálnych modelov v slovenčine.
7. Kúpiť 2× Pixel po zvládnutí úloh E2-02 až E2-06 na Cuttlefish.
8. Rozhodnúť otázku O4 (financovanie) – určuje veľkosť tímu.
