# LECH OS

> **Pracovný názov.** Mobilný operačný systém postavený na AOSP, ktorý dáva súkromie používateľa na prvé miesto,
> má zabudovanú umelú inteligenciu fungujúcu primárne priamo v telefóne a nie je závislý od Google, Apple ani
> od jedného poskytovateľa AI.

**Stav projektu:** Etapa 1 – architektúra a návrh systému. Repozitár zatiaľ obsahuje len dokumentáciu
a návrhové rozhodnutia. Kód pribudne od etapy 2 (vlastná zostava AOSP).

---

## Hlavné princípy

1. Súkromie používateľa na prvom mieste.
2. Žiadne povinné konto Google (ani žiadne iné povinné konto).
3. Žiadne odosielanie osobných údajov na reklamné účely.
4. Žiadne sledovanie používateľa na marketingové účely.
5. Používateľ rozhoduje, aké údaje opustia jeho telefón.
6. Umelá inteligencia integrovaná priamo do systému.
7. Kompatibilita s Android aplikáciami.
8. Dlhodobá podpora a vlastné aktualizácie.
9. Nezávislosť od Google, Apple aj od jedného poskytovateľa AI.

Každý princíp je v [docs/01-vizia-a-principy.md](docs/01-vizia-a-principy.md) prepísaný na **overiteľnú požiadavku** –
aby sa dalo kedykoľvek zmerať, či ho systém naozaj spĺňa.

## Päť častí systému

| Komponent         | Funkcia                                   | Dokument |
|-------------------|-------------------------------------------|----------|
| **LECH OS Core**  | Základ operačného systému (fork AOSP)     | [Architektúra](docs/02-architektura.md) |
| **LECH AI**       | Inteligentný asistent integrovaný do systému | [LECH AI](docs/04-lech-ai.md) |
| **LECH Store**    | Vlastný obchod s aplikáciami              | [Store a Cloud](docs/06-lech-store-a-cloud.md) |
| **LECH Cloud**    | Voliteľné cloudové služby                 | [Store a Cloud](docs/06-lech-store-a-cloud.md) |
| **LECH Security** | Ochrana súkromia a bezpečnosť             | [LECH Security](docs/05-lech-security.md) |

## Dokumentácia

| #  | Dokument | Obsah |
|----|----------|-------|
| 01 | [Vízia a princípy](docs/01-vizia-a-principy.md) | Poslanie, overiteľné princípy, čo LECH OS nie je, postavenie na trhu |
| 02 | [Architektúra](docs/02-architektura.md) | Vrstvy systému, Device Support Package, služby, aktualizácie, repozitáre |
| 03 | [Nezávislosť od Google](docs/03-nezavislost-od-google.md) | Náhrady služieb Google, kompatibilita aplikácií, Play Integrity |
| 04 | [LECH AI](docs/04-lech-ai.md) | Architektúra asistenta, súhlasy, lokálne modely, nezávislosť od poskytovateľa |
| 05 | [LECH Security](docs/05-lech-security.md) | Model hrozieb, vrstvy ochrany, kľúče, bezpečnostné procesy |
| 06 | [LECH Store a LECH Cloud](docs/06-lech-store-a-cloud.md) | Obchod s aplikáciami a voliteľné cloudové služby |
| 07 | [Testovacie zariadenia](docs/07-zariadenia.md) | Porovnanie Pixel / Fairphone / Nothing a odporúčanie |
| 08 | [Plán etáp](docs/08-plan-etap.md) | 9 etáp, výstupy, kritériá dokončenia, paralelné pracovné prúdy |
| 09 | [Právo, regulácia a licencie](docs/09-pravo-a-licencie.md) | Licencie AOSP/Linux, ochranné známky, GDPR, CRA, AI Act |
| 10 | [Riziká a otvorené otázky](docs/10-rizika-a-otvorene-otazky.md) | Hlavné riziká, zmiernenie, rozhodnutia, ktoré čakajú na vlastníka |
| –  | [Katalóg AI akcií](spec/ai-akcie.yaml) | Strojovo čitateľný zoznam akcií LECH AI s úrovňami rizika a súhlasu |
| –  | [Validátor katalógu](tools/validate_ai_actions.py) | Kontrola, že žiadna akcia neobchádza pravidlá súhlasu: `python3 tools/validate_ai_actions.py` |

## Kľúčové rozhodnutia (ADR)

Zásadné rozhodnutia zapisujeme ako *Architecture Decision Records* do [docs/adr](docs/adr/README.md).

| ADR | Rozhodnutie | Stav |
|-----|-------------|------|
| [0001](docs/adr/0001-obchodna-cesta.md) | Obchodná cesta: najprv **A** (vlastný OS na podporovaných telefónoch), architektúra pripravená na **B** (vlastný telefón) | Navrhnuté – čaká na potvrdenie |
| [0002](docs/adr/0002-zaklad-systemu-aosp.md) | Základ systému: čisté AOSP bez GMS, vlastné podpisové kľúče, preberanie overených open-source komponentov | Navrhnuté |
| [0003](docs/adr/0003-kompatibilita-google-sluzieb.md) | Google služby: v systéme žiadne; voliteľne „Google Play v sandboxe“ ako bežná aplikácia bez privilégií | Navrhnuté |
| [0004](docs/adr/0004-nezavislost-od-ai-poskytovatela.md) | AI: lokálne modely ako predvolené, poskytovatelia zameniteľní cez jedno rozhranie | Navrhnuté |
| [0005](docs/adr/0005-vyvojove-zariadenie.md) | Vývoj: virtuálne zariadenie Cuttlefish + Google Pixel; Fairphone ako druhá platforma | Navrhnuté |

## Zhrnutie odporúčaní v skratke

- **Obchodná cesta:** začať cestou **A**, s medzikrokom **A+** (predaj telefónov s predinštalovaným LECH OS
  od partnerského výrobcu). Vlastný hardvér (**B**) až po overení záujmu trhu. Architektúra to umožní vďaka
  oddeleniu všetkého, čo je špecifické pre zariadenie, do *Device Support Package*.
- **Prvý telefón:** Google Pixel (odomknuteľný a znovu zamknuteľný bootloader s vlastnými kľúčmi, hardvérový
  bezpečnostný čip, 7 rokov aktualizácií od Pixel 8). Popri ňom virtuálne zariadenie Cuttlefish na rýchly vývoj.
- **Kompatibilita aplikácií:** reálne dosiahnuteľná pre veľkú väčšinu aplikácií. Najväčšie riziko sú bankové
  aplikácie a platby cez NFC, ktoré vyžadujú certifikáciu Google (Play Integrity). Toto riziko je pomenované
  a má vlastný plán – pozri [docs/03](docs/03-nezavislost-od-google.md#play-integrity--najväčšie-riziko-kompatibility).
- **AI:** „Model navrhuje, pravidlá rozhodujú, používateľ potvrdzuje.“ Bezpečnostné pravidlá nie sú
  súčasťou AI modelu, takže ich model nemôže obísť.

## Pracovný postup

- Návrhy a zmeny architektúry prechádzajú cez pull request.
- Každé zásadné rozhodnutie dostane nový ADR (šablóna v [docs/adr/README.md](docs/adr/README.md)).
- Tvrdenia o externých faktoch (stav AOSP, legislatíva, podpora zariadení) majú v dokumentoch uvedený zdroj
  a dátum overenia, pretože sa rýchlo menia.
