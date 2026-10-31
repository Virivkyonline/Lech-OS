# 07 – Testovacie zariadenia

## Kritériá výberu

Telefón pre vývoj bezpečného systému musí spĺňať viac než „dá sa naň nahrať iný systém“:

1. **Zamknutie bootloadera s vlastnými kľúčmi** – bez toho nemáme overený boot (pozri [docs/02](02-architektura.md#čo-musí-každé-podporované-zariadenie-spĺňať)).
2. **Hardvérová bezpečnosť** – bezpečnostný čip, hardvérová atestácia.
3. **Dĺžka podpory firmvéru od výrobcu** – modem, bootloader a TEE vieme aktualizovať len s firmvérom výrobcu.
4. **Dostupnosť zdrojových kódov** – jadro, device tree, existujúca podpora v open-source projektoch.
5. **Relevantnosť pre obchodnú cestu** – je výrobca otvorený partnerstvu (cesta A+ / B)?

## Porovnanie

| Kritérium | Google Pixel | Fairphone | Nothing Phone |
|-----------|--------------|-----------|---------------|
| Odomknutie bootloadera | Áno (verzie bez operátorského zámku) | Áno, oficiálne | Áno |
| Zamknutie s vlastnými kľúčmi | **Áno, osvedčené v praxi** (GrapheneOS) | Oficiálne áno, ale výrobca varuje, že s iným systémom nemusí zariadenie naštartovať; komunita hlási problémy pri Fairphone 6 | Neoverené |
| Hardvérová bezpečnosť | Samostatný bezpečnostný čip (Titan M2) | TEE v čipe Qualcomm | TEE v čipe |
| Dĺžka podpory | 7 rokov (Pixel 8 a novšie) | Dlhá podpora deklarovaná výrobcom (overiť pre konkrétny model) | Kratšia |
| Zdrojové kódy pre vlastný systém | Jadro áno; **od Android 16 Google nezverejňuje device trees ani binárne ovládače Pixel v AOSP** – treba ich udržiavať samostatne (GrapheneOS to robí) | Dostupné, existujú /e/OS a iodéOS | Komunitné porty |
| Existujúce súkromné systémy | GrapheneOS, LineageOS, /e/OS, CalyxOS | /e/OS (aj predaj s predinštalovaným systémom), iodéOS, LineageOS | LineageOS (niektoré modely) |
| Vhodnosť ako partner (A+ / B) | Nízka – Google nie je partner pre konkurenčný OS | **Vysoká** – už spolupracuje so súkromnými systémami | Neznáma |
| Vhodnosť na vývoj | **Najvyššia** | Dobrá ako druhá platforma | Odložiť |

> **Poznámka k trhu:** GrapheneOS na MWC 2026 oznámil partnerstvo s Motorolou (prvý telefón sa očakáva v roku 2027).
> Ukazuje to, že veľkí výrobcovia sú otvorení spolupráci so súkromnými systémami – je to relevantné pre cestu A+ a B.

## Odporúčanie

Podrobné zdôvodnenie v [ADR-0005](adr/0005-vyvojove-zariadenie.md).

| Kedy | Zariadenie | Účel |
|------|------------|------|
| **Ihneď** | **Cuttlefish** (virtuálne zariadenie AOSP) | Od roku 2025 oficiálny referenčný cieľ AOSP. Vývoj systému a automatické testy v CI bez fyzického telefónu. Zadarmo. |
| **Etapa 2** | **Google Pixel – 2 kusy** rovnakého modelu (Pixel 9 alebo novší, verzia bez operátorského zámku) | Prvé reálne zariadenie. Jeden kus na každodenné testovanie, druhý na experimenty s bootloaderom a firmvérom. Odporúčame model, ktorý už podporuje GrapheneOS alebo LineageOS – je to dôkaz, že spustenie bez oficiálnych device trees je zvládnuteľné. |
| **Etapa 7–8** | **Fairphone** (aktuálny model) | Druhá platforma – overenie, že Device Support Package je naozaj oddelený. Zároveň začiatok rozhovoru o partnerstve. |
| **Neskôr** | Nothing, Motorola, ďalší | Prehodnotiť podľa podpory zamknutia bootloadera s vlastnými kľúčmi a záujmu o partnerstvo. |

### Nákupný zoznam pre etapu 2

- 2× Google Pixel rovnakého modelu, 12+ GB RAM (kvôli lokálnej AI), kúpený v EÚ bez operátorského zámku.
- USB-C káble s dátovým prenosom, USB hub s napájaním.
- Zostavovací server alebo prenájom (pozri [docs/02](02-architektura.md#zostavovacia-infraštruktúra)).

> Pred kúpou overiť: či model umožňuje „OEM unlocking“ v predajnej verzii, dátum konca podpory
> a aktuálny stav podpory modelu v open-source projektoch.

## Zdroje

- Device trees Pixel v AOSP: [9to5Google, 12. 6. 2025](https://9to5google.com/2025/06/12/android-open-source-project-pixel-change/),
  [Android Authority](https://www.androidauthority.com/google-not-killing-aosp-3566882/)
- Fairphone – odomknutie a zamknutie bootloadera: [Fairphone Support](https://support.fairphone.com/hc/en-us/articles/10492476238865),
  komunitné hlásenia: [Fairphone Forum](https://forum.fairphone.com/t/fairphone-6-unbrick-trapped-in-fastboot-mode-with-locked-bootloader-and-corrupted-stock-rom/120168)
- Motorola a GrapheneOS: [Android Authority](https://www.androidauthority.com/grapheneos-motorola-partnership-announced-3645710/)

(overené 31. 10. 2026)
