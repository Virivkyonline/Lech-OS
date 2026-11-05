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

7. **Stiahnutie zdrojových kódov AOSP** (niekoľko hodín, dá sa prerušiť a spustiť znova – pokračuje):

   ```bash
   tools/etapa2/04-stiahni-aosp.sh          # bez histórie (--depth=1), šetrí miesto; do ~/aosp
   ```

8. **Prvé zostavenie** čistého AOSP z vlastných zdrojových kódov (na notebooku niekoľko hodín; po prerušení
   pokračuje):

   ```bash
   tools/etapa2/05-zostav-aosp.sh --spustit   # zostaví a spustí telefón z vlastnej zostavy
   ```

   Skript sám nájde platný cieľ zostavenia (release konfigurácia sa medzi vetvami AOSP líši) a uloží ho
   do `~/lech-work/lunch-target`. Audit vlastnej zostavy: `tools/etapa2/03-sietovy-audit.sh --lokalna-zostava`.

9. **Prvý produkt LECH OS** – telefón `LECH-PHONE` s verziou LECH OS a vlastnou animáciou pri štarte
   (úlohy E2-04 a E2-05, podrobnosti v [časti 4b](#4b-prvý-produkt-lech-os-úlohy-e2-04-a-e2-05)):

   ```bash
   git pull
   tools/etapa2/06-zostav-lech.sh --spustit   # zostaví produkt LECH, spustí telefón a overí jeho identitu
   ```

10. **Bez spojení na Google** (úloha E2-06, [časť 4c](#4c-bez-predvolených-spojení-na-google-úloha-e2-06)) –
    rovnaký príkaz ako v kroku 9 použije aj úpravy AOSP z `patches/`; potom sieťový audit LECH OS:

    ```bash
    git pull
    tools/etapa2/06-zostav-lech.sh --spustit
    tools/etapa2/03-sietovy-audit.sh --lokalna-zostava --ano   # 30 minút, správa do audit/
    ```

## 1. Zostavovací počítač

Na zostavenie stačí **server alebo dostatočne vybavený notebook či stolný počítač**. Rozhoduje RAM, rýchly disk
a virtualizácia KVM.

| Parameter | Minimum | Odporúčané |
|-----------|---------|------------|
| Architektúra | x86_64 (Intel / AMD) | x86_64 |
| Operačný systém | Linux (Ubuntu / Debian) nainštalovaný priamo | Ubuntu LTS |
| RAM | 24 GB + 32 GB swap (pomalšie, menej súbežných úloh; napr. WSL na notebooku s 32 GB) | 64 GB a viac (oficiálne odporúčanie Google) |
| Voľné miesto na disku | 300 GB SSD (zdrojové kódy bez histórie ~140 GB + jedna zostava ~140 GB, tesne) | 400 GB a viac; 1 TB NVMe |
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
4. **RAM:** WSL predvolene dostane polovicu RAM počítača. Na zostavovanie pri 32 GB RAM dajte WSL 24 GB
   a 32 GB swap (Windows zostane 8 GB – počas zostavovania nespúšťajte náročné aplikácie). V PowerShell:

   ```powershell
   "[wsl2]`nmemory=24GB`nswap=32GB" | Set-Content -Encoding ASCII "$env:USERPROFILE\.wslconfig"
   wsl --shutdown
   ```

   Zmena platí po `wsl --shutdown` a novom otvorení Ubuntu.
5. **Disk:** disk Ubuntu vo WSL je súbor na disku C: vo Windows, preto rozhoduje voľné miesto na C:
   (na zostavenie aspoň 300 GB pri sťahovaní bez histórie). `tools/check_build_host.sh` ho vo WSL kontroluje.
   Disk WSL sám od seba nezmenšuje, keď sa v Ubuntu niečo zmaže. Voliteľne ho to naučíte v PowerShell:
   `wsl --manage Ubuntu-24.04 --set-sparse true` (pri vypnutom WSL).
6. **Obmedzenie:** Cuttlefish potrebuje okrem `/dev/kvm` aj zariadenia `/dev/vhost-vsock` a `/dev/vhost-net`.
   Ak ich jadro WSL neposkytuje, `tools/check_build_host.sh` to označí – vtedy je potrebné Ubuntu priamo na disku.
7. **Hodiny:** Ubuntu 24.04 vo WSL predvolene synchronizuje čas službou `systemd-timesyncd`, ktorá sa „preťahuje“
   so synchronizáciou času z Windows – hodiny sa posúvajú o sekundy sem a tam a zostavenie padá s `future mtime`.
   Ubuntu pre WSL odporúča túto službu vypnúť (`sudo systemctl disable --now systemd-timesyncd.service`);
   `01-priprava-notebooku.sh` to robí automaticky a `check_build_host.sh` na to upozorní.
8. **Sandbox crosvm:** vo WSL zlyhá izolácia virtuálneho stroja (minijail: `cannot mount '/usr/lib' …
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
# (to isté robí tools/etapa2/04-stiahni-aosp.sh; --depth=1 = bez histórie, výrazne menej miesta)
mkdir -p ~/aosp && cd ~/aosp
repo init -u https://android.googlesource.com/platform/manifest -b android-latest-release --depth=1
repo sync -c --no-tags -j8

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

## 4b. Prvý produkt LECH OS (úlohy E2-04 a E2-05)

Produkt LECH je opísaný v [vendor/lech](../vendor/lech) a všetko dedí z Cuttlefish telefónu
`aosp_cf_x86_64_only_phone`. Pridáva iba identitu:

| Čo | Hodnota | Kde to vidno |
|----|---------|--------------|
| Cieľ zostavenia | `lech_cf_x86_64_only_phone-trunk_staging-userdebug` | `~/lech-work/lunch-target` |
| Názov telefónu (model) | `LECH-PHONE` | Settings → About phone: *Device name* a *Model* |
| Značka a výrobca | `LECH` | odtlačok zostavy (`adb shell getprop ro.build.fingerprint`) |
| Verzia LECH OS | `0.1.0-dev` ([version.mk](../vendor/lech/config/version.mk)) | Settings → About phone → *Build number*: `LECH-OS-0.1.0-dev-userdebug`; `adb shell getprop ro.lech.version` |
| Animácia pri štarte | [bootanimation.zip](../vendor/lech/bootanimation/bootanimation.zip) | pri každom štarte telefónu |

```bash
tools/etapa2/06-zostav-lech.sh --spustit      # skopíruje vendor/lech do ~/aosp, zostaví, spustí, overí identitu
tools/etapa2/06-zostav-lech.sh --ulohy 4      # pri nedostatku pamäte; po prerušení pokračuje
```

- **Trvanie a miesto:** zariadenie (`vsoc_x86_64_only`) ostáva rovnaké, takže sa takmer nič neprekladá znova.
  Prebehne len analýza Soong (pamäť je takmer plná ako pri prvom zostavení) a vytvoria sa nové obrazy.
  Na notebooku to trvalo **18 minút** a `out/` narástol o 12 GB (zo 139 na 151 GB). Skript pre istotu vyžaduje
  40 GB voľného miesta; vo WSL počíta s voľným miestom na disku C:, nie s veľkosťou virtuálneho disku.
- **Prečo kópia, nie odkaz:** zostavovací systém hľadá produkty v `vendor/*` a symbolické odkazy na iné
  adresáre nesleduje. Skript preto pri každom spustení prepíše `~/aosp/vendor/lech` obsahom z repozitára.
- **Mení sa len partícia product** (od úlohy E2-06 aj záplaty kódu AOSP, [časť 4c](#4c-bez-predvolených-spojení-na-google-úloha-e2-06)).
  Systémová partícia je na Cuttlefish spoločný obraz definovaný v Soong
  (`aosp_shared_system_image`). Init načíta `build.prop` partície product ako poslednú, takže jej hodnoty majú
  prednosť – aj pre *Build number* (`ro.build.display.id`).
- **Rovnaký spôsob zostavenia ako AOSP:** `aosp_cf.mk` zapína niektoré režimy (napr. `PRODUCT_SOONG_ONLY`) len pre
  vlastný názov produktu; produkt LECH ich preberá. Ak nová verzia AOSP pridá ďalší, skript na to upozorní.
- **Bez medzier vo verzii:** zostavovací systém z hodnôt vlastností medzery vypúšťa, preto `LECH-OS-0.1.0-dev`.
  Verzia sa mení na jednom mieste – vo [version.mk](../vendor/lech/config/version.mk).
- **Animácia** sa vytvára skriptom `python3 tools/make_bootanimation.py` (vyžaduje `python3-pil`). Výsledný zip je
  v repozitári, takže zostavenie ho nepotrebuje.
- **Presné verzie AOSP:** skript uloží `repo manifest -r` do `~/lech-work/aosp-manifest.xml` – zoznam presných
  revízií všetkých projektov AOSP, na ktorých stojí táto zostava. Z neho vznikne `lech-manifest` a vlastné
  zrkadlo ([docs/16](16-zavislosti-a-plan-odchodu.md), Z1 a Z2).
- **Otvorené:** obrazovka *Android version* a ďalšie texty s názvom Android pochádzajú z aplikácie Nastavenia
  AOSP. Ich premenovanie ([docs/09](09-pravo-a-licencie.md#ochranné-známky), Z7) príde s úpravou Nastavení.
  Model sa dostáva aj do identifikátora prehliadača (User-Agent vo WebView), takže `LECH-PHONE` prezrádza
  LECH OS. Na Cuttlefish to nevadí. Pri Pixeli (E2-11) rozhodneme, či ponechať pôvodný model, aby sa telefón
  nedal od ostatných odlíšiť.
- **Návrat k čistému AOSP:** `tools/etapa2/05-zostav-aosp.sh --spustit`. Oba produkty zdieľajú adresár zariadenia
  v `out/`, preto sa pri každom prepnutí obrazy vytvoria znova.

## 4c. Bez predvolených spojení na Google (úloha E2-06)

Pôvod každého zo 7 spojení z auditu čistého AOSP ([audit/README.md](../audit/README.md)) sme dohľadali
v zdrojovom kóde Android 17. Kým LECH OS nemá vlastný server, spojenie radšej vypneme, než by sme ho
presmerovali na cudzí server ([ADR-0010](adr/0010-upravy-aosp-ako-zaplaty.md)).

| Spojenie | Kto ho robí | Zmena v LECH OS | Dôsledok, kým nemáme vlastný server |
|----------|-------------|-----------------|-------------------------------------|
| `connectivitycheck.gstatic.com`, `www.google.com` | Kontrola pripojenia: modul NetworkStack (`NetworkMonitor`) | Záplata: kontrola je predvolene vypnutá (`captive_portal_mode`) | Prihlasovacia stránka verejnej Wi-Fi (hotel, letisko) sa neotvorí sama; telefón nerozozná sieť bez internetu. Zapnutie (spojenie na Google): `adb shell settings put global captive_portal_mode 1` |
| `time.android.com` | Sieťový čas: framework (`config_ntpServers`) | Prekrytie RRO vo `vendor/lech`: `europe.pool.ntp.org` | Žiadny. Pred vydaním požiadame NTP Pool o vlastnú zónu výrobcu |
| `…-dnsotls-ds.metric.gstatic.com` | Overenie šifrovaného DNS (DoT aj DoH): modul DnsResolver; dopyt ide cez nastavený DNS server, aj nešifrovane | Záplata: `…-dnsotls-ds.probe.lech.invalid`. Názov má rovnakú dĺžku a doména `.invalid` nikam nevedie (RFC 6761); overenie prijme akúkoľvek odpoveď | Žiadny |
| `preprod-remoteprovisioning.googleapis.com` | Atestačné kľúče (RKP): `rkpd`; testovací server Google nastavuje Cuttlefish | Záplata v `device/google/cuttlefish`: bez servera | Atestácia kľúčov na LECH Cuttlefish nefunguje. Na vývoj ju netreba; pri Pixeli rozhodneme v E2-11 ([docs/13](13-bankove-aplikacie.md)) |
| `www.gstatic.com` | Zoznam logov Certificate Transparency: modul Connectivity (`CertificateTransparencyService`) ho sťahuje po štarte | Záplata: sťahovanie je predvolene vypnuté | Aplikácie, ktoré si samy zapnú kontrolu CT, nemajú aktuálny zoznam. Cieľ: kópia zoznamu na serveri LECH |
| `g.co` | **Neurčené.** V moduloch NetworkStack, Connectivity, DnsResolver ani RemoteKeyProvisioning nie je | – | Audit LECH OS teraz ukáže aj aplikáciu, ktorá sa na názov `g…` pýta |

- **Záplaty** sú v [patches/](../patches) a použije ich `tools/etapa2/aplikuj-zaplaty.sh`. Skript
  `06-zostav-lech.sh` ho spúšťa sám; `aplikuj-zaplaty.sh --kontrola` len vypíše stav. Každá záplata je commit
  v príslušnom projekte (`git -C ~/aosp/packages/modules/NetworkStack log -1`). Po `repo sync` treba záplaty
  použiť znova.
- **Zostavenie** tentoraz prekladá zmenené moduly NetworkStack, Connectivity a DnsResolver a zmena v
  `device/google/cuttlefish` znova spustí analýzu Soong. Odhad je 30 až 90 minút.
- **Kontrola po štarte:** `06-zostav-lech.sh --spustit` overí aj zapnuté prekrytie času a prázdny server RKP.
  Úplné overenie urobí sieťový audit (`03-sietovy-audit.sh --lokalna-zostava`). Ten teraz obsahuje aj časť
  „Kto sa pýtal“, teda DNS dopyty podľa aplikácií zo záznamu DNS resolvera v telefóne. Je to prvý krok
  k [LECH Privacy Control](12-lech-privacy-control.md).
- **Ostáva na neskôr (E2-06b, spolu so serverom aktualizácií E2-08):**
  - vlastná kontrola pripojenia namiesto vypnutej,
  - vlastná doména na overenie DNS v *striktnom* režime súkromného DNS – keď používateľ zadá názov
    poskytovateľa, NetworkStack sa naň pýta s doménou `metric.gstatic.com` a potrebuje skutočnú odpoveď,
  - kópia zoznamu CT,
  - diagnostika siete v module Connectivity (`NetworkDiagnostics`), ktorá sa spúšťa len pri poruche siete.

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
| E2-01 | Zostavovací počítač (server alebo notebook) | `tools/check_build_host.sh` skončí výsledkom „zvládne zostavenie aj spustenie“ | **Hotové 5. 11. 2026** – notebook s Windows a WSL (24 GB RAM + 32 GB swap); prvé zostavenie AOSP za 6 h 40 min |
| E2-02 | Čisté AOSP na Cuttlefish | Virtuálny telefón nabootuje a je ovládateľný cez prehliadač | **Hotové 4. 11. 2026** – WSL na notebooku, hotové obrazy `aosp-android-latest-release` (build 16644040), sandbox crosvm vypnutý, obrazovka cez prehliadač |
| E2-03 | Sieťový audit čistého AOSP | Zoznam všetkých spojení pri prvom spustení je zapísaný v repozitári | **Hotové 4. 11. 2026** – dve merania; čisté AOSP sa pri prvom spustení spojí so 7 cieľmi Google ([audit/README.md](../audit/README.md)); zostáva určiť pôvod 2 spojení |
| E2-04 | Repozitár `lech-manifest` a produkt LECH pre Cuttlefish | Zostaví sa vlastný produkt odvodený od `aosp_cf_x86_64_only_phone` | **Hotové 5. 11. 2026** – produkt `lech_cf_x86_64_only_phone` vo [vendor/lech](../vendor/lech) ([časť 4b](#4b-prvý-produkt-lech-os-úlohy-e2-04-a-e2-05)) sa zostavil za 18 min a nabootoval; `lech-manifest` zatiaľ ako snímka presných verzií AOSP (`~/lech-work/aosp-manifest.xml`) |
| E2-05 | Základná identita | Názov LECH OS a verzia v nastaveniach, vlastná animácia pri štarte | **Hotové 5. 11. 2026** – v bežiacom telefóne overené cez adb: model `LECH-PHONE`, *Build number* `LECH-OS-0.1.0-dev-userdebug`, `ro.lech.version` 0.1.0-dev, animácia v `/product/media`; zostáva premenovať texty „Android“ v Nastaveniach (Z7) |
| E2-06 | Nahradenie spojení na Google | Audit z E2-03 na produkte LECH neukáže žiadne spojenie na Google | Rozpracované 5. 11. 2026 – 6 zo 7 cieľov vypnutých alebo nahradených ([časť 4c](#4c-bez-predvolených-spojení-na-google-úloha-e2-06)), pôvod `g.co` zisťujeme; čaká na zostavenie a audit |
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

6. Stiahnutie zdrojových kódov AOSP (`android-latest-release`, `--depth=1`) za približne 30 minút pri 626 Mbit/s:
   **139 GB** v `~/aosp`. Servery Google počas sťahovania odmietli 3 projekty s `RESOURCE_EXHAUSTED` (kvóta pre
   anonymné sťahovanie z jednej IP adresy); druhý pokus s menším počtom úloh ich hneď stiahol. Prihlásenie
   do Google nebolo potrebné.

7. Prvé zostavenie: analýza zostavy (Soong) trvala pri 23 GB RAM + 32 GB swap 12 minút (pamäť 97 %, disk 100 %),
   potom začalo prekladanie (197 973 krokov). Po 229 krokoch zlyhalo s `future mtime` – hodiny WSL sa pri veľkom
   zaťažení posunuli o 8 s späť a zostavovač (siso) odmietol práve zapísaný súbor. Nie je to chyba zostavenia;
   `05-zostav-aosp.sh` po takejto chybe zostavenie automaticky spustí znova (najviac 5-krát, `LECH_CLOCK_RETRIES`).
   Chyba sa zopakovala po 2 minútach – príčinou je súbeh dvoch synchronizácií času (pozri „Hodiny“ v časti
   o WSL); riešením je vypnúť `systemd-timesyncd` podľa dokumentácie Ubuntu pre WSL.

8. **Prvé úspešné zostavenie (5. 11. 2026):** po vypnutí `systemd-timesyncd` prebehlo bez ďalšieho posunu hodín –
   192 188 krokov za 6 h 40 min (i9-12900H, 8 súbežných úloh, WSL s 24 GB RAM + 32 GB swap). Cieľ
   `aosp_cf_x86_64_only_phone-trunk_staging-userdebug`, výstup `out/` **139 GB** (spolu so zdrojovými kódmi
   približne 280 GB). Telefón z vlastnej zostavy nabehol (`cvd create` s `--ril_dns` a `--enable_sandbox=false`).

9. **Aplikácia 中国移动 (China Mobile) v zozname aplikácií** nie je čínsky softvér v systéme. Je to štandardná
   aplikácia AOSP **SIM Toolkit** (`com.android.stk`): v zozname aplikácií sa zobrazí, len keď SIM karta ponúka
   vlastné menu, a jej názov je názov tohto menu. Virtuálna SIM karta Cuttlefish (simulátor modemu z
   [android-cuttlefish](https://github.com/google/android-cuttlefish), súbor
   `base/cvd/cuttlefish/host/commands/modem_simulator/etc/files/iccprofile_for_sim0.xml`) obsahuje testovacie menu
   operátora China Mobile. Príkaz SET UP MENU s položkami „SIM快捷确认“ (rýchle potvrdenie SIM) a „USIM卡信息“
   (informácie o USIM karte) sú statické testovacie údaje na hostiteľovi, nie v obraze telefónu. Audity E2-03
   nezachytili žiadne súvisiace spojenie. Na skutočnom telefóne tá istá aplikácia ukáže menu SIM karty vlastného
   operátora. V LECH OS ostáva, lebo ju potrebujú služby SIM kariet. Bezpečnostné hľadisko je opísané v
   [docs/05](05-lech-security.md#4-sieť-a-súkromie).

10. **Prvý LECH OS (5. 11. 2026):** `06-zostav-lech.sh --spustit` po zostavení čistého AOSP – Kati prijal produkt
    `lech_cf_x86_64_only_phone`, zostavenie bežalo v rovnakom režime ako AOSP (`SOONG_ONLY=true`,
    `SOONG_INCREMENTAL_ANALYSIS=true`), build `CP2A.260605.016`. Trvalo **18 min** (počas analýzy Soong hlásenia
    `memory stall` – plná pamäť, bez vplyvu na výsledok), `out/` 151 GB. Telefón LECH-PHONE nabootoval a kontrola
    identity cez adb prešla: model, verzia, *Build number* aj animácia pri štarte. Pred zostavením treba vypnúť
    bežiaci telefón (`cvd reset --yes`), aby mala analýza viac pamäte.

Nástroje Cuttlefish na hostiteľovi sa pri štarte pokúšajú kontaktovať `metadata.google.internal` (zisťujú, či bežia
v cloude Google). Mimo Google Cloud to zlyhá bez vplyvu na štart; do auditu virtuálneho telefónu to nepatrí,
pretože ide o prevádzku notebooku, nie telefónu.

Etapa 2 je hotová, keď sú splnené kritériá z [docs/08](08-plan-etap.md#etapa-2--vlastná-zostava-aosp).
