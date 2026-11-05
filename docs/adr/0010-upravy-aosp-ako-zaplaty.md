# ADR-0010: Úpravy kódu AOSP ako sada záplat v repozitári

- **Stav:** Prijaté (2026-11-05)
- **Dátum:** 2026-11-05

## Kontext

Pri úlohe E2-06 sa ukázalo, že väčšinu spojení na Google sa nedá vypnúť iba nastavením produktu
([docs/11, časť 4c](../11-etapa-2-cuttlefish.md#4c-bez-predvolených-spojení-na-google-úloha-e2-06)):

- adresy kontroly pripojenia sa dajú zmeniť, ale vypnúť ju možno len zmenou predvoleného správania,
- názvy na overenie šifrovaného DNS a adresa zoznamu Certificate Transparency sú v kóde napevno,
- server atestačných kľúčov nastavujú súbory Cuttlefish a produkt LECH ho nemôže prepísať.

Treba teda rozhodnúť, ako LECH OS mení zdrojový kód AOSP. Zásada [ADR-0002](0002-zaklad-systemu-aosp.md) platí:
úpravy majú byť čo najmenšie.

## Zvážené možnosti

1. **Vlastné kópie (forky) upravených projektov AOSP** na GitHube a manifest, ktorý ich použije namiesto
   pôvodných. Je to bežný postup veľkých projektov. Pri prvých štyroch drobných úpravách by sme však museli
   spravovať štyri veľké repozitáre, z ktorých každý má tisíce súborov.
2. **Sada záplat v repozitári Lech-OS** (`patches/<cesta projektu AOSP>/*.patch`). Skript
   `tools/etapa2/aplikuj-zaplaty.sh` ich použije na stiahnutý strom AOSP ako commity. Všetky zmeny proti AOSP
   sú tak na jednom mieste, dajú sa ľahko skontrolovať a spočítať. Pri novej verzii AOSP skript presne
   ukáže, ktorá záplata sa nedá použiť.
3. **Úpravy priamo v stiahnutom strome bez záznamu.** To je neopakovateľné a neprehľadné, preto vylúčené.

## Rozhodnutie

1. **Možnosť 2: sada záplat.** Každá záplata mení jednu vec v jednom projekte, obsahuje vysvetlenie v správe
   commitu a je čo najmenšia. Zmeny, ktoré sa dajú urobiť nastavením produktu (`vendor/lech`, prekrytia RRO),
   záplatou nerobíme.
2. **Prechod na forky** (možnosť 1) zvážime, keď záplaty v jednom projekte prestanú byť malé alebo keď
   budeme potrebovať vlastné zrkadlo zdrojových kódov ([docs/16](../16-zavislosti-a-plan-odchodu.md), Z1 a Z2).
3. **Dočasné vypnutie namiesto náhrady:** kým LECH OS nemá vlastný server, spojenie na Google radšej vypneme,
   než by sme ho presmerovali na cudzí server. Každé takéto vypnutie má v [docs/11](../11-etapa-2-cuttlefish.md)
   uvedený dôsledok a cieľový stav.

## Dôsledky

- ✅ Celý rozdiel medzi LECH OS a AOSP je v repozitári: `vendor/lech` a `patches/`.
- ✅ Prechod na novú verziu AOSP: `repo sync`, potom `aplikuj-zaplaty.sh`. Záplaty, ktoré sa nedajú použiť, sa ukážu hneď.
- ❌ Stiahnutý strom `~/aosp` už nie je čisté AOSP. Čisté AOSP na porovnanie ostáva v hotových obrazoch
  (`tools/etapa2/02-ciste-aosp.sh`).
- ❌ Záplaty v moduloch AOSP (NetworkStack, Connectivity, DnsResolver) treba pri každej novej verzii AOSP preveriť.
  Moduly sa v LECH OS aktualizujú so systémom, nie cez Google Play.

## Kedy rozhodnutie prehodnotiť

- Keď sada záplat presiahne približne 30 záplat alebo keď jeden projekt bude mať viac ako 5 záplat.
- Keď budeme potrebovať zostavenie z vlastného zrkadla AOSP (úloha E2-09, CI).
