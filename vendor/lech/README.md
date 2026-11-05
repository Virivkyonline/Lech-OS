# vendor/lech – produkt LECH OS pre zostavovací systém AOSP

Tento adresár sa kopíruje do stromu AOSP ako `vendor/lech` (robí to `tools/etapa2/06-zostav-lech.sh`).
Zostavovací systém AOSP v ňom nájde súbor `AndroidProducts.mk` a z neho produkty LECH OS.
Postup a rozhodnutia sú opísané v [docs/11, časť 4b](../../docs/11-etapa-2-cuttlefish.md#4b-prvý-produkt-lech-os-úlohy-e2-04-a-e2-05).

| Súbor | Účel |
|-------|------|
| `AndroidProducts.mk` | Zoznam produktov LECH OS a ponuka pre `lunch` |
| `products/lech_cf_x86_64_only_phone.mk` | Virtuálny telefón LECH-PHONE pre Cuttlefish. Dedí z `aosp_cf_x86_64_only_phone` a mení názov, značku a model |
| `config/common.mk` | Identita spoločná pre všetky produkty: verzia v *Build number*, `ro.lech.version`, animácia pri štarte |
| `config/version.mk` | Verzia LECH OS – jediné miesto, kde sa mení |
| `bootanimation/bootanimation.zip` | Animácia pri štarte, vytvorená skriptom `tools/make_bootanimation.py` |
| `overlay/frameworks-res/` | Prekrytie (RRO) nastavení frameworku Android v partícii product: sieťový čas `europe.pool.ntp.org` namiesto `time.android.com` |

Pravidlá:

- **Čo sa dá nastaviť tu, nerobiť záplatou.** Zmeny kódu AOSP sú v [patches/](../../patches) ([ADR-0010](../../docs/adr/0010-upravy-aosp-ako-zaplaty.md)).
- **Meniť len partície product (a neskôr system_ext a vendor), nie systémový obraz.** Malý patch set
  ([ADR-0002](../../docs/adr/0002-zaklad-systemu-aosp.md)) uľahčuje prechod na nové verzie AOSP.
- **Názov produktu bez pomlčky.** `lunch` delí cieľ `produkt-release-variant` práve podľa pomlčiek.
- **V súboroch `.mk` nepísať komentár na koniec riadku s priradením.** Make by do hodnoty započítal medzery
  pred znakom `#`.
- Ďalší produkt (napríklad Pixel v úlohe E2-11) dedí z produktu zariadenia v AOSP a z `config/common.mk`.
