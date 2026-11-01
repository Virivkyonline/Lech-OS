#!/usr/bin/env bash
# Checks whether a machine is ready to build AOSP and run Cuttlefish for LECH OS (stage 2).
# Usage: tools/check_build_host.sh
# Exit code 0 = all required checks passed, 1 = at least one required check failed.
# Thresholds follow docs/11-etapa-2-cuttlefish.md.

set -u

MIN_RAM_GB=64
MIN_DISK_GB=400
MIN_CPUS=16

fail=0
pass() { printf '  [OK]    %s\n' "$1"; }
warn() { printf '  [POZOR] %s\n' "$1"; }
bad()  { printf '  [CHYBA] %s\n' "$1"; fail=1; }

echo "Kontrola zostavovacieho servera LECH OS"
echo

# Architecture
arch=$(uname -m)
if [ "$arch" = "x86_64" ]; then pass "Architektúra: $arch"; else bad "Architektúra $arch – návod počíta s x86_64"; fi

# Operating system
if [ -r /etc/os-release ]; then
  . /etc/os-release
  case "${ID:-}" in
    ubuntu|debian) pass "Systém: ${PRETTY_NAME:-$ID}" ;;
    *) warn "Systém ${PRETTY_NAME:-neznámy} – odporúčané je Ubuntu LTS (balíky Cuttlefish sú pre Debian/Ubuntu)" ;;
  esac
else
  warn "Nepodarilo sa zistiť operačný systém"
fi

# CPU
cpus=$(nproc 2>/dev/null || echo 0)
if [ "$cpus" -ge "$MIN_CPUS" ]; then pass "Procesor: $cpus vlákien"; else warn "Procesor: $cpus vlákien – zostavenie bude pomalé (odporúčané ${MIN_CPUS}+)"; fi

# RAM
ram_kb=$(awk '/^MemTotal:/ {print $2}' /proc/meminfo 2>/dev/null || echo 0)
ram_gb=$(( ram_kb / 1024 / 1024 ))
if [ "$ram_gb" -ge "$MIN_RAM_GB" ]; then pass "RAM: ${ram_gb} GB"; else bad "RAM: ${ram_gb} GB – potrebné aspoň ${MIN_RAM_GB} GB"; fi

# Free disk space in the target directory (default: current directory)
target_dir=${AOSP_DIR:-$PWD}
disk_gb=$(df -Pk "$target_dir" 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')
disk_gb=${disk_gb:-0}
if [ "$disk_gb" -ge "$MIN_DISK_GB" ]; then pass "Voľné miesto v $target_dir: ${disk_gb} GB"; else bad "Voľné miesto v $target_dir: ${disk_gb} GB – potrebné aspoň ${MIN_DISK_GB} GB (cieľový priečinok nastavíte premennou AOSP_DIR)"; fi

# Virtualization for Cuttlefish
virt=$(grep -c -w -E 'vmx|svm' /proc/cpuinfo 2>/dev/null || true)
if [ "${virt:-0}" -gt 0 ]; then pass "Procesor podporuje virtualizáciu (vmx/svm)"; else bad "Procesor nehlási virtualizáciu (vmx/svm) – Cuttlefish potrebuje KVM; na cloudovom serveri zapnite vnorenú virtualizáciu"; fi

if [ -e /dev/kvm ]; then
  if [ -r /dev/kvm ] && [ -w /dev/kvm ]; then pass "/dev/kvm je dostupné"; else warn "/dev/kvm existuje, ale používateľ k nemu nemá prístup – pridajte ho do skupiny kvm"; fi
else
  bad "/dev/kvm neexistuje – KVM nie je zapnuté"
fi

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
  if command -v "$tool" >/dev/null 2>&1; then pass "Nástroj $tool je nainštalovaný"; else bad "Chýba nástroj $tool"; fi
done
if command -v repo >/dev/null 2>&1; then pass "Nástroj repo je nainštalovaný"; else warn "Chýba nástroj repo (inštalácia v docs/11-etapa-2-cuttlefish.md)"; fi

echo
if [ "$fail" -eq 0 ]; then
  echo "Výsledok: server spĺňa povinné požiadavky."
else
  echo "Výsledok: server NESPĹŇA povinné požiadavky – pozri riadky [CHYBA]."
fi
exit "$fail"
