# Ikony aplikácií LECH OS

Ikony systémových aplikácií LECH OS (vlastník projektu, 8. 11. 2026): **Galéria, Prehliadač, Správy, Nastavenia,
Telefón, Fotoaparát, Kontakty**. Popis balíka a toho, ako vrstvy vznikli: [README-balik.md](README-balik.md); náhľad všetkých tvarov
(kruh, zaoblený štvorec, squircle, tematické ikony): [nahlad_adaptivne_ikony.png](nahlad_adaptivne_ikony.png).

| Priečinok | Obsah |
|-----------|-------|
| `galeria/`, `prehliadac/`, `spravy/`, `nastavenia/`, `telefon/`, `fotoaparat/`, `kontakty/` | zdroje 1024 × 1024 px: pozadie, popredie, jednofarebná vrstva (PNG, SVG), klasická ikona |
| `android-res/` | vrstvy pre Android po hustotách (108 dp: 108–432 px) – z nich sa robí systém |
| `navrhy/` | pôvodné ploché návrhy |

## Ako sa dostanú do systému

Aplikácie sú zatiaľ aplikácie AOSP (Gallery2, Messaging, Settings, Dialer, Camera2, Contacts) a prehliadač Vanadium
(TrichromeChrome, ADR-0011); ikonu im LECH OS mení
**prekrytím zdrojov** (runtime resource overlay, oddiel product), bez zásahu do ich kódu:

- `tools/etapa2/12-zostav-pixel.sh` pred zostavením spustí `tools/etapa2/ikony_prekrytia.py`: ten z aplikácií
  poslednej zostavy (aapt2) zistí, ktoré zdroje sú ich ikonou na ploche (ikona aplikácie, okrúhla ikona, ikony
  aktivít so spúšťačom), a pre každú aplikáciu vytvorí prekrytie `LechIkona<Aplikácia>Overlay`, ktoré tieto zdroje
  nahradí adaptívnou ikonou LECH OS (pozadie, popredie, jednofarebná vrstva pre tematické ikony).
- Zoznam aplikácií a ikon je v `ikony_prekrytia.py` (`APPS`). Novú ikonu pridať do `android-res/` s rovnakými
  názvami (`ic_<názov>_{background,foreground,monochrome}.png` v každej hustote) a do zoznamu.
- Keď sa aplikácie AOSP nahradia modernými (etapa 3), dostanú ikonu priamo v svojich zdrojoch.
- Launcher si ikony pamätá; po aktualizácii sa obnovia samy, inak pomôže reštart telefónu.

## Práva

Pôvodné návrhy pochádzajú z AI generátora obrázkov – pred vydaním overiť, že jeho podmienky dovoľujú komerčné
použitie. Jednofarebné glyfy sú vlastná vektorová kresba.
