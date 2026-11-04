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

lech_in_wsl() { grep -qi microsoft /proc/version 2>/dev/null; }

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
