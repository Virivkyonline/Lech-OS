#!/usr/bin/env bash
# Measures how much of GrapheneOS a port of its sandboxed Google Play compatibility layer ("gmscompat") onto the
# AOSP frameworks/base of LECH OS would take (docs/18-google-play-v-sandboxe.md, option 1). Read-only: nothing in
# the AOSP tree changes (the GrapheneOS branch is fetched into FETCH_HEAD only).
#
# Usage: tools/etapa2/meranie-zony-google.sh        (AOSP_DIR defaults to ~/aosp)
set -u
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
FB="$AOSP_DIR/frameworks/base"
GOS=https://github.com/GrapheneOS
BRANCH=17
GMS_PATHS=('*gmscompat*' '*compat/gms*' '*GmsCompat*')
GMS_GREP='gmscompat\|gms compat\|play services\|play store\|sandboxed google\|GmsCore\|gmscore'

api_count() {   # repository, extra query: number of commits (the last page of a listing with one commit per page)
  curl -sI "https://api.github.com/repos/GrapheneOS/$1/commits?sha=$BRANCH&per_page=1$2" \
    | tr -d '\r' | sed -n 's/.*[?&]page=\([0-9]*\)>; rel="last".*/\1/p'
}

echo "== Aplikácie a projekty GrapheneOS, ktoré by sa prevzali celé (počet commitov vetvy $BRANCH) =="
for repo in platform_packages_apps_GmsCompat; do
  echo "  $repo: $(api_count "$repo" "" || true)"
done

echo "== frameworks/base GrapheneOS: commity, ktoré menia súbory vrstvy (GitHub API) =="
for path in core/java/com/android/internal/gmscompat core/java/android/app/compat/gms; do
  echo "  $path: $(api_count platform_frameworks_base "&path=$path" || true)"
done

[ -d "$FB/.git" ] || [ -f "$FB/.git" ] || { echo "Chýba $FB – podrobné meranie sa nedá urobiť."; exit 0; }
cd "$FB" || exit 1
if [ "$(git rev-parse --is-shallow-repository)" = true ]; then
  echo "== $FB je plytký klon (bez histórie) – podrobné meranie z gitu vynechané =="
  exit 0
fi

echo "== Sťahujem vetvu $BRANCH GrapheneOS frameworks/base (len rozdiel oproti vášmu stromu)… =="
git fetch --quiet --no-tags "$GOS/platform_frameworks_base.git" "$BRANCH" || { echo "fetch zlyhal"; exit 0; }
base=$(git merge-base HEAD FETCH_HEAD)
echo "  spoločný základ: $(git log -1 --format='%h %ad %s' --date=short "$base" | cut -c1-90)"
range="$base..FETCH_HEAD"
notaosp=(--perl-regexp --author='^(?!.*@(google|android)\.com)')

echo "== Rozsah =="
echo "  všetky commity GrapheneOS nad základom (autor mimo Google): $(git rev-list --count --no-merges "${notaosp[@]}" "$range")"
by_path=$(git log --no-merges --format=%H "$range" -- "${GMS_PATHS[@]}" | sort -u)
by_msg=$(git log --no-merges --format=%H "${notaosp[@]}" -i --grep="$GMS_GREP" "$range" | sort -u)
all=$(printf '%s\n%s\n' "$by_path" "$by_msg" | grep . | sort -u)
echo "  commity vrstvy Google Play (podľa súborov): $(printf '%s\n' "$by_path" | grep -c .)"
echo "  commity vrstvy Google Play (podľa súborov alebo popisu): $(printf '%s\n' "$all" | grep -c .)"
echo "  súbory vrstvy (nové alebo zmenené):$(git diff --shortstat "$base" FETCH_HEAD -- "${GMS_PATHS[@]}")"
files=$(printf '%s\n' "$all" | xargs -r -n 200 git show --no-patch --format= --name-only 2>/dev/null | grep . | sort -u)
echo "  všetky súbory, ktorých sa commity vrstvy dotýkajú: $(printf '%s\n' "$files" | grep -c .)"
echo "    z toho mimo priečinkov vrstvy (zásahy do jadra): $(printf '%s\n' "$files" | grep -vicE 'gmscompat|compat/gms')"
echo "  celý rozdiel GrapheneOS oproti základu:$(git diff --shortstat "$base" FETCH_HEAD)"

echo "== Ako často sa vrstva menila (commity podľa mesiacov, posledných 12) =="
printf '%s\n' "$all" | xargs -r -n 200 git show --no-patch --format=%ad --date=format:%Y-%m 2>/dev/null \
  | sort | uniq -c | tail -12 | sed 's/^/  /'
