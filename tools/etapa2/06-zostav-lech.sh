#!/usr/bin/env bash
# Builds LECH OS for Cuttlefish (tasks E2-04 to E2-07): the phone LECH-PHONE with LECH OS version,
# boot animation, without default connections to Google and signed with the LECH OS test keys
# (created by 07-vytvor-kluce.sh on the first run, kept outside the repository). The product definition lives in
# vendor/lech of this repository and changes to AOSP code in patches/; the script copies vendor/lech
# into the AOSP tree ($AOSP_DIR/vendor/lech), applies the patches (aplikuj-zaplaty.sh) and builds
# the lunch target lech_cf_x86_64_only_phone-<release>-userdebug with 05-zostav-aosp.sh.
# The build continues from the AOSP build (same device), but the first LECH build runs the Soong
# analysis again and rebuilds the images. With --spustit it starts the phone and checks its identity.
#
# Usage: tools/etapa2/06-zostav-lech.sh [--ulohy N] [--spustit] [--len-skopirovat]
#   --ulohy N          parallel build jobs (default 8; more needs more RAM)
#   --spustit          start the LECH OS phone after the build and check its identity
#   --len-skopirovat   only prepare the AOSP tree (vendor/lech, keys, patches), do not build
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_KEYS (default
#   ~/lech-keys/test), LECH_RELEASE, LECH_VARIANT

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
PRODUCT=lech_cf_x86_64_only_phone
BASE_PRODUCT=aosp_cf_x86_64_only_phone
SRC="$REPO_DIR/vendor/lech"
DEST="$AOSP_DIR/vendor/lech"
KEYS=${LECH_KEYS:-$HOME/lech-keys/test}
KEYS_DEST="$AOSP_DIR/vendor/lech-keys/test"
PRODUCT_MK="$SRC/products/$PRODUCT.mk"
BASE_MK="$AOSP_DIR/device/google/cuttlefish/vsoc_x86_64_only/phone/aosp_cf.mk"
MIN_FREE_GB=40      # the first LECH build added 12 GB to out/ (5. 11. 2026); keep a safety margin

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

BUILD_ARGS=()
START=0
COPY_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --ulohy) BUILD_ARGS+=(--ulohy "${2:?chýba počet úloh}"); shift 2 ;;
    --spustit) START=1; BUILD_ARGS+=(--spustit); shift ;;
    --len-skopirovat) COPY_ONLY=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -f "$SRC/AndroidProducts.mk" ] || die "V repozitári chýba $SRC – spustite skript z repozitára Lech-OS (git pull)."
[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -f "$BASE_MK" ] || die "Chýba $BASE_MK – produkt LECH z neho dedí. Zmenila sa štruktúra Cuttlefish v novej verzii AOSP?"
mkdir -p "$WORKDIR"

# The Cuttlefish phone sets some build modes only for its own product name. The LECH product mirrors
# them; warn when a new AOSP version adds a variable there that the LECH product does not set.
missing=$(comm -23 <(lech_block_vars "$BASE_MK" "$BASE_PRODUCT") <(lech_block_vars "$PRODUCT_MK" "$PRODUCT") | tr '\n' ' ')
if [ -n "${missing// /}" ]; then
  echo "UPOZORNENIE: $BASE_MK nastavuje pre $BASE_PRODUCT aj: $missing"
  echo "  Produkt LECH ich nenastavuje – zostavenie môže prebehnúť inak ako pri čistom AOSP."
  echo "  Doplňte ich do vendor/lech/products/$PRODUCT.mk (blok ifeq na konci súboru)."
fi

if [ -d "$AOSP_DIR/out" ]; then
  free_gb=$(lech_free_gb "$AOSP_DIR")
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

# LECH OS test signing keys (E2-07): created once outside the repository, copied into the AOSP tree.
if [ ! -f "$KEYS/platform.x509.pem" ] || [ ! -f "$KEYS/avb.pem" ] || [ ! -f "$KEYS/avb_pkmd.bin" ]; then
  new_avb=0; [ -f "$KEYS/avb.pem" ] || new_avb=1
  echo "Chýbajú kľúče LECH OS v $KEYS – vytváram ich (raz, približne pol minúty)…"
  LECH_KEYS=$KEYS AOSP_DIR=$AOSP_DIR "$SCRIPT_DIR/07-vytvor-kluce.sh" >/dev/null || die "Kľúče sa nepodarilo vytvoriť."
  if [ "$new_avb" -eq 1 ]; then
    echo "  Nový kľúč overeného štartu (AVB, E2-12): $KEYS/avb.pem – ZÁLOHUJTE ho a na iné zostavovacie počítače"
    echo "  kopírujte celý priečinok $KEYS (zamknutý telefón spustí len systém podpísaný týmto kľúčom)."
    echo "  Odtlačok: $(sha256sum "$KEYS/avb_pkmd.bin" | cut -d' ' -f1)"
  fi
fi
# Keys the source tree asks for that were added later (e.g. gmscompat_lib of GrapheneOS, ADR-0012): the key script
# creates only the missing ones and never replaces an existing key.
missing_keys=$(find "$AOSP_DIR/build/make/target/product/security" -maxdepth 1 -name '*.pk8' -printf '%f\n' 2>/dev/null |
  sed 's/\.pk8$//' | while read -r n; do [ -f "$KEYS/$n.pk8" ] || printf '%s ' "$n"; done)
if [ -n "$missing_keys" ]; then
  echo "Dopĺňam kľúče LECH OS, ktoré strom potrebuje: $missing_keys"
  LECH_KEYS=$KEYS AOSP_DIR=$AOSP_DIR "$SCRIPT_DIR/07-vytvor-kluce.sh" >/dev/null || die "Kľúče sa nepodarilo doplniť."
fi
mkdir -p "$KEYS_DEST" && chmod 700 "$AOSP_DIR/vendor/lech-keys" "$KEYS_DEST"
cp -p "$KEYS"/*.pk8 "$KEYS"/*.x509.pem "$KEYS/avb.pem" "$KEYS_DEST/" || die "Kľúče sa nepodarilo skopírovať do $KEYS_DEST."
# Soong modules for keys that apps reference by module name (PRODUCT_CERTIFICATE_OVERRIDES in common.mk).
cat > "$KEYS_DEST/Android.bp" <<'EOF'
// Written by tools/etapa2/06-zostav-lech.sh: LECH OS test keys as Soong modules (task E2-07).
package {
    default_applicable_licenses: ["Android-Apache-2.0"],
}

// The Bluetooth apps have a certificate of their own module; LECH OS signs them with its "bluetooth" key.
android_app_certificate {
    name: "lech_bluetooth_certificate",
    certificate: "bluetooth",
}
EOF
echo "Podpisové kľúče: $KEYS (testovacie kľúče LECH OS)"
# SELinux certificates of the mainline system apps are set in common.mk; warn if AOSP no longer knows
# the Bluetooth variable (the Bluetooth app would get the wrong SELinux domain and the boot would fail).
if ! grep -q PRODUCT_MAINLINE_BLUETOOTH_SEPOLICY_DEV_CERTIFICATES "$AOSP_DIR/build/make/core/config.mk" 2>/dev/null; then
  echo "UPOZORNENIE: AOSP nepozná PRODUCT_MAINLINE_BLUETOOTH_SEPOLICY_DEV_CERTIFICATES – skontrolujte keys.conf (docs/11, časť 4d)."
fi

version=${LECH_VERSION:-$(sed -n 's/^LECH_VERSION *[:?]= *//p' "$SRC/config/version.mk")}
echo "LECH OS ${version:-?} (commit $(git -C "$REPO_DIR" rev-parse --short HEAD 2>/dev/null || echo '?'))"

# Record the exact AOSP revisions this build is based on (the seed of lech-manifest, task E2-04).
# Only while no LECH patch is committed in the tree, so the file lists plain AOSP revisions.
patched=0
for project in $(cd "$REPO_DIR/patches" 2>/dev/null && find . -name '*.patch' -printf '%h\n' | sed 's|^\./||' | sort -u); do
  [ "$(git -C "$AOSP_DIR/$project" log -1 --format=%ae 2>/dev/null)" = dev@lech-os.invalid ] && patched=1
done
REPO_BIN=$(command -v repo || echo "$HOME/bin/repo")
if [ "$patched" -eq 0 ] && [ -x "$REPO_BIN" ] && \
   (cd "$AOSP_DIR" && "$REPO_BIN" manifest -r -o "$WORKDIR/aosp-manifest.xml") >/dev/null 2>&1; then
  echo "Presné verzie zdrojových kódov AOSP: $WORKDIR/aosp-manifest.xml"
fi

AOSP_DIR=$AOSP_DIR "$SCRIPT_DIR/aplikuj-zaplaty.sh" || exit 1

[ "$COPY_ONLY" -eq 1 ] && { echo "Hotovo (len príprava, bez zostavenia)."; exit 0; }

# The Soong analysis needs about 25 GB of RAM incl. swap; a running phone takes several GB of it.
if lech_phone_running; then
  if [ "$START" -eq 1 ]; then
    echo "Vypínam bežiaci virtuálny telefón, aby mala analýza zostavy viac pamäte (po zostavení sa spustí nový)."
    lech_reset_phones
  else
    echo "UPOZORNENIE: beží virtuálny telefón a berie pamäť, ktorú zostavenie potrebuje (vypnutie: cvd reset --yes)."
  fi
fi
mem_gb=$(lech_free_mem_gb)
if [ "${mem_gb:-0}" -lt 25 ]; then
  echo "UPOZORNENIE: voľnej pamäte je len ${mem_gb} GB (RAM + swap). Ak sa mení nastavenie produktu, analýza"
  echo "  zostavy potrebuje približne 25 GB – zatvorte veľké aplikácie vo Windows."
fi

echo
echo "Zostavujem LECH OS ($PRODUCT). Na notebooku to po zostavení čistého AOSP trvalo 18 minút;"
echo "pri novej verzii AOSP alebo po zmene v device/ to môže byť oveľa dlhšie."
LECH_PRODUCT=$PRODUCT LECH_RERUN=tools/etapa2/06-zostav-lech.sh "$SCRIPT_DIR/05-zostav-aosp.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"}
status=$?
[ "$status" -eq 0 ] || exit "$status"
[ "$START" -eq 1 ] || { echo "Spustenie telefónu: tools/etapa2/06-zostav-lech.sh --spustit (zostavenie sa už nezopakuje)."; exit 0; }

# Identity check on the running phone.
ADB="$AOSP_DIR/out/host/linux-x86/bin/adb"
[ -x "$ADB" ] || ADB=$(command -v adb || true)
[ -n "$ADB" ] || { echo "adb nie je k dispozícii – identitu overte v Nastaveniach telefónu."; exit 0; }
echo
echo "Overujem LECH OS v bežiacom telefóne…"
timeout 180 "$ADB" wait-for-device || { echo "Telefón sa cez adb neozval – identitu overte v Nastaveniach."; exit 0; }
prop() { "$ADB" shell getprop "$1" 2>/dev/null | tr -d '\r'; }
ok=1
report() {   # OK|CHYBA|?, what, detail ("?" = could not be checked, not an error)
  printf '  %-7s %s%*s %s\n' "$1" "$2" $(( 22 - ${#2} )) "" "$3"   # ${#2} counts characters, not bytes
  [ "$1" != CHYBA ] || ok=0
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
# E2-06: network time from the LECH overlay, no remote key provisioning server.
if "$ADB" shell cmd overlay list android 2>/dev/null | tr -d '\r' | grep -q '\[x\] lech.os.overlay.framework'; then
  report OK "čas (NTP)" "europe.pool.ntp.org (lech.os.overlay.framework zapnutý)"
else
  report CHYBA "čas (NTP)" "prekrytie lech.os.overlay.framework nie je zapnuté"
fi
# E2-07: signed with the LECH OS test keys.
tags=$(prop ro.build.tags)
if [ "$tags" = dev-keys ]; then
  report OK "podpisové kľúče" "dev-keys (nie verejné test-keys AOSP)"
else
  report CHYBA "podpisové kľúče" "${tags:-<prázdne>} (očakávané dev-keys)"
fi
check_signature() {   # what, package, LECH certificate
  local want have
  want=$(lech_cert_hash "$KEYS/$3.x509.pem")
  have=$("$ADB" shell dumpsys package "$2" 2>/dev/null | tr -d '\r' \
    | grep -m1 -o 'version:[0-9]*, signatures:\[[0-9a-f, ]*\]' | sed 's/^version:[0-9]*, //' || true)
  case "$have" in
    *"$want"*) report OK "$1" "kľúč $3 LECH OS ($want)" ;;
    "") report "?" "$1" "nezistený (dumpsys package $2)" ;;
    *) report CHYBA "$1" "$have, očakávaný kľúč LECH OS $want" ;;
  esac
}
check_signature "podpis systému" android platform
check_signature "podpis Bluetooth" com.android.bluetooth bluetooth
rkp=$(prop remote_provisioning.hostname)
if [ -z "$rkp" ]; then
  report OK "atestačné kľúče (RKP)" "bez servera"
else
  report CHYBA "atestačné kľúče (RKP)" "server $rkp"
fi
echo
if [ "$ok" -eq 1 ]; then
  echo "LECH OS je v poriadku. V telefóne: Settings → About phone."
  echo "Spojenia telefónu overí sieťový audit: tools/etapa2/03-sietovy-audit.sh --lokalna-zostava"
else
  echo "LECH OS nie je v poriadku – pošlite tento výstup."
  exit 1
fi
