# 11 – Etapa 2: štart na Cuttlefish

Prvým „zariadením“ LECH OS je **Cuttlefish** – virtuálny telefón, ktorý Google používa ako referenčný cieľ AOSP
([ADR-0005](adr/0005-vyvojove-zariadenie.md)). Beží na Linux serveri, ovláda sa cez prehliadač a hodí sa na
automatické testy. Fyzický Pixel príde na rad, až keď zvládneme celý cyklus na Cuttlefish.

## Rýchly štart na notebooku

Postup od čistého notebooku po prvý sieťový audit. Skripty sú v [tools/etapa2](../tools/etapa2).

1. **Ubuntu 24.04 LTS** nainštalované priamo na notebooku a v BIOS/UEFI zapnutá **virtualizácia**
   (Intel VT-x alebo AMD-V / SVM).
2. **Stiahnuť repozitár** (ak je súkromný, Git si vypýta prihlásenie do GitHubu – heslo nahrádza osobný token):

   ```bash
   sudo apt install -y git
   git clone -b claude/lech-os-vision-tudkiw https://github.com/Virivkyonline/Lech-OS.git
   cd Lech-OS
   ```

3. **Príprava notebooku** – balíky, Cuttlefish, DNS pre virtuálny telefón, skupiny, nástroj `repo`:

   ```bash
   tools/etapa2/01-priprava-notebooku.sh            # pri 32 GB RAM pridajte: --swap 32
   sudo reboot
   ```

4. **Kontrola pripravenosti:** `tools/check_build_host.sh`
5. **Vyskúšať čisté AOSP** (bez zostavovania, stiahne hotové obrazy):

   ```bash
   tools/etapa2/02-ciste-aosp.sh                    # obrazovka telefónu: https://localhost:8443
   cvd remove                                       # po vyskúšaní odstrániť
   ```

6. **Sieťový audit prvého spustenia** (úloha E2-03, trvá približne 40 minút):

   ```bash
   tools/etapa2/03-sietovy-audit.sh
   git add audit/ && git commit -m "Audit prvého spustenia čistého AOSP" && git push
   ```

7. Potom **zostavenie zo zdrojových kódov** (časť 3) – stiahne stovky GB a prvé zostavenie trvá hodiny.

## 1. Zostavovací počítač

Na zostavenie stačí **server alebo dostatočne vybavený notebook či stolný počítač**. Rozhoduje RAM, rýchly disk
a virtualizácia KVM.

| Parameter | Minimum | Odporúčané |
|-----------|---------|------------|
| Architektúra | x86_64 (Intel / AMD) | x86_64 |
| Operačný systém | Linux (Ubuntu / Debian) nainštalovaný priamo | Ubuntu LTS |
| RAM | 32 GB + 32 GB swap (pomalšie, menej súbežných úloh) | 64 GB a viac (oficiálne odporúčanie Google) |
| Voľné miesto na disku | 400 GB SSD | 1 TB NVMe |
| Procesor | 8 jadier | 16+ jadier |
| Virtualizácia | KVM (`/dev/kvm`) zapnutá v BIOS/UEFI | – |

Pripravenosť počítača overí skript – rozlíši, či počítač zvládne **zostavenie**, alebo len **spustenie hotových
obrazov** (časť 2b):

```bash
tools/check_build_host.sh            # kontroluje aktuálny priečinok
AOSP_DIR=/srv/aosp tools/check_build_host.sh
```

### Notebook ako zostavovací počítač

Notebook môže byť zostavovací počítač, ak spĺňa minimum z tabuľky. Praktické rady:

- **Linux nainštalovaný priamo**, nie vo virtuálnom stroji. Ak je na notebooku Windows, odporúčame Ubuntu na samostatný
  disk (druhý interný NVMe alebo rýchly externý NVMe cez USB4 / Thunderbolt). Vo WSL sa dá zostavovať, ale Cuttlefish
  tam oficiálne podporovaný nie je.
- **Pri 32 GB RAM** treba zväčšiť swap (súbor alebo oddiel aspoň 32 GB) a zostavovať s menším počtom súbežných úloh,
  napr. `m -j8`. Pri 16 GB RAM zostavenie AOSP prakticky nie je možné.
- Mnohé notebooky majú RAM prispájkovanú – pred kúpou rozšírenia overiť, či sa dá vymeniť.
- Prvé úplné zostavenie potrvá na notebooku **niekoľko hodín**, ďalšie (inkrementálne) desiatky minút alebo menej.
  Notebook musí byť na sieti, dobre chladený a s vypnutým uspávaním.

### Počítač s Windows: Ubuntu vo WSL

Na vyskúšanie hotových obrazov a prvý sieťový audit sa dá použiť Ubuntu vo WSL, bez zásahu do Windows.
Na zostavovanie vlastného LECH OS odporúčame Ubuntu priamo na disku (napr. na externom NVMe cez USB-C).

1. V PowerShell (ako správca): `wsl --install -d Ubuntu-24.04`, potom reštart Windows a znova ten istý príkaz –
   nainštaluje Ubuntu a vypýta si meno a heslo.
2. V Ubuntu pracujte v domovskom priečinku (`cd ~`), nie v `/mnt/c/...` – priečinky Windows sú vo WSL pomalé.
3. **Reštart Ubuntu** (napr. po pridaní do skupín) sa robí v PowerShell príkazom `wsl --shutdown`
   a opätovným otvorením Ubuntu, nie reštartom Windows.
4. **RAM:** WSL predvolene dostane polovicu RAM počítača. Limit sa dá zvýšiť v súbore
   `C:\Users\<meno>\.wslconfig`, napr. pri 32 GB RAM:

   ```ini
   [wsl2]
   memory=24GB
   swap=32GB
   ```

   Zmena platí po `wsl --shutdown`.
5. **Obmedzenie:** Cuttlefish potrebuje okrem `/dev/kvm` aj zariadenia `/dev/vhost-vsock` a `/dev/vhost-net`.
   Ak ich jadro WSL neposkytuje, `tools/check_build_host.sh` to označí – vtedy je potrebné Ubuntu priamo na disku.
6. **Sandbox crosvm:** vo WSL zlyhá izolácia virtuálneho stroja (minijail: `cannot mount '/usr/lib' …
   Invalid argument`, potom `VIRTUAL_DEVICE_BOOT_FAILED`). Skripty `02-ciste-aosp.sh` a `03-sietovy-audit.sh`
   ju preto vo WSL vypínajú (`"vm": {"crosvm": {"enable_sandbox": false}}`). Pre testovací telefón je to prijateľné;
   na Ubuntu priamo na disku zostáva sandbox zapnutý. Prepísať sa to dá premennou `LECH_CF_SANDBOX=0` alebo `1`.

### Prečo nestačí bežný VPS

Lacné virtuálne servery (VPS) majú spravidla málo RAM na zostavenie AOSP (16–24 GB) a hlavne **nesprístupňujú KVM**,
takže Cuttlefish na nich nespustíme vôbec. Ak nebude k dispozícii vlastný počítač, treba **dedikovaný (bare-metal)
server** s 64 GB RAM u európskeho poskytovateľa – nie VPS.

## 2. Inštalácia Cuttlefish

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

## 2b. Bez zostavovania: hotové obrazy AOSP

Čisté AOSP pre Cuttlefish Google zostavuje automaticky a dá sa stiahnuť. Na spustenie stačí počítač s KVM,
16 GB RAM a približne 50 GB voľného miesta. Takto sa dajú urobiť úlohy **E2-02 a E2-03** (spustenie čistého AOSP
a sieťový audit) ešte pred tým, ako bude k dispozícii počítač na zostavovanie.

Podľa nápovedy nástroja `cvd` v repozitári android-cuttlefish (overené 1. 11. 2026) – súbor `spec.json`:

```json
{
  "instances": [
    {
      "name": "aosp-baseline",
      "disk": {
        "default_build": "@ab/aosp-android-latest-release/aosp_cf_x86_64_only_phone-userdebug"
      }
    }
  ]
}
```

```bash
cvd create --config_file=spec.json   # stiahne hotové obrazy a spustí virtuálny telefón
cvd fleet                            # stav bežiacich zariadení
```

Vlastný LECH OS (úlohy E2-04 a ďalej) už vyžaduje zostavenie zo zdrojového kódu.

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

Po vlastnom zostavení (v tom istom termináli, kde prebehol `lunch` a `m`), podľa nápovedy nástroja `cvd`:

```bash
cvd create                 # spustí virtuálny telefón zo zostavených obrazov
adb devices                # telefón sa zobrazí ako pripojené zariadenie
adb shell                  # príkazový riadok v telefóne
cvd stop                   # vypnutie; po zmene a novom zostavení (m) znova cvd start
cvd remove                 # odstránenie zariadenia a jeho údajov
```

Obrazovku telefónu zobrazí webové rozhranie na **https://localhost:8443** – presnú adresu vypíše `cvd create`
(pri vzdialenom počítači cez SSH tunel, napr. `ssh -L 8443:localhost:8443 server`).

Ak telefón nenaštartuje (`VIRTUAL_DEVICE_BOOT_FAILED`), spustite `tools/etapa2/diagnostika.sh` – vypíše stav služieb,
sieťových rozhraní, zariadení a chybové riadky z logov na jednu obrazovku a úplnú správu uloží do `~/lech-work`.

## 5. Prvá úloha: zmapovať, kam volá čisté AOSP

Skôr než začneme systém upravovať, zmeriame východiskový stav (princíp P1 v [docs/01](01-vizia-a-principy.md)):

Celý postup robí skript `tools/etapa2/03-sietovy-audit.sh`:

1. Odstráni existujúce virtuálne telefóny, aby audit začínal od čistého prvého spustenia.
2. **Ešte pred štartom telefónu** spustí záznam prevádzky na sieťach virtuálneho telefónu
   (192.168.93.x – 192.168.99.x, podľa konfigurácie `cuttlefish-base`). Prevádzka samotného notebooku sa nezaznamenáva.
3. Vytvorí čerstvý telefón – z hotových obrazov AOSP, alebo s parametrom `--lokalna-zostava` z vlastnej zostavy.
4. Zaznamenáva 30 minút (`--minuty N`), potom záznam ukončí a telefón odstráni (`--ponechat` ho nechá bežať).
5. Analýza (`tools/etapa2/analyzuj_audit.py`, používa `tshark`) vytvorí správu `audit/<dátum>-prve-spustenie/sprava.md`
   a `sprava.json`: domény z DNS, TLS a HTTP, cieľové IP adresy a porty, čas prvého spojenia. Porovná ich so
   zoznamom povolených spojení [spec/povolene-spojenia.txt](../spec/povolene-spojenia.txt).

> **Testovacie prostredie:** Cuttlefish predvolene prideľuje virtuálnemu telefónu DNS servery Google (8.8.8.8).
> Prípravný skript ich mení na Quad9 (9.9.9.9) v súbore `/etc/default/cuttlefish-host-resources`, aby audit
> nemiešal správanie Androidu s nastavením testovacieho prostredia. Ak telefón použije šifrované DNS (port 853),
> názvy domén v správe pochádzajú z TLS, HTTP a reverzného DNS.

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

| ID | Úloha | Hotové, keď | Stav |
|----|-------|-------------|------|
| E2-01 | Zostavovací počítač (server alebo notebook) | `tools/check_build_host.sh` skončí výsledkom „zvládne zostavenie aj spustenie“ | Čiastočne – notebook s Windows a WSL spustí hotové obrazy; na zostavenie treba viac RAM pre WSL alebo Ubuntu priamo na disku |
| E2-02 | Čisté AOSP na Cuttlefish | Virtuálny telefón nabootuje a je ovládateľný cez prehliadač | **Hotové 4. 11. 2026** – WSL na notebooku, hotové obrazy `aosp-android-latest-release` (build 16644040), sandbox crosvm vypnutý, obrazovka cez prehliadač |
| E2-03 | Sieťový audit čistého AOSP | Zoznam všetkých spojení pri prvom spustení je zapísaný v repozitári | **Hotové 4. 11. 2026** – dve merania; čisté AOSP sa pri prvom spustení spojí so 7 cieľmi Google ([audit/README.md](../audit/README.md)); zostáva určiť pôvod 2 spojení |
| E2-04 | Repozitár `lech-manifest` a produkt LECH pre Cuttlefish | Zostaví sa vlastný produkt odvodený od `aosp_cf_x86_64_only_phone` | – |
| E2-05 | Základná identita | Názov LECH OS a verzia v nastaveniach, vlastná animácia pri štarte | – |
| E2-06 | Nahradenie spojení na Google | Audit z E2-03 na produkte LECH neukáže žiadne spojenie na Google | – |
| E2-07 | Vlastné testovacie podpisové kľúče | Zostava je podpísaná kľúčmi LECH (testovacími, nie produkčnými) | – |
| E2-08 | Server aktualizácií v0 | Cuttlefish nainštaluje aktualizáciu z nášho servera (A/B) | – |
| E2-09 | CI | Nočné zostavenie, test bootu, sieťový audit a kontroly z `tools/` bežia automaticky | – |
| E2-10 | Prvé kompatibilitné testy | 10 aplikácií z F-Droid sa nainštaluje a spustí | – |
| E2-11 | Pixel – Device Support Package | LECH OS nabootuje na Pixeli (2 kusy kúpiť po dokončení E2-02 až E2-06) | – |
| E2-12 | Pixel – zamknutý bootloader | Pixel bootuje LECH OS so zamknutým bootloaderom a vlastným AVB kľúčom | – |

### Záznam z prvého spustenia (4. 11. 2026)

Prvé spustenie na skutočnom počítači (notebook, Windows 11, Ubuntu 24.04 vo WSL, 20 vlákien, 15 GB RAM pre WSL):

1. `01-priprava-notebooku.sh` – balíky aj Cuttlefish sa nainštalovali; moduly `vhost_vsock` a `vhost_net` sa vo WSL načítali.
2. Prvý štart zlyhal: sandbox crosvm (minijail) vo WSL nevie pripojiť `/usr/lib` → `VIRTUAL_DEVICE_BOOT_FAILED`.
   Riešenie: vo WSL vypnúť sandbox (skripty to robia automaticky).
3. Druhý pokus zlyhal na zvyšku prvého pokusu (`New instance conflicts with existing instance`) → `cvd reset --yes`
   (skript to už robí sám).
4. Tretí pokus: `Virtual device booted successfully`, obrazovka telefónu na `https://localhost:8443`.

5. Prvý sieťový audit (30 minút) prebehol a zachytil 13 cieľov – výsledky v [audit/README.md](../audit/README.md).
   Ukázal aj dve nastavenia testovacieho prostredia, ktoré skreslili výsledok: DNS Google pre mobilnú sieť
   telefónu (`--ril_dns`, Cuttlefish ho predvolene nastavuje na 8.8.8.8 bez ohľadu na `dns_servers`) a NTP
   virtuálneho Wi-Fi routra OpenWrt. Skripty preto spúšťajú telefón cez `cvd fetch` + `cvd create --host_path
   --product_path` s parametrami `--ril_dns` a vo WSL `--enable_sandbox=false` (spoločná časť je v
   `tools/etapa2/lib-cuttlefish.sh`); obrazy sa stiahnu raz do `~/lech-work/images`.

Nástroje Cuttlefish na hostiteľovi sa pri štarte pokúšajú kontaktovať `metadata.google.internal` (zisťujú, či bežia
v cloude Google). Mimo Google Cloud to zlyhá bez vplyvu na štart; do auditu virtuálneho telefónu to nepatrí,
pretože ide o prevádzku notebooku, nie telefónu.

Etapa 2 je hotová, keď sú splnené kritériá z [docs/08](08-plan-etap.md#etapa-2--vlastná-zostava-aosp).
