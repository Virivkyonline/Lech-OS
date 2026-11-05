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
