# patches-gos – úpravy LECH OS nad základom GrapheneOS (ADR-0012)

Používajú sa namiesto `patches/`, keď je `LECH_BASE=gos` (`tools/etapa2/gos.sh`). Triedenie 13. 11. 2026 voči
GrapheneOS 2026110800 (`tools/etapa2/16-triedenie-zaplat.sh`): z 40 záplat 18 už GrapheneOS obsahuje, 13 sadne,
8 je v konflikte, 1 projekt chýba.

| Záplaty | Stav |
|---------|------|
| `frameworks/base` 0010, 0011 (názvy a ikony LECH OS), `frameworks/libs/systemui` 0002, 0003, `Launcher3` 0001, 0002, `LatinIME` 0001, 0002 | prevzaté sem |
| `frameworks/base` 0001–0008, `external/selinux`, `frameworks/native`, `frameworks/hardware/interfaces`, `system/core`, `system/sepolicy`, `tools/apksig`, `AdServices`, `systemui` 0001 | odpadli – sú v GrapheneOS |
| `vendor/adevtool` 0001–0004, `CarrierConfig2` 0001 | odpadli – prispôsobovali adevtool čistému AOSP (0004 eSIM: GrapheneOS má vlastné riešenie, overiť auditom) |
| `frameworks/base` 0009 (GPS) | odpadla – GrapheneOS má vlastné nastavenie PSDS/SUPL; overiť auditom |
| `Connectivity`, `DnsResolver`, `NetworkStack`, `RemoteKeyProvisioning`, `WallpaperPicker2` (spojenia na Google) | konflikt – GrapheneOS ich rieši inak (vlastné servery); rozhodnúť po sieťovom audite |
| `packages/apps/Messaging` | projekt v GrapheneOS chýba; SMS robí LechSpravy |
| `device/google/cuttlefish` | len pre virtuálny telefón, zatiaľ neprenesená |
| `bootable/recovery` 0001 | nové (14. 11.): obrazovky recovery a fastbootd sa volajú „LECH OS Recovery“ / „LECH OS Fastboot“ |
| `Launcher3` 0002 | doplnené (14. 11.): skrytá ikona App Store základu (`app.grapheneos.apps`), ostáva pre zónu Google |

Dvojité aplikácie základu sa odstraňujú cez `overrides` vlastných aplikácií (`vendor/lech/aplikacie/*/Android.bp`):
Camera, Messaging, ExactCalculator, Auditor, InfoApp.

| `packages/apps/SetupWizard2` 0001 | nové (14. 11.): úvodný sprievodca s názvom a logom LECH OS a slovenskými textami (logo kopíruje `12-zostav-pixel.sh`) |
| `Launcher3` 0003 | nové (14. 11.; súbor `res-stock/xml/default_workspace_4x6.xml`, mriežka „medium“): plocha nového telefónu s aplikáciami LECH OS (dock Telefón, Správy, Prehliadač, Fotoaparát; riadok Obchod, Galéria, Mapy, Nastavenia) |
| `build/make` 0001 | nové (14. 11.): predvolené gestá v `aosp_product.mk` sú len voliteľná hodnota (`?=`), aby `lech_tokay.mk` mohol predvoliť tri tlačidlá |
| `packages/apps/Dialer` 0001 | nové (14. 11.): obrazovka hovoru znova nad tapetou (`windowShowWallpaper`), slovenské texty nahrávania hovorov („Nahrať hovor“), upozornenie menuje LECH OS |
| `packages/apps/Dialer` 0002 | nové (14. 11.): obrazovka hovoru má jeden tmavý priesvitný prechod LECH OS (farba už nezávisí od kontaktu) |
| `packages/apps/GmsCompat` 0001 | nové (15. 11.): nastavenia zóny Google (povolenia Android Auto, služby Play, upozornenia vrstvy) po slovensky, obrazovka sa volá „Zóna Google“ |
| `packages/apps/Settings` 0002 | nové (15. 11.): hlavná obrazovka systémových nastavení má ikony LECH OS (`lech_set_*.png` z `branding/ikony/nastavenia`, 160 px) namiesto dlaždíc základu |
| `packages/apps/Settings` 0003 | nové (15. 11.): obrazovka „Ochrana pred zneužitím chýb“ pri aplikácii po slovensky, anglický text menuje LECH OS |
| `frameworks/base` 0014 | nové (15. 11.): názvy LECH OS aj v zoznamoch čítaných cez `ResolveInfo.loadLabel` (náhľad veľkosti zobrazenia ukazoval „PDF Viewer“, „Vanadium“) |
| `frameworks/base` 0012 | nové (15. 11.): systémový balík sa volá „LECH OS“ (upozornenia boli podpísané „GrapheneOS“); ikony LECH OS pre Obchod aplikácií a Mapy aj v Nastaveniach |
| `packages/apps/Settings` 0001 | nové (15. 11.): slovenské texty 231 nastavení základu; núdzové heslo menuje LECH OS, text Play Integrity bez GrapheneOS |
| `frameworks/base` 0013 | nové (15. 11.): slovenské texty 21 systémových hlásení základu („Treba reštartovať aplikácie“…) |
