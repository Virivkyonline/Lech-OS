# Sieťové audity

Výsledky auditov sieťovej prevádzky virtuálneho telefónu (úloha E2-03, princíp P1 v [docs/01](../docs/01-vizia-a-principy.md)).
Každý audit má vlastný priečinok so správou `sprava.md` a strojovo čitateľným `sprava.json`, ktoré vytvára
[tools/etapa2/03-sietovy-audit.sh](../tools/etapa2/03-sietovy-audit.sh). Súbory so zachytenou prevádzkou (`.pcap`)
v repozitári nie sú – zostávajú na počítači, na ktorom audit prebehol.

## Prehľad

| Dátum | Systém | Prostredie | Výsledok |
|-------|--------|------------|----------|
| 4. 11. 2026 (1.) | Čisté AOSP, `aosp-android-latest-release` (build 16644040) | Notebook, WSL, Cuttlefish z hotových obrazov | 13 cieľov mimo telefónu, z toho 9 služieb Google – pozri nižšie |
| 4. 11. 2026 (2.) | To isté, DNS mobilnej siete opravené na Quad9 | To isté | Android: **7 cieľov Google**, rovnakých ako v 1. audite – [úplná správa](2026-11-04-audit-2/sprava.md) |
| 5. 11. 2026 (3.) | **LECH OS 0.1.0-dev** (`lech_cf_x86_64_only_phone`, vlastná zostava s úpravami E2-06) | Notebook, WSL, Cuttlefish z vlastnej zostavy | **1 cieľ Google** (`g.co`) namiesto 7 – pozri nižšie |
| 5. 11. 2026 (4.) | LECH OS 0.1.0-dev, aj s úpravou `g.co` | To isté | **Žiadne spojenie na Google.** Telefón: len DNS testovacieho prostredia a `europe.pool.ntp.org` – pozri nižšie |
| 6. 11. 2026 (5.) | LECH OS 0.1.0-dev ako GSI (`lech_gsi_arm64`) | **Pixel 9** (vendor Android 17), SIM 4ka, domáca Wi-Fi; záznam priamo v telefóne (`11-pixel.sh audit`), 15 min | 332 paketov, **žiadny dopyt DNS**; mimo lokálnej siete len DNS operátora – pozri nižšie |
| 6. 11. 2026 (6.) | To isté | To isté, počas auditu zapnutá Wi-Fi aj mobilné dáta, 5 min | **2 ciele, oba DNS servery 4ka (TCP/853).** Žiadne spojenie na Google ani inam |

## 1. audit – 4. 11. 2026, čisté AOSP

Telefón po prvom spustení 30 minút bez zásahu používateľa. Tabuľka je prepísaná zo snímky obrazovky správy;
úplná správa (`sprava.md`, `sprava.json`) sa doplní z počítača, na ktorom audit prebehol.

| Cieľ | Protokol/port | Pôvod | Čo to je |
|------|---------------|-------|----------|
| `dns.google` (8.8.8.8) | UDP/53 | Testovacie prostredie → Android | Bežné DNS. Cuttlefish dáva mobilnej sieti virtuálneho telefónu DNS Google (`--ril_dns`, predvolene 8.8.8.8) |
| `dns.google` (8.8.8.8) | TCP/853 | **Android** | Šifrované DNS (DNS over TLS) – Android ho sám zapne voči DNS serveru, ktorý dostal |
| `dns.google` (8.8.8.8) | UDP/443 | **Android** | Šifrované DNS cez HTTP/3 (DoH) – Android ho pozná pre DNS Google |
| `time.android.com` | UDP/123 | **Android** | Synchronizácia času (NTP) |
| `connectivitycheck.gstatic.com` | TCP/80 | **Android** | Kontrola pripojenia na internet (captive portál) |
| `www.google.com` | TCP/443 | **Android** | Kontrola pripojenia cez HTTPS |
| `preprod-remoteprovisioning.googleapis.com` | TCP/443 | **Android** | Poskytovanie atestačných kľúčov (RKP); Cuttlefish používa testovací server Google |
| `g.co` | TCP/443 | **Android** – zložku treba určiť | Doména Google na krátke odkazy |
| `www.gstatic.com` | TCP/443 | **Android** – zložku treba určiť | Statický obsah Google (66 paketov, najviac zo všetkých) |
| `0.–3.openwrt.pool.ntp.org` | UDP/123 | Testovacie prostredie | Čas pre virtuálny Wi-Fi router OpenWrt, ktorý Cuttlefish spúšťa vedľa telefónu – nie je súčasťou Androidu |

### Zistenia

1. **Potvrdené predpoklady** z [docs/11](../docs/11-etapa-2-cuttlefish.md#5-prvá-úloha-zmapovať-kam-volá-čisté-aosp):
   kontrola pripojenia, čas (`time.android.com`), DNS Google a poskytovanie atestačných kľúčov (RKP).
   Metóda auditu teda funguje.
2. **Nové:** Android sám prešiel na šifrované DNS k Google (port 853 a HTTP/3). Pri LECH OS bude predvolený
   šifrovaný DNS server zvolený nami alebo používateľom, nie odvodený od siete.
3. **Treba určiť pôvod** spojení na `g.co` a `www.gstatic.com`. Presne na to slúži priradenie spojení
   k aplikáciám v [LECH Privacy Control](../docs/12-lech-privacy-control.md).
4. **Testovacie prostredie** pridalo DNS Google pre mobilnú sieť a NTP routra OpenWrt. Skripty od 4. 11. 2026
   nastavujú mobilnej sieti DNS Quad9 (`--ril_dns`) a NTP routra OpenWrt je v
   [spec/povolene-spojenia.txt](../spec/povolene-spojenia.txt) označené ako testovacie prostredie.

## 2. audit – 4. 11. 2026, čisté AOSP, opravené DNS testovacieho prostredia

Rovnaký systém aj počítač, telefón spustený s `--ril_dns=9.9.9.9`. Úplná správa:
[sprava.md](2026-11-04-audit-2/sprava.md), [sprava.json](2026-11-04-audit-2/sprava.json).
Android začal komunikovať 20,5 s od začiatku záznamu; všetko pred tým patrí virtuálnemu routru.

| Cieľ | Protokol/port | 1. audit | 2. audit | Pôvod | Čo to je |
|------|---------------|:--------:|:--------:|-------|----------|
| `connectivitycheck.gstatic.com` | TCP/80 | áno | áno | **Android** | Kontrola pripojenia (HTTP) |
| `www.google.com` | TCP/443 | áno | áno | **Android** | Kontrola pripojenia (HTTPS) |
| `time.android.com` | UDP/123 | áno | áno | **Android** | Synchronizácia času |
| `…-dnsotls-ds.metric.gstatic.com` | DNS dopyt | – | áno | **Android** | Overenie šifrovaného DNS (náhodná predpona); v 1. audite bol dopyt pravdepodobne skrytý v šifrovanom DNS k Google |
| `preprod-remoteprovisioning.googleapis.com` | TCP/443 | áno | áno | **Android** | Atestačné kľúče (RKP) |
| `www.gstatic.com` | TCP/443 | áno | áno | **Android** – zložku treba určiť | Najväčší prenos (64 paketov) |
| `g.co` | TCP/443 | áno | áno | **Android** – zložku treba určiť | Doména Google na krátke odkazy |
| `dns.google` (8.8.8.8) | TCP/853, UDP/443 | áno | **nie** | Testovacie prostredie | Šifrované DNS k Google – zmizlo po oprave DNS |
| `dns.google` (8.8.8.8) | UDP/53 | áno | áno, 8 paketov v čase 0,0 s | Testovacie prostredie – **router OpenWrt (potvrdené)** | Dopyty routra na `openwrt.pool.ntp.org` (4 servery × IPv4 a IPv6 = 8); stĺpec „Odkiaľ“ ukazuje sieť Wi-Fi/router OpenWrt |
| `9.9.9.9` | UDP/53, TCP/853 | – | áno | Testovacie prostredie | DNS Quad9, ktoré telefónu prideľujeme; Android ho sám prepol na šifrované |
| `*.openwrt.pool.ntp.org` | UDP/123 | áno | áno | Testovacie prostredie | Čas virtuálneho routra |

### Východiskový stav čistého AOSP

Pri prvom spustení, bez akéhokoľvek súhlasu, sa čisté AOSP spojí so **7 cieľmi Google**: dvojitá kontrola
pripojenia, čas, overenie šifrovaného DNS, atestačné kľúče a dve spojenia, ktorých pôvod treba určiť
(`www.gstatic.com`, `g.co`). Výsledok sa v oboch auditoch zhodoval – meranie je opakovateľné.

Tento zoznam je vstupom pre úlohu **E2-06**: v LECH OS každé z týchto spojení nahradíme vlastnou službou,
službou zvolenou používateľom, alebo ho úplne vypneme.

### Ďalšie kroky

1. ~~Potvrdiť, že dopyty na `8.8.8.8` v čase 0,0 s pochádzajú z routra~~ – **potvrdené 4. 11. 2026**: opätovná
   analýza záznamu 2. auditu so stĺpcom „Odkiaľ“ ukázala sieť Wi-Fi/router OpenWrt. Android sám `8.8.8.8` nepoužil.
2. Určiť, ktorá časť Androidu volá `www.gstatic.com` a `g.co` – zaznamenať prenosy podľa aplikácií (UID)
   priamo v telefóne počas auditu. To je zároveň prvý krok k [LECH Privacy Control](../docs/12-lech-privacy-control.md).
   **5. 11. 2026:** `www.gstatic.com` je sťahovanie zoznamu logov Certificate Transparency (modul Connectivity),
   zistené v zdrojovom kóde. Audit od tohto dňa ukladá aj DNS dopyty podľa aplikácií (časť „Kto sa pýtal“),
   ktoré ukážu pôvod `g.co`.

## Pôvod spojení a zmeny v LECH OS (5. 11. 2026)

| Cieľ | Kto ho robí (Android 17) | LECH OS |
|------|--------------------------|---------|
| `connectivitycheck.gstatic.com`, `www.google.com` | Kontrola pripojenia, modul NetworkStack | Predvolene vypnutá (záplata) |
| `time.android.com` | Sieťový čas, framework | `europe.pool.ntp.org` (prekrytie RRO) |
| `…-dnsotls-ds.metric.gstatic.com` | Overenie šifrovaného DNS, modul DnsResolver | `…-dnsotls-ds.probe.lech.invalid` (záplata) |
| `preprod-remoteprovisioning.googleapis.com` | Atestačné kľúče (RKP), server nastavuje Cuttlefish | Bez servera (záplata) |
| `www.gstatic.com` | Zoznam logov Certificate Transparency, modul Connectivity | Sťahovanie predvolene vypnuté (záplata) |
| `g.co` | Overenie odkazov aplikácií (App Links), `com.android.statementservice`; odkaz `g.co/wallpaper` deklaruje aplikácia na tapety `com.android.wallpaper` | Odkaz bez overovania (záplata) |

Podrobnosti a dôsledky: [docs/11, časť 4c](../docs/11-etapa-2-cuttlefish.md#4c-bez-predvolených-spojení-na-google-úloha-e2-06).

## 3. audit – 5. 11. 2026, LECH OS 0.1.0-dev

Prvý audit LECH OS: vlastná zostava s úpravami z [docs/11, časť 4c](../docs/11-etapa-2-cuttlefish.md#4c-bez-predvolených-spojení-na-google-úloha-e2-06)
(záplaty v [patches/](../patches), čas `europe.pool.ntp.org`), telefón 30 minút po prvom spustení bez zásahu.
Tabuľka je prepísaná zo správy na notebooku (`audit/20261105-075138-prve-spustenie/sprava.md`).
Časť „Kto sa pýtal“ v tomto audite chýba: skript ešte nepoužil `adb root` (opravené).

| Cieľ | Protokol/port | Odkiaľ | Pakety | Prvýkrát | Čo to je |
|------|---------------|--------|-------:|---------:|----------|
| `0.–3.openwrt.pool.ntp.org` | UDP/123 | Wi-Fi/router OpenWrt | 26–28 každý | 0,3 s | Čas virtuálneho routra – testovacie prostredie |
| `dns.google` (8.8.8.8) | UDP/53 | Wi-Fi/router OpenWrt | 8 | 0,0 s | DNS virtuálneho routra – testovacie prostredie; router ho má v konfigurácii OpenWrt z AOSP (`external/openwrt-prebuilts`) |
| `9.9.9.9` | TCP/853, UDP/53 | mobilná sieť telefónu | 38 + 2 | 6,7 s | DNS Quad9 (šifrované), ktoré telefónu prideľuje testovacie prostredie |
| `europe.pool.ntp.org` | UDP/123 | mobilná sieť telefónu | 2 | 6,8 s | **LECH OS:** sieťový čas namiesto `time.android.com` |
| `…-dnsotls-ds.probe.lech.invalid` | DNS dopyt | mobilná sieť telefónu | 1 | 6,8 s | **LECH OS:** overenie šifrovaného DNS – dopyt len na DNS server, názov nikam nevedie |
| `g.co` | TCP/443 | mobilná sieť telefónu | 11 | 18,4 s | **Google – pôvod treba určiť** (`tools/etapa2/kto-sa-pyta.sh`) |

### Zistenia

1. **Zo 7 cieľov Google ostal jeden.** Kontrola pripojenia (`connectivitycheck.gstatic.com`, `www.google.com`),
   `time.android.com`, overenie DNS cez `metric.gstatic.com`, atestačné kľúče (`preprod-remoteprovisioning.googleapis.com`)
   a zoznam Certificate Transparency (`www.gstatic.com`) zmizli. Úpravy z E2-06 teda fungujú tak, ako majú.
2. **Čas funguje** cez európsky pool NTP (požiadavka a odpoveď, 2 pakety).
3. **`g.co`** je krátke spojenie (11 paketov, približne TLS handshake a jedna požiadavka) 12 sekúnd po pripojení
   na sieť. Nesúvisí s kontrolou pripojenia ani so zoznamom CT – ostalo aj po ich vypnutí.
   **Pôvod (5. 11. 2026, `tools/etapa2/kto-sa-pyta.sh`):** dopyt `g***` poslala aplikácia
   `com.android.statementservice` (UID 10069) – služba AOSP, ktorá overuje odkazy aplikácií (Android App Links):
   pre každú doménu, ktorú aplikácia v manifeste označí na overenie, stiahne `https://<doména>/.well-known/assetlinks.json`.
   `adb shell pm get-app-links` ukázal aplikáciu: `com.android.wallpaper: g.co: legacy_failure` – aplikácia na tapety
   (WallpaperPicker2) deklaruje odkaz `https://g.co/wallpaper` s `autoVerify` a overenie zlyháva, lebo `g.co`
   uvádza len aplikáciu Google. Záplata: odkaz bez overovania.
   Druhý dopyt `e***` (systém Android, UID 1000) je čas `europe.pool.ntp.org`.
4. **Testovacie prostredie:** DNS `8.8.8.8` používa len virtuálny router OpenWrt (v čase 0,0 s, pred štartom
   Androidu), nie telefón.

## 4. audit – 5. 11. 2026, LECH OS 0.1.0-dev bez spojení na Google

To isté ako 3. audit, navyše záplata aplikácie na tapety (odkaz `g.co/wallpaper` bez overovania).
Správa na notebooku: `audit/20261105-103322-prve-spustenie/sprava.md` – 7 cieľov, 6 domén, 1 neznáma položka.

| Cieľ | Protokol/port | Odkiaľ | Pakety | Prvýkrát | Čo to je |
|------|---------------|--------|-------:|---------:|----------|
| `dns.google` (8.8.8.8) | UDP/53 | Wi-Fi/router OpenWrt | 8 | 0,0 s | DNS virtuálneho routra – testovacie prostredie (jediná neznáma položka) |
| `0.–3.openwrt.pool.ntp.org` | UDP/123 | Wi-Fi/router OpenWrt | 28–29 každý | 0,3 s | Čas virtuálneho routra – testovacie prostredie |
| `9.9.9.9` | TCP/853, UDP/53 | mobilná sieť telefónu | 12 + 2 | 6,8 s | DNS Quad9 (šifrované), ktoré telefónu prideľuje testovacie prostredie |
| `europe.pool.ntp.org` | UDP/123 | mobilná sieť telefónu | (spolu s routrom 29) | 6,8 s | **LECH OS:** sieťový čas (rovnaký server poolu ako router) |
| `…-dnsotls-ds.probe.lech.invalid` | DNS dopyt | mobilná sieť telefónu | 1 | 6,9 s | **LECH OS:** overenie šifrovaného DNS, len na DNS server |

**Kto sa pýtal** (záznam DNS resolvera v telefóne): jediný dopyt `e***` od systému Android (UID 1000) =
`europe.pool.ntp.org`. Dopyt na overenie DNS posiela resolver sám, preto v zázname nie je.

### Zistenie

**Úloha E2-06 je splnená:** pri prvom spustení a 30 minútach v pokoji sa LECH OS nespojí so žiadnym serverom Google.
Telefón komunikoval len s DNS serverom, ktorý mu pridelila sieť, a so serverom času. Porovnanie: čisté AOSP
sa v rovnakom teste spojilo so 7 cieľmi Google (2. audit).

Ostáva (nie je to spojenie telefónu): DNS `8.8.8.8` virtuálneho routra OpenWrt – dá sa zmeniť v konfigurácii
routra v testovacom prostredí (`external/openwrt-prebuilts`), ak bude treba úplne čistú správu.

## 5. a 6. audit – 6. 11. 2026, LECH OS na Pixeli 9 (E2-11a)

Prvé audity na skutočnom telefóne: LECH OS 0.1.0-dev ako GSI (`lech_gsi_arm64`) na Pixeli 9 s pôvodným vendorom
(Android 17), so serverom RKP Google vypnutým v systéme (`init.lech.rc`), SIM 4ka a domáca Wi-Fi. Prevádzku
zaznamenal `tcpdump` priamo v telefóne (aj mobilná sieť, ktorá nejde cez notebook), vyhodnotenie
`analyzuj_audit.py --zariadenie`: prevádzka telefónu = jeho odchádzajúce pakety, cudzie zariadenia vo Wi-Fi sa
nerátajú. Správy a záznamy sú na notebooku v `~/lech-work/pixel/audit-20261106-073318` a `…-075533`.

| Cieľ | Protokol/port | Odkiaľ | Pakety | Čo to je |
|------|---------------|--------|-------:|----------|
| `84.245.112.80`, `84.245.112.81` | TCP/853 | mobilná sieť telefónu | 6 + 6 | DNS servery mobilnej siete 4ka: Android skúša šifrované DNS (režim Súkromné DNS – automaticky) na DNS serveri, ktorý dala sieť; operátor ho nepodporuje |
| router Wi-Fi, multicast | DHCP, ARP, ICMPv6 | Wi-Fi telefónu | – | lokálna sieť |

### Zistenie

**Na skutočnom telefóne sa LECH OS v pokoji nespojí so žiadnym serverom na internete** – ani Google, ani inde.
Za 15 minút (5. audit) a 5 minút s Wi-Fi aj mobilnými dátami (6. audit) telefón neposlal ani jeden dopyt DNS;
jediné spojenia mimo lokálnej siete sú pokusy o šifrované DNS na DNS serveri mobilnej siete. Server času sa v
auditoch neobjavil – čas sa nastavil pri štarte pred auditom (sieťový čas sa obnovuje raz za deň).

Poznámky:
- DNS servery, ktoré telefónu dajú siete (DHCP, mobilný operátor), audit odteraz zisťuje z telefónu
  (`dumpsys connectivity`) a označí ich ako „DNS siete“ (povolené) – v týchto dvoch auditoch sú ešte neznáme.
- Audit prvého spustenia (hneď po nahratí, pred pripojením Wi-Fi) na Pixeli ešte chýba: telefón mal SIM a
  mobilné dáta už pri štarte, kým audit nebežal.


## 7. audit – 7. 11. 2026, úplný LECH OS na Pixeli 9 (E2-11b)

Prvý audit úplnej zostavy (`lech_tokay-cp2a-userdebug`: vlastný systém, vendor Pixelu z adevtool vrátane IMS,
CarrierConfig2). 15 minút, Wi-Fi aj mobilné dáta 4ka; počas auditu sa telefón používal (Bluetooth, prehliadač,
stránky internetových rádií). Správa: `~/lech-work/pixel/audit-20261107-100635/sprava.md`.

| Kto | Ciele | Čo to je |
|-----|-------|----------|
| prehliadač `org.chromium.webview_shell` (18 dopytov) | `www.google.com`, `googleadservices.com`, `doubleclick.net`, `googlesyndication.com`, `criteo`, `pubmatic`, `flashtalking`, `analytics.yahoo.com`… | vyhľadávanie Google a stránky rádií s reklamami a sledovačmi – **obsah stránok, nie systém**; prvý dopyt až v 125. s |
| vyhľadávacie pole `com.android.quicksearchbox` | `www.google.com` | vyhľadávanie zadané používateľom |
| **gps** (UID 1021), pri štarte | `s***` = **`supl.google.com`** | A-GPS (SUPL) z nastavení operátora Google pre 4ka (`gps.supl_host`); pri SUPL sa posiela identita SIM (IMSI) |
| systém (UID 1000), pri štarte | `1***` = **`1.ssiloc.com`** | predpovede satelitov (PSDS) Samsungu (`gps.longterm_psds_server_1`) |
| systém (UID 1000), pri štarte | `e***` = `europe.pool.ntp.org` | čas – povolené |
| eSIM `com.google.android.euicc`, pri štarte | `l***` (pravdepodobne `lpa.ds.gsma.com`) | vyhľadanie profilov eSIM (GSMA) – bez eSIM zbytočné |

Dopyty „pri štarte“ (10:00:58 – 10:01:00) sú zo záznamu DNS v telefóne (`dumpsys dnsresolver`, z mena len prvé
písmeno); audit sa začal až 10:06:35, ich prevádzka v zázname nie je – domény sú odvodené z nastavení
(`dumpsys carrier_config`: `gps.supl_host`, `gps.longterm_psds_server_1`).

### Zistenie a zmeny

**Bez používania telefón do 125. s nič nevolal; všetko ostatné spôsobil prehliadač.** Systém sa však pri štarte
spojil s Google (SUPL) a Samsungom (PSDS) kvôli GPS. Rozhodnutie (7. 11. 2026): **A-GPS a PSDS predvolene
vypnuté** – záplaty `frameworks/base` 0008 (z GrapheneOS: IMSI ani telefónne číslo sa serveru SUPL neposielajú)
a 0009 (mimo tiesňového volania žiadny server SUPL ani PSDS; GPS samostatne, prvá poloha trvá dlhšie). Neskôr
vlastný sprostredkujúci server na lech-web.sk. Ďalej: prehliadač s blokovaním reklám a sledovačov namiesto
vývojárskeho WebView Shell, aplikácia eSIM od Google predvolene vypnutá, audit hneď od štartu telefónu.

## 8.–10. audit – 7. 11. 2026, 5 minút hneď po štarte (úplný LECH OS)

Telefón počas auditu ležal. **GPS už nikam nevolá** (záplaty `frameworks/base` 0008/0009). Ostali:

| Kto | Cieľ | Čo to je | Riešenie |
|-----|------|----------|----------|
| `com.google.android.euicc` (aplikácia eSIM od Google) | `lpa.live.esimdiscovery.com`, `lpa.ds.gsma.com` | vyhľadanie profilov eSIM (Google, GSMA) pri každom štarte | `pm disable-user` systém pri štarte zrušil; aplikácie eSIM sa vylúčia zo zostavy (adevtool 0004, `lech-no-esim.xml`) |
| systém (UID 1000) | `epdg.epc.mnc003.mcc231.pub.3gppnetwork.org` (len DNS) | ePDG operátora 4ka pre volania cez Wi-Fi (3GPP) | infraštruktúra operátora – povolené |

## 11.–14. audit – 7.–8. 11. 2026, bez aplikácií eSIM (úplný LECH OS)

| Audit | Podmienky | Výsledok |
|-------|-----------|----------|
| 11. (7. 11.) | Zostava bez eSIM (adevtool 0004), 5 min, počas auditu sa používal prehliadač | Žiadne eSIM ani GPS. 7 domén Google (`www.google.com`, `encrypted-tbn0.gstatic.com`, `googleadservices`…) z vyhľadávania v prehliadači |
| 12. (8. 11.) | Zostava z nového zostavovacieho počítača po vymazaní dát; telefón už bežal, mobilné dáta, 5 min | **0 cieľov** – 35 paketov, len lokálna sieť |
| 13. (8. 11.) | Audit začatý ~1 min po reštarte, mobilné dáta (`rmnet1`), 5 min | **0 cieľov, záznam DNS od štartu prázdny** (4 pakety) |
| 14. (8. 11.) – kontrolný | 2 min, počas auditu otvorená Wikipédia cez vyhľadávanie v prehliadači | Zachytené `sk.wikipedia.org` a `*.wikimedia.org` → meranie funguje. K tomu 8 domén Google zo stránky vyhľadávania Google v prehliadači (`www.google.com`, `www.gstatic.com`, `consent.google.com`, `ogads-pa.clients6.google.com`, `play.google.com`, `content-autofill.googleapis.com`, `encrypted-tbn0.gstatic.com`, `www.googleadservices.com`) |

### Zistenie

- **Systém LECH OS sám od seba kontaktuje len časový server** `europe.pool.ntp.org`, a to pri pripojení k sieti
  (`dumpsys network_time_update_service`: synchronizácia 8. 11. 2026 o 7:27 pri zapnutí Wi-Fi, ďalšia o 18 h).
  Žiadne spojenie na Google, eSIM ani GPS; dopyt na ePDG 4ka sa objaví len pri Wi-Fi (volania cez Wi-Fi).
- **Všetky spojenia na Google boli z prehliadača**, keď sa v ňom vyhľadávalo (predvolený vyhľadávač Google). Úloha
  etapy 3: náhrada prehliadača a súkromný predvolený vyhľadávač ([docs/08](../docs/08-plan-etap.md)).
- Audit spustený po štarte nezachytí prvú minútu (štart, `adb root`); záznam DNS z telefónu (`dns-dopyty.txt`)
  ju pokrýva – v 13. audite bol prázdny.

## 15. meranie – 9.–10. 11. 2026, 24 h mimo telefónu (brána 1)

Telefón (LECH OS 0.1.5-dev, zamknutý bootloader) po obnovení výrobných nastavení, bez SIM, len na Wi-Fi prístupovom
bode notebooku; záznam `pktmon` na notebooku 24 h (`tools/etapa2/13-meranie-mimo.sh`, 27 hodinových súborov, 5137
rámcov Wi-Fi, z toho 4111 bez dát – úspora energie). Kontrolný test s prehliadačom pred meraním prevádzku zachytil.

| Doména | Kedy | Kto | Stav |
|--------|------|-----|------|
| `europe.pool.ntp.org` | 0,7 s po pripojení | systém (čas) | povolené |
| `www.googleapis.com` | 1,0 s po pripojení, 2× (DNS, TLS) | `com.android.rkpdapp` (UID 10176): zriadenie DRM Widevine pri štarte | **opravené** záplatou `packages/modules/RemoteKeyProvisioning` 0001 |

Potom 24 h žiadne spojenie. Overenie opravy: prvý štart po obnovení výrobných nastavení s 0.1.6-dev.
