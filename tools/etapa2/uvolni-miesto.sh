#!/usr/bin/env bash
# Frees space on the Windows disk C: for the AOSP builds in WSL (E2-11b, 7. 11. 2026: C: had 17 GB free).
# The Linux disk of WSL is one file on C: (ext4.vhdx) that grows while builds write and never shrinks by itself,
# so files deleted in Linux do not make C: freer. The script:
#   1. deletes outputs that can be made again (after confirmation): the stock Google image downloaded for adevtool
#      (vendor/adevtool/dl), the outputs of the Pixel GSI (out/target/product/lech_arm64; its images for flashing
#      are in C:\lech\pixel) and of Cuttlefish (out/target/product/vsoc_x86_64_only; the nightly check builds them
#      again),
#   2. marks the freed space as unused in the virtual disk (sudo fstrim),
#   3. writes C:\lech\zmensi-disk-wsl.ps1, which compacts ext4.vhdx – to be run in PowerShell as administrator,
#      with WSL shut down (the script shuts it down itself).
#
# Usage: tools/etapa2/uvolni-miesto.sh [--ano]
#   --ano   delete without asking
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
PS1_FILE=/mnt/c/lech/zmensi-disk-wsl.ps1
ASSUME_YES=0
ORIG_ARGS=("$@")

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --ano) ASSUME_YES=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

lech_in_wsl && [ -d /mnt/c ] || die "Skript je pre WSL vo Windows (disk WSL na C:)."

# Nothing may build while outputs are deleted.
if [ -z "${LECH_BUILD_LOCK_HELD:-}" ]; then
  lech_with_build_lock "$0" ${ORIG_ARGS[@]+"${ORIG_ARGS[@]}"}
  status=$?
  if [ "$status" -eq 75 ]; then
    echo
    lech_build_lock_report
    die "V strome AOSP práve beží zostavenie – miesto uvoľnite po ňom."
  fi
  exit "$status"
fi

gb_of() { du -sk "$1" 2>/dev/null | awk '{print int($1/1024/1024)}'; }

echo "Voľné miesto: $(lech_free_detail "$AOSP_DIR")"

# 1. Outputs that can be made again.
candidates=()
add_candidate() {   # path, description
  [ -e "$1" ] || return 0
  candidates+=("$1")
  printf '  %4s GB  %s\n          %s\n' "$(gb_of "$1")" "${1/#"$HOME"/\~}" "$2"
}
echo
echo "Dá sa zmazať (vznikne znova, keď bude treba):"
add_candidate "$AOSP_DIR/vendor/adevtool/dl" "pôvodný obraz Google pre adevtool – pri ďalšom generovaní podpory Pixelu sa stiahne znova"
add_candidate "$AOSP_DIR/out/target/product/lech_arm64" "výstup zostavy GSI – obrazy na nahratie sú v C:\\lech\\pixel, GSI sa dá zostaviť znova"
if lech_phone_running; then
  echo "  (obrazy Cuttlefish nechávam – virtuálny telefón beží)"
else
  add_candidate "$AOSP_DIR/out/target/product/vsoc_x86_64_only" "obrazy Cuttlefish – nočná kontrola (05-zostav-aosp.sh) ich zostaví znova"
fi

if [ "${#candidates[@]}" -eq 0 ]; then
  echo "  (nič)"
else
  answer=a
  if [ "$ASSUME_YES" -eq 0 ]; then
    read -r -p "Zmazať tieto priečinky? [a/N] " answer
  fi
  case "$answer" in
    a|A|ano|áno)
      for c in "${candidates[@]}"; do
        rm -rf -- "$c" && echo "  zmazané: ${c/#"$HOME"/\~}"
      done ;;
    *) echo "  nič som nezmazal." ;;
  esac
fi

# 2. Mark the free space as unused in ext4.vhdx, so the compaction can drop it.
echo
echo "Označujem voľné miesto disku WSL (sudo fstrim – môže sa pýtať heslo)…"
sudo fstrim -v / || echo "UPOZORNENIE: fstrim zlyhal – zmenšenie disku uvoľní menej miesta."

# 3. The compaction script for Windows. Find ext4.vhdx of this distribution (registry of WSL; fallback: the only one).
vhd_win=""
if command -v powershell.exe >/dev/null 2>&1; then
  ps_script=$(sed "s/__DISTRO__/${WSL_DISTRO_NAME:-}/" <<'EOF'
$d = Get-ChildItem 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Lxss' | Get-ItemProperty | Where-Object { $_.DistributionName -eq '__DISTRO__' }
if ($d) { 'LECH-OK ' + (Join-Path ($d.BasePath -replace '^\\\\\?\\', '') 'ext4.vhdx') }
EOF
)
  vhd_win=$(cd /mnt/c && powershell.exe -NoProfile -NonInteractive -EncodedCommand \
    "$(printf '%s' "$ps_script" | iconv -f UTF-8 -t UTF-16LE | base64 -w0)" < /dev/null 2>/dev/null | tr -d '\r' | sed -n 's/^LECH-OK //p' | head -n 1)
fi
if [ -z "$vhd_win" ] || [ ! -f "$(wslpath -u "$vhd_win" 2>/dev/null)" ]; then
  mapfile -t found < <(ls /mnt/c/Users/*/AppData/Local/wsl/*/ext4.vhdx \
    /mnt/c/Users/*/AppData/Local/Packages/*/LocalState/ext4.vhdx 2>/dev/null)
  [ "${#found[@]}" -eq 1 ] || die "Súbor disku WSL (ext4.vhdx) sa nenašiel jednoznačne (${#found[@]}). Pošlite tento výpis."
  vhd_win=$(wslpath -w "${found[0]}")
fi
vhd_size=$(( $(stat -c %s "$(wslpath -u "$vhd_win")") / 1024 / 1024 / 1024 ))
used_gb=$(df -Pk / | awk 'NR==2 {print int($3/1024/1024)}')

mkdir -p "$(dirname "$PS1_FILE")"
# UTF-8 with BOM: Windows PowerShell 5.1 reads a file without it in the ANSI code page (diacritics).
{
  printf '\xEF\xBB\xBF'
  vhd_ps=${vhd_win//\'/\'\'}   # inside a single-quoted PowerShell string
  sed "s|__VHD__|${vhd_ps//\\/\\\\}|" <<'EOF'
# LECH OS: zmenší virtuálny disk WSL (ext4.vhdx), aby sa miesto uvoľnené v Linuxe vrátilo na disk C:.
# Vytvoril ho tools/etapa2/uvolni-miesto.sh. Spúšťa sa v PowerShell ako správca; WSL sa počas neho vypne.
$ErrorActionPreference = 'Stop'
$vhd = '__VHD__'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'Spustite PowerShell ako správca (pravý klik na Štart - Terminál (správca)).' -ForegroundColor Red
    exit 1
}
$before = (Get-Item -LiteralPath $vhd).Length
Write-Host ('Disk WSL má teraz {0:N1} GB. Vypínam WSL...' -f ($before / 1GB))
wsl.exe --shutdown
Start-Sleep -Seconds 10
$cmd = Join-Path $env:TEMP 'lech-zmensi-disk-wsl.txt'
Set-Content -LiteralPath $cmd -Encoding Ascii -Value @("select vdisk file=""$vhd""", 'attach vdisk readonly', 'compact vdisk', 'detach vdisk')
Write-Host 'Zmenšujem disk WSL (diskpart, compact vdisk) - môže to trvať aj desiatky minút, nezatvárajte okno...'
diskpart /s $cmd
if ($LASTEXITCODE -ne 0) { Write-Host 'diskpart skončil s chybou - pošlite tento výpis.' -ForegroundColor Red }
$after = (Get-Item -LiteralPath $vhd).Length
$freeC = (Get-PSDrive -Name C).Free
Write-Host ('Hotovo: disk WSL {0:N1} GB -> {1:N1} GB, voľné na C: {2:N1} GB.' -f ($before / 1GB), ($after / 1GB), ($freeC / 1GB)) -ForegroundColor Green
Write-Host 'Otvorte znova Ubuntu a pokračujte: cd ~/Lech-OS && tools/etapa2/12-zostav-pixel.sh'
EOF
} > "$PS1_FILE" || die "Súbor $PS1_FILE sa nedá zapísať."

echo
echo "Disk WSL: ${vhd_win}"
echo "  na C: zaberá ${vhd_size} GB, Linux v ňom používa ${used_gb} GB → zmenšením sa uvoľní približne $(( vhd_size > used_gb ? vhd_size - used_gb : 0 )) GB."
cat <<EOF

Posledný krok vo Windows (WSL sa pri ňom vypne – zatvorte otvorené okná Ubuntu):
  1. Pravý klik na tlačidlo Štart → „Terminál (správca)“ (alebo „Windows PowerShell (správca)“).
  2. Spustite:  powershell -ExecutionPolicy Bypass -File C:\\lech\\zmensi-disk-wsl.ps1
  3. Po „Hotovo“ otvorte znova Ubuntu a pokračujte v zostavení.
EOF
