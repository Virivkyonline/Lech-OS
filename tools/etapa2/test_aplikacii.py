#!/usr/bin/env python3
"""Compatibility test of apps from F-Droid on a running LECH OS phone (task E2-10).

For every app in the list (spec/testovacie-aplikacie.txt) the test
  1. picks the newest version from the F-Droid index (index-v2.json) that the phone can run:
     its native code matches the phone's ABIs (Cuttlefish: x86_64 only) and minSdk <= the phone's SDK,
  2. downloads the APK and checks its SHA-256 against the index,
  3. installs it (adb install); if Android refuses an old targetSdk, it notes that and retries with
     --bypass-low-target-sdk-block,
  4. starts it, waits, and checks that its process runs, its activity is in the foreground and it did
     not crash; notes when it looks for Google Play services; takes a screenshot,
  5. writes a report (Markdown and JSON).

Usage:
    python3 tools/etapa2/test_aplikacii.py --adb ADB --list FILE --report DIR --work DIR [--wait S]
Environment: LECH_FDROID_REPO (default https://f-droid.org/repo)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from datetime import datetime
from pathlib import Path

REPO = os.environ.get("LECH_FDROID_REPO", "https://f-droid.org/repo").rstrip("/")
USER_AGENT = "LECH-OS-compatibility-test"
GOOGLE_HINTS = re.compile(r"com\.google\.android\.gms|GooglePlayServices|play-services|FirebaseApp|"
                          r"Google Play services", re.I)


# ---------------------------------------------------------------- F-Droid index

def download(url: str, dest: Path, sha256: str | None = None) -> Path:
    if dest.exists() and (sha256 is None or file_sha256(dest) == sha256):
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, open(part, "wb") as out:
        while block := response.read(1 << 20):
            out.write(block)
    if sha256 is not None and file_sha256(part) != sha256:
        part.unlink()
        raise RuntimeError(f"SHA-256 súboru {url} nesedí s indexom F-Droid")
    part.replace(dest)
    return dest


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as fp:
        while block := fp.read(1 << 20):
            digest.update(block)
    return digest.hexdigest()


def load_index(cache: Path) -> dict:
    index = cache / "index-v2.json"
    if not index.exists() or time.time() - index.stat().st_mtime > 24 * 3600:
        print(f"Sťahujem zoznam aplikácií F-Droid ({REPO}/index-v2.json)…", flush=True)
        if index.exists():
            index.unlink()
        download(f"{REPO}/index-v2.json", index)
    return json.loads(index.read_text(encoding="utf-8"))


def localized(value, default: str = "") -> str:
    if isinstance(value, dict):
        for key in ("sk", "sk-SK", "en-US", "en"):
            if value.get(key):
                return value[key]
        return next(iter(value.values()), default)
    return value or default


def candidates(index: dict, package: str) -> tuple[list[dict], dict]:
    entry = index.get("packages", {}).get(package)
    if not entry:
        return [], {}
    result = []
    for version in entry.get("versions", {}).values():
        manifest = version.get("manifest", {})
        file = version.get("file", {})
        result.append({
            "versionCode": manifest.get("versionCode", 0),
            "versionName": manifest.get("versionName", "?"),
            "file": file.get("name", "").lstrip("/"),
            "sha256": file.get("sha256"),
            "size": file.get("size", 0),
            "minSdk": manifest.get("usesSdk", {}).get("minSdkVersion", 1),
            "targetSdk": manifest.get("usesSdk", {}).get("targetSdkVersion"),
            "nativecode": manifest.get("nativecode", []) or [],
        })
    return result, entry.get("metadata", {})


def choose(cands: list[dict], metadata: dict, abis: list[str], sdk: int) -> tuple[dict | None, str]:
    if not cands:
        return None, "nie je v F-Droid"
    suggested = metadata.get("suggestedVersionCode")
    pool = [c for c in cands if not suggested or c["versionCode"] <= int(suggested)] or cands
    abi_ok = [c for c in pool if not c["nativecode"] or set(c["nativecode"]) & set(abis)]
    if not abi_ok:
        native = sorted({a for c in pool for a in c["nativecode"]})
        return None, f"len pre {', '.join(native)} (telefón: {', '.join(abis)})"
    sdk_ok = [c for c in abi_ok if c["minSdk"] <= sdk]
    if not sdk_ok:
        return None, f"vyžaduje novší Android (minSdk {min(c['minSdk'] for c in abi_ok)})"
    # Prefer code for the primary ABI of the phone (Cuttlefish: x86_64) over the translated one (arm64-v8a),
    # then the newest version. APKs without native code or with all ABIs count as primary.
    def abi_rank(c: dict) -> int:
        if not c["nativecode"]:
            return 0
        return min(abis.index(a) for a in c["nativecode"] if a in abis)
    best = min(sdk_ok, key=lambda c: (abi_rank(c), -c["versionCode"]))
    best["abi"] = "bez natívneho kódu" if not best["nativecode"] else next(a for a in abis if a in best["nativecode"])
    if abi_rank(best) > 0:
        best["abi"] += " (preklad kódu ARM)"
    return best, ""


# ---------------------------------------------------------------- phone

class Phone:
    def __init__(self, adb: str):
        self.adb = adb

    def run(self, *args: str, timeout: int = 120, binary: bool = False):
        result = subprocess.run([self.adb, *args], capture_output=True, timeout=timeout)
        if binary:
            return result.returncode, result.stdout
        out = (result.stdout + result.stderr).decode(errors="replace").replace("\r", "")
        return result.returncode, out

    def shell(self, command: str, timeout: int = 60) -> str:
        return self.run("shell", command, timeout=timeout)[1].strip()

    def prop(self, name: str) -> str:
        return self.shell(f"getprop {name}")


def test_app(phone: Phone, app: dict, apk: Path, wait: int, shots: Path) -> dict:
    package = app["package"]
    result = {"instalacia": "", "spustenie": "", "poznamky": []}

    code, out = phone.run("install", "-r", str(apk), timeout=300)
    if code != 0 or "Success" not in out:
        reason = (re.findall(r"INSTALL_[A-Z_]+[^\n]*", out) or [out.strip()[-160:]])[0]
        if "INSTALL_FAILED_DEPRECATED_SDK_VERSION" in out:
            result["poznamky"].append("Android odmietol starý targetSdk; nainštalované s --bypass-low-target-sdk-block")
            code, out = phone.run("install", "-r", "--bypass-low-target-sdk-block", str(apk), timeout=300)
        if code != 0 or "Success" not in out:
            result["instalacia"] = f"CHYBA: {reason}"
            return result
    result["instalacia"] = "OK"

    phone.shell("logcat -c; logcat -b crash -c")
    out = phone.shell(f"monkey -p {package} -c android.intent.category.LAUNCHER 1")
    if "No activities found" in out or "Error" in out and "Events injected: 1" not in out:
        result["spustenie"] = "CHYBA: aplikácia nemá spúšťaciu aktivitu"
        return result
    time.sleep(wait)

    pid = (phone.shell(f"pidof {package}").split() or [""])[0]
    crash = phone.shell("logcat -b crash -d")
    focus = phone.shell("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -n 2")
    log = phone.shell("logcat -d -v brief")
    code, png = phone.run("exec-out", "screencap", "-p", binary=True)
    if code == 0 and png.startswith(b"\x89PNG"):
        shot = shots / f"{package}.png"
        shot.write_bytes(png)
        result["snimka"] = shot.name

    crashed = f"Process: {package}" in crash or f">>> {package} <<<" in crash
    if crashed:
        line = next((l for l in crash.splitlines() if "Exception" in l or "Error" in l), "pád")
        result["spustenie"] = f"PÁD: {line.strip()[:150]}"
    elif not pid:
        result["spustenie"] = "CHYBA: proces po spustení nebeží"
    elif package not in focus:
        result["spustenie"] = "OK (beží, ale nie je na popredí)"
    else:
        result["spustenie"] = "OK"
    own = re.compile(rf"\(\s*{pid}\)") if pid else None
    app_lines = [l for l in log.splitlines() if package in l or (own and own.search(l))]
    if GOOGLE_HINTS.search("\n".join(app_lines)):
        result["poznamky"].append("hľadá služby Google (Play services/Firebase) – ďalej funguje bez nich?")
    phone.shell(f"am force-stop {package}")
    return result


# ---------------------------------------------------------------- report

def write_report(report: Path, device: dict, rows: list[dict]) -> int:
    ok = sum(1 for r in rows if r.get("instalacia") == "OK" and r.get("spustenie", "").startswith("OK"))
    lines = [
        "# Test kompatibility aplikácií z F-Droid na LECH OS", "",
        f"- Dátum: {datetime.now().astimezone().strftime('%Y-%m-%d %H:%M %Z')}",
        f"- Telefón: {device['model']}, LECH OS {device['version']} (`{device['display']}`), Android SDK {device['sdk']}, ABI {', '.join(device['abis'])}",
        f"- Zdroj: {REPO} (index-v2.json; SHA-256 každého APK overený podľa indexu)",
        f"- **Výsledok: {ok} z {len(rows)} aplikácií sa nainštalovalo a spustilo bez pádu**", "",
        "| Aplikácia | Kategória | Verzia | Kód | Inštalácia | Spustenie | Poznámky |",
        "|---|---|---|---|---|---|---|",
    ]
    cell = lambda text: str(text).replace("|", "\\|").replace("\n", " ")  # noqa: E731
    for r in rows:
        version = f"{r['versionName']} ({r['versionCode']})" if r.get("versionCode") else "–"
        notes = "; ".join(r.get("poznamky", [])) or "–"
        lines.append(f"| {cell(r['name'])} (`{r['package']}`) | {cell(r['category'])} | {cell(version)} | "
                     f"{cell(r.get('abi') or '–')} | {cell(r.get('instalacia') or '–')} | "
                     f"{cell(r.get('spustenie') or '–')} | {cell(notes)} |")
    lines += ["", "## Ako sa testovalo", "",
              "Inštalácia `adb install -r`, spustenie spúšťacej aktivity (`monkey -p <balík> -c android.intent.category.LAUNCHER 1`), "
              "po čakaní kontrola procesu (`pidof`), aktivity na popredí (`dumpsys activity`) a záznamu pádov (`logcat -b crash`). "
              "Test overuje, že sa aplikácia spustí – nie všetky jej funkcie. Snímky obrazovky sú na zostavovacom počítači.", ""]
    report.mkdir(parents=True, exist_ok=True)
    (report / "sprava.md").write_text("\n".join(lines), encoding="utf-8")
    (report / "sprava.json").write_text(json.dumps({"telefon": device, "aplikacie": rows}, ensure_ascii=False, indent=2),
                                        encoding="utf-8")
    return ok


def read_list(path: Path) -> list[dict]:
    apps = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = [p.strip() for p in line.split("|")]
        apps.append({"package": parts[0], "name": parts[1] if len(parts) > 1 else parts[0],
                     "category": parts[2] if len(parts) > 2 else "", "why": parts[3] if len(parts) > 3 else ""})
    return apps


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--adb", required=True)
    ap.add_argument("--list", type=Path, required=True)
    ap.add_argument("--report", type=Path, required=True, help="priečinok správy (v repozitári)")
    ap.add_argument("--work", type=Path, required=True, help="pracovný priečinok (APK, index, snímky)")
    ap.add_argument("--wait", type=int, default=8, help="sekundy po spustení aplikácie")
    args = ap.parse_args()

    phone = Phone(args.adb)
    device = {"model": phone.prop("ro.product.model"), "version": phone.prop("ro.lech.version"),
              "display": phone.prop("ro.build.display.id"), "sdk": int(phone.prop("ro.build.version.sdk") or 0),
              "abis": [a for a in phone.prop("ro.product.cpu.abilist").split(",") if a]}
    if not device["sdk"]:
        sys.exit("CHYBA: telefón neodpovedá cez adb (beží? tools/etapa2/06-zostav-lech.sh --spustit)")
    print(f"Telefón: {device['model']}, LECH OS {device['version']}, SDK {device['sdk']}, ABI {','.join(device['abis'])}")

    # Screen on and unlocked, so the apps start in the foreground and the screenshots show them.
    phone.shell("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard; svc power stayon true")
    index = load_index(args.work)
    if not isinstance(index.get("packages"), dict) or not index["packages"]:
        sys.exit("CHYBA: index F-Droid má neznámy formát (chýba 'packages') – pošlite tento výpis.")
    shots = args.work / "snimky"
    shots.mkdir(parents=True, exist_ok=True)
    rows = []
    apps = read_list(args.list)
    for number, app in enumerate(apps, 1):
        cands, metadata = candidates(index, app["package"])
        chosen, why_not = choose(cands, metadata, device["abis"], device["sdk"])
        row = {**app, "name": app["name"] or localized(metadata.get("name"), app["package"])}
        print(f"[{number}/{len(apps)}] {row['name']}: ", end="", flush=True)
        if not chosen:
            row.update({"instalacia": f"NEDOSTUPNÉ: {why_not}", "spustenie": "–"})
            print(row["instalacia"])
            rows.append(row)
            continue
        row.update({k: chosen[k] for k in ("versionName", "versionCode", "targetSdk", "nativecode", "sha256", "abi")})
        try:
            apk = download(f"{REPO}/{chosen['file']}", args.work / "apk" / chosen["file"], chosen["sha256"])
        except Exception as error:  # network, hash mismatch
            row.update({"instalacia": f"CHYBA sťahovania: {error}", "spustenie": "–"})
            print(row["instalacia"])
            rows.append(row)
            continue
        row.update(test_app(phone, app, apk, args.wait, shots))
        print(f"inštalácia {row['instalacia']}, spustenie {row['spustenie']}"
              + (f" ({'; '.join(row['poznamky'])})" if row["poznamky"] else ""))
        rows.append(row)

    phone.shell("svc power stayon false")
    ok = write_report(args.report, device, rows)
    print(f"\nVýsledok: {ok} z {len(rows)} aplikácií OK. Správa: {args.report / 'sprava.md'}; snímky: {shots}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
