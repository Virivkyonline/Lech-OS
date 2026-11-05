#!/usr/bin/env bash
# LECH OS on a Pixel (task E2-11a): bootloader unlock, flashing the LECH OS GSI and checks after the boot.
# The phone keeps its own vendor, kernel and boot images; only the system image (and a vbmeta with
# verification switched off) is replaced. Everything can be undone with the Android Flash Tool (`spat`).
# In WSL the Windows tools adb.exe/fastboot.exe from Google's platform-tools are used (C:\lech\platform-tools):
# USB devices reach Windows, not WSL. The Windows adb server runs on port 5038, apart from the WSL adb
# of Cuttlefish.
#
# Usage: tools/etapa2/11-pixel.sh <command>
#   nastroje   download platform-tools for Windows (adb.exe, fastboot.exe) to C:\lech\platform-tools
#   stav       what is connected: Android (adb) or bootloader (fastboot), model, version, bootloader state
#   odomknut   unlock the bootloader – ERASES ALL DATA in the phone (confirm on the phone)
#   nahrat     flash the LECH OS GSI from 11-zostav-gsi.sh – erases all data
#   kontrola   checks in the running LECH OS (identity, keys, network time, mobile network)
#   spat       how to go back to the original Google system
# Environment: LECH_WORK (default ~/lech-work), LECH_KEYS (default ~/lech-keys/test), LECH_PIXEL_DIR (images;
#   default /mnt/c/lech/pixel in WSL), LECH_PIXEL_TOOLS (default /mnt/c/lech/platform-tools in WSL)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
WORKDIR=${LECH_WORK:-$HOME/lech-work}
KEYS=${LECH_KEYS:-$HOME/lech-keys/test}
EXPECTED_DEVICE=tokay   # Pixel 9
TOOLS_URL=https://dl.google.com/android/repository/platform-tools-latest-windows.zip
DRIVER_URL=https://developer.android.com/studio/run/win-usb

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

if lech_in_wsl && [ -d /mnt/c ]; then
  WINDOWS=1
  PIXEL_DIR=${LECH_PIXEL_DIR:-/mnt/c/lech/pixel}
  TOOLS=${LECH_PIXEL_TOOLS:-/mnt/c/lech/platform-tools}
  ADB=("$TOOLS/adb.exe" -P 5038)
  FASTBOOT=("$TOOLS/fastboot.exe")
else
  WINDOWS=0
  PIXEL_DIR=${LECH_PIXEL_DIR:-$WORKDIR/pixel}
  ADB=("$(lech_adb || echo adb)")
  FASTBOOT=("$(command -v fastboot || echo "${AOSP_DIR:-$HOME/aosp}/out/host/linux-x86/bin/fastboot")")
fi

need_tools() {
  if [ -x "${FASTBOOT[0]}" ] && [ -x "${ADB[0]}" ]; then return 0; fi
  [ "$WINDOWS" -eq 1 ] && die "Chýbajú nástroje Google pre Windows – spustite: tools/etapa2/11-pixel.sh nastroje"
  die "Chýba adb alebo fastboot (${ADB[0]}, ${FASTBOOT[0]})."
}
# Windows programs print CRLF; fastboot prints its answers to stderr. Images are passed by name from PIXEL_DIR.
# fastboot waits forever for a phone that is not connected: call fb_var only after fastboot_ready.
adb_() { "${ADB[@]}" "$@" 2>&1 | tr -d '\r'; }
fb() { (cd "$PIXEL_DIR" 2>/dev/null || cd "${TOOLS:-/}" 2>/dev/null || cd /; "${FASTBOOT[@]}" "$@") 2>&1 | tr -d '\r'; }
fb_var() { timeout 30 "${FASTBOOT[@]}" getvar "$1" 2>&1 | tr -d '\r' | sed -n "s/^$1: *//p" | head -n 1; }
adb_ready() { adb_ devices | grep -q $'\tdevice$'; }
adb_unauthorized() { adb_ devices | grep -q $'\tunauthorized$'; }
fastboot_ready() { fb devices | grep -q $'\tfastboot$'; }
in_fastbootd() { fastboot_ready && [ "$(fb_var is-userspace)" = yes ]; }
prop() { adb_ shell getprop "$1" | head -n 1; }
cleanup() { [ "$WINDOWS" -eq 1 ] && [ -x "${ADB[0]}" ] && "${ADB[@]}" kill-server >/dev/null 2>&1; true; }
trap cleanup EXIT

wait_for() {   # seconds, test function
  local i
  for (( i = 0; i < $1; i += 2 )); do "$2" && return 0; sleep 2; done
  return 1
}
confirm() {   # question
  local answer
  printf '\n%s\nPokračovať? Napíšte ano: ' "$1"
  read -r answer
  [ "$answer" = ano ] || die "Zrušené, v telefóne sa nič nezmenilo."
}
driver_hint() {
  [ "$WINDOWS" -eq 1 ] || return 0
  cat <<EOF
  Ak telefón ukazuje režim bootloader (Fastboot Mode) a Windows ho nevidí, chýba ovládač: Správca zariadení →
  zariadenie s výkričníkom (Android, Pixel 9) → Aktualizovať ovládač → Google USB Driver ($DRIVER_URL).
  Kábel USB-C priamo do notebooku, nie cez rozbočovač.
EOF
}
# Into the bootloader: from Android over adb, or the phone is already there.
to_bootloader() {
  if fastboot_ready; then return 0; fi
  if adb_unauthorized; then
    echo "Telefón čaká na povolenie ladenia cez USB – potvrďte na telefóne 'Povoliť' a spustite znova."
  fi
  if adb_ready; then
    echo "Reštartujem telefón do bootloadera…"
    adb_ reboot bootloader >/dev/null
  else
    echo "Telefón nie je pripojený. Buď zapnite ladenie cez USB (Pre vývojárov → Ladenie cez USB) a povoľte"
    echo "tento počítač, alebo telefón vypnite a zapnite so stlačeným tlačidlom Zníženie hlasitosti (bootloader)."
    echo "Čakám 3 minúty…"
  fi
  wait_for 180 fastboot_ready || { driver_hint; die "Telefón sa v bootloaderi neukázal."; }
}
save_stock_info() {   # the original Google system, once, before it is replaced (for docs and the way back)
  adb_ready || return 0
  [ -f "$WORKDIR/pixel/povodny-system.txt" ] && return 0
  [ -z "$(prop ro.lech.version)" ] || return 0
  mkdir -p "$WORKDIR/pixel"
  {
    echo "Pôvodný systém Pixelu ($(date '+%d. %m. %Y %H:%M')):"
    for p in ro.product.model ro.product.device ro.build.version.release ro.build.id ro.build.fingerprint \
             ro.build.version.security_patch ro.bootloader gsm.version.baseband; do
      echo "  $p=$(prop "$p")"
    done
  } > "$WORKDIR/pixel/povodny-system.txt"
  echo "Pôvodný systém zapísaný: $WORKDIR/pixel/povodny-system.txt"
}

cmd_nastroje() {
  [ "$WINDOWS" -eq 1 ] || { echo "Mimo WSL použite adb a fastboot zo zostavy AOSP alebo z balíka distribúcie."; return 0; }
  mkdir -p "$(dirname "$TOOLS")"
  local zip="$(dirname "$TOOLS")/platform-tools-windows.zip"
  echo "Sťahujem platform-tools pre Windows od Google ($TOOLS_URL)…"
  curl -fL --progress-bar -o "$zip" "$TOOLS_URL" || die "Sťahovanie zlyhalo."
  rm -rf -- "${TOOLS:?}"
  python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$zip" "$(dirname "$TOOLS")" \
    || die "Rozbalenie zlyhalo."
  rm -f -- "$zip"
  [ -f "$TOOLS/fastboot.exe" ] || die "V balíku chýba fastboot.exe."
  chmod +x "$TOOLS"/*.exe 2>/dev/null
  echo "Hotovo: $TOOLS ($("${FASTBOOT[@]}" --version 2>&1 | tr -d '\r' | head -n 1))"
}

cmd_stav() {
  need_tools
  if adb_ready; then
    echo "Telefón beží v Androide (adb):"
    for p in ro.product.model ro.product.vendor.model ro.product.vendor.device ro.build.version.release \
             ro.build.display.id ro.vendor.build.version.release ro.boot.flash.locked ro.boot.verifiedbootstate; do
      printf '  %-32s %s\n' "$p" "$(prop "$p")"
    done
  elif fastboot_ready; then
    echo "Telefón je v $(in_fastbootd && echo 'režime fastbootd' || echo 'bootloaderi') (fastboot):"
    for v in product unlocked current-slot version-bootloader version-baseband snapshot-update-status; do
      printf '  %-24s %s\n' "$v" "$(fb_var "$v")"
    done
  else
    echo "Žiadny telefón. Zapnite ladenie cez USB (Nastavenia → Systém → Pre vývojárov) a na telefóne povoľte"
    echo "tento počítač, alebo telefón zapnite do bootloadera (Zníženie hlasitosti + Napájanie)."
    driver_hint
    return 1
  fi
}

cmd_odomknut() {
  need_tools
  if adb_ready; then
    [ "$(prop ro.oem_unlock_supported)" = 1 ] || echo "UPOZORNENIE: telefón nehlási podporu odomknutia (ro.oem_unlock_supported)."
    save_stock_info
  fi
  to_bootloader
  local product unlocked
  product=$(fb_var product); unlocked=$(fb_var unlocked)
  echo "Telefón: $product, bootloader odomknutý: $unlocked"
  [ "$unlocked" = yes ] && { echo "Bootloader je už odomknutý. Ďalší krok: tools/etapa2/11-pixel.sh nahrat"; return 0; }
  [ "$product" = "$EXPECTED_DEVICE" ] || echo "UPOZORNENIE: očakávaný Pixel 9 ($EXPECTED_DEVICE), pripojený je $product."
  confirm "Odomknutie bootloadera VYMAŽE VŠETKY DÁTA v telefóne. Na telefóne potom tlačidlami hlasitosti
vyberte 'Unlock the bootloader' a potvrďte tlačidlom Napájanie."
  fb flashing unlock >/dev/null
  echo "Potvrďte odomknutie na telefóne (máte 2 minúty)…"
  local i
  for (( i = 0; i < 120; i += 3 )); do
    fastboot_ready && [ "$(fb_var unlocked)" = yes ] && break
    sleep 3
  done
  if fastboot_ready && [ "$(fb_var unlocked)" = yes ]; then
    echo "Bootloader je odomknutý. Ďalší krok: tools/etapa2/11-pixel.sh nahrat"
  else
    echo "Odomknutie sa nepotvrdilo. Ak sa telefón reštartoval, vypnite ho, zapnite do bootloadera"
    echo "(Zníženie hlasitosti + Napájanie) a skontrolujte: tools/etapa2/11-pixel.sh stav"
    echo "Ak je v Nastaveniach prepínač Odomknutie OEM vypnutý, odomknutie nejde."
    return 1
  fi
}

cmd_nahrat() {
  need_tools
  local f
  for f in system.img vbmeta.img; do
    [ -f "$PIXEL_DIR/$f" ] || die "Chýba $PIXEL_DIR/$f – najprv zostavte LECH OS pre ARM: tools/etapa2/11-zostav-gsi.sh"
  done
  [ -f "$PIXEL_DIR/info.txt" ] && sed -n '1,2p' "$PIXEL_DIR/info.txt"
  save_stock_info
  to_bootloader
  local product unlocked slot snapshot
  product=$(fb_var product); unlocked=$(fb_var unlocked); slot=$(fb_var current-slot)
  echo "Telefón: $product, bootloader odomknutý: $unlocked, slot: $slot"
  [ "$unlocked" = yes ] || die "Bootloader je zamknutý – najprv: tools/etapa2/11-pixel.sh odomknut"
  [ "$product" = "$EXPECTED_DEVICE" ] || echo "UPOZORNENIE: očakávaný Pixel 9 ($EXPECTED_DEVICE), pripojený je $product."
  snapshot=$(fb_var snapshot-update-status)
  case "$snapshot" in
    ""|none) ;;
    *) die "Telefón ešte dokončuje aktualizáciu systému Google (snapshot-update-status: $snapshot). Spustite ho
  normálne, nechajte ho 10 minút zapnutý a skúste znova." ;;
  esac
  confirm "Do telefónu sa nahrá LECH OS (systém) a VYMAŽÚ SA VŠETKY DÁTA. Pôvodný systém sa dá vrátiť
cez Android Flash Tool (tools/etapa2/11-pixel.sh spat)."

  echo
  echo "1/4 vbmeta bez overovania (systém LECH OS nie je podpísaný kľúčom Google)…"
  local out
  out=$(fb --disable-verity --disable-verification flash vbmeta vbmeta.img) || \
    { printf '%s\n' "$out" | tail -n 5; die "vbmeta sa nepodarilo nahrať."; }
  echo "2/4 Reštart do režimu fastbootd (systém je v dynamickej partícii super)…"
  fb reboot fastboot >/dev/null
  sleep 5
  wait_for 120 in_fastbootd || { driver_hint; die "Telefón sa v režime fastbootd neukázal."; }
  echo "3/4 Nahrávam systém LECH OS ($(du -h "$PIXEL_DIR/system.img" | cut -f1), niekoľko minút)…"
  if ! out=$(fb flash system system.img); then
    printf '%s\n' "$out" | tail -n 5
    if printf '%s' "$out" | grep -qiE 'not enough space|resize'; then
      # The GSI does not mount the phone's /product (gsi_skip_mount.cfg); its space in super is needed.
      echo "V partícii super nie je miesto – odstraňujem nepoužívanú partíciu product_$slot (Flash Tool ju obnoví)…"
      fb delete-logical-partition "product_$slot"
      out=$(fb flash system system.img) || { printf '%s\n' "$out" | tail -n 5; die "Systém sa nepodarilo nahrať."; }
    else
      die "Systém sa nepodarilo nahrať."
    fi
  fi
  printf '%s\n' "$out" | grep -E '^Finished' || true
  echo "4/4 Mažem dáta (fastboot -w) a reštartujem…"
  out=$(fb -w) || { printf '%s\n' "$out" | tail -n 5; die "Dáta sa nepodarilo vymazať – v telefóne: Recovery → Factory reset."; }
  fb reboot >/dev/null
  cat <<EOF

Hotovo. Telefón štartuje LECH OS:
  - Najprv ukáže varovanie o odomknutom bootloaderi (oranžové) – je to v poriadku, po chvíli pokračuje.
  - Prvý štart trvá niekoľko minút. Ak sa telefón do 10 minút nedostane na úvodnú obrazovku, pošlite fotku
    obrazovky. Nahratie sa dá zopakovať: tools/etapa2/11-pixel.sh nahrat
  - Po štarte: Nastavenia → Informácie o telefóne (7× Číslo zostavy) → Systém → Pre vývojárov → Ladenie cez USB,
    povoliť tento počítač a spustiť: tools/etapa2/11-pixel.sh kontrola
  - Bootloader NIKDY nezamykajte s LECH OS (fastboot flashing lock) – telefón by sa nespustil. Zamknúť až
    s pôvodným systémom (tools/etapa2/11-pixel.sh spat) alebo s úplnou zostavou LECH OS (úloha E2-12).
EOF
}

cmd_kontrola() {
  need_tools
  echo "Čakám na telefón cez adb (povoľte tento počítač na telefóne)…"
  if ! wait_for 180 adb_ready; then
    adb_unauthorized && die "Telefón čaká na povolenie ladenia cez USB – potvrďte na telefóne 'Povoliť'."
    die "Telefón sa cez adb neozval – zapnite Ladenie cez USB (Pre vývojárov) a povoľte tento počítač."
  fi
  local version ok=1
  version=$(sed -n '1s/^LECH OS \([^ ]*\) .*/\1/p' "$PIXEL_DIR/info.txt" 2>/dev/null)
  [ -n "$version" ] || version=$(sed -n 's/^LECH_VERSION *[:?]= *//p' "$REPO_DIR/vendor/lech/config/version.mk")
  report() {   # OK|CHYBA|INFO, what, detail
    printf '  %-7s %s%*s %s\n' "$1" "$2" $(( 24 - ${#2} )) "" "$3"
    [ "$1" != CHYBA ] || ok=0
  }
  expect() {   # what, property, expected (glob)
    local value
    value=$(prop "$2")
    # shellcheck disable=SC2254
    case "$value" in
      $3) report OK "$1" "$value" ;;
      *) report CHYBA "$1" "${value:-<prázdne>} (očakávané ${3%\*})" ;;
    esac
  }
  echo
  echo "LECH OS na Pixeli:"
  report INFO "zariadenie" "$(prop ro.product.vendor.model) ($(prop ro.product.vendor.device)), vendor Android $(prop ro.vendor.build.version.release)"
  expect "model" ro.product.model "LECH-PHONE"
  expect "verzia LECH OS" ro.lech.version "$version"
  expect "číslo zostavy" ro.build.display.id "LECH-OS-$version*"
  if adb_ shell 'test -f /product/media/bootanimation.zip && echo ano' | grep -qx ano; then
    report OK "animácia pri štarte" /product/media/bootanimation.zip
  else
    report CHYBA "animácia pri štarte" "/product/media/bootanimation.zip chýba"
  fi
  if adb_ shell cmd overlay list android | grep -q '\[x\] lech.os.overlay.framework'; then
    report OK "čas (NTP)" "europe.pool.ntp.org"
  else
    report CHYBA "čas (NTP)" "prekrytie lech.os.overlay.framework nie je zapnuté"
  fi
  expect "podpisové kľúče" ro.build.tags "dev-keys"
  local want have
  for pair in "podpis systému:android:platform" "podpis Bluetooth:com.android.bluetooth:bluetooth"; do
    IFS=: read -r what pkg key <<< "$pair"
    want=$(lech_cert_hash "$KEYS/$key.x509.pem")
    have=$(adb_ shell dumpsys package "$pkg" | grep -m1 -o 'version:[0-9]*, signatures:\[[0-9a-f, ]*\]' | sed 's/^version:[0-9]*, //')
    case "$have" in
      *"$want"*) report OK "$what" "kľúč $key LECH OS ($want)" ;;
      "") report INFO "$what" "nezistený (dumpsys package $pkg)" ;;
      *) report CHYBA "$what" "$have, očakávaný kľúč LECH OS $want" ;;
    esac
  done
  report INFO "overený štart" "$(prop ro.boot.verifiedbootstate) (orange = odomknutý bootloader, v E2-11a očakávané)"
  report INFO "SIM a mobilná sieť" "SIM $(prop gsm.sim.state), operátor $(prop gsm.operator.alpha), sieť $(prop gsm.network.type)"
  report INFO "Wi-Fi" "$(adb_ shell cmd wifi status | head -n 1)"
  local rkp
  rkp=$(prop remote_provisioning.hostname)
  report INFO "atestačné kľúče (RKP)" "${rkp:-bez servera}"
  echo
  if [ "$ok" -eq 1 ]; then
    echo "LECH OS na Pixeli je v poriadku. Vyskúšajte volanie, SMS, mobilné dáta, Wi-Fi, fotoaparát a Bluetooth"
    echo "a napíšte, čo nefunguje (docs/11, časť 4h)."
  else
    echo "Niektorá kontrola zlyhala – pošlite tento výstup."
    return 1
  fi
}

cmd_spat() {
  cat <<EOF
Návrat k pôvodnému systému Google:
  1. Vo Windows v prehliadači Chrome alebo Edge otvorte https://flash.android.com
  2. Pripojte Pixel, povoľte prístup, vyberte najnovšiu verejnú zostavu (Back to Public) a Install build.
     Ponechajte zapnuté Wipe device; Lock bootloader zapnite len vtedy, keď telefón chcete natrvalo vrátiť.
  3. Ak Flash Tool telefón nevidí, zatvorte ostatné programy s adb (tento skript server adb po skončení vypína).
Pôvodný systém pred prvým nahratím LECH OS: $WORKDIR/pixel/povodny-system.txt
EOF
}

case "${1:-}" in
  nastroje) cmd_nastroje ;;
  stav) cmd_stav ;;
  odomknut) cmd_odomknut ;;
  nahrat) cmd_nahrat ;;
  kontrola) cmd_kontrola ;;
  spat) cmd_spat ;;
  -h|--help|"") sed -n '2,19p' "$0" ;;
  *) die "Neznámy príkaz: $1 (pomoc: --help)" ;;
esac
