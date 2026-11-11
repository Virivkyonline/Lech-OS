# Stav práce (11. 11. 2026)

## Hotové

- **Etapa 2 a brána 1 – Overený základ: splnená** ([docs/08](08-plan-etap.md)): LECH OS na Pixeli 9 so zamknutým
  bootloaderom a vlastným kľúčom, 3 OTA aktualizácie s návratom po chybnej, generálka mesačnej aktualizácie,
  0 neznámych spojení pri prvom štarte aj počas 24 h (meranie mimo telefónu, `tools/etapa2/13-meranie-mimo.sh`).
- Telefón: posledná overená zostava **0.2.1-dev** (nová plocha a animácia pri štarte – používateľ spokojný). Bezpečnostné opravy september–november
  (okrem neverejných) v zostave.

## Rozpracované – etapa 3 (vlastné grafické prostredie)

| Úloha | Stav |
|-------|------|
| E3-01 Prehliadač a WebView Vanadium (ADR-0011) | v telefóne; ikona LECH cez balík ikon plochy (`patches/frameworks/libs/systemui` 0002, modul `LechIkony`) – **overiť v 0.2.0-dev** |
| E3-03 Plocha: tapeta, tvar ikon, 10 ikon (`branding/ikony`), 4 stĺpce s ikonami 66 dp (`patches/packages/apps/Launcher3`) | v zostave **0.2.0-dev** (používateľ zostavuje); po aktualizácii `adb … shell pm clear com.android.launcher3` |
| Nová animácia pri štarte (logo z tapety, kruhy, „YOUR SECURE PHONE“, `tools/make_bootanimation.py`, logo `branding/logo`) | zip v `vendor/lech/bootanimation`, v telefóne od 0.2.1-dev, schválená |

## Ďalšie kroky (poradie)

1. **0.2.2-dev** (pripravené, používateľ zostavuje): názov „Vanadium“ → „Prehliadač“ (záplata
   `frameworks/libs/systemui` 0003, tabuľka v `LauncherActivityCachingLogic`) a bez aplikácie „Search“
   (QuickSearchBox v `overrides` modulu `LechVanadium`; balík z telefónu neoverený – adb ho vtedy nevidel). Overiť
   na ploche po `pm clear com.android.launcher3`.
2. Plocha podľa návrhu: presklený dock so svietiacim okrajom, tlačidlo všetkých aplikácií v strede (úprava Launcher3).
3. E3-02 Fotoaparát a Galéria (Camera2/Gallery2 kreslia pod navigačné tlačidlá) – vybrať moderné otvorené aplikácie.
4. E3-04 eSIM bez Google (OpenEUICC). Aplikácie E-mail a Počasie (ikony v návrhu plochy).
5. Popri tom: 12 novembrových bezpečnostných opráv na prispôsobenie, neverejná CVE-2026-58868, červené „Your device
   is corrupt“ pri chybnom slote, server LECH OS (E2-08b), ochranná známka LECH OS (triedy 9, 42; firma sa vyberie).

## Poznámky

- Na notebooku je **ToDesk** (vzdialené ovládanie) so zapnutým prístupom – odporučené odinštalovať alebo vypnúť.
- Starý notebook: AOSP zmazané (388 GB), zostáva zmenšiť disk WSL (`C:\lech\zmensi-disk-wsl.ps1` v PowerShell ako
  správca, Ubuntu zatvorené) – stav nepotvrdený.
