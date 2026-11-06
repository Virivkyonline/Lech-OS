#!/usr/bin/env bash
# Applies the LECH OS patch set (patches/<AOSP project path>/*.patch) to the AOSP source tree.
# Each patch becomes a commit in its AOSP project, so `git log` there shows what LECH OS changed.
# Safe to run repeatedly: patches that are already applied are skipped. If AOSP changed and a patch
# no longer applies, nothing more is applied and the script exits with an error.
# A later `repo sync` puts the projects back to plain AOSP; running this script again re-applies the set.
#
# Usage: tools/etapa2/aplikuj-zaplaty.sh [--kontrola]
#   --kontrola   only report the state of each patch, change nothing
# Environment: AOSP_DIR (default ~/aosp)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
PATCH_DIR="$REPO_DIR/patches"
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
CHECK_ONLY=0

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --kontrola) CHECK_ONLY=1; shift ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -d "$AOSP_DIR/.repo" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP (chýba .repo) – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -d "$PATCH_DIR" ] || { echo "Žiadne úpravy AOSP (chýba $PATCH_DIR)."; exit 0; }

mapfile -t patches < <(cd "$PATCH_DIR" && find . -name '*.patch' -type f | sed 's|^\./||' | LC_ALL=C sort)
[ "${#patches[@]}" -gt 0 ] || { echo "Žiadne úpravy AOSP."; exit 0; }

echo "Úpravy AOSP (patches/): ${#patches[@]}"
applied=0 present=0
for rel in "${patches[@]}"; do
  project=$(dirname "$rel")
  patch="$PATCH_DIR/$rel"
  dir="$AOSP_DIR/$project"
  label="$project: $(basename "$rel" .patch)"
  if ! git -C "$dir" rev-parse --git-dir >/dev/null 2>&1; then
    # Projects of the Pixel device support (manifests/lech-pixel.xml) exist only after 12-priprav-pixel.sh.
    if grep -q "path=\"$project\"" "$(dirname "$PATCH_DIR")/manifests/lech-pixel.xml" 2>/dev/null; then
      printf '  bez Pixelu %s (projekt nie je stiahnutý)\n' "$label"
      continue
    fi
    die "Projekt $project v $AOSP_DIR nie je – úprava $rel sa nedá použiť."
  fi

  if git -C "$dir" apply --reverse --check "$patch" >/dev/null 2>&1; then
    printf '  už je    %s\n' "$label"
    present=$((present + 1))
    continue
  fi
  if ! err=$(git -C "$dir" apply --check "$patch" 2>&1); then
    printf '%s\n' "$err" | sed 's/^/    /'
    die "Úprava $rel sa nedá použiť – kód AOSP v $project sa zmenil. Úpravu treba prispôsobiť novej verzii AOSP (pošlite tento výpis)."
  fi
  if [ "$CHECK_ONLY" -eq 1 ]; then
    printf '  chýba    %s\n' "$label"
    continue
  fi
  # A fixed committer keeps this independent of the user's git configuration.
  if ! git -C "$dir" -c user.name="LECH OS" -c user.email=dev@lech-os.invalid am --quiet "$patch"; then
    git -C "$dir" am --abort >/dev/null 2>&1 || true
    die "Úprava $rel sa nepoužila (git am zlyhal v $project)."
  fi
  printf '  použitá  %s\n' "$label"
  applied=$((applied + 1))
done

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "Použitých: $present z ${#patches[@]}."
else
  echo "Nové: $applied, už použité: $present."
fi
