# 19 – Dopyt na výrobcov hardvéru (vzorky)

Stav: návrh 13. 11. 2026. Cieľ vlastníka: vzorky od menšieho výrobcu, ponuky, **softvér nahráva LECH OS sám**
(kým nie je zaregistrovaná ochranná známka, telefóny bez značky LECH OS). Kritériá: [docs/07](07-zariadenia.md).

## Koho osloviť

| Výrobca | Prečo | Čo je overené / neoverené |
|---------|-------|---------------------------|
| **Volla (Hallo Welt Systeme, Nemecko)** – prvá voľba | Malá firma zameraná na súkromie a systémy bez Googlu; telefóny podporujú viac systémov | Komunitný prehľad uvádza odomknutie cez fastboot bez kódov a **vlastné kľúče AVB** (zamknutý bootloader s vlastným systémom). Neoverené: ochota predávať vzorky/veľkoobchod, dĺžka podpory firmvéru, zdrojové kódy |
| **SHIFT (Nemecko)** | Malý výrobca opraviteľných telefónov, otvorený iným systémom | Neoverené: vlastné kľúče AVB, podmienky |
| **Fairphone (Holandsko)** | V pláne ako druhá platforma (docs/07); spolupracuje s /e/OS | Zamknutie s vlastnými kľúčmi oficiálne áno, komunita hlási problémy (docs/07) |

Zdroj k Volla: [bootloader-unlock-wall-of-shame, Volla](https://tangled.org/zenfyr.dev/bootloader-unlock-wall-of-shame/tree/main/brands/volla/README.md).

### Čínski výrobcovia na zákazku (ODM) – voľba vlastníka kvôli cene (13. 11. 2026)

Výhoda: výrazne nižšia cena a ako zákazník ODM môžeme žiadať, aby **náš kľúč overeného štartu zabudovali priamo do
bootloadera** (telefón potom štartuje bez varovania). Riziká, ktoré musí dopyt pokryť:

- **Kľúče secure boot:** kto ich vlastní a napáli do čipu (eFuse) – musia byť naše alebo nám odovzdané, inak výrobca
  vie podpísať vlastný firmvér pre naše telefóny.
- **Zdrojové kódy (BSP):** úplný balík pre čipset (pri MediaTek/Unisoc licencovaný), na akú verziu Androidu; bez neho
  LECH OS na zariadenie neprenesieme. Android 17 – overiť, či ho čipset vôbec má.
- **Podpora firmvéru:** lacné modely mávajú opravy modemu a TEE len 1–2 roky – pre značku súkromia zásadné, žiadať
  písomný záväzok.
- **Pôvod hardvéru** bude súčasťou našej bezpečnostnej dokumentácie (docs/07); niektorí firemní zákazníci čínsky
  hardvér obmedzujú.
- **Minimálny odber** pri vlastnom modeli býva tisíce kusov; vzorky existujúcich modelov sa dajú kúpiť po kusoch.

Koho osloviť: menšie značky zo Šen-čenu, ktoré robia aj výrobu na zákazku (napr. Unihertz, Ulefone, Oukitel, Doogee,
Blackview, Cubot) – **mená sú len tipy na oslovenie, ich podmienky nie sú overené**. K textu dopytu pridať body
o kľúčoch secure boot, BSP a písomnej dĺžke podpory (nižšie bod 7).

**Blackview – prvý na oslovenie:** vlastník má na nich kontakt a vlastní ich mobil aj tablet (13. 11. 2026); modely
a čipsety doplniť, na zariadeniach overiť odomknutie bootloadera a verziu Androidu.

## Čo musí ponuka obsahovať (bez toho LECH OS nenahráme bezpečne)

1. **Bootloader:** odomknutie bez obmedzení a **zamknutie s vlastnými kľúčmi AVB** (overený štart s kľúčom LECH OS),
   aká obrazovka/varovanie sa pri štarte zobrazí.
2. **Zdrojové kódy a súbory:** jadro (GPL), device tree, binárne ovládače (vendor) s právom ich šíriť v našom systéme,
   nástroj a postup na nahranie systému (fastboot) a na obnovu po zlyhaní.
3. **Podpora firmvéru:** ako dlho a ako často vychádzajú bezpečnostné opravy modemu, bootloadera a TEE; verzia Androidu,
   na ktorú sú ovládače (cieľ: Android 17).
4. **Hardvér:** čipset, RAM 12 GB a viac, úložisko, NFC, eSIM, pásma pre EÚ, hardvérové úložisko kľúčov (StrongBox/TEE),
   certifikácie CE/RED pre predaj v EÚ.
5. **Obchod:** cena vzoriek (2–5 ks), cena pri 100 / 500 / 1000 ks, dodacia lehota, záruka a servis, možnosť dodania
   **bez predinštalovaného systému alebo s odomknutým bootloaderom**, neskôr vlastná značka na zariadení a obale.
6. **Dôvernosť:** NDA, ak ju výrobca žiada pre zdrojové kódy.
7. **Len pre ODM:** vlastníctvo kľúčov secure boot (eFuse), zabudovanie nášho kľúča AVB do bootloadera, úplný BSP
   pre čipset a verzia Androidu, písomný záväzok dĺžky bezpečnostných opráv.

## Text dopytu (anglicky, na odoslanie e-mailom)

> Subject: Request for samples and quotation – privacy-focused Android OS vendor (Slovakia)
>
> Dear Sir or Madam,
>
> we are Lechstav s. r. o., a company from Slovakia developing LECH OS, a privacy-focused operating system based on
> the Android Open Source Project (Android 17). It currently runs on a reference device with verified boot and a locked
> bootloader using our own signing keys. We are looking for a hardware partner and would like to evaluate your devices.
>
> We would install our own operating system on the devices ourselves. Could you please send us a quotation and the
> following information for your current model(s):
>
> 1. Bootloader: can it be unlocked without restrictions and re-locked with custom AVB keys (verified boot with our
>    own key)?
> 2. Availability of kernel sources, device tree and vendor binaries, and the terms for redistributing the vendor
>    binaries as part of our OS; flashing and recovery tools.
> 3. Firmware support: how long and how often do you release security updates for modem, bootloader and TEE; the
>    Android version your drivers target.
> 4. Hardware specification: chipset, RAM, storage, NFC, eSIM, EU bands, hardware-backed keystore (StrongBox/TEE),
>    CE/RED certification.
> 5. Prices: 2–5 sample units, and 100 / 500 / 1000 units; lead time, warranty and service; delivery without a
>    preinstalled OS or with an unlocked bootloader; custom branding at a later stage.
> 6. (ODM) Secure boot: who owns and burns the secure boot keys (eFuse)? Can the bootloader be built with our AVB
>    public key? Do you provide the full chipset BSP (which Android version), and can you commit in writing to the
>    period of security updates for modem, bootloader and TEE?
>
> We are happy to sign an NDA if required.
>
> Kind regards,
> Stanislav …, Lechstav s. r. o., Slovakia

## Záznam oslovení

- 13. 11. 2026 **Blackview** (Lily Chen, WeChat): dopyt odoslaný; odpoveď: OEM pri 100/500/1000 ks nerobia („too small
  quantity“). Odoslaná otázka na MOQ, nákup bežných modelov a technické body (bootloader, BSP, opravy) – čaká sa.
- 13. 11. 2026 **Shenzhen Connectech Technology (Cwell)** (Ada Guo, Alibaba, „Verified Custom Manufacturer“, 9 rokov,
  online tržby 1–2 mil. USD): dopyt odoslaný – čaká sa. Overiť, či majú vlastný vývoj dosiek a BSP.
  Odpoveď 13. 11. (Ada Guo): robia OEM/ODM vrátane dodania telefónov na nahranie vlastného systému AOSP; postup
  dopyt → vzorky → záloha → schválenie vzorky → výroba → doprava, min. ~30 dní od schválenia; **BSP vedia dať, za
  poplatok**; technické body (bootloader s naším kľúčom AVB, eFuse, opravy, ceny, MOQ) postúpené technikom – čaká sa.
  Pýtajú vizitku, termín uvedenia a či sú vzorky len na interné testy.
- 13. 11. 2026 **Shenzhen Pretech Industrial** (Wendy Wang, Alibaba, „Verified Multispecialty Supplier“ – skôr obchodná firma než výrobca; 10 rokov, 6 hodnotení, miera sporov 8,2 %): dopyt odoslaný – čaká sa.
- 13. 11. 2026 **Shenzhen Teckunion Electronic** (Alibaba; uvádza „Manufacturer“, ale 5–10 zamestnancov a hlavný tovar použité a repasované telefóny, bez overenia Alibabou, odpovedá na 50 % správ – takmer isto obchodník, nie výrobca): dopyt odoslaný – čaká sa.
  Odpoveď 13. 11. (Wendy Wang): tvrdia „full ODM“ (vlastný hardvér aj softvér); jediný model: Unisoc T606, 6 GB + 128 GB, 6,5" 720×1600, len 4G, NFC, Android 14/16; **MOQ 3000 ks**. Hodnotenie: slabý čip z roku 2021, málo RAM (cieľ 12 GB), bez 5G, BSP nie pre Android 17, vysoký minimálny odber – **nevhodné**; bootloader/kľúče/BSP nezodpovedané.
