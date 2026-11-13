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

## Zóna Google (smer vlastníka, 13. 11. 2026)

Súkromie je hlavná hodnota značky, preto Google Play **nie je v hlavnom profile**, ale len v oddelenej, dobrovoľnej
**„zóne Google“** (samostatný profil):

- *„Čo je v zóne Google, môže komunikovať s Googlom. Čo je mimo nej, o Googli nevie a Google o tom nevie.“*
- Predvolene žiadny Google; zónu zapne zákazník jedným ťuknutím, keď potrebuje banku, Android Auto a pod.
- **Prehľad zóny Google:** ktoré aplikácie v nej sú, aké oprávnenia má každá z nich aj Google Play (s možnosťou
  vypnúť), kam sa pripájajú.
- Zónu možno pozastaviť (nič v nej nebeží) alebo celú vymazať.
- **Čo sľúbiť nevieme:** presný obsah toho, čo aplikácia Googlu posiela (je šifrovaný). Vieme ukázať, ktoré
  aplikácie Google používajú, s akými oprávneniami a kam sa pripájajú, a zaručiť, že mimo zóny sa nedostanú.

## Obmedzené údaje namiesto skutočných

Poctivá ochrana (ako GrapheneOS): aplikácia funguje, ale dostane prázdne alebo vybrané údaje.

- Kontakty: prázdny zoznam alebo len vybrané kontakty. Súbory a fotky: len vybrané.
- Poloha: len od systému a len po povolení, prípadne iba približná.
- Identifikátory zóny sú oddelené od zvyšku telefónu, po vymazaní zóny nové; reklamné ID vypnuteľné.
- Internetová adresa: VPN len pre zónu.
- **Nerobíme:** predstieranie iného zariadenia ani obchádzanie kontroly pravosti Googlu (Play Integrity) – je to
  klamanie o zariadení, banky to odhaľujú (blokovanie účtu) a pre značku je to právne riziko.

Veta pre zákazníka: *„Google dostane len to, čo mu sami povolíte, a namiesto ostatného prázdne údaje.“*

## Platené služby Google Play

- Fungujú (podľa návodu GrapheneOS): platené aplikácie a hry, nákupy v aplikáciách, predplatné, kontrola licencií.
- Podmienka: prihlásenie do účtu Google v zóne a platobná karta v ňom – Google vtedy pozná účet, kartu a nákupy.
- Nefunguje: platenie pri pokladni cez Google Peňaženku (NFC) – Google ho povoľuje len na zariadeniach, ktoré
  certifikoval.

## Platenie mobilom pri pokladni (NFC) bez Google Peňaženky

Neoverené na LECH OS; zdroje sú skúsenosti používateľov GrapheneOS (fóra GrapheneOS, Privacy Guides, 2025–2026).

1. **Curve Pay** – karty z banky sa pridajú do Curve, telefón platí cez NFC (predvolená aplikácia na bezkontaktné
   platby). Hlásené ako funkčné bez Play Integrity v krajinách EHP; pre Slovensko nepotvrdené; v 2026 aj hlásené
   výpadky. Curve vidí všetky platby (tretia strana).
2. **Vlastné NFC platenie v aplikácii banky** – niektoré európske banky ho majú; slovenské treba vyskúšať.
3. **Mimo telefónu** – hodinky (napr. Garmin Pay), prsteň, nálepka, karta: funguje hneď.
4. **Dohoda s bankami** – telefón vie hardvérovo dokázať nezmenený LECH OS so zamknutým bootloaderom; banka by
   LECH OS musela zaradiť medzi dôveryhodné systémy (obchodné rokovanie, dlhodobé).

Veta pre zákazníka (zatiaľ): *„Platenie mobilom pri pokladni závisí od vašej banky. Google Peňaženka v LECH OS nie je.“*

## Android Auto

Potrebuje viac prístupu ako bežná aplikácia. GrapheneOS ho podporuje ako rozšírenie s **vlastnými prepínačmi**
(každý prístup zapína používateľ sám). Rovnako by to bolo v LECH OS.

## Čo treba v LECH OS urobiť (technicky)

1. Vrstvu kompatibility GrapheneOS (gmscompat) – v našom zdrojovom kóde zatiaľ nie je.
2. Inštaláciu jedným ťuknutím (overené balíky Google Play) a sprievodcu s voľbou profilu.
3. Overiť banky ([docs/13](13-bankove-aplikacie.md)) a Android Auto.
