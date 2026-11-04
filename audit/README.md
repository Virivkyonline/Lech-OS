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
| `…-dnsotls-ds.metric.gstatic.com` | DNS dopyt | – | áno | **Android** | Overenie šifrovaného DNS (náhodná predpona); v 1. audite bol dopyt skrytý v šifrovanom DNS k Google |
| `preprod-remoteprovisioning.googleapis.com` | TCP/443 | áno | áno | **Android** | Atestačné kľúče (RKP) |
| `www.gstatic.com` | TCP/443 | áno | áno | **Android** – zložku treba určiť | Najväčší prenos (64 paketov) |
| `g.co` | TCP/443 | áno | áno | **Android** – zložku treba určiť | Doména Google na krátke odkazy |
| `dns.google` (8.8.8.8) | TCP/853, UDP/443 | áno | **nie** | Testovacie prostredie | Šifrované DNS k Google – zmizlo po oprave DNS |
| `dns.google` (8.8.8.8) | UDP/53 | áno | áno, 8 paketov v čase 0,0 s | Pravdepodobne router OpenWrt | Prichádza spolu s dopytmi routra na `openwrt.pool.ntp.org` (4 servery × IPv4 a IPv6 = 8); potvrdí stĺpec „Odkiaľ“ |
| `9.9.9.9` | UDP/53, TCP/853 | – | áno | Testovacie prostredie | DNS Quad9, ktoré telefónu prideľujeme; Android ho sám prepol na šifrované |
| `*.openwrt.pool.ntp.org` | UDP/123 | áno | áno | Testovacie prostredie | Čas virtuálneho routra |

### Východiskový stav čistého AOSP

Pri prvom spustení, bez akéhokoľvek súhlasu, sa čisté AOSP spojí so **7 cieľmi Google**: dvojitá kontrola
pripojenia, čas, overenie šifrovaného DNS, atestačné kľúče a dve spojenia, ktorých pôvod treba určiť
(`www.gstatic.com`, `g.co`). Výsledok sa v oboch auditoch zhodoval – meranie je opakovateľné.

Tento zoznam je vstupom pre úlohu **E2-06**: v LECH OS každé z týchto spojení nahradíme vlastnou službou,
službou zvolenou používateľom, alebo ho úplne vypneme.

### Ďalšie kroky

1. Potvrdiť, že dopyty na `8.8.8.8` v čase 0,0 s pochádzajú z routra – analýza teraz pri každom cieli uvádza,
   z ktorej siete paket odišiel (stĺpec „Odkiaľ“).
2. Určiť, ktorá časť Androidu volá `www.gstatic.com` a `g.co` – zaznamenať prenosy podľa aplikácií (UID)
   priamo v telefóne počas auditu. To je zároveň prvý krok k [LECH Privacy Control](../docs/12-lech-privacy-control.md).
