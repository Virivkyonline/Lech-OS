# Sieťové audity

Výsledky auditov sieťovej prevádzky virtuálneho telefónu (úloha E2-03, princíp P1 v [docs/01](../docs/01-vizia-a-principy.md)).
Každý audit má vlastný priečinok so správou `sprava.md` a strojovo čitateľným `sprava.json`, ktoré vytvára
[tools/etapa2/03-sietovy-audit.sh](../tools/etapa2/03-sietovy-audit.sh). Súbory so zachytenou prevádzkou (`.pcap`)
v repozitári nie sú – zostávajú na počítači, na ktorom audit prebehol.

## Prehľad

| Dátum | Systém | Prostredie | Výsledok |
|-------|--------|------------|----------|
| 4. 11. 2026 | Čisté AOSP, `aosp-android-latest-release` (build 16644040) | Notebook, WSL, Cuttlefish z hotových obrazov | 13 cieľov mimo telefónu, z toho 9 služieb Google – pozri nižšie |

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

### Ďalší krok

Zopakovať audit s opraveným DNS pre mobilnú sieť. Tak oddelíme správanie Androidu od nastavení testovacieho
prostredia a získame čistý východiskový stav pre úlohu E2-06 (nahradenie spojení na Google).
