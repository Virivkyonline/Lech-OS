#!/usr/bin/env python3
"""Third-party apps preinstalled in LECH OS: download, verify the publisher's signature, generate the build module.

Run by tools/etapa2/12-zostav-pixel.sh before the build. For every app the APK is downloaded from its publisher
(kept in a cache, so a build without internet uses the last verified copy), its package name is checked with aapt2
and its signing certificate with apksigner against the SHA-256 published by the publisher. Only a verified APK goes
into $AOSP_DIR/vendor/lech/tretie: one android_app_import per app (presigned: the publisher's signature stays, so
the app updates itself from its publisher) and tretie.mk with PRODUCT_PACKAGES, which
vendor/lech/products/lech_tokay.mk includes when it exists.

Apps (owner, 12. 11. 2026: customers must not download the app store by hand):
  F-Droid (org.fdroid.fdroid, GPL-3.0-or-later) - the app store. Certificate SHA-256 as published by F-Droid in the
  client whitelist of its Privileged Extension (gitlab.com/fdroid/privileged-extension, ClientWhitelist.java:
  "certificate SHA-256 of https://f-droid.org/F-Droid.apk").

Usage: tretie_aplikacie.py --aapt2 PATH --apksigner PATH --zipalign PATH --cache DIR --out DIR [--java-home DIR]
  --java-home   JDK for apksigner (a Java program; outside the build environment java is not on PATH):
                12-zostav-pixel.sh passes the JDK of the AOSP tree (prebuilts/jdk/jdk*/linux-x86)
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

APPS = [
    {
        "module": "LechFDroid",
        "label": "F-Droid",
        "file": "F-Droid.apk",
        "url": "https://f-droid.org/F-Droid.apk",
        "package": "org.fdroid.fdroid",
        "cert_sha256": "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab",
        "license_kind": "SPDX-license-identifier-GPL-3.0-or-later",
    },
]


ENV = dict(os.environ)


def preprocess_issues(apk: Path, zipalign: str) -> bool:
    """The checks of Soong's check_prebuilt_presigned_apk.py for a preprocessed, non-privileged APK."""
    try:
        if subprocess.run([zipalign, "-c", "-p", "4", str(apk)], stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL, timeout=120).returncode != 0:
            return True
    except (OSError, subprocess.TimeoutExpired):
        return True
    with zipfile.ZipFile(apk) as zf:
        return any(i.filename.startswith("lib/") and i.filename.endswith(".so") and i.compress_type != zipfile.ZIP_STORED
                   for i in zf.infolist())


def download(url: str, dest: Path) -> bool:
    tmp = dest.with_suffix(".part")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "LECH-OS-build"})
        with urllib.request.urlopen(req, timeout=60) as resp, open(tmp, "wb") as f:
            shutil.copyfileobj(resp, f)
        os.replace(tmp, dest)
        return True
    except OSError as e:
        print(f"    sťahovanie zlyhalo ({e})")
        tmp.unlink(missing_ok=True)
        return False


def check(apk: Path, app: dict, aapt2: str, apksigner: str):
    """Returns (ok, version text)."""
    try:
        badging = subprocess.run([aapt2, "dump", "badging", str(apk)], capture_output=True, text=True,
                                 timeout=120).stdout
        certs = subprocess.run([apksigner, "verify", "--print-certs", str(apk)], capture_output=True, text=True,
                               timeout=120, env=ENV)
    except (OSError, subprocess.TimeoutExpired) as e:
        print(f"    kontrola zlyhala ({e})")
        return False, ""
    m = re.search(r"package: name='([^']*)' versionCode='([^']*)' versionName='([^']*)'", badging)
    if not m or m.group(1) != app["package"]:
        print(f"    nie je to balík {app['package']}")
        return False, ""
    if certs.returncode != 0:
        print("    podpis APK sa nepodarilo overiť (apksigner):")
        for line in (certs.stderr or certs.stdout).strip().splitlines()[:5]:
            print(f"      {line}")
        return False, ""
    digests = set(re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", certs.stdout))
    if digests != {app["cert_sha256"]}:
        print(f"    podpis nie je od vydavateľa (certifikát {', '.join(sorted(digests)) or '?'})")
        return False, ""
    return True, f"{m.group(3)} ({m.group(2)})"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--zipalign", required=True)
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--java-home", type=Path)
    args = parser.parse_args()
    if args.java_home:
        ENV["JAVA_HOME"] = str(args.java_home)
        ENV["PATH"] = f"{args.java_home / 'bin'}{os.pathsep}{ENV.get('PATH', '')}"

    args.cache.mkdir(parents=True, exist_ok=True)
    if args.out.exists():
        shutil.rmtree(args.out)
    modules = []
    for app in APPS:
        print(f"  {app['label']}:")
        cached = args.cache / app["file"]
        with tempfile.TemporaryDirectory(dir=args.cache) as tmpdir:
            fresh = Path(tmpdir) / app["file"]
            if download(app["url"], fresh):
                ok, version = check(fresh, app, args.aapt2, args.apksigner)
                if ok:
                    os.replace(fresh, cached)
                    print(f"    stiahnutá a overená verzia {version}")
                else:
                    print("    stiahnutý súbor sa NEPOUŽIJE")
        if not cached.exists():
            print(f"    {app['label']} v tejto zostave nebude (žiadna overená kópia)")
            continue
        ok, version = check(cached, app, args.aapt2, args.apksigner)
        if not ok:
            print(f"    {app['label']} v tejto zostave nebude (kópia v {cached} neprešla kontrolou)")
            continue
        dest = args.out / app["module"]
        dest.mkdir(parents=True)
        shutil.copyfile(cached, dest / app["file"])
        # Presigned APKs with targetSdkVersion >= 30 must be "preprocessed" (Soong, check_prebuilt_presigned_apk.py):
        # the build keeps the publisher's v2+ signature and only checks the zip alignment and that JNI libraries are
        # stored uncompressed. An APK that does not meet this (still installable: Android extracts compressed
        # libraries) needs skip_preprocessed_apk_checks, which Soong in turn refuses for an APK without such issues.
        skip_checks = preprocess_issues(dest / app["file"], args.zipalign)
        (dest / "Android.bp").write_text(
            f"// {app['label']} {version}, generated by tools/etapa2/tretie_aplikacie.py - do not edit.\n"
            f"// Downloaded from {app['url']}, signing certificate SHA-256 {app['cert_sha256']}.\n\n"
            "package {\n"
            f"    default_applicable_licenses: [\"{app['module']}_license\"],\n"
            "}\n\n"
            "license {\n"
            f"    name: \"{app['module']}_license\",\n"
            f"    license_kinds: [\"{app['license_kind']}\"],\n"
            "}\n\n"
            "android_app_import {\n"
            f"    name: \"{app['module']}\",\n"
            f"    apk: \"{app['file']}\",\n"
            "    preprocessed: true,\n"
            + ("    skip_preprocessed_apk_checks: true,\n" if skip_checks else "")
            + "    product_specific: true,\n"
            "    enforce_uses_libs: false,\n"
            "    dex_preopt: {\n"
            "        enabled: false,\n"
            "    },\n"
            "}\n")
        modules.append(app["module"])
        print(f"    v zostave ({version}, podpis vydavateľa overený)")
    if modules:
        (args.out / "tretie.mk").write_text(
            "# Generated by tools/etapa2/tretie_aplikacie.py - do not edit.\n"
            "PRODUCT_PACKAGES += \\\n" + "".join(f"    {m} \\\n" for m in modules))
    return 0


if __name__ == "__main__":
    sys.exit(main())
