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
  HeliBoard (helium314.keyboard, GPL-3.0-only) - the keyboard. Reproducible build: the APK in the F-Droid repo carries
  the developer's signature, SHA-256 as allowed in F-Droid's metadata (gitlab.com/fdroid/fdroiddata,
  metadata/helium314.keyboard.yml, AllowedAPKSigningKeys).
  Organic Maps (app.organicmaps) is NOT in the system: its F-Droid APK stores liborganicmaps.so compressed, Android
  does not extract native libraries of system apps and android_app_import has no way to extract them, so the app
  crashed ("liborganicmaps.so not found", Pixel 9, 0.5.6-dev). Installed as a normal app it works (adb install -r).

Apps with "fdroid_index" come from the main F-Droid repo: index-v1.jar is downloaded and accepted only when its JAR
signature (jarsigner) is valid and made by the repo key, whose SHA-256 is the repo fingerprint 43238d51... (the same
key signs F-Droid.apk). From the index: the suggested version, the APK name, its SHA-256 and the signer. Because the
system copy has the same signature as the repo, the F-Droid client then keeps these apps updated.

Usage: tretie_aplikacie.py --aapt2 PATH --apksigner PATH --zipalign PATH --cache DIR --out DIR [--java-home DIR]
  --java-home   JDK for apksigner (a Java program; outside the build environment java is not on PATH):
                12-zostav-pixel.sh passes the JDK of the AOSP tree (prebuilts/jdk/jdk*/linux-x86)
"""

import argparse
import hashlib
import json
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
    {
        "module": "LechHeliBoard",
        "label": "HeliBoard",
        "file": "HeliBoard.apk",
        "fdroid_index": True,
        "package": "helium314.keyboard",
        "cert_sha256": "5ec0a5313aa43558ee75b20b58ccd8194cdbf066df94a43ba288d933d60b86ce",
        "license_kind": ["SPDX-license-identifier-GPL-3.0-only", "SPDX-license-identifier-GPL-3.0"],
    },
]

FDROID_REPO = "https://f-droid.org/repo"
FDROID_REPO_CERT_SHA256 = "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab"


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


def check(apk: Path, app: dict, aapt2: str, apksigner: str, cert: str):
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
    if digests != {cert}:
        print(f"    podpis nie je od vydavateľa (certifikát {', '.join(sorted(digests)) or '?'})")
        return False, ""
    return True, f"{m.group(3)} ({m.group(2)})"


def tool(name: str) -> str:
    java_home = ENV.get("JAVA_HOME")
    if java_home and (Path(java_home) / "bin" / name).exists():
        return str(Path(java_home) / "bin" / name)
    return shutil.which(name, path=ENV.get("PATH")) or name


def fdroid_index(cache: Path):
    """Downloads index-v1.jar of the F-Droid repo, verifies its signature, returns the parsed index or None."""
    jar = cache / "fdroid-index-v1.jar"
    if not download(f"{FDROID_REPO}/index-v1.jar", jar):
        return None
    # The repo signs index-v1.jar with SHA1withRSA, which current JDKs treat as unsigned ("signed with a weak
    # algorithm that is now disabled", LECHSPA 12. 11. 2026). For this one check SHA-1 is allowed again; the APKs
    # themselves are then checked by their SHA-256 from the index and their own (v2+) signature with apksigner.
    security = cache / "fdroid-index-java.security"
    security.write_text("jdk.jar.disabledAlgorithms=MD2, MD5, RSA keySize < 1024, DSA keySize < 1024\n")
    try:
        verify = subprocess.run([tool("jarsigner"), f"-J-Djava.security.properties={security}", "-verify", str(jar)], capture_output=True, text=True,
                                timeout=300, env=ENV)
        printed = subprocess.run([tool("keytool"), "-printcert", "-jarfile", str(jar)], capture_output=True,
                                 text=True, timeout=120, env=ENV)
    except (OSError, subprocess.TimeoutExpired) as e:
        print(f"    index F-Droid sa nepodarilo overiť ({e})")
        return None
    signers = {d.replace(":", "").lower() for d in re.findall(r"SHA256:\s*([0-9A-Fa-f:]{95})", printed.stdout)}
    if verify.returncode != 0 or "jar verified." not in verify.stdout or signers != {FDROID_REPO_CERT_SHA256}:
        print(f"    index F-Droid NEMÁ platný podpis repozitára (podpisy: {', '.join(sorted(signers)) or '?'})")
        return None
    with zipfile.ZipFile(jar) as zf:
        if "index-v1.json" not in zf.read("META-INF/MANIFEST.MF").decode("utf-8", "replace"):
            print("    index-v1.json nie je v podpísanom zozname súborov")
            return None
        return json.loads(zf.read("index-v1.json"))


def fdroid_pick(index: dict, app: dict):
    """The suggested arm64 build of the app with the expected signer: (url, sha256 of the file, signer) or None."""
    suggested = next((a.get("suggestedVersionCode") for a in index.get("apps", [])
                      if a.get("packageName") == app["package"]), None)
    candidates = [p for p in index.get("packages", {}).get(app["package"], [])
                  if p.get("signer") and p.get("hashType") == "sha256"
                  and (not p.get("nativecode") or "arm64-v8a" in p["nativecode"])
                  and (not app.get("cert_sha256") or p["signer"] == app["cert_sha256"])]
    if not candidates:
        print("    v indexe F-Droid nie je vhodná verzia (arm64, očakávaný podpis)")
        return None
    best = [p for p in candidates if str(p.get("versionCode")) == str(suggested)] or candidates
    best = max(best, key=lambda p: int(p.get("versionCode", 0)))
    return f"{FDROID_REPO}/{best['apkName']}", best["hash"].lower(), best["signer"].lower()


def license_kind(app: dict, aosp: Path) -> str:
    """The first of the app's license kinds that Soong defines (build/soong/licenses/Android.bp)."""
    kinds = app["license_kind"] if isinstance(app["license_kind"], list) else [app["license_kind"]]
    try:
        defined = (aosp / "build/soong/licenses/Android.bp").read_text()
    except OSError:
        return kinds[0]
    return next((k for k in kinds if f'"{k}"' in defined), kinds[-1])


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


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
    # The output is $AOSP_DIR/vendor/lech/tretie: the AOSP tree, for the license kinds Soong defines.
    out_parents = args.out.resolve().parents
    aosp = out_parents[2] if len(out_parents) > 2 else args.out
    index = None
    for app in APPS:
        print(f"  {app['label']}:")
        cached = args.cache / app["file"]
        # The certificate the cached copy was verified against (for apps from the F-Droid index it comes from the
        # verified index, so it is stored beside the copy for builds without internet).
        cert_file = args.cache / (app["file"] + ".cert")
        url, file_hash, cert = app.get("url"), None, app.get("cert_sha256")
        if app.get("fdroid_index"):
            if index is None:
                index = fdroid_index(args.cache) or {}
            picked = fdroid_pick(index, app) if index else None
            url, file_hash, cert = picked if picked else (None, None, None)
        if url:
            with tempfile.TemporaryDirectory(dir=args.cache) as tmpdir:
                fresh = Path(tmpdir) / app["file"]
                if download(url, fresh):
                    if file_hash and sha256(fresh) != file_hash:
                        print("    súbor nesedí s kontrolným súčtom v indexe F-Droid – NEPOUŽIJE sa")
                    else:
                        ok, version = check(fresh, app, args.aapt2, args.apksigner, cert)
                        if ok:
                            os.replace(fresh, cached)
                            cert_file.write_text(cert + "\n")
                            print(f"    stiahnutá a overená verzia {version}")
                        else:
                            print("    stiahnutý súbor sa NEPOUŽIJE")
        if not cached.exists():
            print(f"    {app['label']} v tejto zostave nebude (žiadna overená kópia)")
            continue
        cert = app.get("cert_sha256") or (cert_file.read_text().strip() if cert_file.exists() else "")
        ok, version = check(cached, app, args.aapt2, args.apksigner, cert)
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
            f"// Downloaded from {url or app.get('url') or FDROID_REPO}, signing certificate SHA-256 {cert}.\n\n"
            "package {\n"
            f"    default_applicable_licenses: [\"{app['module']}_license\"],\n"
            "}\n\n"
            "license {\n"
            f"    name: \"{app['module']}_license\",\n"
            f"    license_kinds: [\"{license_kind(app, aosp)}\"],\n"
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
