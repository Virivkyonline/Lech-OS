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
