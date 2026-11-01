# 11 – Etapa 2: štart na Cuttlefish

Prvým „zariadením“ LECH OS je **Cuttlefish** – virtuálny telefón, ktorý Google používa ako referenčný cieľ AOSP
([ADR-0005](adr/0005-vyvojove-zariadenie.md)). Beží na Linux serveri, ovláda sa cez prehliadač a hodí sa na
automatické testy. Fyzický Pixel príde na rad, až keď zvládneme celý cyklus na Cuttlefish.

## 1. Zostavovací server

| Parameter | Minimum | Odporúčané |
|-----------|---------|------------|
| Architektúra | x86_64 | x86_64 |
| Operačný systém | Debian / Ubuntu | Ubuntu LTS |
| RAM | 64 GB | 128 GB |
| Voľné miesto na disku | 400 GB | 1 TB NVMe (viac pracovných kópií + cache) |
| Procesor | 16 vlákien | 32+ vlákien |
| Virtualizácia | KVM (`/dev/kvm`) | Dedikovaný (bare-metal) server |

- Cuttlefish potrebuje **KVM**. Na cloudových virtuálnych serveroch musí byť zapnutá vnorená virtualizácia
  (býva pomalšia alebo nedostupná) – preto odporúčame dedikovaný server u európskeho poskytovateľa.
- Bežný notebook ani malý cloudový kontajner nestačí.
- Pripravenosť servera overí skript:

  ```bash
  tools/check_build_host.sh            # kontroluje aktuálny priečinok
  AOSP_DIR=/srv/aosp tools/check_build_host.sh
  ```

## 2. Inštalácia Cuttlefish na server

Podľa oficiálneho repozitára [google/android-cuttlefish](https://github.com/google/android-cuttlefish)
(overené 1. 11. 2026):

```bash
# Kontrola virtualizácie (výsledok musí byť väčší ako 0)
grep -c -w "vmx\|svm" /proc/cpuinfo

# Registrácia apt repozitára Cuttlefish
sudo curl -fsSL https://us-apt.pkg.dev/doc/repo-signing-key.gpg \
    -o /etc/apt/trusted.gpg.d/artifact-registry.asc
sudo chmod a+r /etc/apt/trusted.gpg.d/artifact-registry.asc
echo "deb https://us-apt.pkg.dev/projects/android-cuttlefish-artifacts android-cuttlefish main" \
    | sudo tee -a /etc/apt/sources.list.d/artifact-registry.list
sudo apt update

# Inštalácia balíkov a pridanie používateľa do potrebných skupín
sudo apt install cuttlefish-base cuttlefish-user
sudo usermod -aG kvm,cvdnetwork,render $USER
sudo reboot
```

Balíky sa dajú zostaviť aj zo zdrojového kódu (`tools/buildutils/build_packages.sh` v repozitári android-cuttlefish) –
vhodné, ak nechceme závisieť od apt repozitára Google.

## 3. Stiahnutie a zostavenie AOSP

```bash
# Nástroj repo
mkdir -p ~/bin
curl https://storage.googleapis.com/git-repo-downloads/repo > ~/bin/repo
chmod a+rx ~/bin/repo
export PATH="$HOME/bin:$PATH"

# Zdrojové kódy – vetva odporúčaná Google od roku 2026
mkdir -p /srv/aosp && cd /srv/aosp
repo init --partial-clone -b android-latest-release -u https://android.googlesource.com/platform/manifest
repo sync -c -j8

# Zostavenie pre Cuttlefish
source build/envsetup.sh
lunch aosp_cf_x86_64_only_phone-trunk_staging-userdebug
m
```

> **Overiť pred prvým zostavením:** zoznam balíkov potrebných na zostavenie v Ubuntu a presný názov cieľa pre `lunch`.
> Časť `trunk_staging` (tzv. release konfigurácia) sa medzi vetvami AOSP mení. Produkt `aosp_cf_x86_64_only_phone`
> je overený z testov v repozitári android-cuttlefish. Oficiálny návod je na source.android.com
> (*Set up for AOSP development* a *Use Cuttlefish*). Táto stránka nebola z prostredia, v ktorom vznikol tento dokument,
> dostupná.

## 4. Spustenie virtuálneho telefónu

```bash
launch_cvd --daemon        # spustí virtuálny telefón na pozadí
adb devices                # telefón sa zobrazí ako pripojené zariadenie
adb shell                  # príkazový riadok v telefóne
stop_cvd                   # vypnutie
```

Obrazovku telefónu zobrazí webové rozhranie na **https://localhost:8443** (pri vzdialenom serveri cez SSH tunel,
napr. `ssh -L 8443:localhost:8443 server`).

## 5. Prvá úloha: zmapovať, kam volá čisté AOSP

Skôr než začneme systém upravovať, zmeriame východiskový stav (princíp P1 v [docs/01](01-vizia-a-principy.md)):

1. Spustiť čisté AOSP na Cuttlefish s vymazanými údajmi.
2. Na hostiteľovi zachytávať prevádzku na sieťových rozhraniach, ktoré Cuttlefish vytvorí (zobrazí ich `ip link`
   po spustení), napr. `sudo tcpdump -i <rozhranie> -w prve-spustenie.pcap`.
3. Prejsť sprievodcom prvého spustenia, nechať telefón 30 minút v pokoji, pripojiť a odpojiť sieť.
4. Z DNS dopytov a spojení vytvoriť zoznam cieľov a porovnať ho so zoznamom povolených cieľov.

Kandidáti, ktorých očakávame (z verejne známej konfigurácie AOSP – **potvrdí až audit**):

| Funkcia | Typické predvolené nastavenie AOSP | Náhrada v LECH OS |
|---------|------------------------------------|-------------------|
| Kontrola pripojenia a captive portál | Servery Google (`connectivitycheck.gstatic.com`, `www.google.com/generate_204`) | Vlastný endpoint |
| Synchronizácia času (NTP) | `time.android.com` | Vlastný alebo európsky NTP |
| A-GPS (SUPL) | `supl.google.com` | Proxy alebo nezávislý poskytovateľ |
| Záložný DNS server | Verejný DNS Google | DNS podľa voľby používateľa |
| Údaje pre GNSS (PSDS / XTRA) | Servery výrobcu čipu alebo Google (závisí od zariadenia) | Proxy – rieši Device Support Package |
| Poskytovanie atestačných kľúčov (RKP) | Servery Google (na zariadeniach, ktoré ho používajú) | Posúdiť – súvisí s hardvérovou atestáciou |

Výsledkom je automatický test, ktorý pri každom zostavení zlyhá, ak sa objaví nové, neschválené spojenie.

## 6. Úlohy etapy 2

| ID | Úloha | Hotové, keď |
|----|-------|-------------|
| E2-01 | Zostavovací server | `tools/check_build_host.sh` prejde bez chýb |
| E2-02 | Čisté AOSP na Cuttlefish | Virtuálny telefón nabootuje a je ovládateľný cez prehliadač |
| E2-03 | Sieťový audit čistého AOSP | Zoznam všetkých spojení pri prvom spustení je zapísaný v repozitári |
| E2-04 | Repozitár `lech-manifest` a produkt LECH pre Cuttlefish | Zostaví sa vlastný produkt odvodený od `aosp_cf_x86_64_only_phone` |
| E2-05 | Základná identita | Názov LECH OS a verzia v nastaveniach, vlastná animácia pri štarte |
| E2-06 | Nahradenie spojení na Google | Audit z E2-03 na produkte LECH neukáže žiadne spojenie na Google |
| E2-07 | Vlastné testovacie podpisové kľúče | Zostava je podpísaná kľúčmi LECH (testovacími, nie produkčnými) |
| E2-08 | Server aktualizácií v0 | Cuttlefish nainštaluje aktualizáciu z nášho servera (A/B) |
| E2-09 | CI | Nočné zostavenie, test bootu, sieťový audit a kontroly z `tools/` bežia automaticky |
| E2-10 | Prvé kompatibilitné testy | 10 aplikácií z F-Droid sa nainštaluje a spustí |
| E2-11 | Pixel – Device Support Package | LECH OS nabootuje na Pixeli (2 kusy kúpiť po dokončení E2-02 až E2-06) |
| E2-12 | Pixel – zamknutý bootloader | Pixel bootuje LECH OS so zamknutým bootloaderom a vlastným AVB kľúčom |

Etapa 2 je hotová, keď sú splnené kritériá z [docs/08](08-plan-etap.md#etapa-2--vlastná-zostava-aosp).
