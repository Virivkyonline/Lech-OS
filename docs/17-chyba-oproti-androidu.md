# Čo chýba oproti bežnému Androidu (12. 11. 2026)

Prehľad pre rozhodnutie, čo pridať ďalej. Základ LECH OS je AOSP `generic_system` + handheld/telephony + `aosp_product`
(adevtool GrapheneOS, vetva 17). Zoznam balíkov AOSP je zo zrkadla `build/target/product/handheld_*.mk` – môže
zaostávať za Androidom 17. Skutočný stav v telefóne overí `adb -P 5038 shell pm list packages`.

**Podľa zoznamov AOSP v zostave sú:** klávesnica LatinIME, Hudba, tlač (PrintSpooler, BuiltInPrintService),
Stiahnuté súbory, prihlásenie do Wi-Fi siete (CaptivePortalLogin), VPN, núdzové výstrahy (CellBroadcast),
núdzové informácie, výber tapety (ThemePicker).

**Už známe a naplánované:** eSIM bez Google (OpenEUICC), E-mail, Počasie, sťahovanie a odosielanie MMS.

| # | Čo chýba | Stav v LECH OS (dôkaz) | Prečo vadí | Odporúčanie (licencia) |
|---|----------|------------------------|------------|------------------------|
| 1 | Obchod s aplikáciami | v `lech_tokay.mk` nie je; LECH Store až etapa 6 (`docs/08`) | aplikácie sa nedajú inštalovať ani aktualizovať | F-Droid + Privileged Extension (GPL-3.0); Accrescent (Apache-2.0) |
| 2 | Bankové aplikácie | chýba vrstva GmsCompat (`patches/README.md`); banky neotestované (`docs/13`) | 2FA a 3-D Secure potrebujú FCM, často aj Play Integrity | najprv otestovať cez Aurora Store; potom GmsCompat podľa GrapheneOS (ADR-0003) |
| 3 | Upozornenia bez FCM (push) | nič; plán `lech.push` (`docs/02`) | správy z chatov a bánk chodia neskoro alebo vôbec | ntfy (Apache-2.0) ako UnifiedPush, server LECH |
| 4 | Slovenská klávesnica so slovníkom | LatinIME má slovenské rozloženie, ale **chýba slovenský slovník** (návrhy, oprava a kontrola pravopisu nefungujú) | písanie od prvého dňa | HeliBoard (GPL-3.0) + slovenský slovník z voľného zoznamu slov |
| 5 | Prenos kontaktov | LechKontakty nahradili Kontakty AOSP; chýba im import a export .vcf a SIM; Kalendár nevie importovať .ics | kontakty zo starého telefónu | import a export .vcf v LechKontaktoch |
| 6 | Prihlásenie do hotelovej Wi-Fi, rozpoznanie siete bez internetu | vypnuté záplatami `packages/modules/NetworkStack` (súkromie: žiadne overovanie na Google) | prihlasovacia stránka sa neukáže; Wi-Fi bez internetu sa asi nevymení za mobilné dáta (neoverené) | vlastná adresa overenia na serveri LECH (E2-08b), potom overovanie zapnúť |
| 7 | Rýchla poloha | GPS bez pomocných dát A-GPS (mimo 112, `patches/frameworks/base` 0009); polohu zo sietí nič neposkytuje (predpoklad) | prvé určenie polohy pomalé, v budove žiadne | proxy A-GPS na serveri LECH; neskôr Wi-Fi poloha (BeaconDB) |
| 8 | Mapy a navigácia | nie sú | potrebné od prvého dňa | Organic Maps (Apache-2.0, offline) |
| 9 | Kalkulačka | v zostave nie je (grep) | základná aplikácia | ExactCalculator z AOSP (Apache-2.0) – overiť v strome AOSP 17 |
| 10 | Prehliadač PDF | asi žiadny (overiť: `cmd package resolve-activity -a android.intent.action.VIEW -t application/pdf`) | faktúry, lístky | PdfViewer od GrapheneOS (MIT, cez WebView Vanadium) |
| 11 | Čítanie textu nahlas (TTS) | žiadny engine | hlas navigácie, TalkBack | RHVoice so slovenčinou (GPL-2.0; licenciu hlasu overiť) |
| 12 | Hlasové zadávanie | žiadny rozpoznávač reči; tlačidlo mikrofónu sa skrýva (`LechNastavenia` MainActivity) | diktovanie, hlasové hľadanie | Transcribro (ISC, offline) – kvalita slovenčiny neoverená |
| 13 | Záloha | žiadna (Seedvault nie je) | strata telefónu = strata dát | Seedvault (Apache-2.0, vetva android17) |
| 14 | Miniaplikácie na ploche | LechHodiny nahradili DeskClock; žiadna aplikácia LECH nemá miniaplikáciu | zmizne miniaplikácia hodín | hodiny a program dňa v LechHodiny a LechKalendar |
| 15 | Synchronizácia kontaktov a kalendára | iba v telefóne | počítač, rodina | DAVx5 (GPL-3.0) |
| 16 | Poznámky, diktafón | nie sú | bežné aplikácie | Fossify Notes, Voice Recorder (GPL-3.0) |
| 17 | Filmy v HD (DRM) | hardvérový Widevine nie je zriadený (`patches/packages/modules/RemoteKeyProvisioning`) | Netflix, Voyo len v SD alebo vôbec | zriaďovanie cez proxy LECH |
| 18 | Posielanie súborov do počítača | Quick Share chýba | fotky do PC | LocalSend (Apache-2.0, domáca sieť) |

## Odporúčaný postup

1. **Hneď, málo práce:** Kalkulačka, Prehliadač PDF, F-Droid (obchod) – v zostave od 12. 11. (`docs/stav-prace.md`);
   Organic Maps (mapy) a klávesnica HeliBoard cez F-Droid, neskôr priamo v systéme rovnakým postupom ako F-Droid.
2. **LechKontakty:** import a export .vcf skôr, než niekto prenesie kontakty.
3. **Server LECH (E2-08b):** jeden server vyrieši riadky 6 a 7 (overenie internetu, A-GPS) a môže niesť aj ntfy (push).
4. **Písanie a hlas po slovensky:** HeliBoard so slovníkom, hlas RHVoice – pred prvým cudzím používateľom.
5. **Banky:** krok 1 z `docs/13` – vyskúšať bankové aplikácie cez Aurora Store; ukáže, či treba vrstvu GmsCompat.

Každú pridanú aplikáciu treba premerať (meranie mimo telefónu) – F-Droid, mapy a ntfy sa pripájajú na svoje servery.
