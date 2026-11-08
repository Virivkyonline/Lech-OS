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

11. **Vlastné podpisové kľúče** (úloha E2-07, [časť 4d](#4d-vlastné-testovacie-podpisové-kľúče-úloha-e2-07)) –
    rovnaký príkaz `tools/etapa2/06-zostav-lech.sh --spustit`; pri prvom spustení vytvorí testovacie kľúče LECH OS.

12. **Aktualizácia z vlastného servera** (úloha E2-08, [časť 4e](#4e-aktualizácia-z-vlastného-servera-úloha-e2-08)):

    ```bash
    cvd reset --yes
    tools/etapa2/08-aktualizacia.sh   # zostaví verziu A a B, telefón A sa aktualizuje na B
    ```

13. **Test aplikácií z F-Droid** (úloha E2-10, [časť 4f](#4f-test-aplikácií-z-f-droid-úloha-e2-10)):

    ```bash
    tools/etapa2/06-zostav-lech.sh --spustit
    tools/etapa2/10-test-aplikacii.sh          # 10 aplikácií, správa do kompatibilita/
    ```

14. **Nočná kontrola** (úloha E2-09, [časť 4g](#4g-nočná-kontrola-úloha-e2-09)) – raz nastaviť, potom beží sama:

    ```bash
    tools/etapa2/09-nastav-nocnu-kontrolu.sh   # sudo pre tcpdump + úloha v Plánovači úloh Windows (02:30)
    tools/etapa2/09-nocna-kontrola.sh          # vyskúšať hneď
    ```

15. **LECH OS na Pixeli 9** (úloha E2-11a, [časť 4h](#4h-lech-os-na-pixeli-9-úloha-e2-11a)) – zostavenie pre ARM
    trvá prvýkrát niekoľko hodín; medzitým sa dá odomknúť bootloader:

    ```bash
    tools/etapa2/11-zostav-gsi.sh              # LECH OS pre ARM64, obrazy do C:\lech\pixel
    tools/etapa2/11-pixel.sh nastroje          # adb.exe a fastboot.exe od Google pre Windows
    tools/etapa2/11-pixel.sh odomknut          # odomknutie bootloadera – vymaže telefón
    tools/etapa2/11-pixel.sh nahrat            # nahratie LECH OS – vymaže telefón
    tools/etapa2/11-pixel.sh kontrola          # kontrola po štarte
    tools/etapa2/11-pixel.sh audit             # sieťový audit priamo v telefóne (Wi-Fi aj mobilná sieť)
    ```

16. **Úplná zostava pre Pixel 9** (úloha E2-11b, [časť 4i](#4i-úplná-zostava-lech-os-pre-pixel-9-úloha-e2-11b)) – podpora
    zariadenia z GrapheneOS, aj IMS pre SMS a VoLTE:

    ```bash
    tools/etapa2/12-priprav-pixel.sh           # Node.js, projekty GrapheneOS, adevtool → vendor/google_devices/tokay
    tools/etapa2/12-zostav-pixel.sh            # LECH OS pre Pixel 9, balík do C:\lech\pixel-uplne
    tools/etapa2/11-pixel.sh nahrat-uplne      # nahratie všetkých partícií – vymaže telefón
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
v zdrojovom kóde Android 17. **Overené 5. 11. 2026 (4. audit): LECH OS sa pri prvom spustení nespojí so žiadnym
serverom Google.** Kým LECH OS nemá vlastný server, spojenie radšej vypneme, než by sme ho
presmerovali na cudzí server ([ADR-0010](adr/0010-upravy-aosp-ako-zaplaty.md)).

| Spojenie | Kto ho robí | Zmena v LECH OS | Dôsledok, kým nemáme vlastný server |
|----------|-------------|-----------------|-------------------------------------|
| `connectivitycheck.gstatic.com`, `www.google.com` | Kontrola pripojenia: modul NetworkStack (`NetworkMonitor`) | Záplata: kontrola je predvolene vypnutá (`captive_portal_mode`) | Prihlasovacia stránka verejnej Wi-Fi (hotel, letisko) sa neotvorí sama; telefón nerozozná sieť bez internetu. Zapnutie (spojenie na Google): `adb shell settings put global captive_portal_mode 1` |
| `time.android.com` | Sieťový čas: framework (`config_ntpServers`) | Prekrytie RRO vo `vendor/lech`: `europe.pool.ntp.org` | Žiadny. Pred vydaním požiadame NTP Pool o vlastnú zónu výrobcu |
| `…-dnsotls-ds.metric.gstatic.com` | Overenie šifrovaného DNS (DoT aj DoH): modul DnsResolver; dopyt ide cez nastavený DNS server, aj nešifrovane | Záplata: `…-dnsotls-ds.probe.lech.invalid`. Názov má rovnakú dĺžku a doména `.invalid` nikam nevedie (RFC 6761); overenie prijme akúkoľvek odpoveď | Žiadny |
| `preprod-remoteprovisioning.googleapis.com` | Atestačné kľúče (RKP): `rkpd`; testovací server Google nastavuje Cuttlefish | Záplata v `device/google/cuttlefish`: bez servera | Atestácia kľúčov na LECH Cuttlefish nefunguje. Na vývoj ju netreba; pri Pixeli rozhodneme v E2-11 ([docs/13](13-bankove-aplikacie.md)) |
| `www.gstatic.com` | Zoznam logov Certificate Transparency: modul Connectivity (`CertificateTransparencyService`) ho sťahuje po štarte | Záplata: sťahovanie je predvolene vypnuté | Aplikácie, ktoré si samy zapnú kontrolu CT, nemajú aktuálny zoznam. Cieľ: kópia zoznamu na serveri LECH |
| `g.co` | Overenie odkazov aplikácií (Android App Links): `com.android.statementservice` sťahuje `https://g.co/.well-known/assetlinks.json`, lebo aplikácia na tapety `com.android.wallpaper` (WallpaperPicker2) deklaruje odkaz `g.co/wallpaper` s `autoVerify` (zistené cez `kto-sa-pyta.sh` a `pm get-app-links`) | Záplata: odkaz bez overovania | Žiadny. Overenie aj tak zlyhávalo (`legacy_failure`), lebo `g.co` uvádza len aplikáciu Google |

- **Záplaty** sú v [patches/](../patches) a použije ich `tools/etapa2/aplikuj-zaplaty.sh`. Skript
  `06-zostav-lech.sh` ho spúšťa sám; `aplikuj-zaplaty.sh --kontrola` len vypíše stav. Každá záplata je commit
  v príslušnom projekte (`git -C ~/aosp/packages/modules/NetworkStack log -1`). Po `repo sync` treba záplaty
  použiť znova.
- **Zostavenie** tentoraz prekladá zmenené moduly NetworkStack, Connectivity a DnsResolver a zmena v
  `device/google/cuttlefish` znova spustí analýzu Soong. Odhad je 30 až 90 minút.
- **Kontrola po štarte:** `06-zostav-lech.sh --spustit` overí aj zapnuté prekrytie času a prázdny server RKP.
  Úplné overenie urobí sieťový audit (`03-sietovy-audit.sh --lokalna-zostava`). Ten teraz obsahuje aj časť
  „Kto sa pýtal“, teda DNS dopyty podľa aplikácií zo záznamu DNS resolvera v telefóne. Je to prvý krok
  k [LECH Privacy Control](12-lech-privacy-control.md). Na bežiacom telefóne ich vypíše aj
  `tools/etapa2/kto-sa-pyta.sh`. Záznam si vyžaduje `adb root`, ktorý vývojová zostava (userdebug) dovolí.
- **Ostáva na neskôr (E2-06b, spolu so serverom aktualizácií E2-08):**
  - vlastná kontrola pripojenia namiesto vypnutej,
  - vlastná doména na overenie DNS v *striktnom* režime súkromného DNS – keď používateľ zadá názov
    poskytovateľa, NetworkStack sa naň pýta s doménou `metric.gstatic.com` a potrebuje skutočnú odpoveď,
  - kópia zoznamu CT,
  - diagnostika siete v module Connectivity (`NetworkDiagnostics`), ktorá sa spúšťa len pri poruche siete.

## 4d. Vlastné testovacie podpisové kľúče (úloha E2-07)

Každá aplikácia a každý balík aktualizácií je podpísaný. AOSP sa predvolene podpisuje **verejnými testovacími
kľúčmi** (`build/make/target/product/security`), ktoré pozná každý. Takú zostavu by vedel ktokoľvek „aktualizovať“
vlastnou upravenou aplikáciou so systémovými právami. LECH OS preto používa vlastné kľúče.

| Čo | Ako |
|----|-----|
| Vytvorenie | `tools/etapa2/07-vytvor-kluce.sh` – pre každý testovací kľúč AOSP (testkey, platform, shared, media, networkstack, sdk_sandbox, bluetooth, nfc, cts_uicc_2021) jeden kľúč RSA 4096 s certifikátom na 10 000 dní. `06-zostav-lech.sh` ho pri prvom spustení zavolá sám |
| Uloženie | `~/lech-keys/test` – **mimo repozitára**, prístup len pre používateľa (`chmod 700`). Pred zostavením sa skopírujú do `~/aosp/vendor/lech-keys/test` |
| Použitie | `PRODUCT_DEFAULT_DEV_CERTIFICATE` v [common.mk](../vendor/lech/config/common.mk): podpisuje všetky aplikácie a kľúč na overenie aktualizácií (`otacerts`). Značka zostavy sa zmení z `test-keys` na `dev-keys` |
| Kontrola | `06-zostav-lech.sh --spustit` overí v telefóne `ro.build.tags = dev-keys` a porovná podpis systému (`dumpsys package android`) s certifikátom `platform` LECH OS |

- **Raz vytvorené kľúče sa nemenia.** Telefón podpísaný inými kľúčmi treba vymazať (na Cuttlefish to robí
  `cvd reset`, ktorý `06-zostav-lech.sh --spustit` volá sám). Priečinok `~/lech-keys` si zálohujte.
- **Zostavenie** podpíše nanovo všetky aplikácie. Odhad je 20 až 60 minút.
- **SELinux rozpoznáva systémové aplikácie podľa certifikátu** (`system/sepolicy/private/keys.conf`): pre moduly
  NetworkStack, SDK sandbox, Bluetooth a NFC berie certifikát z priečinka kľúčov produktu
  (`PRODUCT_MAINLINE_*SEPOLICY_DEV_CERTIFICATES`). **Aplikácie Bluetooth sú výnimka:** podpisujú sa vlastným
  certifikátom modulu (`packages/modules/Bluetooth/android/app/certs`), ktorý je zhodou totožný s verejným kľúčom
  AOSP `bluetooth`. S kľúčmi LECH preto zygote nevedel nastaviť aplikácii `com.android.bluetooth` kontext SELinux
  (`JNI FatalError … selinux_android_setcontext`), Bluetooth stále padal a Cuttlefish hlásil
  `VIRTUAL_DEVICE_BOOT_FAILED: Dependencies not ready after 10 checks: Bluetooth`. Riešenie v
  [common.mk](../vendor/lech/config/common.mk): `PRODUCT_CERTIFICATE_OVERRIDES` podpíše `Bluetooth` aj
  `BluetoothLegacyMigration` kľúčom LECH `bluetooth`. Modul `lech_bluetooth_certificate` zapíše `06-zostav-lech.sh`
  do priečinka kľúčov. Zistiť sa to dalo z `tools/etapa2/diagnostika.sh` (pády procesov, certifikáty SELinux).
- **Sú to testovacie kľúče**, nie produkčné. Produkčné kľúče vzniknú pred vydaním podľa
  [docs/05](05-lech-security.md#správa-kľúčov): offline, dve osoby, nikdy na vývojárskom počítači.
- **Ostáva na ďalšie kroky:**
  - kľúče systémových modulov APEX a kľúč overeného bootu (AVB) – zatiaľ testovacie kľúče AOSP,
  - podpis hotovej zostavy nástrojom `sign_target_files_apks` – patrí k serveru aktualizácií (E2-08) a
    k zamknutému bootloaderu na Pixeli (E2-12).

## 4e. Aktualizácia z vlastného servera (úloha E2-08)

Prvá verzia (v0) celého postupu aktualizácie. Server beží na zostavovacom počítači; server v EÚ, aplikácia na
aktualizácie v telefóne a podpis celej zostavy prídu v ďalších krokoch. **Overené 5. 11. 2026:** telefón s
LECH OS 0.1.0-dev sa z nášho servera aktualizoval na 0.1.0-dev-ota a nabehol zo slotu `_b` (záznam č. 15).

```bash
tools/etapa2/08-aktualizacia.sh                   # verzia B = <verzia A>-ota
tools/etapa2/08-aktualizacia.sh --verzia 0.1.1-dev
tools/etapa2/08-aktualizacia.sh --len-instalovat  # znova len inštalácia (uložené obrazy A a balík)
```

| Krok | Čo sa deje |
|------|------------|
| 1. Verzia A | `06-zostav-lech.sh` zostaví LECH OS s verziou z [version.mk](../vendor/lech/config/version.mk). Skript skopíruje jeho obrazy do `~/lech-work/ota/obrazy-A`, lebo zostavenie B prepíše súbory v `out/` |
| 2. Verzia B | To isté s `LECH_VERSION=<verzia B>` (premenná prostredia prepíše `version.mk`), potom `m otapackage`. Balík `ota.zip` podpíše `ota_from_target_files` kľúčom `testkey` LECH OS – tým istým kľúčom, ktorému telefón A verí (`otacerts`, E2-07) |
| 3. Server v0 | [ota_server.py](../tools/etapa2/ota_server.py) `publish` uloží balík do `~/lech-work/ota/server` a zapíše zoznam `aktualizacie.json` (verzia, veľkosť, SHA-256, umiestnenie `payload.bin` v balíku, hlavičky). `serve` ho poskytuje cez HTTP s čiastočným sťahovaním (Range), ktoré `update_engine` potrebuje |
| 4. Inštalácia | Telefón naštartuje z obrazov A. `update_engine_client` stiahne cez tunel `adb reverse` z `http://127.0.0.1:8008/` len `payload.bin`, overí podpis a zapíše B do druhého slotu (A/B), kým telefón beží |
| 5. Kontrola | Reštart; skript overí novú verziu (`ro.lech.version`), zmenený slot (`ro.boot.slot_suffix`) a inú zostavu |

- **Trvanie:** na notebooku približne 25 minút. Obe zostavenia trvali po 2 minúty, lebo zmena verzie mení len
  `build.prop` a analýza Soong je prírastková. Ďalej `m otapackage`, sťahovanie a overenie balíka (1 008 MB,
  približne 3 minúty) a reštart. Miesto: kópia obrazov A (skript vypíše veľkosť) a balík.
- **Prečo `adb reverse` v0:** telefón nepotrebuje sieť k počítaču a postup je rovnaký ako v nástroji AOSP
  `system/update_engine/scripts/update_device.py`. Server v EÚ s HTTPS nahradí tunel (E2-08b).
- **Ďalšie kroky (E2-08b):**
  - aplikácia na aktualizácie v telefóne, ktorá číta `aktualizacie.json` a pýta sa používateľa,
  - server v EÚ s HTTPS,
  - podpis celej zostavy (`sign_target_files_apks`) vrátane kľúčov APEX a AVB,
  - rozdielové (inkrementálne) balíky,
  - test chybnej aktualizácie, po ktorej sa telefón sám vráti na funkčnú verziu
    ([docs/14](14-aktualizacie-a-podpora.md#overenie-v-etape-2)).

## 4f. Test aplikácií z F-Droid (úloha E2-10)

Prvý test kompatibility: nainštalujú sa a spustia sa bežné aplikácie z [F-Droid](https://f-droid.org) – katalógu
aplikácií s otvoreným kódom, ktorý funguje bez Google. Výber v
[spec/testovacie-aplikacie.txt](../spec/testovacie-aplikacie.txt) zodpovedá potrebám pilotu pre firmy
([ADR-0007](adr/0007-prvy-trh-firmy.md)): prehliadač, pošta, kalendár a kontakty, MFA, VPN, heslá, firemná
komunikácia, mapy, súbory, médiá.

| Krok | Ako |
|------|-----|
| Výber verzie | Z indexu F-Droid (`index-v2.json`) najnovšia odporúčaná verzia, ktorá má natívny kód pre ABI telefónu (Cuttlefish: len `x86_64`) a `minSdk` nie vyšší ako Android v telefóne |
| Stiahnutie | APK z `f-droid.org/repo`; **SHA-256 sa porovná s indexom**, nezhodné APK sa nenainštaluje |
| Inštalácia | `adb install -r`; ak Android odmietne starý `targetSdk`, zapíše sa to a inštalácia sa zopakuje s `--bypass-low-target-sdk-block` |
| Spustenie | Spúšťacia aktivita; po 8 s kontrola, že proces beží, aktivita je na popredí a v zázname pádov (`logcat -b crash`) nič nie je. Poznamená sa, ak aplikácia hľadá služby Google. Snímka obrazovky |
| Správa | `kompatibilita/<čas>-fdroid/sprava.md` a `sprava.json` v repozitári; APK a snímky v `~/lech-work/kompatibilita` |

- **Čo test overuje:** že sa aplikácia nainštaluje a spustí bez pádu – nie všetky jej funkcie (prihlásenie,
  synchronizáciu, hovory). Tie overí podrobnejší test s pilotným zákazníkom.
- **ABI Cuttlefish:** telefón je 64-bitový `x86_64` a hlási aj `arm64-v8a` – kód pre ARM prekladá (native bridge).
  Test preto uprednostní verziu pre `x86_64`; ak F-Droid ponúka len ARM, použije ju a v stĺpci „Kód“ uvedie
  „preklad kódu ARM“. Pixel (ARM) beží natívne. Aplikácie s knižnicou JNA vo verzii len pre ARM pod prekladom padajú
  (Fennec, 2. test) – JNA hľadá kód pre x86-64.
- **Čistá inštalácia:** pred každou inštaláciou test aplikáciu odinštaluje; pri páde uloží log do
  `~/lech-work/kompatibilita/logy`.
- **Index F-Droid** sa sťahuje cez HTTPS a jeho podpis sa zatiaľ neoveruje; overenie podpisu repozitára bude súčasťou
  LECH Store ([docs/06](06-lech-store-a-cloud.md)).

## 4g. Nočná kontrola (úloha E2-09)

Prvá verzia automatickej kontroly (CI v0) beží na zostavovacom notebooku. Každú noc overí, že aktuálny LECH OS
sa zostaví, naštartuje, nespojí sa s ničím nepovoleným a beží na ňom 10 testovacích aplikácií.

| Krok | Skript | Chyba, keď |
|------|--------|------------|
| 1. Aktualizácia | `git pull --ff-only`, potom sa nočná kontrola spustí znova v novej verzii | – (pri zlyhaní len upozornenie) |
| 2. Kontroly repozitára | syntax skriptov shell a Python, [validátor katalógu AI akcií](../tools/validate_ai_actions.py) | akákoľvek chyba |
| 3. Zostavenie a štart | `06-zostav-lech.sh --spustit` – záplaty, zostavenie, štart, 9 kontrol telefónu | zostavenie alebo kontrola telefónu zlyhá |
| 4. Sieťový audit | `03-sietovy-audit.sh --lokalna-zostava` na čerstvom telefóne (predvolene 30 min) | nepovolená doména alebo nepovolený cieľ zo siete telefónu. DNS 8.8.8.8 routra OpenWrt (testovacie prostredie) sa toleruje, iný neznámy cieľ cez router je upozornenie |
| 5. Aplikácie | `10-test-aplikacii.sh` | aspoň jedna aplikácia sa nenainštaluje alebo nespustí |

- **Výsledok:** `~/lech-work/ci/<čas>/suhrn.md` (tabuľka OK / UPOZORNENIE / CHYBA / PRESKOČENÉ), logy a správy každého
  kroku v tom istom priečinku. `~/lech-work/ci/posledna.md` je posledný súhrn, `historia.txt` má jeden riadok na beh.
  Vo Windows: `\\wsl.localhost\Ubuntu-24.04\home\lech\lech-work\ci\posledna.md`. Správy nočných behov nejdú do
  repozitára – len ak treba niečo zaznamenať.
- **Nastavenie** (`09-nastav-nocnu-kontrolu.sh`, raz):
  - pravidlo `sudo` `/etc/sudoers.d/lech-nocna-kontrola` – bez hesla len `/usr/bin/tcpdump`, nič iné,
  - úloha **„LECH OS - nocna kontrola“** v Plánovači úloh Windows: každý deň o 02:30 (`--cas HH:MM`), zobudí počítač
    zo spánku, na batérii sa nespustí (začatá kontrola však pri prechode na batériu dobehne), najviac 4 hodiny. Beží, keď ste prihlásený vo Windows (aj pri zamknutej
    obrazovke). Zrušenie: `--zrusit`. Skript úlohu po vytvorení overí a vypíše najbližší beh; ak sa to nepodarí,
    vypíše celý výstup PowerShell.
  - kontrola neskôr (vo WSL): `cd /mnt/c && powershell.exe -NoProfile -Command "Get-ScheduledTask -TaskName 'LECH OS - nocna kontrola' | Get-ScheduledTaskInfo"`
  - ručne, ak PowerShell z WSL nefunguje: Plánovač úloh → *Vytvoriť úlohu*; spúšťač denne 02:30; akcia program
    `wsl.exe`, argumenty `-d Ubuntu-24.04 -u lech -- bash -lc "/home/lech/Lech-OS/tools/etapa2/09-nocna-kontrola.sh >> /home/lech/lech-work/ci/beh.log 2>&1"`;
    na karte *Podmienky* zapnúť *Prebudiť počítač na spustenie tejto úlohy*.
- **Trvanie** približne 45 minút pri malých zmenách (zostavenie a štart 7 min, audit 30 min, aplikácie 2 min), po
  veľkej zmene alebo novej verzii AOSP dlhšie. Vtedy notebook naplno pracuje – preto noc.
- **Ďalší krok (neskôr):** CI na serveri v EÚ, ktoré zostavuje z vlastného zrkadla AOSP a kontroluje každú zmenu
  v repozitári ešte pred zlúčením.

## 4h. LECH OS na Pixeli 9 (úloha E2-11a)

Od Androidu 16 (jún 2025) Google nezverejňuje stromy zariadení Pixel v AOSP a ako vzor odporúča Cuttlefish.
Prvý krok na skutočnom telefóne je preto **LECH OS ako jednotný systém (GSI)**: nahradí sa len systémový obraz,
vendor, jadro a ovládače zostávajú pôvodné z Pixelu (Android 17). Úplná zostava pre Pixel so stromami
zariadenia z otvoreného kódu GrapheneOS je úloha E2-11b; až s ňou sa dá bootloader zamknúť s vlastným kľúčom AVB
(E2-12).

| Čo | Ako |
|----|-----|
| Produkt | `lech_gsi_arm64` ([vendor/lech/products/lech_gsi_arm64.mk](../vendor/lech/products/lech_gsi_arm64.mk)) je zložený ako GSI `aosp_arm64`, ale **len 64-bitový** (`core_64_bit_only.mk`, doska [vendor/lech/board/lech_arm64](../vendor/lech/board/lech_arm64/BoardConfig.mk) bez 32-bitového ARM, ako Cuttlefish `vsoc_arm64_only`), plus `config/common.mk`: identita, kľúče LECH, prekrytie NTP, animácia, záplaty z `patches/` (moduly sa zostavujú zo zdrojov). Pixel 9 32-bitový kód nespúšťa a s dvoma architektúrami analýze zostavy došla pamäť |
| Partícia product | je v systémovom obraze (`system/product`). Partície `/product` a `/system_ext` Pixelu (aplikácie Google) GSI nepripája (`gsi_skip_mount.cfg`) |
| Zostavenie | `11-zostav-gsi.sh`: príprava ako pre Cuttlefish (`06-zostav-lech.sh --len-skopirovat`), potom `05-zostav-aosp.sh` s cieľom v `~/lech-work/lunch-target-gsi`. Prvé zostavenie pre ARM: niekoľko hodín a približne 100 GB. Po ňom sa ďalšie zostavenie pre Cuttlefish (aj nočná kontrola) začne novou analýzou Soong |
| Súbeh | zostavenie pre ARM a nočná kontrola majú spoločný zámok – nočná kontrola sa počas zostavenia preskočí (`historia.txt`: PRESKOČENÉ) |
| Nahratie | `11-pixel.sh` vo WSL používa `adb.exe` a `fastboot.exe` od Google pre Windows (`C:\lech\platform-tools`), lebo USB zariadenia vidí Windows, nie WSL. Server adb na porte 5038, oddelene od adb pre Cuttlefish |
| Postup nahratia | bootloader → `vbmeta` s vypnutým overovaním → režim fastbootd → `system` (ak chýba miesto v `super`, odstráni sa nepoužívaná `product_<slot>`) → vymazanie dát (fastbootd Pixelu 9 ho odmieta – `DCK wipe failed` –, preto v bootloaderi) → štart. Keď nahratie skončí pred vymazaním dát: `11-pixel.sh dokoncit` |
| Návrat | `11-pixel.sh spat`: Android Flash Tool (`flash.android.com`) nahrá pôvodný systém Google. Pôvodná verzia sa zapíše do `~/lech-work/pixel/povodny-system.txt` |

Pred prvým nahratím v telefóne s pôvodným systémom: najnovšia aktualizácia, *Nastavenia → Informácie o telefóne*
(7× *Číslo zostavy*), *Systém → Pre vývojárov*: zapnúť **Odomknutie OEM** a **Ladenie cez USB**, pripojiť kábel
USB-C a na telefóne povoliť počítač. Odomknutie bootloadera aj nahratie LECH OS vymažú všetky dáta v telefóne.

- **Bootloader nikdy nezamykať s LECH OS GSI** (`fastboot flashing lock`) – overenie by zlyhalo a telefón by sa
  nespustil. Zamknúť až s pôvodným systémom alebo s úplnou zostavou podpísanou kľúčom LECH (E2-12).
- **Očakávané obmedzenia GSI:** varovanie o odomknutom bootloaderi pri štarte; funkcie, ktoré Pixel má v
  partíciách `/product` a `/system_ext` (napríklad volania cez LTE – VoLTE – a časť funkcií fotoaparátu), nemusia
  fungovať. Čo funguje a čo nie, zapíšeme do záznamu v časti 6 a použijeme pri E2-11b.
- **Sieťový audit na Pixeli:** `11-pixel.sh audit [--minuty N]` (predvolene 15 min) – `tcpdump` priamo v telefóne
  (`adb root`, všetky rozhrania okrem loopback, formát `LINUX_SLL2`), lebo prevádzka mobilnej siete nejde cez
  notebook. `analyzuj_audit.py --zariadenie` berie ako prevádzku telefónu **odchádzajúce pakety** záznamu
  (`sll.pkttype` 4) – cudzie zariadenia v Wi-Fi sieti sa nerátajú; sieť (Wi-Fi, mobilná) podľa rozhrania
  (`ip -o link`). DNS cez router a DNS operátora (CGNAT 100.64.0.0/10) sú lokálna sieť, názvy domén sa však
  započítajú. Správa: `~/lech-work/pixel/audit-<čas>/sprava.md`, kópia v `C:\lech\pixel`. Pri výpadku USB:
  `audit --dokoncit`; nové vyhodnotenie bez telefónu: `audit --vyhodnotit`. Pri prvom audite po nahratí: Wi-Fi
  pripojiť až po spustení auditu.

Čo funguje v LECH OS GSI na Pixeli 9 (doplní sa po teste):

| Funkcia | Stav | Poznámka |
|---------|------|----------|
| Štart, displej, dotyk, USB (adb) | OK | 6. 11. 2026 |
| Wi-Fi | OK | 6. 11. 2026 |
| Fotoaparát | OK | 6. 11. 2026 |
| Mikrofón, nahrávanie obrazovky | OK | 6. 11. 2026 |
| Nabíjanie | OK | 6. 11. 2026 |
| Otáčanie obrazovky (senzory) | OK | 6. 11. 2026 |
| Slovenčina v systéme | OK | 6. 11. 2026 |
| SIM, mobilné dáta | čiastočne | 6. 11. 2026, SIM **4ka**: SIM načítaná, 15 služieb rádia vendora, telefón je v sieti **Orange SK (231 01) cez národný roaming, len EDGE (2G)**; dáta idú, ikona signálu s výkričníkom. 4ka má vlastnú sieť len LTE/5G s hovormi cez VoLTE – bez IMS sa telefón ako „hlasový“ prepne do 2G/3G partnera (3GPP) |
| SMS | nie | 6. 11. 2026: prijatie SMS nefunguje ani v 2G (národný roaming), ani po novom štarte v LTE siete 4ka. 4ka doručuje SMS cez IMS; IMS Pixelu je v `/product`, ktorú GSI nepoužíva → riešenie v E2-11b |
| Volanie | OK (2G) | 6. 11. 2026: odchádzajúci hovor ide – cez 2G národného roamingu, nie VoLTE |
| Bluetooth, GPS, odtlačok prsta, NFC | ? | |

## 4i. Úplná zostava LECH OS pre Pixel 9 (úloha E2-11b)

GSI (časť 4h) beží s vendorom Google, ale bez IMS Pixelu (SMS a VoLTE pri 4ka nefungujú) a bootloader sa s ním
nedá zamknúť. Úplná zostava vytvorí všetky partície telefónu: systém LECH OS, vendor, boot a vbmeta.

Google od Androidu 16 nezverejňuje stromy zariadení Pixel. Podporu zariadenia preberáme z **GrapheneOS**
(ADR-0002 – výberové preberanie overených súčastí; závislosť Z14 v [docs/16](16-zavislosti-a-plan-odchodu.md)):

| Súčasť | Odkiaľ | Čo robí |
|--------|--------|---------|
| `vendor/adevtool` (MIT) | GrapheneOS | z pôvodného obrazu Google (`CP3A.261005.005`) vytvorí `vendor/google_devices/tokay`: súbory vendora, firmvér, prekrytia, oprávnenia, **IMS Pixelu (`ShannonIms`)**; konfigurácia zariadenia (BoardConfig, device.mk) je v `vendor/adevtool/config/mk` |
| `vendor/state` (MIT) | GrapheneOS | stav systémovej zostavy, s ktorým adevtool porovnáva pôvodné obrazy |
| `device/google/caimito-kernels/6.1` (GPL) | GrapheneOS | predzostavené jadro rodiny Pixel 9 |
| `packages/apps/CarrierConfig2` (MIT) | GrapheneOS | nastavenia operátorov z dát Google (VoLTE, SMS cez IMS) |
| `tools/arsclib` (MIT) | GrapheneOS | nástroj na zdroje, ktorý adevtool používa pri prekrytiach |

Verzie sú pevné v [manifests/lech-pixel.xml](../manifests/lech-pixel.xml). Úpravy LECH OS v
[patches/vendor/adevtool](../patches/vendor/adevtool): (1) iné číslo zostavy systému ako pôvodného vendora je len
varovanie (LECH OS má vlastné `BUILD_ID`); (2) vývojárska zostava môže použiť cieľ GSI – ARMv8-A bez pamäťových
značiek (MTE) – a využiť tak systém už zostavený pre GSI (miesto a čas notebooku). Vydania: ARMv9 s MTE.
adevtool potrebuje rozšírenia nástrojov AOSP od GrapheneOS (`aapt2`, `apksigner print-certs`, `dispol -a`); sú
prevzaté ako záplaty s pôvodnými autormi ([patches/README.md](../patches/README.md)), rovnako podpora súborov
`sepolicy_ext.cil` v `system/sepolicy`, do ktorých adevtool zapisuje pravidlá SELinux Pixelu. Politiku Pixelu adevtool
počíta voči platformovej politike AOSP (`plat_sepolicy.cil` zostavy, záplata adevtool 0003). CarrierConfig2 sa zostavuje
bez testovacej obrazovky GrapheneOS, ktorá potrebuje rozhrania GmsCompat.

| Krok | Skript | Trvanie (odhad) |
|------|--------|-----------------|
| Príprava | `12-priprav-pixel.sh`: Node.js 24 a yarn do `~/lech-work/node` (oficiálna zostava, overený súčet), projekty GrapheneOS (`repo sync` len ich), záplaty, `yarn install`, nástroje pre adevtool v bežnom `out/`, `adevtool generate-all -d tokay` (stiahne obraz Google ~3 GB) | 1–2 h (nová analýza Soong) |
| Zostavenie | `12-zostav-pixel.sh`: cieľ `lech_tokay-cp2a-userdebug` ([vendor/lech/products/lech_tokay.mk](../vendor/lech/products/lech_tokay.mk)); najprv platformová politika SELinux vydania `cp2a` (ak sa líši od tej, s ktorou vznikol modul Pixelu, modul sa vytvorí znova), potom celá zostava; balík `C:\lech\pixel-uplne\lech-tokay-img.zip` | prvýkrát niekoľko hodín |
| Nahratie | `11-pixel.sh nahrat-uplne`: `fastboot update` (partície bootloadera, potom dynamické vo fastbootd), vymazanie dát v bootloaderi, štart | 10–15 min |

Potom `11-pixel.sh kontrola`, `siet`, `audit` ako pri GSI a test SMS a volania cez LTE. Úloha E2-12 (zamknutý
bootloader s vlastným kľúčom AVB) stavia na tejto zostave.

## 4j. Nový zostavovací počítač

Prechod na iný počítač (7. 11. 2026: notebook s 64 GB RAM a 1,8 TB diskom namiesto 24 GB a plného C:). Prenášajú sa
len **dva súbory** – všetko ostatné vytvoria skripty znova:

1. **Na starom počítači** – pripnuté verzie zdrojov AOSP a podpisové kľúče LECH OS:
   ```bash
   cd ~/Lech-OS && git pull
   tools/etapa2/04-stiahni-aosp.sh --vytvor-manifest /mnt/c/lech/aosp-pinned.xml
   tar -czf /mnt/c/lech/lech-keys.tgz -C ~ lech-keys
   ```
   Pripnutý manifest je `repo manifest -r`, len projekty so záplatami LECH OS sú v ňom pripnuté na commit pod
   commitmi LECH OS – tie sú len v tomto strome a nový počítač by ich nestiahol. Dá sa vytvoriť aj počas zostavenia.
   Oba súbory preniesť na nový počítač do `C:\lech\` (USB kľúč). **Kľúče sú tajné** – po prenose ich z USB zmazať.
   S rovnakými kľúčmi sa dá do telefónu nahrať nová zostava bez mazania dát; s inými by sa telefón musel vymazať.
2. **Windows na novom počítači – disk WSL na vlastnej partícii.** Disk Linuxu (`ext4.vhdx`) rastie so
   zostavami (na starom počítači 462 GB) a sám sa nezmenšuje; na C: by znova vzal miesto Windows. Preto patrí na
   samostatnú partíciu s aspoň ~1 TB:
   - *Správa diskov* (Win + R, `diskmgmt.msc`): partície **na tom istom disku, tesne za sebou** zlúčiť do jednej.
     Nový notebook (7. 11. 2026, disk 1863 GB: C: 450 GB, D: 434 GB, prázdne E: a F: po 488 GB): pravý klik na F:
     a E: → *Odstrániť zväzok*, potom pravý klik na D: → *Rozšíriť zväzok* (celé voľné miesto) → D: ~1411 GB,
     súbory na D: ostanú. Ak by Windows ponúkol prevod na **dynamický disk**, zrušiť – vtedy partície nie sú za
     sebou alebo sú na rôznych diskoch.
   - V PowerShell ako správca: `wsl --install --no-distribution`, reštart, potom
     `wsl --update` a `wsl --install -d Ubuntu-24.04 --location D:\WSL\Ubuntu` a v Ubuntu vytvoriť používateľa.
     (Už nainštalované Ubuntu sa dá presunúť: `wsl --shutdown`, `wsl --manage Ubuntu-24.04 --move D:\WSL\Ubuntu`.)
     Skripty zisťujú voľné miesto na disku, kde `ext4.vhdx` naozaj leží (`lech_wsl_vhd_win`).

   Pamäť a swap pre WSL (pri 64 GB RAM: 48 GB pre WSL, zvyšok pre Windows):
   ```powershell
   "[wsl2]`nmemory=48GB`nswap=16GB" | Set-Content -Encoding ASCII "$env:USERPROFILE\.wslconfig"; wsl --shutdown
   ```
3. **Ubuntu na novom počítači:**
   ```bash
   sudo apt update && sudo apt install -y git
   git clone https://github.com/Virivkyonline/Lech-OS.git ~/Lech-OS && cd ~/Lech-OS && git checkout claude/lech-os-vision-tudkiw
   tools/etapa2/01-priprava-notebooku.sh          # balíky, Cuttlefish, repo; potom nový terminál
   tools/check_build_host.sh                      # má vyjsť „zvládne zostavenie“
   tar -xzf /mnt/c/lech/lech-keys.tgz -C ~ && chmod -R go-rwx ~/lech-keys && rm /mnt/c/lech/lech-keys.tgz
   tools/etapa2/04-stiahni-aosp.sh --manifest /mnt/c/lech/aosp-pinned.xml   # tie isté verzie AOSP, niekoľko hodín
   tools/etapa2/12-priprav-pixel.sh               # podpora Pixelu (GrapheneOS, adevtool, obraz Google)
   tools/etapa2/12-zostav-pixel.sh --ulohy 16     # prvé úplné zostavenie
   tools/etapa2/11-pixel.sh nastroje && tools/etapa2/11-pixel.sh ovladac   # adb/fastboot a ovládač USB vo Windows
   tools/etapa2/11-pixel.sh nahrat-uplne --bez-mazania
   ```
   Cuttlefish a nočná kontrola (05/06, `09-nastav-nocnu-kontrolu.sh`) neskôr.

`04-stiahni-aosp.sh --manifest` stiahne presne tie verzie projektov ako na starom počítači (vetva
`android-latest-release` sa posúva a záplaty LECH OS by na novšom AOSP nemuseli sedieť); projekty Pixelu z
`manifests/lech-pixel.xml` z neho vynechá, pridá ich `12-priprav-pixel.sh`.

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
| E2-06 | Nahradenie spojení na Google | Audit z E2-03 na produkte LECH neukáže žiadne spojenie na Google | **Hotové 5. 11. 2026** – 4. audit: žiadne spojenie na Google; telefón len DNS siete a `europe.pool.ntp.org` ([audit/README.md](../audit/README.md), [časť 4c](#4c-bez-predvolených-spojení-na-google-úloha-e2-06)); 5 záplat a 1 prekrytie RRO |
| E2-07 | Vlastné testovacie podpisové kľúče | Zostava je podpísaná kľúčmi LECH (testovacími, nie produkčnými) | **Hotové 5. 11. 2026** – aplikácie (aj Bluetooth) a kľúč aktualizácií podpísané kľúčmi LECH, `dev-keys`, overené v telefóne ([časť 4d](#4d-vlastné-testovacie-podpisové-kľúče-úloha-e2-07)). Kľúče APEX a AVB spolu s E2-08 |
| E2-08 | Server aktualizácií v0 | Cuttlefish nainštaluje aktualizáciu z nášho servera (A/B) | **Hotové 5. 11. 2026 (v0)** – `0.1.0-dev → 0.1.0-dev-ota`, slot `_a → _b`, balík podpísaný kľúčom LECH zo servera `ota_server.py` ([časť 4e](#4e-aktualizácia-z-vlastného-servera-úloha-e2-08)). E2-08b: server v EÚ, aplikácia na aktualizácie, podpis celej zostavy; so serverom aj kontrola pripojenia, doména na overenie DNS a kópia zoznamu CT (z E2-06). Rozhodnuté 5. 11. 2026: doména `lech-web.sk` (Websupport, podadresy), server IONOS (Ubuntu 24.04, nginx) |
| E2-09 | CI | Nočné zostavenie, test bootu, sieťový audit a kontroly z `tools/` bežia automaticky | **Hotové 5. 11. 2026 (v0)** – `09-nocna-kontrola.sh` na notebooku, každú noc o 02:30 z Plánovača úloh Windows ([časť 4g](#4g-nočná-kontrola-úloha-e2-09)); prvý beh (ručne): všetkých 5 krokov OK. Ďalej: CI na serveri v EÚ, kontrola každej zmeny pred zlúčením |
| E2-10 | Prvé kompatibilitné testy | 10 aplikácií z F-Droid sa nainštaluje a spustí | **Hotové 5. 11. 2026** – 10 z 10 (prehliadač, pošta, kalendár, MFA, VPN, heslá, komunikácia, mapy, súbory, médiá) bez služieb Google ([kompatibilita/README.md](../kompatibilita/README.md)) |
| E2-11 | Pixel – Device Support Package | LECH OS nabootuje na Pixeli (2 kusy kúpiť po dokončení E2-02 až E2-06) | **E2-11a hotové 6. 11. 2026** – LECH OS ako GSI beží na Pixeli 9 (vendor Android 17 `CP3A.261005.005`): identita, kľúče LECH, NTP aj animácia overené (`11-pixel.sh kontrola`, [časť 4h](#4h-lech-os-na-pixeli-9-úloha-e2-11a)). Bez RKP servera Google (`init.lech.rc`); sieťový audit na telefóne: žiadne spojenie na Google (5. a 6. audit). Zostáva: SMS a VoLTE (IMS), verzia „Baklava“ namiesto 17; E2-11b (začaté 6. 11. 2026): úplná zostava s podporou zariadenia z GrapheneOS (adevtool, [časť 4i](#4i-úplná-zostava-lech-os-pre-pixel-9-úloha-e2-11b)) – **7. 11. 2026 beží úplný LECH OS** (Android 17, `cp2a`): hovory, SMS, mobilné dáta (4ka LTE), Wi-Fi; VoLTE pre 4ka vypnuté už v nastaveniach operátora od Google. Bluetooth a fotoaparát fungujú. Zostáva: sieťový audit úplnej zostavy, GPS; staré aplikácie AOSP Fotoaparát (Camera2) a Galéria (Gallery2) kreslia pod navigačné tlačidlá (vynútené zobrazenie od okraja po okraj od Androidu 15) – nahradiť modernými aplikáciami; eSIM zatiaľ nie je (aplikácie Google vylúčené) – eSIM bez Google cez OpenEUICC je úloha etapy 3 |
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

11. **LECH OS bez spojení na Google (5. 11. 2026, E2-06):** všetky 4 záplaty sa na strom AOSP použili čisto,
    zostavenie trvalo 13 minút a kontrola po štarte prešla vrátane času a RKP. 3. sieťový audit: zo 7 cieľov
    Google ostal jediný – `g.co` ([audit/README.md](../audit/README.md)). DNS dopyty podľa aplikácií audit
    nenačítal, lebo záznam DNS resolvera vyžaduje `adb root` (opravené, `tools/etapa2/kto-sa-pyta.sh`).

12. **Pôvod `g.co` a 4. audit (5. 11. 2026):** `kto-sa-pyta.sh` ukázal, že sa na `g***` pýta
    `com.android.statementservice` (overenie odkazov aplikácií), a `adb shell pm get-app-links` aplikáciu, ktorá
    odkaz deklaruje: `com.android.wallpaper: g.co: legacy_failure`. Po 5. záplate (WallpaperPicker2 bez `autoVerify`)
    zostavenie trvalo 1 minútu a 4. audit neukázal **žiadne spojenie na Google** – E2-06 je hotové.

13. **Kľúče LECH, prvý pokus (5. 11. 2026, E2-07):** prvé zostavenie zlyhalo pri analýze Soong pre nedostatok pamäte
    (`Killed`, exit 137); po zatvorení aplikácií vo Windows prešlo za 18 minút. Telefón však nenaštartoval:
    `VIRTUAL_DEVICE_BOOT_FAILED: Dependencies not ready after 10 checks: Bluetooth` a opakované pády procesu v logcate.
    Druhý pokus (certifikáty SELinux pre moduly na kľúče LECH) tiež nenaštartoval. Diagnostika ukázala pád
    `com.android.bluetooth` v `selinux_android_setcontext` a v `mac_permissions.xml` už kľúč LECH. Príčina:
    aplikácie Bluetooth sa podpisujú vlastným certifikátom modulu (časť 4d). Oprava: `PRODUCT_CERTIFICATE_OVERRIDES`.

14. **E2-07 hotové (5. 11. 2026):** s prepísaným certifikátom aplikácií Bluetooth zostavenie trvalo 10 minút a
    telefón nabootoval. Kontrola po štarte: `dev-keys`, podpis systému kľúčom `platform` LECH OS aj podpis
    `com.android.bluetooth` kľúčom `bluetooth` LECH OS – všetkých 9 kontrol OK.

15. **Prvá aktualizácia z vlastného servera (5. 11. 2026, E2-08):** `08-aktualizacia.sh` – verzia A aj B sa zostavili
    za 2 minúty, balík `ota.zip` mal 1 008 MB. `update_engine` stiahol `payload.bin` zo servera v0 (jedna požiadavka
    s Range), overil podpis kľúčom LECH a zapísal B do druhého slotu (`kSuccess`, `UPDATED_NEED_REBOOT`). Po reštarte:
    `0.1.0-dev → 0.1.0-dev-ota`, slot `_a → _b`. Kontrola „zostava“ ukázala falošnú chybu – číslo zostavy je pri
    lokálnych zostavách vždy `eng.<používateľ>`; skript odteraz porovnáva čas zostavenia.

16. **Prvý test aplikácií z F-Droid (5. 11. 2026, E2-10):** telefón hlási ABI `x86_64,arm64-v8a` (Cuttlefish prekladá
    kód ARM). 8 z 10 aplikácií sa nainštalovalo a spustilo bez pádu; žiadna nepotrebovala služby Google.
    Fennec pre x86_64 padá (`libjnidispatch.so` pre `android-x86-64` chýba v balíku). WireGuard nie je v hlavnom
    repozitári F-Droid. Test teraz uprednostní kód pre `x86_64`, vie vynútiť verziu pre ARM (stĺpec ABI v zozname)
    a žiadosť o povolenie počíta ako úspešné spustenie.

17. **E2-10 hotové (5. 11. 2026):** 2. test – 8 OK vrátane OpenVPN for Android; Fennec x86_64 zablokoval
    `INSTALL_FAILED_VERSION_DOWNGRADE`, lebo 1. test nainštaloval verziu pre ARM (vyššie číslo verzie) – pády Fennec
    patrili ARM pod prekladom (JNA hľadá x86-64). 3. test s čistou inštaláciou: Fennec x86_64 aj Organic Maps OK.
    Spolu **10 z 10** aplikácií.

18. **Prvá nočná kontrola (5. 11. 2026, E2-09):** `09-nastav-nocnu-kontrolu.sh` vytvoril pravidlo `sudo` len pre
    `tcpdump`. Úloha v Plánovači úloh Windows sa však nevytvorila a PowerShell nevypísal žiadnu chybu – skript
    teraz posiela PowerShell príkaz cez `-EncodedCommand` (bez dočasného súboru) a úlohu po vytvorení overí. Po oprave: úloha vytvorená a overená, najbližší beh 6. 11. 2026 o 02:30. Skúšobný beh `09-nocna-kontrola.sh --minuty 5`:
    aktualizácia repozitára OK, kontroly repozitára OK, zostavenie a štart OK (zostavenie 4 min, 9 kontrol telefónu),
    sieťový audit OK (8 cieľov, žiadne nepovolené spojenie telefónu), aplikácie z F-Droid OK (10 z 10).
    Výsledok **OK** – prvá automatická kontrola celej zostavy LECH OS.

19. **Pixel 9 pripojený, bootloader odomknutý (5. 11. 2026, E2-11a):** pôvodný systém Android 17 `CP3A.261005.005`
    (október 2026; zdrojové kódy AOSP na notebooku sú `CP2A`, jún 2026 – LECH OS GSI bude o niečo starší ako vendor
    Pixelu). Poučenia: Windows potrebuje pre režim bootloader ovládač Google USB Driver (`11-pixel.sh ovladac`,
    inštalácia cez `pnputil`, Windows ho ukazuje ako „Google, Inc. SAMSUNG Android Phone“ od Google LLC); nový server
    adb prvé sekundy nevidí telefón; `fastboot devices` vypisuje rozhranie rôzne, preto skript ráta každé zariadenie
    v zozname. Odomknutie: `11-pixel.sh odomknut`, potvrdenie tlačidlami na telefóne.

20. **Prvé zostavenie pre ARM (5. 11. 2026, E2-11a):** analýze zostavy (Soong) pre GSI s 64-bitovým aj 32-bitovým
    ARM došla po 37 minútach pamäť (`Killed`, 24 GB RAM + swap vo WSL). Produkt je odteraz len 64-bitový – Pixel 9
    32-bitový kód nespúšťa (Tensor G4 nemá AArch32) a analýza aj preklad majú menej variantov modulov.
    Druhý pokus (64-bitový GSI) **prešiel za 297 minút** (115 428 krokov): `system.img` 1,6 GB, `vbmeta.img` 64 KB.
    `out/` má po zostavení pre Cuttlefish aj pre ARM 217 GB, voľných zostalo 109 GB. Nočná kontrola 6. 11. sa počas
    zostavenia preskočila (spoločný zámok); ďalšia začne novou analýzou Soong pre Cuttlefish.

21. **LECH OS nabootoval na Pixeli 9 (6. 11. 2026, E2-11a):** `11-pixel.sh nahrat` – `vbmeta` bez overovania,
    `system_b` (1,6 GB, 59 s) po odstránení nepoužívanej `product_b` (v `super` nebolo miesto). Vymazanie dát
    v režime fastbootd Pixel odmietol (`Status(-8, EX_SERVICE_SPECIFIC): '1: Error DCK wipe failed'`) a fastbootd
    potom na príkazy neodpovedal; vymazanie prešlo v bootloaderi (`11-pixel.sh dokoncit`). Po reštarte logo Google
    (bootloader Pixelu) a animácia LECH OS. Skript odteraz maže dáta v bootloaderi, rozpozná zaseknutý fastbootd
    a overí reštart.

22. **Kontrola LECH OS na Pixeli 9 (6. 11. 2026):** model `LECH-PHONE`, `LECH-OS-0.1.0-dev-userdebug`, animácia,
    NTP `europe.pool.ntp.org`, `dev-keys`, podpis systému aj Bluetooth kľúčmi LECH – všetko OK; overený štart
    `orange` (odomknutý bootloader, očakávané). Zistenie: vendor Pixelu nastavuje
    `remote_provisioning.hostname=remoteprovisioning.googleapis.com` – atestačné kľúče (RKP) by sa sťahovali zo
    servera Google. Pri GSI sa vendor meniť nedá; LECH OS to musí riešiť v systéme (rkpd) – rozhodnutie a
    sieťový audit na Pixeli sú ďalší krok. Na audit priamo v telefóne treba do zostavy pridať `tcpdump`
    (v ladiacej zostave AOSP nie je).

23. **Test funkcií a príprava auditu na Pixeli (6. 11. 2026):** Wi-Fi, fotoaparát, mikrofón, nahrávanie obrazovky,
    nabíjanie, otáčanie obrazovky a slovenčina fungujú (tabuľka v časti 4h); SIM zatiaľ nie je. Do zostavy pribudli
    `init.lech.rc` (pre všetky produkty: `remote_provisioning.hostname` sa pred štartom zygote vyprázdni, rkpd
    potom nič nesťahuje – rovnaké rozhodnutie ako v E2-06 na Cuttlefish; následok: atestačné certifikáty kľúčov
    od Google nie sú) a pre Pixel `tcpdump` v ladiacej zostave na sieťový audit priamo v telefóne.

24. **Mobilná sieť na Pixeli (6. 11. 2026, `11-pixel.sh siet`):** SIM 4ka načítaná, rádio vendora funguje
    (15 služieb `android.hardware.radio.*`), registrácia pre hovory `IN_SERVICE`, odchádzajúci hovor ide, dáta
    idú – ale len **EDGE v sieti Orange (národný roaming)**. Príčina: 4ka má vlastnú sieť len LTE/5G a hovory
    a SMS cez VoLTE; IMS Pixelu (Google) je v `/product`, ktorú GSI nepoužíva, takže telefón bez VoLTE prejde
    k partnerovi s 2G. SMS 6. 11. neprišla. Riešenie: IMS v úplnej zostave pre Pixel (E2-11b) – zistiť, ako
    to robí GrapheneOS (súčasti IMS z pôvodného systému Pixelu).

25. **Nočná kontrola 6. 11. 2026 02:30 prerušená:** zostavenie a štart OK (91 min – po zostavení pre Pixel nová
    analýza Soong pre Cuttlefish), sieťový audit 30 min OK (8 cieľov, žiadne nepovolené spojenie telefónu); počas
    testu aplikácií (~04:31) sa kontrola skončila bez súhrnu a Cuttlefish ostal zapnutý – pravdepodobne ju ukončil
    Plánovač úloh pri prechode notebooku na batériu (predvolené nastavenie). Zvyšný Cuttlefish držal zdedený zámok
    zostavenia, preto sa ráno nespustil `11-zostav-gsi.sh`. Opravy: úloha sa pri prechode na batériu už nezastaví
    (`-DontStopIfGoingOnBatteries`, treba znova spustiť `09-nastav-nocnu-kontrolu.sh`), prerušená kontrola vypne
    Cuttlefish a zapíše súhrn PRERUŠENÉ, zámok drží len `flock -o` (procesy na pozadí ho nezdedia).

26. **Prvý audit na Pixeli 9 (6. 11. 2026 07:33, 15 min):** spojenie USB sa počas auditu prerušilo, záznam sa
    stiahol cez `audit --dokoncit`: 332 paketov, **žiadny dopyt DNS**, prevádzka takmer len lokálna (ARP, SSDP, mDNS,
    DHCP). Prvé vyhodnotenie podľa adries telefónu našlo 0 cieľov – adresa mobilnej siete 4ka (CGNAT
    100.100.x.x) v zozname nebola. Odteraz sa prevádzka telefónu určuje podľa smeru paketov a rozhrania.

27. **Sieťový audit LECH OS na Pixeli 9 (6. 11. 2026):** vyhodnotené podľa smeru paketov – 1. audit (15 min)
    aj 2. audit (5 min, Wi-Fi aj mobilné dáta): **žiadny dopyt DNS, žiadne spojenie na Google**; jediné ciele mimo
    lokálnej siete sú DNS servery 4ka `84.245.112.80/.81` na TCP/853 (Android skúša šifrované DNS). Zhrnutie v
    [audit/README.md](../audit/README.md) (5. a 6. audit). Audit odteraz povolí DNS servery, ktoré dala sieť.
28. **Príprava úplnej zostavy pre Pixel 9 (E2-11b, 6. 11. 2026):** `12-priprav-pixel.sh` prešiel celý – projekty
    GrapheneOS, nástroje pre adevtool (11 min, nová analýza Soong) a `adevtool generate-all -d tokay` vytvoril
    `vendor/google_devices/tokay` z obrazu Google `CP3A.261005.005` (4,3 GB). Cestou bolo treba prevziať z GrapheneOS
    rozšírenia `aapt2` (5 commitov), `apksigner print-certs` a `dispol -a`. Pred prvým zostavením: CarrierConfig2
    bez testovacej obrazovky GmsCompat (inak by sa na AOSP nezostavil) a prázdne `TARGET_BOARD_KERNEL_HEADERS`
    (balík jadra GrapheneOS nemá `kernel-headers`).
29. **Prvé zostavenie `lech_tokay` (6. 11. 2026):** analýza Soong prijala modul Pixelu, po 38 min zlyhala politika
    SELinux `system_ext`: `Duplicate declaration of type` (`preloads_copy`) v `vendor/google_devices/tokay/sepolicy/system_ext/public/types.te`.
    Príčina: adevtool počíta politiku Pixelu voči politike GrapheneOS, ktorá niektoré typy AOSP odstránila. Oprava:
    adevtool dostane `plat_sepolicy.cil` zostavy AOSP (`LECH_PLAT_SEPOLICY_CIL`). Cestou sa ukázalo, že AOSP
    nepoužíva súbory `sepolicy_ext.cil`, do ktorých adevtool zapisuje pravidlá pôvodnej politiky Pixelu – prevzatá
    zmena GrapheneOS pre `system/sepolicy/Android.bp`. `12-zostav-pixel.sh` odteraz zastaví zostavenie, ak modul
    Pixelu vytvorila iná verzia adevtool (treba `12-priprav-pixel.sh --len-generovat`); `05-zostav-aosp.sh` pri chybe
    vypíše výstup zlyhaného kroku.
30. **Druhé zostavenie `lech_tokay` (6. 11. 2026):** politika `system_ext` prešla, zlyhala politika vendora
    (`version_policy … -n 202704`: `Failed to resolve allow statement`). Príčina: konfigurácia vydania `trunk_staging`
    má úroveň API vendora 202704 (rozpracovaná), pravidlá pôvodného vendora Pixelu (Android 17) menujú typy platformy
    `…_202604`. Úplná zostava pre Pixel preto používa konfiguráciu Androidu 17 **`cp2a`** (202604; rovnako GrapheneOS,
    jeho `cur` = `aosp_current` = `cp2a`) – zároveň sa verzia zobrazí ako 17, nie „Baklava“. Moduly mainline sa
    zostavujú zo zdrojov (`MODULE_BUILD_FROM_SOURCE`, záplaty LECH v `packages/modules` platia). `12-zostav-pixel.sh`
    najprv zostaví `plat_sepolicy.cil` vydania `cp2a` a modul Pixelu vytvorí znova, ak bol vygenerovaný voči inej
    platformovej politike. Cuttlefish a GSI zatiaľ ostávajú na `trunk_staging`.
31. **Tretie zostavenie `lech_tokay` (6.–7. 11. 2026, `cp2a`):** politika SELinux prešla, po 89 min (32 425 krokov)
    zlyhal `host_init_verifier` na `init.zumapro.usb.rc` vendora: `mkdir /dev/usb-ffs/ctrl 0770 aoad aoad` – používateľ
    `aoad` je až v Androide 17 QPR1. Vendor Pixelu je z QPR1 (`CP3A`), platforma Android 17 (26Q2); GrapheneOS má pre to
    sadu zmien „17-qpr1“. Prevzatých 6 (overené proti súborom AOSP 26Q2): `AID_AOAD` (system/core), `android.frameworks.stats`
    V3 (frameworks/hardware/interfaces, frameworks/native, frameworks/base), nová funkcia `Parcel` v libbinder
    (frameworks/native) a ID zdrojov frameworku podľa CP3A (frameworks/base). Matica kompatibility HAL zmeny nepotrebuje.
32. **Miesto na disku (7. 11. 2026):** ďalšie zostavenie sa nespustilo – na C: ostalo 17 GB (WSL má vnútri 546 GB
    voľných). Disk WSL je jeden súbor na C: (`ext4.vhdx`, 462 GB), ktorý pri zápise rastie a po zmazaní súborov sa sám
    nezmenší; celý sa na D: (120 GB voľných) nezmestí. Nový skript `tools/etapa2/uvolni-miesto.sh` zmaže výstupy, ktoré
    vzniknú znova (obraz Google pre adevtool, výstupy GSI a Cuttlefish), označí voľné miesto (`fstrim`) a pripraví
    `C:\lech\zmensi-disk-wsl.ps1`, ktorý v PowerShell ako správca vypne WSL a zmenší `ext4.vhdx` (`diskpart compact vdisk`).
    Nočná kontrola počas prác na zostave Pixelu zlyhávala (plný disk, iné nastavenia vydania) – vypnutá, kým zostava
    Pixelu nebude hotová.
33. **Prvá úplná zostava LECH OS na Pixeli 9 (7. 11. 2026):** `lech_tokay-cp2a-userdebug` zostavená za 119 min
    (balík 2,9 GB), nahratá `11-pixel.sh nahrat-uplne` (`fastboot update` 157 s; mazanie dát vo fastbootd opäť
    „DCK wipe failed“, v bootloaderi OK – fastbootd potom neodpovedal, `fastboot reboot bootloader` dostal limit 30 s).
    LECH OS naštartoval s vlastným vendorom, boot aj vbmeta; `11-pixel.sh kontrola` všetko OK (model, verzia,
    kľúče LECH, NTP, animácia, RKP bez Google). SIM 4ka **v sieti 4ka na LTE** (s GSI národný roaming Orange, EDGE).
    Obrazy `vbmeta_system` a `vbmeta_vendor` zostava nemá (systém je priamo vo `vbmeta`) – rieši sa pri zamknutí (E2-12).
    `11-pixel.sh siet` odteraz ukazuje aj službu IMS, jej registráciu a VoLTE z nastavení operátora (CarrierConfig2).
34. **Test úplnej zostavy (7. 11. 2026):** Android 17, hovory, Wi-Fi a mobilné dáta (IPv4, DNS) fungujú; CarrierConfig2
    načítal nastavenia Google pre 4ka (`4ka_sk`, 3 APN), VoLTE v nich pre 4ka vypnuté (`carrier_volte_available_bool
    = false`), služba IMS `com.shannon.imsservice` naviazaná. **SMS:** prijaté sa uložia, ale bez upozornenia, a odoslať
    nejde – aplikácia SMS a MMS (AOSP Messaging, `targetSdk 24`) má rolu SMS, no `SmsApplication` ju vyradí:
    `lost android:receive_wap_push` → `isDefaultSmsApp=false`. Android 17 vyžaduje app ops `RECEIVE_WAP_PUSH`
    a `READ_CELL_BROADCASTS`, ktoré nasledujú oprávnenia, a aplikácia si ich nežiadala (aplikácia SMS od GrapheneOS
    áno). Záplata manifestu: [patches/packages/apps/Messaging](../patches/packages/apps/Messaging). Nové príkazy:
    `11-pixel.sh sms` (záznam jedného pokusu o SMS) a `nahrat-uplne --bez-mazania` (novšia zostava bez mazania dát).
    Po zostave so záplatou a nahratí bez mazania dát bolo treba rolu SMS prideliť znova (`11-pixel.sh sms --rola`),
    aby udelila nové oprávnenia – potom **SMS fungujú: odoslanie aj prijatie so zvukom** (7. 11. 2026). Pri čistej
    inštalácii (nahratie s mazaním dát) sa rola prideľuje už s novými oprávneniami.
35. **7. audit – úplný LECH OS (7. 11. 2026):** bez používania žiadne spojenie do 125. s; reklamy a sledovače
    (Google, doubleclick, criteo…) načítal prehliadač zo stránok rádií. Pri štarte však GPS kontaktoval
    `supl.google.com` (A-GPS z nastavení operátora Google) a systém `1.ssiloc.com` (PSDS Samsungu). Rozhodnutie:
    A-GPS a PSDS predvolene vypnuté – záplaty `frameworks/base` 0008 (GrapheneOS: bez IMSI pre SUPL) a 0009
    (mimo tiesňového volania bez serverov SUPL a PSDS). Podrobnosti: [audit/README.md](../audit/README.md), 7. audit.
36. **8.–10. audit (7. 11. 2026, 5 min hneď po štarte):** so záplatami 0008/0009 GPS nikam nevolá. Ostali aplikácie
    eSIM od Google (`lpa.live.esimdiscovery.com`, `lpa.ds.gsma.com`); `pm disable-user` systém pri štarte zrušil.
    Riešenie: adevtool 0004 vylúči aplikácie eSIM z modulu Pixelu a produkt neohlasuje funkciu eSIM. Dopyt DNS na
    `epdg.epc.mnc003.mcc231.pub.3gppnetwork.org` je ePDG 4ka (volania cez Wi-Fi) – infraštruktúra operátora.
37. **Zostava bez eSIM (7. 11. 2026):** 58 min; 11. audit bez eSIM aj GPS – 7 domén Google boli z vyhľadávania
    v prehliadači počas auditu. Pri štarte bolo logo „android“ z AOSP: bootloader Pixelu odovzdá tmavý motív
    (`ro.boot.theme=1`) a animácia sa potom hľadá len v `/product/media/bootanimation-dark.zip`. LECH OS ju odteraz
    inštaluje pod oboma menami; `11-pixel.sh kontrola` overuje súbor, ktorý telefón naozaj otvára.
38. **Nový zostavovací počítač (7.–8. 11. 2026, [časť 4j](#4j-nový-zostavovací-počítač)):** WSL na `D:\WSL\Ubuntu`
    (D:, E: a F: zlúčené, ~1,4 TB), AOSP podľa pripnutého manifestu za 26 min (`04-stiahni-aosp.sh
    --vytvor-manifest` na starom počítači: 15 projektov so záplatami pripnutých pod commitmi LECH OS), prvé úplné
    zostavenie od nuly 7 h 46 min (178 967 krokov; varovania `memory stall` – pamäť bola plná). Ďalšie zostavenia
    prekladajú len zmeny.
39. **Prerušené nahratie (8. 11. 2026):** z nového notebooku sa spojenie USB opakovane prerušilo (chyby 22, 31, 433)
    – `vendor_b` ostal napoly zapísaný a telefón zo slotu b nenaštartoval ani do fastbootd. Port, do ktorého bol
    telefón pripojený, sa predtým poškodil (dym pri pripojení telefónu); nahrávalo sa cez iný port. Postup opravy:
    1. Štartovacie oddiely v bootloaderi: z `lech-tokay-img.zip` vybaliť `boot init_boot vendor_boot
       vendor_kernel_boot dtbo pvmfw vbmeta` a `fastboot flash <oddiel>_b <oddiel>.img`.
    2. `fastboot reboot fastboot` – po štarte z poškodeného slotu Pixel pri každom štarte ukáže „Your device is
       corrupt“ (dm-verity v režime eio) a čaká na tlačidlo napájania; bez neho sa vypne. `fastboot update` naň
       nepočká („Failed to boot into userspace fastboot“), preto ručne.
    3. Vo fastbootd `fastboot flash` pre `system system_ext product vendor system_dlkm vendor_dlkm`, potom reboot.
    4. Slot b po neúspešných štartoch minul pokusy (`slot-retry-count:b` 1) a init so starými dátami štart zastavil
       (Fastboot Mode, „Enter reason: BL1 requested“) – `fastboot -w` a `fastboot --set-active=b` (telefón bol
       prázdny). Potom LECH OS naštartoval s animáciou LECH OS.
    5. Varovanie „Your device is corrupt“ zruší reštart s dôvodom `dm-verity enforcing`:
       `adb shell 'reboot "dm-verity enforcing"'` (`adb reboot "…"` text rozdelí). `kontrola` stav hlási.

    `fastboot update` musí začínať v bootloaderi – vo fastbootd zlyhá na `dtbo` („No such file or directory“);
    `nahrat-uplne` telefón do bootloadera prepne sám.
40. **12.–14. audit (8. 11. 2026):** LECH OS z nového počítača po vymazaní dát – nečinný telefón aj 5 min po štarte
    **0 cieľov**, záznam DNS od štartu prázdny; čas sa synchronizuje s `europe.pool.ntp.org` pri pripojení k sieti.
    Kontrolný audit s Wikipédiou meranie potvrdil; spojenia na Google boli len z vyhľadávania v prehliadači.
    Hovory, SMS, dáta a Wi-Fi fungujú. Podrobnosti: [audit/README.md](../audit/README.md), 11.–14. audit.

Nástroje Cuttlefish na hostiteľovi sa pri štarte pokúšajú kontaktovať `metadata.google.internal` (zisťujú, či bežia
v cloude Google). Mimo Google Cloud to zlyhá bez vplyvu na štart; do auditu virtuálneho telefónu to nepatrí,
pretože ide o prevádzku notebooku, nie telefónu.

Etapa 2 je hotová, keď sú splnené kritériá z [docs/08](08-plan-etap.md#etapa-2--vlastná-zostava-aosp).
