# LECH OS – pokyny pre Claude

LECH OS je vlastný operačný systém pre telefóny postavený na AOSP (Android 17), so zameraním na súkromie
a bezpečnosť. Vízia a plán: `docs/01` až `docs/16`, rozhodnutia `docs/adr/`. **Aktuálny stav a ďalšie kroky:
[docs/stav-prace.md](docs/stav-prace.md) – prečítať ako prvé.**

## Používateľ

- Stanislav (vlastník projektu, firma Lechstav s. r. o.) – **píše a čaká odpovede po slovensky**, nie je programátor.
- Pracuje na notebooku **LECHSPA** (Windows 11 + Ubuntu 24.04 vo WSL na `D:\WSL\Ubuntu`, i9, 64 GB RAM).
  Táto relácia je v cloude a k notebooku nemá prístup: všetko spúšťa on podľa presných príkazov a posiela snímky
  obrazovky (F10 → Ctrl+V).
- Telefón: **Pixel 9 (tokay)**, LECH OS so **zamknutým bootloaderom** a kľúčom AVB LECH OS → nové zostavy len ako
  aktualizácia OTA (`tools/etapa2/11-pixel.sh aktualizovat`), nie fastboot. Kábel do portu USB-A (ľavý USB-C je
  poškodený). adb: `/mnt/c/lech/platform-tools/adb.exe -P 5038`.

## Ako s ním pracovať

- Krátko, vecne, krok za krokom; vždy napísať, či príkaz patrí do **Ubuntu** alebo do **PowerShell (správca)**.
- Odporúčanie namiesto zoznamu možností; nehádať – ak niečo nie je overené, povedať to. Nepredlžovať čakanie
  zbytočnými testami; šetriť kredit (neopakovať veľké výpisy, nečítať zbytočne súbory).
- Pred dlhým meraním alebo testom overiť predpoklady (napr. internet cez prístupový bod).

## Repozitár a postup

- Vetva `claude/lech-os-vision-tudkiw` – commit a push po každej ucelenej zmene; PR len na požiadanie.
- Zmeny AOSP sú záplaty v `patches/<projekt>/` (ADR-0010, `tools/etapa2/aplikuj-zaplaty.sh`), prehľad
  `patches/README.md`. Bezpečnostné opravy z bulletinov: `bezpecnost/`, `tools/etapa2/14-bezpecnost.sh`.
- Produkt `vendor/lech` (Pixel: `vendor/lech/products/lech_tokay.mk`), projekty GrapheneOS `manifests/lech-pixel.xml`.
- Zostava a aktualizácia telefónu (Ubuntu): `cd ~/Lech-OS && git pull && tools/etapa2/12-zostav-pixel.sh --ota
  --verzia <nová verzia> && tools/etapa2/11-pixel.sh aktualizovat` (verzia musí byť vyššia ako v telefóne).
- Postupy a záznam: `docs/11-etapa-2-cuttlefish.md` (časti 4i–4m), audity `audit/README.md`.
- Komentáre v kóde a správy commitov po anglicky, dokumentácia po slovensky.
- **Nikdy** do repozitára ani do správ: podpisové kľúče (`~/lech-keys`), heslá, e-mail používateľa.
