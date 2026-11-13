#!/usr/bin/env bash
# LECH OS on GrapheneOS (ADR-0012): runs a script of tools/etapa2 against the GrapheneOS tree with its own work
# directory, so the AOSP line (~/aosp, ~/lech-work) stays untouched.
#
# Usage: tools/etapa2/gos.sh SKRIPT [parametre]     e.g. tools/etapa2/gos.sh 12-priprav-pixel.sh
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
[ $# -ge 1 ] || { sed -n '2,6p' "$0"; exit 1; }
script=$1; shift
[ -x "$SCRIPT_DIR/$script" ] || { echo "CHYBA: $SCRIPT_DIR/$script neexistuje."; exit 1; }
export LECH_BASE=gos
export AOSP_DIR=${GOS_DIR:-$HOME/gos}
export LECH_WORK=${LECH_WORK_GOS:-$HOME/lech-work-gos}
[ -d /mnt/c/lech ] && export LECH_PIXEL_FULL_DIR=${LECH_PIXEL_FULL_DIR:-/mnt/c/lech/pixel-gos}
mkdir -p "$LECH_WORK"
exec "$SCRIPT_DIR/$script" "$@"
