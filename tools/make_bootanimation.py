#!/usr/bin/env python3
"""Generates the LECH OS boot animation: vendor/lech/bootanimation/bootanimation.zip.

The format is described in frameworks/base/cmds/bootanimation/FORMAT.md: a desc.txt file plus one
folder of PNG frames per part, stored uncompressed in a zip. Frames are drawn centered on the screen
at their native size; the rest of the screen is filled with the background colour from desc.txt.

Design (owner, 11. 11. 2026: "a modern start animation with the font of the home screen, 'your secure phone'
under LECH OS and rings around it", in English): on black, the LECH OS logo of the wallpaper (branding/logo),
the line "YOUR SECURE PHONE" under it and three glowing rings in the green and cyan of the wallpaper.
Part 0 (played once): the rings open and fade in, then the logo, then the line.
Part 1 (loops until the system has booted): arcs run around the rings, each ring at its own speed; the loop
is seamless because every ring turns by a multiple of its symmetry.

Requires Pillow (apt install python3-pil) and the DejaVu Sans fonts (apt install fonts-dejavu-core).
The generated zip is committed, so the AOSP build itself needs neither.

Usage: python3 tools/make_bootanimation.py [--out PATH] [--preview DIR] [--tagline TEXT]
"""

import argparse
import io
import math
import sys
import zipfile
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.exit("Pillow is missing: sudo apt install python3-pil")

SIZE, FPS = 1080, 30
CENTER = (SIZE / 2, SIZE / 2 - 10)
BACKGROUND = (0, 0, 0)
GREEN = (118, 255, 64)
CYAN = (40, 200, 255)
TEXT = (205, 214, 224)
INTRO_FRAMES = 48
LOOP_FRAMES = 60
TAGLINE = "YOUR SECURE PHONE"
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
REPO_ROOT = Path(__file__).resolve().parent.parent
LOGO = REPO_ROOT / "branding/logo/lech-os-logo-z-tapety.png"
DEFAULT_OUT = REPO_ROOT / "vendor/lech/bootanimation/bootanimation.zip"

# Rings: radius, line width, colour, number of arcs, arc length (degrees), turn per loop (degrees; a multiple of
# 360 / arcs keeps the loop seamless), start angle.
RINGS = [
    (318, 6, GREEN, 2, 70, 180, 10),
    (382, 5, CYAN, 3, 38, -120, 40),
    (446, 4, GREEN, 1, 110, 360, 200),
]


def ease_out(t):
    t = min(max(t, 0.0), 1.0)
    return 1 - (1 - t) ** 3


def load_assets():
    try:
        font = ImageFont.truetype(str(FONT_DIR / "DejaVuSans.ttf"), 30)
    except OSError:
        sys.exit(f"DejaVu fonts not found in {FONT_DIR}: sudo apt install fonts-dejavu-core")
    if not LOGO.is_file():
        sys.exit(f"Logo missing: {LOGO}")
    logo = Image.open(LOGO).convert("RGBA")
    scale = 720 / logo.width
    logo = logo.resize((round(logo.width * scale), round(logo.height * scale)), Image.LANCZOS)
    return font, logo


def tagline_image(font, text):
    spacing = 9   # letter spacing in px, as "M O B I L E  S Y S T E M" in the design
    widths = [font.getlength(ch) for ch in text]
    width = round(sum(widths) + spacing * (len(text) - 1)) + 4
    image = Image.new("RGBA", (width, 44), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    x = 2
    for ch, w in zip(text, widths):
        draw.text((x, 34), ch, font=font, fill=TEXT + (255,), anchor="ls")
        x += w + spacing
    return image


def with_alpha(image, amount):
    if amount >= 1:
        return image
    alpha = image.getchannel("A").point(lambda v: round(v * max(amount, 0.0)))
    out = image.copy()
    out.putalpha(alpha)
    return out


def draw_rings(t_frame, opening, glow_boost):
    """The rings at frame t_frame (frames since the start of the loop; negative in the intro)."""
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    cx, cy = CENTER
    scale = 0.72 + 0.28 * opening
    for radius, width, colour, arcs, length, turn, start in RINGS:
        r = radius * scale
        box = (cx - r, cy - r, cx + r, cy + r)
        # the whole ring, faint
        draw.ellipse(box, outline=colour + (round(80 * opening),), width=2)
        angle = start + turn * t_frame / LOOP_FRAMES
        for k in range(arcs):
            a0 = angle + k * 360 / arcs
            draw.arc(box, a0, a0 + length, fill=colour + (round(235 * opening),), width=width)
    wide = layer.filter(ImageFilter.GaussianBlur(16))
    near = layer.filter(ImageFilter.GaussianBlur(5))
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for _ in range(2 + glow_boost):
        out = Image.alpha_composite(out, wide)
    out = Image.alpha_composite(out, near)
    return Image.alpha_composite(out, layer)


def draw_frame(assets, t_frame, opening, logo_amount, tagline_amount, pulse):
    font, logo, tagline = assets
    frame = Image.new("RGBA", (SIZE, SIZE), BACKGROUND + (255,))
    frame = Image.alpha_composite(frame, draw_rings(t_frame, opening, 1 if pulse > 0.5 else 0))
    if logo_amount > 0:
        s = 0.96 + 0.04 * logo_amount
        lg = logo.resize((round(logo.width * s), round(logo.height * s)), Image.LANCZOS)
        lg = with_alpha(lg, logo_amount)
        frame.alpha_composite(lg, (round(CENTER[0] - lg.width / 2), round(CENTER[1] - lg.height / 2 - 40)))
    if tagline_amount > 0:
        tg = with_alpha(tagline, tagline_amount)
        frame.alpha_composite(tg, (round(CENTER[0] - tg.width / 2), round(CENTER[1] + 95)))
    return frame.convert("RGB")


def build_parts(assets):
    part0 = []
    for i in range(INTRO_FRAMES):
        t = i - INTRO_FRAMES   # continuous with the loop: the last intro frame is just before loop frame 0
        opening = ease_out(i / 20)
        logo_amount = ease_out((i - 12) / 18)
        tagline_amount = ease_out((i - 28) / 16)
        part0.append(draw_frame(assets, t, opening, logo_amount, tagline_amount, 0.0))
    part1 = []
    for i in range(LOOP_FRAMES):
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * i / LOOP_FRAMES)
        part1.append(draw_frame(assets, i, 1.0, 1.0, 1.0, pulse))
    return part0, part1


def png_bytes(image):
    buffer = io.BytesIO()
    image.save(buffer, format="PNG", optimize=True)
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--preview", type=Path, help="also write the frames as PNG files into this directory")
    parser.add_argument("--tagline", default=TAGLINE, help="the line under the logo")
    args = parser.parse_args()

    font, logo = load_assets()
    part0, part1 = build_parts((font, logo, tagline_image(font, args.tagline)))
    hex_bg = "#%02X%02X%02X" % BACKGROUND
    desc = f"{SIZE} {SIZE} {FPS}\nc 1 0 part0 {hex_bg}\np 0 0 part1 {hex_bg}\n"

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
