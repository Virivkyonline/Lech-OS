#!/usr/bin/env bash
# Builds LECH OS for the Pixel 9 (tokay) – the full build with own vendor, boot and vbmeta images (task E2-11b) –
# and packs the images for flashing with 11-pixel.sh nahrat-uplne (in WSL to C:\lech\pixel-uplne).
# Prerequisite: tools/etapa2/12-priprav-pixel.sh (GrapheneOS device support, vendor/google_devices/tokay).
# The target is lech_tokay-cp2a-userdebug (kept in ~/lech-work/lunch-target-pixel): the release configuration of
# Android 17 with the vendor API level 202604 of the stock vendor files – trunk_staging is already at 202704 and its
# vendor SELinux policy cannot use the rules of the stock vendor (they name the platform types of 202604).
# The development build uses the generic ARMv8-A target of the LECH OS GSI (LECH_PIXEL_GENERIC_CPU).
# Steps: 1. the platform SELinux policy of this release (plat_sepolicy.cil); when it differs from the one the vendor
# module was generated against, the module is generated again (12-priprav-pixel.sh --len-generovat),
# 2. the whole product, 3. the image package.
#
# Usage: tools/etapa2/12-zostav-pixel.sh [--ulohy N]
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_PIXEL_FULL_DIR (where the image
#   package goes; default /mnt/c/lech/pixel-uplne in WSL, ~/lech-work/pixel-uplne otherwise),
#   LECH_PIXEL_RELEASE (release configuration, default cp2a)

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
RELEASE=${LECH_PIXEL_RELEASE:-cp2a}
VENDOR_API_LEVEL=202604   # of the stock vendor files (Android 17, CP3A.261005.005)
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
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -f "$AOSP_DIR/vendor/google_devices/$DEVICE/$DEVICE.mk" ] || \
  die "Chýba vendor/google_devices/$DEVICE – najprv pripravte podporu Pixelu: tools/etapa2/12-priprav-pixel.sh"
[ -f "$AOSP_DIR/build/release/release_configs/$RELEASE.textproto" ] || \
  die "V build/release chýba konfigurácia vydania $RELEASE (sú: $(ls "$AOSP_DIR/build/release/release_configs" 2>/dev/null | sed 's/\.textproto$//' | tr '\n' ' '))."
api=$(sed -n 's/.*string_value: *"\([0-9]*\)".*/\1/p' "$AOSP_DIR/build/release/flag_values/$RELEASE/RELEASE_BOARD_API_LEVEL.textproto" 2>/dev/null)
[ -z "$api" ] || [ "$api" = "$VENDOR_API_LEVEL" ] || \
  die "Konfigurácia vydania $RELEASE má úroveň API vendora $api, súbory vendora Pixelu majú $VENDOR_API_LEVEL."

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
  die "Na zostavenie LECH OS pre Pixel 9 treba aspoň ${need} GB voľného miesta, voľných je ${free_gb:-0} GB ($(lech_free_detail "$AOSP_DIR"))."

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

# Also in the environment: the board configuration reads it (patches/vendor/adevtool/0002).
export LECH_PIXEL_GENERIC_CPU=true
export LECH_PRODUCT=$PRODUCT LECH_RELEASE=$RELEASE LECH_RELEASE_ONLY=1 LECH_TARGET_SAVE="$WORKDIR/lunch-target-pixel" \
  LECH_RERUN=tools/etapa2/12-zostav-pixel.sh

# Type and attribute declarations of a platform policy (what the generated Pixel policy depends on).
cil_names() { grep -oE '^\((type|typeattribute) [^ )]+\)' "$1" 2>/dev/null | LC_ALL=C sort; }

echo
echo "1/2 Platformová politika SELinux vydania $RELEASE (plat_sepolicy.cil) – prvýkrát s analýzou zostavy…"
LECH_M_TARGETS=plat_sepolicy.cil "$SCRIPT_DIR/05-zostav-aosp.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"} || exit $?
plat_cil="$PRODUCT_OUT/system/etc/selinux/plat_sepolicy.cil"
[ -f "$plat_cil" ] || die "Po zostavení chýba $plat_cil."
if [ -z "$(cil_names "$plat_cil")" ] || ! cmp -s <(cil_names "$plat_cil") <(cil_names "$WORKDIR/pixel-plat_sepolicy.cil"); then
  echo "Podpora Pixelu bola vytvorená s inou platformovou politikou SELinux – vytváram ju znova pre vydanie $RELEASE…"
  LECH_PLAT_CIL="$plat_cil" "$SCRIPT_DIR/12-priprav-pixel.sh" --len-generovat || exit 1
fi

echo
echo "2/2 Zostavujem LECH OS pre Pixel 9 ($PRODUCT-$RELEASE): systém, vendor, boot a vbmeta. Prvé zostavenie:"
echo "nová analýza zostavy, moduly Pixelu a systém s nastaveniami vydania $RELEASE (niekoľko hodín)."
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

# Verified boot (E2-12): vbmeta must carry the LECH OS AVB key and match every image (verify_image checks the key and
# all hashes and hash trees) - a phone locked with the key starts nothing else.
avbtool="$AOSP_DIR/out/host/linux-x86/bin/avbtool"
echo "Overujem podpis overeného štartu (vbmeta, kľúč AVB LECH OS, všetky obrazy)…"
if ! avb_out=$("$avbtool" verify_image --image "$PRODUCT_OUT/vbmeta.img" --key "$AOSP_DIR/vendor/lech-keys/test/avb.pem" 2>&1); then
  printf '%s\n' "$avb_out" | tail -n 5
  die "vbmeta nie je podpísaná kľúčom AVB LECH OS alebo nesedí s obrazmi – telefón so zamknutým bootloaderom by ju nespustil."
fi
avb_sha1=$("$avbtool" info_image --image "$PRODUCT_OUT/vbmeta.img" 2>/dev/null | sed -n 's/^Public key (sha1): *//p')

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
  echo "LECH OS $version - Pixel 9 ($PRODUCT-$RELEASE-userdebug, úplná zostava)"
  echo "zostavené: $(date '+%d. %m. %Y %H:%M'), commit $(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo '?')"
  echo "zostava: $(sed -n 's/^ro.system.build.fingerprint=//p' "$PRODUCT_OUT/system/build.prop" 2>/dev/null)"
  echo "vendor: $(sed -n 's/^ro.vendor.build.fingerprint=//p' "$PRODUCT_OUT/vendor/build.prop" 2>/dev/null)"
  echo "obrazy: ${present[*]}"
  echo "overený štart: vbmeta podpísaná kľúčom AVB LECH OS (verejný kľúč sha1 $avb_sha1)"
  (cd "$OUT_DIR_IMG" && sha256sum lech-tokay-img.zip)
} > "$OUT_DIR_IMG/info.txt"
ls -lh "$OUT_DIR_IMG" | sed 1d
echo
echo "Hotovo. Nahratie do Pixelu: tools/etapa2/11-pixel.sh nahrat-uplne (postup: docs/11, časť 4i)."
