# Stav práce (12. 11. 2026)

## Hotové

- **Etapa 2 a brána 1 – Overený základ: splnená** ([docs/08](08-plan-etap.md)): LECH OS na Pixeli 9 so zamknutým
  bootloaderom a vlastným kľúčom, 3 OTA aktualizácie s návratom po chybnej, generálka mesačnej aktualizácie,
  0 neznámych spojení pri prvom štarte aj počas 24 h (meranie mimo telefónu, `tools/etapa2/13-meranie-mimo.sh`).
- **Telefón: LECH OS 0.5.9-dev** (OTA 13. 11. 2026 ~09:00 do slotu _b). Ďalšia zostava **0.5.10-dev**. **Overené 13. 11.:** Mapy
  prepodpísané v systéme sa otvoria (po zmene podpisu ostal starý priečinok `/storage/emulated/0/Android/data/
  app.organicmaps` cudzieho UID → „Permission denied“ pri sťahovaní mapy; zmazaný cez adb, sťahovanie ide – len tento
  telefón, nová inštalácia to nemá); klávesnica LatinIME navrhuje slovenské slová (slovník funguje). Prepínač **„Prediktívny text“** je na
  prvej obrazovke nastavení klávesnice (podržať čiarku → Nastavenia klávesnice Android (AOSP)) – overené. Ikona Máp nová – overené.
  **Neoverené:** GPS vonku. **Čaká na 0.5.10-dev:** názov „Klávesnica“ namiesto „Klávesnica Android (AOSP)“
  (`patches/packages/inputmethods/LatinIME` 0002). V 0.5.9-dev: ikona Máp, Mapy prepodpísané v systéme, slovenský slovník,
  prepínač Prediktívny text, bez HeliBoard, GPS s PSDS z kópie GrapheneOS.
- **Predtým 0.5.7-dev** (OTA 12. 11. 2026 ~21:40 do slotu _b): Organic Maps mimo systému, názov „Mapy“
  a „Obchod aplikácií“ – **overené v telefóne** (13. 11.): názvy, nová ikona Obchodu, Mapy sa otvárajú (kópia v /data). Ďalšia zostava **0.5.8-dev**: **ikona Máp** (obrázok vlastníka 13. 11. → `branding/ikony/mapy`,
  vrstvy `ic_mapy`, tematická ikona = špendlík; cez `lech.os.ikony`). **Mapy znova súčasťou systému** (vlastník: po obnovení výrobných
  nastavení nesmú zmiznúť): Organic Maps overená z F-Droid a **prepodpísaná zostavou** (`default_dev_cert`, v podpísanom
  vydaní kľúč LECH OS) – Soong uloží knižnice nezbalené, Android ich načíta z APK. Aktualizácie Máp prídu s LECH OS,
  nie z F-Droid. Kópia v /data s podpisom F-Droid sa po aktualizácii nahradí systémovou. Overiť: Mapy sa otvoria.
  **GPS:** bez PSDS telefón vonku polohu nenašiel – `frameworks/base` 0009
  teraz berie PSDS z kópie GrapheneOS `samsung.psds.grapheneos.org` (ako GrapheneOS), SUPL ostáva vypnutý – **až v 0.5.9-dev**
  (0.5.8-dev už bežala, keď vznikla). Overiť:
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

## Prechod na základ GrapheneOS (ADR-0012, rozhodnutie vlastníka 13. 11. 2026)

Dôvod: zóna Google (Fio banka a ďalšie aplikácie závislé od Google Play) – [docs/18](18-google-play-v-sandboxe.md).
Súčasná línia `~/aosp` (0.5.x) ostáva, kým prechod neprejde; nový strom je `~/gos`.

1. **Stiahnuť strom GrapheneOS** – `tools/etapa2/15-grapheneos-strom.sh` (značku vydania zapísať do
   `manifests/grapheneos-tag`). Spustené 13. 11. ~11:10, vydanie **2026110800** (pripnuté v `manifests/grapheneos-tag`), sťahuje sa do `~/gos`.
2. **Triedenie záplat – hotové 13. 11.** (`16-triedenie-zaplat.sh`): z 40 je 18 už v GrapheneOS, 13 sadne, 8 konflikt,
   1 chýba projekt. Sada pre nový základ: `patches-gos/` (8 záplat: názvy/ikony, plocha, klávesnica) – prehľad
   `patches-gos/README.md`. Skripty majú režim `LECH_BASE=gos` cez obal `tools/etapa2/gos.sh` (strom `~/gos`, pracovný
   priečinok `~/lech-work-gos`, obrazy `/mnt/c/lech/pixel-gos`). **13. 11. 12:50: `gos.sh 12-priprav-pixel.sh` prešiel
   na prvý raz** (8 záplat nasadených, adevtool si zostavil nástroje za 23 min, `vendor/google_devices/tokay` 1,8 GB).
   **Prvá zostava na GrapheneOS prešla 13. 11. 23:28** (`lech_tokay-cur-userdebug`, 620 min, BUILD_ID
   CP3A.261005.005, opravy 2026-11-01, ARMv9; balík `~/lech-work-gos/pixel-ota/lech-tokay-ota-0.6.0-dev.zip` 1620 MB,
   obrazy `/mnt/c/lech/pixel-gos`). Opravy cestou: BUILD_ID_<produkt> pre `lech_tokay` (`05-zostav-aosp.sh`), kľúč
   `gmscompat_lib` (`06-zostav-lech.sh` dopĺňa chýbajúce kľúče). Bez Obchodu, Máp a ikon (nástroje vznikli až v nej)
   – doplní druhá zostava (hotová 14. 11. 08:33, balík 1675 MB).
   **OTA 0.5.9-dev → 0.6.0-dev 14. 11. zlyhala:** nový slot _a nenaštartoval (príčina neznáma, pstore prázdny),
   telefón sa vrátil do _b a starý systém skončil v recovery „Cannot load Android system“
   (`--prompt_and_wipe_data --reason=set_policy_failed:/data/bootchart` – údaje už starý systém nevie odomknúť).
   Poučenie: prechod medzi základmi nie cez OTA so zachovaním údajov. Postup: `adb reboot sideload`, `adb sideload`
   balíka 0.6.0-dev, v recovery vymazať údaje, reštart.
   **14. 11. dopoludnia:** sideload v starom recovery zlyhal (`kPostInstallMountError`); `aktualizovat --do-recovery`
   + vymazanie údajov v recovery nového slotu: 0.6.0-dev ukáže logo Google, animáciu LECH OS ~2 min a vráti sa do
   0.5.9-dev (tá pri návrate vymaže /data aj /metadata – žiadne stopy; adb sa v novom systéme neohlási, pstore
   prázdny). Ďalší krok: zostava so záznamom štartu na oddiel persist (`init.lech-bootlog.rc`, len userdebug),
   po pokuse prečítať z 0.5.9: `adb shell su root cat /mnt/vendor/persist/lech-bootlog.txt`. V recovery je nápis
   „GrapheneOS Recovery“ – premenovať.
   **14. 11. popoludní:** záznam na persist nevznikol ani raz (3 pokusy). „Holá“ zostava (`LECH_BARE=true`: len
   základ + kľúče LECH OS) tiež nenaštartuje → chyba nie je v doplnkoch LECH OS. Zavádzač: `slot-unbootable:a:yes`,
   `retry-count:a:2`, červená obrazovka – slot odmietnutý, nie obyčajný pád. Firmvér sedí (android-info: bootloader
   ripcurrentpro-17.0-15819938, baseband g5400c-260604-260807-B-16035863 = v telefóne). Ďalší krok: ladiť
   s **odomknutým bootloaderom** (`11-pixel.sh odomknut`, `gos.sh 11-pixel.sh nahrat-uplne`) – bez návratu do starého
   slotu, ktorý maže stopy; potom znova zamknúť (`zamknut`).
   **14. 11. 14:10:** bootloader **odomknutý**; `gos.sh 11-pixel.sh nahrat-uplne` nahral omylom obrazy starej línie
   (cesta `../pixel-uplne` bola v skripte napevno) → telefón beží 0.5.9-dev v slote _b, stav orange. Opravené
   (`wslpath -w`), nahrať znova.
   **14. 11. 14:29 – základ GrapheneOS beží v telefóne:** holá zostava 0.6.0-dev (`LECH_BARE=true`), CP3A.261005.005,
   slot _b, orange (odomknutý), `sys.boot_completed=1`, `app.grapheneos.gmscompat.config` prítomný. Po `nahrat-uplne`
   ostal telefón najprv vo Fastboot Mode („Enter reason: BL1 requested“; mazanie userdata vo fastbootd zlyhá „DCK
   wipe failed“, zmaže sa len metadata) – naštartoval až po kroku v recovery. Prečo zlyhávala cesta cez OTA so
   zamknutým bootloaderom, nie je zistené (kandidáti: prechod medzi základmi cez OTA, overenie pri zamknutom
   bootloaderi) – overiť neskôr OTA v rámci novej línie a po zamknutí. Záznam na persist nevznikol ani pri úspešnom
   štarte – služba `lech-bootlog` nefunguje, opraviť alebo odstrániť. V holej zostave chýba ikona Nastavení
   (Launcher3 0002 ju skrýva, LechNastavenia nie sú). Ďalej: plná zostava LECH OS (bez `LECH_BARE`) cez `nahrat-uplne`.
   **14. 11. 16:31 – plná zostava LECH OS 0.6.1-dev na GrapheneOS beží v telefóne** (`nahrat-uplne --bez-mazania`,
   bootloader odomknutý, slot _b): naše ikony a aplikácie, Mapy, Obchod (F-Droid), „Build number
   LECH-OS-0.6.1-dev-userdebug“. **Telefón: 0.6.1-dev; ďalšia zostava 0.6.2-dev.** Treba upratať (z fotky zoznamu
   aplikácií): dvojice Calculator (GrapheneOS + LECH), Camera (GrapheneOS + LECH), Messaging (GrapheneOS) + SMS
   (LECH), App Store (GrapheneOS) + Obchod aplikácií; aplikácie Auditor a Info; značka GrapheneOS v recovery,
   fastbootd, sprievodcovi a inde; jazyk bol anglický (údaje z holej zostavy) – overiť slovenčinu a predvolené
   aplikácie po čistom štarte. Potom: zamknúť bootloader kľúčom LECH OS, overiť OTA v novej línii, zóna Google + Fio.
   **Upratovanie pripravené pre 0.6.2-dev (14. 11., neskúšané):** `overrides` odstránia Camera, Messaging,
   ExactCalculator, Auditor a InfoApp základu; App Store základu skrytý na ploche (ostáva pre zónu Google, rozhodnutie
   vlastníka); recovery a fastbootd premenované (`patches-gos/bootable/recovery` 0001); úvodný sprievodca s názvom,
   logom LECH OS a po slovensky (`patches-gos/packages/apps/SetupWizard2` 0001; odkaz grapheneos.org/UBL pri
   odomknutom bootloaderi ostal). **0.6.2-dev v telefóne (14. 11. večer):** sprievodca po slovensky, ale záverečná
   obrazovka mala veľké logo GrapheneOS – opravené pre **0.6.3-dev** (záplata odstráni vektorovú ikonu základu, logo
   LECH OS dostane jej meno, platí pre všetky obrazovky). Ďalšie nálezy vlastníka v 0.6.2-dev: App Store základu na
   ploche a diera v docku (predvolená plocha → `Launcher3` 0003 s pevnými aplikáciami LECH OS, aktivity overené
   v telefóne); **chýbajú tri navigačné tlačidlá** (v telefóne zapnuté prekrytie `navbar.gestural`,
   `navigation_mode=2`, `ro.boot.vendor.overlay.theme` prázdne – zistiť, kde ho základ zapína, a predvoliť
   `navbar.threebutton`); „Kontakty nesedia“ – neupresnené (APP_CONTACTS → `lech.os.kontakty`); texty „F-Droid“ vnútri
   Obchodu ostávajú (vlastník súhlasí). **Pripravené pre 0.6.3-dev (neskúšané):** tri tlačidlá predvolene
   (`ro.boot.vendor.overlay.theme=…navbar.threebutton` v `lech_tokay.mk`; v telefóne „Default overlays: …gestural“);
   „Kontakty nesedia“ = ikona Telefón na ploche otvárala Dialer základu namiesto LECH Telefónu → dock v `Launcher3`
   0003 má pevne `lech.os.kontakty/.PhoneActivity`.
   **0.6.3-dev v telefóne (14. 11. 19:00, čistá inštalácia):** tri tlačidlá fungujú (po `patches-gos/build/make`
   0001), sprievodca „Vitajte v LECH OS“ po slovensky, dvojice aplikácií preč, vlastné aplikácie sa otvárajú správne.
   Plocha ešte zlá: launcher základu berie mriežky z `res-stock` (mriežka „medium“, `launcher_4_by_6.db`), nie
   z `res/xml` – `Launcher3` 0003 prepísaná na `res-stock/xml/default_workspace_4x6.xml` pre **0.6.4-dev**. Pozor:
   `Launcher3` 0001 (4 stĺpce, ikony 66 dp) mení `res/xml/device_profiles.xml`, ktorý sa na Pixeli nepoužíva –
   veľkosť ikon upraviť v `res-stock`, ak ju vlastník bude chcieť. App Store základu ostáva v Nastavenia → Aplikácie
   (návrh: premenovať na neutrálny názov).
   **Obrazovka hovoru (nález vlastníka 14. 11.):** na novom základe je Dialer GrapheneOS – plochá modrá obrazovka
   hovoru, nepreložené „Record call“; v 0.5.x bola obrazovka hovoru z AOSP Dialera (vlastník: „pekné pozadie“).
   LECH OS vlastnú obrazovku hovoru nemá (LechKontakty nemá InCallService). Úloha: vzhľad obrazovky hovoru v štýle
   LECH OS (prekrytie farieb/pozadia Dialera alebo vlastná obrazovka) a preklad „Record call“.
   **Google Play pre zákazníka (rozhodnutie vlastníka 14. 11.):** Aurora Store sa NEpredinštaluje; v LECH
   Nastaveniach stránka „Aplikácie z Google Play“ (`GooglePlayActivity`, zobrazí sa len keď je v systéme App Store
   základu): tlačidlo „Zapnúť zónu Google“ otvorí v App Store základu stránku Google Play services
   (`ACTION_SHOW_APP_INFO`, inak hlavnú obrazovku, inak hlásenie) + neutrálna informácia o aplikáciách tretích strán
   (menuje Aurora Store; text dať pozrieť právnikovi). Pre **0.6.5-dev**, neskompilované ani neskúšané. Neskôr:
   rovnaká zmienka v sprievodcovi, Android Auto (prepínače), skúška Fio. Android Auto: vlastník má v aute vlastné
   aplikácie mimo Play (režim pre vývojárov Android Auto) – overiť pri skúške.
   **0.6.4-dev v telefóne (14. 11. 20:20):** App Store základu už na ploche nie je (vlastník); v Nastavenia →
   Aplikácie sa od 0.6.5-dev volá „Zóna Google“ (`frameworks/base` 0011). Fotka plochy zatiaľ neprišla – rozloženie
   (Správy, Obchod, Galéria, Mapy, Nastavenia) potvrdiť.
   **Dávka pre 0.6.5-dev (pripravené 14. 11. večer, neskúšané):** stránka „Aplikácie z Google Play“; názov „Zóna
   Google“; `packages/apps/Dialer` 0001 (obrazovka hovoru nad tapetou – základ mal `windowShowWallpaper=false`,
   farby prechodu rovnaké; preklad nahrávania hovorov); odstránená nefunkčná služba `lech-bootlog`. Ikony plochy:
   mriežka „medium“ základu má už 4 stĺpce a ikony 66 dp – netreba meniť. Odložené: zmienka o Google Play
   v sprievodcovi (vyžaduje novú obrazovku), Android Auto, skúška Fio.
   **Telefón: 0.6.5-dev (14. 11. 21:20, `nahrat-uplne --bez-mazania`, zostava 30 min, všetky záplaty sadli);
   ďalšia zostava 0.6.6-dev.** V telefóne overiť: obrazovka hovoru, „Nahrať hovor“, stránka „Aplikácie z Google
   Play“ a tlačidlo zóny Google, potom Fio.
   **0.6.5-dev v telefóne – nálezy:** tapeta za hovorom presvitá a „Nahrať hovor“ je preložené, ale farba prechodu
   závisí od kontaktu (ružová pri neznámom čísle) → `Dialer` 0002: pevný tmavý prechod LECH OS (pre 0.6.6-dev).
   Položka „Aplikácie z Google Play“ sa v LECH Nastaveniach nezobrazila – príčina nezistená (overiť
   `am start -n lech.os.nastavenia/.GooglePlayActivity` a `pm list packages app.grapheneos.apps`).
   Overené: stránka existuje a otvorí sa (`am start`), App Store je nainštalovaný, názov „Zóna Google“
   v Nastavenia → Aplikácie funguje; riadok v zozname LECH Nastavení vlastník nenašiel → podmienka odstránená, riadok
   sa zobrazuje vždy (0.6.6-dev). Drobnosť: Obchod aplikácií má v zozname systému ikonu F-Droid, nie našu.
   **ZÓNA GOOGLE FUNGUJE (14. 11. 2026 ~21:55, 0.6.5-dev):** tlačidlo „Zapnúť zónu Google“ otvorilo App Store
   základu (celý zoznam, nie stránku Google Play services – `SHOW_APP_INFO` nezabral; v zozname je všade meno
   GrapheneOS → neskôr vlastná inštalačná obrazovka). Vlastník nainštaloval Google Play services (+ GmsCompat Config,
   Play Store), bez účtu Google; Aurora Store z Obchodu aplikácií; **Fio banka (`sk.fio.sb2`) prešla cez obrazovku
   polohy a funguje** – cieľ prechodu na GrapheneOS (ADR-0012) splnený. Ďalej: zamknúť bootloader kľúčom LECH OS
   a overiť OTA v novej línii, meranie súkromia (čaká na vlastníka), Android Auto, vlastná inštalačná obrazovka.
   **Telefón: 0.6.6-dev (14. 11. 22:09, `nahrat-uplne --bez-mazania`, zostava 16 min); ďalšia zostava 0.6.7-dev.**
   **Overené vlastníkom 14. 11. 22:15:** obrazovka hovoru tmavá (prechod LECH OS), Fio funguje aj po aktualizácii,
   riadok „Aplikácie z Google Play“ je v hlavnom zozname LECH Nastavení (vlastník ho hľadal vnútri „Aplikácie“). Chyba v skripte:
   pri bežiacej zostave v inom okne hlási zámok „zostavenie nebeží… kill PID“ (`lech_build_lock_report`) – opraviť.
   **BOOTLOADER ZAMKNUTÝ kľúčom LECH OS (14. 11. 22:16, `gos.sh 11-pixel.sh zamknut`, odtlačok 95318521…f4fb)
   a 0.6.6-dev so zamknutým bootloaderom NAŠTARTOVAL** (fotka rýchlych nastavení 22:21) – zamknutý štart nového
   základu teda funguje; ranné zlyhanie bolo v ceste cez OTA zo starej línie. Zostáva overiť OTA v rámci novej línie
   (0.6.6 → 0.6.7). Údaje vymazané: zónu Google, Aurora a Fio nainštalovať znova.
   Nález: rýchle nastavenia sú olivovozelené – systém odvodzuje farby z tapety (SystemUI `ThemeOverlayController`,
   farba z tapety); vlastník: „tak to nemá byť“ → určiť pevnú farebnú tému LECH OS (zistiť, akú chce).
   Rozhodnutie vlastníka: predvolená **neutrálna čiernobiela** téma, kto chce, zmení si ju. Pripravené pre
   0.6.7-dev: prekrytie `theming_defaults` = `*|MONOCHROMATIC|#808080` (`vendor/lech/overlay/frameworks-res`); platí
   len pri prvom štarte (sprievodca), neskúšané. Plocha podľa plánu potvrdená fotkou (22:25).
   **OTA 0.6.6 → 0.6.7 (14. 11. 22:57) odmietnutá skriptom „balík nie je novší“:** envsetup.sh základu berie
   BUILD_DATETIME z `out/build_date.txt` predošlej zostavy → všetky zostavy mali rovnaký dátum. Opravené
   v `05-zostav-aosp.sh` (vlastný BUILD_DATETIME pre každú zostavu); zostaviť znova ako 0.6.7-dev a aktualizovať.
   **Súkromie na novom základe NIE JE zmerané** (24 h testy „0 neznámych spojení“ platili len pre 0.5.x). Základ sa
   hlási serverom GrapheneOS (kontrola internetu, čas, PSDS, aktualizácie, App Store – podľa ich dokumentácie);
   záplaty LECH OS na vypnutie spojení (Connectivity, DnsResolver, NetworkStack…) sú v konflikte a neprenesené.
   Meranie (`gos.sh 11-pixel.sh audit`, potom 24 h) **odložené – vlastník povie kedy** (14. 11.). Zostáva: ďalšie výskyty mena GrapheneOS v Nastaveniach, texty „Vanadium“ vnútri prehliadača (len vlastnou
   zostavou prehliadača), služba `lech-bootlog` (nefunguje).
3. `tools/etapa2/gos.sh 12-priprav-pixel.sh` (adevtool si zostaví nástroje a vytvorí podporu Pixelu), potom
   `tools/etapa2/gos.sh 12-zostav-pixel.sh --ota --verzia 0.6.0-dev --ulohy 4`; prvá skúška aktualizáciou OTA
   z 0.5.9-dev (pri zlyhaní štartu sa telefón vráti do starého slotu), inak čistá inštalácia.
4. Vypnúť/presmerovať spojenia na servery GrapheneOS, sieťový audit, preskúšanie telefónu, zóna Google + Fio.

## Ďalšie kroky (poradie, 13. 11. 2026)

Čo chýba oproti bežnému Androidu: [docs/17](17-chyba-oproti-androidu.md). Každá zostava: verzia vyššia než v telefóne,
po aktualizácii hneď zapísať sem. Hotové 12.–13. 11.: Obchod aplikácií (F-Droid, názov, ikona), Mapy v systéme
(názov, ikona), klávesnica so slovenským slovníkom a prepínačom Prediktívny text.

1. **Banky (vlastník, bez zostavy):** z Obchodu aplikácií nainštalovať Aurora Store, z neho aplikáciu svojej banky,
   zapísať, čo funguje (krok 1 z `docs/13`).
   **Fio banka (`sk.fio.sb2`), 13. 11., 0.5.9-dev, z Aurora Store:** spustí sa, na obrazovke „Prístup k polohe“ po
   udelení povolenia nejde ďalej – logcat: „requires the Google Play Store, but it is missing“, služba polohy Google
   (`com.google.android.gms…location`) `SERVICE_INVALID`. **Bez služieb Google Play nefunguje** → potrebná zóna Google
   (docs/18, vrstva kompatibility GrapheneOS) – zvyšuje jej prioritu.
   **Tatra banka, 13. 11., 0.5.9-dev:** aplikácia Tatra banka aj **Čítačka** fungujú bez Googlu, vrátane
   prihlásenia („ide ako má“; z Aurora Store). Platenie mobilom v aplikácii vlastník nepozná – neoverené.
2. **Kontakty: import a export .vcf** – vlákno session_013B5DEdokH9dxeZDJMiVh7g nič nepushlo (13. 11.); dokončiť tu,
   do zostavy 0.5.10-dev spolu s názvom „Klávesnica“.
3. **GPS vonku** – overiť polohu do ~1 min (PSDS z kópie GrapheneOS, v 0.5.9-dev). **QR vo Fotoaparáte** – overiť.
4. **Wi-Fi sa občas odpojí** (sieť Tina29): čaká sa na výpis po odpojení
   (`adb … shell "dumpsys wifi | grep -iE 'disconnect|reason' | tail -25"`).
5. **Plocha podľa návrhu:** presklený dock so svietiacim okrajom, tlačidlo všetkých aplikácií v strede (Launcher3).
6. **Server LECH OS (E2-08b):** overenie internetu (prihlásenie do hotelovej Wi-Fi), vlastné PSDS, push (ntfy).
   **Hardvér:** odpovede výrobcov – [docs/19](19-dopyt-na-vyrobcov-hardveru.md) (čaká sa na Connectech/UNIWA a Blackview).
7. **Banky:** krok 1 z `docs/13` – vyskúšať bankové aplikácie (cez Aurora Store / F-Droid).
   **Platenie mobilom (NFC)** bez Google Peňaženky – kandidáti na skúšku (13. 11., zdroje: fóra GrapheneOS a Privacy
   Guides, neoverené na LECH OS): Curve Pay (EHP, bez Play Integrity; v 2026 hlásené aj výpadky), vlastné NFC platenie
   v aplikácii banky, Garmin Pay. **Google v LECH OS:** návrh „zóna Google“ (oddelený profil, prehľad oprávnení,
   obmedzené údaje namiesto skutočných) – zapísané v [docs/18](18-google-play-v-sandboxe.md) a ADR-0003 (upresnenie 13. 11.); neimplementované.
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
