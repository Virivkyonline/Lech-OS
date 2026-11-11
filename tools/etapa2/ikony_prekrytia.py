#!/usr/bin/env python3
"""Generates runtime resource overlays (RRO) that give the system apps of LECH OS their LECH OS icons.

The icons are adaptive icons from branding/ikony/android-res (background, foreground and monochrome layers per
density). Which resources an app uses as its launcher icon is read from the app itself, as built in the last
build (aapt2: AndroidManifest.xml of the APK and its resource table) - so the overlay does not depend on knowing
the icon names of each AOSP version: the application icon and roundIcon and the icons of launcher activities
(MAIN/LAUNCHER) are replaced. An overlay is a static overlay in the product partition; it can replace any
resource of an app that declares no <overlayable> resources (the AOSP system apps), otherwise only overlayable
ones - other cases are reported and skipped.

Output: <out>/<App>/{Android.bp, AndroidManifest.xml, res/...} and <out>/ikony.mk (PRODUCT_PACKAGES), included
by vendor/lech/config/common.mk. Without a previous build (no APKs yet) ikony.mk stays empty: the icons come with
the next build.

Usage: python3 ikony_prekrytia.py --aapt2 AAPT2 --product-out OUT --icons RES_DIR --out OUT_DIR
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

# Module (APK directory) names that may hold the app, icon base name in branding/ikony/android-res, label.
# Contacts, Gallery2, Messaging, DeskClock, Calendar and Camera2 are replaced by the own apps of vendor/lech/aplikacie.
APPS = [
    (("TrichromeChrome", "Browser2"), "ic_prehliadac", "Prehliadač"),
    (("Settings",), "ic_nastavenia", "Nastavenia"),
    (("Dialer",), "ic_telefon", "Telefón"),
    (("DocumentsUI",), "ic_subory", "Súbory"),
]
PARTITIONS = ("system", "system_ext", "product")
DENSITIES = ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
LAYERS = ("background", "foreground", "monochrome")
ANDROID_NS = "http://schemas.android.com/apk/res/android:"
ELEMENT = re.compile(r"^(\s*)E: ([\w.-]+)")
ATTR = re.compile(r"^(\s*)A: (\S+?)(?:\(0x[0-9a-f]+\))?=(.*)$")
RESOURCE = re.compile(r"^\s*resource 0x([0-9a-fA-F]{8}) ([\w.-]+)/([\w.$-]+)")


def run(cmd: list[str]) -> str:
    return subprocess.run(cmd, check=True, capture_output=True, text=True).stdout


def find_apk(product_out: Path, names: tuple[str, ...], extra: dict[str, Path]) -> Path | None:
    for name in names:   # prebuilt APKs given on the command line (--apk): the icon comes with their first build
        if name in extra and extra[name].is_file():
            return extra[name]
    for part in PARTITIONS:
        for kind in ("priv-app", "app"):
            for name in names:
                apk = product_out / part / kind / name / f"{name}.apk"
                if apk.is_file():
                    return apk
    return None


def parse_manifest(text: str) -> tuple[str, list[str]]:
    """Package name and the icon values (ids '@0x7f..' or '@type/name') of the app and its launcher entries."""
    package = ""
    stack: list[dict] = []   # elements: {"name", "depth", "attrs", "children"}
    root: list[dict] = []
    for line in text.splitlines():
        m = ELEMENT.match(line)
        if m:
            depth = len(m.group(1))
            while stack and stack[-1]["depth"] >= depth:
                stack.pop()
            el = {"name": m.group(2), "depth": depth, "attrs": {}, "children": []}
            (stack[-1]["children"] if stack else root).append(el)
            stack.append(el)
            continue
        m = ATTR.match(line)
        if m and stack:
            key, value = m.group(2), m.group(3).strip()
            if key.startswith(ANDROID_NS):
                key = key[len(ANDROID_NS):]
            value = re.sub(r'\s*\(Raw: .*\)$', "", value).strip().strip('"')
            stack[-1]["attrs"][key] = value
            if stack[-1]["name"] == "manifest" and key == "package":
                package = value

    def walk(els):
        for el in els:
            yield el
            yield from walk(el["children"])

    def is_launcher(el) -> bool:
        for f in el["children"]:
            if f["name"] != "intent-filter":
                continue
            names = {c["attrs"].get("name") for c in f["children"]}
            if "android.intent.action.MAIN" in names and "android.intent.category.LAUNCHER" in names:
                return True
        return False

    icons: list[str] = []
    for el in walk(root):
        launcher = el["name"] in ("activity", "activity-alias") and is_launcher(el)
        if el["name"] == "application" or launcher:
            for key in ("icon", "roundIcon"):
                value = el["attrs"].get(key, "")
                if value.startswith("@") and value not in icons:
                    icons.append(value)
    return package, icons


def resource_names(text: str) -> dict[str, tuple[str, str]]:
    names = {}
    for line in text.splitlines():
        m = RESOURCE.match(line)
        if m:
            names["@0x" + m.group(1).lower()] = (m.group(2), m.group(3))
    return names


def write_overlay(out: Path, module: str, app: str, base: str, label: str, package: str,
                  targets: list[tuple[str, str]], icons_dir: Path) -> None:
    app_dir = out / module
    if app_dir.exists():
        shutil.rmtree(app_dir)
    res = app_dir / "res"
    for d in DENSITIES:
        (res / f"mipmap-{d}").mkdir(parents=True)
        for layer in LAYERS:
            shutil.copyfile(icons_dir / f"mipmap-{d}" / f"{base}_{layer}.png",
                            res / f"mipmap-{d}" / f"lech_{base}_{layer}.png")
    adaptive = ("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                "<!-- LECH OS icon (branding/ikony), generated by tools/etapa2/ikony_prekrytia.py -->\n"
                "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                f"    <background android:drawable=\"@mipmap/lech_{base}_background\"/>\n"
                f"    <foreground android:drawable=\"@mipmap/lech_{base}_foreground\"/>\n"
                f"    <monochrome android:drawable=\"@mipmap/lech_{base}_monochrome\"/>\n"
                "</adaptive-icon>\n")
    for rtype, name in targets:
        d = res / f"{rtype}-anydpi-v26"
        d.mkdir(parents=True, exist_ok=True)
        (d / f"{name}.xml").write_text(adaptive, encoding="utf-8")
    (app_dir / "AndroidManifest.xml").write_text(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        f"<!-- LECH OS: icon of {label} ({package}); generated by tools/etapa2/ikony_prekrytia.py -->\n"
        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
        f"    package=\"lech.os.overlay.ikona.{app.lower()}\">\n"
        "    <application android:hasCode=\"false\" />\n"
        f"    <overlay android:targetPackage=\"{package}\" android:isStatic=\"true\" android:priority=\"10\" />\n"
        "</manifest>\n", encoding="utf-8")
    (app_dir / "Android.bp").write_text(
        "// LECH OS: icon overlay, generated by tools/etapa2/ikony_prekrytia.py (do not edit).\n"
        "package {\n    default_applicable_licenses: [\"Android-Apache-2.0\"],\n}\n\n"
        "runtime_resource_overlay {\n"
        f"    name: \"{module}\",\n"
        "    product_specific: true,\n"
        "    sdk_version: \"current\",\n"
        "}\n", encoding="utf-8")


def write_icon_package(out: Path, entries: list[tuple[str, str, str]], icons_dir: Path) -> None:
    """The resource package lech.os.ikony (module LechIkony): one adaptive icon per app, named after its package,
    for apps whose icon an overlay cannot replace. The launcher takes it from there (patches/frameworks/libs/systemui
    0002)."""
    app_dir = out / "LechIkony"
    if app_dir.exists():
        shutil.rmtree(app_dir)
    res = app_dir / "res"
    (res / "mipmap-anydpi-v26").mkdir(parents=True)
    for package, base, label in entries:
        for d in DENSITIES:
            (res / f"mipmap-{d}").mkdir(exist_ok=True)
            for layer in LAYERS:
                shutil.copyfile(icons_dir / f"mipmap-{d}" / f"{base}_{layer}.png",
                                res / f"mipmap-{d}" / f"lech_{base}_{layer}.png")
        name = package.lower().replace(".", "_")
        (res / "mipmap-anydpi-v26" / f"{name}.xml").write_text(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            f"<!-- LECH OS icon of {label} ({package}), generated by tools/etapa2/ikony_prekrytia.py -->\n"
            "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
            f"    <background android:drawable=\"@mipmap/lech_{base}_background\"/>\n"
            f"    <foreground android:drawable=\"@mipmap/lech_{base}_foreground\"/>\n"
            f"    <monochrome android:drawable=\"@mipmap/lech_{base}_monochrome\"/>\n"
            "</adaptive-icon>\n", encoding="utf-8")
    (app_dir / "AndroidManifest.xml").write_text(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
        "<!-- LECH OS: icons for the launcher (patches/frameworks/libs/systemui 0002); generated by\n"
        "     tools/etapa2/ikony_prekrytia.py -->\n"
        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
        "    package=\"lech.os.ikony\">\n"
        "    <application android:hasCode=\"false\" android:label=\"Ikony LECH OS\" />\n"
        "</manifest>\n", encoding="utf-8")
    (app_dir / "Android.bp").write_text(
        "// LECH OS: icons for the launcher, generated by tools/etapa2/ikony_prekrytia.py (do not edit).\n"
        "package {\n    default_applicable_licenses: [\"Android-Apache-2.0\"],\n}\n\n"
        "android_app {\n"
        "    name: \"LechIkony\",\n"
        "    product_specific: true,\n"
        "    sdk_version: \"current\",\n"
        "    dex_preopt: {\n        enabled: false,\n    },\n"
        "}\n", encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--aapt2", required=True)
    ap.add_argument("--product-out", type=Path, required=True)
    ap.add_argument("--icons", type=Path, required=True, help="branding/ikony/android-res")
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--apk", action="append", default=[], metavar="MODUL=CESTA",
                    help="prebuilt APK of a module, used before the APKs of the last build")
    args = ap.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    modules = []
    icon_package: list[tuple[str, str, str]] = []   # apps whose icon only the launcher can replace
    for apk_names, base, label in APPS:
        module = "LechIkona" + apk_names[0][0].upper() + apk_names[0][1:] + "Overlay"
        if not all((args.icons / f"mipmap-{d}" / f"{base}_{layer}.png").is_file() for d in DENSITIES for layer in LAYERS):
            print(f"  {label}: chýbajú vrstvy ikony {base} v {args.icons}")
            continue
        apk = find_apk(args.product_out, apk_names,
                       {k: Path(v) for k, _, v in (a.partition("=") for a in args.apk)})
        if not apk:
            print(f"  {label}: aplikácia ({'/'.join(apk_names)}) nie je v poslednej zostave – ikona s ďalšou zostavou")
            continue
        try:
            package, icons = parse_manifest(run([args.aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)]))
            names = resource_names(run([args.aapt2, "dump", "resources", str(apk)]))
            try:
                overlayable = run([args.aapt2, "dump", "overlayable", str(apk)])
            except subprocess.CalledProcessError:
                overlayable = ""
        except (OSError, subprocess.CalledProcessError) as e:
            print(f"  {label}: aapt2 zlyhal ({e}) – ikona sa nenahradí")
            continue
        targets, skipped = [], []
        for value in icons:
            if value.startswith("@0x"):
                if not value.startswith("@0x7f"):
                    skipped.append(f"{value} (zdroj frameworku)")
                    continue
                rt = names.get(value.lower())
                if not rt:
                    skipped.append(f"{value} (neznámy zdroj)")
                    continue
            else:
                m = re.match(r"@(?:\+)?(?:[\w.]+:)?([\w-]+)/([\w.$-]+)$", value)
                if not m:
                    skipped.append(value)
                    continue
                rt = (m.group(1), m.group(2))
            if "obfuscated" in rt[1]:   # release builds of Chromium collapse resource names: no overlay by name
                skipped.append(f"{rt[0]}/{rt[1]} (názov zdroja je skrytý)")
                continue
            if rt[0] not in ("mipmap", "drawable"):
                skipped.append(f"{rt[0]}/{rt[1]}")
                continue
            if overlayable.strip() and f"{rt[0]}/{rt[1]}" not in overlayable:
                skipped.append(f"{rt[0]}/{rt[1]} (aplikácia ho nepovoľuje prekryť)")
                continue
            if rt not in targets:
                targets.append(rt)
        if package and not targets and icons:
            # The app does not let overlays change its icon: the launcher takes it from lech.os.ikony instead.
            icon_package.append((package, base, label))
            print(f"  {label}: {package} – ikona v balíku ikon plochy (prekrytie aplikácia nepovoľuje)")
            continue
        if not package or not targets:
            print(f"  {label}: ikona sa nedá nahradiť ({', '.join(skipped) or 'žiadna ikona v manifeste'})")
            continue
        write_overlay(args.out, module, apk_names[0], base, label, package, targets, args.icons)
        modules.append(module)
        note = f"; vynechané: {', '.join(skipped)}" if skipped else ""
        print(f"  {label}: {package} – {', '.join(f'@{t}/{n}' for t, n in targets)}{note}")

    if icon_package:
        write_icon_package(args.out, icon_package, args.icons)
        modules.append("LechIkony")
    elif (args.out / "LechIkony").exists():
        shutil.rmtree(args.out / "LechIkony")
    (args.out / "ikony.mk").write_text(
        "# LECH OS icons of the system apps, generated by tools/etapa2/ikony_prekrytia.py (do not edit).\n"
        + ("PRODUCT_PACKAGES += \\\n" + "".join(f"    {m} \\\n" for m in modules) + "\n" if modules else ""),
        encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
