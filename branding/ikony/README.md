# Ikony aplikácií LECH OS

Návrhy ikon (vlastník projektu, 8. 11. 2026) – použijú sa v etape 3 pre základné aplikácie LECH OS
([docs/08](../../docs/08-plan-etap.md#etapa-3--vlastné-grafické-prostredie)):

| Súbor | Aplikácia |
|-------|-----------|
| `navrhy/galeria-1024.webp` | Galéria (náhrada Gallery2) |
| `navrhy/prehliadac-1024.webp` | Prehliadač |

## Čo Android potrebuje (adaptívna ikona)

Launcher Pixelu ikonu sám oreže do tvaru (kruh, zaoblený štvorec – podľa nastavenia) a pri pohybe vrstvy posúva.
Preto ikona nie je jeden obrázok, ale **dve vrstvy** 108 × 108 dp (podklady 432 × 432 px, kreslené aj vo väčšom):

- **pozadie** – farebný prechod cez celú plochu, bez zaobleného rámu (rám dokreslí launcher),
- **popredie** – len motív (fotky s krajinou, zemeguľa s prstencom) na priehľadnom pozadí; dôležité časti
  v strednom kruhu s priemerom 66 dp (≈ 61 % šírky), inak ich launcher oreže,
- **monochromatická vrstva** – motív jednou farbou na priehľadnom pozadí, pre ikony podľa motívu (Material You).

Súčasné návrhy majú zaoblený štvorec a pozadie zapečené v jednom obrázku – pri kruhovom tvare by launcher odrezal
rohy a ikona by mala „rám v ráme“. Na finálne ikony treba z toho istého návrhu vrstvy zvlášť (PNG s priehľadnosťou,
ideálne 1024 × 1024 px, alebo SVG).

## Práva

Ikony musia byť vlastné alebo s licenciou, ktorá dovoľuje použitie v produkte (pri obrázkoch z generátora AI
podmienky toho nástroja). Ikony a názvy aplikácií prevzatých z iných projektov sa menia aj kvôli ich ochranným
známkam (napr. prehliadače odvodené od Chromium alebo Firefoxu nesmú niesť pôvodné meno a logo).
