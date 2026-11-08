# Lech-OS – ikony aplikácií
Galéria, Prehliadač, Správy, Nastavenia, Telefón, Fotoaparát, Kontakty

## Čo je v balíku
Pre každú aplikáciu je priečinok s tromi vrstvami adaptívnej ikony (PNG s priehľadnosťou, 1024 × 1024 px)
a s „klasickou“ jednoobrázkovou ikonou pre staršie launchery / Android < 8:

| súbor | obsah |
|---|---|
| `ic_<app>_background.png` | pozadie – len farebný prechod cez celú plochu, bez zaobleného rámu, nepriehľadné |
| `ic_<app>_foreground.png` | popredie – len motív na priehľadnom pozadí, dôležité časti v strede v bezpečnej zóne (66/108 dp ≈ 61 % šírky) |
| `ic_<app>_monochrome.png` / `.svg` | jednofarebná verzia (biely glyf, tvar určuje alfa) pre tematické ikony v Androide 13+ |
| `<app>_klasicka_1024.png`, `_512.png` | pôvodný návrh so zaobleným štvorcom, orezaný, priehľadné rohy |

`android-res/` obsahuje hotové zdroje pripravené na skopírovanie do `app/src/main/res/`:
`mipmap-anydpi-v26/ic_<app>.xml` (adaptívna ikona s vrstvami background / foreground / monochrome)
a `mipmap-{mdpi…xxxhdpi}/` s vrstvami v 108 dp (108/162/216/324/432 px) a klasickou ikonou v 48 dp (48/72/96/144/192 px).

V manifeste potom stačí `android:icon="@mipmap/ic_galeria"` (resp. `ic_prehliadac`, `ic_spravy`, `ic_nastavenia`,
`ic_telefon`, `ic_fotoaparat`, `ic_kontakty`).

## Ako vrstvy vznikli
Vrstvy sú oddelené z pôvodných plochých návrhov (zdrojové obrázky sú v `branding/ikony/navrhy/`):
- pozadie je hladký farebný prechod dopočítaný z pozadia návrhu (bez neónového rámu a bez motívu),
- popredie je motív vymaskovaný z návrhu vrátane jeho žiary; mierka je zvolená tak, aby sa celý motív zmestil do bezpečného kruhu,
  takže na Pixeli (kruh) ani na Samsungu (squircle) sa nič neodreže. Pozadie aj popredie používajú rovnakú mierku,
  preto priesvitné časti motívu (sklenené fotky, žiara) presne sedia na prechod pod nimi,
- jednofarebné glyfy sú nakreslené nanovo ako vektory (SVG). Pozn.: SVG používajú `<mask>`, ktorý importér Android Studia
  nepodporuje – do appky použite PNG, SVG slúži na ďalšie úpravy.
- Fotoaparát a Kontakty prišli v jednom obrázku (každá ikona ≈ 600 px), preto sú ich zdroje mierne zväčšené;
  pre maximálnu ostrosť klasickej ikony sa oplatí vygenerovať ich znova samostatne v plnom rozlíšení.

`nahlad_adaptivne_ikony.png` ukazuje všetky ikony tak, ako ich orežú rôzne launchery, aj tematický svetlý/tmavý režim.

## Práva
Pôvodné návrhy pochádzajú z AI generátora obrázkov – pred vydaním si overte, že jeho podmienky dovoľujú komerčné použitie.
Jednofarebné glyfy sú pôvodná vektorová kresba vytvorená pre Lech-OS.
