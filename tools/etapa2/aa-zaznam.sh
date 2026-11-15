#!/usr/bin/env bash
# Android Auto diagnostics: pulls the log of the phone and keeps what concerns Android Auto and the sandboxed
# Google Play layer (errors and warnings of their processes, permission denials, crashes). The result goes to the
# Windows clipboard, so it can be pasted as text.
#
# Usage: tools/etapa2/aa-zaznam.sh priprav        before the attempt: bigger log buffer, cleared log
#        tools/etapa2/aa-zaznam.sh [HH:MM]        after the attempt: summary (only lines from HH:MM on)
set -u
ADB=${ADB:-/mnt/c/lech/platform-tools/adb.exe -P 5038}
OUT=${OUT:-$HOME/aa.log}
RES=${RES:-$HOME/aa-vypis.txt}

if [ "${1:-}" = priprav ]; then
  $ADB logcat -G 16M && $ADB logcat -c && echo "Záznam je vyčistený a zväčšený. Teraz skúste pripojenie v aute."
  exit
fi

FROM=${1:-00:00}
$ADB logcat -d | tr -d '\r' > "$OUT"
{
  echo "zaznam od: $(grep -m1 -oE '^[0-9-]+ [0-9:]+' "$OUT")  do: $(tail -1 "$OUT" | grep -oE '^[0-9-]+ [0-9:]+')  riadkov: $(wc -l < "$OUT")"
  awk -v from="$FROM" '
    BEGIN { pkg = "com.google.android.projection.gearhead|app.grapheneos.gmscompat" }
    substr($2, 1, 5) < from { next }
    # processes of Android Auto and of the compatibility layer, by the "Start proc" lines of the system
    /Start proc [0-9]+:/ && $0 ~ pkg { split($0, a, "Start proc "); split(a[2], b, ":"); pids[b[1]] = 1 }
    /ProcStatsManager|ProcessStats|NotifRow/ { next }
    ($3 in pids) && ($5 == "E" || $5 == "W" || $5 == "F") { print; next }
    /FATAL EXCEPTION|Permission Denial|SecurityException|Access denied|avc: +denied/ { print; next }
    /Start proc|has died|Process .* \(pid/ && $0 ~ pkg { print; next }
    /GmcDynStub|GmsCompat/ && ($5 == "E" || $5 == "W") { print }
  ' "$OUT" | cut -c1-330 | tail -120
} > "$RES"
cat "$RES"
clip.exe < "$RES" 2>/dev/null && echo "--- Výpis je v schránke: vložte ho do správy cez Ctrl+V. ---"
