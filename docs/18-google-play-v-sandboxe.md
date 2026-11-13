# Google Play v izolácii (sandbox) – ako to funguje a za akých podmienok

Stav: **návrh pre zákazníkov, v LECH OS zatiaľ nie je hotové** (13. 11. 2026). Vzor je GrapheneOS, z ktorého LECH OS
vychádza; fakty nižšie sú z jeho oficiálneho návodu ([grapheneos.org/usage#sandboxed-google-play](https://grapheneos.org/usage#sandboxed-google-play)).
Rozhodnutie: [ADR-0003](adr/0003-kompatibilita-google-sluzieb.md).

## Krátko pre zákazníka

- **LECH OS je predvolene úplne bez Googlu.** Telefón nič neposiela Googlu, kým si to sami nezvolíte.
- Google Play je **dobrovoľná aplikácia**. Potrebujú ju napr. niektoré banky, Android Auto, hry či platby v aplikáciách.
- Keď si ju nainštalujete, je to **obyčajná aplikácia ako každá iná** – nemá žiadne zvláštne práva v systéme.
- Systém LECH OS Google **nikdy nepoužíva** pre vlastné služby (poloha, aktualizácie, zálohy, klávesnica…).
- Kedykoľvek ju môžete **odinštalovať** – telefón ďalej funguje bez nej.

## Čo Google Play v LECH OS môže a nemôže

| Nemôže | Môže (len s vaším súhlasom alebo cez aplikácie, ktoré ju používate) |
|--------|----------------------------------------------------------------------|
| čítať údaje iných aplikácií (správy, fotky, súbory) | fungovať pre aplikácie, ktoré ju samy chcú použiť (banka, Android Auto) |
| obísť izoláciu aplikácií ako na bežnom Androide | dostať oprávnenie (kontakty, poloha…), len ak ho výslovne povolíte |
| meniť systém, inštalovať potichu aplikácie | inštalovať a aktualizovať aplikácie z Obchodu Play – každú inštaláciu potvrdíte |
| byť zdrojom polohy pre systém | doručovať upozornenia (push) aplikáciám, ktoré ho používajú |

**Čo Google uvidí:** vašu internetovú adresu, základné údaje o zariadení, ktoré Play služby posielajú, údaje
aplikácií, ktoré cez Google Play pracujú (napr. upozornenia banky), a ak sa prihlásite, váš účet Google.
Prihlásenie je dobrovoľné – je potrebné len na sťahovanie z Obchodu Play a platby.

**Poloha:** aplikácie, ktoré chcú polohu cez Google, ju dostanú zo systému LECH OS – Google polohu nezisťuje
(GrapheneOS: „Reroute location requests to OS APIs“, predvolene zapnuté).

## Kde ju mať (profily)

- **Najjednoduchšie:** v hlavnom profile. Aj tam je izolovaná a nič zvláštne nevidí.
- **Viac súkromia:** v samostatnom profile (napr. „Banka a auto“) – Google Play potom vidí len aplikácie v tom
  profile, vaše bežné aplikácie v hlavnom profile o nej nevedia. Odporúčané pre tých, komu záleží na súkromí najviac.
- Návrh pre LECH OS: pri prvom spustení dať zákazníkovi jasnú voľbu medzi „bez Googlu“ (predvolené), „v hlavnom
  profile“ a „v samostatnom profile“ – s týmto vysvetlením.

## Android Auto

Potrebuje viac prístupu ako bežná aplikácia. GrapheneOS ho podporuje ako rozšírenie s **vlastnými prepínačmi**
(každý prístup zapína používateľ sám). Rovnako by to bolo v LECH OS.

## Čo treba v LECH OS urobiť (technicky)

1. Vrstvu kompatibility GrapheneOS (gmscompat) – v našom zdrojovom kóde zatiaľ nie je.
2. Inštaláciu jedným ťuknutím (overené balíky Google Play) a sprievodcu s voľbou profilu.
3. Overiť banky ([docs/13](13-bankove-aplikacie.md)) a Android Auto.
