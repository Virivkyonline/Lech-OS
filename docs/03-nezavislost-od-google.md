# 03 – Nezávislosť od Google a kompatibilita aplikácií

## Východisko: AOSP verzus Google Mobile Services

Android, ktorý ľudia poznajú z bežných telefónov, má dve časti:

- **AOSP (Android Open Source Project)** – otvorený základ systému. Licencia Apache 2.0, môžeme ho voľne používať a upravovať.
- **GMS (Google Mobile Services)** – uzavretý balík Google: Play Store, Play Services, Google účet, push notifikácie,
  mapy, poloha, záloha, Play Integrity. Licenciu na GMS dostávajú len certifikovaní výrobcovia za podmienok,
  ktoré sú v rozpore s princípmi LECH OS (napr. povinné predinštalovanie aplikácií Google).

LECH OS stavia **len na AOSP**. Všetko, čo pochádza z GMS, musíme nahradiť alebo urobiť voliteľným.

## Náhrady služieb Google

| Funkcia (Google) | Náhrada v LECH OS | Náročnosť | Poznámka |
|------------------|-------------------|-----------|----------|
| Google účet | Žiadne konto; [LECH Identity](06-lech-store-a-cloud.md#lech-identity) len pre dobrovoľné služby | Nízka | Princíp P2 |
| Play Store | **LECH Store** | Vysoká | Pozri [docs/06](06-lech-store-a-cloud.md) |
| Synchronizácia kontaktov a kalendára | CardDAV / CalDAV – LECH Cloud alebo vlastný server | Nízka | Otvorené štandardy, existujúce open-source implementácie |
| Push notifikácie (FCM) | **UnifiedPush** + vlastný push server; FCM len cez vrstvu kompatibility | Stredná | UnifiedPush už podporujú napr. Element, Nextcloud, SimpleX; veľké aplikácie väčšinou len FCM |
| Záloha | Šifrovaná záloha na strane klienta (vychádzame zo Seedvault, ktorý je integrovaný v AOSP) | Stredná | Kľúč má len používateľ |
| Mapy | Mapy postavené na OpenStreetMap, offline | Nízka | Existujú vyspelé open-source aplikácie |
| Sieťová poloha (Fused Location) | Lokálna fúzia GNSS + databáza Wi-Fi / BTS (BeaconDB alebo vlastná), offline cache | Stredná | Mozilla Location Service bola v roku 2024 ukončená |
| A-GPS (SUPL) | Vlastný proxy alebo nezávislý poskytovateľ | Nízka | AOSP predvolene používa server Google – treba zmeniť |
| Kontrola pripojenia, NTP čas | Vlastné servery | Nízka | AOSP predvolene volá servery Google |
| WebView (Chrome) | Vlastná zostava Chromium WebView s úpravami súkromia | Vysoká | WebView treba aktualizovať tak často ako prehliadač |
| Play Protect | Skener aplikácií LECH Security | Stredná | Pozri [docs/05](05-lech-security.md) |
| Google Asistent | **LECH AI** | Vysoká | Pozri [docs/04](04-lech-ai.md) |
| Klávesnica a hlasové písanie | Open-source klávesnica + lokálny prevod reči na text | Stredná | Slovenčina je kritická |
| Aktualizácie systému | Vlastný server aktualizácií | Stredná | Pozri [docs/02](02-architektura.md#aktualizácie) |
| **Play Integrity** | **Nemá plnohodnotnú náhradu** | **Kritická** | Pozri nižšie |

### Audit volaní na servery Google v AOSP

Aj samotné AOSP obsahuje predvolené spojenia na infraštruktúru Google (napr. kontrola pripojenia, čas, A-GPS,
zachytávanie captive portálov). Súčasťou etapy 2 je **úplný audit sieťovej prevádzky** a nahradenie každého
takého spojenia. Výsledkom je automatický test „prvé spustenie bez neznámych spojení“ (princíp P1).

## Úrovne kompatibility aplikácií

| Úroveň | Typ aplikácie | Funguje na LECH OS? |
|--------|---------------|---------------------|
| **0** | Aplikácie bez závislosti od Google (napr. z F-Droid, mnohé európske aplikácie) | Áno, bez úprav |
| **1** | Aplikácie, ktoré Google služby používajú voliteľne (ak chýbajú, vypnú len napr. push alebo mapu) | Áno, s obmedzeniami |
| **2** | Aplikácie, ktoré Google Play Services vyžadujú (push, poloha, prihlásenie Google, platby v aplikácii) | Áno, **so zapnutou vrstvou kompatibility** |
| **3** | Aplikácie, ktoré vyžadujú certifikované zariadenie (Play Integrity – niektoré bankové aplikácie, Google Wallet, časť hier a streamovacích služieb) | **Problém** – rieši sa individuálne |

Cieľ „nechcem, aby polovica aplikácií nefungovala“ je pri úrovniach 0–2 **realisticky dosiahnuteľný**.
Úroveň 3 je jediná, kde nemáme plnú kontrolu nad výsledkom.

## Vrstva kompatibility s Google službami

Možnosti:

| Možnosť | Ako funguje | Kompatibilita | Súkromie | Riziká |
|---------|-------------|---------------|----------|--------|
| **Žiadna** | Žiadne Google služby | Úrovne 0–1 | Najlepšie | Mnohé bežné aplikácie nebudú fungovať |
| **microG** | Open-source reimplementácia častí Play Services | Dobrá, ale neúplná | Dobré | Vyžaduje „podvrhnutie podpisu“ pre microG; reimplementácia vždy zaostáva za originálom |
| **Google Play v sandboxe** | Originálne Google Play Services bežia ako **obyčajná aplikácia bez systémových privilégií**; systém im poskytuje len tenkú vrstvu kompatibility | Najlepšia dosiahnuteľná | Dobré – Google vidí len to, čo aplikácia smie vidieť; používateľ ich vie kedykoľvek odinštalovať | Väčšia inžinierska práca; právne posúdenie distribúcie |

**Odporúčanie ([ADR-0003](adr/0003-kompatibilita-google-sluzieb.md)):**

- predvolený stav je **bez Google**,
- v sprievodcovi prvým spustením si používateľ môže zvoliť **„Google Play v sandboxe“** – s jasným vysvetlením,
  čo to znamená,
- Google aplikácie nikdy nie sú predinštalované a nikdy nedostanú systémové privilégiá,
- vrstvu kompatibility je možné zapnúť len pre vybraný **profil** (napr. pracovný profil s bankou a firemnými
  aplikáciami), zatiaľ čo hlavný profil ostáva bez Google.

Tento prístup v praxi overil projekt GrapheneOS. Jeho kód je open source; pri preberaní akéhokoľvek kódu
dodržíme licencie (pozri [docs/09](09-pravo-a-licencie.md)).

## Play Integrity – najväčšie riziko kompatibility

**Čo to je:** Play Integrity je služba Google, cez ktorú aplikácia zisťuje, či beží na zariadení certifikovanom
spoločnosťou Google s originálnym systémom. Niektoré bankové aplikácie, Google Wallet, časť hier a streamovacích
služieb odmietnu fungovať, ak test neprejde.

**Úprimne:** LECH OS ako necertifikovaný systém **neprejde** kontrolami Play Integrity, ktoré vyžadujú certifikované
zariadenie. Neexistuje legitímny technický spôsob, ako to obísť, a nebudeme sa o to pokúšať (obchádzanie by bolo
nespoľahlivé a poškodilo by dôveryhodnosť bezpečnostného produktu).

**Čo s tým urobíme:**

1. **Hardvérová atestácia s našimi kľúčmi.** Android má štandardné API hardvérovej atestácie (súčasť AOSP),
   ktoré nezávisí od Google. Zverejníme otlačky našich kľúčov overeného bootu, aby aplikácie mohli LECH OS
   bezpečne rozpoznať a povoliť.
2. **Aktívna spolupráca s bankami** – najprv v SK a CZ, potom v krajinách ďalších vĺn ([ADR-0009](adr/0009-trh-eu.md)). Zmapujeme bankové aplikácie (etapa 7) a oslovíme banky
   s technickým podkladom, ako LECH OS overiť. Toto je obchodná, nie len technická úloha.
3. **Sledovanie európskych iniciatív.** V roku 2026 vznikla európska iniciatíva *Unified Attestation* ako
   open-source alternatíva k Play Integrity. Je kontroverzná (GrapheneOS ju kritizuje ako nahradenie jedného
   strážcu brány iným). Sledujeme ju a rozhodneme sa na základe toho, či je otvorená pre nezávislé systémy.
4. **Regulácia.** Akt o digitálnych trhoch (DMA) a súťažné právo EÚ môžu tlačiť na otvorenejšie pravidlá.
   Ako európsky výrobca môžeme byť aktívni v konzultáciách.
5. **Platby cez NFC.** Google Wallet fungovať nebude. Zmapujeme banky, ktoré majú vlastné platby v aplikácii,
   a alternatívy (platobné hodinky, karty).

> **Dôležité pre cestu B:** ani vlastný telefón automaticky nerieši Play Integrity. Ten by vyriešila len
> certifikácia GMS, ktorá je v rozpore s princípmi projektu. Preto riziko neodkladáme „na neskôr“ –
> riešime ho od etapy 1.

## Meranie kompatibility

Kompatibilitu nebudeme odhadovať, ale merať:

1. **Zoznam cieľových aplikácií:** spoločné celoeurópske aplikácie + 200 najpoužívanejších aplikácií v každej krajine
   aktuálnej vlny uvádzania (najprv SK a CZ, [ADR-0009](adr/0009-trh-eu.md)), rozdelených do kategórií
   (banky, komunikácia, doprava, štátne služby, nákupy, médiá, práca, zdravie).
2. **Automatizovaný test:** inštalácia, spustenie, základný scenár, push notifikácia, poloha – v troch režimoch
   (bez Google / microG / Google Play v sandboxe).
3. **Verejná databáza kompatibility:** používatelia pred kúpou vidia, ktoré aplikácie fungujú.

**Cieľové metriky (overíme v etape 7):**

| Režim | Cieľ pre zoznam top 200 |
|-------|-------------------------|
| Google Play v sandboxe | ≥ 95 % plne funkčných, zvyšok zdokumentovaný |
| Bez Google | zmerať a zverejniť (bez cieľa – slúži na informovanie) |
| Bankové aplikácie v krajinách aktuálnej vlny | zoznam funkčných a stav rokovaní s každou bankou |

## Zdroje

- Play Integrity a alternatívy: [Android Authority](https://androidauthority.com/custom-roms-vs-google-3469378),
  [LWN.net](https://lwn.net/Articles/1004034)
- Unified Attestation: [Born's Tech, 10. 3. 2026](https://borncity.com/blog/2026/03/10/unifiedattestation-oss-alternative-zu-google-play-integrity-fuer-android/),
  [PiunikaWeb, 10. 3. 2026](https://piunikaweb.com/2026/03/10/grapheneos-calls-on-privacy-focused-app-developers-to-boycott-european-unified-attestation/)
- UnifiedPush: [F-Droid – 5 years of UnifiedPush, 8. 1. 2026](https://f-droid.org/cs/2026/01/08/unifiedpush-5-years.html)

(overené 31. 10. 2026)
