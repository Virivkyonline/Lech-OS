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
#   ovladac    download and install the Google USB Driver (Windows needs it for the bootloader; asks for admin)
#   stav       what is connected: Android (adb) or bootloader (fastboot), model, version, bootloader state
#   odomknut   unlock the bootloader – ERASES ALL DATA in the phone (confirm on the phone)
#   nahrat     flash the LECH OS GSI from 11-zostav-gsi.sh – erases all data
#   nahrat-uplne [--bez-mazania]  flash the full LECH OS for the Pixel 9 from 12-zostav-pixel.sh (E2-11b) – erases
#              all data; --bez-mazania keeps the data (a newer build of LECH OS over a running one, same keys)
#   dokoncit   only the last step of nahrat: erase the data and start (when the erase failed)
#   kontrola   checks in the running LECH OS (identity, keys, network time, mobile network)
#   siet       mobile network diagnostics (SIM, radio HAL, registration, errors) to ~/lech-work/pixel
#   sms [--rola]  log of one SMS attempt: state of the SMS app (permissions, app ops), clears the log, waits while
#              an SMS is sent in the phone, keeps the SMS lines; --rola first removes and adds the SMS role again
#              (the role grants the permissions of a newer app version)
#   audit [--minuty N] [--dokoncit] [--vyhodnotit]  network audit in the phone itself (tcpdump, Wi-Fi and mobile network; default
#              15 min); --dokoncit finishes a started audit after the USB connection dropped,
#              --vyhodnotit evaluates the last downloaded audit again (no phone needed)
#   slot a|b   make a slot active and start it (when the phone fell back to the Google system in the other slot)
#   spat       how to go back to the original Google system
# Environment: LECH_WORK (default ~/lech-work), LECH_KEYS (default ~/lech-keys/test), LECH_PIXEL_DIR (images;
#   default /mnt/c/lech/pixel in WSL). In WSL the tools and the driver live in C:\lech (fixed paths).

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
WORKDIR=${LECH_WORK:-$HOME/lech-work}
KEYS=${LECH_KEYS:-$HOME/lech-keys/test}
EXPECTED_DEVICE=tokay   # Pixel 9
REPO_URL=https://dl.google.com/android/repository
TOOLS_URL=$REPO_URL/platform-tools-latest-windows.zip
DRIVER_URL=https://developer.android.com/studio/run/win-usb

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

if lech_in_wsl && [ -d /mnt/c ]; then
  WINDOWS=1
  PIXEL_DIR=${LECH_PIXEL_DIR:-/mnt/c/lech/pixel}
  TOOLS=/mnt/c/lech/platform-tools
  ADB=("$TOOLS/adb.exe" -P 5038)
  FASTBOOT=("$TOOLS/fastboot.exe")
else
  WINDOWS=0
  PIXEL_DIR=${LECH_PIXEL_DIR:-$WORKDIR/pixel}
  ADB=("$(lech_adb || echo adb)")
  FASTBOOT=("$(command -v fastboot || echo "${AOSP_DIR:-$HOME/aosp}/out/host/linux-x86/bin/fastboot")")
fi

need_tools() {
  if ! [ -x "${FASTBOOT[0]}" ] || ! [ -x "${ADB[0]}" ]; then
    [ "$WINDOWS" -eq 1 ] && die "Chýbajú nástroje Google pre Windows – spustite: tools/etapa2/11-pixel.sh nastroje"
    die "Chýba adb alebo fastboot (${ADB[0]}, ${FASTBOOT[0]})."
  fi
  adb_start
}
# A freshly started adb server lists no phone for the first seconds (USB is still being scanned), and this
# script stops its server at the end - so start it first and give the phone time to show up.
adb_start() {
  local i
  "${ADB[@]}" start-server >/dev/null 2>&1
  for (( i = 0; i < 10; i++ )); do
    adb_ devices | grep -qE $'\t(device|unauthorized|recovery|sideload)$' && return 0
    fastboot_ready && return 0
    sleep 1
  done
}
# Windows programs print CRLF; fastboot prints its answers to stderr. Images are passed by name from PIXEL_DIR.
# fastboot waits forever for a phone that is not connected: call fb_var only after fastboot_ready.
# With ADB_TIMEOUT set (seconds) a command that does not answer is stopped (siet hung for 10 min, 7. 11. 2026).
adb_() {
  if [ -n "${ADB_TIMEOUT:-}" ]; then
    { timeout "$ADB_TIMEOUT" "${ADB[@]}" "$@" 2>&1; [ $? -eq 124 ] && echo "(adb: časový limit ${ADB_TIMEOUT} s: $*)"; } | tr -d '\r'
  else
    "${ADB[@]}" "$@" 2>&1 | tr -d '\r'
  fi
}
fb() { (cd "$PIXEL_DIR" 2>/dev/null || cd "${TOOLS:-/}" 2>/dev/null || cd /; "${FASTBOOT[@]}" "$@") 2>&1 | tr -d '\r'; }
fb_var() {   # a phone that is busy (just after a failed command) may not answer the first time
  local value i
  for i in 1 2 3; do
    value=$(timeout 30 "${FASTBOOT[@]}" getvar "$1" 2>&1 | tr -d '\r' | sed -n "s/^$1: *//p" | head -n 1)
    [ -n "$value" ] && break
    sleep 2
  done
  printf '%s\n' "$value"
}
adb_ready() { adb_ devices | grep -q $'\tdevice$'; }
# Wait for the phone over adb; when it does not show up, restart the Windows adb server once (after an
# interrupted command a hung adb.exe kept the phone, 7. 11. 2026: "no devices/emulators found").
adb_wait() {   # seconds
  wait_for 20 adb_ready && return 0
  echo "Telefón sa cez adb neukázal – reštartujem adb…"
  "${ADB[@]}" kill-server >/dev/null 2>&1
  [ "$WINDOWS" -eq 1 ] && taskkill.exe /IM adb.exe /F >/dev/null 2>&1
  sleep 2
  adb_start
  wait_for "${1:-60}" adb_ready
}
adb_unauthorized() { adb_ devices | grep -q $'\tunauthorized$'; }
# "fastboot devices" prints "<serial><TAB><interface>"; the interface name differs between bootloaders
# ("fastboot" in fastbootd), so any listed device counts.
fastboot_ready() { fb devices | grep -qE $'^[^ \t<]+[ \t]+[^ \t]'; }
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
  Ak telefón ukazuje režim bootloader (Fastboot Mode) a Windows ho nevidí, chýba ovládač Google USB Driver.
  Nainštalujte ho (telefón nechajte v bootloaderi): tools/etapa2/11-pixel.sh ovladac
  Kábel USB-C priamo do notebooku, nie cez rozbočovač.
EOF
}
# Into the bootloader: from Android over adb, or the phone is already there.
to_bootloader() {
  local i said=0
  for (( i = 0; i < 180; i += 2 )); do
    fastboot_ready && return 0
    if adb_ready; then
      echo "Reštartujem telefón do bootloadera…"
      adb_ reboot bootloader >/dev/null
      wait_for 120 fastboot_ready && return 0
      echo "fastboot devices -l: $(fb devices -l | head -n 3 | tr '\n' ' ')"
      windows_usb; driver_hint; die "Telefón sa v bootloaderi neukázal."
    fi
    if [ "$said" -eq 0 ]; then
      said=1
      if adb_unauthorized; then
        echo "Na telefóne potvrďte 'Povoliť ladenie cez USB' (Vždy povoliť z tohto počítača)…"
      else
        echo "Telefón nie je pripojený. Buď zapnite ladenie cez USB (Pre vývojárov → Ladenie cez USB) a povoľte"
        echo "tento počítač, alebo telefón vypnite a zapnite so stlačeným tlačidlom Zníženie hlasitosti (bootloader)."
        echo "Čakám 3 minúty…"
      fi
    fi
    sleep 2
  done
  echo "fastboot devices -l: $(fb devices -l | head -n 3 | tr '\n' ' ')"
  windows_usb
  driver_hint; die "Telefón sa nepripojil."
}
# What Windows itself sees on USB (Device Manager), to tell a missing driver from a missing connection.
windows_usb() {
  [ "$WINDOWS" -eq 1 ] && command -v powershell.exe >/dev/null 2>&1 || return 0
  local out
  out=$(cd /mnt/c && powershell.exe -NoProfile -NonInteractive -Command \
    "Get-PnpDevice -PresentOnly | Where-Object { \$_.FriendlyName -match 'Pixel|Android|ADB|Fastboot|Bootloader' -or \$_.InstanceId -match 'VID_18D1' } | ForEach-Object { '  ' + \$_.Status + '  ' + \$_.Class + '  ' + \$_.FriendlyName }" \
    < /dev/null 2>&1 | tr -d '\r')
  if [ -n "$out" ]; then
    echo "Windows vidí tieto zariadenia Google (stav OK = ovládač v poriadku, Error/Unknown = chýba ovládač):"
    printf '%s\n' "$out"
  else
    echo "Windows nevidí žiadne zariadenie Google na USB: skúste iný kábel (dátový, nie len nabíjací) alebo iný port"
    echo "a na telefóne v upozornení 'Nabíjanie cez USB' zvoľte 'Prenos súborov'."
  fi
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
  mkdir -p /mnt/c/lech || die "Priečinok C:\\lech sa nedá vytvoriť."
  echo "Sťahujem platform-tools pre Windows od Google ($TOOLS_URL)…"
  curl -fL --progress-bar -o /mnt/c/lech/platform-tools-windows.zip "$TOOLS_URL" || die "Sťahovanie zlyhalo."
  # The archive has the folder platform-tools/; extracting over an older copy replaces its files.
  python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' \
    /mnt/c/lech/platform-tools-windows.zip /mnt/c/lech || die "Rozbalenie zlyhalo."
  rm -f -- /mnt/c/lech/platform-tools-windows.zip
  [ -f "$TOOLS/fastboot.exe" ] || die "V balíku chýba fastboot.exe."
  chmod +x "$TOOLS"/*.exe 2>/dev/null
  echo "Hotovo: $TOOLS ($("${FASTBOOT[@]}" --version 2>&1 | tr -d '\r' | head -n 1))"
}

cmd_ovladac() {
  [ "$WINDOWS" -eq 1 ] || { echo "Ovládač je len pre Windows; v Linuxe stačia pravidlá udev pre adb a fastboot."; return 0; }
  local found url sha inf
  mkdir -p /mnt/c/lech || die "Priečinok C:\\lech sa nedá vytvoriť."
  echo "Hľadám aktuálny Google USB Driver v zozname balíkov Android SDK…"
  # Package "extras;google;usb_driver": archive name and SHA-1 checksum.
  found=$(curl -fsSL "$REPO_URL/addon2-1.xml" | python3 -I -c '
import sys, xml.etree.ElementTree as ET
local = lambda e: e.tag.rsplit("}", 1)[-1]
for pkg in ET.parse(sys.stdin).getroot().iter():
    if local(pkg) == "remotePackage" and pkg.get("path") == "extras;google;usb_driver":
        url = sha = ""
        for e in pkg.iter():
            if local(e) == "url" and not url: url = (e.text or "").strip()
            if local(e) == "checksum" and not sha: sha = (e.text or "").strip()
        print(url, sha)
        break')
  read -r url sha <<< "$found"
  case "${url:-}" in
    usb_driver_*.zip) ;;
    *) die "Balík Google USB Driver sa v zozname Android SDK nenašiel – stiahnite ho ručne: $DRIVER_URL" ;;
  esac
  echo "Sťahujem $url…"
  curl -fL --progress-bar -o /mnt/c/lech/usb_driver.zip "$REPO_URL/$url" || die "Sťahovanie zlyhalo."
  if [ -n "${sha:-}" ] && [ "$(sha1sum /mnt/c/lech/usb_driver.zip | cut -d' ' -f1)" != "$sha" ]; then
    rm -f -- /mnt/c/lech/usb_driver.zip
    die "Kontrolný súčet ovládača nesedí – súbor sa pri sťahovaní poškodil, skúste znova."
  fi
  python3 -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' \
    /mnt/c/lech/usb_driver.zip /mnt/c/lech || die "Rozbalenie zlyhalo."
  rm -f -- /mnt/c/lech/usb_driver.zip
  inf=/mnt/c/lech/usb_driver/android_winusb.inf
  [ -f "$inf" ] || die "V balíku chýba android_winusb.inf."
  echo "Inštalujem ovládač – Windows sa opýta, či povoliť zmeny (Áno)…"
  (cd /mnt/c && powershell.exe -NoProfile -NonInteractive -Command \
    "Start-Process -FilePath pnputil.exe -ArgumentList '/add-driver','$(wslpath -w "$inf")','/install' -Verb RunAs -Wait" \
    < /dev/null 2>&1 | tr -d '\r')
  # Windows binds the new driver to the phone a few seconds after the installation.
  adb_start
  if wait_for 30 fastboot_ready; then
    echo "Hotovo – Windows vidí telefón v bootloaderi. Ďalší krok: tools/etapa2/11-pixel.sh odomknut"
  else
    echo "Ovládač je nainštalovaný. Ak je telefón v bootloaderi a Windows ho stále nevidí, odpojte a znova pripojte"
    echo "kábel a skontrolujte: tools/etapa2/11-pixel.sh stav"
    windows_usb
  fi
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
    for v in product unlocked current-slot version-bootloader version-baseband snapshot-update-status \
             slot-successful:a slot-unbootable:a slot-retry-count:a slot-successful:b slot-unbootable:b slot-retry-count:b; do
      printf '  %-24s %s\n' "$v" "$(fb_var "$v")"
    done
  elif adb_unauthorized; then
    echo "Telefón je pripojený, ale čaká na povolenie: na telefóne potvrďte 'Povoliť ladenie cez USB'"
    echo "(zaškrtnite Vždy povoliť z tohto počítača) a spustite znova: tools/etapa2/11-pixel.sh stav"
    return 1
  else
    echo "Žiadny telefón. Zapnite ladenie cez USB (Nastavenia → Systém → Pre vývojárov) a na telefóne povoľte"
    echo "tento počítač, alebo telefón zapnite do bootloadera (Zníženie hlasitosti + Napájanie)."
    echo "adb: $(adb_ devices | sed 1d | tr '\n' ' ')"
    windows_usb
    driver_hint
    return 1
  fi
}

cmd_odomknut() {
  need_tools
  if adb_ready; then
    if [ "$(prop sys.oem_unlock_allowed)" = 0 ]; then
      echo "UPOZORNENIE: Odomknutie OEM je vypnuté (Pre vývojárov) – bootloader sa nedá odomknúť, kým ho nezapnete."
    fi
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
  local attempt deleted=0
  for attempt in 1 2 3; do
    out=$(fb flash system system.img) && break
    printf '%s\n' "$out" | tail -n 3
    if [ "$deleted" -eq 0 ] && printf '%s' "$out" | grep -qiE 'not enough space|resize'; then
      # The GSI does not mount the phone's /product (gsi_skip_mount.cfg); its space in super is needed.
      echo "V partícii super nie je miesto – odstraňujem nepoužívanú partíciu product_$slot (Flash Tool ju obnoví)…"
      fb delete-logical-partition "product_$slot"
      deleted=1
    elif printf '%s' "$out" | grep -qiE 'AdbWriteEndpointSync|AdbReadEndpointSync|not functioning|protocol error|no link|remote: .?Status read failed'; then
      # USB transfer broke off (6. 11. 2026: "A device attached to the system is not functioning").
      echo "Spojenie USB sa prerušilo (pokus $attempt z 3). Ak sa to opakuje: kábel priamo do notebooku, iný port USB."
      sleep 5
      wait_for 120 in_fastbootd || { driver_hint; die "Telefón sa v režime fastbootd znova neukázal – odpojte a pripojte kábel a spustite znova: tools/etapa2/11-pixel.sh nahrat"; }
    else
      die "Systém sa nepodarilo nahrať."
    fi
    [ "$attempt" -eq 3 ] && die "Systém sa nepodarilo nahrať ani na tretí pokus – skúste iný kábel alebo port USB a spustite znova."
  done
  printf '%s\n' "$out" | grep -E '^Finished' || true
  finish_flash "$slot"
}

# Full LECH OS for the Pixel 9 (E2-11b, 12-zostav-pixel.sh): all partitions with `fastboot update`, which flashes
# the boot partitions in the bootloader and the dynamic ones (system, vendor…) in fastbootd.
cmd_nahrat_uplne() {
  local keep=0
  case "${1:-}" in
    --bez-mazania) keep=1 ;;
    "") ;;
    *) die "Neznámy parameter: $1 (použitie: 11-pixel.sh nahrat-uplne [--bez-mazania])" ;;
  esac
  need_tools
  local zip_dir zip=lech-tokay-img.zip out attempt slot product unlocked snapshot
  if [ "$WINDOWS" -eq 1 ]; then zip_dir=/mnt/c/lech/pixel-uplne; else zip_dir=${LECH_PIXEL_FULL_DIR:-$WORKDIR/pixel-uplne}; fi
  [ -f "$zip_dir/$zip" ] || die "Chýba $zip_dir/$zip – najprv zostavte LECH OS pre Pixel 9: tools/etapa2/12-zostav-pixel.sh"
  [ -f "$zip_dir/info.txt" ] && sed -n '1,2p' "$zip_dir/info.txt"
  save_stock_info
  to_bootloader
  product=$(fb_var product); unlocked=$(fb_var unlocked); slot=$(fb_var current-slot)
  echo "Telefón: $product, bootloader odomknutý: $unlocked, slot: $slot"
  [ "$unlocked" = yes ] || die "Bootloader je zamknutý – najprv: tools/etapa2/11-pixel.sh odomknut"
  [ "$product" = "$EXPECTED_DEVICE" ] || die "Úplná zostava je pre Pixel 9 ($EXPECTED_DEVICE), pripojený je $product."
  snapshot=$(fb_var snapshot-update-status)
  case "$snapshot" in ""|none) ;; *) die "Telefón ešte dokončuje aktualizáciu (snapshot-update-status: $snapshot)." ;; esac
  if [ "$keep" -eq 1 ]; then
    confirm "Do telefónu sa nahrá nová zostava ÚPLNÉHO LECH OS; dáta ostanú (len nad bežiaci úplný LECH OS
s rovnakými kľúčmi – nie nad systém Google ani GSI; ak by telefón potom nenaštartoval: nahrat-uplne bez --bez-mazania)."
  else
    confirm "Do telefónu sa nahrá ÚPLNÝ LECH OS (systém, vendor, boot, vbmeta) a VYMAŽÚ SA VŠETKY DÁTA.
Pôvodný systém sa dá vrátiť cez Android Flash Tool (tools/etapa2/11-pixel.sh spat)."
  fi
  echo
  echo "1/2 Nahrávam obrazy (fastboot update, niekoľko minút; telefón sa medzitým prepne do fastbootd)…"
  for attempt in 1 2 3; do
    # fb runs in C:\lech\pixel (or C:\lech\platform-tools): the package is in the sibling folder pixel-uplne.
    if [ "$WINDOWS" -eq 1 ]; then out=$(fb update --skip-reboot "../pixel-uplne/$zip") && break
    else out=$(fb update --skip-reboot "$zip_dir/$zip") && break; fi
    printf '%s\n' "$out" | tail -n 4
    if printf '%s' "$out" | grep -qiE 'AdbWriteEndpointSync|AdbReadEndpointSync|not functioning|protocol error|no link'; then
      echo "Spojenie USB sa prerušilo (pokus $attempt z 3). Ak sa to opakuje: kábel priamo do notebooku, iný port USB."
      sleep 5
      wait_for 120 fastboot_ready || die "Telefón sa vo fastboot znova neukázal – pripojte kábel a spustite znova: tools/etapa2/11-pixel.sh nahrat-uplne"
    else
      die "Obrazy sa nepodarilo nahrať (výpis vyššie)."
    fi
    [ "$attempt" -eq 3 ] && die "Obrazy sa nepodarilo nahrať ani na tretí pokus – skúste iný kábel alebo port USB."
  done
  printf '%s\n' "$out" | grep -E '^(Finished|Sending|Writing)' | tail -n 3 | sed 's/^/    /'
  if [ "$keep" -eq 1 ]; then
    echo "2/2 Štart (dáta ostávajú):"
    finish_flash "$slot" keep
  else
    echo "2/2 Vymazanie dát a štart:"
    finish_flash "$slot"
  fi
}

# Erase the data of the previous system. The Pixel 9's fastbootd refuses it ("Error DCK wipe failed",
# 6. 11. 2026); the bootloader erases userdata and metadata itself.
wipe_data() {
  local out
  # A fastbootd that hangs after a failed command lists the phone but answers nothing (6. 11. 2026).
  if [ -z "$(fb_var product)" ]; then
    echo "    Telefón v režime fastboot neodpovedá. Na telefóne tlačidlami hlasitosti vyberte 'Reboot to bootloader'"
    echo "    a potvrďte tlačidlom Napájanie; potom spustite znova: tools/etapa2/11-pixel.sh dokoncit"
    return 2
  fi
  if in_fastbootd; then
    out=$(fb -w) && { printf '%s\n' "$out" | grep -E "^(Erasing|Formatting|Finished)" | sed 's/^/    /'; return 0; }
    printf '%s\n' "$out" | grep FAILED | tail -n 1
    echo "    V režime fastbootd sa dáta vymazať nedajú – mažem ich v bootloaderi…"
    # After the failed wipe fastbootd may not answer at all: the reboot command then hangs (7. 11. 2026).
    if ! (cd "$PIXEL_DIR" 2>/dev/null || cd /; timeout 30 "${FASTBOOT[@]}" reboot bootloader) >/dev/null 2>&1; then
      echo "    Telefón (fastbootd) neodpovedá. Na telefóne tlačidlami hlasitosti vyberte 'Reboot to bootloader'"
      echo "    a potvrďte tlačidlom Napájanie – čakám 5 minút…"
      in_bootloader_wait 300 || return 1
    else
      in_bootloader_wait || return 1
    fi
  fi
  out=$(fb -w)
  printf '%s\n' "$out" | grep -vE '^ *$' | tail -n 4 | sed 's/^/    /'
  # Success only when the bootloader really erased userdata (6. 11. 2026 a run printed only "Finished").
  printf '%s' "$out" | grep -qE "Erasing 'userdata'.*OKAY" && return 0
  return 1
}
# Wait until the phone answers from the bootloader (not fastbootd): a reboot takes some seconds, and in the
# meantime the old fastbootd can still be listed.
in_bootloader_wait() {   # [seconds, default 120]
  local i limit=${1:-120}
  sleep 3
  for (( i = 0; i < limit; i += 3 )); do
    if fastboot_ready && [ -n "$(fb_var product)" ] && [ "$(fb_var is-userspace)" != yes ]; then return 0; fi
    sleep 3
  done
  return 1
}
fastboot_gone() { ! fastboot_ready; }
recovery_wipe_hint() {
  cat <<EOF

Vymažte dáta v telefóne ručne (Recovery):
  1. V bootloaderi tlačidlami hlasitosti vyberte "Recovery mode" a potvrďte tlačidlom Napájanie.
  2. Keď sa objaví ležiaci Android s nápisom "No command": podržte Napájanie a raz stlačte Zvýšenie hlasitosti.
  3. Vyberte "Wipe data/factory reset" → "Factory data reset" (tlačidlá hlasitosti, potvrdenie Napájaním).
  4. Potom "Reboot system now" – spustí sa LECH OS.
EOF
}
finish_flash() {   # slot [keep]
  local status=0
  if [ "${2:-}" = keep ]; then
    echo "    Reštartujem (bez mazania dát)…"
  else
    echo "4/4 Mažem dáta starého systému a reštartujem…"
    wipe_data || status=$?
  fi
  [ "$status" -eq 2 ] && die "Dáta sa nevymazali, telefón neodpovedá (postup vyššie). LECH OS je nahratý."
  if [ "$status" -ne 0 ]; then
    recovery_wipe_hint
    die "Dáta sa cez fastboot vymazať nepodarilo. LECH OS je nahratý – chýba len vymazanie dát (postup vyššie)."
  fi
  fb reboot >/dev/null
  if ! wait_for 40 fastboot_gone; then
    die "Telefón sa nereštartoval. Na telefóne vyberte tlačidlami hlasitosti 'Start' (bootloader) alebo 'Reboot system now' (fastbootd) a potvrďte tlačidlom Napájanie."
  fi
  mkdir -p "$WORKDIR/pixel"
  echo "$(date '+%d. %m. %Y %H:%M') LECH OS nahratý do slotu ${1:-?}" >> "$WORKDIR/pixel/nahratia.txt"
  cat <<EOF

Hotovo (slot ${1:-nezistený}). Telefón štartuje LECH OS:
  - Najprv ukáže varovanie o odomknutom bootloaderi (oranžové) – je to v poriadku, po chvíli pokračuje.
  - Prvý štart trvá niekoľko minút. Ak sa telefón do 10 minút nedostane na úvodnú obrazovku, pošlite fotku
    obrazovky. Nahratie sa dá zopakovať: tools/etapa2/11-pixel.sh nahrat
  - Ak sa namiesto LECH OS objaví uvítanie Google ("Vitajte"), LECH OS sa nespustil a telefón sa po niekoľkých
    pokusoch vrátil k systému Google v druhom slote. Telefón do bootloadera a: tools/etapa2/11-pixel.sh stav
  - Po štarte: Nastavenia → Informácie o telefóne (7× Číslo zostavy) → Systém → Pre vývojárov → Ladenie cez USB,
    povoliť tento počítač a spustiť: tools/etapa2/11-pixel.sh kontrola
  - Bootloader NIKDY nezamykajte s LECH OS (fastboot flashing lock) – telefón by sa nespustil. Zamknúť až
    s pôvodným systémom (tools/etapa2/11-pixel.sh spat) alebo s úplnou zostavou LECH OS (úloha E2-12).
EOF
}

# Only the last step of `nahrat` (erase data, start) - when the system is flashed and the erase failed.
cmd_dokoncit() {
  need_tools
  if ! fastboot_ready; then
    echo "Telefón nie je v bootloaderi ani v režime fastbootd. Vypnite ho a zapnite so stlačeným tlačidlom"
    echo "Zníženie hlasitosti (bootloader). Čakám 3 minúty…"
    wait_for 180 fastboot_ready || { windows_usb; driver_hint; die "Telefón sa v bootloaderi neukázal."; }
  fi
  local slot
  slot=$(fb_var current-slot)
  confirm "Vymažú sa dáta v telefóne (slot ${slot:-nezistený}) a spustí sa LECH OS."
  finish_flash "$slot"
}

cmd_kontrola() {
  need_tools
  echo "Čakám na telefón cez adb (povoľte tento počítač na telefóne)…"
  if ! adb_wait 180; then
    adb_unauthorized && die "Telefón čaká na povolenie ladenia cez USB – potvrďte na telefóne 'Povoliť'."
    die "Telefón sa cez adb neozval – zapnite Ladenie cez USB (Pre vývojárov) a povoľte tento počítač."
  fi
  local version anim ok=1
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
  # The file the boot animation really opens (frameworks/base/cmds/bootanimation): ro.product.bootanim.file, else
  # bootanimation-dark.zip when the bootloader passes the dark theme (ro.boot.theme=1, Pixel), else bootanimation.zip.
  anim=$(prop ro.product.bootanim.file)
  if [ -z "$anim" ]; then
    if [ "$(prop ro.boot.theme)" = 1 ]; then anim=bootanimation-dark.zip; else anim=bootanimation.zip; fi
  fi
  if adb_ shell "test -f /product/media/$anim && echo ano" | grep -qx ano; then
    report OK "animácia pri štarte" "/product/media/$anim"
  else
    report CHYBA "animácia pri štarte" "/product/media/$anim chýba – pri štarte je logo „android“ z AOSP"
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
  # E2-11a: vendor of the Pixel names the Google RKP server; init.lech.rc clears it (vendor/lech/config/init).
  rkp=$(prop remote_provisioning.hostname)
  if [ -z "$rkp" ]; then
    report OK "atestačné kľúče (RKP)" "bez servera Google"
  else
    report CHYBA "atestačné kľúče (RKP)" "server $rkp (zostava bez init.lech.rc? 11-zostav-gsi.sh a nahrat)"
  fi
  # SELinux hides tcpdump from the adb shell user ("Permission denied"), so only "No such file" means missing.
  if ! adb_ shell 'ls /system/bin/tcpdump' | grep -qi 'no such file'; then
    report INFO "sieťový audit" "tcpdump v telefóne je (11-pixel.sh audit)"
  else
    report INFO "sieťový audit" "tcpdump v telefóne nie je – staršia zostava"
  fi
  echo
  if [ "$ok" -eq 1 ]; then
    echo "LECH OS na Pixeli je v poriadku. Vyskúšajte volanie, SMS, mobilné dáta, Wi-Fi, fotoaparát a Bluetooth"
    echo "a napíšte, čo nefunguje (docs/11, časť 4h)."
  else
    echo "Niektorá kontrola zlyhala – pošlite tento výstup."
    return 1
  fi
}

# Mobile network diagnostics: SIM, radio HAL of the vendor, registration, telephony errors in the log.
# The full output goes to ~/lech-work/pixel/siet-<time>.txt, a summary to the screen.
cmd_siet() {
  need_tools
  adb_wait 60 || die "Telefón sa cez adb neozval – zapnite Ladenie cez USB a povoľte tento počítač (prípadne odpojte a zapojte kábel)."
  mkdir -p "$WORKDIR/pixel"
  local out="$WORKDIR/pixel/siet-$(date +%Y%m%d-%H%M%S).txt" p hal sim reg simnum sms_app
  local ADB_TIMEOUT=45
  echo "Zbieram stav siete (najviac pár minút)…"
  simnum=$(prop gsm.sim.operator.numeric); simnum=${simnum%%,*}
  {
    echo "== Vlastnosti"
    for p in gsm.sim.state gsm.operator.alpha gsm.operator.numeric gsm.network.type gsm.version.ril-impl \
             gsm.version.baseband persist.radio.multisim.config ro.telephony.default_network \
             ro.vendor.build.fingerprint; do
      echo "$p=$(prop "$p")"
    done
    echo "== Funkcie telefónu"; adb_ shell pm list features | grep -iE 'telephony|radio'
    echo "== Služby rádia (HAL vendora)"; adb_ shell service list | grep -iE 'radio|ims|telephony'
    echo "== Registrácia v sieti"
    adb_ shell dumpsys telephony.registry | grep -E 'mServiceState=|mDataConnectionState|mSignalStrength=|mCallState' | head -n 12
    echo "== SIM (isub)"; adb_ shell dumpsys isub | head -n 60
    # IMS (VoLTE, SMS over IMS; E2-11b): the bound IMS service (on the Pixel com.shannon.imsservice), its
    # registration and the carrier settings loaded by CarrierConfig2.
    echo "== IMS služba"; adb_ shell cmd phone ims get-ims-service -d 2>&1 | head -n 3; adb_ shell cmd phone ims get-ims-service -c 2>&1 | head -n 3
    echo "== IMS registrácia"; adb_ shell dumpsys phone | grep -iE 'ImsRegistered|ImsRegistration|isVolteEnabled|isWifiCallingEnabled' | head -n 12
    echo "== Nastavenia operátora (CarrierConfig2)"
    adb_ shell dumpsys carrier_config | grep -E 'carrier_config_version_string|carrier_volte_available_bool|carrier_vt_available_bool|carrier_wfc_ims_available_bool|vonr_enabled_bool|config_ims_package_override_string' | head -n 12
    echo "== Balík nastavení operátora"; adb_ shell dumpsys carrier_config | grep -iE 'carrierconfig2|com\.android\.carrierconfig|CarrierService' | head -n 5
    echo "== APN pre SIM $simnum (TelephonyProvider)"
    adb_ shell "content query --uri content://telephony/carriers --projection name:apn:type:numeric:edited | grep 'numeric=$simnum'" | head -n 20
    echo "== APN spolu"; adb_ shell "content query --uri content://telephony/carriers --projection numeric | grep -c numeric="
    echo "== Log CarrierConfig2"
    adb_ logcat -d | grep -iE 'carrierconfig2|CSettingsDir|CarrierConfigLoader|CarrierServiceImpl|ApnServiceImpl|subIdToCarrierId|TelephonyProvider' | tail -n 30
    echo "== Predvolená aplikácia SMS"; adb_ shell cmd role get-role-holders android.app.role.SMS
    # SMS (E2-11b): sending and receiving in the radio and telephony logs, incl. the SMS centre (SMSC).
    echo "== SMS v logu rádia"
    adb_ logcat -b radio -d | grep -iE 'sms|smsc|SEND_SMS|NEW_SMS|IMS_SEND|CDMA_SEND|ackSms' | tail -n 50
    echo "== SMS v logu telefónu"
    adb_ logcat -d | grep -iE 'SmsDispatcher|SmsController|InboundSms|SmsManager|MessagingApp|SendMessageAction|ProcessSentMessage|smsc' | tail -n 40
    # Mobile data (E2-11b: slow, some sites do not open): addresses, DNS, private DNS and reachability by address
    # and by name, IPv4 and IPv6 (Quad9 and Wikipedia, no Google servers).
    echo "== Dáta: siete"; adb_ shell dumpsys connectivity | grep -E '^ *NetworkAgentInfo\{' | cut -c1-400 | head -n 4
    echo "== Dáta: adresy"; adb_ shell ip -o addr show | grep -vE ' lo |dummy' | awk '{print $2, $3, $4}'
    echo "== Dáta: súkromné DNS"; adb_ shell settings get global private_dns_mode; adb_ shell settings get global private_dns_specifier
    echo "== Dáta: test"
    # -w: deadline – ping of toybox otherwise waits for replies without end (siet hung, 7. 11. 2026).
    echo "ping IPv4 9.9.9.9: $(adb_ shell ping -c 2 -w 8 9.9.9.9 | grep -oE '[0-9]+ received' || echo zlyhal)"
    echo "ping IPv6 2620:fe::fe: $(adb_ shell ping6 -c 2 -w 8 2620:fe::fe | grep -oE '[0-9]+ received' || echo zlyhal)"
    echo "DNS www.wikipedia.org: $(adb_ shell ping -c 1 -w 8 www.wikipedia.org | head -n 1 | grep -oE '\([0-9a-f.:]+\)' || echo zlyhal)"
    echo "== Chyby v logu rádia"; adb_ logcat -b radio -d | grep -E ' [EF] ' | tail -n 60
    echo "== Chyby telefónnej služby"; adb_ logcat -d | grep -iE 'com\.android\.phone|RILJ|IRadio|AndroidRuntime' | grep -E ' [EF] ' | tail -n 40
  } > "$out" 2>&1
  hal=$(grep -cE 'android\.hardware\.radio\.' "$out")
  sim=$(prop gsm.sim.state)
  reg=$(grep -m1 -oE 'mVoiceRegState=[0-9]+\([A-Z_]+\)|mDataRegState=[0-9]+\([A-Z_]+\)' "$out" | head -n 1)
  echo
  echo "Mobilná sieť na Pixeli:"
  printf '  %-26s %s\n' "SIM" "${sim:-?} (READY/LOADED = karta rozpoznaná, ABSENT = bez karty)"
  printf '  %-26s %s\n' "rádio vendora (HAL)" "$hal služieb android.hardware.radio.*"
  printf '  %-26s %s\n' "operátor" "$(prop gsm.operator.alpha) $(prop gsm.operator.numeric)"
  printf '  %-26s %s\n' "typ siete" "$(prop gsm.network.type) (GPRS/EDGE = 2G, UMTS/HSPA = 3G, LTE = 4G, NR = 5G)"
  printf '  %-26s %s\n' "registrácia" "${reg:-?}"
  printf '  %-26s %s\n' "služba IMS" "$(sed -n '/^== IMS služba/,/^== IMS registrácia/p' "$out" | sed '1d;/^== /d' | head -n 1 | cut -c1-90)"
  printf '  %-26s %s\n' "IMS registrovaná" "$(sed -n '/^== IMS registrácia/,/^== Nastavenia operátora/p' "$out" | grep -oiE 'ImsRegistered[^,]*=[ ]*(true|false)' | head -n 1)"
  printf '  %-26s %s\n' "VoLTE podľa operátora" "$(grep -m1 -oE 'carrier_volte_available_bool *= *[a-z]+' "$out" || echo '?')"
  printf '  %-26s %s\n' "nastavenia operátora" "$(grep -m1 -oE 'carrier_config_version_string *= *[^ ]+' "$out" | cut -c1-90 || echo '?')"
  printf '  %-26s %s\n' "APN pre SIM ${simnum:-?}" "$(sed -n '/^== APN pre SIM/,/^== APN spolu/p' "$out" | grep -c 'numeric=') (v telefóne spolu: $(sed -n '/^== APN spolu/{n;p}' "$out"))"
  sms_app=$(sed -n '/^== Predvolená aplikácia SMS/{n;p}' "$out")
  printf '  %-26s %s\n' "SMS v logu" "$(sed -n '/^== SMS v logu rádia/,/^== SMS v logu telefónu/p' "$out" | grep -ciE 'send|deliver|new_sms') záznamov odoslania/prijatia, $(sed -n '/^== SMS v logu rádia/,$p' "$out" | grep -ciE 'fail|error|exception') chýb"
  printf '  %-26s %s\n' "predvolená aplikácia SMS" "${sms_app:-žiadna – Nastavenia → Aplikácie → Predvolené aplikácie → SMS}"
  printf '  %-26s %s\n' "chyby rádia v logu" "$(sed -n '/^== Chyby v logu rádia/,/^== Chyby telefónnej/p' "$out" | grep -cE ' [EF] ')"
  printf '  %-26s %s\n' "súkromné DNS" "$(sed -n '/^== Dáta: súkromné DNS/,/^== Dáta: test/p' "$out" | sed '1d;$d' | tr '\n' ' ')"
  sed -n '/^== Dáta: test/,$p' "$out" | sed '1d' | sed 's/^/  /'
  echo
  echo "Chyby SMS v logu (posledné):"
  sed -n '/^== SMS v logu rádia/,/^== Dáta: siete/p' "$out" | grep -iE 'fail|error|exception|denied' | tail -n 14 | cut -c1-170 | sed 's/^/  /'
  echo
  echo "Úplný výpis: $out"
  echo "Pošlite snímku tohto súhrnu; ak treba, aj: tail -n 60 $out"
}

# One SMS attempt in the log (E2-11b: the messaging app keeps asking to become the default SMS app although it
# holds the SMS role, received messages make no sound): the app, its permissions and app ops, and the log lines.
cmd_sms() {
  local renew=0
  case "${1:-}" in --rola) renew=1 ;; "") ;; *) die "Neznámy parameter: $1 (použitie: 11-pixel.sh sms [--rola])" ;; esac
  need_tools
  adb_wait 60 || die "Telefón sa cez adb neozval – zapnite Ladenie cez USB a povoľte tento počítač (prípadne odpojte a zapojte kábel)."
  mkdir -p "$WORKDIR/pixel"
  local out="$WORKDIR/pixel/sms-$(date +%Y%m%d-%H%M%S).txt" app
  local ADB_TIMEOUT=45
  app=$(adb_ shell cmd role get-role-holders android.app.role.SMS | head -n 1)
  if [ "$renew" -eq 1 ] && [ -n "$app" ]; then
    echo "Odoberám a znova prideľujem rolu SMS ($app)…"
    adb_ shell cmd role remove-role-holder android.app.role.SMS "$app" >/dev/null
    sleep 2
    adb_ shell cmd role add-role-holder android.app.role.SMS "$app" >/dev/null
    sleep 3
  fi
  local pkg=${app:-com.android.messaging} dump p state
  echo "Zostava v telefóne: $(prop ro.build.display.id), $(prop ro.build.date)"
  echo "Predvolená aplikácia SMS podľa systému: ${app:-žiadna}"
  dump=$(adb_ shell dumpsys package "$pkg")
  for p in RECEIVE_WAP_PUSH READ_CELL_BROADCASTS; do
    if ! printf '%s\n' "$dump" | sed -n '/requested permissions:/,/install permissions:\|runtime permissions:/p' | grep -q "android.permission.$p"; then
      state="aplikácia si ho NEŽIADA (záplata nie je v zostave?)"
    else
      state=$(printf '%s\n' "$dump" | grep -m1 "android.permission.$p: granted=" | grep -oE 'granted=(true|false)')
      state="žiada si ho, ${state:-granted=?}"
    fi
    printf '  %-24s %s; app op (UID): %s\n' "$p" "$state" \
      "$(adb_ shell appops get --uid "$pkg" "$p" | grep -oE '(allow|ignore|deny|default|foreground)' | head -n 1)"
  done
  adb_ logcat -c >/dev/null; adb_ logcat -b radio -c >/dev/null
  echo
  echo "Teraz v telefóne v aplikácii SMS a MMS pošlite SMS (ak sa pýta na predvolenú aplikáciu, potvrďte to)."
  echo "Môžete si nechať poslať aj SMS. Čakám 60 sekúnd…"
  sleep 60
  {
    echo "== Rola SMS"; adb_ shell cmd role get-role-holders android.app.role.SMS
    echo "== Predvolená SMS podľa telefónnej služby"; adb_ shell settings get secure sms_default_application
    echo "== Balík ${app:-com.android.messaging}"
    adb_ shell dumpsys package "${app:-com.android.messaging}" | grep -E 'versionName|targetSdk|SMS|MMS|NOTIFICATIONS|granted=false' | head -n 40
    echo "== App ops"; adb_ shell cmd appops get "${app:-com.android.messaging}" | grep -iE 'sms|mms|notification' | head -n 20
    echo "== Log aplikácie a telefónu"
    adb_ logcat -d | grep -iE 'messaging|sms|RoleManager|RoleService|DefaultSms|SmsApplication|AndroidRuntime' | tail -n 120
    echo "== Log rádia"; adb_ logcat -b radio -d | grep -iE 'sms|smsc' | tail -n 40
  } > "$out" 2>&1
  echo
  echo "Výťah (chyby a upozornenia):"
  sed -n '/^== Log aplikácie a telefónu/,$p' "$out" | grep -E ' [EWF] |Exception|denied|not default|default' | tail -n 25 | cut -c1-170 | sed 's/^/  /'
  echo
  echo "Úplný výpis: $out"
  echo "Pošlite snímku tohto výťahu."
}

# Network audit in the phone itself: tcpdump on all interfaces (Wi-Fi and mobile network, which does not pass
# the notebook), DNS queries by app, evaluated by analyzuj_audit.py --siete against spec/povolene-spojenia.txt.
cmd_audit() {
  local minutes=15 dir remote=/data/local/tmp/lech-audit.pcap m finish_only=0
  while [ $# -gt 0 ]; do
    case "$1" in
      --minuty) minutes=${2:?chýba počet minút}; shift 2 ;;
      --dokoncit) finish_only=1; shift ;;
      --vyhodnotit) finish_only=2; shift ;;
      *) die "Neznámy parameter auditu: $1 (použitie: 11-pixel.sh audit [--minuty N] [--dokoncit] [--vyhodnotit])" ;;
    esac
  done
  case "$minutes" in ''|*[!0-9]*) die "--minuty očakáva celé číslo." ;; esac
  command -v tshark >/dev/null 2>&1 || die "Chýba tshark – nainštalujte ho: tools/etapa2/01-priprava-notebooku.sh"
  if [ "$finish_only" -eq 2 ]; then
    dir=$(ls -td "$WORKDIR"/pixel/audit-* 2>/dev/null | head -n 1)
    [ -f "${dir:-/nonexistent}/zachytenie.pcap" ] || die "Žiadny stiahnutý záznam auditu v $WORKDIR/pixel."
    echo "Vyhodnocujem znova $dir"
    audit_report "$dir"
    return
  fi
  need_tools
  if [ "$finish_only" -eq 1 ]; then
    dir=$(ls -td "$WORKDIR"/pixel/audit-* 2>/dev/null | head -n 1)
    [ -n "$dir" ] || die "Žiadny začatý audit v $WORKDIR/pixel – spustite: tools/etapa2/11-pixel.sh audit"
    echo "Dokončujem audit $dir"
    audit_finish "$dir"
    return
  fi
  adb_wait 60 || die "Telefón sa cez adb neozval – zapnite Ladenie cez USB a povoľte tento počítač (prípadne odpojte a zapojte kábel)."
  [ -n "$(prop ro.lech.version)" ] || die "V telefóne nebeží LECH OS – audit je pre LECH OS (11-pixel.sh nahrat)."
  audit_root
  adb_ shell 'command -v tcpdump' | grep -q tcpdump || \
    die "V telefóne nie je tcpdump – nahrajte novšiu zostavu: tools/etapa2/11-zostav-gsi.sh a 11-pixel.sh nahrat"

  dir="$WORKDIR/pixel/audit-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$dir" || die "Priečinok $dir sa nedá vytvoriť."
  adb_ shell ip -o addr show > "$dir/adresy-zaciatok.txt"
  adb_ shell ip -o link show > "$dir/rozhrania-telefonu.txt"
  adb_ shell dumpsys connectivity > "$dir/connectivity-zaciatok.txt"
  # setsid + redirects: tcpdump keeps running after this adb shell ends; -U writes every packet at once.
  # LINUX_SLL2 records the interface of every packet (Wi-Fi or mobile network); older tcpdump: without it.
  local linktype
  for linktype in "-y LINUX_SLL2" ""; do
    adb_ shell "rm -f $remote; setsid tcpdump -i any $linktype -s 0 -U -w $remote 'not (net 127.0.0.0/8 or ip6 host ::1)' \
      </dev/null >/data/local/tmp/lech-audit.log 2>&1 &" >/dev/null
    sleep 3
    adb_ shell "pgrep -f lech-audit.pcap" | grep -q '[0-9]' && break
  done
  if ! adb_ shell "pgrep -f lech-audit.pcap" | grep -q '[0-9]'; then
    adb_ shell cat /data/local/tmp/lech-audit.log | tail -n 5
    die "tcpdump sa v telefóne nespustil."
  fi
  cat <<EOF

Zachytávam sieťovú prevádzku telefónu $minutes min (Wi-Fi aj mobilná sieť). Teraz:
  - zapnite Wi-Fi alebo mobilné dáta (pri prvom audite po nahratí: pripojte Wi-Fi až teraz),
  - telefón potom nechajte ležať, nič nespúšťajte – audit meria, čo robí sám.
Skončiť skôr: Enter.
EOF
  for (( m = minutes; m > 0; m-- )); do
    printf '\r  zostáva %3d min ' "$m"
    if [ -t 0 ]; then read -r -t 60 && break; else sleep 60; fi
  done
  printf '\r  záznam končí…    \n'
  audit_finish "$dir"
}

# adb as root (the query log and tcpdump need it); after a reconnect adbd normally stays root.
audit_root() {
  if [ "$(adb_ shell id -u | head -n 1)" != 0 ]; then
    adb_ root >/dev/null
    sleep 4
    wait_for 60 adb_ready || die "Telefón sa po adb root neozval."
  fi
  [ "$(adb_ shell id -u | head -n 1)" = 0 ] || die "adb root nefunguje – audit potrebuje ladiacu zostavu (userdebug)."
}

# Stop the capture in the phone, fetch it with the DNS log and evaluate. Also on its own (audit --dokoncit):
# tcpdump keeps running in the phone when the USB connection drops during the audit (6. 11. 2026).
audit_finish() {   # audit directory
  local dir=$1 remote=/data/local/tmp/lech-audit.pcap running
  if ! adb_ready; then
    echo "Telefón sa cez adb neozval – spojenie USB sa asi prerušilo. Odpojte a znova pripojte kábel"
    echo "(záznam v telefóne beží ďalej). Čakám 3 minúty…"
    wait_for 180 adb_ready || die "Telefón sa neozval. Po pripojení kábla audit dokončite: tools/etapa2/11-pixel.sh audit --dokoncit"
  fi
  audit_root
  adb_ shell "ls $remote" | grep -q 'lech-audit.pcap$' || \
    die "Záznam v telefóne nie je (telefón sa medzitým reštartoval?). Spustite audit znova: tools/etapa2/11-pixel.sh audit"
  adb_ shell "pkill -INT -f lech-audit.pcap" >/dev/null
  sleep 3
  running=$(adb_ shell "pgrep -f lech-audit.pcap" | grep -c '[0-9]')
  [ "$running" -eq 0 ] || adb_ shell "pkill -KILL -f lech-audit.pcap" >/dev/null
  adb_ shell ip -o addr show > "$dir/adresy-koniec.txt"
  adb_ shell ip -o link show >> "$dir/rozhrania-telefonu.txt"
  adb_ shell dumpsys connectivity > "$dir/connectivity-koniec.txt"

  "${ADB[@]}" exec-out cat "$remote" > "$dir/zachytenie.pcap"
  adb_ shell dumpsys dnsresolver querylog > "$dir/dns-dopyty.txt"
  adb_ shell pm list packages -U > "$dir/balicky.txt"
  adb_ shell "rm -f $remote /data/local/tmp/lech-audit.log" >/dev/null
  [ "$(stat -c %s "$dir/zachytenie.pcap" 2>/dev/null || echo 0)" -gt 24 ] || die "Záznam z telefónu je prázdny ($dir)."
  audit_report "$dir"
}

# Network of the phone by interface name.
iface_label() {
  case "$1" in
    wlan*) echo "Wi-Fi telefónu" ;;
    rmnet*|ccmni*|seth*|clat*|v4-*|r_rmnet*) echo "mobilná sieť telefónu" ;;
    tun*) echo "VPN telefónu" ;;
    *) echo "telefón ($1)" ;;
  esac
}

# Evaluate an audit directory (also again later: audit --vyhodnotit). The phone's own traffic are the outgoing
# packets of the capture; interfaces (index from `ip -o link/addr`) and addresses only label its networks.
audit_report() {   # audit directory
  local dir=$1 index iface line
  cat "$dir"/adresy-*.txt "$dir"/rozhrania-telefonu.txt 2>/dev/null | while read -r index iface line; do
    index=${index%:}; iface=${iface%:}; iface=${iface%@*}
    case "$index" in ''|*[!0-9]*) continue ;; esac
    printf '%s\t%s\n' "$index" "$(iface_label "$iface")"
  done | sort -u > "$dir/rozhrania.txt"
  cat "$dir"/adresy-*.txt 2>/dev/null | awk '
    ($3 == "inet" || $3 == "inet6") && / scope global / {
      iface = $2; addr = $4; sub(/\/.*/, "", addr); print iface "\t" addr (index(addr, ":") ? "/128" : "/32")
    }' | sort -u | while IFS=$'\t' read -r iface line; do
    printf '%s\t%s\n' "$(iface_label "$iface")" "$line"
  done > "$dir/siete.txt"

  # DNS servers the networks gave the phone (LinkProperties "DnsAddresses: [ /84.245.112.80,/... ]").
  # Older audits have no connectivity dump: a dns-siete.txt written by hand is kept.
  if ls "$dir"/connectivity-*.txt >/dev/null 2>&1; then
    cat "$dir"/connectivity-*.txt | grep -oE 'DnsAddresses: \[[^]]*\]' | grep -oE '/[0-9a-fA-F:.]+' \
      | tr -d / | sort -u > "$dir/dns-siete.txt"
  fi
  echo
  python3 -B "$REPO_DIR/tools/etapa2/analyzuj_audit.py" "$dir/zachytenie.pcap" --out "$dir/sprava.md" --ptr \
    --dns-siete "$dir/dns-siete.txt" \
    --allowlist "$REPO_DIR/spec/povolene-spojenia.txt" --zariadenie --rozhrania "$dir/rozhrania.txt" \
    --siete "$dir/siete.txt" --dns-log "$dir/dns-dopyty.txt" --packages "$dir/balicky.txt" \
    --title "Sieťový audit LECH OS na Pixeli 9 – $(basename "$dir" | sed 's/^audit-//')" || die "Vyhodnotenie zlyhalo ($dir)."
  echo
  echo "Domény, na ktoré sa telefón pýtal:"
  sed -n '/^## Domény/,/^## /p' "$dir/sprava.md" | grep -E '^\| `' | sed 's/^/  /'
  if [ "$WINDOWS" -eq 1 ]; then
    mkdir -p /mnt/c/lech/pixel && cp "$dir/sprava.md" "/mnt/c/lech/pixel/$(basename "$dir").md" && \
      echo "Správa aj vo Windows: C:\\lech\\pixel\\$(basename "$dir").md"
  fi
  echo "Správa: $dir/sprava.md (záznam prevádzky: $dir/zachytenie.pcap)"
}

# Make a slot active and start it (after the bootloader fell back to the Google system in the other slot).
cmd_slot() {
  local want=${1:-}
  case "$want" in a|b) ;; *) die "Použitie: 11-pixel.sh slot a|b (LECH OS je v slote z ~/lech-work/pixel/nahratia.txt)" ;; esac
  need_tools
  if ! fastboot_ready; then
    echo "Telefón nie je v bootloaderi. Vypnite ho a zapnite so stlačeným tlačidlom Zníženie hlasitosti. Čakám 3 minúty…"
    wait_for 180 fastboot_ready || { windows_usb; driver_hint; die "Telefón sa v bootloaderi neukázal."; }
  fi
  echo "Aktívny slot teraz: $(fb_var current-slot); nastavujem $want a spúšťam…"
  fb --set-active="$want" | grep -E 'OKAY|FAILED' | sed 's/^/  /'
  fb reboot >/dev/null
  wait_for 40 fastboot_gone || die "Telefón sa nereštartoval – na telefóne vyberte 'Start' a potvrďte tlačidlom Napájanie."
  echo "Telefón štartuje zo slotu $want. Ak sa znova objaví systém Google, LECH OS sa v tomto slote nespustí – pošlite"
  echo "výpis: tools/etapa2/11-pixel.sh stav (v bootloaderi)."
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
  ovladac) need_tools; cmd_ovladac ;;
  stav) cmd_stav ;;
  odomknut) cmd_odomknut ;;
  nahrat) cmd_nahrat ;;
  nahrat-uplne) shift; cmd_nahrat_uplne "$@" ;;
  dokoncit) cmd_dokoncit ;;
  kontrola) cmd_kontrola ;;
  siet) cmd_siet ;;
  sms) shift; cmd_sms "$@" ;;
  audit) shift; cmd_audit "$@" ;;
  slot) shift; cmd_slot "$@" ;;
  spat) cmd_spat ;;
  -h|--help|"") sed -n '2,27p' "$0" ;;
  *) die "Neznámy príkaz: $1 (pomoc: --help)" ;;
esac
