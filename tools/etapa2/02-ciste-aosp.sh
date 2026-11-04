#!/usr/bin/env bash
# Downloads prebuilt AOSP images for Cuttlefish (branch aosp-android-latest-release) and starts a virtual phone.
# No build needed: works on a machine with KVM, 16+ GB RAM and ~50 GB free disk.
# Usage: tools/etapa2/02-ciste-aosp.sh
# Images are kept in $LECH_WORK/images (default ~/lech-work) and reused; see lib-cuttlefish.sh.

set -euo pipefail

. "$(dirname "$0")/lib-cuttlefish.sh"

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

command -v cvd >/dev/null 2>&1 || die "Chýba príkaz cvd – najprv spustite tools/etapa2/01-priprava-notebooku.sh a reštartujte počítač."
[ -r /dev/kvm ] && [ -w /dev/kvm ] || die "Nie je prístup k /dev/kvm – zapnite virtualizáciu v BIOS/UEFI a po príprave reštartujte počítač."

lech_ensure_images

# Leftovers of a failed attempt block a new phone ("New instance conflicts with existing instance").
echo "Odstraňujem predchádzajúce virtuálne telefóny (cvd reset)…"
lech_reset_phones

lech_describe_start
echo "Spúšťam virtuálny telefón…"
lech_create_phone

cat <<'EOF'

Virtuálny telefón beží.
  Obrazovka telefónu:  https://localhost:8443  (otvorte v prehliadači, certifikát potvrďte)
  Stav:                cvd fleet
  Príkazový riadok:    adb shell
  Vypnutie:            cvd stop
  Odstránenie:         cvd remove
EOF
