# 10 – Riziká a otvorené otázky

## Hlavné riziká

| # | Riziko | Pravdepodobnosť | Dopad | Zmiernenie |
|---|--------|-----------------|-------|------------|
| R1 | **Bankové aplikácie a platby nefungujú** kvôli Play Integrity | Vysoká | Vysoký | Hardvérová atestácia s našimi kľúčmi, rokovania s bankami SK/CZ od etapy 1, verejná databáza kompatibility, transparentná komunikácia pred kúpou – podrobný plán v [docs/13](13-bankove-aplikacie.md) |
| R2 | **Údržba je drahšia ako vývoj** – mesačné záplaty, nové verzie AOSP, WebView, firmvér | Vysoká | Vysoký | Minimalizovať rozsah úprav AOSP (malý „patch set“), automatizácia, malý zoznam podporovaných zariadení, rozpočet na údržbu od začiatku; celý cyklus overený už v bráne 1 ([docs/14](14-aktualizacie-a-podpora.md)) |
| R3 | **Google ďalej obmedzí otvorenosť AOSP** (už v roku 2025 prestal zverejňovať device trees Pixel; od roku 2026 zverejňuje zdrojové kódy len dvakrát ročne) | Stredná | Vysoký | Device Support Package vo vlastnej réžii, druhá platforma (Fairphone), partnerstvo s výrobcom, sledovanie zmien |
| R4 | **Lokálna AI je príliš pomalá alebo slabá v slovenčine** | Stredná | Stredný | Malé špecializované modely, doladenie na SK/CZ, voliteľná cloudová vrstva na vlastných serveroch, merania už v prototype |
| R5 | **Konkurencia** (GrapheneOS, /e/OS a ďalší, plus partnerstvá s veľkými výrobcami) | Vysoká | Stredný | Jasné odlíšenie: integrovaná súkromná AI, slovenčina a čeština, európska infraštruktúra, produkt s podporou; prípadne zameranie na B2B |
| R6 | **Oneskorenie bezpečnostných záplat** oproti výrobcom s prístupom k embargovaným záplatám | Vysoká | Stredný | Merať a zverejňovať oneskorenie; partnerstvo s výrobcom (A+ / B) |
| R7 | **Právne riziká** – ochranné známky, distribúcia binárnych súborov výrobcov, Google Play v sandboxe, licencie modelov | Stredná | Vysoký | Právne posúdenie pred etapou 8 ([docs/09](09-pravo-a-licencie.md)) |
| R8 | **Financovanie a tím** – projekt vyžaduje špecialistov, ktorých je na trhu málo | Vysoká | Vysoký | Etapovanie s jasnými míľnikmi pre investorov, otvorený kód ako spôsob získania komunity a talentov |
| R9 | **Koniec podpory firmvéru výrobcom** skôr, ako sme sľúbili zákazníkom | Stredná | Stredný | Sľubovať dobu podpory len do konca podpory firmvéru výrobcom; zverejniť dátum vopred |
| R10 | **Bezpečnostný incident** (podvrhnutá aktualizácia, únik kľúča) zničí dôveru | Nízka | Kritický | Offline kľúče v HSM, dve osoby pri podpise, reprodukovateľné zostavy, nezávislé audity |
| R11 | **Google Play v sandboxe je technicky zložitá** a musí sa udržiavať s každou verziou Google Play Services | Vysoká | Stredný | Rozhodnutie o spôsobe realizácie až v etape 7 s presnými dátami z kompatibilitných testov; preveriť prevzatie overeného open-source riešenia (licencia); dovtedy merať kompatibilitu aj bez Google |
| R12 | **Panel súkromia ukazuje neúplné údaje** (aplikácia nájde cestu, ktorú panel nezachytí) – stratila by sa dôvera | Stredná | Vysoký | Porovnávanie s meraním mimo telefónu pri každom vydaní, nezávislý audit, zverejnená metodika ([docs/12](12-lech-privacy-control.md#nezávislé-overenie)) |

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
| O4 | **Financovanie** | Vlastné zdroje / granty EÚ / investori | Kombinácia: granty EÚ na výskum (súkromie, AI, digitálna suverenita) + investor po verejnom prototype; návrh fáz a nákladov v [docs/15](15-podnikatelsky-plan.md#10-náklady-a-financovanie-p) | Určuje veľkosť tímu a tempo |
| O5 | **Finálny názov** | „LECH OS“ zostáva pracovným názvom | Ochrannú známku a dostupnosť názvu preveriť pred komerčným uvedením; predbežnú rešerš odporúčame pred verejným prototypom | Premenovanie po spustení je drahé |
| O6 | **Jazyky pri spustení** | SK / SK + CZ / SK + CZ + EN | SK + CZ + EN | Ovplyvňuje výber a doladenie AI modelov |
| O7 | **Režim Google pri prvom spustení** | Ponúknuť „Google Play v sandboxe“ priamo v sprievodcovi / len v nastaveniach | V sprievodcovi, s jasným vysvetlením ([ADR-0003](adr/0003-kompatibilita-google-sluzieb.md)) | Zásadne mení prvý dojem a kompatibilitu |

## Čo ďalej (najbližšie kroky)

Postupne, podľa [ADR-0008](adr/0008-postupny-vyvoj-a-brany.md):

1. **Zabezpečiť zostavovací počítač** – server alebo notebook s Linuxom, aspoň 32 GB RAM (ideálne 64 GB), 400 GB SSD
   a KVM ([docs/11](11-etapa-2-cuttlefish.md#1-zostavovací-počítač)). Čisté AOSP z hotových obrazov sa dá spustiť
   a zmerať už na počítači so 16 GB RAM a KVM.
2. Čistý LECH OS na virtuálnom telefóne Cuttlefish a sieťový audit (úlohy E2-01 až E2-10).
3. Kúpiť 2× Pixel a nainštalovať LECH OS so zamknutým bootloaderom (E2-11, E2-12).
4. Splniť **bránu 1 – Overený základ**: aktualizácie vrátane automatického návratu, generálka mesačnej aktualizácie,
   nulové neznáme spojenia overené meraním mimo telefónu.
5. Po kúpe Pixelu: dvojtýždňový test lokálnej AI v slovenčine (jediná vývojová výnimka).

Popri vývoji (bez kapacity vývojárov):

- kontakty s bankami a príprava technického dokumentu pre banky ([docs/13](13-bankove-aplikacie.md)),
- zoznam 200 cieľových aplikácií pre SK a CZ vrátane firemných,
- hľadanie 1–3 pilotných organizácií,
- financovanie (otázka O4).
