#!/usr/bin/env bash
# Creates the LECH OS test signing keys (task E2-07): one RSA 4096 key and certificate for each AOSP
# test key (build/make/target/product/security: testkey, platform, shared, media, networkstack, ...).
# The keys are for development builds only. They are stored OUTSIDE the repository, in $LECH_KEYS
# (default ~/lech-keys/test), readable only by the user; 06-zostav-lech.sh copies them into the AOSP tree.
# Existing keys are never overwritten: a phone signed with other keys has to be wiped.
#
# Usage: tools/etapa2/07-vytvor-kluce.sh
# Environment: LECH_KEYS (default ~/lech-keys/test), AOSP_DIR (default ~/aosp, for the list of keys)

set -euo pipefail

KEYS=${LECH_KEYS:-$HOME/lech-keys/test}
AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
SUBJECT_BASE="/C=SK/O=LECH OS/OU=LECH OS test keys - not for release"

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }

case "${1:-}" in
  -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
  "") ;;
  *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
esac
command -v openssl >/dev/null 2>&1 || die "Chýba openssl (sudo apt install openssl)."

# The same names as the AOSP test keys, so the build finds every certificate it asks for.
if [ -d "$AOSP_DIR/build/make/target/product/security" ]; then
  mapfile -t names < <(find "$AOSP_DIR/build/make/target/product/security" -maxdepth 1 -name '*.pk8' \
                         -printf '%f\n' | sed 's/\.pk8$//' | LC_ALL=C sort)
else
  names=(bluetooth cts_uicc_2021 media networkstack nfc platform sdk_sandbox shared testkey)
fi
[ "${#names[@]}" -gt 0 ] || die "Nenašiel sa zoznam kľúčov AOSP."

umask 077
mkdir -p "$KEYS"
chmod 700 "$KEYS"
created=0
for name in "${names[@]}"; do
  if [ -e "$KEYS/$name.pk8" ] || [ -e "$KEYS/$name.x509.pem" ]; then
    continue
  fi
  tmp="$KEYS/.$name.key.pem"
  openssl genrsa -out "$tmp" 4096 2>/dev/null
  openssl req -new -x509 -sha256 -key "$tmp" -days 10000 -subj "$SUBJECT_BASE/CN=LECH OS $name" \
    -out "$KEYS/$name.x509.pem"
  openssl pkcs8 -in "$tmp" -topk8 -outform DER -nocrypt -out "$KEYS/$name.pk8"
  rm -f -- "$tmp"
  created=$((created + 1))
done
chmod 600 "$KEYS"/*.pk8
chmod 644 "$KEYS"/*.x509.pem

cat > "$KEYS/README.txt" <<'EOF'
LECH OS - testovacie podpisové kľúče (úloha E2-07, docs/11 časť 4d)

Len pre vývojové zostavy. Nie sú to produkčné kľúče a nepatria do repozitára.
Ak sa kľúče zmenia, telefón podpísaný starými kľúčmi treba vymazať (Cuttlefish: cvd reset).
Záloha: skopírujte celý tento priečinok na bezpečné miesto.
EOF

echo "Testovacie kľúče LECH OS: $KEYS (nové: $created, spolu: ${#names[@]})"
for name in "${names[@]}"; do
  printf '  %-14s %s\n' "$name" "$(openssl x509 -in "$KEYS/$name.x509.pem" -noout -fingerprint -sha256 | cut -d= -f2)"
done
