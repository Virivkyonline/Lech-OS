#!/usr/bin/env bash
# Nightly check of LECH OS on this computer (task E2-09, CI v0). Steps:
#   1. updates the repository (git pull --ff-only) and runs the newest version of this script,
#   2. checks of the repository (shell and Python syntax, catalogue of AI actions),
#   3. builds LECH OS, starts the phone and checks it (06-zostav-lech.sh --spustit),
#   4. network audit of a fresh phone (03-sietovy-audit.sh): fails on any connection that is not allowed,
#   5. test of the apps from F-Droid (10-test-aplikacii.sh),
# and writes a summary with OK / UPOZORNENIE / CHYBA for every step.
# Started every night by the Windows Task Scheduler (09-nastav-nocnu-kontrolu.sh) or by hand.
#
# Usage: tools/etapa2/09-nocna-kontrola.sh [--minuty N] [--bez-aktualizacie] [--bez-auditu] [--bez-aplikacii]
#   --minuty N            length of the network audit (default 30)
#   --bez-aktualizacie    do not update the repository
#   --bez-auditu          skip the network audit
#   --bez-aplikacii       skip the app test
# Results: $LECH_WORK/ci/<time>/ (suhrn.md, logs, reports); $LECH_WORK/ci/posledna.md = latest summary,
# $LECH_WORK/ci/historia.txt = one line per run. Exit code 0 = no step failed.

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"

WORKDIR=${LECH_WORK:-$HOME/lech-work}
CI_ROOT="$WORKDIR/ci"
MINUTES=30
PULL=1
AFTER_PULL=0
AUDIT=1
APPS=1
ORIG_ARGS=("$@")

while [ $# -gt 0 ]; do
  case "$1" in
    --minuty) MINUTES=${2:?chýba počet minút}; shift 2 ;;
    --bez-aktualizacie) PULL=0; shift ;;
    --po-aktualizacii) PULL=0; AFTER_PULL=1; shift ;;   # internal: started again after git pull
    --bez-auditu) AUDIT=0; shift ;;
    --bez-aplikacii) APPS=0; shift ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "CHYBA: Neznámy parameter: $1 (pomoc: --help)" >&2; exit 2 ;;
  esac
done
case "$MINUTES" in ''|*[!0-9]*) echo "CHYBA: --minuty očakáva celé číslo." >&2; exit 2 ;; esac
mkdir -p "$CI_ROOT"

# 1. Update first, then continue with the updated script (bash must not run a file that git replaced).
if [ "$PULL" -eq 1 ]; then
  git -C "$REPO_DIR" pull --ff-only > "$CI_ROOT/.git-pull.log" 2>&1
  echo $? > "$CI_ROOT/.git-pull.status"
  exec "$REPO_DIR/tools/etapa2/09-nocna-kontrola.sh" --po-aktualizacii ${ORIG_ARGS[@]+"${ORIG_ARGS[@]}"}
fi

# The same lock is taken by other builds in the AOSP tree (11-zostav-gsi.sh): they must not share out/.
exec 9> "$CI_ROOT/.zamok"
if ! flock -n 9; then
  echo "Nočná kontrola už beží alebo prebieha iné zostavenie (11-zostav-gsi.sh) – dnes sa preskočí." >&2
  echo "$(date +%Y%m%d-%H%M%S) PRESKOČENÉ (beží iné zostavenie)" >> "$CI_ROOT/historia.txt"
  exit 2
fi

STAMP=$(date +%Y%m%d-%H%M%S)
RUN="$CI_ROOT/$STAMP"
mkdir -p "$RUN"
COMMIT=$(git -C "$REPO_DIR" log -1 --format='%h %s' 2>/dev/null | cut -c1-70)
STEPS=()
overall=OK

record() {   # status, step, start time (epoch), detail
  local mins=$(( ( $(date +%s) - $3 ) / 60 ))
  STEPS+=("$1|$2|$mins|$4")
  case "$1" in CHYBA) overall=CHYBA ;; UPOZORNENIE) [ "$overall" = OK ] && overall=UPOZORNENIE ;; esac
  line "$1" "$2" "$(printf '%3d min' "$mins")" "$4"
}
skip() { STEPS+=("PRESKOČENÉ|$1|0|$2"); line "PRESKOČENÉ" "$1" "" "$2"; }
line() {   # columns padded by characters, not bytes (Slovak letters take two bytes)
  printf '%s%*s %s%*s %7s  %s\n' "$1" $(( 12 - ${#1} )) "" "$2" $(( 26 - ${#2} )) "" "$3" "$4"
}

echo "Nočná kontrola LECH OS – $(date '+%d. %m. %Y %H:%M'), commit $COMMIT"
echo "Výsledky: $RUN"

# 1. Repository update (done above, before the restart of this script).
if [ "$AFTER_PULL" -eq 1 ]; then
  t=$(date +%s)
  cp "$CI_ROOT/.git-pull.log" "$RUN/git.log" 2>/dev/null
  if [ "$(cat "$CI_ROOT/.git-pull.status" 2>/dev/null)" = 0 ]; then
    record OK "Aktualizácia repozitára" "$t" "$(tail -n 1 "$RUN/git.log" | cut -c1-60)"
  else
    record UPOZORNENIE "Aktualizácia repozitára" "$t" "git pull zlyhal, kontrolujem aktuálny stav (git.log)"
  fi
fi

# 2. Checks of the repository.
t=$(date +%s)
errors=0
{
  for f in "$REPO_DIR"/tools/*.sh "$REPO_DIR"/tools/etapa2/*.sh; do bash -n "$f" || { echo "syntax: $f"; errors=$((errors + 1)); }; done
  python3 -B -c 'import ast, sys
bad = 0
for f in sys.argv[1:]:
    try:
        ast.parse(open(f, encoding="utf-8").read(), f)
    except SyntaxError as e:
        print("syntax:", e); bad += 1
sys.exit(1 if bad else 0)' "$REPO_DIR"/tools/*.py "$REPO_DIR"/tools/etapa2/*.py || errors=$((errors + 1))
  python3 -B "$REPO_DIR/tools/validate_ai_actions.py" || errors=$((errors + 1))
} > "$RUN/kontroly.log" 2>&1
if [ "$errors" -eq 0 ]; then
  record OK "Kontroly repozitára" "$t" "syntax skriptov, katalóg AI akcií"
else
  record CHYBA "Kontroly repozitára" "$t" "$errors chýb (kontroly.log)"
fi

# 3. Build, start and identity of the phone.
t=$(date +%s)
built=0
if "$SCRIPT_DIR/06-zostav-lech.sh" --spustit > "$RUN/zostavenie.log" 2>&1; then
  built=1
  record OK "Zostavenie a štart" "$t" "$(grep -m1 -o 'Zostavenie úspešné za [0-9]* min' "$RUN/zostavenie.log"); kontroly telefónu OK"
else
  failed=$(grep -E '^  (CHYBA)' "$RUN/zostavenie.log" | head -n 3 | sed 's/  */ /g' | tr '\n' ';')
  grep -q "Zostavenie úspešné" "$RUN/zostavenie.log" && built=1
  record CHYBA "Zostavenie a štart" "$t" "${failed:-$(grep -aE 'CHYBA|FAILED' "$RUN/zostavenie.log" | tail -n 1 | cut -c1-80)} (zostavenie.log)"
fi

# 4. Network audit of a fresh phone.
if [ "$AUDIT" -eq 0 ]; then
  skip "Sieťový audit" "--bez-auditu"
elif [ "$built" -eq 0 ]; then
  skip "Sieťový audit" "zostavenie zlyhalo"
else
  t=$(date +%s)
  if "$SCRIPT_DIR/03-sietovy-audit.sh" --lokalna-zostava --ano --ponechat --minuty "$MINUTES" --sprava "$RUN/audit" \
       > "$RUN/audit.log" 2>&1 && [ -f "$RUN/audit/sprava.json" ]; then
    verdict=$(python3 -B - "$RUN/audit/sprava.json" <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
phone = {"mobilná sieť telefónu", "ethernet telefónu"}
# The OpenWrt router of the test environment asks 8.8.8.8 for the NTP pool (docs/11, audit/README.md).
expected_router = {("8.8.8.8", 53), ("8.8.4.4", 53)}
domains = [x["domena"] for x in d.get("domeny", []) if x.get("povolene") is False]
phone_bad = [f'{x["ip"]}:{x["port"]}' for x in d.get("ciele", []) if x.get("povolene") is False and set(x["odkial"]) & phone]
router = [x for x in d.get("ciele", []) if x.get("povolene") is False and not set(x["odkial"]) & phone]
router_other = [f'{x["ip"]}:{x["port"]}' for x in router if (x["ip"], x["port"]) not in expected_router]
if domains or phone_bad:
    print("CHYBA|neznáme: " + ", ".join(domains + phone_bad)[:150])
elif router_other:
    print("UPOZORNENIE|neznáme cez sieť routra: " + ", ".join(router_other)[:150])
else:
    print(f"OK|{len(d.get('ciele', []))} cieľov, žiadne nepovolené spojenie telefónu")
PYEOF
)
    record "${verdict%%|*}" "Sieťový audit (${MINUTES} min)" "$t" "${verdict#*|}"
  else
    record CHYBA "Sieťový audit (${MINUTES} min)" "$t" "audit sa nedokončil (audit.log)"
  fi
fi

# 5. Apps from F-Droid (on the phone from the audit, or from step 3).
if [ "$APPS" -eq 0 ]; then
  skip "Aplikácie z F-Droid" "--bez-aplikacii"
elif [ "$built" -eq 0 ]; then
  skip "Aplikácie z F-Droid" "zostavenie zlyhalo"
else
  t=$(date +%s)
  if "$SCRIPT_DIR/10-test-aplikacii.sh" --sprava "$RUN/aplikacie" > "$RUN/aplikacie.log" 2>&1 \
       && [ -f "$RUN/aplikacie/sprava.json" ]; then
    verdict=$(python3 -B - "$RUN/aplikacie/sprava.json" <<'PYEOF'
import json, sys
apps = json.load(open(sys.argv[1], encoding="utf-8"))["aplikacie"]
bad = [a["name"] for a in apps if a.get("instalacia") != "OK" or not str(a.get("spustenie", "")).startswith("OK")]
ok = len(apps) - len(bad)
print(("OK" if not bad else "CHYBA") + f"|{ok} z {len(apps)} OK" + (": nefunguje " + ", ".join(bad) if bad else ""))
PYEOF
)
    record "${verdict%%|*}" "Aplikácie z F-Droid" "$t" "${verdict#*|}"
  else
    record CHYBA "Aplikácie z F-Droid" "$t" "test sa nedokončil (aplikacie.log)"
  fi
fi

lech_reset_phones

# Summary.
{
  echo "# Nočná kontrola LECH OS – $(date '+%d. %m. %Y %H:%M')"
  echo
  echo "- Výsledok: **$overall**"
  echo "- Commit: \`$COMMIT\`"
  echo "- Výsledky a logy: \`$RUN\`"
  echo
  echo "| Krok | Výsledok | Trvanie | Podrobnosti |"
  echo "|---|---|---|---|"
  for s in "${STEPS[@]}"; do
    IFS='|' read -r st name mins detail <<< "$s"
    echo "| $name | $st | $mins min | ${detail//|/\\|} |"
  done
} > "$RUN/suhrn.md"
cp "$RUN/suhrn.md" "$CI_ROOT/posledna.md"
echo "$STAMP $overall ${COMMIT%% *}" >> "$CI_ROOT/historia.txt"

echo
echo "Výsledok nočnej kontroly: $overall – $RUN/suhrn.md"
if lech_in_wsl; then
  echo "Vo Windows: \\\\wsl.localhost\\${WSL_DISTRO_NAME:-Ubuntu-24.04}${CI_ROOT//\//\\}\\posledna.md"
fi
[ "$overall" != CHYBA ]
