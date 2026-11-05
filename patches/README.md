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
| `packages/modules/NetworkStack` | Kontrola pripojenia predvolene vypnutá | Kontroluje sa voči `connectivitycheck.gstatic.com` a `www.google.com` |

Pravidlá:

- **Záplata mení jednu vec.** Komentár `LECH OS:` v kóde vysvetľuje, čo sa zmenilo a prečo.
- **Záplata vzniká proti nezmenenému súboru z AOSP:** `git format-patch` v čistom projekte, autor `LECH OS`.
- **Čo sa dá nastaviť v `vendor/lech`** (nastavenie produktu, prekrytie RRO), **sa nerobí záplatou.**
