#!/usr/bin/env bash
# Builds LECH OS for the Pixel 9 (tokay) – the full build with own vendor, boot and vbmeta images (task E2-11b) –
# and packs the images for flashing with 11-pixel.sh nahrat-uplne (in WSL to C:\lech\pixel-uplne).
# Prerequisite: tools/etapa2/12-priprav-pixel.sh (GrapheneOS device support, vendor/google_devices/tokay).
# The target lech_tokay-<release>-userdebug is kept in ~/lech-work/lunch-target-pixel. The development build
# uses the generic ARMv8-A target of the LECH OS GSI (LECH_PIXEL_GENERIC_CPU), so most of the system compiled
# for the GSI is reused; the first build still runs a new Soong analysis and builds the Pixel modules.
#
# Usage: tools/etapa2/12-zostav-pixel.sh [--ulohy N]
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_PIXEL_FULL_DIR (where the image
#   package goes; default /mnt/c/lech/pixel-uplne in WSL, ~/lech-work/pixel-uplne otherwise)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
PRODUCT=lech_tokay
DEVICE=tokay
PRODUCT_OUT="$AOSP_DIR/out/target/product/$DEVICE"
if lech_in_wsl && [ -d /mnt/c ]; then OUT_DIR_IMG=${LECH_PIXEL_FULL_DIR:-/mnt/c/lech/pixel-uplne}; else OUT_DIR_IMG=${LECH_PIXEL_FULL_DIR:-$WORKDIR/pixel-uplne}; fi
MIN_FREE_FIRST_GB=60   # estimate: Pixel modules and images next to the GSI build (same ARMv8-A target)
MIN_FREE_GB=30
# Partitions of the Pixel 9 that the build makes (BoardConfig of the vendor module); super_empty for fastboot update.
IMAGES=(boot init_boot vendor_boot vendor_kernel_boot dtbo pvmfw vbmeta vbmeta_system vbmeta_vendor
        system system_ext product vendor system_dlkm vendor_dlkm super_empty)
ORIG_ARGS=("$@")

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

BUILD_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --ulohy) BUILD_ARGS+=(--ulohy "${2:?chýba počet úloh}"); shift 2 ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -f "$AOSP_DIR/vendor/google_devices/$DEVICE/$DEVICE.mk" ] || \
  die "Chýba vendor/google_devices/$DEVICE – najprv pripravte podporu Pixelu: tools/etapa2/12-priprav-pixel.sh"

if [ -z "${LECH_BUILD_LOCK_HELD:-}" ]; then
  lech_with_build_lock "$0" ${ORIG_ARGS[@]+"${ORIG_ARGS[@]}"}
  status=$?
  if [ "$status" -eq 75 ]; then
    echo
    lech_build_lock_report
    die "Zostavenie sa nespustilo – v strome AOSP práve beží iné zostavenie."
  fi
  exit "$status"
fi

if [ -d "$PRODUCT_OUT" ]; then need=$MIN_FREE_GB; else need=$MIN_FREE_FIRST_GB; fi
free_gb=$(lech_free_gb "$AOSP_DIR")
[ "${free_gb:-0}" -ge "$need" ] || \
  die "Na zostavenie LECH OS pre Pixel 9 treba aspoň ${need} GB voľného miesta, voľných je ${free_gb:-0} GB."

# vendor/lech, keys and patches (also those for vendor/adevtool): the same preparation as for Cuttlefish.
"$SCRIPT_DIR/06-zostav-lech.sh" --len-skopirovat || exit 1

# The vendor module records the adevtool revision it was generated with (only a warning in the build, --noVerify).
# A different revision means the LECH patches of adevtool changed: generate the module again first.
gen_rev=$(sed -n 's/^ifneq (\$(shell git -C [^ ]* rev-parse HEAD),\([0-9a-f]\{40\}\))$/\1/p' \
  "$AOSP_DIR/vendor/google_devices/$DEVICE/adevtool-version-check.mk" 2>/dev/null)
cur_rev=$(git -C "$AOSP_DIR/vendor/adevtool" rev-parse HEAD 2>/dev/null)
if [ -n "$gen_rev" ] && [ "$gen_rev" != "$cur_rev" ]; then
  die "Podporu Pixelu (vendor/google_devices/$DEVICE) vytvorila iná verzia adevtool, než je teraz (zmenili sa úpravy LECH OS). Najprv: tools/etapa2/12-priprav-pixel.sh --len-generovat"
fi

if lech_phone_running; then
  echo "Vypínam virtuálny telefón, aby mala analýza zostavy viac pamäte."
  lech_reset_phones
fi
mem_gb=$(lech_free_mem_gb)
if [ "${mem_gb:-0}" -lt 25 ]; then
  echo "UPOZORNENIE: voľnej pamäte je len ${mem_gb} GB (RAM + swap); analýza zostavy potrebuje približne 25 GB."
fi

echo
echo "Zostavujem LECH OS pre Pixel 9 ($PRODUCT): systém, vendor, boot a vbmeta. Prvé zostavenie: nová analýza"
echo "zostavy a moduly Pixelu, potom sa využije systém zostavený pre GSI."
# Also in the environment: the board configuration reads it (patches/vendor/adevtool/0002).
export LECH_PIXEL_GENERIC_CPU=true
LECH_PRODUCT=$PRODUCT LECH_TARGET_SAVE="$WORKDIR/lunch-target-pixel" LECH_RERUN=tools/etapa2/12-zostav-pixel.sh \
  "$SCRIPT_DIR/05-zostav-aosp.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"}
status=$?
[ "$status" -eq 0 ] || exit "$status"

[ -f "$PRODUCT_OUT/android-info.txt" ] || die "Zostavenie skončilo, ale chýba $PRODUCT_OUT/android-info.txt."
present=()
for img in "${IMAGES[@]}"; do
  if [ -f "$PRODUCT_OUT/$img.img" ]; then present+=("$img.img"); else echo "  (obraz $img.img zostava nemá)"; fi
done
for img in boot.img vendor_boot.img system.img vendor.img vbmeta.img super_empty.img; do
  [ -f "$PRODUCT_OUT/$img" ] || die "Zostavenie skončilo, ale chýba $PRODUCT_OUT/$img."
done

echo
echo "Balím obrazy pre fastboot do $OUT_DIR_IMG/lech-tokay-img.zip…"
mkdir -p "$OUT_DIR_IMG" || die "Priečinok $OUT_DIR_IMG sa nedá vytvoriť."
python3 -c '
import sys, zipfile, os
out, src, files = sys.argv[1], sys.argv[2], sys.argv[3:]
with zipfile.ZipFile(out + ".tmp", "w", zipfile.ZIP_STORED, allowZip64=True) as z:
    for f in ["android-info.txt"] + files:
        z.write(os.path.join(src, f), f)
os.replace(out + ".tmp", out)
' "$OUT_DIR_IMG/lech-tokay-img.zip" "$PRODUCT_OUT" "${present[@]}" || die "Balenie obrazov zlyhalo."
version=${LECH_VERSION:-$(sed -n 's/^LECH_VERSION *[:?]= *//p' "$REPO_DIR/vendor/lech/config/version.mk")}
{
  echo "LECH OS $version - Pixel 9 ($PRODUCT-userdebug, úplná zostava)"
  echo "zostavené: $(date '+%d. %m. %Y %H:%M'), commit $(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo '?')"
  echo "zostava: $(sed -n 's/^ro.system.build.fingerprint=//p' "$PRODUCT_OUT/system/build.prop" 2>/dev/null)"
  echo "vendor: $(sed -n 's/^ro.vendor.build.fingerprint=//p' "$PRODUCT_OUT/vendor/build.prop" 2>/dev/null)"
  echo "obrazy: ${present[*]}"
  (cd "$OUT_DIR_IMG" && sha256sum lech-tokay-img.zip)
} > "$OUT_DIR_IMG/info.txt"
ls -lh "$OUT_DIR_IMG" | sed 1d
echo
echo "Hotovo. Nahratie do Pixelu: tools/etapa2/11-pixel.sh nahrat-uplne (postup: docs/11, časť 4i)."
