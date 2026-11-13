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
Čínski výrobcovia na zákazku (ODM) až neskôr: bez zmluvy zvyčajne nedajú zdrojové kódy ani zamknutie vlastnými kľúčmi.

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
>
> We are happy to sign an NDA if required.
>
> Kind regards,
> Stanislav …, Lechstav s. r. o., Slovakia
