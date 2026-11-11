# Tapety LECH OS

Návrh vlastníka projektu (8. 11. 2026, AI generátor obrázkov – pred vydaním overiť podmienky komerčného použitia):

| Súbor | Použitie |
|-------|----------|
| `tapeta-logo-941x1672.png` | predvolená tapeta plochy (s logom LECH OS) |
| `tapeta-bez-loga-941x1672.png` | predvolená tapeta zamknutej obrazovky (bez loga – sú tam hodiny) |

Do systému idú orezané na stred na pomer displeja Pixelu 9 a zväčšené na 1080 × 2424 (Lanczos):
`vendor/lech/tapety/lech_wallpaper.png` a `lech_lock_wallpaper.png`. Širší obrázok plocha zobrazila od ľavého okraja
a logo bolo posunuté doprava (11. 11. 2026). Nastavujú ich vlastnosti
`ro.config.wallpaper` a `ro.config.lock_wallpaper` (`vendor/lech/config/common.mk`). Platia, kým si používateľ
nenastaví vlastnú tapetu.

Návrhy majú 941 × 1672 px – pre ostrosť na displeji Pixelu treba neskôr verziu aspoň 1440 × 3232 px (pomer 9 : 20),
logo v 10–30 % výšky (hore je otvor fotoaparátu), pokojnejšia spodná časť pod ikonami.
