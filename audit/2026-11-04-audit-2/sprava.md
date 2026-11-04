# Sieťový audit prvého spustenia – hotové obrazy aosp-android-latest-release/aosp_cf_x86_64_only_phone-userdebug

- Dátum analýzy: 2026-11-04 18:17 CET
- Súbor so zachytenou prevádzkou: `prve-spustenie.pcap`
- Posledný paket telefónu od začiatku záznamu: 1 641.0 s
- Počet rôznych cieľov mimo telefónu: **13**
- Počet rôznych domén: **11**
- Neznáme položky: **14**
- Zoznam povolených spojení: použitý

## Domény

Zdroj: DNS = dopyt na DNS, TLS = názov v šifrovanom spojení (SNI), HTTP = nešifrované HTTP.

| Doména | Zdroj | Počet | Prvýkrát | Stav |
|---|---|---|---|---|
| `0.openwrt.pool.ntp.org` | DNS | 2 | 0.0 s | povolené |
| `1.openwrt.pool.ntp.org` | DNS | 2 | 0.1 s | povolené |
| `2.openwrt.pool.ntp.org` | DNS | 2 | 0.1 s | povolené |
| `3.openwrt.pool.ntp.org` | DNS | 2 | 0.2 s | povolené |
| `www.google.com` | DNS, TLS | 2 | 20.5 s | **NEZNÁME** |
| `connectivitycheck.gstatic.com` | DNS, HTTP | 2 | 20.5 s | **NEZNÁME** |
| `time.android.com` | DNS | 1 | 20.5 s | **NEZNÁME** |
| `xuwyah-dnsotls-ds.metric.gstatic.com` | DNS | 1 | 20.6 s | **NEZNÁME** |
| `www.gstatic.com` | TLS | 5 | 31.4 s | **NEZNÁME** |
| `preprod-remoteprovisioning.googleapis.com` | TLS | 1 | 31.4 s | **NEZNÁME** |
| `g.co` | TLS | 1 | 31.4 s | **NEZNÁME** |

## Ciele (IP adresa, protokol, port)

| IP adresa | Protokol/port | Názvy | Pakety | Prvýkrát | Stav |
|---|---|---|---|---|---|
| 8.8.8.8 | UDP/53 | `dns.google (PTR)` | 8 | 0.0 s | **NEZNÁME** |
| 188.68.34.173 | UDP/123 | `3.openwrt.pool.ntp.org` | 24 | 0.3 s | povolené |
| 193.87.160.18 | UDP/123 | `2.openwrt.pool.ntp.org` | 25 | 0.3 s | povolené |
| 185.242.56.3 | UDP/123 | `1.openwrt.pool.ntp.org` | 25 | 0.3 s | povolené |
| 91.237.170.89 | UDP/123 | `0.openwrt.pool.ntp.org` | 23 | 0.3 s | povolené |
| 9.9.9.9 | TCP/853 | `dns9.quad9.net (PTR)` | 39 | 20.5 s | povolené |
| 9.9.9.9 | UDP/53 | `dns9.quad9.net (PTR)` | 4 | 20.5 s | povolené |
| 142.251.38.131 | TCP/80 | `connectivitycheck.gstatic.com` | 5 | 20.5 s | **NEZNÁME** |
| 142.251.154.119 | TCP/443 | `www.google.com` | 8 | 20.5 s | **NEZNÁME** |
| 216.239.35.8 | UDP/123 | `time.android.com` | 1 | 20.6 s | **NEZNÁME** |
| 142.251.152.120 | TCP/443 | `www.gstatic.com` | 64 | 31.3 s | **NEZNÁME** |
| 172.217.116.4 | TCP/443 | `preprod-remoteprovisioning.googleapis.com` | 13 | 31.3 s | **NEZNÁME** |
| 142.251.141.174 | TCP/443 | `g.co` | 11 | 31.4 s | **NEZNÁME** |

## Lokálna prevádzka (neopúšťa notebook)

| Protokol/port | Pakety |
|---|---|
| 2/0 | 2 |

## Obmedzenia

- Obsah šifrovaných spojení nie je viditeľný – správa ukazuje, kam a koľko, nie čo.
- Ak telefón používa šifrované DNS (DNS over TLS, port 853), názvy domén pochádzajú len z TLS/HTTP a z PTR záznamov; spojenie na port 853 je spojenie na DNS server testovacieho prostredia.
- Názvy označené (PTR) sú odhad z reverzného DNS, nie názov, ktorý telefón skutočne použil.
