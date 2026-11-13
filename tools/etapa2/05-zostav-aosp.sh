#!/usr/bin/env bash
# Builds AOSP for Cuttlefish from the sources in $AOSP_DIR – the first local build, a prerequisite for E2-04.
# The build target is <product>-<release>-<variant>; the release configuration differs between AOSP branches,
# so the script tries trunk_staging first and then the release configurations found in the source tree.
# The chosen target is saved, so later runs (and 03-sietovy-audit.sh --lokalna-zostava) reuse it.
# A failed or interrupted build can be resumed by running the script again (the build is incremental).
#
# Usage: tools/etapa2/05-zostav-aosp.sh [--ulohy N] [--spustit]
#   --ulohy N   parallel build jobs (default: the processor threads, at most 1 per 2.5 GB of RAM, at least 8;
#               more needs more RAM)
#   --spustit   start the built phone afterwards
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_CLOCK_RETRIES (default 5),
#   LECH_PRODUCT (default aosp_cf_x86_64_only_phone), LECH_RELEASE (force a release config),
#   LECH_VARIANT (default userdebug), LECH_RERUN (command suggested after a failure; set by 06-zostav-lech.sh),
#   LECH_TARGET_SAVE (file for the chosen target; default the Cuttlefish one, ~/lech-work/lunch-target),
#   LECH_RELEASE_ONLY (with LECH_RELEASE: no other release configuration is tried),
#   LECH_M_TARGETS (build only these targets instead of the whole product, e.g. plat_sepolicy.cil)

set -uo pipefail   # no -e: build/envsetup.sh and lunch are not written for errexit

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"

AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
PRODUCT=${LECH_PRODUCT:-aosp_cf_x86_64_only_phone}
VARIANT=${LECH_VARIANT:-userdebug}
RERUN=${LECH_RERUN:-tools/etapa2/05-zostav-aosp.sh}
# A build number of its own for every LECH OS version (ro.build.version.incremental, part of ro.build.fingerprint).
# Without BUILD_NUMBER, Soong uses "eng.<user>" for every build, so all LECH OS builds had the same fingerprint: after an
# update PackageManager kept its parser cache of the system apps (/data/system/package_cache, kept while the
# fingerprint is unchanged) and the launcher its icon and label cache (Build.VERSION.INCREMENTAL,
# patches/frameworks/libs/systemui 0001). A system app then read its new resources with the resource IDs of an old
# version: on 11. 11. 2026 LECH Nastavenia was named "Režim v lietadle" on the home screen (0.4.6-dev).
if [ -z "${BUILD_NUMBER:-}" ]; then
  lech_version=${LECH_VERSION:-$(sed -n 's/^LECH_VERSION *[:?]= *//p' "$SCRIPT_DIR/../../vendor/lech/config/version.mk")}
  export BUILD_NUMBER="lech.${lech_version:-0}"
fi
# Other products (the Pixel GSI) keep their target apart: the audit and the nightly check start the phone
# from the target in LECH_TARGET_FILE, which must stay the Cuttlefish one.
TARGET_FILE=${LECH_TARGET_SAVE:-$LECH_TARGET_FILE}
# Default jobs: one per processor thread, but at most one per 2.5 GB of RAM (Java and linker steps need 2-3 GB each),
# and not below the 8 that worked on the first build notebook (15 GB RAM + swap). New notebook: 16 threads, 47 GB → 16.
mem_gb=$(awk '/^MemTotal:/ {print int($2 / 1024 / 1024)}' /proc/meminfo 2>/dev/null)
JOBS=$(( $(nproc 2>/dev/null || echo 8) ))
[ "$JOBS" -le $(( ${mem_gb:-20} * 2 / 5 )) ] || JOBS=$(( ${mem_gb:-20} * 2 / 5 ))
[ "$JOBS" -ge 8 ] || JOBS=8
START=0
MIN_FREE_GB=150     # one Cuttlefish build wrote 139 GB into out/ (first real build, 5. 11. 2026)

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --ulohy) JOBS=${2:?chýba počet úloh}; shift 2 ;;
    --spustit) START=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done
case "$JOBS" in ''|*[!0-9]*) die "--ulohy očakáva celé číslo." ;; esac

[ -f "$AOSP_DIR/build/envsetup.sh" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP – najprv tools/etapa2/04-stiahni-aosp.sh."
mkdir -p "$WORKDIR"
free_gb=$(lech_free_gb "$AOSP_DIR")
if [ ! -d "$AOSP_DIR/out" ] && [ "${free_gb:-0}" -lt "$MIN_FREE_GB" ]; then
  die "Na zostavenie treba približne ${MIN_FREE_GB} GB voľného miesta, voľných je ${free_gb:-0} GB."
fi

LOG="$WORKDIR/zostavenie-$(date +%Y%m%d-%H%M%S).log"
cd "$AOSP_DIR" || die "Nedá sa prejsť do $AOSP_DIR."

echo "Pripravujem prostredie zostavenia (build/envsetup.sh)…"
# shellcheck disable=SC1091
source build/envsetup.sh >>"$LOG" 2>&1 || die "build/envsetup.sh zlyhal – pozri $LOG"

# GrapheneOS tree (ADR-0012): build ID and security patch level are per product (BUILD_ID_<product>, set by
# vendor/google_devices/<device>/cmds-for-envsetup.sh, which lunch reads only for the product named like the
# device). The LECH OS product lech_<device> needs the values of its device, else the vendor module refuses the
# build ("BUILD_ID: expected CP3A…, got CP2A…", 13. 11. 2026).
if [ "${LECH_BASE:-}" = gos ]; then
  lech_dev=${PRODUCT#lech_}
  lech_extra="vendor/google_devices/$lech_dev/cmds-for-envsetup.sh"
  if [ -f "$lech_extra" ]; then
    # shellcheck disable=SC1090
    source "$lech_extra"
    for lech_var in BUILD_ID PLATFORM_SECURITY_PATCH; do
      lech_src="${lech_var}_${lech_dev}"
      if [ -n "${!lech_src:-}" ]; then export "${lech_var}_${PRODUCT}=${!lech_src}"; else unset "${lech_var}_${PRODUCT}"; fi
    done
  fi
fi

release_candidates() {
  if [ -n "${LECH_RELEASE:-}" ] && [ -n "${LECH_RELEASE_ONLY:-}" ]; then echo "$LECH_RELEASE"; return; fi
  [ -n "${LECH_RELEASE:-}" ] && echo "$LECH_RELEASE"
  [ -f "$TARGET_FILE" ] && sed -n "s/^${PRODUCT}-\(.*\)-${VARIANT}\$/\1/p" "$TARGET_FILE"
  echo trunk_staging
  ls build/release/release_configs/*.textproto 2>/dev/null | xargs -r -n1 basename | sed 's/\.textproto$//' | sort -r
}

TARGET=""
tried=""
for release in $(release_candidates | awk 'NF && !seen[$0]++'); do
  candidate="${PRODUCT}-${release}-${VARIANT}"
  tried="$tried $candidate"
  if lunch "$candidate" >>"$LOG" 2>&1 && [ "${TARGET_RELEASE:-$release}" = "$release" ] && [ -n "${TARGET_PRODUCT:-}" ]; then
    TARGET=$candidate
    break
  fi
done
[ -n "$TARGET" ] || die "Žiadny cieľ zostavenia nefungoval (skúšané:${tried}). Pozri $LOG; konkrétny cieľ sa dá zadať cez LECH_RELEASE."
echo "$TARGET" > "$TARGET_FILE"
echo "Cieľ zostavenia: $TARGET"

read -r -a M_TARGETS <<< "${LECH_M_TARGETS:-}"
if [ "${#M_TARGETS[@]}" -gt 0 ]; then
  echo "Zostavujem len ${M_TARGETS[*]} (${JOBS} súbežných úloh); log: $LOG"
else
  echo "Zostavujem (${JOBS} súbežných úloh). Prvé zostavenie AOSP trvá na notebooku niekoľko hodín, ďalšie sú kratšie; log: $LOG"
fi
SECONDS=0
# Under heavy load the WSL clock can step backwards; the build then rejects freshly written files
# ("future mtime"). Nothing is wrong with the build, so retry automatically a few times.
MAX_CLOCK_RETRIES=${LECH_CLOCK_RETRIES:-5}
attempt=0
while :; do
  start_bytes=$(stat -c %s "$LOG" 2>/dev/null || echo 0)
  m -j"$JOBS" ${M_TARGETS[@]+"${M_TARGETS[@]}"} 2>&1 | tee -a "$LOG"
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

# Output of the failed build steps: the lines after "FAILED: <target>" up to the next progress line (the tool's
# own message, e.g. of checkpolicy or secilc, has no "error:"), long command lines shortened. Up to 3 steps.
failed_steps() {   # file, byte offset where this build attempt starts
  tail -c +$(( $2 + 1 )) "$1" 2>/dev/null | awk '
    /^FAILED: / && !/^FAILED: ninja/ { if (steps++ == 3) exit; show = 1; n = 0 }
    show && /^(\[ *[0-9]+%|FAILED: ninja|ninja:|[0-9:]+ ninja failed|####)/ { show = 0 }
    show && n++ < 40 { print "  " (length($0) > 220 ? substr($0, 1, 220) " …" : $0) }'
}

# Ctrl+C: m ends with 130 (SIGINT) and siso/ninja reports "interrupted" - not a failure of the build.
if [ "$status" -ne 0 ] && { [ "$status" -eq 130 ] || tail -c +$(( start_bytes + 1 )) "$LOG" | tail -n 20 | grep -aqiE 'interrupt'; }; then
  echo
  echo "Zostavenie prerušené po ${minutes} min (Ctrl+C). Hotová časť ostáva – po novom spustení pokračuje tam, kde skončilo."
  exit 130
fi

if [ "$status" -ne 0 ]; then
  echo
  echo "Zostavenie zlyhalo po ${minutes} min."
  failed_out=$(failed_steps "$LOG" "$start_bytes")
  # siso keeps the full output of failed commands in out/siso_output when the console shows a shortened one.
  if [ -f "$AOSP_DIR/out/siso_output" ] && [ "$AOSP_DIR/out/siso_output" -nt "$TARGET_FILE" ]; then
    siso_out=$(failed_steps "$AOSP_DIR/out/siso_output" 0)
    [ "$(printf '%s\n' "$siso_out" | wc -l)" -gt "$(printf '%s\n' "$failed_out" | wc -l)" ] && failed_out=$siso_out
  fi
  if [ -n "$failed_out" ]; then
    echo "Zlyhaný krok a jeho výstup:"
    printf '%s\n' "$failed_out"
  else
    echo "Posledné chybové riadky:"
    tail -c +$(( start_bytes + 1 )) "$LOG" | grep -aE "FAILED:|error:|Error |ERROR|future mtime" | tail -n 15
  fi
  if tail -c +$(( start_bytes + 1 )) "$LOG" | grep -aq "soong bootstrap failed" && \
     tail -c +$(( start_bytes + 1 )) "$LOG" | grep -aqE "^Killed|signal:killed"; then
    cat <<EOF

Analýze zostavy (Soong) došla pamäť – potrebuje približne 25 GB RAM vrátane swapu.
Menej úloh (--ulohy) tu nepomôže: analýza je jeden proces.
  - Vypnite virtuálny telefón: cvd reset --yes
  - Vo Windows zatvorte veľké aplikácie (prehliadače, hry); voľná pamäť: free -h
  - Skontrolujte swap: swapon --show (WSL: swap v .wslconfig, docs/11)
  - Potom spustite znova: ${RERUN}
EOF
  fi
  cat <<EOF

Čo ďalej:
  - Ak došla pamäť pri preklade (killed, out of memory): spustite znova s menej úlohami: ${RERUN} --ulohy 4
  - Inak spustite znova ${RERUN} – zostavenie pokračuje tam, kde skončilo.
  - Úplný log: $LOG
EOF
  exit "$status"
fi

echo
echo "Zostavenie úspešné za ${minutes} min (cieľ $TARGET)."
echo "Výstup: $ANDROID_PRODUCT_OUT ($(du -sh "$AOSP_DIR/out" 2>/dev/null | cut -f1) v out/), voľné miesto: $(lech_free_gb "$AOSP_DIR") GB"

if [ "$START" -eq 1 ]; then
  echo "Spúšťam virtuálny telefón z vlastnej zostavy…"
  lech_reset_phones
  lech_describe_start
  lech_create_phone --local
  echo "Obrazovka telefónu: https://localhost:8443"
fi
