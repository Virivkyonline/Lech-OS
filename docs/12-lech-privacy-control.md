# 12 – LECH Privacy Control

## Prečo samostatná oblasť

„Telefón nič neposiela“ je len vyhlásenie. LECH Privacy Control z neho robí **overiteľnú vlastnosť**: telefón technicky
zaznamenáva, ktorá aplikácia čo používa a kam posiela údaje, ukáže to používateľovi a dovolí to okamžite zastaviť.
Zároveň to vieme **overiť zvonka** – meraním mimo telefónu a nezávislým auditom.

Panel „Odchod údajov“ z predchádzajúcich dokumentov je súčasťou LECH Privacy Control.

## Čo používateľ uvidí

| Funkcia | Čo ukazuje | Okamžitý zásah |
|---------|------------|----------------|
| **Mikrofón** | Ktorá aplikácia ho používa teraz a kedy ho použila (časová os) | Zakázať aplikácii mikrofón; globálny vypínač mikrofónu |
| **Kamera** | Ktorá aplikácia ju používa teraz a kedy ju použila | Zakázať aplikácii kameru; globálny vypínač kamery |
| **Poloha** | Kto pristupoval k polohe, presnej alebo približnej, na popredí alebo na pozadí | Zmeniť na približnú alebo zakázať |
| **Senzory** | Prístup k pohybovým senzorom | Zakázať (nové povolenie Senzory) |
| **Kam odchádzajú údaje** | Pre každú aplikáciu zoznam domén a krajín, s ktorými komunikuje; označené známe trackery | Zablokovať doménu pre aplikáciu |
| **Koľko údajov** | Prenesené dáta pre každú aplikáciu za deň, týždeň a mesiac; Wi-Fi a mobilné dáta zvlášť | – |
| **Internet** | Či má aplikácia prístup na internet | **Jedným dotykom zakázať prístup na internet** (povolenie Sieť) |
| **Systém a AI** | Každý prenos systémových služieb LECH a LECH AI: kedy, kam, prečo, koľko | Vypnúť príslušnú službu |
| **Týždenný prehľad** | Zhrnutie: nové domény, nezvyčajne veľké prenosy, aplikácie, ktoré sa ozývajú na pozadí | Odkaz na nastavenia danej aplikácie |

Celý záznam zostáva **len v telefóne**, šifrovaný, s nastaviteľnou dobou uchovania (predvolene 30 dní).
Neodosiela sa nikam – ani nám.

## Technické riešenie

### Mikrofón, kamera, poloha

AOSP už obsahuje evidenciu používania oprávnení (AppOps), indikátory kamery a mikrofónu a prehľad súkromia
(Privacy Dashboard, od Android 12). Na tom staviame: predĺžime históriu, doplníme senzory a spojíme to so sieťovými
údajmi do jedného prehľadu.

### Sieťová prevádzka po aplikáciách

| Údaj | Odkiaľ ho systém vezme | Poznámka |
|------|------------------------|----------|
| Objem dát pre každú aplikáciu | Štatistiky sieťovej prevádzky podľa UID (AOSP ich už zbiera pomocou eBPF v `netd`) | Spoľahlivé, existujúci mechanizmus |
| Domény, na ktoré sa aplikácia pýta | Systémový DNS resolver (modul `DnsResolver`) – pozná UID aplikácie, ktorá sa pýta | Úprava systémového modulu. Prvý krok od 5. 11. 2026: sieťový audit číta záznam resolvera (`dumpsys dnsresolver querylog`); Android v ňom z názvu ukáže len prvé písmeno |
| Cieľové IP adresy a porty | Záznam nadviazaných spojení podľa UID (eBPF) | Zachytí aj aplikácie, ktoré obchádzajú systémové DNS |
| Krajina a vlastník cieľa | Lokálna databáza IP rozsahov, aktualizovaná so systémom | Bez dopytov na internet |
| Známe trackery | Lokálny zoznam domén a knižníc trackerov | Rovnaký zoznam ako pri štítkoch v LECH Store |

**Blokovanie:** povolenie **Sieť** je skutočná systémová brána (pravidlá firewallu podľa UID), nie len skrytie ikonky.
Aplikácia bez povolenia Sieť sa nedostane na internet ani cez inú aplikáciu, ani cez systémové služby
v jej mene (napr. sťahovanie súborov). Rovnako budú fungovať blokované domény.

**Úprimné obmedzenia:**

- Vidíme, **kam** aplikácia posiela údaje a **koľko**, ale nie **čo** – obsah je šifrovaný (TLS). Systém nebude
  obsah rozšifrovávať; bolo by to v rozpore s bezpečnosťou.
- Ak aplikácia používa vlastné šifrované DNS alebo pevne zadané IP adresy, uvidíme IP adresu a vlastníka,
  nie názov domény.
- Prenosy cez spoločné systémové komponenty (napr. sťahovanie, push notifikácie) treba priradiť správnej aplikácii –
  každá taká služba musí pôvodcu zapisovať explicitne. Toto je súčasťou požiadaviek na všetky systémové služby LECH.

## Nezávislé overenie

Používateľ nemusí veriť panelu. Overujeme ho tromi spôsobmi:

1. **Meranie mimo telefónu (laboratórium):** telefón je pripojený cez testovaciu sieť, kde zaznamenávame všetku
   prevádzku na routeri. Automatický test porovná záznam z routera so záznamom v telefóne.
   **Cieľ: každé spojenie zachytené routerom je v telefóne priradené konkrétnej aplikácii alebo službe.**
   Test beží pri každom vydaní a výsledky zverejňujeme.
2. **Reprodukovateľné zostavy:** ktokoľvek si môže zo zverejneného zdrojového kódu zostaviť systém a overiť,
   že je zhodný s tým, ktorý distribuujeme. Bez toho by otvorený kód nebol dôkazom.
3. **Nezávislý bezpečnostný audit** (etapa 8 a 9) – vrátane overenia, že Privacy Control nie je možné obísť.

Metodiku testov zverejníme, aby si ich mohli zopakovať novinári, výskumníci aj firemní zákazníci.

## Kedy vznikne

| Čo | Etapa |
|----|-------|
| Laboratórne meranie prevádzky a audit prvého spustenia | 2 (súčasť brány „Overený základ“, [ADR-0008](adr/0008-postupny-vyvoj-a-brany.md)) |
| Povolenia Sieť a Senzory, evidencia prevádzky po aplikáciách, panel Privacy Control | 4 |
| Záznam prenosov LECH AI | 5 |
| Porovnanie panelu s meraním mimo telefónu ako automatický test | 4, potom pri každom vydaní |
