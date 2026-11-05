#!/usr/bin/env bash
# Builds the first LECH OS product for Cuttlefish (tasks E2-04 and E2-05): the phone LECH-PHONE with
# LECH OS version and boot animation. The product definition lives in vendor/lech of this repository;
# the script copies it into the AOSP tree ($AOSP_DIR/vendor/lech) and builds the lunch target
# lech_cf_x86_64_only_phone-<release>-userdebug with 05-zostav-aosp.sh.
# The build continues from the AOSP build (same device), but the first LECH build runs the Soong
# analysis again and rebuilds the images. With --spustit it starts the phone and checks its identity.
#
# Usage: tools/etapa2/06-zostav-lech.sh [--ulohy N] [--spustit] [--len-skopirovat]
#   --ulohy N          parallel build jobs (default 8; more needs more RAM)
#   --spustit          start the LECH OS phone after the build and check its identity
#   --len-skopirovat   only copy vendor/lech into the AOSP tree, do not build
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_RELEASE, LECH_VARIANT

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
PRODUCT=lech_cf_x86_64_only_phone
BASE_PRODUCT=aosp_cf_x86_64_only_phone
SRC="$REPO_DIR/vendor/lech"
DEST="$AOSP_DIR/vendor/lech"
PRODUCT_MK="$SRC/products/$PRODUCT.mk"
BASE_MK="$AOSP_DIR/device/google/cuttlefish/vsoc_x86_64_only/phone/aosp_cf.mk"
MIN_FREE_GB=40      # extra space for the second product next to the existing out/ (estimate)

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

BUILD_ARGS=()
START=0
COPY_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --ulohy) BUILD_ARGS+=(--ulohy "${2:?chýba počet úloh}"); shift 2 ;;
    --spustit) START=1; BUILD_ARGS+=(--spustit); shift ;;
    --len-skopirovat) COPY_ONLY=1; shift ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -f "$SRC/AndroidProducts.mk" ] || die "V repozitári chýba $SRC – spustite skript z repozitára Lech-OS (git pull)."
[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -f "$BASE_MK" ] || die "Chýba $BASE_MK – produkt LECH z neho dedí. Zmenila sa štruktúra Cuttlefish v novej verzii AOSP?"
mkdir -p "$WORKDIR"

# The Cuttlefish phone sets some build modes only for its own product name. The LECH product mirrors
# them; warn when a new AOSP version adds a variable there that the LECH product does not set.
block_vars() {   # variables assigned inside "ifeq ($(TARGET_PRODUCT),<product>)" blocks of a makefile
  awk -v p="$2" '
    $0 ~ "^ifeq \\(\\$\\(TARGET_PRODUCT\\)," p "\\)" { inside = 1; next }
    inside && /^endif/ { inside = 0 }
    inside && /^[A-Z_]+ *[:+?]?=/ { sub(/ *[:+?]?=.*/, ""); print }' "$1" | sort -u
}
missing=$(comm -23 <(block_vars "$BASE_MK" "$BASE_PRODUCT") <(block_vars "$PRODUCT_MK" "$PRODUCT") | tr '\n' ' ')
if [ -n "${missing// /}" ]; then
  echo "UPOZORNENIE: $BASE_MK nastavuje pre $BASE_PRODUCT aj: $missing"
  echo "  Produkt LECH ich nenastavuje – zostavenie môže prebehnúť inak ako pri čistom AOSP."
  echo "  Doplňte ich do vendor/lech/products/$PRODUCT.mk (blok ifeq na konci súboru)."
fi

if [ -d "$AOSP_DIR/out" ]; then
  free_gb=$(df -Pk "$AOSP_DIR" | awk 'NR==2 {print int($4/1024/1024)}')
  [ "${free_gb:-0}" -ge "$MIN_FREE_GB" ] || \
    die "Na zostavenie produktu LECH treba aspoň ${MIN_FREE_GB} GB voľného miesta, voľných je ${free_gb:-0} GB."
fi

# Copy instead of a symbolic link: the build's file finder does not follow links into other directories.
echo "Kopírujem produkt LECH: $SRC → $DEST"
mkdir -p "$AOSP_DIR/vendor"
[ -L "$DEST" ] && rm -f "$DEST"
if command -v rsync >/dev/null 2>&1; then
  rsync -a --delete "$SRC/" "$DEST/" || die "Kopírovanie do $DEST zlyhalo."
else
  rm -rf -- "$DEST" && cp -a "$SRC" "$DEST" || die "Kopírovanie do $DEST zlyhalo."
fi
version=$(sed -n 's/^LECH_VERSION *:= *//p' "$SRC/config/version.mk")
echo "LECH OS ${version:-?} (commit $(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo '?'))"

# Record the exact AOSP revisions this build is based on (the seed of lech-manifest, task E2-04).
REPO_BIN=$(command -v repo || echo "$HOME/bin/repo")
if [ -x "$REPO_BIN" ] && (cd "$AOSP_DIR" && "$REPO_BIN" manifest -r -o "$WORKDIR/aosp-manifest.xml") >/dev/null 2>&1; then
  echo "Presné verzie zdrojových kódov AOSP: $WORKDIR/aosp-manifest.xml"
fi

[ "$COPY_ONLY" -eq 1 ] && { echo "Hotovo (len kopírovanie)."; exit 0; }

echo
echo "Zostavujem LECH OS ($PRODUCT). Prvé zostavenie produktu LECH trvá približne 1 až 3 hodiny,"
echo "ďalšie zmeny v identite už len minúty až desiatky minút."
LECH_PRODUCT=$PRODUCT LECH_RERUN=tools/etapa2/06-zostav-lech.sh "$SCRIPT_DIR/05-zostav-aosp.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"}
status=$?
[ "$status" -eq 0 ] || exit "$status"
[ "$START" -eq 1 ] || { echo "Spustenie telefónu: tools/etapa2/06-zostav-lech.sh --spustit (zostavenie sa už nezopakuje)."; exit 0; }

# Identity check on the running phone.
ADB="$AOSP_DIR/out/host/linux-x86/bin/adb"
[ -x "$ADB" ] || ADB=$(command -v adb || true)
[ -n "$ADB" ] || { echo "adb nie je k dispozícii – identitu overte v Nastaveniach telefónu."; exit 0; }
echo
echo "Overujem identitu LECH OS v bežiacom telefóne…"
timeout 180 "$ADB" wait-for-device || { echo "Telefón sa cez adb neozval – identitu overte v Nastaveniach."; exit 0; }
prop() { "$ADB" shell getprop "$1" 2>/dev/null | tr -d '\r'; }
ok=1
report() {   # OK|CHYBA, what, detail
  printf '  %-7s %s%*s %s\n' "$1" "$2" $(( 22 - ${#2} )) "" "$3"   # ${#2} counts characters, not bytes
  [ "$1" = OK ] || ok=0
}
expect_prefix() {   # property, expected value or prefix ending with *
  local value
  value=$(prop "$1")
  # shellcheck disable=SC2254
  case "$value" in
    $2) report OK "$1" "$value" ;;
    *) report CHYBA "$1" "${value:-<prázdne>} (očakávané ${2%\*})" ;;
  esac
}
expect_prefix ro.product.model "LECH-PHONE"
expect_prefix ro.lech.version "$version"
expect_prefix ro.build.display.id "LECH-OS-$version*"
if "$ADB" shell test -f /product/media/bootanimation.zip 2>/dev/null; then
  report OK "animácia pri štarte" /product/media/bootanimation.zip
else
  report CHYBA "animácia pri štarte" "/product/media/bootanimation.zip chýba"
fi
echo
if [ "$ok" -eq 1 ]; then
  echo "Identita LECH OS je v poriadku. V telefóne: Settings → About phone."
else
  echo "Identita LECH OS nie je úplná – pošlite tento výstup."
  exit 1
fi
