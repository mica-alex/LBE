#!/usr/bin/env python3
"""Generate the loot-box block textures and the TESR glint.

Writes 16x16 PNGs into ``src/main/resources/assets/lbe/textures/blocks``:

    loot_box_<tier>_side.png   crate side: plank grain, iron corner brackets,
                               a lid seam, and a tier-coloured gem lock
    loot_box_<tier>_top.png    crate lid: cross-bracing and a tier gem
    glint.png                  white 4-point sparkle with alpha, tinted per
                               tier at render time by TileEntityLootBoxRenderer

Generated rather than hand-drawn on purpose: the tier palette lives in one
dict here, so restyling all four boxes is a one-line edit instead of eight
image files opened in an editor. The shapes are deliberately chunky and
high-contrast — these are read at a distance, in a dark cave, by someone
deciding whether it is worth walking over.

Pure standard library (zlib + struct): the repo has no Pillow dependency and
is not getting one.

Usage (from the repo root):
    python tools/gen_box_textures.py
"""

import os
import struct
import zlib

SIZE = 16
OUT_DIR = os.path.join("src", "main", "resources", "assets", "lbe", "textures", "blocks")

# --- shared crate body ------------------------------------------------------
# Warm wood, so the tier colour is the only thing competing for attention.
PLANK_LIT = (0x9C, 0x78, 0x4C)
PLANK = (0x8B, 0x6A, 0x43)
PLANK_DARK = (0x74, 0x58, 0x38)
GRAIN = (0x66, 0x4D, 0x30)

# Iron banding around the edges and corners.
IRON_LIT = (0x9A, 0x9A, 0xA2)
IRON = (0x6E, 0x6E, 0x78)
IRON_DARK = (0x4A, 0x4A, 0x52)

# Tier accents: (bright, mid, dark). Matches Rarity.rgb() on the Java side —
# keep the two in step or the block and its glint will disagree.
TIERS = {
    "common": ((0xE0, 0xE0, 0xE0), (0xB0, 0xB0, 0xB8), (0x78, 0x78, 0x80)),
    "uncommon": ((0x7B, 0xE8, 0x6C), (0x4F, 0xC0, 0x45), (0x2E, 0x84, 0x2A)),
    "rare": ((0x7A, 0xDC, 0xF0), (0x3F, 0xA9, 0xC9), (0x25, 0x6E, 0x8C)),
    "legendary": ((0xFF, 0xD5, 0x54), (0xF0, 0xA8, 0x1E), (0xB0, 0x70, 0x08)),
}


def write_png(path, pixels, alpha=False):
    """Write an RGB (or RGBA) PNG. `pixels` is SIZE rows of SIZE tuples."""
    depth = 6 if alpha else 2
    fmt = "BBBB" if alpha else "BBB"
    raw = b"".join(
        b"\x00" + b"".join(struct.pack(fmt, *pixel) for pixel in row) for row in pixels
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, depth, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as handle:
        handle.write(png)


def plank_field():
    """Horizontal planks with grain flecks — the base every face starts from."""
    rows = []
    for y in range(SIZE):
        row = []
        band = y % 4
        for x in range(SIZE):
            if band == 0:
                colour = PLANK_DARK  # seam between planks
            elif band == 1:
                colour = PLANK_LIT  # top of the plank catches light
            else:
                colour = PLANK
            # Deterministic pseudo-grain: no RNG, so reruns are byte-identical
            # and the textures never show up as a spurious diff.
            if band in (2, 3) and (x * 7 + y * 13) % 11 == 0:
                colour = GRAIN
            row.append(colour)
        rows.append(row)
    return rows


def iron_frame(rows):
    """Iron banding around the border, brighter along the top and left."""
    for i in range(SIZE):
        rows[0][i] = IRON_LIT
        rows[SIZE - 1][i] = IRON_DARK
        rows[i][0] = IRON_LIT if i < SIZE // 2 else IRON
        rows[i][SIZE - 1] = IRON_DARK
    # Corner blocks, so the banding reads as brackets rather than an outline.
    for y, x in [(1, 1), (1, 2), (2, 1), (1, SIZE - 2), (1, SIZE - 3), (2, SIZE - 2),
                 (SIZE - 2, 1), (SIZE - 2, 2), (SIZE - 3, 1),
                 (SIZE - 2, SIZE - 2), (SIZE - 2, SIZE - 3), (SIZE - 3, SIZE - 2)]:
        rows[y][x] = IRON
    return rows


def gem(rows, cx, cy, bright, mid, dark):
    """A four-pixel-wide cut gem centred at (cx, cy), lit from the top left."""
    rows[cy - 1][cx] = bright
    rows[cy - 1][cx + 1] = mid
    rows[cy][cx - 1] = bright
    rows[cy][cx] = bright
    rows[cy][cx + 1] = mid
    rows[cy][cx + 2] = mid
    rows[cy + 1][cx - 1] = mid
    rows[cy + 1][cx] = mid
    rows[cy + 1][cx + 1] = dark
    rows[cy + 1][cx + 2] = dark
    rows[cy + 2][cx] = dark
    rows[cy + 2][cx + 1] = dark
    return rows


def side_texture(bright, mid, dark):
    """Crate side: iron frame, a lid seam near the top, and a gem lock."""
    rows = iron_frame(plank_field())
    # Lid seam — tells you which way up the crate is and where it opens.
    for x in range(1, SIZE - 1):
        rows[4][x] = IRON_DARK
        rows[5][x] = IRON
    # Lock plate behind the gem, so the gem does not float on bare wood.
    for y in range(7, 12):
        for x in range(6, 10):
            rows[y][x] = IRON_DARK if y in (7, 11) or x in (6, 9) else IRON
    return gem(rows, 7, 9, bright, mid, dark)


def top_texture(bright, mid, dark):
    """Crate lid: iron cross-bracing with a tier gem at the crossing."""
    rows = iron_frame(plank_field())
    for i in range(1, SIZE - 1):
        rows[7][i] = IRON
        rows[8][i] = IRON_DARK
        rows[i][7] = IRON
        rows[i][8] = IRON_DARK
    return gem(rows, 7, 7, bright, mid, dark)


def glint_texture():
    """A white four-point sparkle on transparent, tinted per tier at render time.

    One texture rather than four: the renderer already knows the tier and can
    call GlStateManager.color, so shipping four near-identical white-on-alpha
    PNGs would be four things to keep in sync for no gain.
    """
    rows = [[(255, 255, 255, 0) for _ in range(SIZE)] for _ in range(SIZE)]
    cx = cy = 7
    for i in range(SIZE):
        # Vertical and horizontal spikes, fading toward the tips.
        fade = max(0, 255 - abs(i - cx) * 34)
        if fade:
            rows[cy][i] = (255, 255, 255, fade)
            rows[cy + 1][i] = (255, 255, 255, fade // 2)
            rows[i][cx] = (255, 255, 255, fade)
            rows[i][cx + 1] = (255, 255, 255, fade // 2)
    # Solid core.
    for y in range(cy - 1, cy + 3):
        for x in range(cx - 1, cx + 3):
            rows[y][x] = (255, 255, 255, 255)
    return rows


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for tier, (bright, mid, dark) in TIERS.items():
        write_png(os.path.join(OUT_DIR, "loot_box_%s_side.png" % tier),
                  side_texture(bright, mid, dark))
        write_png(os.path.join(OUT_DIR, "loot_box_%s_top.png" % tier),
                  top_texture(bright, mid, dark))
        print("wrote loot_box_%s_{side,top}.png" % tier)
    write_png(os.path.join(OUT_DIR, "glint.png"), glint_texture(), alpha=True)
    print("wrote glint.png")


if __name__ == "__main__":
    main()
