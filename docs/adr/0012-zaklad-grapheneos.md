# ADR-0012: Základ systému – GrapheneOS namiesto čistého AOSP

- **Stav:** Prijaté (2026-11-13, vlastník projektu) – mení [ADR-0002](0002-zaklad-systemu-aosp.md)
- **Dátum:** 2026-11-13

## Kontext

ADR-0002 zvolilo čisté AOSP a „selektívne preberanie“ častí GrapheneOS vrátane vrstvy kompatibility pre Google Play
v sandboxe (ADR-0003). Prieskum 13. 11. 2026 ([docs/18](../18-google-play-v-sandboxe.md)) ukázal, že táto vrstva nie
je oddeliteľná: je vrastená do `frameworks/base` GrapheneOS a ďalších z jeho 147 upravených projektov. Fio banka
(`sk.fio.sb2`) bez služieb Google Play na LECH OS nefunguje; Tatra banka áno.

## Zvážené možnosti

1. Preniesť vrstvu po kúskoch do nášho AOSP – nezávislosť, ale stovky záplat a trvalá údržba pri každej zmene služieb
   Google Play a každej aktualizácii Androidu.
2. **Stavať LECH OS na GrapheneOS** (jeho manifest a projekty), naše aplikácie, vzhľad, kľúče a záplaty nad ním.
3. microG – vyžaduje falšovanie podpisov; vylúčené.

## Rozhodnutie

- **Možnosť 2.** Vlastník: „nepáči sa mi to veľmi, ale ideme“ – súkromie a bezpečnosť sú základ značky a GrapheneOS
  ich posilňuje (sieťové povolenie, povolenie senzorov, obmedzený prístup ku kontaktom a súborom, spevnenie, zóna
  Google v sandboxe).
- Berieme **pevne určené vydania** (značka manifestu GrapheneOS), systém zostavujeme sami a podpisujeme **vlastnými
  kľúčmi**; značka GrapheneOS sa v LECH OS nepoužíva (je chránená).
- Predvolene bez Googlu; spojenia na servery GrapheneOS (aktualizácie, kontrola internetu, PSDS/SUPL, čas, atestácia)
  vypneme alebo presmerujeme na servery LECH OS a overíme sieťovým auditom.
- **Plán odchodu:** keby GrapheneOS skončil alebo zmenil smer, posledné prevzaté vydanie udržiavame sami (licencie
  MIT/Apache to dovoľujú); pri úspechu a zákazníkoch zvážiť vlastnú údržbu jadra (možnosť 1). Doplniť do docs/16.

## Dôsledky

- ✅ Zóna Google, scopes a spevnenia bez vlastného vývoja; menej vlastných záplat (časť dnešných odpadne).
- ❌ Závislosť od tempa a rozhodnutí GrapheneOS; podporované len jeho zariadenia (Pixel) – vlastný hardvér z Číny
  ([docs/19](../19-dopyt-na-vyrobcov-hardveru.md)) by potreboval prenos GrapheneOS na iný čip, čo je veľká práca.
- ⚠️ Jednorazový prechod: nový strom zdrojových kódov (`~/gos`), nové nasadenie záplat, preskúšanie celého telefónu,
  pravdepodobne čistá inštalácia. Súčasná línia (`~/aosp`, 0.5.x) ostáva funkčná, kým prechod neprejde.
- ⚠️ Overovanie podpisov značiek GrapheneOS pri sťahovaní zatiaľ nie je zavedené – doplniť.
