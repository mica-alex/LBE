#!/usr/bin/env python3
"""Generate the placeholder loot-box block textures.

Writes 16x16 PNGs for each rarity tier into
``src/main/resources/assets/lbe/textures/blocks``: a wooden crate with a
tier-coloured band on the sides and a tier-coloured latch on the top.

These are *placeholders* — deliberately simple, deliberately readable at a
glance, and deliberately generated rather than hand-drawn so that changing the
tier palette is a one-line edit here rather than four image files. Replace them
with real art whenever someone has the time; nothing in the mod depends on how
they look.

Pure standard library (zlib + struct): the repo has no Pillow dependency and is
not getting one for four sixteen-pixel squares.

Usage (from the repo root):
    python tools/gen_box_textures.py
"""

import os
import struct
import zlib

SIZE = 16
OUT_DIR = os.path.join("src", "main", "resources", "assets", "lbe", "textures", "blocks")

# Crate body, shared by every tier. The tier colour is the only thing that changes,
# which is the point: four boxes that read as the same object at four values.
PLANK = (0x8B, 0x6A, 0x43)
PLANK_DARK = (0x6E, 0x53, 0x34)
FRAME = (0x5C, 0x44, 0x29)
FRAME_DARK = (0x45, 0x33, 0x1E)

# Tier accents, matching the formatting codes in Rarity.java.
TIERS = {
    "common": ((0xC6, 0xC6, 0xC6), (0x8E, 0x8E, 0x8E)),
    "uncommon": ((0x55, 0xD8, 0x55), (0x35, 0x9B, 0x35)),
    "rare": ((0x55, 0xD8, 0xD8), (0x33, 0x96, 0x9C)),
    "legendary": ((0xFF, 0xB3, 0x00), (0xC1, 0x7D, 0x00)),
}


def write_png(path, pixels):
    """Write an RGB PNG. `pixels` is a SIZE-row list of SIZE (r, g, b) tuples."""
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBB", *pixel) for pixel in row) for row in pixels
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as handle:
        handle.write(png)


def crate_base():
    """A plank field with a dark frame around the edge — the shared crate body."""
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            edge = x in (0, SIZE - 1) or y in (0, SIZE - 1)
            inner_edge = x in (1, SIZE - 2) or y in (1, SIZE - 2)
            if edge:
                row.append(FRAME_DARK)
            elif inner_edge:
                row.append(FRAME)
            else:
                # Alternating plank shading, two rows per plank.
                row.append(PLANK if (y // 2) % 2 == 0 else PLANK_DARK)
        rows.append(row)
    return rows


def side_texture(accent, accent_dark):
    """Crate body with a horizontal tier band across the middle."""
    rows = crate_base()
    for y in range(6, 10):
        for x in range(2, SIZE - 2):
            rows[y][x] = accent if y in (7, 8) else accent_dark
    return rows


def top_texture(accent, accent_dark):
    """Crate body with a tier-coloured latch in the centre."""
    rows = crate_base()
    for y in range(6, 10):
        for x in range(6, 10):
            border = y in (6, 9) or x in (6, 9)
            rows[y][x] = accent_dark if border else accent
    return rows


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for tier, (accent, accent_dark) in TIERS.items():
        write_png(
            os.path.join(OUT_DIR, "loot_box_%s_side.png" % tier),
            side_texture(accent, accent_dark),
        )
        write_png(
            os.path.join(OUT_DIR, "loot_box_%s_top.png" % tier),
            top_texture(accent, accent_dark),
        )
        print("wrote loot_box_%s_{side,top}.png" % tier)


if __name__ == "__main__":
    main()
