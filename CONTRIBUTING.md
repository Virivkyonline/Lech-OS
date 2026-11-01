# Ako prispievať do LECH OS

Ďakujeme za záujem. LECH OS je otvorený projekt ([ADR-0006](docs/adr/0006-licencia-otvoreny-kod.md)).

## Licencia príspevkov

- Kód v tomto repozitári je pod licenciou **Apache 2.0** (súbor [LICENSE](LICENSE)).
- Odoslaním príspevku súhlasíte, že bude zverejnený pod licenciou repozitára, do ktorého prispievate.
- Názov a logo LECH nie sú súčasťou licencie kódu – odnože musia používať iný názov.

## Developer Certificate of Origin (DCO)

Každý commit od externého prispievateľa musí obsahovať riadok:

```
Signed-off-by: Meno Priezvisko <email@example.com>
```

Pridáte ho automaticky príkazom `git commit -s`. Tým potvrdzujete, že máte právo kód poskytnúť
(plné znenie: <https://developercertificate.org/>).

## Postup

1. **Zásadné zmeny začínajú návrhom ADR** v [docs/adr](docs/adr/README.md) (šablóna je v README).
2. Zmeny posielajte ako pull request. Každý PR kontroluje aspoň jedna ďalšia osoba;
   zmeny v bezpečnostne citlivých častiach dve osoby ([docs/05](docs/05-lech-security.md#bezpečnostné-procesy)).
3. Pred odoslaním spustite kontroly:

   ```bash
   python3 tools/validate_ai_actions.py
   ```

4. Tvrdenia o externých faktoch (stav AOSP, legislatíva, podpora zariadení) doplňte o zdroj a dátum overenia.

## Jazyk

Dokumentácia projektu je v slovenčine. Kód, názvy v kóde a komentáre v kóde píšeme v angličtine,
aby sa do projektu mohli zapojiť aj zahraniční vývojári. Výnimkou sú špecifikácie a nástroje, ktoré priamo
nadväzujú na slovenskú dokumentáciu (napr. [spec/ai-akcie.yaml](spec/ai-akcie.yaml) a jeho validátor).

## Bezpečnostné chyby

Bezpečnostné chyby **nehláste verejne** v issues. Kým nebude zriadený bezpečnostný kontakt
(plánované pred etapou 8), kontaktujte priamo vlastníka repozitára.
