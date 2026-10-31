# 06 – LECH Store a LECH Cloud

## LECH Store

### Ciele

- Používateľ nájde aplikácie, ktoré potrebuje, **bez konta** (konto len pre platené aplikácie a nákupy).
- Pri každej aplikácii vidí **skutočné** informácie o súkromí – zistené analýzou, nie len vyhlásené vývojárom.
- Vývojár vie aplikáciu zverejniť jednoducho a bez závislosti od Google.

### Fázy

| Fáza | Obsah | Etapa |
|------|-------|-------|
| **1 – Kurátorský katalóg** | Výber overených open-source aplikácií (kompatibilita s formátom repozitárov F-Droid), automatické aktualizácie, kontrola podpisov | 6 |
| **2 – Portál pre vývojárov** | Registrácia vývojárov, nahrávanie aplikácií, automatická kontrola (malvér, trackery, oprávnenia), ručná kontrola pri citlivých oprávneniach | 6–8 |
| **3 – Komerčné aplikácie** | Platené aplikácie, predplatné, platby v aplikácii cez európskeho platobného partnera | 9 |

### Štítky súkromia

Každá aplikácia v Store dostane automaticky vytvorený štítok:

- zistené knižnice trackerov a reklamy,
- požadované oprávnenia a ich vysvetlenie,
- domény, s ktorými aplikácia komunikuje (z dynamickej analýzy),
- závislosť od Google služieb (úroveň kompatibility 0–3 z [docs/03](03-nezavislost-od-google.md#úrovne-kompatibility-aplikácií)).

### Aplikácie z Google Play

Komerčný produkt **nesmie byť postavený na neoficiálnom sťahovaní z Google Play** (porušuje podmienky Google
a je nespoľahlivé). Ak používateľ chce aplikácie z Google Play, môže si sám nainštalovať Google Play Store
v rámci voliteľnej vrstvy „Google Play v sandboxe“ (pozri [ADR-0003](adr/0003-kompatibilita-google-sluzieb.md)).
Dlhodobým cieľom je, aby čo najviac vývojárov zverejňovalo aplikácie priamo v LECH Store.

## LECH Cloud

### Princípy

1. **Voliteľný** – LECH OS je plne použiteľný bez LECH Cloud.
2. **Otvorené protokoly** – rovnaké služby si môže firma alebo používateľ prevádzkovať na vlastnom serveri.
3. **Šifrovanie na strane klienta** všade, kde to funkcia dovoľuje.
4. **Prevádzka v EÚ**, súlad s GDPR, minimum logov.
5. **Žiadna závislosť od jedného cloudového poskytovateľa** – infraštruktúra ako kód, prenositeľná medzi
   európskymi poskytovateľmi.

### Služby

| Služba | Protokol | Šifrovanie | Vlastný server? |
|--------|----------|------------|-----------------|
| LECH ID (konto) | OpenID Connect | – | – |
| Kontakty | CardDAV | Prenos TLS; voliteľne E2E v ďalšej verzii | Áno |
| Kalendár | CalDAV | Prenos TLS; voliteľne E2E v ďalšej verzii | Áno |
| Záloha telefónu | Vlastný formát nad WebDAV / S3 | **E2E – kľúč má len používateľ** | Áno |
| Súbory a fotografie | WebDAV | E2E | Áno |
| Push notifikácie | UnifiedPush | Obsah šifruje aplikácia | Áno |
| Polohová služba | Vlastné API kompatibilné s otvorenými databázami | Bez identifikátora zariadenia | Áno |
| LECH AI servery | Interné API (pozri [docs/04](04-lech-ai.md)) | TLS, bez ukladania požiadaviek | Áno (pre firmy) |
| Hľadanie strateného telefónu | Vlastný protokol | E2E – polohu vidí len vlastník | – |

> Pri E2E šifrovaní kontaktov a kalendára treba nájsť rovnováhu medzi súkromím a kompatibilitou
> so štandardnými klientmi (CardDAV/CalDAV šifrovanie obsahu štandardne nepodporujú). Rozhodneme v samostatnom ADR.

### Obchodný model cloudu (návrh na diskusiu)

| Úroveň | Obsah |
|--------|-------|
| **Bez konta** | Celý systém, LECH Store (bezplatné aplikácie), lokálna AI |
| **Bezplatné konto** | Synchronizácia kontaktov a kalendára, malá záloha |
| **Predplatné** | Väčšia záloha a úložisko, LECH AI servery, hľadanie telefónu, prioritná podpora |
| **Firmy** | Správa zariadení, vlastné servery alebo vyhradená inštancia, AI na vlastnej infraštruktúre, SLA |

Princíp: **platíme za služby, nie údajmi.** Predaj alebo reklamné využitie údajov je vylúčené aj v obchodných podmienkach.
