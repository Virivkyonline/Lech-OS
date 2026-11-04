# LECH OS

> **Telefón, ktorý patrí používateľovi.** Operačný systém novej generácie postavený na AOSP: používateľ má kontrolu
> nad svojimi údajmi, umelá inteligencia je súčasťou samotného systému a funguje primárne priamo v telefóne,
> bez závislosti od Google, Apple aj od jedného poskytovateľa AI.
>
> *LECH OS je pracovný názov – ochrannú známku a dostupnosť názvu preveríme pred komerčným uvedením.*

**Stav projektu:** Etapa 1 (architektúra a návrh) je uzavretá – kľúčové rozhodnutia sú prijaté (ADR 0001–0008).
Začína **etapa 2: vlastná zostava AOSP na virtuálnom zariadení Cuttlefish** – postup je v
[docs/11-etapa-2-cuttlefish.md](docs/11-etapa-2-cuttlefish.md).
Vývoj ide **postupne, jedna vec naraz**, s bránami kvality medzi etapami ([ADR-0008](docs/adr/0008-postupny-vyvoj-a-brany.md)).
Prvá brána – **Overený základ** – preverí zostavu na Pixeli, bezpečnosť sieťovej prevádzky a celý cyklus aktualizácií.

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

## Časti systému

| Komponent | Funkcia | Dokument |
|-----------|---------|----------|
| **LECH OS Core** | Základ operačného systému (fork AOSP) | [Architektúra](docs/02-architektura.md) |
| **LECH AI Assistant** | Systémový asistent, nie chatbot: rozumie slovenčine, ovláda telefón a aplikácie, pracuje s dokumentmi, vybavuje úlohy, funguje bez internetu a hlasom | [LECH AI](docs/04-lech-ai.md) |
| **LECH Privacy Control** | Jeden prehľad: mikrofón, kamera, poloha, kam a koľko údajov posiela každá aplikácia; internet zakázaný jedným dotykom | [Privacy Control](docs/12-lech-privacy-control.md) |
| **LECH Security** | Overený boot, vlastné kľúče, spevnenie systému, kontrola aplikácií, nezávislé audity | [LECH Security](docs/05-lech-security.md) |
| **LECH Identity** | Vlastná identita bez povinného konta Google; synchronizácia a záloha dobrovoľné | [LECH Identity](docs/06-lech-store-a-cloud.md#lech-identity) |
| **LECH Store** | Vlastný obchod s aplikáciami so štítkami súkromia | [Store a Cloud](docs/06-lech-store-a-cloud.md) |
| **LECH Cloud** | Voliteľné cloudové služby v EÚ, aj na vlastnom serveri | [Store a Cloud](docs/06-lech-store-a-cloud.md#lech-cloud) |

## Dokumentácia

| #  | Dokument | Obsah |
|----|----------|-------|
| 01 | [Vízia a princípy](docs/01-vizia-a-principy.md) | Poslanie, produktová rada, overiteľné princípy, čo LECH OS nie je, postavenie na trhu |
| 02 | [Architektúra](docs/02-architektura.md) | Vrstvy systému, Device Support Package, služby, aktualizácie, repozitáre |
| 03 | [Nezávislosť od Google](docs/03-nezavislost-od-google.md) | Náhrady služieb Google, kompatibilita aplikácií, Play Integrity |
| 04 | [LECH AI](docs/04-lech-ai.md) | Schopnosti asistenta, architektúra, súhlasy, ovládanie aplikácií, dokumenty, lokálne modely |
| 05 | [LECH Security](docs/05-lech-security.md) | Model hrozieb, vrstvy ochrany, kľúče, bezpečnostné procesy |
| 06 | [LECH Store, Cloud a Identity](docs/06-lech-store-a-cloud.md) | Obchod s aplikáciami, voliteľné cloudové služby, identita bez povinného konta |
| 07 | [Testovacie zariadenia](docs/07-zariadenia.md) | Porovnanie Pixel / Fairphone / Nothing a odporúčanie |
| 08 | [Plán etáp](docs/08-plan-etap.md) | 9 etáp postupne, brány kvality, čo beží popri vývoji |
| 09 | [Právo, regulácia a licencie](docs/09-pravo-a-licencie.md) | Licencie AOSP/Linux, ochranné známky, GDPR, CRA, AI Act |
| 10 | [Riziká a otvorené otázky](docs/10-rizika-a-otvorene-otazky.md) | Hlavné riziká, zmiernenie, prijaté a otvorené rozhodnutia |
| 11 | [Etapa 2: štart na Cuttlefish](docs/11-etapa-2-cuttlefish.md) | Zostavovací počítač (aj notebook), hotové obrazy AOSP, inštalácia Cuttlefish, zostavenie, sieťový audit, úlohy etapy 2 |
| 12 | [LECH Privacy Control](docs/12-lech-privacy-control.md) | Prehľad súkromia, technické sledovanie prevádzky po aplikáciách, nezávislé overenie |
| 13 | [Bankové aplikácie](docs/13-bankove-aplikacie.md) | Cieľové banky SK, testovací protokol, hardvérová atestácia, postup rokovaní |
| 14 | [Aktualizácie a dlhodobá podpora](docs/14-aktualizacie-a-podpora.md) | Záväzky, mesačný proces, tím a náklady, povinnosti EÚ, overenie v etape 2 |
| –  | [Katalóg AI akcií](spec/ai-akcie.yaml) | Strojovo čitateľný zoznam akcií LECH AI s úrovňami rizika a súhlasu |
| –  | [Validátor katalógu](tools/validate_ai_actions.py) | Kontrola, že žiadna akcia neobchádza pravidlá súhlasu: `python3 tools/validate_ai_actions.py` |
| –  | [Kontrola zostavovacieho počítača](tools/check_build_host.sh) | Overí RAM, disk, KVM a nástroje pred etapou 2: `tools/check_build_host.sh` |
| –  | [Skripty etapy 2](tools/etapa2) | Príprava notebooku, čisté AOSP z hotových obrazov, sieťový audit prvého spustenia, stiahnutie a zostavenie AOSP – [rýchly štart](docs/11-etapa-2-cuttlefish.md#rýchly-štart-na-notebooku) |
| –  | [Povolené spojenia](spec/povolene-spojenia.txt) | Zoznam spojení, ktoré LECH OS smie nadviazať; audit všetko ostatné označí ako neznáme |

## Kľúčové rozhodnutia (ADR)

Zásadné rozhodnutia zapisujeme ako *Architecture Decision Records* do [docs/adr](docs/adr/README.md).

| ADR | Rozhodnutie | Stav |
|-----|-------------|------|
| [0001](docs/adr/0001-obchodna-cesta.md) | Obchodná cesta: najprv **A** (vlastný OS na podporovaných telefónoch), architektúra pripravená na **B** (vlastný telefón) | Prijaté |
| [0002](docs/adr/0002-zaklad-systemu-aosp.md) | Základ systému: čisté AOSP bez GMS, vlastné podpisové kľúče, preberanie overených open-source komponentov | Prijaté |
| [0003](docs/adr/0003-kompatibilita-google-sluzieb.md) | Google služby: v systéme žiadne; voliteľne „Google Play v sandboxe“ ako bežná aplikácia bez privilégií | Prijaté |
| [0004](docs/adr/0004-nezavislost-od-ai-poskytovatela.md) | AI: lokálne modely ako predvolené, poskytovatelia zameniteľní cez jedno rozhranie | Prijaté |
| [0005](docs/adr/0005-vyvojove-zariadenie.md) | Vývoj: virtuálne zariadenie Cuttlefish + Google Pixel; Fairphone ako druhá platforma | Prijaté |
| [0006](docs/adr/0006-licencia-otvoreny-kod.md) | Otvorený kód: Apache 2.0 pre systém, AGPL v3 pre servery, značka chránená | Prijaté |
| [0007](docs/adr/0007-prvy-trh-firmy.md) | Prvý trh: pilot pre firmy a organizácie + komunitná verzia pre jednotlivcov | Prijaté |
| [0008](docs/adr/0008-postupny-vyvoj-a-brany.md) | Postupný vývoj, jedna vec naraz, brány kvality medzi etapami | Prijaté |

## Prijaté rozhodnutia v skratke

- **Obchodná cesta:** začať cestou **A**, s medzikrokom **A+** (predaj telefónov s predinštalovaným LECH OS
  od partnerského výrobcu). Vlastný hardvér (**B**) až po overení záujmu trhu. Architektúra to umožní vďaka
  oddeleniu všetkého, čo je špecifické pre zariadenie, do *Device Support Package*.
- **Vývojové zariadenia:** najprv virtuálne zariadenie **Cuttlefish**, potom Google Pixel (odomknuteľný a znovu
  zamknuteľný bootloader s vlastnými kľúčmi, hardvérový bezpečnostný čip, 7 rokov aktualizácií od Pixel 8).
- **Otvorený kód:** systém a aplikácie pod Apache 2.0, serverové služby pod AGPL v3, názov a logo chránené ochrannou známkou.
- **Prvý trh:** pilot pre 1–3 firmy alebo organizácie (20–100 zariadení) a paralelne bezplatná komunitná verzia.
- **Postup:** virtuálny telefón → Pixel → grafické prostredie → bezpečnosť → lokálna AI → obchod a testovanie aplikácií.
  Prvá brána overí základ, bezpečnosť sieťovej prevádzky a aktualizácie skôr, ako pridáme ďalšie funkcie.
- **Kompatibilita aplikácií:** reálne dosiahnuteľná pre veľkú väčšinu aplikácií. Najväčšie riziko sú bankové
  aplikácie a platby cez NFC, ktoré vyžadujú certifikáciu Google (Play Integrity). Toto riziko je pomenované
  a má vlastný plán – pozri [docs/13](docs/13-bankove-aplikacie.md).
- **AI:** „Model navrhuje, pravidlá rozhodujú, používateľ potvrdzuje.“ Bezpečnostné pravidlá nie sú
  súčasťou AI modelu, takže ich model nemôže obísť.

## Pracovný postup

- Návrhy a zmeny architektúry prechádzajú cez pull request.
- Každé zásadné rozhodnutie dostane nový ADR (šablóna v [docs/adr/README.md](docs/adr/README.md)).
- Tvrdenia o externých faktoch (stav AOSP, legislatíva, podpora zariadení) majú v dokumentoch uvedený zdroj
  a dátum overenia, pretože sa rýchlo menia.
- Podrobnosti pre prispievateľov sú v [CONTRIBUTING.md](CONTRIBUTING.md).

## Licencia

Obsah tohto repozitára je pod licenciou [Apache 2.0](LICENSE). Licencie ostatných častí projektu určuje
[ADR-0006](docs/adr/0006-licencia-otvoreny-kod.md). Názov a logo LECH nie sú súčasťou licencie.
