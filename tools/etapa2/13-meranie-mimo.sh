#!/usr/bin/env bash
# Network measurement OUTSIDE the phone (gate 1, docs/adr/0008): the phone is a client of the Wi-Fi hotspot of this
# Windows computer (Settings → Network & internet → Mobile hotspot, network 192.168.137.0/24) and the computer
# records everything the phone sends there with pktmon, the packet monitor built into Windows. Unlike
# 11-pixel.sh audit (tcpdump in the phone itself), a modified phone cannot hide traffic from this record.
# Gate 1 needs 0 unknown connections at the first start and during 24 hours of idling.
#
# Usage: tools/etapa2/13-meranie-mimo.sh pripravit
#          writes C:\lech\meranie\start.ps1 and stop.ps1 – run in PowerShell as administrator:
#          start.ps1 starts recording the hotspot adapter, stop.ps1 stops it and converts the record to pcapng
#        tools/etapa2/13-meranie-mimo.sh vyhodnotit [--siet 192.168.137.0/24]
#          evaluates C:\lech\meranie\meranie*.pcapng (analyzuj_audit.py --hotspot, allowlist
#          spec/povolene-spojenia.txt) into ~/lech-work/meranie-mimo-<date>/sprava.md, a copy in C:\lech\meranie
# Environment: LECH_WORK (default ~/lech-work)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"
WORKDIR=${LECH_WORK:-$HOME/lech-work}
WIN_DIR=/mnt/c/lech/meranie
NET=192.168.137.0/24

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

# A PowerShell script for Windows PowerShell 5.1: UTF-8 with BOM, otherwise it reads it in the ANSI code page.
write_ps1() {   # file (content on stdin)
  { printf '\xEF\xBB\xBF'; cat; } > "$1" || die "Súbor $1 sa nedá zapísať."
}

cmd_pripravit() {
  lech_in_wsl && [ -d /mnt/c ] || die "Meranie cez prístupový bod je pre Windows (WSL)."
  mkdir -p "$WIN_DIR" || die "Priečinok C:\\lech\\meranie sa nedá vytvoriť."
  write_ps1 "$WIN_DIR/start.ps1" <<'EOF'
# LECH OS: starts the record of the Wi-Fi hotspot of this computer (pktmon). From tools/etapa2/13-meranie-mimo.sh.
$ErrorActionPreference = 'Stop'
$dir = 'C:\lech\meranie'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'Spustite PowerShell ako správca (Win, napíšte powershell, Spustiť ako správca).' -ForegroundColor Red; exit 1
}
# The hotspot adapter is the one with the gateway address of the hotspot network.
$ip = Get-NetIPAddress -AddressFamily IPv4 -IPAddress 192.168.137.1 -ErrorAction SilentlyContinue
if (-not $ip) {
    Write-Host 'Mobilný prístupový bod nebeží. Zapnite ho: Nastavenia - Sieť a internet - Mobilný prístupový bod.' -ForegroundColor Red; exit 1
}
$desc = (Get-NetAdapter -InterfaceIndex $ip.InterfaceIndex).InterfaceDescription
$id = $null
foreach ($line in (pktmon list)) {
    if ($line -match '^\s*(\d+)\s+\S+\s+(.+?)\s*$' -and $Matches[2] -eq $desc) { $id = $Matches[1] }
}
if (-not $id) { Write-Host "Adaptér prístupového bodu '$desc' nie je v zozname pktmon list. Pošlite výpis pktmon list." -ForegroundColor Red; exit 1 }
pktmon stop 2>$null | Out-Null
pktmon filter remove | Out-Null
New-Item -ItemType Directory -Force $dir | Out-Null
Remove-Item "$dir\meranie*.etl", "$dir\meranie*.pcapng" -ErrorAction SilentlyContinue
pktmon start --capture --comp $id --pkt-size 0 --file-name "$dir\meranie.etl" --log-mode multi-file --file-size 256 | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host 'pktmon sa nespustil - pošlite tento výpis.' -ForegroundColor Red; exit 1 }
Set-Content -Encoding Ascii "$dir\zaciatok.txt" (Get-Date -Format o)
Write-Host "Meranie beží od $(Get-Date -Format 'dd. MM. yyyy HH:mm') (adaptér $id, $desc)." -ForegroundColor Green
Write-Host 'Teraz pripojte telefón k Wi-Fi tohto počítača. Ukončenie: powershell -ExecutionPolicy Bypass -File C:\lech\meranie\stop.ps1'
EOF
  write_ps1 "$WIN_DIR/stop.ps1" <<'EOF'
# LECH OS: stops the record of the hotspot and converts it to pcapng. From tools/etapa2/13-meranie-mimo.sh.
$ErrorActionPreference = 'Stop'
$dir = 'C:\lech\meranie'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'Spustite PowerShell ako správca.' -ForegroundColor Red; exit 1
}
pktmon stop | Out-Null
Set-Content -Encoding Ascii "$dir\koniec.txt" (Get-Date -Format o)
$files = Get-ChildItem "$dir\meranie*.etl"
if (-not $files) { Write-Host 'Záznam (meranie*.etl) sa nenašiel.' -ForegroundColor Red; exit 1 }
foreach ($f in $files) {
    pktmon etl2pcap $f.FullName --out ($f.FullName -replace '\.etl$', '.pcapng') | Out-Null
}
$start = Get-Date (Get-Content "$dir\zaciatok.txt")
Write-Host ('Meranie ukončené po {0:N1} h, súbory: {1}. Vyhodnotenie v Ubuntu: tools/etapa2/13-meranie-mimo.sh vyhodnotit' -f `
    ((Get-Date) - $start).TotalHours, $files.Count) -ForegroundColor Green
EOF
  cat <<'EOF'
Pripravené: C:\lech\meranie\start.ps1 a stop.ps1.

Postup (prvý štart a 24 h nečinnosti, merané mimo telefónu):
  1. Windows: Nastavenia → Sieť a internet → Mobilný prístupový bod – zapnúť; Úsporu energie (vypnúť prístupový
     bod bez pripojených zariadení) VYPNÚŤ. Meno siete a heslo si poznačte.
  2. PowerShell ako správca:  powershell -ExecutionPolicy Bypass -File C:\lech\meranie\start.ps1
  3. Telefón: vybrať SIM (všetka prevádzka pôjde cez Wi-Fi notebooku), obnoviť výrobné nastavenia
     (Nastavenia → Systém → Možnosti obnovenia → Vymazať všetky údaje). V sprievodcovi pripojiť len Wi-Fi notebooku,
     nič neinštalovať ani neprihlasovať. Potom nechať 24 h ležať na nabíjačke.
  4. Po 24 h:  powershell -ExecutionPolicy Bypass -File C:\lech\meranie\stop.ps1
     a v Ubuntu:  tools/etapa2/13-meranie-mimo.sh vyhodnotit
Notebook počas merania nesmie zaspať (nabíjačka, veko otvorené alebo „Nerobiť nič“).
EOF
}

cmd_vyhodnotit() {
  local net=$NET out files=() n
  while [ $# -gt 0 ]; do
    case "$1" in
      --siet) net=${2:?chýba sieť}; shift 2 ;;
      *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
    esac
  done
  for c in tshark mergecap editcap python3; do command -v "$c" >/dev/null 2>&1 || die "Chýba $c (tools/etapa2/01-priprava-notebooku.sh)."; done
  mapfile -t files < <(ls "$WIN_DIR"/meranie*.pcapng 2>/dev/null)
  [ "${#files[@]}" -gt 0 ] || die "V C:\\lech\\meranie nie je záznam (meranie*.pcapng) – najprv stop.ps1 ako správca."
  out="$WORKDIR/meranie-mimo-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "$out" || die "Priečinok $out sa nedá vytvoriť."
  mergecap -w "$out/spojene.pcapng" "${files[@]}" || die "Spojenie záznamov zlyhalo."
  # pktmon can record a packet at more than one place of the adapter's stack: drop exact duplicates.
  editcap -d "$out/spojene.pcapng" "$out/zachytenie.pcapng" >/dev/null || die "Odstránenie duplikátov zlyhalo."
  rm -f "$out/spojene.pcapng"
  cp "$WIN_DIR"/zaciatok.txt "$WIN_DIR"/koniec.txt "$out/" 2>/dev/null
  n=$(capinfos -c -M "$out/zachytenie.pcapng" 2>/dev/null | sed -n 's/^Number of packets: *//p')
  echo "Záznam: ${#files[@]} súbor(y), ${n:-?} paketov; telefón v sieti $net."
  python3 -B "$SCRIPT_DIR/analyzuj_audit.py" "$out/zachytenie.pcapng" --out "$out/sprava.md" --hotspot "$net" \
    --allowlist "$REPO_DIR/spec/povolene-spojenia.txt" --ptr \
    --title "Meranie mimo telefónu – Wi-Fi prístupový bod notebooku ($(date '+%d. %m. %Y'))" \
    || die "Vyhodnotenie zlyhalo."
  cp "$out/sprava.md" "$WIN_DIR/sprava-$(basename "$out").md" 2>/dev/null && \
    echo "Správa aj vo Windows: C:\\lech\\meranie\\sprava-$(basename "$out").md"
  echo
  sed -n '/^## Domény/,/^## Lokálna/p' "$out/sprava.md" | grep '^| `' | head -n 40
}

case "${1:-}" in
  pripravit) cmd_pripravit ;;
  vyhodnotit) shift; cmd_vyhodnotit "$@" ;;
  -h|--help|"") sed -n '2,15p' "$0" ;;
  *) die "Neznámy príkaz: $1 (pomoc: --help)" ;;
esac
