#!/usr/bin/env bash
# Downloads prebuilt AOSP images for Cuttlefish (branch aosp-android-latest-release) and starts a virtual phone.
# No build needed: works on a machine with KVM, 16+ GB RAM and ~50 GB free disk.
# Usage: tools/etapa2/02-ciste-aosp.sh
# Working directory: $LECH_WORK (default ~/lech-work)

set -euo pipefail

BUILD="@ab/aosp-android-latest-release/aosp_cf_x86_64_only_phone-userdebug"
WORK="${LECH_WORK:-$HOME/lech-work}/aosp-baseline"

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

command -v cvd >/dev/null 2>&1 || die "Chýba príkaz cvd – najprv spustite tools/etapa2/01-priprava-notebooku.sh a reštartujte počítač."
[ -r /dev/kvm ] && [ -w /dev/kvm ] || die "Nie je prístup k /dev/kvm – zapnite virtualizáciu v BIOS/UEFI a po príprave reštartujte počítač."

mkdir -p "$WORK"
cat > "$WORK/spec.json" <<EOF
{
  "instances": [
    {
      "name": "aosp-baseline",
      "disk": {
        "default_build": "$BUILD"
      }
    }
  ]
}
EOF

echo "Sťahujem hotové obrazy AOSP a spúšťam virtuálny telefón (prvýkrát to môže trvať dlhšie)…"
cd "$WORK"
cvd create --config_file=spec.json

cat <<'EOF'

Virtuálny telefón beží.
  Obrazovka telefónu:  https://localhost:8443  (otvorte v prehliadači, certifikát potvrďte)
  Stav:                cvd fleet
  Príkazový riadok:    adb shell
  Vypnutie:            cvd stop
  Odstránenie:         cvd remove
EOF
