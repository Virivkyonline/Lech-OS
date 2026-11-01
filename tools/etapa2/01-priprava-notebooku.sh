#!/usr/bin/env bash
# Prepares an Ubuntu/Debian notebook or server for LECH OS stage 2:
#   - packages for building AOSP and for the network audit (tcpdump, tshark, adb)
#   - Cuttlefish host packages from the official apt repository
#   - non-Google DNS servers for Cuttlefish guests (default is 8.8.8.8)
#   - user groups, the `repo` tool and optional swap
# Run as a normal user; the script calls sudo where needed. Safe to run repeatedly.
#
# Usage: tools/etapa2/01-priprava-notebooku.sh [--swap GB] [--dns IP,IP]
#   --swap GB   create an additional swap file of GB gigabytes (recommended with 32 GB RAM: --swap 32)
#   --dns LIST  DNS servers for Cuttlefish guests (default: Quad9 9.9.9.9,149.112.112.112)

set -euo pipefail

SWAP_GB=0
DNS_SERVERS="9.9.9.9,149.112.112.112"
CF_DEFAULTS=/etc/default/cuttlefish-host-resources
SWAPFILE=/swapfile-lech

step() { printf '\n==> %s\n' "$1"; }
info() { printf '    %s\n' "$1"; }
die()  { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --swap) SWAP_GB=${2:?chýba počet GB}; shift 2 ;;
    --dns)  DNS_SERVERS=${2:?chýba zoznam DNS serverov}; shift 2 ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

[ "$(id -u)" -ne 0 ] || die "Spustite skript ako bežný používateľ (bez sudo). Skript si sudo vypýta sám."
[ "$(uname -m)" = "x86_64" ] || die "Potrebný je počítač s procesorom Intel alebo AMD (x86_64)."
. /etc/os-release
case "${ID:-}" in ubuntu|debian) ;; *) die "Skript je pre Ubuntu alebo Debian (zistené: ${PRETTY_NAME:-neznáme})." ;; esac
case "$SWAP_GB" in ''|*[!0-9]*) die "--swap očakáva celé číslo (GB)." ;; esac

step "Overenie oprávnení (sudo)"
sudo -v

step "1/6 Balíky na zostavenie AOSP a na sieťový audit"
sudo apt-get update -q
packages="git gnupg flex bison build-essential zip curl zlib1g-dev libc6-dev-i386 x11proto-core-dev libx11-dev
lib32z1-dev libgl1-mesa-dev libxml2-utils xsltproc unzip fontconfig python3 tcpdump tshark adb ca-certificates"
available=""
for p in $packages; do
  if apt-cache show "$p" >/dev/null 2>&1; then available="$available $p"; else info "Balík $p nie je v tejto verzii systému dostupný – preskakujem"; fi
done
# shellcheck disable=SC2086
sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y -q $available

step "2/6 Cuttlefish z oficiálneho apt repozitára (github.com/google/android-cuttlefish)"
if [ ! -f /etc/apt/trusted.gpg.d/artifact-registry.asc ]; then
  sudo curl -fsSL https://us-apt.pkg.dev/doc/repo-signing-key.gpg -o /etc/apt/trusted.gpg.d/artifact-registry.asc
  sudo chmod a+r /etc/apt/trusted.gpg.d/artifact-registry.asc
fi
CF_LINE="deb https://us-apt.pkg.dev/projects/android-cuttlefish-artifacts android-cuttlefish main"
if ! grep -qsF "$CF_LINE" /etc/apt/sources.list.d/artifact-registry.list; then
  echo "$CF_LINE" | sudo tee -a /etc/apt/sources.list.d/artifact-registry.list >/dev/null
fi
sudo apt-get update -q
sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y -q cuttlefish-base cuttlefish-user

step "3/6 DNS pre virtuálny telefón (Cuttlefish predvolene používa DNS servery Google)"
if [ -f "$CF_DEFAULTS" ] && grep -qE '^dns_servers=' "$CF_DEFAULTS"; then
  sudo sed -i "s/^dns_servers=.*/dns_servers=${DNS_SERVERS}/" "$CF_DEFAULTS"
else
  printf '\n# LECH OS: non-Google DNS for Cuttlefish guests\ndns_servers=%s\n' "$DNS_SERVERS" | sudo tee -a "$CF_DEFAULTS" >/dev/null
fi
info "dns_servers=${DNS_SERVERS} (súbor ${CF_DEFAULTS})"
sudo systemctl restart cuttlefish-host-resources 2>/dev/null || info "Služba cuttlefish-host-resources sa reštartuje pri ďalšom štarte počítača"

step "4/6 Skupiny používateľa pre Cuttlefish"
sudo usermod -aG kvm,cvdnetwork,render "$USER"
info "Používateľ $USER pridaný do skupín kvm, cvdnetwork, render (platí po reštarte)"

step "5/6 Nástroj repo"
mkdir -p "$HOME/bin"
if [ ! -x "$HOME/bin/repo" ]; then
  curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o "$HOME/bin/repo"
  chmod a+rx "$HOME/bin/repo"
fi
if ! grep -qsF 'export PATH="$HOME/bin:$PATH"' "$HOME/.bashrc"; then
  echo 'export PATH="$HOME/bin:$PATH"' >> "$HOME/.bashrc"
fi
info "repo je v $HOME/bin/repo"

step "6/6 Swap"
if [ "$SWAP_GB" -gt 0 ]; then
  if swapon --show=NAME --noheadings | grep -qx "$SWAPFILE"; then
    info "$SWAPFILE už je aktívny – nič nemením"
  else
    free_gb=$(df -Pk / | awk 'NR==2 {print int($4/1024/1024)}')
    [ "$free_gb" -gt $(( SWAP_GB + 20 )) ] || die "Na disku nie je dosť miesta na swap ${SWAP_GB} GB (voľné: ${free_gb} GB)."
    sudo fallocate -l "${SWAP_GB}G" "$SWAPFILE"
    sudo chmod 600 "$SWAPFILE"
    sudo mkswap "$SWAPFILE" >/dev/null
    sudo swapon "$SWAPFILE"
    grep -qsF "$SWAPFILE" /etc/fstab || echo "$SWAPFILE none swap sw 0 0" | sudo tee -a /etc/fstab >/dev/null
    info "Vytvorený a zapnutý swap ${SWAP_GB} GB ($SWAPFILE)"
  fi
else
  info "Bez zmeny (pri 32 GB RAM spustite skript znova s --swap 32)"
fi

cat <<'EOF'

Hotovo. Ďalší postup:
  1. Reštartujte počítač:              sudo reboot
  2. Overte pripravenosť:              tools/check_build_host.sh
  3. Spustite čisté AOSP na vyskúšanie: tools/etapa2/02-ciste-aosp.sh
  4. Sieťový audit prvého spustenia:   tools/etapa2/03-sietovy-audit.sh
EOF
