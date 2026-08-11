#!/usr/bin/env python3
"""Generate the cabinet textures for every casino machine except the slots.

The slot machine has its own generator (``gen_slot_textures.py``) because its
face carries a reel window and a marquee that nothing else needs. Everything
here is the same cabinet in a different colour with a different motif on the
front, which is exactly the sort of thing worth generating rather than drawing
six times.

Writes 16x16 PNGs into ``src/main/resources/assets/lbe/textures/blocks``:

    <machine>_front / _side / _top / _bottom          all machines
    <machine>_upper_front / _upper_side               tall cabinets only

Pure standard library (zlib + struct); the repo has no Pillow dependency and is
not getting one.

Usage (from the repo root):
    python tools/gen_casino_textures.py
"""

import os
import struct
import zlib

OUT_DIR = os.path.join("src", "main", "resources", "assets", "lbe", "textures", "blocks")

GOLD_LIT = (0xFF, 0xD5, 0x54)
GOLD = (0xF0, 0xA8, 0x1E)
GOLD_DARK = (0xB0, 0x70, 0x08)
WHITE = (0xF2, 0xF2, 0xF6)
BLACK = (0x0A, 0x08, 0x10)
FELT_LINE = (0xD8, 0xD8, 0xE4)
RED = (0xD8, 0x3A, 0x3A)

# name -> (tall?, body, body_dark, motif)
# The motif is what goes on the front face, drawn by MOTIFS below.
MACHINES = {
    "coin_flip_table":  (False, (0x2A, 0x50, 0x38), (0x1B, 0x38, 0x26), "coin"),
    "war_table":        (False, (0x50, 0x28, 0x2A), (0x38, 0x1B, 0x1D), "cards"),
    "high_low_machine": (True,  (0x2E, 0x28, 0x50), (0x1D, 0x1A, 0x38), "arrows"),
    "roulette_table":   (False, (0x24, 0x3E, 0x50), (0x18, 0x2A, 0x38), "wheel"),
    "plinko_machine":   (True,  (0x50, 0x40, 0x24), (0x38, 0x2C, 0x18), "pegs"),
    "keno_machine":     (True,  (0x40, 0x24, 0x50), (0x2C, 0x18, 0x38), "grid"),
    "baccarat_table":   (False, (0x1E, 0x44, 0x2E), (0x14, 0x2E, 0x1F), "baccarat"),
}


def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBB", *pixel) for pixel in row) for row in pixels
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def grid(fill):
    return [[fill for _ in range(16)] for _ in range(16)]


def box(rows, x0, y0, x1, y1, colour):
    for y in range(max(0, y0), min(16, y1 + 1)):
        for x in range(max(0, x0), min(16, x1 + 1)):
            rows[y][x] = colour


def frame(rows, colour):
    for i in range(16):
        rows[0][i] = rows[15][i] = rows[i][0] = rows[i][15] = colour


def lighten(colour, amount=0x14):
    return tuple(min(0xFF, c + amount) for c in colour)


def body(base, dark):
    rows = grid(base)
    box(rows, 0, 0, 15, 2, lighten(base))
    box(rows, 0, 13, 15, 15, dark)
    frame(rows, dark)
    return rows


# --- motifs -----------------------------------------------------------------
# Each draws onto the middle of a front face. Chunky and high contrast: these
# are read across a room, in a dim casino, by somebody deciding what to play.


def motif_coin(rows):
    for y in range(5, 12):
        for x in range(4, 12):
            if (x - 7.5) ** 2 + (y - 8) ** 2 <= 11:
                rows[y][x] = GOLD
            elif (x - 7.5) ** 2 + (y - 8) ** 2 <= 16:
                rows[y][x] = GOLD_DARK
    rows[8][7] = rows[8][8] = GOLD_LIT


def motif_cards(rows):
    box(rows, 3, 5, 7, 12, WHITE)
    box(rows, 3, 5, 7, 5, (0xC0, 0xC0, 0xC8))
    rows[8][5] = RED
    box(rows, 8, 4, 12, 11, (0xE8, 0xE8, 0xF0))
    rows[7][10] = BLACK


def motif_arrows(rows):
    # Up arrow on the left, down on the right: the whole game in one glyph.
    for i in range(4):
        box(rows, 4 - i, 8 + i, 4 + i, 8 + i, GOLD_LIT)
    box(rows, 3, 4, 5, 8, GOLD)
    for i in range(4):
        box(rows, 11 - i, 7 - i, 11 + i, 7 - i, WHITE)
    box(rows, 10, 7, 12, 11, (0xC0, 0xC0, 0xD0))


def motif_wheel(rows):
    for y in range(3, 13):
        for x in range(3, 13):
            d = (x - 7.5) ** 2 + (y - 7.5) ** 2
            if d <= 6:
                rows[y][x] = GOLD_DARK
            elif d <= 20:
                rows[y][x] = RED if (x + y) % 2 == 0 else BLACK
            elif d <= 26:
                rows[y][x] = GOLD


def motif_pegs(rows):
    for row in range(4):
        y = 4 + row * 3
        offset = 0 if row % 2 == 0 else 2
        for x in range(3 + offset, 14, 4):
            rows[y][x] = WHITE
            if y + 1 < 16:
                rows[y + 1][x] = (0x80, 0x80, 0x90)


def motif_grid(rows):
    for row in range(4):
        for column in range(4):
            x = 3 + column * 3
            y = 4 + row * 3
            box(rows, x, y, x + 1, y + 1,
                GOLD if (row + column) % 3 == 0 else FELT_LINE)


def motif_baccarat(rows):
    """Two hands facing each other across the felt, which is the whole game."""
    box(rows, 2, 4, 5, 9, WHITE)
    box(rows, 4, 5, 7, 10, (0xE0, 0xE0, 0xEA))
    box(rows, 10, 4, 13, 9, WHITE)
    box(rows, 8, 5, 11, 10, (0xE0, 0xE0, 0xEA))
    rows[6][3] = RED
    rows[6][12] = BLACK
    box(rows, 7, 12, 8, 13, GOLD)


MOTIFS = {
    "baccarat": motif_baccarat,
    "coin": motif_coin,
    "cards": motif_cards,
    "arrows": motif_arrows,
    "wheel": motif_wheel,
    "pegs": motif_pegs,
    "grid": motif_grid,
}


def table_top(base, dark, motif):
    """A table's top face is the one players look at, so the motif goes there too."""
    rows = grid(base)
    frame(rows, dark)
    box(rows, 2, 2, 13, 13, lighten(base, 0x0A))
    MOTIFS[motif](rows)
    return rows


def main():
    written = 0
    for name, (tall, base, dark, motif) in MACHINES.items():
        front = body(base, dark)
        box(front, 1, 1, 14, 2, GOLD)
        box(front, 1, 1, 14, 1, GOLD_LIT)
        if not tall:
            MOTIFS[motif](front)

        side = body(base, dark)
        box(side, 1, 1, 14, 2, GOLD)
        box(side, 1, 1, 14, 1, GOLD_LIT)
        box(side, 2, 6, 13, 6, dark)
        box(side, 2, 10, 13, 10, dark)

        top = table_top(base, dark, motif) if not tall else body(dark, BLACK)
        bottom = grid(dark)
        frame(bottom, BLACK)

        faces = {
            name + "_front": front,
            name + "_side": side,
            name + "_top": top,
            name + "_bottom": bottom,
        }
        if tall:
            upper_front = body(base, dark)
            box(upper_front, 1, 1, 14, 3, GOLD)
            box(upper_front, 1, 1, 14, 1, GOLD_LIT)
            for x in (4, 7, 10):
                upper_front[2][x] = WHITE
            box(upper_front, 1, 5, 14, 13, (0x2A, 0x2A, 0x34))
            box(upper_front, 2, 6, 13, 12, BLACK)
            MOTIFS[motif](upper_front)
            faces[name + "_upper_front"] = upper_front

            upper_side = body(base, dark)
            box(upper_side, 1, 1, 14, 3, GOLD)
            box(upper_side, 1, 1, 14, 1, GOLD_LIT)
            box(upper_side, 2, 6, 13, 12, dark)
            faces[name + "_upper_side"] = upper_side

        for face_name, rows in faces.items():
            write_png(os.path.join(OUT_DIR, face_name + ".png"), rows)
            written += 1
        print("wrote", name, "(%s)" % ("tall" if tall else "table"))
    print("%d textures written" % written)


if __name__ == "__main__":
    main()
