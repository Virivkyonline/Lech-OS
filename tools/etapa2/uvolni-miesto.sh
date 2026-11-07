#!/usr/bin/env bash
# Frees space on the Windows drive of the WSL disk for the AOSP builds (E2-11b, 7. 11. 2026: C: had 17 GB free).
# The Linux disk of WSL is one file (ext4.vhdx, on C: unless WSL was installed elsewhere) that grows while builds
# write and never shrinks by itself, so files deleted in Linux do not make that drive freer. The script:
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

lech_in_wsl && [ -d /mnt/c ] || die "Skript je pre WSL vo Windows."

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
        # The unpacked images of adevtool contain read-only directories (as in the image); rm needs write access.
        chmod -R u+w -- "$c" 2>/dev/null
        if rm -rf -- "$c"; then echo "  zmazané: ${c/#"$HOME"/\~}"; else echo "  UPOZORNENIE: ${c/#"$HOME"/\~} sa nezmazal celý."; fi
      done ;;
    *) echo "  nič som nezmazal." ;;
  esac
fi

# 2. Mark the free space as unused in ext4.vhdx, so the compaction can drop it.
echo
echo "Označujem voľné miesto disku WSL (sudo fstrim – môže sa pýtať heslo)…"
sudo fstrim -v / || echo "UPOZORNENIE: fstrim zlyhal – zmenšenie disku uvoľní menej miesta."

# 3. The compaction script for Windows, for the ext4.vhdx of this distribution (lech_wsl_vhd_win).
vhd_win=$(lech_wsl_vhd_win)
[ -n "$vhd_win" ] || die "Súbor disku WSL (ext4.vhdx) sa nenašiel jednoznačne. Pošlite tento výpis."
drive=${vhd_win:0:1}
vhd_size=$(( $(stat -c %s "$(wslpath -u "$vhd_win")") / 1024 / 1024 / 1024 ))
used_gb=$(df -Pk / | awk 'NR==2 {print int($3/1024/1024)}')

mkdir -p "$(dirname "$PS1_FILE")"
# UTF-8 with BOM: Windows PowerShell 5.1 reads a file without it in the ANSI code page (diacritics).
{
  printf '\xEF\xBB\xBF'
  vhd_ps=${vhd_win//\'/\'\'}   # inside a single-quoted PowerShell string
  sed "s|__VHD__|${vhd_ps//\\/\\\\}|" <<'EOF'
# LECH OS: zmenší virtuálny disk WSL (ext4.vhdx), aby sa miesto uvoľnené v Linuxe vrátilo na disk Windows.
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
# WSL stops in the background; wait until nothing has the disk file open (up to 3 minutes).
$deadline = (Get-Date).AddMinutes(3)
while ($true) {
    try { $fs = [IO.File]::Open($vhd, 'Open', 'Read', 'None'); $fs.Close(); break }
    catch {
        if ((Get-Date) -gt $deadline) {
            Write-Host 'Súbor disku WSL je stále otvorený (beží WSL alebo iný program, napr. Docker). Zatvorte ho, spustite wsl --shutdown a tento skript znova.' -ForegroundColor Red
            exit 1
        }
        Start-Sleep -Seconds 3
    }
}
$cmd = Join-Path $env:TEMP 'lech-zmensi-disk-wsl.txt'
Set-Content -LiteralPath $cmd -Encoding Ascii -Value @("select vdisk file=""$vhd""", 'attach vdisk readonly', 'compact vdisk', 'detach vdisk')
Write-Host 'Zmenšujem disk WSL (diskpart, compact vdisk) - môže to trvať aj desiatky minút, nezatvárajte okno...'
diskpart /s $cmd
$failed = $LASTEXITCODE -ne 0
$after = (Get-Item -LiteralPath $vhd).Length
$drive = $vhd.Substring(0, 1)
$free = (Get-PSDrive -Name $drive).Free
$sizes = 'disk WSL {0:N1} GB -> {1:N1} GB, voľné na {2}: {3:N1} GB.' -f ($before / 1GB), ($after / 1GB), $drive, ($free / 1GB)
if ($failed) { Write-Host ('diskpart skončil s chybou - pošlite tento výpis. ' + $sizes) -ForegroundColor Red; exit 1 }
Write-Host ('Hotovo: ' + $sizes) -ForegroundColor Green
Write-Host 'Otvorte znova Ubuntu a pokračujte: cd ~/Lech-OS && tools/etapa2/12-zostav-pixel.sh'
EOF
} > "$PS1_FILE" || die "Súbor $PS1_FILE sa nedá zapísať."

echo
echo "Disk WSL: ${vhd_win}"
echo "  na ${drive}: zaberá ${vhd_size} GB, Linux v ňom používa ${used_gb} GB → zmenšením sa uvoľní približne $(( vhd_size > used_gb ? vhd_size - used_gb : 0 )) GB."
cat <<EOF

Posledný krok vo Windows (WSL sa pri ňom vypne – zatvorte otvorené okná Ubuntu):
  1. Pravý klik na tlačidlo Štart → „Terminál (správca)“ (alebo „Windows PowerShell (správca)“).
  2. Spustite:  powershell -ExecutionPolicy Bypass -File C:\\lech\\zmensi-disk-wsl.ps1
  3. Po „Hotovo“ otvorte znova Ubuntu a pokračujte v zostavení.
EOF
