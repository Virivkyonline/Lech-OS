#!/usr/bin/env bash
# Compatibility test of apps from F-Droid on the running LECH OS phone (task E2-10): downloads every app
# from spec/testovacie-aplikacie.txt from the official F-Droid repository (SHA-256 checked against the
# index), installs it, starts it and checks that it runs without crashing; takes screenshots.
# The report goes into the repository (kompatibilita/); APKs and screenshots stay in $LECH_WORK.
#
# Usage: tools/etapa2/10-test-aplikacii.sh [--cakanie S] [--zoznam FILE] [--balik PKG ...]
#   --cakanie S    seconds to wait after starting each app (default 8)
#   --zoznam FILE  list of apps (default spec/testovacie-aplikacie.txt)
#   --balik PKG    test only this package from the list (can be repeated)
# The phone must be running: tools/etapa2/06-zostav-lech.sh --spustit

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"

WAIT=8
LIST="$REPO_DIR/spec/testovacie-aplikacie.txt"
WORK="${LECH_WORK:-$HOME/lech-work}/kompatibilita"
STAMP=$(date +%Y%m%d-%H%M%S)
PACKAGES=()

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --cakanie) WAIT=${2:?chýba počet sekúnd}; shift 2 ;;
    --zoznam) LIST=${2:?chýba súbor}; shift 2 ;;
    --balik) PACKAGES+=(--package "${2:?chýba balík}"); shift 2 ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done
case "$WAIT" in ''|*[!0-9]*) die "--cakanie očakáva celé číslo." ;; esac
[ -f "$LIST" ] || die "Zoznam aplikácií $LIST neexistuje."

ADB=$(lech_adb) || die "adb sa nenašiel."
timeout 60 "$ADB" wait-for-device >/dev/null 2>&1 || die "Telefón nebeží – spustite tools/etapa2/06-zostav-lech.sh --spustit"
[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] || die "Telefón ešte nedokončil štart."

mkdir -p "$WORK"
python3 -B "$SCRIPT_DIR/test_aplikacii.py" --adb "$ADB" --list "$LIST" --wait "$WAIT" ${PACKAGES[@]+"${PACKAGES[@]}"} \
  --report "$REPO_DIR/kompatibilita/$STAMP-fdroid" --work "$WORK"
