#!/usr/bin/env python3
"""Generates the LECH OS boot animation: vendor/lech/bootanimation/bootanimation.zip.

The format is described in frameworks/base/cmds/bootanimation/FORMAT.md: a desc.txt file plus one
folder of PNG frames per part, stored uncompressed in a zip. Frames are drawn centered on the screen
at their native size; the rest of the screen is filled with the background colour from desc.txt.

Part 0 (played once): the LECH OS wordmark fades in.
Part 1 (loops until the system has booted): three dots pulse under the wordmark.

Requires Pillow (apt install python3-pil) and the DejaVu Sans fonts (apt install fonts-dejavu-core).
The generated zip is committed, so the AOSP build itself needs neither.

Usage: python3 tools/make_bootanimation.py [--out PATH] [--preview DIR]
"""

import argparse
import io
import math
import sys
import zipfile
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("Pillow is missing: sudo apt install python3-pil")

WIDTH, HEIGHT, FPS = 600, 360, 30
BACKGROUND = (0x0A, 0x0E, 0x17)
FOREGROUND = (0xF2, 0xF5, 0xF8)
ACCENT = (0x5E, 0xEA, 0xD4)
FADE_IN_FRAMES = 24
HOLD_FRAMES = 6
PULSE_FRAMES = 36
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUT = REPO_ROOT / "vendor/lech/bootanimation/bootanimation.zip"


def mix(color, amount):
    """Blends color over the background; amount 0 = background, 1 = color."""
    return tuple(round(b + (c - b) * amount) for b, c in zip(BACKGROUND, color))


def load_fonts():
    try:
        bold = ImageFont.truetype(str(FONT_DIR / "DejaVuSans-Bold.ttf"), 112)
        regular = ImageFont.truetype(str(FONT_DIR / "DejaVuSans.ttf"), 112)
    except OSError:
        sys.exit(f"DejaVu fonts not found in {FONT_DIR}: sudo apt install fonts-dejavu-core")
    return bold, regular


def draw_frame(fonts, text_amount, dot_amounts):
    bold, regular = fonts
    image = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(image)

    lech, os_ = "LECH", "OS"
    gap = 30
    lech_w = draw.textlength(lech, font=bold)
    os_w = draw.textlength(os_, font=regular)
    x = (WIDTH - (lech_w + gap + os_w)) / 2
    baseline = 200
    draw.text((x, baseline), lech, font=bold, fill=mix(FOREGROUND, text_amount), anchor="ls")
    draw.text((x + lech_w + gap, baseline), os_, font=regular, fill=mix(ACCENT, text_amount), anchor="ls")

    radius, spacing, y = 7, 34, 278
    first_x = WIDTH / 2 - spacing
    for i, amount in enumerate(dot_amounts):
        cx = first_x + i * spacing
        draw.ellipse((cx - radius, y - radius, cx + radius, y + radius), fill=mix(ACCENT, amount))
    return image


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def build_parts(fonts):
    part0 = []
    for i in range(FADE_IN_FRAMES):
        t = (i + 1) / FADE_IN_FRAMES
        eased = 1 - (1 - t) ** 3
        part0.append(draw_frame(fonts, eased, (0.0, 0.0, 0.0)))
    part0 += [draw_frame(fonts, 1.0, (0.0, 0.0, 0.0))] * HOLD_FRAMES

    part1 = []
    for i in range(PULSE_FRAMES):
        phase = 2 * math.pi * i / PULSE_FRAMES
        dots = tuple(0.25 + 0.75 * (0.5 + 0.5 * math.sin(phase - k * 2 * math.pi / 3)) ** 2 for k in range(3))
        part1.append(draw_frame(fonts, 1.0, dots))
    return part0, part1


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--preview", type=Path, help="also write the frames as PNG files into this directory")
    args = parser.parse_args()

    part0, part1 = build_parts(load_fonts())
    hex_bg = "#%02X%02X%02X" % BACKGROUND
    desc = f"{WIDTH} {HEIGHT} {FPS}\nc 1 0 part0 {hex_bg}\np 0 0 part1 {hex_bg}\n"

    args.out.parent.mkdir(parents=True, exist_ok=True)
    # Fixed timestamps keep the zip byte-for-byte reproducible.
    stamp = (2026, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(args.out, "w", compression=zipfile.ZIP_STORED) as archive:
        archive.writestr(zipfile.ZipInfo("desc.txt", stamp), desc)
        for name, frames in (("part0", part0), ("part1", part1)):
            for index, frame in enumerate(frames):
                archive.writestr(zipfile.ZipInfo(f"{name}/{index:03d}.png", stamp), png_bytes(frame))

    if args.preview:
        args.preview.mkdir(parents=True, exist_ok=True)
        for name, frames in (("part0", part0), ("part1", part1)):
            for index, frame in enumerate(frames):
                frame.save(args.preview / f"{name}-{index:03d}.png")

    print(f"{args.out}: {len(part0) + len(part1)} frames, {args.out.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
