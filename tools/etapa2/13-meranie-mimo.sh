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
# Not 'Stop': Windows PowerShell 5.1 turns any stderr line of a native program (pktmon) into a terminating error
# then - "pktmon stop" when nothing runs printed "Packet Monitor is not running" and ended the script (8. 11. 2026).
$ErrorActionPreference = 'Continue'
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
pktmon stop 2>&1 | Out-Null
pktmon filter remove 2>&1 | Out-Null
New-Item -ItemType Directory -Force $dir | Out-Null
Remove-Item "$dir\meranie*.etl", "$dir\meranie*.pcapng" -ErrorAction SilentlyContinue
Set-Content -Encoding Ascii "$dir\adapter.txt" $id
pktmon start --capture --comp $id --pkt-size 0 --file-name "$dir\meranie-000.etl" --log-mode multi-file --file-size 256 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host 'pktmon sa nespustil - pošlite tento výpis.' -ForegroundColor Red; exit 1 }
Set-Content -Encoding Ascii "$dir\zaciatok.txt" (Get-Date -Format o)
# pktmon keeps packets in memory buffers and writes them to the file only when a buffer is full or the record stops:
# with an idle phone a whole day fits in the buffers, and the Windows crash of 8. 11. 2026 (vfpext.sys) lost 7 h.
# A scheduled task (as SYSTEM) therefore closes the file every hour and starts the next one (gap ~1 s).
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument '-NoProfile -ExecutionPolicy Bypass -File C:\lech\meranie\rotate.ps1'
$trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddHours(1) -RepetitionInterval (New-TimeSpan -Hours 1) -RepetitionDuration (New-TimeSpan -Days 3)
$principalTask = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName 'LECH meranie' -Action $action -Trigger $trigger -Principal $principalTask -Force | Out-Null
if (-not (Get-ScheduledTask -TaskName 'LECH meranie' -ErrorAction SilentlyContinue)) {
    Write-Host 'UPOZORNENIE: hodinové ukladanie záznamu (naplánovaná úloha) sa nevytvorilo - pri páde Windows by sa záznam stratil.' -ForegroundColor Yellow
}
Write-Host "Meranie beží od $(Get-Date -Format 'dd. MM. yyyy HH:mm') (adaptér $id, $desc), záznam sa ukladá každú hodinu." -ForegroundColor Green
Write-Host 'Teraz pripojte telefón k Wi-Fi tohto počítača. Ukončenie: powershell -ExecutionPolicy Bypass -File C:\lech\meranie\stop.ps1'
EOF
  write_ps1 "$WIN_DIR/rotate.ps1" <<'EOF'
# LECH OS: closes the current record file of the hotspot measurement and starts the next one (run every hour by the
# scheduled task "LECH meranie" from start.ps1), so a crash of Windows loses at most one hour. Also restarts the
# record after a restart of Windows, when the hotspot is on again.
$ErrorActionPreference = 'Continue'
$dir = 'C:\lech\meranie'
$id = (Get-Content "$dir\adapter.txt" -ErrorAction SilentlyContinue | Select-Object -First 1)
if (-not $id) { exit 1 }
pktmon stop 2>&1 | Out-Null
$n = @(Get-ChildItem "$dir\meranie-*.etl" -ErrorAction SilentlyContinue).Count
pktmon start --capture --comp $id --pkt-size 0 --file-name ("$dir\meranie-{0:D3}.etl" -f $n) --log-mode multi-file --file-size 256 2>&1 | Out-Null
Add-Content -Encoding Ascii "$dir\rotacie.txt" ("{0} subor {1} pktmon {2}" -f (Get-Date -Format o), $n, $LASTEXITCODE)
EOF
  write_ps1 "$WIN_DIR/stop.ps1" <<'EOF'
# LECH OS: stops the record of the hotspot and converts it to pcapng. From tools/etapa2/13-meranie-mimo.sh.
$ErrorActionPreference = 'Continue'   # see start.ps1: native programs and stderr
$dir = 'C:\lech\meranie'
$principal = [Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host 'Spustite PowerShell ako správca.' -ForegroundColor Red; exit 1
}
Unregister-ScheduledTask -TaskName 'LECH meranie' -Confirm:$false -ErrorAction SilentlyContinue
pktmon stop 2>&1 | Out-Null
Set-Content -Encoding Ascii "$dir\koniec.txt" (Get-Date -Format o)
$files = Get-ChildItem "$dir\meranie*.etl"
if (-not $files) { Write-Host 'Záznam (meranie*.etl) sa nenašiel.' -ForegroundColor Red; exit 1 }
foreach ($f in $files) {
    pktmon etl2pcap $f.FullName --out ($f.FullName -replace '\.etl$', '.pcapng') 2>&1 | Out-Null
}
Get-ChildItem "$dir\meranie*.etl" | ForEach-Object { '{0}  {1:N0} B  {2}' -f $_.Name, $_.Length, $_.LastWriteTime } | Write-Host
$start = Get-Date (Get-Content "$dir\zaciatok.txt")
Write-Host ('Meranie ukončené po {0:N1} h, súbory: {1}. Vyhodnotenie v Ubuntu: tools/etapa2/13-meranie-mimo.sh vyhodnotit' -f `
    ((Get-Date) - $start).TotalHours, $files.Count) -ForegroundColor Green
EOF
  cat <<'EOF'
Pripravené: C:\lech\meranie\start.ps1, rotate.ps1 (každú hodinu, naplánovaná úloha „LECH meranie“) a stop.ps1.

Postup (prvý štart a 24 h nečinnosti, merané mimo telefónu):
  1. Windows: Nastavenia → Sieť a internet → Mobilný prístupový bod – zapnúť; Úsporu energie (vypnúť prístupový
     bod bez pripojených zariadení) VYPNÚŤ. Meno siete a heslo si poznačte.
  2. Kontrola (9. 11. 2026 zdieľanie po páde Windows nefungovalo a 24 h meranie bolo slepé): telefón na Wi-Fi notebooku
     musí načítať stránku. Krátky test: start.ps1, v telefóne otvoriť stránku, stop.ps1, vyhodnotit – stránka musí byť
     v správe. Ak nie je internet: reštart notebooku, prístupový bod zdieľa pripojenie Wi-Fi.
     PowerShell ako správca:  powershell -ExecutionPolicy Bypass -File C:\lech\meranie\start.ps1
  3. Telefón: vybrať SIM (všetka prevádzka pôjde cez Wi-Fi notebooku), obnoviť výrobné nastavenia
     (Nastavenia → Systém → Možnosti obnovenia → Vymazať všetky údaje). V sprievodcovi pripojiť len Wi-Fi notebooku,
     nič neinštalovať ani neprihlasovať. Potom nechať 24 h ležať na nabíjačke.
     Počas merania notebook nezaťažovať: Ubuntu vypnúť (PowerShell: wsl --shutdown) – 8. 11. 2026 Windows padol
     (vfpext.sys, sieť Hyper-V/WSL) počas zostavovania a záznamu naraz.
  4. Po 24 h:  powershell -ExecutionPolicy Bypass -File C:\lech\meranie\stop.ps1
     a v Ubuntu:  tools/etapa2/13-meranie-mimo.sh vyhodnotit
Notebook počas merania nesmie zaspať (nabíjačka, veko otvorené alebo „Nerobiť nič“).
EOF
}

# Percent of the first 500 frames that tshark decodes as IP, IPv6 or ARP.
decoded_share() {   # pcap file
  local total ok
  total=$(tshark -r "$1" -c 500 -T fields -e frame.number 2>/dev/null | wc -l)
  ok=$(tshark -r "$1" -c 500 -Y "ip || ipv6 || arp" -T fields -e frame.number 2>/dev/null | wc -l)
  [ "$total" -gt 0 ] || { echo 0; return; }
  echo $(( ok * 100 / total ))
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
  # pktmon on the adapter of the Windows hotspot (Wi-Fi Direct, vwifimp.sys) records IEEE 802.11 data frames but
  # labels the file Ethernet: tshark decodes nothing and the report showed 0 targets (9. 11. 2026). When most frames
  # do not decode, they are converted to Ethernet frames (wifi_na_ethernet.py).
  if [ "$(decoded_share "$out/zachytenie.pcapng")" -lt 50 ]; then
    editcap -F pcap "$out/zachytenie.pcapng" "$out/wifi.pcap" >/dev/null || die "Prevod záznamu na pcap zlyhal."
    python3 -I "$SCRIPT_DIR/wifi_na_ethernet.py" "$out/wifi.pcap" "$out/ethernet.pcap" || die "Prevod rámcov 802.11 zlyhal."
    if [ "$(decoded_share "$out/ethernet.pcap")" -ge 50 ]; then
      mv -f "$out/ethernet.pcap" "$out/zachytenie.pcapng"
      echo "Záznam obsahoval rámce Wi-Fi (802.11) – prevedené na Ethernet ($(decoded_share "$out/zachytenie.pcapng") % rozpoznaných)."
    else
      die "Pakety v zázname sa nedajú rozpoznať ani ako Wi-Fi – pošlite výpis: tshark -r $out/zachytenie.pcapng -c 5 -x"
    fi
    rm -f "$out/wifi.pcap" "$out/ethernet.pcap"
  fi
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
