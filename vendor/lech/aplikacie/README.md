# Vlastné aplikácie LECH OS (etapa 3)

| Modul | Čo je | Nahrádza |
|-------|-------|----------|
| `LechUi` | spoločný vzhľad (tmavé sklo, neónovo zelená, hlavička s logom a horami), ikony Material | – |
| `LechKontakty` (`lech.os.kontakty`) | kontakty (všetky, obľúbené, skupiny, firmy, hľadanie), detail, úprava s fotkou, história hovorov, klávesnica | AOSP Contacts |
| `LechSubory` (`lech.os.subory`) | kategórie, úložiská, priečinky (mriežka/zoznam), hľadanie, kopírovať/presunúť/vymazať, premenovať, otvoriť, zdieľať; prístup ku všetkým súborom povolí používateľ raz | – (DocumentsUI ostáva ako systémový výber súborov) |
| `LechNastavenia` (`lech.os.nastavenia`) | hlavné časti nastavení s hľadaním, úložisko a batéria naživo, podstránka Systém | – (Settings ostáva, otvárajú sa jeho obrazovky) |
| `LechKontakty` – `PhoneActivity` | **Telefón**: rýchla voľba, kontakty, história, hlasová schránka, nedávne hovory, klávesnica (vlastná ikona) | – (Dialer ostáva pre obrazovku hovoru) |
| `LechSpravy` (`lech.os.spravy`) | **SMS a MMS**: konverzácie, odosielanie a príjem SMS, upozornenia s odpoveďou, zablokované čísla; predvolená aplikácia pre SMS (`LechSmsOverlay`). MMS: uložené sa zobrazia, sťahovanie a odosielanie MMS neskôr | AOSP Messaging |
| `LechHodiny` (`lech.os.hodiny`) | **Hodiny**: budíky (dni, názov, odloženie, zvonenie aj na zamknutej obrazovke), svetový čas, časovač, stopky s kolami | AOSP DeskClock |
| `LechKalendar` (`lech.os.kalendar`) | **Kalendár**: mesiac (bodky udalostí), týždeň, deň, agenda; udalosti s miestom, pripomienkou, opakovaním a farbou v poskytovateľovi kalendára (miestny kalendár „LECH OS“, nič sa nesynchronizuje); upozornenia pripomienok; úlohy | AOSP Calendar |
| `LechFotoaparat` (`lech.os.fotoaparat`) | **Fotoaparát** (vzhľad navrhnutý podľa štýlu LECH OS): fotky a videá cez Camera2, blesk, priblíženie aj širokouhlý objektív, mriežka, predná kamera; ukladá do DCIM/Camera bez polohy; odpovedá na IMAGE_CAPTURE iných aplikácií | AOSP Camera2 |
| `LechGaleria` (`lech.os.galeria`) | fotky po dňoch, albumy, videá, obľúbené, kôš, prehliadač s priblížením, otočenie (uloží kópiu), zdieľanie | AOSP Gallery2 |

- Java, len verejné API Androidu (`sdk_version: "current"`), bez knižníc AndroidX – zostavujú sa s produktom
  (`vendor/lech/products/lech_tokay.mk`).
- Kontakty sa ukladajú len do zariadenia (lokálny účet), nič sa nesynchronizuje. Volanie ide cez Telecom;
  obrazovku hovoru zatiaľ kreslí aplikácia Telefón (Dialer).
- Povolenia dostanú aplikácie pri prvom štarte (`default-permissions-*.xml`), používateľ ich môže v Nastaveniach odobrať.
- Galéria mení médiá (kôš, obnovenie, obľúbené, odstránenie) cez potvrdzovacie okná systému (`MediaStore.create*Request`).
- Návrhy obrazoviek od vlastníka projektu (11. 11. 2026); obrázok hôr v hlavičke je vyrezaný z návrhu.
- Kontrola kompilácie mimo AOSP (aapt2, javac, d8 zo SDK) prešla; správanie treba overiť v telefóne.
