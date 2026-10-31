# ADR-0002: Základ systému – čisté AOSP bez GMS

- **Stav:** Navrhnuté
- **Dátum:** 2026-10-31

## Kontext

Potrebujeme základ, ktorý poskytuje kompatibilitu s Android aplikáciami, je právne použiteľný v komerčnom produkte
a nerobí nás závislými od jedného subjektu.

## Zvážené možnosti

| Možnosť | Výhody | Nevýhody |
|---------|--------|----------|
| **Čisté AOSP** (vetva `android-latest-release`) | Plná kontrola, priamy upstream od Google, licencia Apache 2.0, žiadna závislosť od tempa iného projektu | Najviac vlastnej práce (device trees, spevnenie, náhrady služieb) |
| **LineageOS ako základ** | Podpora množstva zariadení, veľká komunita | Optimalizované na šírku podpory, nie na overený boot so zamknutým bootloaderom; ďalšia vrstva medzi nami a AOSP |
| **GrapheneOS ako základ** | Špičková bezpečnosť, vrstva kompatibility s Google Play v sandboxe | Závislosť od rozhodnutí a tempa iného projektu; chránená značka; podpora len vybraných zariadení |
| **Vlastné jadro a runtime od nuly** | Úplná nezávislosť | Nereálne – stratili by sme kompatibilitu s aplikáciami a roky vývoja |

## Rozhodnutie

1. **Upstreamom je čisté AOSP**, vetva `android-latest-release` (odporúčaná Google od roku 2026). Bezpečnostné záplaty
   preberáme mesačne.
2. **Žiadne komponenty GMS** v systéme.
3. **Vlastné podpisové kľúče** pre všetko (pozri [docs/05](../05-lech-security.md#správa-kľúčov)).
4. **Selektívne preberanie overených open-source komponentov** z projektov ako GrapheneOS, LineageOS, CalyxOS či /e/OS
   (napr. spevnenie, vrstva kompatibility, zálohovanie) – vždy po overení licencie konkrétneho repozitára a s uvedením
   autorstva. Učíme sa od nich, ale nie sme na nich závislí.
5. **Malý patch set:** úpravy AOSP držíme čo najmenšie a čo najviac funkcií implementujeme ako samostatné systémové
   služby a aplikácie. Znižuje to náklady na prechod na novú verziu Android.

## Dôsledky

- ✅ Nezávislosť a právna čistota (Apache 2.0, GPL len pre jadro).
- ✅ Prechod na nové verzie AOSP (dvakrát ročne) je zvládnuteľný vďaka malému patch setu.
- ❌ Device Support Package pre Pixel musíme udržiavať sami, pretože Google od Android 16 nezverejňuje device trees Pixel.
- ❌ Viac práce v etape 2 ako pri forku existujúceho projektu.

## Kedy rozhodnutie prehodnotiť

- Ak náklady na údržbu vlastného základu prekročia rozpočet a existujúci projekt ponúkne zmluvnú spoluprácu.
- Ak Google zásadne zmení licenciu alebo dostupnosť AOSP.
