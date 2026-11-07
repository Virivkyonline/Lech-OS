#!/usr/bin/env bash
# Downloads the AOSP sources (branch android-latest-release) that LECH OS is built from (preparation for E2-04).
# By default the checkout is shallow (--depth=1, no history, current branch only), which saves a lot of disk
# space and is enough for building. The download can be interrupted and resumed by running the script again.
#
# With --manifest the same revisions as in another tree are downloaded (a new build computer gets exactly the
# sources the LECH patches were made for): the file comes from `repo manifest -r -o FILE` in that tree; the
# projects of the Pixel support (manifests/lech-pixel.xml, added by 12-priprav-pixel.sh) are left out of it.
#
# Usage: tools/etapa2/04-stiahni-aosp.sh [--plna-historia] [--ulohy N] [--manifest FILE]
#   --plna-historia   full git history (much larger download)
#   --ulohy N         parallel download jobs (default 8)
#   --manifest FILE   pinned manifest of another AOSP tree (repo manifest -r), kept as .repo/manifests/lech-pinned.xml
# Environment: AOSP_DIR (default ~/aosp), AOSP_BRANCH (default android-latest-release)

set -euo pipefail

AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
BRANCH=${AOSP_BRANCH:-android-latest-release}
MANIFEST_URL=https://android.googlesource.com/platform/manifest
JOBS=8
DEPTH_ARGS=(--depth=1)
PINNED=""
MIN_FREE_GB=300     # shallow sources + one Cuttlefish build; full history needs more

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --plna-historia) DEPTH_ARGS=(); MIN_FREE_GB=400; shift ;;
    --ulohy) JOBS=${2:?chýba počet úloh}; shift 2 ;;
    --manifest) PINNED=${2:?chýba súbor manifestu}; shift 2 ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done
case "$JOBS" in ''|*[!0-9]*) die "--ulohy očakáva celé číslo." ;; esac
if [ -n "$PINNED" ]; then
  [ -f "$PINNED" ] || die "Manifest $PINNED neexistuje."
  PINNED=$(cd "$(dirname "$PINNED")" && pwd)/$(basename "$PINNED")
fi

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

# Run a command, show its output on the terminal and append it to $LOG.
# repo hides its progress meter when stdout is not a terminal (e.g. piped into tee), so `script`
# gives it a pseudo-terminal; the exit code of the command is preserved (--return).
logged() {
  if command -v script >/dev/null 2>&1; then
    script --quiet --return --append --command "$(printf '%q ' "$@")" "$LOG"
  else
    "$@" 2>&1 | tee -a "$LOG"
    return "${PIPESTATUS[0]}"
  fi
}

if [ ! -d .repo ]; then
  echo "Inicializujem zdrojové kódy AOSP ($BRANCH) v $AOSP_DIR…"
  logged repo init -u "$MANIFEST_URL" -b "$BRANCH" "${DEPTH_ARGS[@]}" || die "repo init zlyhal – pozri $LOG"
else
  echo "Pokračujem v sťahovaní do $AOSP_DIR (repo už je inicializované)."
fi

if [ -n "$PINNED" ]; then
  # The pinned manifest without the Pixel projects of GrapheneOS: 12-priprav-pixel.sh adds them as a local
  # manifest, the same path in both would stop repo.
  python3 - "$PINNED" .repo/manifests/lech-pinned.xml <<'PYEOF' || die "Manifest $PINNED sa nedá spracovať."
import sys, xml.etree.ElementTree as ET
tree = ET.parse(sys.argv[1])
root = tree.getroot()
pixel = {r.get("name") for r in root.findall("remote") if r.get("name", "").startswith("lech-")}
removed = 0
for el in list(root):
    if (el.tag == "project" and el.get("remote") in pixel) or (el.tag == "remote" and el.get("name") in pixel):
        root.remove(el)
        removed += el.tag == "project"
projects = len(root.findall("project"))
if projects < 100:
    sys.exit("too few projects: %d" % projects)
tree.write(sys.argv[2], encoding="UTF-8", xml_declaration=True)
print("    pripnutý manifest: %d projektov (bez %d projektov Pixelu)" % (projects, removed))
PYEOF
  echo "Používam pripnuté verzie z $PINNED (.repo/manifests/lech-pinned.xml)…"
  logged repo init -m lech-pinned.xml || die "repo init s pripnutým manifestom zlyhal – pozri $LOG"
fi

echo "Sťahujem zdrojové kódy (${JOBS} súbežných úloh). Môže to trvať niekoľko hodín; log: $LOG"
quota_hit() { grep -aq "RESOURCE_EXHAUSTED" "$LOG" 2>/dev/null; }

if ! logged repo sync -c --no-tags -j"$JOBS"; then
  echo "Sťahovanie zlyhalo – skúšam znova s menším počtom úloh…" | tee -a "$LOG"
  if ! logged repo sync -c --no-tags -j2; then
    if quota_hit; then
      die "Servery Google dočasne obmedzili sťahovanie z vašej IP adresy (RESOURCE_EXHAUSTED – kvóta pre anonymné
sťahovanie). Nič sa nestratilo; chýba len niekoľko projektov.
  1. Počkajte 1–2 hodiny (kvóta sa obnoví).
  2. Spustite znova pomalšie:  tools/etapa2/04-stiahni-aosp.sh --ulohy 2
Skript stiahne len to, čo chýba. Log: $LOG"
    fi
    die "Sťahovanie zlyhalo. Spustite skript znova – pokračuje tam, kde skončil. Log: $LOG"
  fi
fi

echo
echo "Hotovo. Zdrojové kódy AOSP sú v $AOSP_DIR (zaberajú $(du -sh "$AOSP_DIR" 2>/dev/null | cut -f1))."
echo "Voľné miesto: $(free_gb "$AOSP_DIR") GB. Ďalší krok: zostavenie (docs/11, časť 3)."
