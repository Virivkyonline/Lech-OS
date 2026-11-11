# ADR-0011: Prehliadač a WebView – Vanadium od GrapheneOS

- **Stav:** Prijaté (2026-11-11, vlastník projektu)
- **Dátum:** 2026-11-11

## Kontext

LECH OS mal prehliadač a WebView (jadro, ktorým aplikácie zobrazujú web) zo stromu AOSP z júna 2026: WebView bez
bezpečnostných opráv Chromia od júna a namiesto prehliadača testovaciu aplikáciu WebView (Browser2) s vyhľadávačom
Google. V auditoch (11. a 14.) boli všetky spojenia na Google z vyhľadávania v prehliadači. Chromium dostáva opravy
každé 1–2 týždne; prehliadač a WebView treba aktualizovať tak často ([docs/03](../03-nezavislost-od-google.md)).

## Zvážené možnosti

1. **Vlastná zostava Chromia** s úpravami súkromia (napr. záplaty Vanadium) pod menom LECH. Úplná kontrola, ale prvá
   zostava zaberie zostavovací počítač 1–2 dni a ďalšia je potrebná pri každom vydaní Chromia.
2. **Hotový Vanadium od GrapheneOS** (prehliadač aj WebView, zverejnené zostavy v `platform_external_vanadium`):
   predvolený vyhľadávač DuckDuckGo, vypnuté služby Google (synchronizácia, variácie, hlásenia Safe Browsing, kontrola
   aktualizácií cez Google), vstavané filtrovanie obsahu, spevnenie. Rovnaký dodávateľ ako jadro a podpora Pixelu
   (Z14); nové verzie zvyčajne pár dní po vydaní Chromia.
3. **Hotový Cromite**: silnejšie blokovanie reklám, ale vyvíja ho v podstate jeden človek – menšia istota
   pravidelných opráv.

## Rozhodnutie

**Možnosť 2 teraz, možnosť 1 neskôr.** LECH OS preberá zverejnené zostavy Vanadium s pevnou revíziou
(`manifests/lech-pixel.xml`, projekt `external/vanadium`). Modul `vendor/lech/vanadium` nahrádza WebView a Browser2
z AOSP, Vanadium je poskytovateľ WebView a predvolený prehliadač; na ploche má ikonu LECH OS (`branding/ikony`).
Aktualizácie Vanadium prichádzajú s aktualizáciami LECH OS (nová revízia v manifeste), nie cez obchod GrapheneOS.

## Dôsledky

- Prehliadač a WebView majú aktuálne opravy Chromia, LECH OS ich preberá pri mesačnej aktualizácii alebo skôr pri
  kritickej chybe (sledovať vydania Vanadium).
- Ďalšia závislosť od GrapheneOS ([docs/16](../16-zavislosti-a-plan-odchodu.md), Z16); licencia (GPL-2.0 pre úpravy,
  BSD pre Chromium) dovoľuje vlastnú zostavu – plán odchodu je možnosť 1.
- V aplikácii sa prehliadač volá Vanadium; vlastné meno a vzhľad až s vlastnou zostavou.
- Spojenia Vanadium (napr. aktualizácie súčastí cez `vanadium.app`) overí audit; povolené sú len zdokumentované.

## Kedy rozhodnutie prehodnotiť

Keď GrapheneOS prestane zverejňovať zostavy Vanadium alebo ich bude vydávať pomaly, keď bude treba vlastné meno
prehliadača (etapa 8), alebo keď LECH OS bude mať zostavovací server s kapacitou na vlastné Chromium.
