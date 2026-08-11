#!/usr/bin/env python3
"""Generate the slot machine's block textures and the GUI symbol sheet.

Writes into ``src/main/resources/assets/lbe/textures``:

    blocks/slot_machine_lower_front.png   coin slot, payout tray, lever mount
    blocks/slot_machine_lower_side.png    plain cabinet flank
    blocks/slot_machine_upper_front.png   the reel window and the marquee
    blocks/slot_machine_upper_side.png    flank with the marquee band carried round
    blocks/slot_machine_top.png           the cabinet lid
    blocks/slot_machine_bottom.png        the cabinet foot
    gui/slot_symbols.png                  six 32x32 symbols, stacked in SlotSymbol order

Generated rather than drawn for the same reason as the loot boxes: the palette
lives in one dict, so restyling the whole cabinet is a one-line edit instead of
six files opened in an editor. The GUI sheet especially — six symbols hand-drawn
at 32x32 would be six chances to get the shared outline slightly wrong.

Pure standard library (zlib + struct); the repo has no Pillow dependency and is
not getting one.

Usage (from the repo root):
    python tools/gen_slot_textures.py
"""

import os
import struct
import zlib

BLOCK_DIR = os.path.join("src", "main", "resources", "assets", "lbe", "textures", "blocks")
GUI_DIR = os.path.join("src", "main", "resources", "assets", "lbe", "textures", "gui")

# --- palette ----------------------------------------------------------------
# A dark cabinet so the gold trim and the lit reel window carry the whole read.
# These are picked to sit beside the loot boxes without matching them: the boxes
# are warm wood, the casino is lacquered metal.
BODY_LIT = (0x3A, 0x2E, 0x4A)
BODY = (0x2A, 0x20, 0x38)
BODY_DARK = (0x1B, 0x14, 0x26)
BODY_SHADOW = (0x12, 0x0D, 0x1A)

GOLD_LIT = (0xFF, 0xD5, 0x54)
GOLD = (0xF0, 0xA8, 0x1E)
GOLD_DARK = (0xB0, 0x70, 0x08)

GLASS_LIT = (0x2E, 0x6E, 0x8C)
GLASS = (0x18, 0x42, 0x5A)
GLASS_DARK = (0x0C, 0x24, 0x32)

RED = (0xD8, 0x3A, 0x3A)
RED_DARK = (0x8C, 0x1E, 0x1E)
WHITE = (0xF2, 0xF2, 0xF6)
STEEL = (0x9A, 0x9A, 0xA2)
STEEL_DARK = (0x5A, 0x5A, 0x64)
BLACK = (0x08, 0x06, 0x0C)


def write_png(path, pixels, alpha=False):
    """Write an RGB (or RGBA) PNG from a list of rows of tuples."""
    height = len(pixels)
    width = len(pixels[0])
    depth = 6 if alpha else 2
    fmt = "BBBB" if alpha else "BBB"
    raw = b"".join(
        b"\x00" + b"".join(struct.pack(fmt, *pixel) for pixel in row) for row in pixels
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", width, height, 8, depth, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def grid(size, fill):
    return [[fill for _ in range(size)] for _ in range(size)]


def frame(rows, colour, corner=None):
    """One-pixel border, with optional distinct corners."""
    size = len(rows)
    for i in range(size):
        rows[0][i] = colour
        rows[size - 1][i] = colour
        rows[i][0] = colour
        rows[i][size - 1] = colour
    if corner:
        for y, x in ((0, 0), (0, size - 1), (size - 1, 0), (size - 1, size - 1)):
            rows[y][x] = corner


def box(rows, x0, y0, x1, y1, colour):
    """Filled rectangle, inclusive of both corners, clipped to the texture."""
    for y in range(max(0, y0), min(len(rows), y1 + 1)):
        for x in range(max(0, x0), min(len(rows[0]), x1 + 1)):
            rows[y][x] = colour


# --- block textures ---------------------------------------------------------


def cabinet_base():
    """Shared cabinet body: lit at the top, shadowed at the bottom."""
    rows = grid(16, BODY)
    box(rows, 0, 0, 15, 2, BODY_LIT)
    box(rows, 0, 12, 15, 15, BODY_DARK)
    frame(rows, BODY_SHADOW)
    return rows


def lower_front():
    """The business end: coin slot, a lever mount, and the payout tray."""
    rows = cabinet_base()
    # Gold band under the reel window above, tying the two halves together.
    box(rows, 1, 1, 14, 2, GOLD)
    box(rows, 1, 1, 14, 1, GOLD_LIT)
    # Coin slot.
    box(rows, 6, 5, 9, 6, BLACK)
    box(rows, 6, 5, 9, 5, STEEL_DARK)
    # Lever mount on the right flank.
    box(rows, 13, 5, 14, 8, STEEL_DARK)
    box(rows, 13, 4, 14, 4, RED)
    # Payout tray, recessed.
    box(rows, 3, 10, 12, 13, BODY_SHADOW)
    box(rows, 4, 11, 11, 12, BLACK)
    box(rows, 4, 11, 11, 11, STEEL_DARK)
    return rows


def lower_side():
    """Plain flank, with the same gold band so a row of cabinets lines up."""
    rows = cabinet_base()
    box(rows, 1, 1, 14, 2, GOLD)
    box(rows, 1, 1, 14, 1, GOLD_LIT)
    box(rows, 2, 6, 13, 6, BODY_DARK)
    box(rows, 2, 10, 13, 10, BODY_DARK)
    return rows


def upper_front():
    """The reel window under a lit marquee — the face that has to read at distance."""
    rows = cabinet_base()
    # Marquee across the top.
    box(rows, 1, 1, 14, 3, GOLD)
    box(rows, 1, 1, 14, 1, GOLD_LIT)
    box(rows, 2, 2, 13, 2, GOLD_DARK)
    # Three bulbs on the marquee.
    for x in (4, 7, 10):
        rows[2][x] = WHITE
    # Glass reel window.
    box(rows, 1, 5, 14, 13, STEEL_DARK)
    box(rows, 2, 6, 13, 12, GLASS_DARK)
    # Three reels behind the glass, each with a suggestion of a symbol.
    for i, x in enumerate((3, 6, 10)):
        box(rows, x, 7, x + 2, 11, GLASS)
        box(rows, x, 7, x + 2, 7, GLASS_LIT)
    rows[9][4] = RED          # cherry
    rows[9][7] = GOLD_LIT     # bell
    rows[9][11] = WHITE       # seven
    return rows


def upper_side():
    """Flank of the head, marquee band carried round the corner."""
    rows = cabinet_base()
    box(rows, 1, 1, 14, 3, GOLD)
    box(rows, 1, 1, 14, 1, GOLD_LIT)
    box(rows, 2, 2, 13, 2, GOLD_DARK)
    box(rows, 2, 6, 13, 12, BODY_DARK)
    box(rows, 3, 7, 12, 11, BODY)
    return rows


def cabinet_top():
    rows = grid(16, BODY_DARK)
    frame(rows, BODY_SHADOW)
    box(rows, 3, 3, 12, 12, BODY)
    box(rows, 5, 5, 10, 10, GOLD_DARK)
    box(rows, 6, 6, 9, 9, GOLD)
    return rows


def cabinet_bottom():
    rows = grid(16, BODY_SHADOW)
    frame(rows, BLACK)
    box(rows, 2, 2, 13, 13, BODY_DARK)
    return rows


# --- GUI symbol sheet -------------------------------------------------------
# 32x32 per symbol, stacked vertically in SlotSymbol declaration order:
# cherry, lemon, bell, star, diamond, seven. The Java side indexes into this by
# SlotSymbol.index(), so the ORDER HERE IS LOAD-BEARING — reorder the enum and
# every symbol on screen becomes the wrong one.
TILE = 32
TRANSPARENT = (0, 0, 0, 0)


def tile():
    return [[TRANSPARENT for _ in range(TILE)] for _ in range(TILE)]


def disc(rows, cx, cy, radius, colour):
    for y in range(TILE):
        for x in range(TILE):
            if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius:
                rows[y][x] = colour


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def cherry_tile():
    rows = tile()
    disc(rows, 12, 21, 7, rgba(RED_DARK))
    disc(rows, 12, 20, 6, rgba(RED))
    disc(rows, 21, 23, 6, rgba(RED_DARK))
    disc(rows, 21, 22, 5, rgba(RED))
    disc(rows, 10, 18, 2, rgba(WHITE))
    for i in range(10):
        rows[10 + i][15 + i // 3] = rgba((0x3E, 0x8C, 0x2E))
        rows[10 + i][20 - i // 4] = rgba((0x3E, 0x8C, 0x2E))
    box_rgba(rows, 14, 6, 21, 9, rgba((0x4F, 0xC0, 0x45)))
    return rows


def box_rgba(rows, x0, y0, x1, y1, colour):
    for y in range(max(0, y0), min(TILE, y1 + 1)):
        for x in range(max(0, x0), min(TILE, x1 + 1)):
            rows[y][x] = colour


def lemon_tile():
    rows = tile()
    for y in range(TILE):
        for x in range(TILE):
            dx = (x - 16) / 11.0
            dy = (y - 17) / 8.0
            if dx * dx + dy * dy <= 1.0:
                rows[y][x] = rgba((0xE8, 0xD0, 0x3A))
            elif dx * dx + dy * dy <= 1.25:
                rows[y][x] = rgba((0xB8, 0xA0, 0x18))
    box_rgba(rows, 10, 12, 20, 13, rgba((0xF6, 0xEC, 0x9A)))
    box_rgba(rows, 4, 16, 6, 18, rgba((0xB8, 0xA0, 0x18)))
    box_rgba(rows, 25, 16, 27, 18, rgba((0xB8, 0xA0, 0x18)))
    return rows


def bell_tile():
    rows = tile()
    for y in range(8, 24):
        half = int(3 + (y - 8) * 0.62)
        box_rgba(rows, 16 - half, y, 16 + half, y, rgba(GOLD))
        rows[y][16 - half] = rgba(GOLD_DARK)
        rows[y][16 + half] = rgba(GOLD_DARK)
    box_rgba(rows, 14, 6, 18, 8, rgba(GOLD_DARK))
    box_rgba(rows, 6, 24, 26, 26, rgba(GOLD_LIT))
    box_rgba(rows, 6, 26, 26, 27, rgba(GOLD_DARK))
    disc(rows, 16, 28, 2, rgba(GOLD_DARK))
    box_rgba(rows, 11, 12, 12, 20, rgba(GOLD_LIT))
    return rows


def star_tile():
    rows = tile()
    points = [
        (16, 3), (19, 12), (28, 12), (21, 18), (24, 28),
        (16, 22), (8, 28), (11, 18), (4, 12), (13, 12),
    ]
    fill_polygon(rows, points, rgba((0xFF, 0xE0, 0x6A)))
    inner = [(16, 7), (18, 13), (24, 13), (19, 17), (21, 24),
             (16, 20), (11, 24), (13, 17), (8, 13), (14, 13)]
    fill_polygon(rows, inner, rgba((0xFF, 0xF3, 0xB8)))
    return rows


def fill_polygon(rows, points, colour):
    """Scanline fill. Crude but exact enough at 32x32, and dependency-free."""
    ys = [p[1] for p in points]
    for y in range(max(0, min(ys)), min(TILE, max(ys) + 1)):
        crossings = []
        for i in range(len(points)):
            x0, y0 = points[i]
            x1, y1 = points[(i + 1) % len(points)]
            if (y0 <= y < y1) or (y1 <= y < y0):
                t = (y - y0) / float(y1 - y0)
                crossings.append(x0 + t * (x1 - x0))
        crossings.sort()
        for i in range(0, len(crossings) - 1, 2):
            for x in range(int(round(crossings[i])), int(round(crossings[i + 1])) + 1):
                if 0 <= x < TILE:
                    rows[y][x] = colour


def diamond_tile():
    rows = tile()
    fill_polygon(rows, [(16, 4), (28, 15), (16, 29), (4, 15)], rgba((0x3F, 0xA9, 0xC9)))
    fill_polygon(rows, [(16, 7), (24, 15), (16, 25), (8, 15)], rgba((0x7A, 0xDC, 0xF0)))
    fill_polygon(rows, [(16, 7), (20, 14), (16, 18), (12, 14)], rgba((0xCF, 0xF4, 0xFF)))
    return rows


def seven_tile():
    rows = tile()
    # A blocky 7 with a slab serif, in casino red on gold.
    box_rgba(rows, 7, 5, 25, 10, rgba(RED_DARK))
    box_rgba(rows, 7, 5, 25, 9, rgba(RED))
    for i, y in enumerate(range(11, 29)):
        x = 22 - int(i * 0.62)
        box_rgba(rows, x - 3, y, x + 2, y, rgba(RED))
        rows[y][x + 2] = rgba(RED_DARK)
    box_rgba(rows, 8, 6, 24, 6, rgba((0xF0, 0x8A, 0x8A)))
    return rows


SYMBOL_TILES = [
    ("cherry", cherry_tile),
    ("lemon", lemon_tile),
    ("bell", bell_tile),
    ("star", star_tile),
    ("diamond", diamond_tile),
    ("seven", seven_tile),
]


def symbol_sheet():
    sheet = []
    for _, builder in SYMBOL_TILES:
        sheet.extend(builder())
    return sheet


def main():
    os.makedirs(BLOCK_DIR, exist_ok=True)
    blocks = {
        "slot_machine_lower_front": lower_front(),
        "slot_machine_lower_side": lower_side(),
        "slot_machine_upper_front": upper_front(),
        "slot_machine_upper_side": upper_side(),
        "slot_machine_top": cabinet_top(),
        "slot_machine_bottom": cabinet_bottom(),
    }
    for name, rows in blocks.items():
        write_png(os.path.join(BLOCK_DIR, name + ".png"), rows)
        print("wrote", name + ".png")

    write_png(os.path.join(GUI_DIR, "slot_symbols.png"), symbol_sheet(), alpha=True)
    print("wrote slot_symbols.png ({}x{}, {} symbols)".format(
        TILE, TILE * len(SYMBOL_TILES), len(SYMBOL_TILES)))


if __name__ == "__main__":
    main()
