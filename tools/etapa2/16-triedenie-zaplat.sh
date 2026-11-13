#!/usr/bin/env bash
# LECH OS on GrapheneOS (ADR-0012): checks every LECH OS patch against the GrapheneOS tree without changing it.
# For each patch: "sadne" (applies cleanly), "uz-je" (already contained: applies in reverse), "konflikt" (must be
# adapted or dropped), "chyba-projekt" (the project is not in the tree).
#
# Usage: tools/etapa2/16-triedenie-zaplat.sh      Environment: GOS_DIR (default ~/gos)
set -u
REPO_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
GOS_DIR=${GOS_DIR:-$HOME/gos}
PATCH_DIR="$REPO_DIR/patches"
[ -d "$GOS_DIR/.repo" ] || { echo "CHYBA: $GOS_DIR nie je strom GrapheneOS (tools/etapa2/15-grapheneos-strom.sh)."; exit 1; }
echo "Záplaty LECH OS voči GrapheneOS $(cat "$GOS_DIR/.lech-gos-tag" 2>/dev/null) v $GOS_DIR:"
cd "$PATCH_DIR" || exit 1
n_ok=0; n_in=0; n_bad=0; n_miss=0
while IFS= read -r p; do
  project=$(dirname "$p")
  dir="$GOS_DIR/$project"
  if [ ! -e "$dir/.git" ]; then
    state="chyba-projekt"; n_miss=$((n_miss + 1))
  elif git -C "$dir" apply --check "$PATCH_DIR/$p" 2>/dev/null; then
    state="sadne"; n_ok=$((n_ok + 1))
  elif git -C "$dir" apply --check -R "$PATCH_DIR/$p" 2>/dev/null; then
    state="uz-je"; n_in=$((n_in + 1))
  else
    state="konflikt"; n_bad=$((n_bad + 1))
  fi
  printf '  %-14s %s\n' "$state" "$p"
done < <(find . -name '*.patch' -type f | sed 's|^\./||' | LC_ALL=C sort)
echo "Spolu: sadne $n_ok, už je v GrapheneOS $n_in, konflikt $n_bad, chýba projekt $n_miss."
