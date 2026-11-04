# 15 – Podnikateľský plán (pracovná verzia)

> **Stav:** prvý návrh zo 4. 11. 2026. Čísla označené **(P)** sú **predpoklady**, nie overené údaje – slúžia na to,
> aby sa dali výpočty ľahko prepočítať. Pred použitím pre investorov alebo v žiadosti o grant ich treba overiť
> (zoznam v časti [Čo treba overiť](#čo-treba-overiť)).

## 1. Zhrnutie

**LECH OS je mobilný operačný systém, v ktorom telefón patrí používateľovi.** Stavia na otvorenom základe Androidu
(AOSP), nepotrebuje konto Google, neposiela údaje na reklamu a jeho asistent LECH AI pracuje primárne priamo v telefóne
– aj v menších jazykoch EÚ, ktoré veľkí poskytovatelia obsluhujú slabšie. Infraštruktúra (aktualizácie, cloud, AI servery)
beží v EÚ alebo priamo u zákazníka. **Cieľovým trhom je celá Európska únia** ([ADR-0009](adr/0009-trh-eu.md)).

- **Problém:** bežný Android a iOS sú postavené na obchodnom modeli, ktorý potrebuje údaje používateľov. Firmy a
  inštitúcie nemajú kontrolu nad tým, kam ich údaje z telefónov odchádzajú, a AI asistenti posielajú obsah do cloudu
  amerických firiem.
- **Dôkaz:** náš audit ukázal, že aj čisté AOSP sa pri prvom zapnutí bez súhlasu spojí so **7 službami Google**
  ([audit/README.md](../audit/README.md)). Telefóny s Google službami komunikujú ešte viac.
- **Riešenie:** LECH OS s meraným súkromím (nula neznámych spojení, overené mimo telefónu), LECH Privacy Control,
  lokálnou AI a európskou infraštruktúrou.
- **Trh:** celá EÚ. Prvými zákazníkmi sú firmy a organizácie, ktoré potrebujú kontrolu nad údajmi – pilot s 1–3
  organizáciami na Slovensku a v Česku ([ADR-0007](adr/0007-prvy-trh-firmy.md)), potom Nemecko, Rakúsko a Benelux
  a ďalšie krajiny EÚ; paralelne bezplatná komunitná verzia.
- **Obchodný model:** platí sa za služby, nie údajmi – firemné licencie a správa zariadení, predplatné LECH Cloud,
  firemná AI, neskôr predaj telefónov.

## 2. Prečo teraz

1. **Digitálna suverenita v EÚ** – tlak na nezávislosť od amerických platforiem rastie vo verejnej správe aj vo firmách.
2. **Regulácia** – Akt o kybernetickej odolnosti (CRA, hlásenia od 9/2026, plné povinnosti od 12/2027), GDPR a AI Act
   zvyšujú nároky na transparentnosť; LECH OS je na ne navrhnutý od začiatku ([docs/09](09-pravo-a-licencie.md)).
3. **Google uzatvára Android** – od 2025 nezverejňuje device trees Pixel, od 2026 zverejňuje zdrojové kódy len
   dvakrát ročne a postupne zavádza povinné overenie vývojárov aj pre aplikácie mimo Play Store.
   Rastie dopyt po nezávislej alternatíve.
4. **Lokálna AI je prvýkrát použiteľná v telefóne** – telefóny s 12+ GB RAM zvládnu malé jazykové modely.
5. **Výrobcovia sú otvorení partnerstvám** – Motorola oznámila na MWC 2026 telefón s GrapheneOS (2027).

## 3. Produkt a odlíšenie

| Oblasť | LECH OS | Bežný telefón s Google |
|--------|---------|------------------------|
| Súkromie | Merané: nula neznámych spojení, nezávislý audit | Desiatky spojení na Google, reklamný identifikátor |
| Konto | Nepovinné | Prakticky povinné |
| Kontrola | Privacy Control: kam a koľko posiela každá aplikácia, internet vypnúť jedným dotykom | Prehľad oprávnení, bez kontroly sieťovej prevádzky |
| AI | Lokálne, v jazykoch EÚ vrátane menších, pravidlá mimo modelu, zameniteľní poskytovatelia | Prevažne v cloude jedného poskytovateľa |
| Infraštruktúra | EÚ alebo u zákazníka, otvorené protokoly | Cloud Google |
| Kód | Otvorený (Apache 2.0 / AGPL v3) | Prevažne uzavretý |

**Slabé stránky, ktoré otvorene priznávame:** bankové aplikácie a platby cez NFC (Play Integrity), fotoaparát bez
Google Camera, rýchlosť bezpečnostných záplat (bez embarga), menší ekosystém. Pozri [docs/10](10-rizika-a-otvorene-otazky.md).

## 4. Trh a zákazníci

### Segmenty (v poradí priority)

| Segment | Prečo platia | Príklady | Etapa |
|---------|--------------|----------|-------|
| **Firmy s citlivými údajmi** | Kontrola nad údajmi, GDPR, dôvernosť | Advokátske kancelárie, zdravotníctvo, účtovníci, výroba s know-how | Pilot (etapa 8) |
| **Verejná správa a samospráva** | Suverenita, audit, dlhodobá podpora | Mestá, obce, úrady | Po pilote |
| **Bezpečnostne citlivé profesie** | Overiteľná bezpečnosť | Novinári, manažéri, bezpečnostné firmy | Po pilote |
| **Jednotlivci dbajúci na súkromie** | Súkromie bez expertných znalostí | Komunitná verzia, neskôr predaj telefónov | Komunitná verzia od etapy 8 |

### Krajiny – uvádzanie po vlnách ([ADR-0009](adr/0009-trh-eu.md))

| Vlna | Krajiny | Úloha |
|------|---------|-------|
| 1 | Slovensko, Česko | Pilot, prvé referencie, ladenie produktu |
| 2 | Nemecko, Rakúsko, Benelux | Hlavné uvedenie v EÚ – veľký trh firiem a vysoké povedomie o súkromí |
| 3 | Francúzsko, Poľsko, severské krajiny, ďalšie | Rozšírenie cez partnerov |

### Veľkosť trhu – postup výpočtu

Odhad sa urobí zdola nahor z verejných štatistík (Eurostat, národné štatistické úrady):

- **Celý trh (TAM):** počet firemných mobilných zariadení v EÚ × ročná cena za zariadenie.
- **Dosiahnuteľný trh (SAM):** segmenty z tabuľky vyššie v krajinách vĺn 1 a 2.
- **Reálny cieľ (SOM):** podiel, ktorý vieme získať za 3–5 rokov s naším tímom a partnermi.

**Príklad výpočtu (P):**

| Horizont | Organizácie | Zariadenia na organizáciu | Cena | Ročne |
|----------|-------------|---------------------------|------|-------|
| Vlna 1 (SK, CZ) | 200 | 40 | 8 € / mesiac | 768 000 € |
| Vlna 2 (DE, AT, Benelux) | 1 500 | 50 | 8 € / mesiac | 7 200 000 € |

Čísla sú len ilustrácia; skutočné hodnoty doplníme zo štatistík a z pilotu.

## 5. Obchodný model

| Zdroj príjmu | Pre koho | Cena (P) | Od kedy |
|--------------|----------|----------|---------|
| **Firemná licencia LECH OS** – aktualizácie so SLA, správa zariadení, podpora | Firmy, inštitúcie | 6–12 € / zariadenie / mesiac | Pilot |
| **LECH Cloud predplatné** – záloha, synchronizácia, AI servery v EÚ | Jednotlivci | 3–6 € / mesiac | Etapa 9 |
| **Firemná AI a cloud u zákazníka** – inštalácia, prevádzka | Firmy, inštitúcie | Projektovo, 5 000–50 000 € | Po pilote |
| **Telefóny s predinštalovaným LECH OS** (fáza A+) | Všetci | Marža 50–150 € / kus | Po pilote |
| **Komunitná verzia** | Jednotlivci | Zadarmo | Etapa 8 |

Princíp: **platí sa za služby, nie údajmi**. Predaj údajov a reklama sú vylúčené aj v obchodných podmienkach.

## 6. Konkurencia

| Konkurent | Model | Silné stránky | Naše odlíšenie |
|-----------|-------|---------------|----------------|
| **GrapheneOS** (Kanada) | Nezisková organizácia, zadarmo | Špičková bezpečnosť, Pixel, od 2027 Motorola | Integrovaná AI, jazyky EÚ, podpora a SLA pre firmy, infraštruktúra v EÚ |
| **/e/OS (Murena, Francúzsko)** | Predaj telefónov + cloud | Hotový obchodný model v EÚ, široká podpora zariadení | Overená bezpečnosť (zamknutý bootloader), meraná transparentnosť, AI |
| **Volla (Nemecko), iodéOS (Francúzsko)** | Telefóny / systém bez Google | Európsky pôvod, existujúci zákazníci | AI, overená bezpečnosť, ponuka pre firmy – ich aktuálnu ponuku treba overiť |
| **CalyxOS, iodéOS, LineageOS** | Komunitné / malé projekty | Komunita | Produkt so zmluvnou podporou a pilotmi pre firmy |
| **Samsung Knox, Android Enterprise** | Firemná správa nad Google ekosystémom | Rozšírenosť, nástroje | Nezávislosť od Google, kontrola údajov, otvorený kód |
| **Apple iOS** | Prémiový ekosystém | Bezpečnosť, jednoduchosť | Otvorenosť, suverenita, cena, lokálna AI v jazykoch EÚ |

## 7. Uvedenie na trh

1. **Dôveryhodnosť cez merania:** zverejňované audity sieťovej prevádzky (porovnanie bežného Androidu a LECH OS)
   – najsilnejší marketingový argument, ktorý konkurencia v takejto forme neponúka.
2. **Pilot s 1–3 organizáciami** (etapa 8) na Slovensku a v Česku: 20–100 zariadení, zmluva o pilote, merané výsledky.
3. **Partneri v každej krajine:** IT integrátori a správcovia firemných sietí ako predajný kanál – pre vstup do krajín
   vlny 2 a 3 kľúčoví; neskôr výrobca telefónov (A+).
4. **Komunitná verzia:** bezplatná, otvorený kód – povedomie, spätná väzba, vývojári.
5. **Granty a výzvy EÚ** na súkromie, AI a digitálnu suverenitu ako zdroj financovania aj referencií.

## 8. Plán a míľniky

Vychádza z etáp a brán kvality ([docs/08](08-plan-etap.md), [ADR-0008](adr/0008-postupny-vyvoj-a-brany.md)).

| Míľnik | Obsah | Dôkaz pre investora |
|--------|-------|---------------------|
| ✅ Etapa 1 | Architektúra, rozhodnutia | Repozitár, 8 ADR |
| ✅ E2-02, E2-03 | Čistý Android na vlastnom počítači, audit | Zmeraných 7 spojení na Google |
| Brána 1 | LECH OS na Pixeli, vlastné kľúče, aktualizácie | Video, audit „0 neznámych spojení“ |
| Brána 3 | LECH Privacy Control | Porovnanie s meraním mimo telefónu |
| Brána 4 | Lokálna AI v slovenčine | Ukážka 7 príkazov bez internetu |
| Etapa 8 | Pilot + nezávislý bezpečnostný audit | Referencie, správa z auditu |
| Etapa 9 | Komerčné uvedenie | Prví platiaci zákazníci |

## 9. Tím

| Fáza | Tím | Poznámka |
|------|-----|----------|
| **Teraz** | Zakladateľ + vývoj s pomocou AI | Overenie základu (brána 1) s minimálnymi nákladmi |
| **Prototyp a pilot** | 8–10 ľudí ([docs/08](08-plan-etap.md#tím-orientačne)) | Platforma AOSP, jadro/zariadenia, aplikácie, AI, backend, bezpečnosť, produkt |
| **Komerčná fáza** | + podpora, predaj, právo | Podľa počtu zákazníkov |

## 10. Náklady a financovanie (P)

### Náklady – predpoklady

| Položka | Predpoklad (P) |
|---------|----------------|
| Priemerné celkové mesačné náklady na jedného člena tímu (SK) | 5 000–7 000 € |
| Tím do pilotu | 8 ľudí počas 18–24 mesiacov |
| Infraštruktúra (zostavovacie servery, cloud v EÚ) | 1 000–3 000 € / mesiac |
| Nezávislé bezpečnostné audity | 2 × 30 000–80 000 € |
| Právne služby (ochranná známka, zmluvy, GDPR, CRA) | 20 000–50 000 € |
| Testovacie zariadenia | 5 000–10 000 € |
| Lokalizácia a jazyky AI pre vlnu 2 (DE, FR, PL) – preklady, testovacie sady, doladenie modelov | 30 000–80 000 € |
| Právne služby pre vstup do krajín (ochrana spotrebiteľa, zmluvy, DPH) | 5 000–15 000 € na krajinu |

### Výpočet

- Tím: 8 × 6 000 € × 21 mesiacov ≈ **1 000 000 €**
- Infraštruktúra: 2 000 € × 21 ≈ **42 000 €**
- Audity, právo, zariadenia ≈ **100 000–200 000 €**
- **Spolu do pilotu (vlna 1): približne 1,1–1,3 mil. €** (P)
- **Vstup do vlny 2:** lokalizácia, partneri a predaj v 3–5 krajinách – ďalšie odhadom 0,5–1 mil. € (P)

### Fázy financovania

| Fáza | Zdroj | Účel |
|------|-------|------|
| **0 – teraz** | Vlastné zdroje | Brána 1 (vlastný LECH OS na Pixeli) – minimálne náklady |
| **1 – prototyp** | Granty EÚ a národné výzvy + prípadne angel investor | Tím, brány 2–4 |
| **2 – pilot a uvedenie** | Investor (seed) po dosiahnutí brány 4 | Pilot, audity, predaj |

Programy na preverenie: EIC Accelerator, Digital Europe Programme, Horizon Europe, výzvy na otvorený softvér
a súkromie (napr. NGI / NLnet), národné výzvy na inovácie v krajinách vĺn 1 a 2. Pri projektoch EÚ pomáhajú
partneri z viacerých krajín – zahraniční pilotní zákazníci alebo integrátori sú výhodou aj pre granty.
**Aktuálne výzvy a podmienky treba overiť.**

## 11. Riziká

Hlavné riziká a ich zmiernenie sú v [docs/10](10-rizika-a-otvorene-otazky.md). Pre podnikateľský plán sú kľúčové:

1. **Bankové aplikácie** – riešené rokovaniami s bankami a prvým trhom vo firmách ([docs/13](13-bankove-aplikacie.md)).
2. **Náklady na údržbu** – aktualizácie sú trvalý náklad; plánované od začiatku ([docs/14](14-aktualizacie-a-podpora.md)).
3. **Konkurencia** – odlíšenie cez AI v jazykoch EÚ, podporu pre firmy a merania.
4. **Financovanie a tím** – postup po bránach, každá brána je samostatný dôkaz pre ďalšie kolo.
5. **Rozsah EÚ** – každá krajina pridáva jazyk, banky a obľúbené aplikácie; preto uvádzanie po vlnách.

## Čo treba overiť

- [ ] Veľkosť trhu: počet firemných mobilných zariadení v EÚ a v krajinách vĺn 1 a 2 v cieľových segmentoch (Eurostat,
      národné štatistické úrady).
- [ ] Výber krajín vlny 2: rozhovory s firmami a integrátormi v Nemecku, Rakúsku a Benelux.
- [ ] Ochota platiť a cena: rozhovory s 10–20 potenciálnymi pilotnými organizáciami.
- [ ] Náklady na tím: aktuálne mzdy špecialistov (AOSP, jadro, AI) v SK a v zahraničí na diaľku.
- [ ] Ceny auditov: ponuky od 2–3 nezávislých bezpečnostných firiem.
- [ ] Granty: aktuálne výzvy, termíny, podmienky spolufinancovania.
- [ ] Ochranná známka a názov pred komerčným uvedením ([docs/09](09-pravo-a-licencie.md#ochranné-známky)).
- [ ] Konkurencia: ceny a ponuka /e/OS (Murena), Volla, iodéOS, Samsung Knox a ďalších pre firmy v EÚ.
