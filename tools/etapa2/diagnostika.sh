#!/usr/bin/env bash
# Compact diagnostics when a Cuttlefish virtual phone fails to start.
# Prints a short summary that fits on one screen and saves a fuller report to $LECH_WORK.
# Usage: tools/etapa2/diagnostika.sh

set -u

WORK="${LECH_WORK:-$HOME/lech-work}"
mkdir -p "$WORK"
REPORT="$WORK/diagnostika-$(date +%Y%m%d-%H%M%S).txt"
CVD_DIR="/var/tmp/cvd/$(id -u)"

latest() { [ $# -gt 0 ] || return 0; ls -t "$@" 2>/dev/null | head -1; }
errors() { grep -aiE "error|fail|fatal|denied|cannot|unable|panic|abort|no such|not found|timeout" "$1" 2>/dev/null | tail -n "$2"; }

# Find the logs first: the summary below runs in a pipeline (subshell), so variables set there are lost.
# shellcheck disable=SC2046
CVD_LOG=$(latest "$CVD_DIR"/logs/*.log)
# shellcheck disable=SC2046
LAUNCHER=$(latest $(find "$CVD_DIR" "$HOME" -maxdepth 6 -name launcher.log 2>/dev/null))
# shellcheck disable=SC2046
KERNEL=$(latest $(find "$CVD_DIR" "$HOME" -maxdepth 6 -name kernel.log 2>/dev/null))

{
  echo "## Systém"
  echo "jadro: $(uname -r)"
  grep -qi microsoft /proc/version 2>/dev/null && echo "WSL: áno" || echo "WSL: nie"
  echo "init (PID 1): $(ps -p 1 -o comm= 2>/dev/null)"
  echo "RAM: $(awk '/MemTotal/ {printf "%.1f GB", $2/1024/1024}' /proc/meminfo), voľná: $(awk '/MemAvailable/ {printf "%.1f GB", $2/1024/1024}' /proc/meminfo)"

  echo; echo "## Služba cuttlefish-host-resources"
  echo "stav: $(systemctl is-active cuttlefish-host-resources 2>&1)"
  systemctl status cuttlefish-host-resources --no-pager -n 4 2>&1 | tail -n 4

  echo; echo "## Sieť Cuttlefish"
  n=$(ip -br link 2>/dev/null | grep -c 'cvd-')
  echo "počet rozhraní cvd-*: $n"
  ip -br addr 2>/dev/null | grep -E 'cvd-(ebr|wbr)' | head -n 2

  echo; echo "## Zariadenia a skupiny"
  for d in /dev/kvm /dev/vhost-vsock /dev/vhost-net /dev/net/tun; do
    if [ -e "$d" ]; then
      if [ -r "$d" ] && [ -w "$d" ]; then echo "$d: OK"; else echo "$d: bez prístupu ($(stat -c '%U:%G %a' "$d"))"; fi
    else
      echo "$d: CHÝBA"
    fi
  done
  echo "skupiny: $(id -nG)"

  echo; echo "## Posledný log cvd: ${CVD_LOG:-nenájdený}"
  [ -n "$CVD_LOG" ] && errors "$CVD_LOG" 12

  echo; echo "## launcher.log: ${LAUNCHER:-nenájdený}"
  [ -n "$LAUNCHER" ] && errors "$LAUNCHER" 12

  echo; echo "## kernel.log (virtuálny telefón): ${KERNEL:-nenájdený}"
  [ -n "$KERNEL" ] && tail -n 4 "$KERNEL"
} 2>&1 | tee "$REPORT.short"

# Fuller report for upload if the summary is not enough.
{
  cat "$REPORT.short"
  for f in "$CVD_LOG" "$LAUNCHER" "$KERNEL"; do
    [ -n "$f" ] && [ -f "$f" ] && { echo; echo "===== $f (posledných 200 riadkov)"; tail -n 200 "$f"; }
  done
} > "$REPORT" 2>&1
rm -f "$REPORT.short"

echo
echo "Úplná správa: $REPORT"
if grep -qi microsoft /proc/version 2>/dev/null; then
  echo "Vo Windows ju nájdete v Prieskumníkovi: \\\\wsl.localhost\\${WSL_DISTRO_NAME:-Ubuntu-24.04}${REPORT//\//\\}"
fi
