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

## Text dopytu č. 2 – 2 vzorky, potom 100 ks (18. 11. 2026)

Zadanie vlastníka: najprv 2 vzorky na testovanie, potom 100 ks na skúšku predaja, pri úspechu väčšie objemy.
Predošlé odpovede: zákazková výroba (OEM) pri 100 ks nie je možná, MOQ 3000 ks – dopyt preto žiada **existujúci
model zo skladu**, bez úprav hardvéru. Systém nahrávame sami; výrobca nedostane zdrojový kód LECH OS ani kľúče.

> Subject: Inquiry – 2 samples, then 100 pcs of an existing smartphone model (own OS, Slovakia)
>
> Hello,
>
> we are Lechstav s. r. o. from Slovakia. We develop our own privacy-focused operating system based on Android
> (AOSP). It already runs on a reference phone with a locked bootloader and our own signing keys. We are looking
> for a hardware supplier.
>
> Our plan:
> 1. now: 2 sample units for testing,
> 2. after successful tests: 100 units for a sales trial,
> 3. if the trial goes well: larger regular orders.
>
> We do not need a custom hardware design at this stage – an existing model from your current range is fine.
> We install the operating system ourselves; we only need the device and the technical access described below.
>
> Please send a quotation and answer these points for the model(s) you recommend:
>
> A. Device
>  1. Model name, chipset, RAM / storage (we need at least 8 GB RAM, preferably 12 GB), display, battery.
>  2. 5G and 4G bands (must work in the EU), NFC, eSIM, fingerprint sensor.
>  3. Android version currently shipped, and the Android version your drivers (BSP) support.
>  4. CE / RED certification for sale in the EU.
>
> B. Software access (required – without it we cannot use the device)
>  5. Can the bootloader be unlocked without restrictions (fastboot)?
>  6. Can it be locked again with our own verified boot (AVB) key, so the phone boots our system in a locked state?
>  7. Do you provide the kernel source code, device tree and vendor binaries, with the right to distribute the
>     binaries as part of our system? Is there a fee, and how much?
>  8. Flashing tool and procedure, and how to recover a device that does not boot.
>  9. How long and how often do you release security updates for the modem, bootloader and TEE firmware?
>
> C. Commercial
>  10. Price for 2 samples, and unit price for 100 / 500 / 1000 pcs.
>  11. Delivery time for samples and for 100 pcs; shipping to Slovakia (EU).
>  12. Can the 100 pcs be delivered without your logo, or with a neutral back cover and box?
>      Is our own logo possible later, and from what quantity?
>  13. Warranty, defect rate handling and spare parts.
>  14. Payment terms.
>
> The samples are for internal testing only. We can sign an NDA if you require one.
>
> Thank you, we look forward to your offer.
>
> Stanislav
> Lechstav s. r. o., Slovakia

Krátka verzia do chatu (WeChat, Alibaba):

> Hello, we are Lechstav s. r. o. (Slovakia). We develop our own Android-based OS and install it ourselves.
> We need: 2 samples now, then 100 pcs for a sales trial, larger orders later. An existing model is fine
> (min. 8 GB RAM, 5G, NFC, EU bands, CE). Required: bootloader unlock, re-lock with our own AVB key, kernel source
> and vendor binaries. Please send model, specs, price for 2 / 100 / 500 / 1000 pcs and delivery time. Thank you.

Poznámka pre nás (do dopytu nepatrí): LECH OS dnes beží len na Pixeli 9 a stojí na základe GrapheneOS, ktorý
podporuje len Pixely. Iný telefón znamená preniesť systém na nový hardvér (ovládače od výrobcu, vlastná práca na
úrovni zariadenia) a niektoré ochrany základu závisia od čipu (napr. označovanie pamäte). Bez bodov 5–7 sa to
nedá vôbec; s nimi je to práca na týždne až mesiace, ktorú overia až vzorky.

## Zadanie pre sprostredkovateľa (slovenská firma, 18. 11. 2026)

Vlastník chce vyhľadanie dodávateľa v Číne zadať slovenskej firme (uviedol názov Incelt; firmu sme
nepreverovali). Text zadania:

> **Predmet: Zadanie – vyhľadanie dodávateľa smartfónov v Číne (2 vzorky, potom 100 ks)**
>
> Dobrý deň,
>
> sme Lechstav s. r. o. Vyvíjame vlastný operačný systém pre telefóny postavený na Androide, zameraný na
> súkromie a bezpečnosť. Systém je hotový na referenčnom telefóne a hľadáme výrobcu hardvéru, na ktorý ho
> nahráme. Prosíme o ponuku na vyhľadanie a preverenie dodávateľa.
>
> **Čo hľadáme**
> Existujúci model smartfónu z bežnej ponuky výrobcu (nie vývoj na zákazku). Operačný systém si do telefónov
> nahrávame sami, od výrobcu potrebujeme zariadenie a technický prístup k nemu.
>
> **Postup a množstvá**
> 1. 2 vzorky na testovanie,
> 2. po úspešnom teste 100 ks na skúšobný predaj,
> 3. pri úspechu pravidelné väčšie objednávky.
>
> **Nevyhnutné podmienky (bez nich je telefón pre nás nepoužiteľný)**
> - bootloader sa dá odomknúť bez obmedzení a znova zamknúť **naším vlastným kľúčom** overeného štartu (AVB),
> - výrobca dodá zdrojový kód jadra, popis zariadenia (device tree) a binárne ovládače s právom šíriť ich
>   v našom systéme; ak je to spoplatnené, cenu,
> - nástroj a postup na nahranie systému a na obnovu telefónu, ktorý nenabehne,
> - certifikácia CE / RED pre predaj v EÚ, pásma 4G a 5G pre EÚ.
>
> **Požadované parametre**
> - RAM aspoň 8 GB (radšej 12 GB), 5G, NFC, snímač odtlačkov, podľa možnosti eSIM,
> - ovládače pre čo najnovší Android (uveďte verziu),
> - písomný údaj, ako dlho a ako často výrobca vydáva bezpečnostné opravy modemu a firmvéru.
>
> **Čo od vás potrebujeme**
> 1. zoznam 3–5 vhodných výrobcov alebo modelov s odpoveďami na priložené technické otázky (anglický dopyt
>    v prílohe môžete poslať výrobcom priamo),
> 2. overenie, že ide o skutočného výrobcu, nie o obchodníka ani o repasované či použité zariadenia,
> 3. ceny: 2 vzorky, 100 / 500 / 1000 ks, dodacie lehoty, doprava na Slovensko, clo a DPH,
> 4. možnosť dodať 100 ks bez loga výrobcu (neutrálny kryt a obal) a neskôr s naším logom – od akého množstva,
> 5. záruka, riešenie chybných kusov, platobné podmienky,
> 6. zabezpečenie nákupu a dovozu 2 vzoriek.
>
> **Dôvernosť**
> Zdrojový kód nášho systému ani podpisové kľúče výrobcovi neodovzdávame a nie sú predmetom rokovania.
> Dohodu o mlčanlivosti (NDA) podpíšeme, ak ju výrobca žiada.
>
> **Prosíme uviesť**
> cenu vašej služby (vyhľadanie, preverenie, vzorky, prípadne dovoz 100 ks), čas, za ktorý dodáte zoznam
> výrobcov, a kontaktnú osobu.
>
> Ďakujeme.
> Stanislav, Lechstav s. r. o.

Príloha zadania: anglický „Text dopytu č. 2“ vyššie.

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
- 13. 11. 2026 **Shenzhen Cxt Technology** (fiona liu, Alibaba): ponúkli „box phone“ – skrinky so základnými doskami z použitých telefónov Samsung Galaxy (S8–S23, Z Fold/Flip) a Pixel 4a s jedným zdrojom 550 W (farmy telefónov). Nie sú to nové telefóny ani vlastná výroba; Samsung nedovolí zamknutie vlastnými kľúčmi – **nevhodné**.
  Doplnenie 15. 11. (fiona liu): sami potvrdili, že základné dosky sú **vybraté z mobilných telefónov** („not repair boards“ = neopravované, nie nové), určené na farmy telefónov – hodnotenie sa nemení, **nevhodné**.
  Odpoveď technikov 13. 11. (Ada, WeChat; firma vystupuje aj ako značka **UNIWA** / cwelltech.com): **BSP stojí 90 000 USD, MOQ 3000 ks**. Model, čip, verzia Androidu, kľúče a bootloader zatiaľ neuvedené. Hodnotenie: vstupná investícia rádovo stovky tisíc USD – pre začiatok priveľa; lacnejší štart je predaj na Pixeloch (BSP netreba, 7 rokov opráv).

## Podmienky vlastníka pred platbou za BSP (13. 11. 2026)

Ak sa platí 90 000 USD, musí byť všetko pod našou kontrolou a **výrobca nedostane zdrojové kódy LECH OS ani naše
kľúče**. Výrobcovi stačia hotové podpísané obrazy systému na nahranie vo výrobe (alebo telefóny nahráme sami).
Pred platbou písomne v zmluve: presný obsah BSP (čo je zdrojový kód a čo len binárne súbory), verzia Androidu,
licencia na úpravy a šírenie, vlastníctvo kľúčov secure boot, zamknutie bootloadera naším kľúčom AVB, dĺžka
bezpečnostných opráv, cena za kus, parametre, vzorka na overenie pred výrobou, platba po častiach.
