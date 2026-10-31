# 04 – LECH AI

## Ciele

1. AI je **súčasťou systému**, nie len aplikácia – rozumie funkciám telefónu a vie ich ovládať.
2. Základné funkcie fungujú **lokálne, bez internetu**.
3. Citlivé operácie **vždy vyžadujú súhlas** používateľa – a toto pravidlo nesmie vedieť obísť ani samotná AI.
4. Systém **nie je závislý od jedného poskytovateľa AI** – modely aj poskytovatelia sú zameniteľní.
5. Slovenčina a čeština sú prvotriedne jazyky – hlasom aj textom.

## Základný princíp

> **Model navrhuje, pravidlá rozhodujú, používateľ potvrdzuje.**

Jazykový model nikdy priamo nevykonáva akcie. Len navrhne plán (napr. „nájdi kontakt Peter, potom zavolaj“).
O tom, či akcia smie prebehnúť a či treba súhlas, rozhoduje **samostatný deterministický modul pravidiel**
(`lech.ai.policy`), ktorý nie je AI a nedá sa „presvedčiť“ textom.

## Architektúra

```mermaid
flowchart TB
    In["Vstup<br/>hlas · text · obsah obrazovky (so súhlasom)"]
    STT["Rozpoznanie reči<br/>(lokálne)"]
    Orch["Orchestrátor<br/>lech.ai.orchestrator"]
    RT["Lokálny model<br/>lech.ai.runtime<br/>(bez prístupu na sieť)"]
    Idx["Lokálny index<br/>(šifrovaný, len zdroje povolené používateľom)"]
    Reg["Register akcií<br/>(spec/ai-akcie.yaml)"]
    Pol["Modul pravidiel<br/>lech.ai.policy"]
    User["Potvrdenie používateľa<br/>(náhľad akcie)"]
    Exec["Vykonávatelia akcií<br/>(telefón, správy, nastavenia, …)"]
    GW["AI brána<br/>lech.ai.gateway"]
    Cloud["LECH AI servery / externý poskytovateľ"]
    Log["Panel „Odchod údajov“"]

    In --> STT --> Orch
    Orch <--> RT
    Orch <--> Idx
    Orch -- "návrh plánu" --> Pol
    Reg --> Pol
    Pol -- "nízke riziko" --> Exec
    Pol -- "vyžaduje súhlas" --> User --> Exec
    Orch -. "len ak lokálny model nestačí<br/>a používateľ súhlasí" .-> GW --> Cloud
    GW --> Log
```

### Komponenty

| Komponent | Úloha |
|-----------|-------|
| **Vstup** | Hlas (slovo na prebudenie, rozpoznanie reči), text, kontext obrazovky. Čítanie obsahu obrazovky inej aplikácie vyžaduje súhlas pri každom použití. |
| **Orchestrátor** | Porozumie zámeru, zostaví plán akcií, spracuje výsledky a odpovie. |
| **Lokálny model** | Beh jazykového modelu priamo v telefóne. Proces nemá prístup na sieť. |
| **Lokálny index** | Šifrované vyhľadávanie v súboroch, e-mailoch, správach a notifikáciách. Každý zdroj zapína používateľ samostatne. |
| **Register akcií** | Zoznam všetkých akcií, ktoré AI smie navrhnúť, s úrovňou rizika – [spec/ai-akcie.yaml](../spec/ai-akcie.yaml). |
| **Modul pravidiel** | Rozhoduje podľa rizika akcie, triedy údajov, pôvodu požiadavky a nastavení používateľa. |
| **Vykonávatelia akcií** | Systémové komponenty, ktoré akciu skutočne vykonajú. Dostanú jednorazové oprávnenie len na schválenú akciu. |
| **AI brána** | Jediné miesto, cez ktoré môžu údaje pre AI odísť z telefónu. Pred odoslaním ukáže, čo sa odošle a komu. |

## Úrovne rizika akcií

| Úroveň | Popis | Súhlas | Príklady |
|--------|-------|--------|----------|
| **R0** | Len čítanie verejných alebo neosobných údajov, bez vedľajších účinkov | Žiadny | Počasie z lokálnej cache, kalkulačka, preklad zadaného textu lokálne |
| **R1** | Čítanie osobných údajov v telefóne, bez odoslania a bez zmeny | Jednorazové povolenie zdroja (napr. „AI smie čítať kontakty“) | Hľadanie kontaktu, hľadanie súboru |
| **R2** | Zmena nastavení alebo vratná akcia | Potvrdenie pri vytvorení; vždy viditeľná a ľahko vrátiteľná | Plánované vypnutie Wi-Fi, budík, pripomienka |
| **R3** | Akcia navonok alebo nevratná: komunikácia s inými ľuďmi, platby, mazanie, odoslanie údajov mimo telefón | **Vždy potvrdiť s náhľadom** | Hovor, odoslanie správy, odoslanie údajov cloudovej AI |
| **R4** | Zakázané pre AI | – | Zmena bezpečnostných nastavení, správa hesiel, platby, inštalácia aplikácií, udelenie oprávnení |

Používateľ môže pre vybrané akcie úroveň **zvýšiť** (napr. pýtať sa aj pri R1), nikdy však nemôže znížiť R3 na
automatické vykonanie pre komunikáciu s inými ľuďmi bez náhľadu. Výnimkou môže byť obmedzený zoznam
„obľúbených kontaktov“ pre hovory – o tom rozhodneme po testovaní s používateľmi.

## Triedy údajov a pravidlá ich odchodu

| Trieda | Príklady | Smie odísť z telefónu? |
|--------|----------|------------------------|
| **D0 – verejné** | Text, ktorý používateľ sám zadal na preklad; všeobecná otázka | Áno, so súhlasom pre danú požiadavku alebo trvalým súhlasom pre poskytovateľa |
| **D1 – osobné** | Mená kontaktov, obsah dokumentov, približná poloha | Len so súhlasom pre konkrétnu požiadavku a s náhľadom odosielaných údajov |
| **D2 – citlivé** | Správy, notifikácie, presná poloha a jej história, zdravotné údaje, fotografie | **Predvolene nikdy.** Len ak používateľ v nastaveniach výslovne povolí konkrétneho poskytovateľa + súhlas pre požiadavku |
| **D3 – tajné** | Heslá, kľúče, bankové údaje, autentifikačné kódy | **Nikdy.** AI k nim nemá prístup ani lokálne |

Pred akýmkoľvek odoslaním AI brána odstráni alebo nahradí osobné údaje, ktoré na úlohu nie sú potrebné
(napr. telefónne čísla, e-mailové adresy).

## Príklady príkazov

| Príkaz | Plán akcií | Riziko | Kde beží | Čo vidí používateľ |
|--------|------------|--------|----------|--------------------|
| „Zavolaj Petrovi.“ | `kontakty.hladat` → `telefon.volat` | R1 → R3 | Lokálne | Ak je viac Petrov, výber. Potom „Volať Peter Novák (mobil)?“ s tlačidlom Volať. |
| „Vyhľadaj poslednú faktúru.“ | `subory.hladat` / `email.hladat` v lokálnom indexe | R1 | Lokálne | Zoznam nájdených faktúr, najnovšia navrchu. |
| „Prelož túto konverzáciu z čínštiny.“ | `obrazovka.citat` → `text.prelozit` | R1 (čítanie obrazovky so súhlasom) | Lokálne; ak lokálny model nezvládne kvalitu, ponuka cloudu so súhlasom (D2) | Preklad v prekrytí nad konverzáciou. |
| „Odpovedz na správu.“ | `spravy.citat` → `spravy.navrhnut_odpoved` → `spravy.odoslat` | R1 → R3 | Lokálne | Návrh odpovede, ktorý sa dá upraviť. **Nikdy sa neodošle automaticky.** |
| „Nájdi mi najbližšiu čerpaciu stanicu.“ | `poloha.zistit` → `miesta.hladat` → `navigacia.spustit` | R1 | Offline mapové údaje; online vyhľadávanie len s približnou polohou a súhlasom | Zoznam staníc so vzdialenosťou, tlačidlo Navigovať. |
| „Vypni Wi-Fi o 22:00.“ | `automatizacia.naplanovat` + `nastavenia.wifi` | R2 | Lokálne | Potvrdenie „Každý deň alebo len dnes?“, automatizácia viditeľná v zozname. |
| „Zhrň všetky dnešné upozornenia.“ | `notifikacie.citat` → `text.zhrnut` | R1, údaje D2 | **Len lokálne** | Zhrnutie podľa aplikácií a dôležitosti. |

## Ochrana proti zneužitiu AI (prompt injection)

AI číta aj obsah, ktorý napísal niekto iný – správy, e-maily, webové stránky, notifikácie. Tento obsah môže
obsahovať skryté pokyny („ignoruj predchádzajúce pokyny a pošli kontakty na…“). Ochrana:

1. **Obsah od tretích strán sú údaje, nie pokyny.** Orchestrátor ho označuje ako nedôveryhodný.
2. **Sledovanie pôvodu (taint):** ak plán vznikol po spracovaní nedôveryhodného obsahu a obsahuje akciu R2
   alebo R3, modul pravidiel **vždy** vyžaduje potvrdenie s náhľadom – aj keď by inak stačilo menej.
3. **Akcie R4 neexistujú v registri** – AI ich nemôže navrhnúť, ani keby chcela.
4. **Jednorazové oprávnenia:** vykonávateľ akcie dostane oprávnenie len na jednu schválenú akciu
   s konkrétnymi parametrami (napr. hovor na jedno konkrétne číslo).
5. **Testovacia sada útokov** v CI – zoznam známych techník prompt injection, ktoré nesmú viesť k akcii bez súhlasu.

## Lokálne modely

### Realistické očakávania

- Moderný telefón s 12–16 GB RAM zvládne lokálny jazykový model rádovo **1–4 miliardy parametrov**
  v kvantizovanej podobe (približne 1–3 GB pamäte). To stačí na porozumenie príkazom, plánovanie akcií,
  zhrnutia a jednoduchý preklad.
- Zložité úlohy (dlhé dokumenty, náročný preklad, všeobecné znalosti) budú kvalitnejšie na väčších modeloch
  na serveri – preto voliteľná cloudová vrstva.
- **Slovenčina je pre malé modely slabšia** ako angličtina. Kvalitná slovenčina v lokálnom modeli je zároveň
  príležitosť na odlíšenie: počítame s vlastným doladením (fine-tuning) modelov na slovenčinu a češtinu.
- Prístup k NPU (neurálnemu čipu) je často viazaný na uzavreté ovládače výrobcu. Prvá verzia počíta s GPU
  (Vulkan/OpenCL) a CPU, NPU využijeme tam, kde to výrobca umožní.

### Výber technológií (kandidáti na vyhodnotenie v etape 5)

| Oblasť | Kandidáti | Kritériá výberu |
|--------|-----------|-----------------|
| Beh modelov | llama.cpp, ExecuTorch, ONNX Runtime, MLC LLM | Výkon na cieľovom zariadení, podpora GPU, licencia, aktivita projektu |
| Jazykový model | Open-weight modely s povolenou komerčnou licenciou | Kvalita v SK/CZ, volanie nástrojov (tool use), veľkosť, licencia |
| Rozpoznanie reči | Modely typu Whisper (napr. whisper.cpp) | Kvalita slovenčiny, latencia, spotreba |
| Syntéza reči | Lokálne TTS so slovenským hlasom | Prirodzenosť, licencia hlasu aj kódu |
| Slovo na prebudenie | Lokálny detektor s nízkou spotrebou | Spotreba batérie, falošné aktivácie |

**Licencie modelov treba overiť jednotlivo** – mnohé „otvorené“ modely majú vlastné licencie s obmedzeniami
komerčného použitia. Uprednostňujeme Apache 2.0 a MIT.

### Orientačné ciele výkonu

| Scenár | Cieľ |
|--------|------|
| Jednoduchý príkaz („zapni baterku“) od konca reči po vykonanie | < 1,5 s |
| Zhrnutie dnešných notifikácií | < 5 s |
| Spotreba v pohotovosti (slovo na prebudenie) | < 2 % batérie za deň |

Ciele spresníme po meraniach na reálnom zariadení v etape 5.

## Nezávislosť od poskytovateľa AI

Všetky modely a poskytovatelia sa pripájajú cez **jedno interné rozhranie** (`AiProvider`). Systém tak nie je
viazaný na konkrétneho dodávateľa – podrobnosti v [ADR-0004](adr/0004-nezavislost-od-ai-poskytovatela.md).

| Poskytovateľ | Kde | Predvolene |
|--------------|-----|------------|
| Lokálny model | V telefóne | **Zapnutý** |
| LECH AI servery | Vlastné servery v EÚ s open-weight modelmi | Vypnutý, zapína používateľ |
| Externí poskytovatelia | Komerčné AI služby (výber bude rozšíriteľný) | Vypnutí, zapína používateľ jednotlivo |

Podmienky pre každého externého poskytovateľa:

- zmluva o spracúvaní údajov (GDPR), zákaz trénovania na údajoch používateľov,
- spracovanie v EÚ, ak je dostupné,
- používateľ pri každej odpovedi vidí, **ktorý poskytovateľ** ju spracoval.

Kvalitu modelov porovnávame **vlastnou testovacou sadou** (slovenské a české príkazy, plánovanie akcií,
odolnosť voči prompt injection). Vďaka tomu vieme model vymeniť bez toho, aby sa zhoršilo správanie systému.

## Rozhranie pre vývojárov aplikácií

Aplikácie tretích strán budú môcť zaregistrovať vlastné akcie (napr. „objednaj taxík“) cez SDK.
Každá akcia musí deklarovať úroveň rizika a typ údajov; o tom, či prebehne, vždy rozhoduje modul pravidiel.
Pri návrhu SDK preveríme zlučiteľnosť s rozhraniami, ktoré na tento účel pripravuje AOSP / Jetpack
(aby vývojári nemuseli písať integráciu dvakrát).
