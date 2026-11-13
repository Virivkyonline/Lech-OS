# patches – zmeny kódu AOSP v LECH OS

Každý súbor `<cesta projektu AOSP>/NNNN-popis.patch` je jedna zmena v jednom projekte AOSP. Správa commitu
vysvetľuje, prečo zmena vznikla. Rozhodnutie a pravidlá sú v [ADR-0010](../docs/adr/0010-upravy-aosp-ako-zaplaty.md).

```bash
tools/etapa2/aplikuj-zaplaty.sh             # použije záplaty na ~/aosp (06-zostav-lech.sh to robí sám)
tools/etapa2/aplikuj-zaplaty.sh --kontrola  # len vypíše, ktoré záplaty sú použité
```

| Projekt AOSP | Záplata | Prečo (úloha E2-06) |
|--------------|---------|---------------------|
| `device/google/cuttlefish` | Bez servera atestačných kľúčov | Cuttlefish volá testovací server Google `preprod-remoteprovisioning.googleapis.com` |
| `packages/modules/Connectivity` | Zoznam Certificate Transparency sa predvolene nesťahuje | Sťahuje sa z `www.gstatic.com` |
| `packages/modules/DnsResolver` | Overenie šifrovaného DNS s názvom `…probe.lech.invalid` | Pôvodný názov `…metric.gstatic.com` patrí Google |
| `packages/apps/WallpaperPicker2` | Odkaz `g.co/wallpaper` bez overovania (`autoVerify`) | Overenie sťahuje `https://g.co/.well-known/assetlinks.json` pri prvom štarte |
| `packages/modules/NetworkStack` | Kontrola pripojenia predvolene vypnutá | Kontroluje sa voči `connectivitycheck.gstatic.com` a `www.google.com` |
| `frameworks/base` (z GrapheneOS, E2-11b) | 5 rozšírení aapt2 od GrapheneOS (autori ostávajú): text konfigurácie v `Configuration.proto` (`stringified`), formát prevodu `proto-adevtool`, príkaz `dump brief-package-info` (aj s prvkom `library`), knižnica `aapt2_proto_java` | Potrebujú ich adevtool a `tools/arsclib` pri generovaní podpory Pixelu. Zmena aapt2 raz spôsobí nový preklad zdrojov aplikácií vo všetkých produktoch |
| `tools/apksig` (z GrapheneOS, E2-11b) | Príkaz `apksigner print-certs` (a jeho oprava) | adevtool ním zisťuje certifikáty predpodpísaných aplikácií Pixelu (SELinux). Zmena apksig raz spôsobí nové podpísanie aplikácií |
| `external/selinux` (z GrapheneOS, E2-11b) | `dispol`: chýbajúce úvodzovky a výpis typov s atribútmi (`-a`) | adevtool ním rozoberá politiku SELinux pôvodného systému Pixelu |
| `packages/apps/Messaging` (E2-11b) | Aplikácia SMS si žiada `RECEIVE_WAP_PUSH` a `READ_CELL_BROADCASTS` | Android 17 uzná predvolenú aplikáciu SMS len s povolenými app ops týchto oprávnení; bez nich aplikácia nemohla odosielať a neoznamovala prijaté SMS, hoci mala rolu SMS (Pixel 9, 7. 11. 2026). Aplikácia SMS od GrapheneOS ich žiada tiež |
| `packages/apps/CarrierConfig2` (GrapheneOS, E2-11b) | Bez testovacej obrazovky `TestActivity` (porovnanie s aplikáciou Google CarrierSettings cez GmsCompat) | Používa rozhrania, ktoré má len GrapheneOS (`gmscompat`, `android.ext`); na AOSP by sa aplikácia nezostavila. Načítanie nastavení operátora (SMS, VoLTE, APN) ostáva nezmenené |
| `vendor/adevtool` (GrapheneOS, E2-11b) | Iné číslo zostavy systému ako vendora je len varovanie | LECH OS má vlastné `BUILD_ID`, súbory vendora sú z pôvodnej zostavy Google `CP3A.261005.005` |
| `vendor/adevtool` (GrapheneOS, E2-11b) | Vývojárska zostava s cieľom ARMv8-A (`LECH_PIXEL_GENERIC_CPU`) | Využije systém zostavený pre GSI; vydania zostávajú ARMv9 s MTE |
| `vendor/adevtool` (GrapheneOS, E2-11b) | Politika SELinux Pixelu sa počíta voči platformovej politike AOSP (`LECH_PLAT_SEPOLICY_CIL`) | adevtool ju inak počíta voči politike GrapheneOS (`vendor/state`), ktorá nemá napr. `preloads_copy` a `system_server_startup`; tieto typy deklaroval znova a zostava zlyhala (`Duplicate declaration of type`) |
| `system/core`, `frameworks/native`, `frameworks/hardware/interfaces`, `frameworks/base` (z GrapheneOS „17-qpr1“, E2-11b) | 6 zmien: používateľ `AID_AOAD`, rozhranie `android.frameworks.stats` V3 (definícia, náhradné metódy služby, odkaz v `services`), `Parcel::writeUtf8AsUtf16(std::string_view)` v libbinder, ID zdrojov frameworku zhodné s CP3A (`framework-res-stable-ids.txt`) | Súbory vendora Pixelu sú z Androidu 17 QPR1 (`CP3A.261005.005`), platforma je Android 17: init vendora používa používateľa `aoad`, vendor hlási štatistiky cez stats V3 a volá novú funkciu libbinder. Zmena `Parcel.h` a ID zdrojov raz spôsobí veľký nový preklad |
| `frameworks/base` (0008 z GrapheneOS, 0009 LECH OS; E2-11b) | GPS: serveru SUPL sa neposiela IMSI ani telefónne číslo; mimo tiesňového volania žiadny server SUPL (A-GPS); predpovede satelitov (PSDS) čipu Samsung z kópie GrapheneOS `samsung.psds.grapheneos.org` (od 13. 11. 2026: bez PSDS Pixel 9 vonku polohu nenašiel) | Pixel 9 pri štarte kontaktoval `supl.google.com` (nastavenia operátora Google pre 4ka) a `1.ssiloc.com` (Samsung). Prvá poloha trvá dlhšie; pri tiesňovom volaní ostáva nastavenie operátora |
| `frameworks/base` (0010 LECH OS, E3-01) | Prehliadač Vanadium sa v celom systéme volá „Prehliadač“ („Browser“) a má ikonu LECH OS z balíka `lech.os.ikony` (`PackageItemInfo.loadUnsafeLabel`, `ApplicationPackageManager.loadUnbadgedItemIcon`, nová trieda `LechAppNames`) | Aplikácia je podpísaná GrapheneOS a názov ani ikonu nedovolí prekryť; v Nastaveniach (Predvolené aplikácie, zoznam aplikácií) bolo „Vanadium“ s pôvodnou ikonou (12. 11. 2026) |
| `frameworks/base` (0011 LECH OS, E3) | Systémové súčasti vedľa vlastných aplikácií majú v zoznamoch aplikácií vlastné mená: Settings „Nastavenia systému“, DocumentsUI „Výber súborov“, PDF Viewer GrapheneOS „Zobrazovač PDF“, Launcher3 „Plocha“ (`LechAppNames`, len názov aplikácie) | V Nastaveniach → Aplikácie boli dvakrát Nastavenia, Súbory a PDF/PDF Viewer (12. 11. 2026) |
| `packages/inputmethods/LatinIME` (0001 LECH OS, E3) | Na prvej obrazovke nastavení klávesnice jediný prepínač „Prediktívny text“ (rovnaké nastavenie `show_suggestions` ako Oprava textu → Zobrazovať návrhy) | Zapnutie a vypnutie návrhov slov musí byť pre zákazníka jednoduché (13. 11. 2026); slovenský slovník `main_sk.dict` pridáva `tools/etapa2/12-zostav-pixel.sh` |
| `vendor/adevtool` (GrapheneOS, E2-11b) | Aplikácie eSIM od Google (`com.google.android.euicc`, `com.google.euiccpixel`…) sa do modulu Pixelu nedostanú; produkt neohlasuje funkciu eSIM (`vendor/lech/config/sysconfig/lech-no-esim.xml`) | Pri každom štarte volali `lpa.live.esimdiscovery.com` (Google) a `lpa.ds.gsma.com`; po `pm disable-user` ich systém znova zapol. Fyzická SIM funguje ďalej, eSIM neskôr ako vedomá voľba |
| `system/sepolicy` (z GrapheneOS, E2-11b) | Súbory `sepolicy_ext.cil` v priečinkoch politiky sa pridajú do politiky partícií (system_ext, product, vendor, odm, recovery) | adevtool do nich zapisuje pravidlá pôvodnej politiky Pixelu; AOSP by ich vynechal a vendor Pixelu by nemal svoje pravidlá SELinux. Produkty bez týchto súborov sa nemenia |

| `packages/modules/RemoteKeyProvisioning` (E2-12, brána 1) | Widevine (DRM) sa zriaďuje len cez server nastavený pre systém (`remote_provisioning.hostname`, ako atestačné kľúče); LECH OS ho nechá prázdny → bez zriadenia | rkpd pri každom štarte posielal žiadosť ovládača DRM Pixelu na `www.googleapis.com` – jediné spojenie na Google v 24 h meraní mimo telefónu (10. 11. 2026). Dôsledok: obsah s DRM bez hardvérového Widevine (napr. bez HD v streamovacích aplikáciách), kým nebude proxy LECH OS |

| `packages/modules/AdServices` (z GrapheneOS, E3-01) | Knižnicu `android.ext.adservices` smie použiť aj `external/vanadium` | Prehliadač a WebView Vanadium ju uvádzajú ako voliteľnú; bez tohto povolenia sa zostava zastaví (`not visible to this module`) |

| `frameworks/libs/systemui` (0001 z GrapheneOS, 0002–0003 LECH OS; E3-01) | Plocha obnoví pamäť ikon po každej aktualizácii systému (`Build.VERSION.INCREMENTAL`); ikony aplikácií, ktoré nepovoľujú prekrytie, berie z balíka `lech.os.ikony` (len predinštalovaného); ich názvy (Vanadium → „Prehliadač“/„Browser“) z tabuľky v `LauncherActivityCachingLogic` | Po aktualizácii ostávali na ploche staré ikony (8. 11. 2026); prehliadač Vanadium nepovoľuje prekryť svoju ikonu, ikona LECH OS zmizla a názov ostal „Vanadium“ (11. 11. 2026) |

| `packages/apps/Launcher3` (E3-03) | Nová voľba mriežky 4 × 4 pre telefóny ~411 dp (Pixel 9) s ikonami 66 dp – predvolená | Návrh plochy LECH OS má 4 stĺpce s veľkými ikonami; Launcher3 volil 5 × 5 s ikonami 56 dp |
| `packages/apps/Launcher3` (0002, E3) | Zoznam aplikácií neukazuje Settings, DocumentsUI, Dialer a prehliadač PDF GrapheneOS (`filtered_components` priamo v `res/values/config.xml`) | Nahrádzajú ich vlastné aplikácie LECH OS; prekrytie `LechLauncherOverlay` v telefóne nefungovalo (Súbory, Nastavenia a PDF Viewer boli dvakrát, 12. 11. 2026) – odstránené |

Záplaty pre projekty Pixelu ([manifests/lech-pixel.xml](../manifests/lech-pixel.xml)) sa preskočia, kým projekty
nie sú stiahnuté (`tools/etapa2/12-priprav-pixel.sh`).

Pravidlá:

- **Záplata mení jednu vec.** Komentár `LECH OS:` v kóde vysvetľuje, čo sa zmenilo a prečo.
- **Záplata vzniká proti nezmenenému súboru z AOSP:** `git format-patch` v čistom projekte, autor `LECH OS`.
- **Čo sa dá nastaviť v `vendor/lech`** (nastavenie produktu, prekrytie RRO), **sa nerobí záplatou.**
