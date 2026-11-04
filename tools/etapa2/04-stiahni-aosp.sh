#!/usr/bin/env bash
# Downloads the AOSP sources (branch android-latest-release) that LECH OS is built from (preparation for E2-04).
# By default the checkout is shallow (--depth=1, no history, current branch only), which saves a lot of disk
# space and is enough for building. The download can be interrupted and resumed by running the script again.
#
# Usage: tools/etapa2/04-stiahni-aosp.sh [--plna-historia] [--ulohy N]
#   --plna-historia   full git history (much larger download)
#   --ulohy N         parallel download jobs (default 8)
# Environment: AOSP_DIR (default ~/aosp), AOSP_BRANCH (default android-latest-release)

set -euo pipefail

AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
BRANCH=${AOSP_BRANCH:-android-latest-release}
MANIFEST_URL=https://android.googlesource.com/platform/manifest
JOBS=8
DEPTH_ARGS=(--depth=1)
MIN_FREE_GB=300     # shallow sources + one Cuttlefish build; full history needs more

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --plna-historia) DEPTH_ARGS=(); MIN_FREE_GB=400; shift ;;
    --ulohy) JOBS=${2:?chýba počet úloh}; shift 2 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done
case "$JOBS" in ''|*[!0-9]*) die "--ulohy očakáva celé číslo." ;; esac

command -v repo >/dev/null 2>&1 || die "Chýba nástroj repo – spustite tools/etapa2/01-priprava-notebooku.sh (alebo otvorte nový terminál)."
command -v git >/dev/null 2>&1 || die "Chýba git."

mkdir -p "$AOSP_DIR"
free_gb() { df -Pk "$1" 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}'; }
need=$MIN_FREE_GB
[ -d "$AOSP_DIR/.repo" ] && need=50   # resuming: most data is already there
here=$(free_gb "$AOSP_DIR")
[ "${here:-0}" -ge "$need" ] || die "V $AOSP_DIR je voľných ${here:-0} GB, potrebných je aspoň ${need} GB."
if grep -qi microsoft /proc/version 2>/dev/null; then
  win=$(free_gb /mnt/c)
  if [ -n "${win:-}" ] && [ "$win" -lt "$need" ]; then
    die "Na disku C: vo Windows (tam leží disk WSL) je voľných ${win} GB, potrebných je aspoň ${need} GB."
  fi
fi

if git config --global user.name >/dev/null && git config --global user.email >/dev/null; then :; else
  die "repo potrebuje meno a e-mail pre git. Nastavte ich, napr.:
  git config --global user.name \"Vaše Meno\"
  git config --global user.email \"vas@email\""
fi

cd "$AOSP_DIR"
LOG="$AOSP_DIR/../aosp-sync-$(date +%Y%m%d-%H%M%S).log"

if [ ! -d .repo ]; then
  echo "Inicializujem zdrojové kódy AOSP ($BRANCH) v $AOSP_DIR…"
  repo init -u "$MANIFEST_URL" -b "$BRANCH" "${DEPTH_ARGS[@]}" 2>&1 | tee -a "$LOG"
else
  echo "Pokračujem v sťahovaní do $AOSP_DIR (repo už je inicializované)."
fi

echo "Sťahujem zdrojové kódy (${JOBS} súbežných úloh). Môže to trvať niekoľko hodín; log: $LOG"
if ! repo sync -c --no-tags -j"$JOBS" 2>&1 | tee -a "$LOG"; then
  echo "Sťahovanie zlyhalo – skúšam znova s menším počtom úloh…" | tee -a "$LOG"
  repo sync -c --no-tags -j4 2>&1 | tee -a "$LOG" || die "Sťahovanie zlyhalo. Spustite skript znova – pokračuje tam, kde skončil. Log: $LOG"
fi

echo
echo "Hotovo. Zdrojové kódy AOSP sú v $AOSP_DIR (zaberajú $(du -sh "$AOSP_DIR" 2>/dev/null | cut -f1))."
echo "Voľné miesto: $(free_gb "$AOSP_DIR") GB. Ďalší krok: zostavenie (docs/11, časť 3)."
