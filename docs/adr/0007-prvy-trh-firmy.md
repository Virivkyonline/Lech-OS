# ADR-0007: Prvý trh – pilot pre firmy a organizácie + komunitná verzia

- **Stav:** Prijaté (2026-11-01)
- **Dátum:** 2026-11-01

## Kontext

Prvý komerčný trh určuje, ktoré funkcie majú prednosť. Zvažované možnosti: spotrebitelia (B2C), firmy a verejná
správa (B2B), alebo oboje naraz.

- **B2C** potrebuje veľký marketing, vyladený obchod s aplikáciami a vysokú kompatibilitu s bežnými aplikáciami
  vrátane bánk a platieb – teda presne to, čo je pre nezávislý systém najťažšie.
- **B2B** znamená menej zariadení, vyššiu maržu, zákazníkov ochotných platiť za súkromie, kontrolu nad údajmi a podporu.
  Firmy často používajú obmedzenú sadu aplikácií, takže problém Play Integrity je menší.

## Rozhodnutie

1. **Prvý komerčný produkt: pilot pre firmy a organizácie** – 1 až 3 organizácie, 20 až 100 zariadení,
   počas etapy 8.
2. **Paralelne komunitná verzia** pre jednotlivcov – rovnaký kód, bezplatný inštalátor, podpora cez komunitu.
3. B2C ako hlavný trh až po overení produktu u firiem (fáza A+ z [ADR-0001](0001-obchodna-cesta.md)).

## Dôsledky pre plán a architektúru

| Oblasť | Zmena |
|--------|-------|
| **Správa zariadení (MDM)** | Nová priorita. Bežné riešenia pre firemné Androidy (Android Enterprise) sú postavené na Google službách. Potrebujeme správu zariadení, ktorá funguje bez Google: podpora štandardných rozhraní AOSP pre správu zariadení (*device owner*, pracovný profil) a správcovský server – vlastný alebo open-source. Základ v etape 4, plná verzia v etape 6. |
| **Sprievodca prvým spustením** | Musí podporovať firemné zriadenie zariadenia (napr. naskenovaním QR kódu správcu). |
| **LECH Cloud a AI** | Možnosť prevádzky na infraštruktúre zákazníka (on-premise) alebo vo vyhradenej inštancii. Licencia AGPL v3 to umožňuje ([ADR-0006](0006-licencia-otvoreny-kod.md)). |
| **Kompatibilita** | Do zoznamu cieľových aplikácií pribudnú firemné aplikácie: e-mail a kalendár, videokonferencie, firemná komunikácia, VPN klienti, autentifikačné aplikácie (MFA), firemné bankovníctvo. |
| **LECH Store** | Pre pilot stačí riadený zoznam schválených aplikácií od správcu; verejný obchod môže prísť neskôr. |
| **Bezpečnosť a súlad** | Pre firmy pripravíme bezpečnostný whitepaper, podklady pre posúdenie vplyvu (DPIA) a výsledky nezávislého auditu. |
| **Podpora** | SLA pre pilotných zákazníkov, proces výmeny zariadení, kanál na hlásenie problémov. |

## Kedy rozhodnutie prehodnotiť

- Po pilote: ak firmy nebudú ochotné platiť, alebo ak komunitná verzia ukáže výrazne väčší záujem jednotlivcov.
