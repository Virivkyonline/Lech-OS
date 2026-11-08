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
  a vypíše ako *neprispôsobená* – zostavenie nezastaví.
- **Prispôsobené opravy:** pri konflikte `14-bezpecnost.sh stav` hľadá verziu, ktorú GrapheneOS upravil pre svoju
  vetvu Androidu 17 (rovnaké `Change-Id`, github.com/GrapheneOS, vetva `17`); ak sa dá použiť, `aplikuj-zaplaty.sh` ju
  použije namiesto commitu z AOSP. Overené prispôsobené opravy sú v repozitári v `prisposobene/<projekt>/<CVE>.patch`
  (majú prednosť). 8. 11. 2026: všetkých 6 neprispôsobených opráv zo septembra a októbra prevzatých z GrapheneOS
  (autor z AOSP ostáva, licencia Apache 2.0).
- **Opravy bez odkazu do AOSP:** Google zverejňuje opravy platformy v AOSP neskoro (november 2026: ani 6 dní po
  bulletine), GrapheneOS ich zverejní vo svojich repozitároch v deň bulletinu. `14-bezpecnost.sh stav` preto opravy,
  ktoré bulletin uvádza bez commitu, hľadá podľa čísla chyby (`A-…` v bulletine, `Bug: …` v commite) v repozitároch
  GrapheneOS, ktoré má aj strom LECH OS (manifest GrapheneOS, `aosp-name`); nájdené použije `aplikuj-zaplaty.sh`.
  Súčasti výrobcov čipov (Qualcomm, Arm…) takto nájsť nemožno – tie prídu s firmvérom (vendor).
- `vynechat.txt` – opravy, ktoré LECH OS zámerne nepreberá (s dôvodom).
- Opravy jadra prídu s novším predzostaveným jadrom GrapheneOS a opravy firmvéru a súčastí výrobcov čipov
  s novším pôvodným obrazom Google (vendor) – tie bulletin neodkazuje do AOSP.
- Úroveň zabezpečenia v telefóne (`ro.build.version.security_patch`) sa zvýši, až keď sú v zostave všetky opravy
  daného mesiaca (aj jadro a vendor).
