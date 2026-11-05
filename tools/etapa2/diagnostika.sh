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

# For each SELinux seinfo in the built mac_permissions.xml files: which key it expects
# (LECH OS test key, public AOSP test key, or another one).
lech_mac_certs() {
  local aosp=${AOSP_DIR:-$HOME/aosp} keys=${LECH_KEYS:-$HOME/lech-keys/test}
  [ -d "$aosp/out" ] || { echo "zostava v $aosp/out nie je"; return 0; }
  find "$aosp/out/target/product" "$aosp/out/soong/.intermediates/system/sepolicy" -type f -name '*mac_permissions.xml' \
       -newer "$aosp/build/make/core/config.mk" 2>/dev/null | head -n 12 | python3 -c '
import re, subprocess, sys
from pathlib import Path
def der(pem):
    return subprocess.run(["openssl", "x509", "-in", str(pem), "-outform", "DER"], capture_output=True).stdout.hex()
known = {}
for label, d in (("AOSP", sys.argv[1]), ("LECH", sys.argv[2])):
    for pem in sorted(Path(d).glob("*.x509.pem")):
        known[der(pem)] = f"{label} {pem.name.split(".")[0]}"
seen = set()
for path in sys.stdin.read().split():
    text = Path(path).read_text(errors="replace")
    for sig, body in re.findall(r"<signer signature=\"([0-9a-fA-F]+)\"[^>]*>(.*?)</signer>", text, re.S):
        for seinfo in re.findall(r"seinfo value=\"([^\"]+)\"", body):
            line = f"  {seinfo:14} {known.get(sig.lower(), "iný kľúč"):24} {Path(path).name}"
            if line not in seen:
                seen.add(line); print(line)
if not seen:
    print("  mac_permissions.xml sa v out/ nenašiel")
' "$aosp/build/make/target/product/security" "$keys"
}
errors() { grep -aiE "error|fail|fatal|denied|cannot|unable|panic|abort|no such|not found|timeout" "$1" 2>/dev/null | tail -n "$2"; }

# Find the logs first: the summary below runs in a pipeline (subshell), so variables set there are lost.
# shellcheck disable=SC2046
CVD_LOG=$(latest "$CVD_DIR"/logs/*.log)
# shellcheck disable=SC2046
LAUNCHER=$(latest $(find "$CVD_DIR" "$HOME" -maxdepth 6 -name launcher.log 2>/dev/null))
# shellcheck disable=SC2046
KERNEL=$(latest $(find "$CVD_DIR" "$HOME" -maxdepth 6 -name kernel.log 2>/dev/null))
# shellcheck disable=SC2046
LOGCAT=$(latest $(find "$CVD_DIR" "$HOME" -maxdepth 8 -name logcat -type f 2>/dev/null))
# What usually explains a failed Android boot: crashes of system services, signature problems,
# APEX activation errors and the reason init gave for a reboot.
ANDROID_ERRORS='FATAL EXCEPTION|AndroidRuntime|SecurityException|[Ss]ignature|mismatch|apexd.*(rror|ail)|sys\.powerctl|reboot,|Boot failed|cannot be loaded|Watchdog'

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
  [ -n "$KERNEL" ] && grep -aE "init: .*(ail|rror|reboot)|Kernel panic|apexd" "$KERNEL" | tail -n 8
  [ -n "$KERNEL" ] && tail -n 4 "$KERNEL"

  echo; echo "## logcat (Android v telefóne): ${LOGCAT:-nenájdený}"
  [ -n "$LOGCAT" ] && grep -aE "$ANDROID_ERRORS" "$LOGCAT" | grep -av "F DEBUG *: *#" | cut -c1-200 | tail -n 15

  if [ -n "$LOGCAT" ]; then
    echo; echo "## Pády procesov (záznamy o páde)"
    grep -aE "DEBUG *: (Cmdline:|pid: [0-9]+, tid:|Abort message:|signal [0-9]+ )" "$LOGCAT" | cut -c1-200 | tail -n 8
    echo; echo "## Zamietnutia SELinux (avc: denied, posledné)"
    grep -a "avc: *denied" "$LOGCAT" | sed -E 's/.*avc: *denied *//' | cut -c1-200 | sort | uniq -c | sort -rn | head -n 6
  fi

  echo; echo "## Certifikáty SELinux v zostave (mac_permissions.xml)"
  lech_mac_certs
} 2>&1 | tee "$REPORT.short"

# Fuller report for upload if the summary is not enough.
{
  cat "$REPORT.short"
  for f in "$CVD_LOG" "$LAUNCHER" "$KERNEL"; do
    [ -n "$f" ] && [ -f "$f" ] && { echo; echo "===== $f (posledných 200 riadkov)"; tail -n 200 "$f"; }
  done
  if [ -n "$LOGCAT" ] && [ -f "$LOGCAT" ]; then
    echo; echo "===== $LOGCAT (chyby, najviac 300 riadkov)"; grep -aE "$ANDROID_ERRORS| E |F " "$LOGCAT" | tail -n 300
    echo; echo "===== $LOGCAT (posledných 300 riadkov)"; tail -n 300 "$LOGCAT"
  fi
} > "$REPORT" 2>&1
rm -f "$REPORT.short"

echo
echo "Úplná správa: $REPORT"
if grep -qi microsoft /proc/version 2>/dev/null; then
  echo "Vo Windows ju nájdete v Prieskumníkovi: \\\\wsl.localhost\\${WSL_DISTRO_NAME:-Ubuntu-24.04}${REPORT//\//\\}"
fi
