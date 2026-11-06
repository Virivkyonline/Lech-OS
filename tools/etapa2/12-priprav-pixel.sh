#!/usr/bin/env bash
# Prepares the full LECH OS build for the Pixel 9 (task E2-11b): the device support of GrapheneOS in the AOSP
# tree (ADR-0002, manifests/lech-pixel.xml). Steps:
#   1. Node.js 24 for adevtool: the official build from nodejs.org (checksum verified) in ~/lech-work/node,
#      nothing is installed into the system; yarn 1 into the same directory,
#   2. the GrapheneOS projects of manifests/lech-pixel.xml: ~/aosp/.repo/local_manifests/lech-pixel.xml and
#      `repo sync` of only these projects (pinned revisions),
#   3. the LECH patches (aplikuj-zaplaty.sh), among them two for adevtool,
#   4. `yarn install` for adevtool,
#   5. the host tools adevtool needs (aapt2, lpunpack, simg2img…), built in the normal out/ with the target of the
#      Pixel GSI and offered to adevtool through out_adevtool_deps (adevtool would otherwise run a separate build),
#   6. `adevtool generate-all -d tokay`: downloads the stock factory image of the Pixel 9 (CP3A.261005.005, about
#      3 GB) and generates vendor/google_devices/tokay – vendor files, firmware, overlays, IMS (ShannonIms).
# The new projects make the next build run the Soong analysis again (about 25 GB of RAM incl. swap).
#
# Usage: tools/etapa2/12-priprav-pixel.sh [--bez-generovania] [--len-generovat]
#   --bez-generovania   steps 1–5 only
#   --len-generovat     step 6 only (e.g. again after a failure)
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
DEVICE=tokay
MANIFEST="$REPO_DIR/manifests/lech-pixel.xml"
NODE_DIR="$WORKDIR/node"
NODE_MAJOR=24
MIN_FREE_GB=35   # factory image ~3 GB, unpacked images ~15 GB, vendor module ~5 GB, host tools, margin
HOST_TOOLS=(aapt2 apksigner aprotoc arsclib debugfs dispol fsck.erofs lpdump lpunpack lz4 ota_extractor simg2img sqlite3 toybox)
ORIG_ARGS=("$@")

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

GENERATE=1
PREPARE=1
while [ $# -gt 0 ]; do
  case "$1" in
    --bez-generovania) GENERATE=0; shift ;;
    --len-generovat) PREPARE=0; shift ;;
    -h|--help) sed -n '2,21p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -f "$MANIFEST" ] || die "Chýba $MANIFEST – aktualizujte repozitár (git pull)."
[ "$(id -u)" -ne 0 ] || die "Spúšťajte ako bežný používateľ, nie root (adevtool pod rootom nefunguje)."

# The host tools are built in the shared out/: run under the build lock (lib-cuttlefish.sh).
if [ -z "${LECH_BUILD_LOCK_HELD:-}" ]; then
  lech_with_build_lock "$0" ${ORIG_ARGS[@]+"${ORIG_ARGS[@]}"}
  status=$?
  if [ "$status" -eq 75 ]; then
    echo
    lech_build_lock_report
    die "Príprava sa nespustila – v strome AOSP práve beží iné zostavenie."
  fi
  exit "$status"
fi

free_gb=$(lech_free_gb "$AOSP_DIR")
[ "${free_gb:-0}" -ge "$MIN_FREE_GB" ] || die "Treba aspoň ${MIN_FREE_GB} GB voľného miesta, voľných je ${free_gb:-0} GB."

# 1. Node.js 24 and yarn in ~/lech-work/node.
ensure_node() {
  if [ -x "$NODE_DIR/bin/node" ] && [ "$("$NODE_DIR/bin/node" -p 'process.versions.node.split(".")[0]')" -ge "$NODE_MAJOR" ]; then
    :
  else
    echo "1/6 Sťahujem Node.js $NODE_MAJOR (oficiálna zostava z nodejs.org) do $NODE_DIR…"
    local base="https://nodejs.org/dist/latest-v$NODE_MAJOR.x" sums file tmp
    sums=$(curl -fsSL "$base/SHASUMS256.txt") || die "Zoznam verzií Node.js sa nepodarilo stiahnuť ($base)."
    file=$(printf '%s\n' "$sums" | awk '{print $2}' | grep -E '^node-v[0-9.]+-linux-x64\.tar\.xz$' | head -n 1)
    [ -n "$file" ] || die "V $base sa nenašla zostava Node.js pre linux-x64."
    tmp=$(mktemp -d)
    curl -fL --progress-bar -o "$tmp/$file" "$base/$file" || die "Sťahovanie Node.js zlyhalo."
    (cd "$tmp" && printf '%s\n' "$sums" | grep " $file\$" | sha256sum -c --quiet -) || die "Kontrolný súčet Node.js nesedí."
    mkdir -p "$NODE_DIR"
    tar -xJf "$tmp/$file" -C "$NODE_DIR" --strip-components=1 || die "Rozbalenie Node.js zlyhalo."
    rm -f -- "$tmp/$file"; rmdir -- "$tmp"
  fi
  export PATH="$NODE_DIR/bin:$PATH"
  if ! command -v yarn >/dev/null 2>&1 || [ "$(command -v yarn)" != "$NODE_DIR/bin/yarn" ]; then
    npm install -g --silent yarn@1 >/dev/null || die "yarn sa nepodarilo nainštalovať (npm install -g yarn@1)."
  fi
  echo "    Node.js $(node -v), yarn $(yarn -v)"
}
ensure_node

if [ "$PREPARE" -eq 1 ]; then
  # 2. GrapheneOS projects (pinned revisions) into the AOSP tree.
  echo "2/6 Projekty GrapheneOS pre Pixel (manifests/lech-pixel.xml)…"
  mkdir -p "$AOSP_DIR/.repo/local_manifests"
  cp "$MANIFEST" "$AOSP_DIR/.repo/local_manifests/lech-pixel.xml"
  REPO_BIN=$(command -v repo || echo "$HOME/bin/repo")
  [ -x "$REPO_BIN" ] || die "Chýba nástroj repo ($REPO_BIN)."
  mapfile -t projects < <(grep -oE 'path="[^"]+"' "$MANIFEST" | cut -d'"' -f2)
  (cd "$AOSP_DIR" && "$REPO_BIN" sync -c --no-tags --force-sync -j4 "${projects[@]}") || die "repo sync projektov GrapheneOS zlyhal."

  # 3. LECH patches (also vendor/adevtool).
  echo "3/6 Úpravy LECH OS…"
  AOSP_DIR=$AOSP_DIR "$SCRIPT_DIR/aplikuj-zaplaty.sh" || exit 1

  # 4. adevtool dependencies.
  echo "4/6 Knižnice adevtool (yarn install)…"
  (cd "$AOSP_DIR" && yarn install --cwd vendor/adevtool --frozen-lockfile --silent) || die "yarn install pre adevtool zlyhal."

  # 5. Host tools in the normal out/ (target of the Pixel GSI: its Soong analysis is the most recent one).
  target=$(cat "$WORKDIR/lunch-target-gsi" 2>/dev/null || echo lech_gsi_arm64-trunk_staging-userdebug)
  echo "5/6 Nástroje pre adevtool (${HOST_TOOLS[*]}), cieľ $target – nové projekty spustia analýzu zostavy…"
  log="$WORKDIR/pixel-nastroje-$(date +%Y%m%d-%H%M%S).log"
  (
    cd "$AOSP_DIR" || exit 1
    set +u
    # shellcheck disable=SC1091
    source build/envsetup.sh >/dev/null && lunch "$target" >/dev/null && m "${HOST_TOOLS[@]}"
  ) 2>&1 | tee "$log"
  [ "${PIPESTATUS[0]}" -eq 0 ] || die "Nástroje sa nepodarilo zostaviť – log: $log (ak je v ňom Killed: zatvorte aplikácie vo Windows a spustite znova)."
  for t in "${HOST_TOOLS[@]}"; do
    [ -x "$AOSP_DIR/out/host/linux-x86/bin/$t" ] || die "Po zostavení chýba out/host/linux-x86/bin/$t (log: $log)."
  done
  # adevtool takes its tools from out_adevtool_deps/host/linux-x86/bin (ADEVTOOL_SKIP_DEP_BUILD=1 below).
  mkdir -p "$AOSP_DIR/out_adevtool_deps"
  ln -sfn ../out/host "$AOSP_DIR/out_adevtool_deps/host"
  echo "    hotovo (log: $log)"
fi

[ "$GENERATE" -eq 1 ] || { echo "Hotovo bez generovania. Ďalej: tools/etapa2/12-priprav-pixel.sh --len-generovat"; exit 0; }

# 6. Vendor module of the Pixel 9 from the stock factory image.
[ -x "$AOSP_DIR/vendor/adevtool/bin/run" ] || die "Chýba vendor/adevtool – spustite skript bez --len-generovat."
echo "6/6 adevtool generate-all -d $DEVICE (stiahne pôvodný obraz Google, ~3 GB, potom ho rozbalí)…"
log="$WORKDIR/pixel-adevtool-$(date +%Y%m%d-%H%M%S).log"
(
  cd "$AOSP_DIR" || exit 1
  set +u
  # shellcheck disable=SC1091
  source build/envsetup.sh >/dev/null
  export ANDROID_BUILD_TOP="$AOSP_DIR" ADEVTOOL_SKIP_DEP_BUILD=1
  # --noVerify: the LECH patches change generated files, so they differ from the GrapheneOS reference list.
  vendor/adevtool/bin/run generate-all -d "$DEVICE" --noVerify
) 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
[ "$status" -eq 0 ] || die "adevtool zlyhal – log: $log (pošlite koniec výpisu)."
[ -f "$AOSP_DIR/vendor/google_devices/$DEVICE/$DEVICE.mk" ] || die "adevtool skončil, ale chýba vendor/google_devices/$DEVICE/$DEVICE.mk."
echo
echo "Hotovo: $AOSP_DIR/vendor/google_devices/$DEVICE ($(du -sh "$AOSP_DIR/vendor/google_devices/$DEVICE" | cut -f1))."
echo "Ďalší krok: zostavenie LECH OS pre Pixel 9 (tools/etapa2/12-zostav-pixel.sh)."
