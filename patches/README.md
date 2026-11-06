# patches – zmeny kódu AOSP v LECH OS

Každý súbor `<cesta projektu AOSP>/NNNN-popis.patch` je jedna zmena v jednom projekte AOSP. Správa commitu
vysvetľuje, prečo zmena vznikla. Rozhodnutie a pravidlá sú v [ADR-0010](../docs/adr/0010-upravy-aosp-ako-zaplaty.md).

```bash
tools/etapa2/aplikuj-zaplaty.sh             # použije záplaty na ~/aosp (06-zostav-lech.sh to robí sám)
tools/etapa2/aplikuj-zaplaty.sh --kontrola  # len vypíše, ktoré záplaty sú použité
```

| Projekt AOSP | Záplata | Prečo (úloha E2-06) |
|--------------|---------|---------------------|
| `device/google/cuttlefish` | Bez servera atestačných kľúčov | Cuttlefish volá testovací server Google `preprod-remoteprovisioning.googleapis.com` |
| `packages/modules/Connectivity` | Zoznam Certificate Transparency sa predvolene nesťahuje | Sťahuje sa z `www.gstatic.com` |
| `packages/modules/DnsResolver` | Overenie šifrovaného DNS s názvom `…probe.lech.invalid` | Pôvodný názov `…metric.gstatic.com` patrí Google |
| `packages/apps/WallpaperPicker2` | Odkaz `g.co/wallpaper` bez overovania (`autoVerify`) | Overenie sťahuje `https://g.co/.well-known/assetlinks.json` pri prvom štarte |
| `packages/modules/NetworkStack` | Kontrola pripojenia predvolene vypnutá | Kontroluje sa voči `connectivitycheck.gstatic.com` a `www.google.com` |
| `frameworks/base` (z GrapheneOS, E2-11b) | 5 rozšírení aapt2 od GrapheneOS (autori ostávajú): text konfigurácie v `Configuration.proto` (`stringified`), formát prevodu `proto-adevtool`, príkaz `dump brief-package-info` (aj s prvkom `library`), knižnica `aapt2_proto_java` | Potrebujú ich adevtool a `tools/arsclib` pri generovaní podpory Pixelu. Zmena aapt2 raz spôsobí nový preklad zdrojov aplikácií vo všetkých produktoch |
| `tools/apksig` (z GrapheneOS, E2-11b) | Príkaz `apksigner print-certs` (a jeho oprava) | adevtool ním zisťuje certifikáty predpodpísaných aplikácií Pixelu (SELinux). Zmena apksig raz spôsobí nové podpísanie aplikácií |
| `external/selinux` (z GrapheneOS, E2-11b) | `dispol`: chýbajúce úvodzovky a výpis typov s atribútmi (`-a`) | adevtool ním rozoberá politiku SELinux pôvodného systému Pixelu |
| `vendor/adevtool` (GrapheneOS, E2-11b) | Iné číslo zostavy systému ako vendora je len varovanie | LECH OS má vlastné `BUILD_ID`, súbory vendora sú z pôvodnej zostavy Google `CP3A.261005.005` |
| `vendor/adevtool` (GrapheneOS, E2-11b) | Vývojárska zostava s cieľom ARMv8-A (`LECH_PIXEL_GENERIC_CPU`) | Využije systém zostavený pre GSI; vydania zostávajú ARMv9 s MTE |

Záplaty pre projekty Pixelu ([manifests/lech-pixel.xml](../manifests/lech-pixel.xml)) sa preskočia, kým projekty
nie sú stiahnuté (`tools/etapa2/12-priprav-pixel.sh`).

Pravidlá:

- **Záplata mení jednu vec.** Komentár `LECH OS:` v kóde vysvetľuje, čo sa zmenilo a prečo.
- **Záplata vzniká proti nezmenenému súboru z AOSP:** `git format-patch` v čistom projekte, autor `LECH OS`.
- **Čo sa dá nastaviť v `vendor/lech`** (nastavenie produktu, prekrytie RRO), **sa nerobí záplatou.**
