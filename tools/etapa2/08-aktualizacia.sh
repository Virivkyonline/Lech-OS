#!/usr/bin/env bash
# A/B update of LECH OS on Cuttlefish from the LECH OS update server v0 (task E2-08):
#   1. builds LECH OS version A (vendor/lech/config/version.mk) and keeps a copy of its images,
#   2. builds version B (default: A with the suffix "-ota") and its update package (OTA), signed
#      with the LECH OS key that phone A trusts,
#   3. publishes the package on the update server v0 (tools/etapa2/ota_server.py) on this computer,
#   4. starts a phone from the images of A; update_engine downloads B and installs it into the other slot,
#   5. restarts the phone and checks that it runs version B from the other slot.
# The phone reaches the server through an adb tunnel (adb reverse); a server in the EU comes later.
#
# Usage: tools/etapa2/08-aktualizacia.sh [--verzia B] [--ulohy N] [--len-instalovat]
#   --verzia B         version of the update (default: <version A>-ota)
#   --ulohy N          parallel build jobs (passed to 06-zostav-lech.sh)
#   --len-instalovat   no builds: use the saved images of A and the published package again
# Environment: AOSP_DIR (default ~/aosp), LECH_WORK (default ~/lech-work), LECH_OTA_PORT (default 8008)

set -uo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_DIR=$(cd "$SCRIPT_DIR/../.." && pwd)
. "$SCRIPT_DIR/lib-cuttlefish.sh"

AOSP_DIR=${AOSP_DIR:-$HOME/aosp}
WORKDIR=${LECH_WORK:-$HOME/lech-work}
OTA_DIR="$WORKDIR/ota"
IMAGES_A="$OTA_DIR/obrazy-A"
SERVER_DIR="$OTA_DIR/server"
PORT=${LECH_OTA_PORT:-8008}
PRODUCT=lech_cf_x86_64_only_phone

die() { printf '\nCHYBA: %s\n' "$1" >&2; exit 1; }
step() { printf '\n== %s\n' "$1"; }

VERSION_B=""
BUILD_ARGS=()
INSTALL_ONLY=0
while [ $# -gt 0 ]; do
  case "$1" in
    --verzia) VERSION_B=${2:?chýba verzia}; shift 2 ;;
    --ulohy) BUILD_ARGS+=(--ulohy "${2:?chýba počet úloh}"); shift 2 ;;
    --len-instalovat) INSTALL_ONLY=1; shift ;;
    -h|--help) sed -n '2,16p' "$0"; exit 0 ;;
    *) die "Neznámy parameter: $1 (pomoc: --help)" ;;
  esac
done

VERSION_A=$(sed -n 's/^LECH_VERSION *[:?]= *//p' "$REPO_DIR/vendor/lech/config/version.mk")
[ -n "$VERSION_A" ] || die "Verzia v vendor/lech/config/version.mk sa nenašla."
VERSION_B=${VERSION_B:-$VERSION_A-ota}
case "$VERSION_B" in *[[:space:]]*|"") die "Verzia nesmie obsahovať medzery." ;; esac
[ "$VERSION_B" != "$VERSION_A" ] || die "Verzia aktualizácie sa musí líšiť od verzie A ($VERSION_A)."
for c in cvd python3; do command -v "$c" >/dev/null 2>&1 || die "Chýba príkaz $c."; done
mkdir -p "$OTA_DIR"
echo "Aktualizácia LECH OS: $VERSION_A (A) → $VERSION_B (B), server v0 na porte $PORT"

if [ "$INSTALL_ONLY" -eq 0 ]; then
  lech_reset_phones   # the builds need the memory, and the phone must not use images that are rebuilt

  step "1/5 Zostavujem verziu A ($VERSION_A)"
  env -u LECH_VERSION "$SCRIPT_DIR/06-zostav-lech.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"} || die "Zostavenie verzie A zlyhalo."
  env -u LECH_VERSION bash -c ". '$SCRIPT_DIR/lib-cuttlefish.sh' && lech_enter_build_env >/dev/null && echo \"\$ANDROID_PRODUCT_OUT\"" \
    > "$OTA_DIR/product-out.txt" || die "Prostredie zostavy sa nepodarilo načítať."
  PRODUCT_OUT=$(tail -n 1 "$OTA_DIR/product-out.txt")
  [ -f "$PRODUCT_OUT/android-info.txt" ] || die "V $PRODUCT_OUT nie sú obrazy zostavy."

  # A copy, not links: building B rewrites the files in out/ in place.
  need_gb=$(find "$PRODUCT_OUT" -maxdepth 1 -type f -printf '%s\n' | awk '{s += $1} END {print int(s / 1024^3) + 1}')
  free_gb=$(lech_free_gb "$OTA_DIR")
  [ "$free_gb" -ge $(( need_gb + 20 )) ] || die "Na kópiu obrazov A treba ${need_gb} GB a 20 GB rezervu, voľných je ${free_gb} GB."
  echo "Kopírujem obrazy verzie A (${need_gb} GB) do $IMAGES_A…"
  rm -rf -- "${IMAGES_A:?}"
  mkdir -p "$IMAGES_A"
  find "$PRODUCT_OUT" -maxdepth 1 -type f -exec cp -p --sparse=always -t "$IMAGES_A" {} + || die "Kopírovanie obrazov zlyhalo."
  echo "$VERSION_A" > "$IMAGES_A/lech-version.txt"

  step "2/5 Zostavujem verziu B ($VERSION_B) a balík aktualizácie"
  stamp="$OTA_DIR/.zaciatok-B"; touch "$stamp"
  LECH_VERSION=$VERSION_B "$SCRIPT_DIR/06-zostav-lech.sh" ${BUILD_ARGS[@]+"${BUILD_ARGS[@]}"} || die "Zostavenie verzie B zlyhalo."
  # The same LECH_VERSION, otherwise the product configuration would change back to A.
  LECH_VERSION=$VERSION_B bash -c ". '$SCRIPT_DIR/lib-cuttlefish.sh' && lech_enter_build_env >/dev/null && cd '$AOSP_DIR' && m otapackage" \
    > "$OTA_DIR/otapackage.log" 2>&1 || { tail -n 20 "$OTA_DIR/otapackage.log"; die "m otapackage zlyhalo – log: $OTA_DIR/otapackage.log"; }
  ota=$(find "$AOSP_DIR/out/soong/.intermediates" -path "*${PRODUCT}_generated_device*" -name ota.zip -newer "$stamp" \
          -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -n 1 | cut -d' ' -f2-)
  [ -n "$ota" ] || die "Balík ota.zip sa po m otapackage nenašiel (log: $OTA_DIR/otapackage.log)."

  step "3/5 Zverejňujem aktualizáciu na serveri v0"
  python3 -B "$SCRIPT_DIR/ota_server.py" publish "$ota" "$SERVER_DIR" --version "$VERSION_B" > "$OTA_DIR/zverejnene.json" \
    || die "Balík aktualizácie je neplatný ($ota)."
fi

[ -f "$IMAGES_A/android-info.txt" ] || die "Uložené obrazy verzie A nie sú – spustite skript bez --len-instalovat."
[ -f "$SERVER_DIR/aktualizacie.json" ] || die "Na serveri nie je aktualizácia – spustite skript bez --len-instalovat."
read -r OTA_FILE OTA_OFFSET OTA_SIZE OTA_VERSION < <(python3 -c '
import json, sys
u = json.load(open(sys.argv[1]))["aktualizacie"][0]
print(u["subor"], u["offset"], u["size"], u["verzia"])' "$SERVER_DIR/aktualizacie.json")
HEADERS=$(python3 -c '
import json, sys
u = json.load(open(sys.argv[1]))["aktualizacie"][0]
print("\n".join(u["headers"] + ["USER_AGENT=LECH-OS-updater-v0", "NETWORK_ID=0"]))' "$SERVER_DIR/aktualizacie.json")
echo "Aktualizácia na serveri: $OTA_FILE ($OTA_VERSION, $(( $(stat -c %s "$SERVER_DIR/$OTA_FILE") / 1024 / 1024 )) MB)"

step "4/5 Spúšťam telefón s verziou A a inštalujem aktualizáciu"
lech_enter_build_env >/dev/null || die "Prostredie zostavy sa nepodarilo načítať."
lech_reset_phones
mapfile -t start_args < <(lech_start_args)
cvd create --host_path="$ANDROID_HOST_OUT" --product_path="$IMAGES_A" ${start_args[@]+"${start_args[@]}"} \
  || die "Telefón s verziou A nenaštartoval (tools/etapa2/diagnostika.sh)."
ADB=$(lech_adb) || die "adb sa nenašiel."
timeout 180 "$ADB" wait-for-device || die "Telefón sa cez adb neozval."
prop() { "$ADB" shell getprop "$1" 2>/dev/null | tr -d '\r'; }
# The build number is lech.<version> (05-zostav-aosp.sh); the build time also tells two builds of one version apart.
before_version=$(prop ro.lech.version); before_slot=$(prop ro.boot.slot_suffix); before_build=$(prop ro.build.date.utc)
echo "Telefón beží: LECH OS $before_version, slot ${before_slot:-?}, zostavené $(date -d "@${before_build:-0}" '+%d. %m. %Y %H:%M')"
[ "$before_version" = "$(cat "$IMAGES_A/lech-version.txt" 2>/dev/null)" ] || echo "UPOZORNENIE: telefón nemá verziu A z uložených obrazov."

python3 -B "$SCRIPT_DIR/ota_server.py" serve "$SERVER_DIR" --port "$PORT" > "$OTA_DIR/server.log" 2>&1 &
SERVER_PID=$!
cleanup() { kill "$SERVER_PID" 2>/dev/null || true; "$ADB" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true; }
trap cleanup EXIT
sleep 1
kill -0 "$SERVER_PID" 2>/dev/null || die "Server aktualizácií sa nespustil (port $PORT obsadený?) – $OTA_DIR/server.log"
"$ADB" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null || die "adb reverse zlyhalo."

echo "update_engine sťahuje a inštaluje aktualizáciu do druhého slotu (niekoľko minút)…"
"$ADB" shell "su 0 update_engine_client --update --follow --payload=http://127.0.0.1:$PORT/$OTA_FILE \
  --offset=$OTA_OFFSET --size=$OTA_SIZE --headers=\"$HEADERS\"" 2>&1 | tr -d '\r' | tee "$OTA_DIR/update_engine.log" \
  | awk '/onStatusUpdate/ {
           match($0, /UPDATE_STATUS_[A-Z_]+/); s = substr($0, RSTART, RLENGTH)
           match($0, /[0-9.eE+-]+\)$/); p = substr($0, RSTART, RLENGTH - 1) + 0; b = int(p * 10)
           if (s != ls || b != lb) { printf "  %-34s %3d %%\n", s, p * 100; fflush(); ls = s; lb = b }
           next }
         /onPayloadApplicationComplete|rror/ { print "  " $0; fflush() }'
status=${PIPESTATUS[0]}
requests=$(grep -c '" 20[06] ' "$OTA_DIR/server.log" || true)
echo "Server v0 obslúžil požiadaviek: $requests (log: $OTA_DIR/server.log)"
grep -q "kSuccess\|kUpdatedButNotActive" "$OTA_DIR/update_engine.log" && [ "$status" -eq 0 ] \
  || die "Inštalácia aktualizácie zlyhala – log: $OTA_DIR/update_engine.log"

step "5/5 Reštartujem telefón do verzie B"
"$ADB" reboot
sleep 15
timeout 600 "$ADB" wait-for-device || die "Telefón sa po reštarte neozval."
for _ in $(seq 120); do [ "$(prop sys.boot_completed)" = 1 ] && break; sleep 5; done
[ "$(prop sys.boot_completed)" = 1 ] || die "Telefón po aktualizácii nedokončil štart (tools/etapa2/diagnostika.sh)."
after_version=$(prop ro.lech.version); after_slot=$(prop ro.boot.slot_suffix); after_build=$(prop ro.build.date.utc)

ok=1
row() { printf '  %-7s %s%*s %s\n' "$1" "$2" $(( 16 - ${#2} )) "" "$3"; [ "$1" = OK ] || ok=0; }
echo
[ "$after_version" = "$OTA_VERSION" ] && row OK "verzia" "$before_version → $after_version" \
  || row CHYBA "verzia" "$before_version → ${after_version:-?} (očakávané $OTA_VERSION)"
[ -n "$after_slot" ] && [ "$after_slot" != "$before_slot" ] && row OK "slot A/B" "$before_slot → $after_slot" \
  || row CHYBA "slot A/B" "${before_slot:-?} → ${after_slot:-?} (slot sa mal zmeniť)"
fmt() { date -d "@${1:-0}" '+%d. %m. %Y %H:%M'; }
[ "${after_build:-0}" -gt "${before_build:-0}" ] 2>/dev/null && row OK "čas zostavenia" "$(fmt "$before_build") → $(fmt "$after_build")" \
  || row CHYBA "čas zostavenia" "$(fmt "$before_build") → $(fmt "$after_build") (B má byť novšia)"
echo
if [ "$ok" -eq 1 ]; then
  echo "Aktualizácia z vlastného servera prebehla: LECH OS $after_version beží zo slotu $after_slot."
  echo "V telefóne: Settings → About phone → Build number."
else
  echo "Aktualizácia nie je v poriadku – pošlite tento výpis a $OTA_DIR/update_engine.log."
  exit 1
fi
