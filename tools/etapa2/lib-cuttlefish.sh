# Shared helpers for the stage 2 scripts. Source it: . "$(dirname "$0")/lib-cuttlefish.sh"
#
# Prebuilt images are fetched once into $LECH_IMAGES and reused by later runs and audits.
# The phone is started with `cvd create --host_path --product_path [START_ARGS]` (not --config_file),
# because some settings we need exist only as launch flags:
#   --ril_dns          DNS of the virtual mobile network; Cuttlefish defaults to Google 8.8.8.8
#   --enable_sandbox   crosvm sandbox; its minijail mounts fail under WSL
#
# Environment:
#   LECH_WORK            working directory (default ~/lech-work)
#   LECH_BUILD           branch/target to fetch (default aosp-android-latest-release/aosp_cf_x86_64_only_phone-userdebug)
#   LECH_REFRESH_IMAGES  1 = download the images again (newest build on the branch)
#   LECH_CF_SANDBOX      1 = force the crosvm sandbox on, 0 = force it off (default: off in WSL only)

LECH_BUILD="${LECH_BUILD:-aosp-android-latest-release/aosp_cf_x86_64_only_phone-userdebug}"
LECH_IMAGES="${LECH_WORK:-$HOME/lech-work}/images/$(printf '%s' "$LECH_BUILD" | tr '/' '_')"

LECH_TARGET_FILE="${LECH_WORK:-$HOME/lech-work}/lunch-target"   # written by 05-zostav-aosp.sh

lech_in_wsl() { grep -qi microsoft /proc/version 2>/dev/null; }

# Build lock of the AOSP tree: the nightly check and 11-zostav-gsi.sh must not use out/ at the same time.
LECH_BUILD_LOCK="${LECH_WORK:-$HOME/lech-work}/ci/.zamok"

# Run a command (the calling script again) under the build lock; returns 75 when the lock is taken.
# flock -o keeps the lock in the flock process only: daemons that the script starts (adb server, Cuttlefish)
# do not inherit it. Before, a lock inherited by such a daemon stayed taken after the nightly check (6. 11. 2026).
lech_with_build_lock() {   # command...
  mkdir -p "$(dirname "$LECH_BUILD_LOCK")"
  LECH_BUILD_LOCK_HELD=1 flock -n -o -E 75 "$LECH_BUILD_LOCK" "$@"
}

# Processes of this user that have a file open, "PID command" per line.
lech_file_users() {
  local target p link
  target=$(readlink -f "$1")
  for p in /proc/[0-9]*; do
    for link in "$p"/fd/*; do
      if [ "$(readlink "$link" 2>/dev/null)" = "$target" ]; then
        printf '%s %s\n' "${p#/proc/}" "$(tr '\0' ' ' < "$p/cmdline" 2>/dev/null | cut -c1-110)"
        break
      fi
    done
  done
}

# Who holds the build lock, and what to do about it.
lech_build_lock_report() {
  local users
  users=$(lech_file_users "$LECH_BUILD_LOCK")
  echo "Zámok zostavenia ($LECH_BUILD_LOCK) drží:"
  if [ -n "$users" ]; then printf '%s\n' "$users" | sed 's/^/  /'; else echo "  (proces sa nedá zistiť)"; fi
  if printf '%s' "$users" | grep -qE '09-nocna-kontrola|11-zostav-gsi'; then
    echo "Beží nočná kontrola alebo zostavenie – počkajte, kým skončí (priebeh: tail -f ${LECH_WORK:-$HOME/lech-work}/ci/beh.log)."
  elif printf '%s' "$users" | grep -qE 'run_cvd|crosvm|cvd_internal|/cvd/'; then
    echo "Zámok drží bežiaci virtuálny telefón Cuttlefish (zostal po nočnej kontrole). Vypnite ho: cvd reset --yes"
  elif [ -n "$users" ]; then
    echo "Nočná kontrola ani zostavenie nebeží – zámok drží zvyšný proces. Ukončite ho (číslo PID vyššie): kill PID"
  fi
}

# Free space in GB where a directory lives. In WSL the Linux disk is a growing virtual disk file on C:,
# so df inside WSL shows the virtual disk's maximum size; the real limit is the free space on C:.
lech_free_gb() {
  local gb win
  gb=$(df -Pk "$1" 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')
  if lech_in_wsl && [ -d /mnt/c ]; then
    win=$(df -Pk /mnt/c 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')
    if [ -n "$win" ] && [ "$win" -lt "${gb:-0}" ]; then gb=$win; fi
  fi
  echo "${gb:-0}"
}

# Where the free space of lech_free_gb comes from: in WSL the Linux disk (ext4.vhdx) lies on C:, and files deleted
# in Linux do not make C: freer by themselves (the vhdx file does not shrink).
lech_free_detail() {
  local gb win
  gb=$(df -Pk "$1" 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')
  if lech_in_wsl && [ -d /mnt/c ]; then
    win=$(df -Pk /mnt/c 2>/dev/null | awk 'NR==2 {print int($4/1024/1024)}')
    echo "Linux (WSL) ${gb:-?} GB, Windows C: ${win:-?} GB"
  else
    echo "${gb:-?} GB"
  fi
}

# Enter the environment of the local build saved by 05-zostav-aosp.sh
# (sets ANDROID_HOST_OUT and ANDROID_PRODUCT_OUT, which `cvd create` uses).
lech_enter_build_env() {
  local aosp=${AOSP_DIR:-$HOME/aosp} target old_opts=$- rc=0
  if [ ! -f "$LECH_TARGET_FILE" ]; then
    echo "Chýba $LECH_TARGET_FILE – najprv zostavte systém: tools/etapa2/05-zostav-aosp.sh" >&2
    return 1
  fi
  target=$(cat "$LECH_TARGET_FILE")
  set +eu   # build/envsetup.sh and lunch are not written for errexit/nounset
  pushd "$aosp" >/dev/null || rc=1
  if [ "$rc" -eq 0 ]; then
    # shellcheck disable=SC1091
    source build/envsetup.sh >/dev/null 2>&1 && lunch "$target" >/dev/null 2>&1 || rc=1
    popd >/dev/null || true
  fi
  case $old_opts in *e*) set -e ;; esac
  case $old_opts in *u*) set -u ;; esac
  if [ "$rc" -ne 0 ] || [ -z "${ANDROID_PRODUCT_OUT:-}" ]; then
    echo "Nepodarilo sa pripraviť prostredie zostavy $target v $aosp." >&2
    return 1
  fi
  echo "Prostredie zostavy: $target"
}

# Download the prebuilt images unless they are already present.
lech_ensure_images() {
  if [ -f "$LECH_IMAGES/fetcher_config.json" ] && [ "${LECH_REFRESH_IMAGES:-0}" != "1" ]; then
    echo "Používam už stiahnuté obrazy: $LECH_IMAGES"
    return 0
  fi
  mkdir -p "$LECH_IMAGES"
  echo "Sťahujem hotové obrazy $LECH_BUILD (prvýkrát niekoľko GB)…"
  cvd fetch --default_build="$LECH_BUILD" --target_directory="$LECH_IMAGES"
}

# Launch flags for this machine, one per line.
lech_start_args() {
  local dns
  # `|| true`: callers run with `set -e`, which a missing file must not trigger inside this function.
  dns=$(sed -n 's/^dns_servers=\([^,]*\).*/\1/p' /etc/default/cuttlefish-host-resources 2>/dev/null | tail -n 1 || true)
  echo "--ril_dns=${dns:-9.9.9.9}"
  if [ "${LECH_CF_SANDBOX:-}" = "0" ] || { [ -z "${LECH_CF_SANDBOX:-}" ] && lech_in_wsl; }; then
    echo "--enable_sandbox=false"
  fi
}

# Print a one-line description of the launch settings.
lech_describe_start() {
  local args
  args=$(lech_start_args | tr '\n' ' ')
  echo "Parametre spustenia: ${args}"
}

# Remove all virtual phones of this user (test machine only).
lech_reset_phones() {
  cvd reset --yes >/dev/null 2>&1 || true
}

# What a makefile sets only for one product name: the variables assigned and the makefiles inherited inside
# "ifeq ($(TARGET_PRODUCT),<product>)" or "ifeq (<product>,$(TARGET_PRODUCT))" blocks, one per line.
# A LECH product that inherits from an AOSP product has to repeat these blocks for its own name.
lech_block_vars() {   # makefile, product
  awk -v p="$2" '
    $0 ~ "^ifeq \\(\\$\\(TARGET_PRODUCT\\)," p "\\)" || $0 ~ "^ifeq \\(" p ",\\$\\(TARGET_PRODUCT\\)\\)" { inside = 1; next }
    inside && /^endif/ { inside = 0 }
    inside && /^[A-Z_]+ *[:+?]?=/ { sub(/ *[:+?]?=.*/, ""); print }
    inside && /inherit-product/ { gsub(/[ \t]+/, ""); print }' "$1" | sort -u
}

# Hash of a certificate as Android prints it in `dumpsys package` (signatures:[...]):
# Integer.toHexString(Arrays.hashCode(DER bytes)).
lech_cert_hash() {
  openssl x509 -in "$1" -outform DER 2>/dev/null | python3 -c '
import sys
h = 1
for b in sys.stdin.buffer.read():
    h = (31 * h + (b - 256 if b > 127 else b)) & 0xffffffff
print(format(h, "x"))'
}

# Free memory in GB for a build: available RAM plus free swap (/proc/meminfo).
lech_free_mem_gb() {
  awk '/^MemAvailable:/ {m = $2} /^SwapFree:/ {s = $2} END {print int((m + s) / 1024 / 1024)}' /proc/meminfo
}

# Whether a Cuttlefish phone is running (its launcher process).
lech_phone_running() {
  pgrep -u "$(id -u)" -f 'run_cvd|crosvm' >/dev/null 2>&1
}

# Path to adb for the running phone: from the local build, from the prebuilt host package, or installed.
lech_adb() {
  local c
  for c in "${ANDROID_HOST_OUT:-}/bin/adb" "${AOSP_DIR:-$HOME/aosp}/out/host/linux-x86/bin/adb" "$LECH_IMAGES/bin/adb"; do
    if [ -x "$c" ]; then echo "$c"; return 0; fi
  done
  command -v adb
}

# Save which app asked the phone's DNS resolver for which name (task E2-06, docs/12):
# the resolver keeps the last 200 queries with the app's UID; Android masks each name to its first
# letter. Writes <dir>/dns-dopyty.txt and <dir>/balicky.txt (package name -> UID).
# On failure prints the reason to stderr and returns 1.
lech_save_dns_by_app() {
  local dir=$1 adb out
  adb=$(lech_adb) || { echo "adb sa nenašiel" >&2; return 1; }
  timeout 60 "$adb" wait-for-device >/dev/null 2>&1 || { echo "telefón sa cez adb neozval" >&2; return 1; }
  # The query log needs more rights than the adb shell user has; development builds allow adb root.
  if [ "$(timeout 20 "$adb" shell id -u 2>/dev/null | tr -d '\r')" != 0 ]; then
    timeout 30 "$adb" root >/dev/null 2>&1 || true
    sleep 3
    timeout 60 "$adb" wait-for-device >/dev/null 2>&1 || { echo "telefón sa po adb root neozval" >&2; return 1; }
  fi
  out=$(timeout 60 "$adb" shell dumpsys dnsresolver querylog 2>&1 | tr -d '\r')
  printf '%s\n' "$out" > "$dir/dns-dopyty.txt"
  timeout 60 "$adb" shell pm list packages -U 2>/dev/null | tr -d '\r' > "$dir/balicky.txt"
  if ! grep -q 'uid=' "$dir/dns-dopyty.txt"; then
    echo "záznam DNS je prázdny alebo nedostupný: $(printf '%s' "$out" | grep -v '^ *$' | head -n 2 | tr '\n' ' ')" >&2
    return 1
  fi
}

# Create and start a phone from the fetched images, or from the local build with --local.
lech_create_phone() {
  local args=()
  mapfile -t args < <(lech_start_args)
  if [ "${1:-}" = "--local" ]; then
    cvd create "${args[@]}"
  else
    cvd create --host_path="$LECH_IMAGES" --product_path="$LECH_IMAGES" "${args[@]}"
  fi
}
