#!/usr/bin/env bash
# Builds AOSP for Cuttlefish from the sources in $AOSP_DIR – the first local build, a prerequisite for E2-04.
# The build target is <product>-<release>-<variant>; the release configuration differs between AOSP branches,
# so the script tries trunk_staging first and then the release configurations found in the source tree.
# The chosen target is saved, so later runs (and 03-sietovy-audit.sh --lokalna-zostava) reuse it.
# A failed or interrupted build can be resumed by running the script again (the build is incremental).
#
# Usage: tools/etapa2/05-zostav-aosp.sh [--ulohy N] [--spustit]
#   --ulohy N   parallel build jobs (default 8; more needs more RAM)
#   --spustit   start the built phone afterwards
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_CLOCK_RETRIES (default 5),
#   LECH_PRODUCT (default aosp_cf_x86_64_only_phone), LECH_RELEASE (force a release config),
#   LECH_VARIANT (default userdebug)

set -uo pipefail   # no -e: build/envsetup.sh and lunch are not written for errexit

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"

AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
PRODUCT=${LECH_PRODUCT:-aosp_cf_x86_64_only_phone}
VARIANT=${LECH_VARIANT:-userdebug}
JOBS=8
START=0
MIN_FREE_GB=150     # one Cuttlefish build wrote 139 GB into out/ (first real build, 5. 11. 2026)

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --ulohy) JOBS=${2:?chýba počet úloh}; shift 2 ;;
    --spustit) START=1; shift ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done
case "$JOBS" in ''|*[!0-9]*) die "--ulohy očakáva celé číslo." ;; esac

[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
mkdir -p "$WORKDIR"
free_gb=$(df -Pk "$AOSP_DIR" | awk 'NR==2 {print int($4/1024/1024)}')
if [ ! -d "$AOSP_DIR/out" ] && [ "${free_gb:-0}" -lt "$MIN_FREE_GB" ]; then
  die "Na zostavenie treba približne ${MIN_FREE_GB} GB voľného miesta, voľných je ${free_gb:-0} GB."
fi

LOG="$WORKDIR/zostavenie-$(date +%Y%m%d-%H%M%S).log"
cd "$AOSP_DIR" || die "Nedá sa prejsť do $AOSP_DIR."

echo "Pripravujem prostredie zostavenia (build/envsetup.sh)…"
# shellcheck disable=SC1091
source build/envsetup.sh >>"$LOG" 2>&1 || die "build/envsetup.sh zlyhal – pozri $LOG"

release_candidates() {
  [ -n "${LECH_RELEASE:-}" ] && echo "$LECH_RELEASE"
  [ -f "$LECH_TARGET_FILE" ] && sed -n "s/^${PRODUCT}-\(.*\)-${VARIANT}\$/\1/p" "$LECH_TARGET_FILE"
  echo trunk_staging
  ls build/release/release_configs/*.textproto 2>/dev/null | xargs -r -n1 basename | sed 's/\.textproto$//' | sort -r
}

TARGET=""
tried=""
for release in $(release_candidates | awk 'NF && !seen[$0]++'); do
  candidate="${PRODUCT}-${release}-${VARIANT}"
  tried="$tried $candidate"
  if lunch "$candidate" >>"$LOG" 2>&1; then
    TARGET=$candidate
    break
  fi
done
[ -n "$TARGET" ] || die "Žiadny cieľ zostavenia nefungoval (skúšané:${tried}). Pozri $LOG; konkrétny cieľ sa dá zadať cez LECH_RELEASE."
echo "$TARGET" > "$LECH_TARGET_FILE"
echo "Cieľ zostavenia: $TARGET"

echo "Zostavujem (${JOBS} súbežných úloh). Prvé zostavenie trvá na notebooku niekoľko hodín; log: $LOG"
SECONDS=0
# Under heavy load the WSL clock can step backwards; the build then rejects freshly written files
# ("future mtime"). Nothing is wrong with the build, so retry automatically a few times.
MAX_CLOCK_RETRIES=${LECH_CLOCK_RETRIES:-5}
attempt=0
while :; do
  start_bytes=$(stat -c %s "$LOG" 2>/dev/null || echo 0)
  m -j"$JOBS" 2>&1 | tee -a "$LOG"
  status=${PIPESTATUS[0]}
  [ "$status" -eq 0 ] && break
  if tail -c +$(( start_bytes + 1 )) "$LOG" | grep -aq "future mtime" && [ "$attempt" -lt "$MAX_CLOCK_RETRIES" ]; then
    attempt=$(( attempt + 1 ))
    echo
    echo "Hodiny systému sa posunuli späť (future mtime) – to nie je chyba zostavenia. Pokračujem (pokus ${attempt}/${MAX_CLOCK_RETRIES})…" | tee -a "$LOG"
    sleep 15
    continue
  fi
  break
done
minutes=$(( SECONDS / 60 ))

if [ "$status" -ne 0 ]; then
  echo
  echo "Zostavenie zlyhalo po ${minutes} min. Posledné chybové riadky:"
  grep -aE "FAILED:|error:|Error |future mtime" "$LOG" | tail -n 15
  cat <<EOF

Čo ďalej:
  - Ak došla pamäť (killed, out of memory): spustite znova s menej úlohami: tools/etapa2/05-zostav-aosp.sh --ulohy 4
  - Inak spustite skript znova – zostavenie pokračuje tam, kde skončilo.
  - Úplný log: $LOG
EOF
  exit "$status"
fi

echo
echo "Zostavenie úspešné za ${minutes} min (cieľ $TARGET)."
echo "Výstup: $ANDROID_PRODUCT_OUT ($(du -sh "$AOSP_DIR/out" 2>/dev/null | cut -f1) v out/), voľné miesto: $(df -Pk "$AOSP_DIR" | awk 'NR==2 {print int($4/1024/1024)}') GB"

if [ "$START" -eq 1 ]; then
  echo "Spúšťam virtuálny telefón z vlastnej zostavy…"
  lech_reset_phones
  lech_describe_start
  lech_create_phone --local
  echo "Obrazovka telefónu: https://localhost:8443"
fi
