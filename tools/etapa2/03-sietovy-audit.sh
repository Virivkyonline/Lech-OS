#!/usr/bin/env bash
# Network audit of the first boot of a virtual phone (task E2-03).
# Starts a packet capture of the Cuttlefish guest networks BEFORE the phone boots, creates a fresh phone
# from prebuilt AOSP images (or from a local build), waits, stops the capture and writes a report into
# the repository (audit/). The large capture file stays in $LECH_WORK.
#
# Usage: tools/etapa2/03-sietovy-audit.sh [--minuty N] [--lokalna-zostava] [--ponechat] [--ano]
#   --minuty N          how long to record after the phone has booted (default 30)
#   --lokalna-zostava   use the local build (from 05-zostav-aosp.sh) instead of prebuilt images
#   --ponechat          keep the phone running after the audit (default: remove it)
#   --ano               do not ask before removing existing virtual phones

set -euo pipefail

MINUTES=30
LOCAL_BUILD=0
KEEP=0
YES=0
FILTER="net 192.168.93.0/24 or net 192.168.94.0/23 or net 192.168.96.0/22"

REPO_DIR=$(cd "$(dirname "$0")/../.." && pwd)
. "$REPO_DIR/tools/etapa2/lib-cuttlefish.sh"
STAMP=$(date +%Y%m%d-%H%M%S)
WORK="${LECH_WORK:-$HOME/lech-work}/audit-$STAMP"

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --minuty) MINUTES=${2:?chýba počet minút}; shift 2 ;;
    --lokalna-zostava) LOCAL_BUILD=1; shift ;;
    --ponechat) KEEP=1; shift ;;
    --ano) YES=1; shift ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done
case "$MINUTES" in ''|*[!0-9]*) die "--minuty očakáva celé číslo." ;; esac

for c in cvd tcpdump tshark python3; do
  command -v "$c" >/dev/null 2>&1 || die "Chýba príkaz $c – spustite tools/etapa2/01-priprava-notebooku.sh."
done
[ -r /dev/kvm ] && [ -w /dev/kvm ] || die "Nie je prístup k /dev/kvm."
if [ "$LOCAL_BUILD" -eq 1 ] && [ -z "${ANDROID_PRODUCT_OUT:-}" ]; then
  lech_enter_build_env || die "Pre --lokalna-zostava treba najprv zostaviť systém (tools/etapa2/05-zostav-aosp.sh)."
fi

if [ "$YES" -ne 1 ]; then
  printf 'Audit potrebuje čerstvý telefón. Všetky existujúce virtuálne telefóny sa odstránia (cvd reset). Pokračovať? [a/N] '
  read -r answer
  case "$answer" in a|A|ano|áno) ;; *) echo "Zrušené."; exit 0 ;; esac
fi

# Download the images before the capture starts (the download is host traffic, not the phone's).
if [ "$LOCAL_BUILD" -ne 1 ]; then
  lech_ensure_images
fi

sudo -v
mkdir -p "$WORK"
PCAP="$WORK/prve-spustenie.pcap"   # .pcap suffix: Ubuntu's AppArmor profile lets tcpdump write such files

echo "Odstraňujem existujúce virtuálne telefóny…"
lech_reset_phones

echo "Spúšťam záznam prevádzky virtuálneho telefónu…"
sudo tcpdump -i any -n -U -Z "$USER" -w "$PCAP" "$FILTER" 2>"$WORK/tcpdump.log" &
TCPDUMP_PID=$!
# tcpdump drops privileges to $USER (-Z), so it can be stopped without sudo even if the sudo timestamp expired.
cleanup() {
  local pids
  pids=$(pgrep -u "$(id -u)" -f -- "^tcpdump .*-w $PCAP" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    # shellcheck disable=SC2086
    kill -INT $pids 2>/dev/null || true
  else
    sudo kill -INT "$TCPDUMP_PID" 2>/dev/null || true
  fi
  wait "$TCPDUMP_PID" 2>/dev/null || true
}
trap cleanup EXIT
sleep 3
kill -0 "$TCPDUMP_PID" 2>/dev/null || die "Záznam sa nespustil – pozri $WORK/tcpdump.log"

echo "Vytváram čerstvý virtuálny telefón…"
lech_describe_start
if [ "$LOCAL_BUILD" -eq 1 ]; then
  SOURCE="lokálna zostava ($(basename "$ANDROID_PRODUCT_OUT"))"
  lech_create_phone --local
else
  SOURCE="hotové obrazy $LECH_BUILD"
  lech_create_phone
fi

echo "Telefón beží. Zaznamenávam ${MINUTES} minút (obrazovka: https://localhost:8443; počas auditu na telefón nesiahajte)."
for ((i = MINUTES; i > 0; i--)); do
  printf '\r  zostáva %3d min ' "$i"
  sleep 60
done
echo

cleanup
trap - EXIT

if [ "$KEEP" -ne 1 ]; then
  lech_reset_phones
fi

REPORT="$REPO_DIR/audit/$STAMP-prve-spustenie/sprava.md"
python3 "$REPO_DIR/tools/etapa2/analyzuj_audit.py" "$PCAP" --out "$REPORT" --ptr \
  --allowlist "$REPO_DIR/spec/povolene-spojenia.txt" \
  --title "Sieťový audit prvého spustenia – $SOURCE"

cat <<EOF

Hotovo.
  Správa do repozitára: $REPORT
  Zachytená prevádzka:  $PCAP (zostáva mimo repozitára)
Správu skontrolujte a pridajte do gitu (git add audit/ && git commit).
EOF
