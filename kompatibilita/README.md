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
| 5. 11. 2026 (2.) | To isté; 11 položiek (Fennec aj pre ARM, OpenVPN namiesto WireGuard) | 10 z F-Droid | **8 OK**; Fennec x86_64 sa netestoval (DOWNGRADE), Fennec ARM a Organic Maps nie – pozri nižšie |
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

## 2. test – 5. 11. 2026

OK: K-9 Mail, DAVx⁵, Aegis, **OpenVPN for Android**, KeePassDX, Element, Material Files, VLC.

- **Fennec (x86_64):** `INSTALL_FAILED_VERSION_DOWNGRADE` – v telefóne ostala verzia z 1. testu s vyšším číslom
  (1590020). Z toho vyplýva, že **1. test vybral verziu pre ARM** (najvyššie číslo), verzia pre x86_64 je 1590010.
  Test odteraz pred každou inštaláciou aplikáciu odinštaluje.
- **Fennec (ARM) padá** s `android-x86-64/libjnidispatch.so not found` – rovnako ako v 1. teste. JNA zisťuje procesor
  z Javy, ktorá na Cuttlefish beží ako x86_64, a hľadá kód pre x86-64; balík pre ARM ho nemá. Obmedzenie prekladu kódu
  ARM na Cuttlefish – na telefóne s ARM (Pixel) sa to nestane. Verzia pre x86_64 sa overí v 3. teste.
- **Organic Maps:** proces po 8 s nebežal (v 1. teste bežal na pozadí). Test odteraz pri páde alebo zastavení uloží log
  (`~/lech-work/kompatibilita/logy/`) a príčinu zapíše do správy.

