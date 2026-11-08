#!/usr/bin/env bash
# Applies the LECH OS patch set (patches/<AOSP project path>/*.patch) to the AOSP source tree.
# Each patch becomes a commit in its AOSP project (committer "LECH OS <dev@lech-os.invalid>", the author stays
# the one of the patch – also for patches taken from GrapheneOS), so `git log` there shows what LECH OS changed.
# Safe to run repeatedly. Per project the applied LECH commits are compared with the patches in the repository
# (git patch-id): patches already there are skipped, new ones are added; when a patch changed or was removed,
# the project goes back to its state before the LECH commits and the whole set is applied again.
# If AOSP changed and a patch no longer applies, the script stops with an error.
# Security fixes from the Android Security Bulletins (months in bezpecnost/mesiace.txt, AOSP commits fetched by
# 14-bezpecnost.sh stav into ~/lech-work/bezpecnost/<month>/patches) go first in each project, in bulletin order;
# a fix that is already in AOSP or does not apply is skipped and reported (it never stops the build), fixes listed
# in bezpecnost/vynechat.txt (CVE or commit) are left out. An adapted version replaces the AOSP commit: from
# bezpecnost/prisposobene/<path>/<CVE>.patch, else the GrapheneOS version found by 14-bezpecnost.sh stav.
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
SECURITY_MONTHS="$REPO_DIR/bezpecnost/mesiace.txt"
SECURITY_SKIP="$REPO_DIR/bezpecnost/vynechat.txt"
SECURITY_CACHE="${LECH_WORK:-$HOME/lech-work}/bezpecnost"
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

# Security fixes: lines "project path<TAB>patch file<TAB>label", in month and bulletin order.
security=()
if [ -f "$SECURITY_MONTHS" ]; then
  while read -r month _; do
    case "$month" in ''|'#'*) continue ;; esac
    tsv="$SECURITY_CACHE/$month/vysledky.tsv"
    [ -f "$tsv" ] || die "Bezpečnostné opravy $month nie sú stiahnuté – spustite: tools/etapa2/14-bezpecnost.sh stav $month"
    while IFS=$'\t' read -r cve _level _comp _sev project sha path _status; do
      [ -n "$path" ] && [ -n "$sha" ] || continue
      if [ -f "$SECURITY_SKIP" ] && grep -qE "^($cve|${sha:0:12}[0-9a-f]*)([[:space:]]|$)" "$SECURITY_SKIP"; then continue; fi
      # Adapted versions first: in the repository (bezpecnost/prisposobene/<path>/<CVE>.patch), then the one of
      # GrapheneOS found by 14-bezpecnost.sh stav, then the AOSP commit itself.
      f="$REPO_DIR/bezpecnost/prisposobene/$path/$cve.patch"; label="$month $cve (prispôsobená)"
      if [ ! -s "$f" ]; then
        f="$SECURITY_CACHE/$month/grapheneos/$cve-${project//\//_}-${sha:0:12}.patch"; label="$month $cve (verzia GrapheneOS)"
      fi
      if [ ! -s "$f" ]; then
        f="$SECURITY_CACHE/$month/patches/$cve-${project//\//_}-${sha:0:12}.patch"; label="$month $cve (${sha:0:12})"
      fi
      [ -s "$f" ] && security+=("$path"$'\t'"$f"$'\t'"$label")
    done < "$tsv"
  done < "$SECURITY_MONTHS"
fi
[ "${#patches[@]}" -gt 0 ] || [ "${#security[@]}" -gt 0 ] || { echo "Žiadne úpravy AOSP."; exit 0; }
mapfile -t projects < <({ printf '%s\n' "${patches[@]}" | xargs -r -n1 dirname
                           for e in ${security[@]+"${security[@]}"}; do printf '%s\n' "${e%%$'\t'*}"; done
                         } | sed '/^$/d' | LC_ALL=C sort -u)

# The patches of a project applied to its base in a temporary index (the working tree stays untouched): fills
# plan_files/plan_labels/plan_trees. Security fixes that do not apply are skipped (sec_present: already in AOSP,
# sec_conflict: have to be adapted); a LECH OS patch that does not apply fails with git's message.
plan_project() {   # project dir, base commit; uses sec_files/sec_labels and files (LECH patches, relative)
  local dir=$1 base=$2 index rc=0 i f
  plan_files=(); plan_labels=(); plan_trees=()
  index=$(mktemp)
  GIT_INDEX_FILE="$index" git -C "$dir" read-tree "$base" || rc=1
  for (( i = 0; rc == 0 && i < ${#sec_files[@]}; i++ )); do
    f=${sec_files[$i]}
    if GIT_INDEX_FILE="$index" git -C "$dir" apply --cached "$f" 2>/dev/null; then
      plan_files+=("$f"); plan_labels+=("bezpečnosť ${sec_labels[$i]}")
      plan_trees+=("$(GIT_INDEX_FILE="$index" git -C "$dir" write-tree)")
    elif GIT_INDEX_FILE="$index" git -C "$dir" apply --cached --check -R "$f" 2>/dev/null; then
      sec_present=$((sec_present + 1))
    else
      sec_conflict=$((sec_conflict + 1))
      sec_conflicts+=("${dir#"$AOSP_DIR"/}: ${sec_labels[$i]}")
    fi
  done
  for f in ${files[@]+"${files[@]}"}; do
    [ "$rc" -eq 0 ] || break
    GIT_INDEX_FILE="$index" git -C "$dir" apply --cached "$PATCH_DIR/$f" || { rc=1; break; }
    plan_files+=("$PATCH_DIR/$f"); plan_labels+=("${dir#"$AOSP_DIR"/}: $(basename "$f" .patch)")
    plan_trees+=("$(GIT_INDEX_FILE="$index" git -C "$dir" write-tree)")
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

echo "Úpravy AOSP (patches/): ${#patches[@]}, bezpečnostné opravy (bezpecnost/mesiace.txt): ${#security[@]}"
applied=0 present=0 missing=0 sec_present=0 sec_conflict=0
sec_conflicts=()
for project in "${projects[@]}"; do
  dir="$AOSP_DIR/$project"
  mapfile -t files < <(printf '%s\n' "${patches[@]}" | awk -v p="$project/" 'index($0, p) == 1 && index(substr($0, length(p) + 1), "/") == 0')
  sec_files=(); sec_labels=()
  for e in ${security[@]+"${security[@]}"}; do
    [ "${e%%$'\t'*}" = "$project" ] || continue
    e=${e#*$'\t'}; sec_files+=("${e%%$'\t'*}"); sec_labels+=("${e#*$'\t'}")
  done
  if ! git -C "$dir" rev-parse --git-dir >/dev/null 2>&1; then
    # Projects of the Pixel device support (manifests/lech-pixel.xml) exist only after 12-priprav-pixel.sh.
    if grep -q "path=\"$project\"" "$PIXEL_MANIFEST" 2>/dev/null; then
      for rel in ${files[@]+"${files[@]}"}; do printf '  bez Pixelu %s (projekt nie je stiahnutý)\n' "$project: $(basename "$rel" .patch)"; done
      continue
    fi
    [ "${#files[@]}" -gt 0 ] || continue   # only security fixes for a project LECH OS does not have
    die "Projekt $project v $AOSP_DIR nie je – úpravy ${files[*]} sa nedajú použiť."
  fi

  # LECH commits on top of the project; the project before them is plain AOSP (or the GrapheneOS revision).
  lech=$(git -C "$dir" log --format='%ce' -n 200 | awk -v e="$LECH_EMAIL" '$1 != e {exit} {n++} END {print n + 0}')
  base=$(git -C "$dir" rev-parse "HEAD~$lech")
  plan_err=$(mktemp)
  if ! plan_project "$dir" "$base" 2>"$plan_err"; then
    sed 's/^/    /' "$plan_err"; rm -f -- "$plan_err"
    die "Úpravy v $project sa nedajú použiť – kód projektu sa zmenil. Úpravu treba prispôsobiť novej verzii (pošlite tento výpis)."
  fi
  rm -f -- "$plan_err"
  trees=(${plan_trees[@]+"${plan_trees[@]}"})
  # Up to date when HEAD is the base plus the first $lech patches; otherwise the project is rebuilt from the base.
  have=$lech
  head_tree=$(git -C "$dir" rev-parse "HEAD^{tree}")
  if [ "$lech" -gt "${#plan_files[@]}" ] || { [ "$lech" -gt 0 ] && [ "${trees[$((lech - 1))]}" != "$head_tree" ]; }; then
    if [ "$CHECK_ONLY" -eq 1 ]; then
      printf '  iné      %s: v projekte sú iné úpravy LECH OS ako v patches/ (pri použití sa projekt obnoví)\n' "$project"
      missing=$((missing + ${#plan_files[@]}))
      continue
    fi
    [ -z "$(git -C "$dir" status --porcelain --untracked-files=no)" ] || \
      die "V $project sú neuložené zmeny – úpravy LECH OS sa nedajú obnoviť. Skontrolujte: git -C $dir status"
    echo "  obnova   $project: úpravy LECH OS sa zmenili – vraciam $lech commit(y) LECH OS a používam ich znova"
    git -C "$dir" reset --quiet --hard "$base" || die "Projekt $project sa nepodarilo obnoviť."
    have=0
  fi

  for (( i = 0; i < ${#plan_files[@]}; i++ )); do
    label=${plan_labels[$i]}
    if [ "$i" -lt "$have" ]; then
      printf '  už je    %s\n' "$label"
      present=$((present + 1))
    elif [ "$CHECK_ONLY" -eq 1 ]; then
      printf '  chýba    %s\n' "$label"
      missing=$((missing + 1))
    else
      apply_one "$dir" "${plan_files[$i]}" "$label"
    fi
  done
done

if [ "$CHECK_ONLY" -eq 1 ]; then
  echo "Použitých: $present."
else
  echo "Nové: $applied, už použité: $present."
fi
if [ "${#security[@]}" -gt 0 ]; then
  echo "Bezpečnostné opravy: už v AOSP $sec_present, nedajú sa použiť (treba prispôsobiť) $sec_conflict."
  for c in ${sec_conflicts[@]+"${sec_conflicts[@]}"}; do printf '  neprispôsobená  %s\n' "$c"; done
fi
