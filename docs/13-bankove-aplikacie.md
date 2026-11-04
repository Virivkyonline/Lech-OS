# 13 – Bankové aplikácie

## Cieľ

Na LECH OS musia fungovať aplikácie hlavných bánk v každej krajine, kde ho uvádzame ([ADR-0009](adr/0009-trh-eu.md)).
Začíname na Slovensku – **Tatra banka, Slovenská sporiteľňa, VÚB, ČSOB** a ďalšie – a v Česku; potom nasledujú
krajiny vlny 2 (Nemecko, Rakúsko, Benelux). Bez toho bude predaj bežným používateľom ťažký. Pre firemný pilot ([ADR-0007](adr/0007-prvy-trh-firmy.md))
je to menej kritické, pre spotrebiteľský trh je to podmienka.

## Prečo je to ťažké

Banky chránia aplikácie pred podvodmi aj kontrolou zariadenia. Časť z nich používa **Play Integrity** od Google, ktorá
uzná len zariadenie certifikované spoločnosťou Google. LECH OS ju neprejde a obchádzať ju nebudeme
([docs/03](03-nezavislost-od-google.md#play-integrity--najväčšie-riziko-kompatibility)).

Banka však môže zariadenie overiť aj **štandardnou hardvérovou atestáciou Android** (Key Attestation), ktorá je súčasťou AOSP
a nezávisí od Google. Bezpečnostný čip telefónu podpíše informáciu o stave zariadenia vrátane:

- či je bootloader zamknutý,
- či systém prešiel overeným bootom,
- **odtlačku kľúča, ktorým je systém podpísaný** (u nás kľúč LECH).

Banka si teda vie overiť, že beží originálny, nezmenený LECH OS na zamknutom zariadení. Ide o rovnaký hardvérový mechanizmus,
na ktorom stavia aj najprísnejšia úroveň kontroly Google. Stačí, aby banka do svojej kontroly doplnila naše zverejnené
odtlačky kľúčov.

> Na Pixeli so zamknutým bootloaderom a vlastným kľúčom sa pri štarte zobrazuje upozornenie, že telefón spúšťa iný
> operačný systém. Je to bezpečnostná vlastnosť hardvéru, nie chyba – treba ju vysvetliť zákazníkom aj bankám.

## Postup

### Krok 1 – Zoznam a testovací protokol (teraz, bez vývoja)

Cieľové banky – vlna 1, Slovensko (zoznam doplníme o Česko a krajiny ďalších vĺn):

| Banka | Stav na LECH OS |
|-------|-----------------|
| Tatra banka | neotestované |
| Slovenská sporiteľňa | neotestované |
| VÚB | neotestované |
| ČSOB | neotestované |
| Prima banka | neotestované |
| UniCredit Bank | neotestované |
| 365.bank | neotestované |
| mBank | neotestované |
| Fio banka | neotestované |
| Revolut | neotestované |

Pre každú banku testujeme rovnaké scenáre:

| Scenár | Prečo |
|--------|-------|
| Inštalácia a spustenie | Niektoré aplikácie skončia hneď pri štarte |
| Aktivácia aplikácie a prihlásenie | Často vyžaduje overenie zariadenia |
| Prihlásenie biometriou | Využíva hardvérové úložisko kľúčov |
| Platobný príkaz a jeho potvrdenie | Kľúčová funkcia |
| Potvrdenie platby kartou na internete (3-D Secure) cez aplikáciu | Bez toho zákazník nezaplatí online |
| Potvrdenie prihlásenia do internet bankingu na počítači | Bežný spôsob overenia |
| Push notifikácie | Závisia od vrstvy kompatibility s Google |
| Platba telefónom cez NFC | Google Wallet nebude fungovať; bankové riešenia zmapovať |

Každý scenár testujeme v troch režimoch: **bez Google**, **Google Play v sandboxe** a pre porovnanie na bežnom Androide.

### Krok 2 – Podklady pre banky (počas etapy 2)

Pripravíme technický dokument pre bezpečnostné a mobilné tímy bánk:

- ako LECH OS overiť hardvérovou atestáciou (s ukážkou kódu),
- zverejnené odtlačky kľúčov overeného bootu pre každé podporované zariadenie,
- naše bezpečnostné záväzky: mesačné aktualizácie, doba podpory, proces hlásenia zraniteľností
  ([docs/14](14-aktualizacie-a-podpora.md)),
- výsledky nezávislého auditu (keď budú k dispozícii).

### Krok 3 – Testovanie (po dokončení etapy 2)

Banky aj my potrebujeme reálne zariadenie. Testovať začneme, keď beží LECH OS na Pixeli so zamknutým bootloaderom.
Výsledky zapisujeme do tabuľky vyššie a do verejnej databázy kompatibility.

### Krok 4 – Rokovania s bankami

- Osloviť bezpečnostné a mobilné tímy bánk s technickým dokumentom a testovacím zariadením.
- Zvážiť spoločný postup cez **národné bankové asociácie** (na Slovensku Slovenská banková asociácia) – jedna technická
  špecifikácia pre všetky banky v krajine; tá istá špecifikácia poslúži v celej EÚ.
- Firemný pilot je dobrý argument: banky môžu testovať v kontrolovanom prostredí.
- Kontakty s bankami začneme budovať **hneď**, aj keď vývoj ešte nepokročil – rokovania trvajú mesiace.

## Čo urobíme, ak banka nespolupracuje

- Zákazník to uvidí **pred kúpou** vo verejnej databáze kompatibility.
- Odporučíme náhradné riešenie banky (internet banking v prehliadači, iný spôsob overenia platby).
- Pre platby v obchode: platobná karta alebo hodinky.
- Nikdy nebudeme odporúčať obchádzanie bezpečnostných kontrol bánk.

## Meranie úspechu

| Míľnik | Cieľ |
|--------|------|
| Brána 4 ([ADR-0008](adr/0008-postupny-vyvoj-a-brany.md)) | Stav všetkých bánk zo zoznamu je otestovaný a zverejnený |
| Pred spotrebiteľským predajom v krajine | Aplikácie bánk, ktoré používa väčšina klientov v danej krajine, fungujú aspoň v režime Google Play v sandboxe |
