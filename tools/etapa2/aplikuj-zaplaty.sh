#!/usr/bin/env bash
# Applies the LECH OS patch set (patches/<AOSP project path>/*.patch) to the AOSP source tree.
# Each patch becomes a commit in its AOSP project (committer "LECH OS <dev@lech-os.invalid>", the author stays
# the one of the patch – also for patches taken from GrapheneOS), so `git log` there shows what LECH OS changed.
# Safe to run repeatedly. Per project the applied LECH commits are compared with the patches in the repository
# (git patch-id): patches already there are skipped, new ones are added; when a patch changed or was removed,
# the project goes back to its state before the LECH commits and the whole set is applied again.
# If AOSP changed and a patch no longer applies, the script stops with an error.
# A later `repo sync` puts the projects back to plain AOSP; running this script again re-applies the set.
#
# Usage: tools/etapa2/aplikuj-zaplaty.sh [--kontrola]
#   --kontrola   only report the state of each patch, change nothing
# Environment: AOSP_DIR (default ~/aosp)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
PATCH_DIR="$REPO_DIR/patches"
PIXEL_MANIFEST="$REPO_DIR/manifests/lech-pixel.xml"
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
LECH_EMAIL=dev@lech-os.invalid
CHECK_ONLY=0

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --kontrola) CHECK_ONLY=1; shift ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ -d "$AOSP_DIR/.repo" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP (chýba .repo) – najprv tools/etapa2/04-stiahni-aosp.sh."
[ -d "$PATCH_DIR" ] || { echo "Žiadne úpravy AOSP (chýba $PATCH_DIR)."; exit 0; }

mapfile -t patches < <(cd "$PATCH_DIR" && find . -name '*.patch' -type f | sed 's|^\./||' | LC_ALL=C sort)
[ "${#patches[@]}" -gt 0 ] || { echo "Žiadne úpravy AOSP."; exit 0; }
mapfile -t projects < <(printf '%s\n' "${patches[@]}" | xargs -n1 dirname | LC_ALL=C sort -u)

# Tree after applying patches to a commit, computed in a temporary index (the working tree stays untouched).
# Prints one tree id per patch; fails (with git's message) when a patch does not apply.
trees_after() {   # project dir, base commit, patch files...
  local dir=$1 base=$2 index rc=0
  shift 2
  index=$(mktemp)
  GIT_INDEX_FILE="$index" git -C "$dir" read-tree "$base" || rc=1
  for f in "$@"; do
    [ "$rc" -eq 0 ] || break
    GIT_INDEX_FILE="$index" git -C "$dir" apply --cached "$f" || { rc=1; break; }
    GIT_INDEX_FILE="$index" git -C "$dir" write-tree
  done
  rm -f -- "$index"
  return "$rc"
}

# Apply one patch as a commit (a fixed committer keeps this independent of the user's git configuration).
apply_one() {   # project dir, patch file, label
  if ! err=$(git -C "$1" apply --check "$2" 2>&1); then
    printf '%s\n' "$err" | sed 's/^/    /'
    die "Úprava ${2#"$PATCH_DIR"/} sa nedá použiť – kód v ${1#"$AOSP_DIR"/} sa zmenil. Úpravu treba prispôsobiť novej verzii (pošlite tento výpis)."
  fi
  if ! git -C "$1" -c user.name="LECH OS" -c user.email="$LECH_EMAIL" am --quiet --committer-date-is-author-date "$2"; then
    git -C "$1" am --abort >/dev/null 2>&1 || true
    die "Úprava ${2#"$PATCH_DIR"/} sa nepoužila (git am zlyhal v ${1#"$AOSP_DIR"/})."
  fi
  printf '  použitá  %s\n' "$3"
  applied=$((applied + 1))
}

echo "Úpravy AOSP (patches/): ${#patches[@]}"
applied=0 present=0 missing=0
for project in "${projects[@]}"; do
  dir="$AOSP_DIR/$project"
  mapfile -t files < <(printf '%s\n' "${patches[@]}" | awk -v p="$project/" 'index($0, p) == 1 && index(substr($0, length(p) + 1), "/") == 0')
  if ! git -C "$dir" rev-parse --git-dir >/dev/null 2>&1; then
    # Projects of the Pixel device support (manifests/lech-pixel.xml) exist only after 12-priprav-pixel.sh.
    if grep -q "path=\"$project\"" "$PIXEL_MANIFEST" 2>/dev/null; then
      for rel in "${files[@]}"; do printf '  bez Pixelu %s (projekt nie je stiahnutý)\n' "$project: $(basename "$rel" .patch)"; done
      continue
    fi
    die "Projekt $project v $AOSP_DIR nie je – úpravy ${files[*]} sa nedajú použiť."
  fi

  # LECH commits on top of the project; the project before them is plain AOSP (or the GrapheneOS revision).
  lech=$(git -C "$dir" log --format='%ce' -n 200 | awk -v e="$LECH_EMAIL" '$1 != e {exit} {n++} END {print n + 0}')
  base=$(git -C "$dir" rev-parse "HEAD~$lech")
  paths=()
  for rel in "${files[@]}"; do paths+=("$PATCH_DIR/$rel"); done
  if ! trees_out=$(trees_after "$dir" "$base" "${paths[@]}" 2>&1); then
    printf '%s\n' "$trees_out" | grep -v '^[0-9a-f]\{40\}$' | sed 's/^/    /'
    die "Úpravy v $project sa nedajú použiť – kód projektu sa zmenil. Úpravu treba prispôsobiť novej verzii (pošlite tento výpis)."
  fi
  mapfile -t trees < <(printf '%s\n' "$trees_out" | grep '^[0-9a-f]\{40\}$')
  # Up to date when HEAD is the base plus the first $lech patches; otherwise the project is rebuilt from the base.
  have=$lech
  head_tree=$(git -C "$dir" rev-parse "HEAD^{tree}")
  if [ "$lech" -gt "${#files[@]}" ] || { [ "$lech" -gt 0 ] && [ "${trees[$((lech - 1))]}" != "$head_tree" ]; }; then
    if [ "$CHECK_ONLY" -eq 1 ]; then
      printf '  iné      %s: v projekte sú iné úpravy LECH OS ako v patches/ (pri použití sa projekt obnoví)\n' "$project"
      missing=$((missing + ${#files[@]}))
      continue
    fi
    [ -z "$(git -C "$dir" status --porcelain --untracked-files=no)" ] || \
      die "V $project sú neuložené zmeny – úpravy LECH OS sa nedajú obnoviť. Skontrolujte: git -C $dir status"
    echo "  obnova   $project: úpravy LECH OS sa zmenili – vraciam $lech commit(y) LECH OS a používam ich znova"
    git -C "$dir" reset --quiet --hard "$base" || die "Projekt $project sa nepodarilo obnoviť."
    have=0
  fi

  for (( i = 0; i < ${#files[@]}; i++ )); do
    label="$project: $(basename "${files[$i]}" .patch)"
    if [ "$i" -lt "$have" ]; then
      printf '  už je    %s\n' "$label"
      present=$((present + 1))
    elif [ "$CHECK_ONLY" -eq 1 ]; then
      printf '  chýba    %s\n' "$label"
      missing=$((missing + 1))
    else
      apply_one "$dir" "$PATCH_DIR/${files[$i]}" "$label"
    fi
  done
done

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "Použitých: $present z ${#patches[@]}."
else
  echo "Nové: $applied, už použité: $present."
fi
