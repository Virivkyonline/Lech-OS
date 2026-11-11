# Lech-OS – ikony aplikácií
Galéria, Prehliadač, Správy, Nastavenia, Telefón, Fotoaparát, Kontakty, Súbory, Kalendár, Hodiny

Pre každú aplikáciu: `ic_<app>_background.png` (len farebný prechod, bez rámu), `ic_<app>_foreground.png` (len motív,
priehľadné pozadie, v bezpečnej zóne 66/108 dp), `ic_<app>_monochrome.png/.svg` (jednofarebný glyf pre tematické ikony
Android 13+) a `<app>_klasicka_1024/512.png` (pôvodný návrh, priehľadné rohy). Všetko 1024 × 1024 px.

`android-res/` je pripravené na kopírovanie do `app/src/main/res/` (mipmap-*dpi + `mipmap-anydpi-v26/ic_<app>.xml`);
v manifeste `android:icon="@mipmap/ic_galeria"` atď. SVG glyfy používajú `<mask>` – do appky použite PNG.

Súbory/Kalendár/Hodiny pochádzajú z jedného obrázku (~660 px na ikonu), takže klasické verzie sú zväčšené;
pre maximálnu ostrosť ich vygenerujte samostatne.

Práva: návrhy sú z AI generátora – overte, že jeho podmienky dovoľujú komerčné použitie.
