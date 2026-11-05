#!/usr/bin/env bash
# Builds LECH OS as a generic system image (GSI) for 64-bit ARM phones (task E2-11a, first target Pixel 9)
# and copies system.img and vbmeta.img for flashing with 11-pixel.sh (in WSL to C:\lech\pixel).
# The AOSP tree is prepared like for Cuttlefish (06-zostav-lech.sh --len-skopirovat: vendor/lech, keys,
# patches), then 05-zostav-aosp.sh builds lech_gsi_arm64-<release>-userdebug. The target is kept in
# ~/lech-work/lunch-target-gsi, so the Cuttlefish build, the audit and the nightly check stay unchanged.
# 64-bit only (board vendor/lech/board/lech_arm64). The first build compiles the whole system for ARM:
# several hours and about 100 GB of disk.
# After a GSI build the next Cuttlefish build runs the Soong analysis again (other product).
#
# Usage: tools/etapa2/11-zostav-gsi.sh [--ulohy N]
#   --ulohy N   parallel build jobs (default 8; more needs more RAM)
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_PIXEL_DIR (where the images
#   are copied; default /mnt/c/lech/pixel in WSL, ~/lech-work/pixel otherwise)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
PRODUCT=lech_gsi_arm64
BASE_PRODUCT=aosp_arm64
BASE_MK="$AOSP_DIR/build/make/target/product/aosp_arm64.mk"
PRODUCT_MK="$REPO_DIR/vendor/lech/products/$PRODUCT.mk"
PRODUCT_OUT="$AOSP_DIR/out/target/product/lech_arm64"   # board vendor/lech/board/lech_arm64 (64-bit only)
if lech_in_wsl && [ -d /mnt/c ]; then PIXEL_DIR=${LECH_PIXEL_DIR:-/mnt/c/lech/pixel}; else PIXEL_DIR=${LECH_PIXEL_DIR:-$WORKDIR/pixel}; fi
MIN_FREE_FIRST_GB=100   # estimate for the first ARM build next to the Cuttlefish one (other architecture)
MIN_FREE_GB=40
# Set only for aosp_arm64 itself and left out on purpose (vendor/lech/products/lech_gsi_arm64.mk).
NOT_MIRRORED="PRODUCT_SOONG_DEFINED_SYSTEM_IMAGE USE_SOONG_DEFINED_SYSTEM_IMAGE"

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

BUILD_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --ulohy) BUILD_ARGS+=(--ulohy "${2:?chýba počet úloh}"); shift 2 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -f "$BASE_MK" ] || die "Chýba $BASE_MK – produkt LECH pre Pixel z neho dedí. Zmenila sa štruktúra AOSP?"
[ -f "$PRODUCT_MK" ] || die "Chýba $PRODUCT_MK – aktualizujte repozitár (git pull)."

# The nightly check and this build must not use the same out/ directory at the same time.
mkdir -p "$WORKDIR/ci"
exec 9> "$WORKDIR/ci/.zamok"
flock -n 9 || die "Práve beží nočná kontrola (alebo iné zostavenie). Počkajte, kým skončí: tail -f $WORKDIR/ci/beh.log"

# The LECH product is composed like aosp_arm64.mk (64-bit only: core_64_bit_only.mk instead of core_64_bit.mk);
# warn when a new AOSP version adds an inherited makefile or a product block variable there.
inherits() { grep -E '^ *\$\(call inherit-product' "$1" | tr -d ' \t' | sed 's/core_64_bit_only\.mk/core_64_bit.mk/' | sort -u; }
missing=$( { comm -23 <(lech_block_vars "$BASE_MK" "$BASE_PRODUCT") <(lech_block_vars "$PRODUCT_MK" "$PRODUCT") \
             | grep -vxF -f <(tr ' ' '\n' <<< "$NOT_MIRRORED"); comm -23 <(inherits "$BASE_MK") <(inherits "$PRODUCT_MK"); } \
           | sort -u | tr '\n' ' ')
if [ -n "${missing// /}" ]; then
  echo "UPOZORNENIE: $BASE_MK má aj: $missing"
  echo "  Produkt LECH to nemá – doplňte to do $PRODUCT_MK, inak sa systém zostaví inak ako GSI v AOSP."
fi

if [ -d "$PRODUCT_OUT" ]; then need=$MIN_FREE_GB; else need=$MIN_FREE_FIRST_GB; fi
free_gb=$(lech_free_gb "$AOSP_DIR")
[ "${free_gb:-0}" -ge "$need" ] || \
  die "Na zostavenie LECH OS pre ARM treba aspoň ${need} GB voľného miesta, voľných je ${free_gb:-0} GB."

# vendor/lech, keys and patches: the same preparation as for Cuttlefish.
"$SCRIPT_DIR/06-zostav-lech.sh" --len-skopirovat || exit 1

if lech_phone_running; then
  echo "Vypínam virtuálny telefón, aby mala analýza zostavy viac pamäte."
  lech_reset_phones
fi
mem_gb=$(lech_free_mem_gb)
if [ "${mem_gb:-0}" -lt 25 ]; then
  echo "UPOZORNENIE: voľnej pamäte je len ${mem_gb} GB (RAM + swap); analýza zostavy potrebuje približne 25 GB."
fi

echo
if [ -d "$PRODUCT_OUT" ]; then
  echo "Zostavujem LECH OS pre ARM64 ($PRODUCT), pokračujem v predchádzajúcom zostavení."
else
  echo "Zostavujem LECH OS pre ARM64 ($PRODUCT) prvýkrát: celý systém sa prekladá pre ARM – niekoľko hodín."
  echo "Notebook nechajte zapnutý a v zásuvke; prerušené zostavenie pokračuje po novom spustení tohto skriptu."
fi
LECH_PRODUCT=$PRODUCT LECH_TARGET_SAVE="$WORKDIR/lunch-target-gsi" LECH_RERUN=tools/etapa2/11-zostav-gsi.sh \
  "$SCRIPT_DIR/05-zostav-aosp.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"}
status=$?
[ "$status" -eq 0 ] || exit "$status"

for f in system.img vbmeta.img; do
  [ -f "$PRODUCT_OUT/$f" ] || die "Zostavenie skončilo, ale chýba $PRODUCT_OUT/$f."
done
version=${LECH_VERSION:-$(sed -n 's/^LECH_VERSION *[:?]= *//p' "$REPO_DIR/vendor/lech/config/version.mk")}
echo
echo "Kopírujem obrazy pre Pixel do $PIXEL_DIR…"
mkdir -p "$PIXEL_DIR" || die "Priečinok $PIXEL_DIR sa nedá vytvoriť."
for f in system.img vbmeta.img; do
  cp "$PRODUCT_OUT/$f" "$PIXEL_DIR/$f.tmp" && mv -f "$PIXEL_DIR/$f.tmp" "$PIXEL_DIR/$f" || die "Kopírovanie $f zlyhalo."
done
{
  echo "LECH OS $version - GSI pre ARM64 ($PRODUCT-userdebug)"
  echo "zostavené: $(date '+%d. %m. %Y %H:%M'), commit $(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo '?')"
  echo "zostava: $(sed -n 's/^ro.system.build.fingerprint=//p' "$PRODUCT_OUT/system/build.prop" 2>/dev/null)"
  (cd "$PIXEL_DIR" && sha256sum system.img vbmeta.img)
} > "$PIXEL_DIR/info.txt"
ls -lh "$PIXEL_DIR" | sed 1d
echo
echo "Hotovo. Nahratie do Pixelu: tools/etapa2/11-pixel.sh nahrat (postup: docs/11, časť 4h)."
