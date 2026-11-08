# bezpecnost – mesačné bezpečnostné opravy z AOSP

Google zverejňuje opravy chýb Androidu v mesačných [bulletinoch](https://source.android.com/docs/security/bulletin)
a commity do AOSP. Celé AOSP vychádza od roku 2026 len dvakrát ročne, preto LECH OS mesačné opravy preberá sám:

```bash
tools/etapa2/14-bezpecnost.sh stav 2026-09 2026-10   # stiahne bulletin a commity, správa ~/lech-work/bezpecnost/<mesiac>/stav.md
tools/etapa2/12-zostav-pixel.sh --ota --verzia …     # aplikuj-zaplaty.sh použije opravy mesiacov z mesiace.txt
```

- `mesiace.txt` – ktoré bulletiny sa preberajú. Opravy idú v každom projekte **pred** úpravy LECH OS (`patches/`),
  v poradí bulletinu; commit má autora z AOSP a committera `LECH OS`.
- Oprava, ktorá už v AOSP je, sa preskočí; oprava, ktorá sa na strom nedá použiť (bola pre inú vetvu), sa preskočí
  a vypíše ako *neprispôsobená* – zostavenie nezastaví. Prispôsobená verzia patrí ako záplata do `patches/`.
- `vynechat.txt` – opravy, ktoré LECH OS zámerne nepreberá (s dôvodom).
- Opravy jadra prídu s novším predzostaveným jadrom GrapheneOS a opravy firmvéru a súčastí výrobcov čipov
  s novším pôvodným obrazom Google (vendor) – tie bulletin neodkazuje do AOSP.
- Úroveň zabezpečenia v telefóne (`ro.build.version.security_patch`) sa zvýši, až keď sú v zostave všetky opravy
  daného mesiaca (aj jadro a vendor).
