#!/usr/bin/env bash
# Checks whether a machine (server or notebook) is ready for LECH OS stage 2.
# It reports two separate verdicts:
#   1. Building AOSP / LECH OS from source
#   2. Running Cuttlefish from prebuilt AOSP images (no build needed)
# Usage: tools/check_build_host.sh            (checks free space in the current directory)
#        AOSP_DIR=/srv/aosp tools/check_build_host.sh
# Exit code: 0 = ready for both, 2 = only prebuilt Cuttlefish images can run, 1 = neither.
# Thresholds follow docs/11-etapa-2-cuttlefish.md.

set -u

BUILD_MIN_RAM_GB=24       # with >= 32 GB swap and fewer parallel jobs (e.g. WSL on a 32 GB notebook); Google recommends 64 GB
BUILD_MIN_TOTAL_GB=56     # RAM + swap needed when RAM is below BUILD_GOOD_RAM_GB
BUILD_GOOD_RAM_GB=64
BUILD_MIN_DISK_GB=300     # shallow checkout (04-stiahni-aosp.sh default) + one Cuttlefish build; tight
BUILD_GOOD_DISK_GB=400    # official figure for full sources with history
RUN_MIN_RAM_GB=16
RUN_MIN_DISK_GB=50
GOOD_CPUS=16

build_ok=1
run_ok=1
pass() { printf '  [OK]    %s\n' "$1"; }
warn() { printf '  [POZOR] %s\n' "$1"; }
bad()  { printf '  [CHYBA] %s\n' "$1"; }

echo "Kontrola počítača pre etapu 2 LECH OS"
echo

# Architecture
arch=$(uname -m)
if [ "$arch" = "x86_64" ]; then pass "Architektúra: $arch"; else bad "Architektúra $arch – návod počíta s x86_64"; build_ok=0; run_ok=0; fi

# Operating system
if [ -r /etc/os-release ]; then
  . /etc/os-release
  case "${ID:-}" in
    ubuntu|debian) pass "Systém: ${PRETTY_NAME:-$ID}" ;;
    *) warn "Systém ${PRETTY_NAME:-neznámy} – odporúčané je Ubuntu LTS nainštalované priamo (nie vo virtuálnom stroji)" ;;
  esac
else
  warn "Nepodarilo sa zistiť operačný systém"
fi
in_wsl=0
if grep -qi microsoft /proc/version 2>/dev/null; then
  in_wsl=1
  warn "Beží vo WSL (Windows) – zostavenie je možné, Cuttlefish vo WSL oficiálne podporovaný nie je"
fi

# Time sync conflict under WSL (see docs/11): systemd-timesyncd + Hyper-V time sync step the clock back and forth.
if [ "$in_wsl" -eq 1 ] && systemctl is-active systemd-timesyncd.service >/dev/null 2>&1; then
  warn "Vo WSL beží systemd-timesyncd – hodiny sa môžu posúvať a zostavenie padať (future mtime); vypnite: sudo systemctl disable --now systemd-timesyncd"
fi

# CPU
cpus=$(nproc 2>/dev/null || echo 0)
if [ "$cpus" -ge "$GOOD_CPUS" ]; then pass "Procesor: $cpus vlákien"; else warn "Procesor: $cpus vlákien – prvé zostavenie potrvá niekoľko hodín (odporúčané ${GOOD_CPUS}+)"; fi

# RAM and swap
ram_kb=$(awk '/^MemTotal:/ {print $2}' /proc/meminfo 2>/dev/null || echo 0)
swap_kb=$(awk '/^SwapTotal:/ {print $2}' /proc/meminfo 2>/dev/null || echo 0)
ram_gb=$(( (ram_kb + 524288) / 1024 / 1024 ))
swap_gb=$(( swap_kb / 1024 / 1024 ))
# The OS reports less than the nominal size (firmware, integrated graphics), so allow 10 % tolerance.
has_ram() { [ "$ram_kb" -ge $(( $1 * 1024 * 1024 * 9 / 10 )) ]; }
if has_ram "$BUILD_GOOD_RAM_GB"; then
  pass "RAM: ${ram_gb} GB"
elif has_ram "$BUILD_MIN_RAM_GB"; then
  if [ $(( ram_kb + swap_kb )) -ge $(( BUILD_MIN_TOTAL_GB * 1024 * 1024 * 9 / 10 )) ]; then
    warn "RAM: ${ram_gb} GB + swap ${swap_gb} GB – zostavenie pôjde, pomalšie; zostavujte s menším počtom úloh (napr. m -j8)"
  else
    bad "RAM: ${ram_gb} GB, swap ${swap_gb} GB – pred zostavením zväčšite swap aspoň na $(( BUILD_MIN_TOTAL_GB - ram_gb )) GB (vo WSL v súbore .wslconfig, docs/11)"
    build_ok=0
  fi
elif has_ram "$RUN_MIN_RAM_GB"; then
  bad "RAM: ${ram_gb} GB – na zostavenie AOSP nestačí (minimum ${BUILD_MIN_RAM_GB} GB); na spustenie hotových obrazov Cuttlefish stačí"
  if [ "$in_wsl" -eq 1 ]; then
    warn "WSL predvolene dostane len polovicu RAM počítača – limit sa dá zvýšiť v súbore .wslconfig vo Windows (docs/11)"
  fi
  build_ok=0
else
  bad "RAM: ${ram_gb} GB – nestačí ani na spustenie Cuttlefish (minimum ${RUN_MIN_RAM_GB} GB)"
  build_ok=0; run_ok=0
fi

# Free disk space in the target directory
target_dir=${AOSP_DIR:-$PWD}
disk_gb=$(df -Pk "$target_dir" 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')
disk_gb=${disk_gb:-0}
if [ "$disk_gb" -ge "$BUILD_GOOD_DISK_GB" ]; then
  pass "Voľné miesto v $target_dir: ${disk_gb} GB"
elif [ "$disk_gb" -ge "$BUILD_MIN_DISK_GB" ]; then
  warn "Voľné miesto v $target_dir: ${disk_gb} GB – stačí pri sťahovaní bez histórie (tools/etapa2/04-stiahni-aosp.sh), ale tesne"
elif [ "$disk_gb" -ge "$RUN_MIN_DISK_GB" ]; then
  bad "Voľné miesto v $target_dir: ${disk_gb} GB – na zostavenie treba ${BUILD_MIN_DISK_GB} GB; na hotové obrazy stačí (cieľový priečinok nastavíte premennou AOSP_DIR)"
  build_ok=0
else
  bad "Voľné miesto v $target_dir: ${disk_gb} GB – nestačí ani na hotové obrazy (minimum ${RUN_MIN_DISK_GB} GB)"
  build_ok=0; run_ok=0
fi
rota=$(lsblk -dno ROTA "$(df -P "$target_dir" 2>/dev/null | awk 'NR==2 {print $1}')" 2>/dev/null | head -1 | tr -d ' ')
if [ "$in_wsl" -eq 1 ]; then
  warn "Vo WSL je disk virtuálny – typ disku (SSD/HDD) sa nedá zistiť"
  # The WSL virtual disk is a file on a Windows drive (C: by default, docs/11 part 4j: its own partition), so that
  # drive's free space is the real limit.
  win_mnt=/mnt/c
  if [ -f "$(dirname "$0")/etapa2/lib-cuttlefish.sh" ]; then
    # shellcheck source=etapa2/lib-cuttlefish.sh
    . "$(dirname "$0")/etapa2/lib-cuttlefish.sh"
    win_mnt=$(lech_wsl_drive_mnt)
  fi
  win_drive=${win_mnt#/mnt/}; win_drive="${win_drive^^}:"
  win_gb=$(df -Pk "$win_mnt" 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')
  if [ -n "${win_gb:-}" ]; then
    if [ "$win_gb" -ge "$BUILD_GOOD_DISK_GB" ]; then
      pass "Voľné miesto na disku $win_drive vo Windows (tam leží disk WSL): ${win_gb} GB"
    elif [ "$win_gb" -ge "$BUILD_MIN_DISK_GB" ]; then
      warn "Voľné miesto na disku $win_drive vo Windows: ${win_gb} GB – stačí pri sťahovaní bez histórie, ale tesne; nechajte Windows aspoň 30 GB voľných"
    elif [ "$win_gb" -ge "$RUN_MIN_DISK_GB" ]; then
      bad "Voľné miesto na disku $win_drive vo Windows: ${win_gb} GB – disk WSL tam leží, na zostavenie treba ${BUILD_MIN_DISK_GB} GB"
      build_ok=0
    else
      bad "Voľné miesto na disku $win_drive vo Windows: ${win_gb} GB – nestačí ani na hotové obrazy"
      build_ok=0; run_ok=0
    fi
  fi
elif [ "${rota:-}" = "1" ]; then
  warn "Disk je rotačný (HDD) – zostavenie bude veľmi pomalé, odporúčaný je NVMe SSD"
fi

# Virtualization for Cuttlefish
virt=$(grep -c -w -E 'vmx|svm' /proc/cpuinfo 2>/dev/null || true)
if [ "${virt:-0}" -gt 0 ]; then pass "Procesor hlási virtualizáciu (vmx/svm)"; else bad "Procesor nehlási virtualizáciu (vmx/svm) – zapnite ju v BIOS/UEFI; na VPS býva nedostupná"; run_ok=0; fi
if [ -e /dev/kvm ]; then
  if [ -r /dev/kvm ] && [ -w /dev/kvm ]; then pass "/dev/kvm je dostupné"; else warn "/dev/kvm existuje, ale používateľ k nemu nemá prístup – pridajte ho do skupiny kvm"; fi
else
  bad "/dev/kvm neexistuje – Cuttlefish sa nespustí"
  run_ok=0
fi
# Cuttlefish (crosvm) needs vhost-vsock for adb and vhost-net for networking.
for dev in vhost-vsock vhost-net; do
  if [ -e "/dev/$dev" ]; then
    pass "/dev/$dev je dostupné"
  elif [ "$in_wsl" -eq 1 ]; then
    bad "/dev/$dev chýba – skúste: sudo modprobe $(echo "$dev" | tr - _); ak to zlyhá, jadro WSL ho neposkytuje a treba Ubuntu priamo na disku"
    run_ok=0
  else
    warn "/dev/$dev chýba – zvyčajne ho zapne inštalácia balíkov Cuttlefish (modul jadra $(echo "$dev" | tr - _))"
  fi
done

# Cuttlefish user groups (after installing cuttlefish-base / cuttlefish-user)
user_groups=" $(id -nG 2>/dev/null) "
for g in kvm cvdnetwork render; do
  case "$user_groups" in
    *" $g "*) pass "Používateľ je v skupine $g" ;;
    *) warn "Používateľ nie je v skupine $g (doplní sa po inštalácii balíkov Cuttlefish a reštarte)" ;;
  esac
done

# Tools
for tool in git python3 curl; do
  if command -v "$tool" >/dev/null 2>&1; then pass "Nástroj $tool je nainštalovaný"; else bad "Chýba nástroj $tool"; build_ok=0; fi
done
if command -v repo >/dev/null 2>&1; then pass "Nástroj repo je nainštalovaný"; else warn "Chýba nástroj repo (inštalácia v docs/11-etapa-2-cuttlefish.md)"; fi

echo
if [ "$build_ok" -eq 1 ] && [ "$run_ok" -eq 1 ]; then
  echo "Výsledok: počítač zvládne zostavenie LECH OS aj spustenie Cuttlefish."
  exit 0
elif [ "$run_ok" -eq 1 ]; then
  echo "Výsledok: na zostavenie nestačí, ale spustí Cuttlefish z hotových obrazov AOSP (docs/11, časť 2b)."
  exit 2
else
  echo "Výsledok: počítač nestačí na zostavenie ani na Cuttlefish – pozri riadky [CHYBA]."
  exit 1
fi
