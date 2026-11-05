#!/usr/bin/env bash
# Shows which app of the running virtual phone asked for which domain: the DNS resolver of the phone
# keeps its last 200 queries with the UID of the asking app (since the phone started). Android shows
# only the first letter of each name. Uses adb root, so it works on development (userdebug) builds.
# The raw query log and package list are saved in $LECH_WORK/kto-sa-pyta-<time>/.
#
# Usage: tools/etapa2/kto-sa-pyta.sh
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"

case "${1:-}" in
  -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
  "") ;;
  *) printf 'CHYBA: Neznámy parameter: %s\n' "$1" >&2; exit 1 ;;
esac

OUT="${LECH_WORK:-$HOME/lech-work}/kto-sa-pyta-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
if ! reason=$(lech_save_dns_by_app "$OUT" 2>&1); then
  printf 'CHYBA: DNS dopyty sa z telefónu nepodarilo načítať: %s\n' "$reason" >&2
  echo "Beží telefón? (tools/etapa2/06-zostav-lech.sh --spustit)  Surový výstup: $OUT/dns-dopyty.txt" >&2
  exit 1
fi

python3 -B - "$SCRIPT_DIR" "$OUT/dns-dopyty.txt" "$OUT/balicky.txt" <<'PYEOF'
import sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
import analyzuj_audit as audit  # noqa: E402

packages = audit.load_packages(Path(sys.argv[3]))
rows = audit.dns_by_app(Path(sys.argv[2]), packages, {})
print(f"DNS dopyty podľa aplikácií ({sum(r['pocet'] for r in rows)} dopytov, z názvu len prvé písmeno):")
print(f"  {'prvýkrát':12} {'dopyt':8} {'počet':>5}  aplikácia")
for r in rows:
    print(f"  {r['prvykrat']:12} {r['dopyt']:8} {r['pocet']:>5}  {r['aplikacia'].replace('`', '')}")
PYEOF
echo "Uložené: $OUT"
