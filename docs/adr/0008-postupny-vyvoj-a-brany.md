# ADR-0008: Postupný vývoj s bránami kvality

- **Stav:** Prijaté (2026-11-01) – na základe pokynu vlastníka projektu
- **Dátum:** 2026-11-01
- **Nahrádza:** paralelné pracovné prúdy v pôvodnej verzii [docs/08](../08-plan-etap.md)

## Kontext

Pôvodný plán počítal so súbežnými prúdmi (platforma, AI, ekosystém). Pri malom tíme to znamená rozbehnúť veľa vecí
naraz a žiadnu nedokončiť. Najväčšie riziko projektu je, že by sme o rok museli prerábať polovicu systému, pretože
základ (zostava, bezpečnosť, aktualizácie) nebol overený skôr, ako sme na ňom postavili ďalšie vrstvy.

Vlastník projektu určil poradie:

1. čistý LECH OS na virtuálnom telefóne,
2. LECH OS na reálnom Pixeli,
3. vlastné grafické prostredie,
4. bezpečnostný systém,
5. lokálna AI,
6. testovanie Android aplikácií.

## Rozhodnutie

1. **Vývoj ide postupne, vždy jedna hlavná vec naraz** v poradí podľa vlastníka. Obchod s aplikáciami (etapa 6)
   zaraďujeme pred testovanie aplikácií, pretože ho testy potrebujú.
2. **Medzi etapami sú brány kvality.** Ďalšia etapa začne, až keď predchádzajúca splní merateľné kritériá:

   | Brána | Po etape | Kritériá |
   |-------|----------|----------|
   | **1 – Overený základ** | 2 | LECH OS na Pixeli so zamknutým bootloaderom a vlastným kľúčom; 3 OTA aktualizácie vrátane automatického návratu po chybnej; generálka mesačnej aktualizácie so zmeraným oneskorením; 0 neznámych spojení pri prvom spustení a počas 24 h nečinnosti (overené meraním mimo telefónu) |
   | **2 – Použiteľný systém** | 3 | Testovací používatelia nastavia a používajú telefón bez pomoci |
   | **3 – Overená bezpečnosť** | 4 | LECH Privacy Control hotový; každé spojenie zachytené mimo telefónu je v telefóne priradené aplikácii alebo službe; interná bezpečnostná revízia bez otvorených kritických nálezov |
   | **4 – Overená AI** | 5 | 7 príkladových príkazov funguje bez internetu v slovenčine; žiadna akcia R3 neprebehne bez potvrdenia; testy prompt injection prejdú |
   | **5 – Pripravené na pilot** | 6 a 7 | Splnené ciele kompatibility; stav bankových aplikácií otestovaný a zverejnený ([docs/13](../13-bankove-aplikacie.md)) |

3. **Popri vývoji bežia len nevývojové úlohy** s dlhou dobou vybavenia, ktoré neberú kapacitu vývojárov:
   kontakty s bankami, hľadanie pilotných organizácií, financovanie, právne otázky.
4. **Automatické testy sa nepovažujú za samostatnú vec.** Sieťový audit, testy aktualizácií a základné kompatibilitné testy
   vznikajú ako súčasť etapy 2 a potom bežia pri každom zostavení.
5. **Jedna vývojová výnimka:** krátky test realizovateľnosti lokálnej AI v slovenčine
   (najviac 2 týždne, jedna osoba), hneď ako budeme mať Pixel. Test beží na bežnom Androide, nezasahuje do LECH OS.
   Dôvod: AI je najdôležitejšia časť projektu a zároveň najväčšia technická neznáma. Ak by malé lokálne modely
   slovenčinu nezvládali, musíme to vedieť pred etapou 5, nie v nej. **Výnimku potvrdil vlastník projektu 1. 11. 2026.**

## Dôsledky

- ✅ Každá vrstva stojí na overenom základe – menšie riziko prerábania.
- ✅ Jasné míľniky pre investorov a partnerov: každá brána je dôkaz pokroku.
- ✅ Prevádzková záťaž (aktualizácie) je zmeraná skôr, ako sa zaviažeme zákazníkom.
- ❌ Celkový čas do verejného prototypu je dlhší ako pri súbežnom vývoji.
- ❌ Problémy s kompatibilitou aplikácií sa naplno ukážu až v etape 7 – zmierňujú ich automatické testy od etapy 2
  a skoré rokovania s bankami.

## Kedy rozhodnutie prehodnotiť

- Ak sa tím rozrastie natoľko, že súbežná práca nebude znižovať kvalitu.
- Ak niektorá brána odhalí zásadný problém – potom pred pokračovaním vznikne nový ADR.
