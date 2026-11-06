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

# Runs a PowerShell script from WSL and keeps its whole output (also errors) in PS_OUT.
# -EncodedCommand (UTF-16LE in base64): no temporary file, no quoting problems, no execution policy for .ps1 files.
# stdin from /dev/null: powershell.exe must not wait for input. The output stays ASCII (Windows console code page).
# The script prints lines starting with "LECH-OK " when it worked – the caller checks them, not only the exit code.
PS_OUT=
run_powershell() {
  local script
  command -v powershell.exe >/dev/null 2>&1 || die "powershell.exe nie je dostupné z WSL – úlohu v Plánovači úloh pridajte ručne (docs/11, časť 4g)."
  script="\$ErrorActionPreference = 'Stop'; \$ProgressPreference = 'SilentlyContinue'
$(cat)"
  PS_OUT=$(cd /mnt/c && powershell.exe -NoProfile -NonInteractive -EncodedCommand \
             "$(printf '%s' "$script" | iconv -f UTF-8 -t UTF-16LE | base64 -w0)" < /dev/null 2>&1 | tr -d '\r')
}
ps_ok() { printf '%s\n' "$PS_OUT" | sed -n 's/^LECH-OK //p'; }
ps_failed() {   # message
  printf '\nVýstup PowerShell:\n%s\n' "${PS_OUT:-(prázdny)}" >&2
  die "$1"
}

if [ "$REMOVE" -eq 1 ]; then
  if lech_in_wsl; then
    run_powershell <<EOF
if (Get-ScheduledTask -TaskName '$TASK_NAME' -ErrorAction SilentlyContinue) {
  Unregister-ScheduledTask -TaskName '$TASK_NAME' -Confirm:\$false
  Write-Output 'LECH-OK removed'
} else { Write-Output 'LECH-OK none' }
EOF
    case "$(ps_ok)" in
      removed) echo "Úloha '$TASK_NAME' odstránená z Plánovača úloh Windows." ;;
      none) echo "Úloha '$TASK_NAME' v Plánovači úloh Windows nie je." ;;
      *) printf 'Výstup PowerShell:\n%s\n' "${PS_OUT:-(prázdny)}"; echo "Úlohu v Plánovači úloh sa nepodarilo odstrániť." ;;
    esac
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

echo "2/2 Úloha v Plánovači úloh Windows: '$TASK_NAME', každý deň o $TIME (zobudí počítač; na batérii sa nespustí, ale začatá kontrola dobehne)."
mkdir -p "$CI_ROOT"
command="$REPO_DIR/tools/etapa2/09-nocna-kontrola.sh >> $CI_ROOT/beh.log 2>&1"
run_powershell <<EOF
\$action = New-ScheduledTaskAction -Execute 'wsl.exe' -Argument '-d ${WSL_DISTRO_NAME:-Ubuntu-24.04} -u $USER -- bash -lc "$command"'
\$trigger = New-ScheduledTaskTrigger -Daily -At '$TIME'
\$settings = New-ScheduledTaskSettingsSet -WakeToRun -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 4) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName '$TASK_NAME' -Action \$action -Trigger \$trigger -Settings \$settings -Description 'LECH OS: build, start, network audit and app test (tools/etapa2/09-nocna-kontrola.sh)' -Force | Out-Null
\$next = (Get-ScheduledTask -TaskName '$TASK_NAME' | Get-ScheduledTaskInfo).NextRunTime
if (\$next) { Write-Output ('LECH-OK ' + \$next.ToString('yyyy-MM-dd HH:mm')) } else { Write-Output 'LECH-OK (Windows ho zatial neuviedol)' }
EOF
next_run=$(ps_ok)
[ -n "$next_run" ] || ps_failed "Úlohu v Plánovači úloh sa nepodarilo vytvoriť alebo overiť."
echo "    vytvorená a overená, najbližší beh: $next_run"

cat <<EOF

Hotovo. Nočná kontrola pobeží každý deň o $TIME, ak je notebook zapnutý alebo v režime spánku, pripojený k
napájaniu a ste prihlásený vo Windows. Výsledok ráno: $CI_ROOT/posledna.md
Vyskúšať hneď (bez čakania na noc):  $REPO_DIR/tools/etapa2/09-nocna-kontrola.sh
Zrušiť:                              $REPO_DIR/tools/etapa2/09-nastav-nocnu-kontrolu.sh --zrusit
EOF
