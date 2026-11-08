#!/usr/bin/env bash
# Monthly security update of LECH OS (gate 1: monthly update rehearsal, docs/14): which fixes of the Android
# Security Bulletins are already in the AOSP tree of LECH OS and which are missing.
#
# For each month: downloads the bulletin (source.android.com), lists its fixes and AOSP commits (bulletin.py),
# fetches every commit from android.googlesource.com into its project (no checkout, the tree is not changed) and
# checks its diff against the tree: already there (applies in reverse), missing but applies, or missing with a
# conflict (has to be adapted). The patches are kept for the next step (applying them).
#
# Usage: tools/etapa2/14-bezpecnost.sh stav [YYYY-MM ...]   (default: the current month)
#   e.g. tools/etapa2/14-bezpecnost.sh stav 2026-07 2026-08 2026-09 2026-10 2026-11
# Output: ~/lech-work/bezpecnost/<YYYY-MM>/stav.md (+ bulletin.html, bulletin.json, patches/), a copy in C:\lech
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
OUT_BASE="$WORKDIR/bezpecnost"
GOOGLESOURCE=${LECH_GOOGLESOURCE:-https://android.googlesource.com}   # tests: a local mirror (file://...)
BULLETIN_URL=https://source.android.com/docs/security/bulletin

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

# Security patch level of the last Pixel build of LECH OS (system and vendor).
patch_levels() {
  local out="$AOSP_DIR/out/target/product/tokay"
  printf 'systém %s, vendor %s' \
    "$(sed -n 's/^ro.build.version.security_patch=//p' "$out/system/build.prop" 2>/dev/null | head -n 1)" \
    "$(sed -n 's/^ro.vendor.build.security_patch=//p' "$out/vendor/build.prop" 2>/dev/null | head -n 1)"
}

cmd_stav() {
  local months=("$@") month dir json projects
  [ "${#months[@]}" -gt 0 ] || months=("$(date +%Y-%m)")
  for month in "${months[@]}"; do
    [[ "$month" =~ ^20[0-9]{2}-(0[1-9]|1[0-2])$ ]] || die "Mesiac v tvare YYYY-MM, napr. 2026-11 (zadané: $month)."
  done
  [ -d "$AOSP_DIR/.repo" ] || die "V $AOSP_DIR nie sú zdrojové kódy AOSP."
  for c in curl git python3 repo; do command -v "$c" >/dev/null 2>&1 || die "Chýba $c."; done

  echo "LECH OS (posledná zostava pre Pixel): úroveň zabezpečenia $(patch_levels)"
  # Project name -> path in this tree (repo list prints "path : name").
  projects=$(cd "$AOSP_DIR" && repo list 2>/dev/null) || die "repo list zlyhal."

  for month in "${months[@]}"; do
    dir="$OUT_BASE/$month"
    mkdir -p "$dir/patches" || die "Priečinok $dir sa nedá vytvoriť."
    echo
    echo "== Bulletin $month"
    if ! curl -fsSL --retry 3 -o "$dir/bulletin.html" "$BULLETIN_URL/${month%%-*}/$month-01"; then
      echo "  Bulletin sa nepodarilo stiahnuť ($BULLETIN_URL/${month%%-*}/$month-01) – vyšiel už?"
      continue
    fi
    json="$dir/bulletin.json"
    python3 -I "$SCRIPT_DIR/bulletin.py" "$dir/bulletin.html" > "$json" || die "Bulletin $month sa nedá spracovať."
    if [ "$(python3 -I -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["fixes"]))' "$json")" = 0 ] && \
       grep -qE 'CVE-20[0-9]{2}-[0-9]{4,}' "$dir/bulletin.html"; then
      echo "  UPOZORNENIE: v bulletine sú čísla CVE, ale nenašla sa žiadna tabuľka opráv – zmenila sa stránka?"
      echo "  Pošlite výpis: grep -o 'CVE-20[0-9]*-[0-9]*' $dir/bulletin.html | sort -u | head"
    fi
    # One line per commit (fixes without an AOSP link get one empty line); checked with git, table by Python.
    : > "$dir/vysledky.tsv"
    python3 -I -c '
import json, sys
for f in json.load(open(sys.argv[1]))["fixes"]:
    for c in f["commits"] or [{"project": "", "sha": ""}]:
        print("\t".join([f["cve"], f["patch_level"], f["component"], f["severity"], c["project"], c["sha"]]))
' "$json" > "$dir/commity.tsv"
    local n=0 total cve level comp sev project sha path status patch
    total=$(wc -l < "$dir/commity.tsv")
    while IFS=$'\t' read -r cve level comp sev project sha; do
      n=$((n + 1))
      printf '\r  kontrolujem opravy: %d/%d ' "$n" "$total" >&2
      status=""; path=""
      if [ -z "$project" ]; then
        status="bez odkazu do AOSP"
      else
        path=$(printf '%s\n' "$projects" | awk -F' : ' -v n="$project" '$2 == n {print $1; exit}')
        if [ -z "$path" ]; then
          case "$project" in
            kernel/*) status="jadro – predzostavené jadro GrapheneOS" ;;
            *) status="projekt nie je v strome LECH OS" ;;
          esac
        elif ! git -C "$AOSP_DIR/$path" fetch -q --depth=2 "$GOOGLESOURCE/$project" "$sha" 2>/dev/null; then
          status="commit sa nedá stiahnuť"
        else
          patch="$dir/patches/$cve-${project//\//_}-${sha:0:12}.patch"
          git -C "$AOSP_DIR/$path" format-patch -1 --stdout FETCH_HEAD > "$patch" 2>/dev/null
          if git -C "$AOSP_DIR/$path" apply --check -R "$patch" 2>/dev/null; then status="už je"
          elif git -C "$AOSP_DIR/$path" apply --check "$patch" 2>/dev/null; then status="chýba – dá sa použiť"
          else status="chýba – konflikt (treba prispôsobiť)"
          fi
        fi
      fi
      printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$cve" "$level" "$comp" "$sev" "$project" "$sha" "$path" "$status" \
        >> "$dir/vysledky.tsv"
    done < "$dir/commity.tsv"
    printf '\r%*s\r' 40 "" >&2
    python3 -I - "$json" "$dir/vysledky.tsv" "$dir/stav.md" "$month" "$(patch_levels)" <<'PYEOF'
import json, sys
from collections import Counter
from datetime import datetime, date

json_path, tsv, out, month, levels = sys.argv[1:6]
data = json.load(open(json_path))
rows = [l.rstrip("\n").split("\t") for l in open(tsv, encoding="utf-8") if l.strip()]
published = data.get("published", "")
days = ""
try:
    d = datetime.strptime(published, "%B %d, %Y").date()
    days = f" ({(date.today() - d).days} dní od zverejnenia)"
except ValueError:
    pass
count = Counter(r[7] for r in rows)
crit = [r for r in rows if r[3] == "Critical" and not r[7].startswith("už je")]
lines = [f"# Android Security Bulletin {month} – stav v LECH OS", "",
         f"- Bulletin zverejnený: {published or '?'}{days}",
         f"- LECH OS (posledná zostava pre Pixel): úroveň zabezpečenia {levels}",
         f"- Opráv v bulletine: {len(data['fixes'])}, commitov AOSP: {sum(1 for r in rows if r[4])}", ""]
lines += ["| Stav | Počet |", "|---|---|"] + [f"| {k} | {v} |" for k, v in count.most_common()] + [""]
if crit:
    lines += ["**Kritické, ktoré v LECH OS chýbajú:** " + ", ".join(sorted({r[0] for r in crit})), ""]
lines += ["| CVE | Úroveň | Súčasť | Závažnosť | Projekt | Commit | Stav |", "|---|---|---|---|---|---|---|"]
for r in rows:
    lines.append(f"| {r[0]} | {r[1]} | {r[2]} | {r[3]} | {r[6] or r[4] or '–'} | {r[5][:12] or '–'} | {r[7]} |")
open(out, "w", encoding="utf-8").write("\n".join(lines) + "\n")
print(f"  zverejnený {published or '?'}{days}; opráv {len(data['fixes'])}")
for k, v in count.most_common():
    print(f"    {v:4d}  {k}")
if crit:
    print("  KRITICKÉ chýbajú: " + ", ".join(sorted({r[0] for r in crit})))
PYEOF
    echo "  Správa: $dir/stav.md"
    if lech_in_wsl && [ -d /mnt/c ]; then
      mkdir -p /mnt/c/lech/bezpecnost && cp "$dir/stav.md" "/mnt/c/lech/bezpecnost/stav-$month.md" && \
        echo "  Vo Windows: C:\\lech\\bezpecnost\\stav-$month.md"
    fi
  done

  echo
  echo "Vydania Androidu 17 v AOSP (značky platform/build, najnovšie):"
  git ls-remote --tags "$GOOGLESOURCE/platform/build" 'android-17*' 2>/dev/null | sed 's#.*refs/tags/##; s#\^{}##' \
    | sort -uV | tail -n 8 | sed 's/^/  /'
  echo "Strom LECH OS: build/make $(git -C "$AOSP_DIR/build/make" log -1 --format='%h %cd' --date=short 2>/dev/null)"
}

case "${1:-}" in
  stav) shift; cmd_stav "$@" ;;
  -h|--help|"") sed -n '2,15p' "$0" ;;
  *) die "Neznámy príkaz: $1 (pomoc: --help)" ;;
esac
