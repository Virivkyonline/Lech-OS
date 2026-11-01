#!/usr/bin/env python3
"""Kontrola katalógu akcií LECH AI (spec/ai-akcie.yaml).

Overuje pravidlá z docs/04-lech-ai.md, aby sa do katalógu nedostala akcia,
ktorá by obišla súhlas používateľa. Spustenie:

    python3 tools/validate_ai_actions.py [cesta/k/ai-akcie.yaml]

Vyžaduje PyYAML. Pri chybe vráti nenulový návratový kód.
"""

import sys
from pathlib import Path

import yaml

RIZIKA = {"R0", "R1", "R2", "R3"}
SUHLASY = ["ziadny", "povolenie_zdroja", "potvrdenie_pri_vytvoreni", "vzdy_potvrdit_s_nahladom"]
UDAJE = {"D0", "D1", "D2"}
SIET = {"nikdy", "volitelne_so_suhlasom", "vzdy"}
POVINNE_POLIA = {"id", "popis", "riziko", "suhlas", "udaje", "siet", "vratne"}


def skontroluj(akcia: dict) -> list[str]:
    chyby = []
    aid = akcia.get("id", "<bez id>")

    chybajuce = POVINNE_POLIA - akcia.keys()
    if chybajuce:
        return [f"{aid}: chýbajú polia {sorted(chybajuce)}"]

    riziko, suhlas, udaje, siet = akcia["riziko"], akcia["suhlas"], akcia["udaje"], akcia["siet"]

    if riziko not in RIZIKA:
        chyby.append(f"{aid}: neznáme riziko {riziko!r} (R4 nepatrí do katalógu)")
    if suhlas not in SUHLASY:
        chyby.append(f"{aid}: neznámy súhlas {suhlas!r}")
    if siet not in SIET:
        chyby.append(f"{aid}: neznáma hodnota siet {siet!r}")
    if not isinstance(udaje, list) or not set(udaje) <= UDAJE:
        chyby.append(f"{aid}: udaje musia byť zoznam z {sorted(UDAJE)} (D3 je pre AI zakázané)")
    if not isinstance(akcia["vratne"], bool):
        chyby.append(f"{aid}: vratne musí byť true/false")
    if chyby:
        return chyby

    uroven_suhlasu = SUHLASY.index(suhlas)
    if riziko == "R3" and suhlas != "vzdy_potvrdit_s_nahladom":
        chyby.append(f"{aid}: R3 vyžaduje súhlas 'vzdy_potvrdit_s_nahladom'")
    if riziko == "R2":
        if uroven_suhlasu < SUHLASY.index("potvrdenie_pri_vytvoreni"):
            chyby.append(f"{aid}: R2 vyžaduje aspoň 'potvrdenie_pri_vytvoreni'")
        if not akcia["vratne"]:
            chyby.append(f"{aid}: akcia R2 musí byť vratná, inak patrí do R3")
    if "D2" in udaje and siet == "vzdy":
        chyby.append(f"{aid}: citlivé údaje D2 nesmú vždy odchádzať zo zariadenia")
    if siet != "nikdy" and suhlas == "ziadny":
        chyby.append(f"{aid}: akcia so sieťou nesmie byť bez súhlasu")
    return chyby


def main() -> int:
    cesta = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "spec" / "ai-akcie.yaml"
    katalog = yaml.safe_load(cesta.read_text(encoding="utf-8"))
    akcie = katalog.get("akcie") or []

    chyby = []
    videne = set()
    for akcia in akcie:
        aid = akcia.get("id")
        if aid in videne:
            chyby.append(f"{aid}: duplicitné id")
        videne.add(aid)
        chyby.extend(skontroluj(akcia))

    if chyby:
        print(f"Katalóg {cesta} obsahuje {len(chyby)} chýb:")
        for chyba in chyby:
            print(f"  - {chyba}")
        return 1

    print(f"Katalóg {cesta}: {len(akcie)} akcií, všetky pravidlá splnené.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
