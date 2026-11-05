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

