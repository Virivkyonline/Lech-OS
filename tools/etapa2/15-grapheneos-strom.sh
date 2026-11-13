#!/usr/bin/env bash
# LECH OS on GrapheneOS (ADR-0012): downloads the GrapheneOS source tree of one pinned release into its own
# directory. The current AOSP tree (~/aosp, line 0.5.x) is not touched; its git objects are reused
# (repo --reference), so only what GrapheneOS changed is downloaded and ~/aosp must stay in place.
#
# Usage: tools/etapa2/15-grapheneos-strom.sh [--tag ZNACKA]
#   --tag ZNACKA   release tag of github.com/GrapheneOS/platform_manifest (e.g. 2026111000); default: the tag
#                  recorded in manifests/grapheneos-tag, else the newest tag (printed, to be recorded)
# Environment: GOS_DIR (default ~/gos), AOSP_DIR (default ~/aosp), JOBS (default 8)
set -euo pipefail
REPO_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
GOS_DIR=${GOS_DIR:-$HOME/gos}
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
JOBS=${JOBS:-8}
MANIFEST_URL=https://github.com/GrapheneOS/platform_manifest.git
TAG_FILE="$REPO_DIR/manifests/grapheneos-tag"
die() { echo "CHYBA: $*" >&2; exit 1; }

TAG=""
while [ $# -gt 0 ]; do
  case "$1" in
    --tag) TAG=${2:?}; shift 2 ;;
    *) die "Neznámy parameter $1" ;;
  esac
done
command -v repo >/dev/null || die "Chýba nástroj repo (tools/etapa2/01-priprava-notebooku.sh)."
if [ -z "$TAG" ] && [ -s "$TAG_FILE" ]; then TAG=$(tr -d ' \r\n' < "$TAG_FILE"); fi
if [ -z "$TAG" ]; then
  echo "Zisťujem najnovšie vydanie GrapheneOS…"
  TAG=$(git ls-remote --tags --refs "$MANIFEST_URL" | sed -n 's|.*refs/tags/\([0-9]\{10\}\)$|\1|p' | sort -n | tail -n 1)
  [ -n "$TAG" ] || die "Značku vydania sa nepodarilo zistiť (internet?)."
fi
echo "Vydanie GrapheneOS: $TAG  →  $GOS_DIR"

avail=$(df -BG --output=avail "$HOME" | tail -n 1 | tr -dc 0-9)
[ "$avail" -ge 300 ] || die "Voľných je len ${avail} GB, na strom a zostavu treba aspoň 300 GB."

mkdir -p "$GOS_DIR" && cd "$GOS_DIR"
ref=()
[ -d "$AOSP_DIR/.repo" ] && ref=(--reference="$AOSP_DIR")
repo init -u "$MANIFEST_URL" -b "refs/tags/$TAG" "${ref[@]}" || die "repo init zlyhal."
if ! repo sync -c --no-tags -j"$JOBS"; then
  echo "Sťahovanie zlyhalo, skúšam znova pomalšie (-j2)…"
  repo sync -c --no-tags -j2 || die "repo sync zlyhal – spustite skript znova, pokračuje tam, kde skončil."
fi
echo "$TAG" > "$GOS_DIR/.lech-gos-tag"
echo
echo "Hotovo: GrapheneOS $TAG je v $GOS_DIR ($(du -sh --exclude=.repo "$GOS_DIR" 2>/dev/null | cut -f1) bez .repo)."
echo "Projekty GrapheneOS: $(repo list 2>/dev/null | wc -l). Značku $TAG zapíšte do manifests/grapheneos-tag (urobí Claude)."
