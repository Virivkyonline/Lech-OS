# Stav práce (12. 11. 2026)

## Hotové

- **Etapa 2 a brána 1 – Overený základ: splnená** ([docs/08](08-plan-etap.md)): LECH OS na Pixeli 9 so zamknutým
  bootloaderom a vlastným kľúčom, 3 OTA aktualizácie s návratom po chybnej, generálka mesačnej aktualizácie,
  0 neznámych spojení pri prvom štarte aj počas 24 h (meranie mimo telefónu, `tools/etapa2/13-meranie-mimo.sh`).
- **Telefón: LECH OS 0.4.6-dev** (zostava 11. 11. 21:28, OTA do slotu _a). Ďalšia zostava musí mať vyššiu verziu
  (**0.4.7-dev**). Položka Nastavenia → Heslá v 0.4.6-dev otvára celé Nastavenia – oprava v repozitári od 21:54
  (`Settings$AccountDashboardActivity`), príde s 0.4.7-dev. **Čaká na 0.4.7-dev** aj: Nastavenia – každá
  z 18 položiek má jeden overený cieľ (Sieť, Pripojené zariadenia, Aplikácie, Systém, Heslá, Účty a zálohovanie cez
  dashboard aktivity Settings; Režimy cez ZEN_MODE_SETTINGS → `Settings$ModesSettingsActivity`), žiadne tiché náhrady,
  hľadanie bez náhrady na celé Nastavenia. Bezpečnostné opravy september–november
  (okrem neverejných) v zostave.
- **Čaká na 0.4.7-dev (12. 11.):**
  - **Vlastné číslo zostavy** `lech.<verzia>` (`tools/etapa2/05-zostav-aosp.sh`, BUILD_NUMBER). Doteraz mali všetky
    zostavy rovnaký odtlačok (`eng.<používateľ>`), telefón si po aktualizácii nechal starý rozbor systémových aplikácií
    a plocha staré názvy: ikona Nastavení sa volá „Režim v lietadle“ (starý odkaz na text prvej verzie LECH Nastavení).
    Prvý štart 0.4.7-dev potrvá dlhšie (systém aplikácie načíta nanovo); potom má byť názov „Nastavenia“.
  - **Horné menu (rýchle nastavenia)** – prekrytie `LechSystemUIOverlay` (`vendor/lech/overlay/systemui`): 14 dlaždíc
    rovnakej veľkosti, dve v riadku (Wi-Fi, Mobilné dáta, Bluetooth, Baterka, Režim v lietadle, Otáčanie, Šetrič
    batérie, Režimy, Hotspot, Poloha, Mikrofón, Fotoaparát, Tmavý motív, Nahrávanie obrazovky), bez Peňaženky, Prenosu
    a Ovládania zariadení. V telefóne sa prejaví až po: stiahnuť menu → ceruzka (Upraviť) → Resetovať.

## Rozpracované – etapa 3 (vlastné grafické prostredie)

| Úloha | Stav |
|-------|------|
| E3-01 Prehliadač a WebView Vanadium (ADR-0011) | v telefóne; ikona LECH cez balík ikon plochy (`patches/frameworks/libs/systemui` 0002, modul `LechIkony`) – **overiť v 0.2.0-dev** |
| E3-03 Plocha: tapeta, tvar ikon, 10 ikon (`branding/ikony`), 4 stĺpce s ikonami 66 dp (`patches/packages/apps/Launcher3`) | v zostave **0.2.0-dev** (používateľ zostavuje); po aktualizácii `adb … shell pm clear com.android.launcher3` |
| Nová animácia pri štarte (logo z tapety, kruhy, „YOUR SECURE PHONE“, `tools/make_bootanimation.py`, logo `branding/logo`) | zip v `vendor/lech/bootanimation`, v telefóne od 0.2.1-dev, schválená |

## Ďalšie kroky (poradie)

1. **Vlastné aplikácie podľa návrhov vlastníka** (`vendor/lech/aplikacie`, README tam) – v telefóne je 0.4.6-dev
   (Kontakty, Galéria, Súbory, Nastavenia). Pripravené na ďalšiu zostavu: **Telefón** (`PhoneActivity`
   v LechKontakty), **SMS a MMS** (LechSpravy, predvolená pre SMS, nahrádza Messaging; MMS len zobrazenie
   uložených), **Hodiny** (LechHodiny, nahrádza DeskClock), **Kalendár** (LechKalendar, nahrádza Calendar),
   **Fotoaparát** (LechFotoaparat, nahrádza Camera2; vlastný návrh vzhľadu). Vlastník posiela ďalšie návrhy; potom jedna zostava
   a skúšanie všetkého naraz. Kompilácia overená mimo AOSP, správanie nie.
   Otvorené: skryť spúšťač Dialer (zistiť názov komponentu), nové aplikácie dať na plochu, sťahovanie a
   odosielanie MMS.
2. Aplikácia Telefón (Dialer): tlačidlo „Contacts“ nie je preložené – prekrytie reťazca (názov reťazca zistiť
   v `packages/apps/Dialer`). Fotoaparát (Camera2): úvodné okno o polohe má tlačidlo pod navigačnou lištou.
3. Plocha podľa návrhu: presklený dock so svietiacim okrajom, tlačidlo všetkých aplikácií v strede (úprava Launcher3).
4. E3-02 Fotoaparát (Camera2 kreslí pod navigačné tlačidlá) – vlastný alebo moderný otvorený.
5. E3-04 eSIM bez Google (OpenEUICC). Aplikácie E-mail a Počasie (ikony v návrhu plochy).
6. Popri tom: 12 novembrových bezpečnostných opráv na prispôsobenie, neverejná CVE-2026-58868, červené „Your device
   is corrupt“ pri chybnom slote, server LECH OS (E2-08b), ochranná známka LECH OS (triedy 9, 42; firma sa vyberie).

## Overenie cieľov obrazoviek (11. 11. 2026)

Ciele intentov vlastných aplikácií overené v manifestoch vetvy 17 GrapheneOS: Settings (všetky akcie a aktivity
Nastavení, ALL_FILES, APP_NOTIFICATION, REQUEST_SCHEDULE_EXACT_ALARM, MANAGE_APP_USE_FULL_SCREEN_INTENT),
SettingsIntelligence (`APP_SEARCH_SETTINGS`), PermissionController (`MANAGE_DEFAULT_APPS_SETTINGS`, REQUEST_ROLE),
Telephony (`android.telecom.action.SHOW_CALL_SETTINGS` – tlačidlo Nastavenia v Telefóne), MediaProvider
(`PICK_IMAGES` – výber fotky kontaktu). Chybné boli len položky Nastavení (opravené vyššie).

## Poznámky

- Na notebooku je **ToDesk** (vzdialené ovládanie) so zapnutým prístupom – odporučené odinštalovať alebo vypnúť.
- Starý notebook: AOSP zmazané (388 GB), zostáva zmenšiť disk WSL (`C:\lech\zmensi-disk-wsl.ps1` v PowerShell ako
  správca, Ubuntu zatvorené) – stav nepotvrdený.
