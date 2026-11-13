# Stav práce (12. 11. 2026)

## Hotové

- **Etapa 2 a brána 1 – Overený základ: splnená** ([docs/08](08-plan-etap.md)): LECH OS na Pixeli 9 so zamknutým
  bootloaderom a vlastným kľúčom, 3 OTA aktualizácie s návratom po chybnej, generálka mesačnej aktualizácie,
  0 neznámych spojení pri prvom štarte aj počas 24 h (meranie mimo telefónu, `tools/etapa2/13-meranie-mimo.sh`).
- **Telefón: LECH OS 0.5.7-dev** (OTA 12. 11. 2026 ~21:40 do slotu _b): Organic Maps mimo systému, názov „Mapy“
  a „Obchod aplikácií“ – **overené v telefóne** (13. 11.): názvy, nová ikona Obchodu, Mapy sa otvárajú (kópia v /data). Ďalšia zostava **0.5.8-dev**: **ikona Máp** (obrázok vlastníka 13. 11. → `branding/ikony/mapy`,
  vrstvy `ic_mapy`, tematická ikona = špendlík; cez `lech.os.ikony`). **Mapy znova súčasťou systému** (vlastník: po obnovení výrobných
  nastavení nesmú zmiznúť): Organic Maps overená z F-Droid a **prepodpísaná zostavou** (`default_dev_cert`, v podpísanom
  vydaní kľúč LECH OS) – Soong uloží knižnice nezbalené, Android ich načíta z APK. Aktualizácie Máp prídu s LECH OS,
  nie z F-Droid. Kópia v /data s podpisom F-Droid sa po aktualizácii nahradí systémovou. Overiť: Mapy sa otvoria.
  **GPS:** bez PSDS telefón vonku polohu nenašiel – `frameworks/base` 0009
  teraz berie PSDS z kópie GrapheneOS `samsung.psds.grapheneos.org` (ako GrapheneOS), SUPL ostáva vypnutý. Overiť:
  poloha vonku do ~1 min. **Klávesnica:** HeliBoard vyradený; klávesnica
  Androidu (LatinIME) dostane **slovenský slovník** `main_sk.dict` (codeberg Helium314/aosp-dictionaries, 6 694 001 B,
  SHA-256 a955a81e…, overené 13. 11.; krok v `12-zostav-pixel.sh`, kopíruje do LatinIME `java/res/raw`). Prepínač
  **„Prediktívny text“** na prvej obrazovke nastavení klávesnice (`patches/packages/inputmethods/LatinIME` 0001).
  Overiť: klávesnica po aktualizácii, slovenčina, návrhy slov, prepínač.
- **Predtým 0.5.6-dev** (12. 11. 2026 ~19:45, OTA do slotu _a; zostava len s `--ulohy 4`, s viac úlohami
  došla pamäť pri `services.jar`). V nej: HeliBoard 4.2 a Organic Maps z overeného indexu F-Droid, Obchod aplikácií
  (názov + ikona vlastníka). **Organic Maps v systéme padala** („liborganicmaps.so not found“: APK z F-Droid má
  knižnice zbalené, Android ich systémovým aplikáciám nerozbaľuje, `android_app_import` to nevie). Po
  `adb install -r ~/lech-work/tretie/OrganicMaps.apk` (bežná aplikácia v /data) **funguje**. Preto je od 0.5.7-dev
  zo systému vyradená (kópia v /data zostane); nový telefón ju nainštaluje z Obchodu – neskôr zvážiť inštaláciu pri
  prvom štarte. HeliBoard zapnutý a funguje (slovenčina, rozloženie QWERTY). **Slovenský slovník
  v HeliBoard nie je** (Slovníky: len interné en, bn, bg, fr, el, nl, hu, de, pt …) – ručné pridanie zo súboru
  (codeberg.org/Helium314/aosp-dictionaries) vlastník odmieta („kto má ťukať“): **úloha – slovenský slovník priamo
  v systéme**, bez ťukania; zistiť spôsob (zdroj slovníka overiť, nehádať). Vlastník
  (12. 11.): zložitosť skryť, v klávesnici len **jeden prepínač prediktívneho textu**. Návrh: vlastná zostava
  „LECH Klávesnica“ zo zdrojov HeliBoard (GPL-3.0) so slovenským slovníkom a zjednodušenými nastaveniami, aktualizácie
  cez OTA LECH OS (nie F-Droid) – väčšia úloha, zatiaľ nezačatá. Overiť ešte: Obchod – názov a ikona.
  **V 0.5.7-dev:** Organic Maps mimo systému, názov
  **„Mapy“** (`frameworks/base` 0011, `frameworks/libs/systemui` 0003); ikona Máp (`ic_mapy`) čaká na obrázok od
  vlastníka (ZIP ako pri Obchode).
- **V 0.5.0-dev až 0.5.2-dev (12. 11.), overiť v telefóne:**
  - **Vlastné číslo zostavy** `lech.<verzia>` (`tools/etapa2/05-zostav-aosp.sh`, BUILD_NUMBER). Doteraz mali všetky
    zostavy rovnaký odtlačok (`eng.<používateľ>`), telefón si po aktualizácii nechal starý rozbor systémových aplikácií
    a plocha staré názvy: ikona Nastavení sa volá „Režim v lietadle“ (starý odkaz na text prvej verzie LECH Nastavení).
    Prvý štart 0.5.0-dev trval dlhšie (systém aplikácie načíta nanovo); potom má byť názov „Nastavenia“.
  - **Horné menu (rýchle nastavenia)** – prekrytie `LechSystemUIOverlay` (`vendor/lech/overlay/systemui`): 14 dlaždíc
    rovnakej veľkosti, dve v riadku (Wi-Fi, Mobilné dáta, Bluetooth, Baterka, Režim v lietadle, Otáčanie, Šetrič
    batérie, Režimy, Hotspot, Poloha, Mikrofón, Fotoaparát, Tmavý motív, Nahrávanie obrazovky), bez Peňaženky, Prenosu
    a Ovládania zariadení. V telefóne sa prejaví až po: stiahnuť menu → ceruzka (Upraviť) → Resetovať.
  - **Nastavenia → Zámka SIM karty** (PIN SIM): nová položka pod Sieťou a internetom, otvára
    `Settings$IccLockSettingsActivity` (overené v manifeste Settings 17); ikona `ic_set_sim` nakreslená v štýle
    ostatných (vlastník môže poslať svoju).
  - **Fotoaparát číta QR kódy** (12. 11.): v režime FOTO sa náhľad niekoľkokrát za sekundu dekóduje knižnicou ZXing
    v telefóne (`QrCodes.java`, modul `lech-zxing-core` vo `vendor/lech/kniznice/zxing`, jar z Maven Central so
    súčtami v README). Nájdený kód sa ukáže v pruhu hore: odkaz → Otvoriť (prehliadač), Wi-Fi → Pripojiť (Android sa
    spýta na uloženie siete, heslo sa nezobrazuje), telefón → Zavolať, e-mail → Napísať (zatiaľ niet e-mailovej
    aplikácie), text → Kopírovať. Nie pri videu, pre iné aplikácie ani na zamknutej obrazovke. Kompilácia overená
    mimo AOSP (aapt2, javac, d8 zo SDK 37), dekódovanie overené testom v Jave; v telefóne neoverené.
  - **Dvojité ikony v zozname aplikácií** (Súbory, Nastavenia, PDF Viewer v 0.5.2-dev): skrytie cez prekrytie
    `LechLauncherOverlay` nefungovalo, zoznam je teraz priamo v Launcher3 (`patches/packages/apps/Launcher3` 0002),
    prekrytie odstránené – v 0.5.3-dev **overené** (každá aplikácia raz).
  - **F-Droid** chýbal v 0.5.1/0.5.2-dev (apksigner bez Javy, potom Soong vyžadoval `preprocessed`) – v 0.5.3-dev je.
  - **Vlastná Kalkulačka a PDF podľa návrhov vlastníka** (12. 11., v 0.5.2-dev): `LechKalkulacka` (nahrádza
    ExactCalculator, ten je zo zostavy aj z `manifests/lech-pixel.xml` preč) a `LechPdf` (knižnica PDF; PDF otvára
    prehliadač GrapheneOS `PdfViewerGOS`, jeho ikona na ploche je skrytá). Ikony vlastníka (`branding/ikony`
    kalkulacka, pdf). Kompilácia overená mimo AOSP (SDK 37), výpočty testom (161 kontrol); v telefóne neoverené.
  - **Prehliadač bez slova Vanadium** v Nastaveniach (zoznam aplikácií, Predvolené aplikácie), v dialógoch povolení a
    upozorneniach: záplata `frameworks/base` 0010 (názov „Prehliadač“ a ikona LECH OS). Súčasť systému sa volá
    „Prehliadač – súčasť systému“. V samotnej aplikácii (jej menu) ostáva Vanadium.
  - **F-Droid v systéme** (vlastník: zákazník nemá nič sťahovať ručne): pred každou zostavou ho
    `tools/etapa2/tretie_aplikacie.py` stiahne z f-droid.org, overí balík a certifikát podpisu (SHA-256 zverejnený
    F-Droid v `ClientWhitelist.java` jeho Privileged Extension) a vloží ako `LechFDroid` (podpis vydavateľa, takže
    sa aktualizuje sám). Bez internetu sa použije posledná overená kópia v `~/lech-work/tretie`. F-Droid sa sám
    pripája na f-droid.org (aktualizácia katalógu) – pri ďalšom meraní spojení počítať s ním.

## Rozpracované – etapa 3 (vlastné grafické prostredie)

| Úloha | Stav |
|-------|------|
| E3-01 Prehliadač a WebView Vanadium (ADR-0011) | v telefóne; ikona LECH cez balík ikon plochy (`patches/frameworks/libs/systemui` 0002, modul `LechIkony`) – **overiť v 0.2.0-dev** |
| E3-03 Plocha: tapeta, tvar ikon, 10 ikon (`branding/ikony`), 4 stĺpce s ikonami 66 dp (`patches/packages/apps/Launcher3`) | v zostave **0.2.0-dev** (používateľ zostavuje); po aktualizácii `adb … shell pm clear com.android.launcher3` |
| Nová animácia pri štarte (logo z tapety, kruhy, „YOUR SECURE PHONE“, `tools/make_bootanimation.py`, logo `branding/logo`) | zip v `vendor/lech/bootanimation`, v telefóne od 0.2.1-dev, schválená |

## Ďalšie kroky (poradie, 12. 11. 2026 večer)

Čo chýba oproti bežnému Androidu: [docs/17](17-chyba-oproti-androidu.md). Každá zostava: verzia vyššia než v telefóne,
po aktualizácii hneď zapísať sem.

1. **0.5.4-dev v telefóne (hotové 12. 11.).** Zostáva: QR vo Fotoaparáte. F-Droid aktualizuje sám
   (overené, nič netreba meniť).
2. **Kontakty: import a export .vcf** – zadané vláknu „LECH OS – pokračovanie (etapa 3)“
   (session_013B5DEdokH9dxeZDJMiVh7g) 12. 11.; skontrolovať `git log`, či je hotové, inak dokončiť.
3. **Wi-Fi sa občas odpojí** (sieť Tina29, potom sa pripojí sama): čaká sa na výpis po odpojení
   (`adb … shell "dumpsys wifi | grep -iE 'disconnect|reason' | tail -25"`).
4. **HeliBoard a Organic Maps** – v skripte hotové (12. 11.), prídu v 0.5.5-dev; potom slovenský slovník a
   prípadne HeliBoard ako predvolená klávesnica.
5. **Plocha podľa návrhu:** presklený dock so svietiacim okrajom, tlačidlo všetkých aplikácií v strede (Launcher3).
6. **Server LECH OS (E2-08b):** overenie internetu (prihlásenie do hotelovej Wi-Fi), pomocné dáta A-GPS, push (ntfy).
7. **Banky:** krok 1 z `docs/13` – vyskúšať bankové aplikácie (cez Aurora Store / F-Droid).
8. Meranie spojení po pridaní F-Droid (`tools/etapa2/13-meranie-mimo.sh`, s kontrolným testom internetu).
9. Popri tom: novembrové bezpečnostné opravy na prispôsobenie, neverejná CVE-2026-58868, červené „Your device is
   corrupt“ pri chybnom slote, eSIM (OpenEUICC), E-mail a Počasie, Telefón (Dialer): nepreložené „Contacts“,
   ochranná známka LECH OS (triedy 9, 42).

## Overenie cieľov obrazoviek (11. 11. 2026)

Ciele intentov vlastných aplikácií overené v manifestoch vetvy 17 GrapheneOS: Settings (všetky akcie a aktivity
Nastavení, ALL_FILES, APP_NOTIFICATION, REQUEST_SCHEDULE_EXACT_ALARM, MANAGE_APP_USE_FULL_SCREEN_INTENT),
SettingsIntelligence (`APP_SEARCH_SETTINGS`), PermissionController (`MANAGE_DEFAULT_APPS_SETTINGS`, REQUEST_ROLE),
Telephony (`android.telecom.action.SHOW_CALL_SETTINGS` – tlačidlo Nastavenia v Telefóne), MediaProvider
(`PICK_IMAGES` – výber fotky kontaktu). Chybné boli len položky Nastavení (opravené vyššie).

## Poznámky

- 12. 11. 2026: upozornenie „Pripojené k nešifrovanej sieti“ (4KA SK) – vlastník zapol ochranu pred 2G; odporučený
  PIN namiesto „Potiahnutie“ – **PIN nastavený**.
- Na notebooku je **ToDesk** (vzdialené ovládanie) so zapnutým prístupom – odporučené odinštalovať alebo vypnúť.
- Starý notebook: AOSP zmazané (388 GB), zostáva zmenšiť disk WSL (`C:\lech\zmensi-disk-wsl.ps1` v PowerShell ako
  správca, Ubuntu zatvorené) – stav nepotvrdený.
