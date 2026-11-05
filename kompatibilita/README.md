# Testy kompatibility aplikácií

Výsledky testov, či bežné aplikácie fungujú na LECH OS bez služieb Google (úloha E2-10, ciele kompatibility
v [docs/02](../docs/02-architektura.md) a pilot pre firmy v [ADR-0007](../docs/adr/0007-prvy-trh-firmy.md)).
Každý test má vlastný priečinok so správou `sprava.md` a strojovo čitateľným `sprava.json`, ktoré vytvára
[tools/etapa2/10-test-aplikacii.sh](../tools/etapa2/10-test-aplikacii.sh). Zoznam aplikácií:
[spec/testovacie-aplikacie.txt](../spec/testovacie-aplikacie.txt). Súbory APK a snímky obrazovky v repozitári nie sú –
zostávajú na počítači, na ktorom test prebehol (`~/lech-work/kompatibilita`).

## Prehľad

| Dátum | Systém | Aplikácie | Výsledok |
|-------|--------|-----------|----------|
| 5. 11. 2026 (1.) | LECH OS 0.1.0-dev, Cuttlefish x86_64 (ABI `x86_64,arm64-v8a`), Android SDK 37 | 10 z F-Droid | **8 z 10 OK** – pozri nižšie |

## 1. test – 5. 11. 2026

Prepísané z výpisu na notebooku (`kompatibilita/20261105-134938-fdroid/sprava.md`).

| Aplikácia | Kategória | Inštalácia | Spustenie | Poznámka |
|-----------|-----------|------------|-----------|----------|
| Fennec F-Droid | prehliadač | OK | **PÁD** | `UnsatisfiedLinkError: Native library (com/sun/jna/android-x86-64/libjnidispatch.so) not found` – balíku pre x86_64 chýba knižnica JNA (používa ju Glean od Mozilly). Chyba balíka F-Droid pre x86_64, nie LECH OS; overí sa verzia pre ARM |
| K-9 Mail | e-mail | OK | OK | |
| DAVx⁵ | kalendár a kontakty | OK | OK | |
| Aegis | dvojfaktorové overenie | OK | OK | |
| WireGuard | VPN | – | – | V hlavnom repozitári F-Droid nie je; nahradené aplikáciou OpenVPN for Android |
| KeePassDX | heslá | OK | OK | |
| Element | komunikácia | OK | OK | |
| Organic Maps | mapy | OK | OK | Beží, navrchu bolo iné okno – takmer určite žiadosť o povolenie polohy (test to odteraz rozlišuje) |
| Material Files | súbory | OK | OK | |
| VLC | médiá | OK | OK | |

**Zistenia:** žiadna z aplikácií, ktoré sa dali nainštalovať, nepotrebovala služby Google na spustenie. Jediný pád
je chyba balíka pre x86_64. Ďalší test: Fennec aj vo verzii pre ARM (cez preklad kódu) a OpenVPN for Android
namiesto WireGuard.
