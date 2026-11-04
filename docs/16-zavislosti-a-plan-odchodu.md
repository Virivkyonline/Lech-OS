# 16 – Závislosti a plán odchodu

Princíp P9 ([docs/01](01-vizia-a-principy.md)): **žiadna časť systému nesmie mať jediného nenahraditeľného dodávateľa.**
Tento dokument je register závislostí – pre každú uvádza, čo by sa stalo, keby dodávateľ prestal spolupracovať
(alebo by sa správal nepriateľsky ako konkurent), a aký je plán odchodu. Aktualizuje sa pri každom ADR.

> Stav k 4. 11. 2026. Nejde o právne poradenstvo – právne otázky posúdi advokát ([docs/09](09-pravo-a-licencie.md)).

## Čo sa vziať nedá

- **Zverejnený kód AOSP.** Licencia Apache 2.0 dáva trvalé a neodvolateľné právo kód používať, upravovať a šíriť,
  aj komerčne. Patentová licencia od prispievateľov zaniká len vtedy, ak by sme ich žalovali pre porušenie patentu.
- **Prevádzka telefónov u zákazníkov.** LECH OS nemá mať žiadne spojenie na Google (cieľ úlohy E2-06, overuje audit
  [audit/README.md](../audit/README.md)). V systéme nie je žiadny „vypínač“ tretej strany.
- **Ochrana práva EÚ.** Európska komisia v roku 2018 pokutovala Google sumou 4,34 mld. € okrem iného za bránenie
  výrobcom používať upravené verzie Androidu; Všeobecný súd EÚ rozhodnutie v roku 2022 v podstate potvrdil.
  Google je podľa Aktu o digitálnych trhoch (DMA) strážcom brány pre Android.

## Register závislostí

| # | Závislosť | Dodávateľ | Riziko | Plán odchodu | Stav |
|---|-----------|-----------|--------|--------------|------|
| Z1 | Nové verzie a bezpečnostné záplaty AOSP | Google | Spomalenie alebo zastavenie zverejňovania (od 2026 len 2× ročne, bez device trees Pixel) | Vlastné zrkadlo AOSP; malý patch set ([ADR-0002](adr/0002-zaklad-systemu-aosp.md)); spolupráca s inými nezávislými systémami; v krajnom prípade vlastný bezpečnostný tím a vývoj | Zrkadlo – plánované |
| Z2 | Hosting zdrojových kódov (android.googlesource.com) | Google | Obmedzenie prístupu (už teraz kvóty) | Vlastné zrkadlo všetkých projektov, ktoré používame | Plánované v E2-04 |
| Z3 | Firmvér a ovládače Pixel | Google | Koniec vydávania, sťaženie vlastných systémov | Druhá platforma (Fairphone, [ADR-0005](adr/0005-vyvojove-zariadenie.md)), partnerský výrobca (A+), vlastný telefón (B, [ADR-0001](adr/0001-obchodna-cesta.md)) | Druhá platforma v etape 7 |
| Z4 | Koreň hardvérovej atestácie (overenie zariadenia pre banky) | Google | Odvolanie alebo zmena pravidiel atestácie | Pri vlastnom telefóne vlastné kľúče výrobcu; zverejnené kľúče overeného bootu ([docs/13](13-bankove-aplikacie.md)) | Otvorené |
| Z5 | Google Play Services pre aplikácie (voliteľný sandbox) | Google | Blokovanie na necertifikovaných zariadeniach | Predvolený režim bez Google, microG ako alternatíva, UnifiedPush, LECH Store, argumenty podľa DMA ([ADR-0003](adr/0003-kompatibilita-google-sluzieb.md)) | Návrh |
| Z6 | Vývojárske nástroje: Cuttlefish, `repo`, hotové obrazy z CI | Google | Ukončenie alebo zmena podmienok | Nástroje sa dajú zostaviť zo zdrojových kódov (Apache 2.0); vlastné kópie a vlastné zostavy | Kópie – plánované |
| Z7 | Ochranná známka „Android“ | Google | Spor o značku | Názov Android v produkte nepoužívame ([docs/09](09-pravo-a-licencie.md#ochranné-známky)) | Dodržiavané |
| Z8 | Patenty | Rôzni | Patentové nároky | Patentová licencia Apache 2.0; zvážiť členstvo v Open Invention Network (bezplatné krížové licencie pre Linux a Android) | Na preverenie |
| Z9 | AI modely | Poskytovatelia open-weight modelov | Zmena licencie novších verzií | Viac modelov v hodnotiacej sade, uložené verzie s povolenou licenciou ([ADR-0004](adr/0004-nezavislost-od-ai-poskytovatela.md)) | Návrh |
| Z10 | Cloudová infraštruktúra | Poskytovateľ v EÚ | Zmena podmienok, výpadok | Infraštruktúra ako kód, prenositeľná medzi poskytovateľmi ([docs/06](06-lech-store-a-cloud.md)) | Návrh |
| Z11 | GitHub (repozitár projektu) | Microsoft | Zablokovanie účtu | Pravidelná záloha repozitára mimo GitHubu; projekt je čistý git | Na zavedenie |

## Najbližšie kroky

1. **Zrkadlo AOSP** (Z1, Z2): zálohovať stiahnuté zdrojové kódy a pri E2-04 zaviesť vlastné zrkadlo projektov.
2. **Kópie nástrojov** (Z6): uložiť verzie Cuttlefish a `repo`, ktoré používame; overiť zostavenie Cuttlefish zo zdrojového kódu.
3. **Záloha repozitára** (Z11): pravidelná kópia na druhé miesto.
4. **Open Invention Network** (Z8): preveriť podmienky členstva.
