# ADR-0006: Licencia – otvorený kód

- **Stav:** Prijaté (2026-11-01) – princíp „otvorený kód“ potvrdil vlastník projektu; konkrétne licencie navrhol tím
- **Dátum:** 2026-11-01

## Kontext

LECH OS sľubuje súkromie. Používatelia, firmy a audítori tomu uveria ľahšie, ak si môžu kód sami preveriť.
Zároveň potrebujeme:

- licenciu zlučiteľnú s AOSP (Apache 2.0) a jadrom Linux (GPL v2),
- aby partnerskí výrobcovia (cesta A+ a B) mohli systém bez obáv integrovať,
- aby konkurent nemohol prevziať naše cloudové služby, upraviť ich a prevádzkovať bez zverejnenia zmien,
- chránenú značku, aj keď je kód otvorený.

## Zvážené možnosti

| Možnosť | Výhody | Nevýhody |
|---------|--------|----------|
| Všetko Apache 2.0 | Jednoduché, zhodné s AOSP, obsahuje patentovú licenciu, priateľské pre výrobcov | Ktokoľvek môže prevziať aj backend a prevádzkovať ho ako uzavretú službu |
| Všetko GPL v3 / AGPL v3 | Silná ochrana pred uzavretými odnožami | Ťažko zlučiteľné s AOSP (Apache 2.0) v systémových častiach; odrádza výrobcov |
| **Kombinácia podľa vrstvy** | Zariadenie: Apache 2.0 (ako AOSP); servery: AGPL v3 (úpravy prevádzkovanej služby sa musia zverejniť) | Dve licencie treba vysvetliť |

## Rozhodnutie

| Časť | Licencia |
|------|----------|
| Úpravy AOSP, systémové služby (vrátane LECH AI), systémové aplikácie, SDK, nástroje, špecifikácie, tento repozitár | **Apache 2.0** |
| Jadro Linux a jeho úpravy | **GPL v2** (povinné) |
| Serverové komponenty LECH Cloud a LECH Store | **AGPL v3** |
| Prevzatý kód tretích strán | Pôvodná licencia, s uvedením autorstva |
| Značka „LECH“ / „LECH OS“, logo | **Nie je súčasťou licencie** – chránená ochrannou známkou. Odnože kódu musia používať iný názov. |

Príspevky od externých prispievateľov prijímame s **DCO** (*Developer Certificate of Origin*, riadok `Signed-off-by`
v commite) – pozri [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Dôsledky

- ✅ Dôvera: súkromie a bezpečnosť sa dajú nezávisle overiť; uľahčuje to audity a predaj firmám a verejnej správe.
- ✅ Výrobcovia môžu systém integrovať bez obáv z copyleftu.
- ✅ Firmy môžu LECH Cloud prevádzkovať na vlastných serveroch (AGPL to pri internom použití nijako neobmedzuje).
- ✅ Otvorený kód pomáha získať komunitu a vývojárov (riziko R8 v [docs/10](../10-rizika-a-otvorene-otazky.md)).
- ❌ Konkurenti môžu prevziať kód systému. Obranou je značka, kvalita, podpora, infraštruktúra a tempo vývoja.
- ❌ Pri DCO (bez CLA) neskoršia zmena licencie vyžaduje súhlas prispievateľov. Ak budeme chcieť ponúkať serverové
  komponenty aj pod komerčnou licenciou, prejdeme pred prvými externými príspevkami do backendu na CLA.
- Ďalšie kroky: súbor `LICENSE` v každom repozitári, kontrola licencií v CI, registrácia ochrannej známky,
  pravidlá používania značky (trademark policy).

## Kedy rozhodnutie prehodnotiť

- Ak sa objaví partner, pre ktorého je AGPL v serverových komponentoch prekážkou.
- Pred prijatím prvého externého príspevku do serverových komponentov (DCO verzus CLA).
