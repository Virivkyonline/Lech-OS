#!/usr/bin/env bash
# Sets up the nightly check of LECH OS (task E2-09):
#   1. a sudo rule that lets this user run tcpdump (and only tcpdump) without a password, so the network
#      audit can run unattended: /etc/sudoers.d/lech-nocna-kontrola,
#   2. in WSL: a task in the Windows Task Scheduler that starts 09-nocna-kontrola.sh every night and wakes
#      the computer from sleep for it (it does not start on battery).
#
# Usage: tools/etapa2/09-nastav-nocnu-kontrolu.sh [--cas HH:MM] [--zrusit]
#   --cas HH:MM   time of the nightly check (default 02:30)
#   --zrusit      remove the task and the sudo rule again

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"

TIME=02:30
REMOVE=0
TASK_NAME="LECH OS - nocna kontrola"
SUDOERS=/etc/sudoers.d/lech-nocna-kontrola
CI_ROOT="${LECH_WORK:-$HOME/lech-work}/ci"

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --cas) TIME=${2:?chýba čas}; shift 2 ;;
    --zrusit) REMOVE=1; shift ;;
    -h|--help) sed -n '2,11p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done
[[ "$TIME" =~ ^([01][0-9]|2[0-3]):[0-5][0-9]$ ]] || die "--cas očakáva čas v tvare HH:MM, napr. 02:30."

# Runs a PowerShell script from WSL (written to the Windows temp folder, so quoting stays simple).
# Windows PowerShell reads scripts without a BOM in the ANSI code page: keep the script text ASCII.
run_powershell() {
  local win_temp ps1
  command -v powershell.exe >/dev/null 2>&1 || die "powershell.exe nie je dostupné – úlohu v Plánovači úloh pridajte ručne (docs/11, časť 4g)."
  win_temp=$(cd /mnt/c 2>/dev/null && cmd.exe /c 'echo %TEMP%' 2>/dev/null | tr -d '\r')
  [ -n "$win_temp" ] || die "Priečinok TEMP vo Windows sa nenašiel."
  ps1="$(wslpath "$win_temp")/lech-nocna-kontrola.ps1"
  cat > "$ps1"
  (cd /mnt/c && powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$(wslpath -w "$ps1")") | tr -d '\r'
  local status=${PIPESTATUS[0]}
  rm -f -- "$ps1"
  return "$status"
}

if [ "$REMOVE" -eq 1 ]; then
  if lech_in_wsl; then
    run_powershell <<EOF || echo "Úlohu v Plánovači úloh sa nepodarilo odstrániť (možno neexistuje)."
Unregister-ScheduledTask -TaskName '$TASK_NAME' -Confirm:\$false -ErrorAction Stop
Write-Output 'Task removed from the Windows Task Scheduler.'
EOF
  fi
  sudo rm -f -- "$SUDOERS" && echo "Pravidlo sudo odstránené ($SUDOERS)."
  exit 0
fi

echo "1/2 Pravidlo sudo: $USER smie spustiť /usr/bin/tcpdump bez hesla (len tento príkaz)."
tmp=$(mktemp)
printf '# LECH OS: nočná kontrola (tools/etapa2/09-nastav-nocnu-kontrolu.sh) – sieťový audit bez hesla.\n%s ALL=(root) NOPASSWD: /usr/bin/tcpdump\n' "$USER" > "$tmp"
if sudo visudo -cqf "$tmp" && sudo install -m 0440 -o root -g root "$tmp" "$SUDOERS"; then
  echo "    $SUDOERS"
else
  rm -f -- "$tmp"; die "Pravidlo sudo sa nepodarilo uložiť."
fi
rm -f -- "$tmp"
sudo -n /usr/bin/tcpdump --version >/dev/null 2>&1 || die "tcpdump sa ani po nastavení nespustí bez hesla."

if ! lech_in_wsl; then
  cat <<EOF

2/2 Nie je to WSL – nočnú kontrolu naplánujte cez cron (crontab -e), napríklad:
    ${TIME#*:} ${TIME%:*} * * * $REPO_DIR/tools/etapa2/09-nocna-kontrola.sh >> $CI_ROOT/beh.log 2>&1
EOF
  exit 0
fi

echo "2/2 Úloha v Plánovači úloh Windows: '$TASK_NAME', každý deň o $TIME (zobudí počítač, nie na batériu)."
mkdir -p "$CI_ROOT"
command="$REPO_DIR/tools/etapa2/09-nocna-kontrola.sh >> $CI_ROOT/beh.log 2>&1"
run_powershell <<EOF || die "Úlohu sa nepodarilo vytvoriť."
\$action = New-ScheduledTaskAction -Execute 'wsl.exe' -Argument '-d ${WSL_DISTRO_NAME:-Ubuntu-24.04} -u $USER -- bash -lc "$command"'
\$trigger = New-ScheduledTaskTrigger -Daily -At '$TIME'
\$settings = New-ScheduledTaskSettingsSet -WakeToRun -ExecutionTimeLimit (New-TimeSpan -Hours 4) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName '$TASK_NAME' -Action \$action -Trigger \$trigger -Settings \$settings -Description 'LECH OS: build, start, network audit and app test (tools/etapa2/09-nocna-kontrola.sh)' -Force -ErrorAction Stop | Out-Null
\$t = Get-ScheduledTask -TaskName '$TASK_NAME'
Write-Output ('    next run: ' + ((\$t | Get-ScheduledTaskInfo).NextRunTime))
EOF

cat <<EOF

Hotovo. Nočná kontrola pobeží každý deň o $TIME, ak je notebook zapnutý alebo v režime spánku, pripojený k
napájaniu a ste prihlásený vo Windows. Výsledok ráno: $CI_ROOT/posledna.md
Vyskúšať hneď (bez čakania na noc):  $REPO_DIR/tools/etapa2/09-nocna-kontrola.sh
Zrušiť:                              $REPO_DIR/tools/etapa2/09-nastav-nocnu-kontrolu.sh --zrusit
EOF
