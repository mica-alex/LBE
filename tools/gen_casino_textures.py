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
    "video_poker_machine": (True, (0x28, 0x30, 0x4E), (0x18, 0x1E, 0x34), "poker"),
    "mines_machine":    (True,  (0x4A, 0x2A, 0x22), (0x30, 0x1A, 0x14), "mine"),
    "blackjack_table":  (False, (0x16, 0x56, 0x3C), (0x0E, 0x38, 0x27), "cards"),
    "big_wheel":        (True,  (0x5A, 0x1A, 0x22), (0x3C, 0x10, 0x16), "wheel"),
    "pig_race":         (True,  (0x2A, 0x4A, 0x1E), (0x1A, 0x32, 0x12), "arrows"),
    "craps_table":      (False, (0x1A, 0x4E, 0x5A), (0x10, 0x34, 0x3C), "dice"),
}


def write_png(path, pixels):
    """Writes RGB, or RGBA when the pixels carry an alpha (an item icon needs to be see-through
    round its edges; a block face never does)."""
    height = len(pixels)
    width = len(pixels[0])
    channels = len(pixels[0][0])
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("B" * channels, *pixel) for pixel in row)
        for row in pixels
    )

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6 if channels == 4 else 2,
                                     0, 0, 0))
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


def motif_poker(rows):
    """Five cards fanned, which is the game in one glyph."""
    for i in range(5):
        x = 2 + i * 3
        box(rows, x, 5 + (i % 2), x + 1, 11 + (i % 2), WHITE)
        rows[6 + (i % 2)][x] = RED if i % 2 == 0 else BLACK


def motif_mine(rows):
    """A tile grid with one very obvious mine in it."""
    for row in range(3):
        for column in range(3):
            x = 3 + column * 4
            y = 4 + row * 3
            box(rows, x, y, x + 2, y + 1, FELT_LINE if (row + column) % 2 else WHITE)
    box(rows, 7, 7, 9, 8, BLACK)
    rows[7][8] = RED


def motif_dice(rows):
    """Two dice, a five and a two, side by side."""
    box(rows, 3, 5, 7, 9, WHITE)
    box(rows, 9, 6, 13, 10, WHITE)
    for x, y in ((4, 6), (6, 6), (5, 7), (4, 8), (6, 8), (10, 7), (12, 9)):
        rows[y][x] = BLACK


MOTIFS = {
    "dice": motif_dice,
    "mine": motif_mine,
    "poker": motif_poker,
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


def carpet(base, accent, gem):
    """A diamond lattice that tiles: each tile's diamond touches its edges at their midpoints,
    so neighbouring tiles join into one continuous pattern, with a gem at every centre."""
    rows = grid(base)
    for y in range(16):
        for x in range(16):
            d = abs(x - 7.5) + abs(y - 7.5)
            if 6.5 <= d <= 7.5:
                rows[y][x] = accent
            elif d <= 1.0:
                rows[y][x] = gem
            elif 3.0 <= d <= 3.5:
                rows[y][x] = lighten(base, 0x18)
    return rows


def shaded(light, dark):
    """A horizontal gradient, for metal and rope that should read as round rather than flat."""
    rows = grid(light)
    for y in range(16):
        for x in range(16):
            t = abs(x - 7.5) / 7.5
            rows[y][x] = tuple(int(light[i] + (dark[i] - light[i]) * t) for i in range(3))
    return rows


def decor():
    """The decor set: carpets, the stool, the rope post, the neon sign's backing."""
    faces = {
        "casino_carpet": carpet((0x6A, 0x0C, 0x16), GOLD, GOLD_LIT),
        "casino_carpet_royal": carpet((0x12, 0x1E, 0x5A), GOLD, (0xE0, 0x40, 0x50)),
        "bar_stool_metal": shaded((0xE4, 0xE6, 0xEC), (0x70, 0x74, 0x80)),
        "bar_stool_cushion": carpet((0xA0, 0x10, 0x20), (0x80, 0x0A, 0x18), (0xC0, 0x30, 0x40)),
        "velvet_rope_brass": shaded(GOLD_LIT, GOLD_DARK),
        "velvet_rope": shaded((0xC0, 0x18, 0x28), (0x60, 0x06, 0x10)),
    }
    backing = grid((0x0C, 0x0A, 0x10))
    frame(backing, (0x24, 0x20, 0x2C))
    faces["neon_sign_front"] = backing
    faces["neon_sign_side"] = grid((0x24, 0x20, 0x2C))
    for name, rows in faces.items():
        write_png(os.path.join(OUT_DIR, name + ".png"), rows)
    print("wrote decor (%d)" % len(faces))
    return len(faces) + floor()


WOOD = (0x4A, 0x22, 0x14)
WOOD_DARK = (0x2E, 0x14, 0x0A)


def panel(base, dark):
    """A raised wooden panel: a dark frame, a lighter inset, the way bar fronts are built."""
    rows = grid(dark)
    box(rows, 2, 2, 13, 13, base)
    box(rows, 2, 2, 13, 2, lighten(base, 0x18))
    box(rows, 2, 13, 13, 13, lighten(dark, 0x08))
    return rows


def marble(base, vein):
    rows = grid(base)
    for i in range(16):
        rows[i][(i * 5 // 4 + 3) % 16] = vein
        rows[(i + 9) % 16][(i // 2 + 11) % 16] = vein
    return rows


def chips(colour):
    """A stack of chips seen side-on: one chip per row, alternate rows a shade darker, white edge
    marks staggered between rows. Mapped by position, so any height of cuboid shows whole chips."""
    dark = tuple(c * 3 // 4 for c in colour)
    rows = grid(colour)
    for y in range(16):
        for x in range(16):
            rows[y][x] = colour if y % 2 == 0 else dark
            if (x + (y % 2) * 2) % 4 == 0:
                rows[y][x] = WHITE
    return rows


def bottle(glass, label):
    rows = shaded(lighten(glass, 0x30), glass)
    box(rows, 0, 8, 15, 12, label)
    return rows


def bulbs(base, lit):
    """Rows of marquee bulbs on a dark body, offset row to row the way a chase light is wired."""
    rows = grid(base)
    frame(rows, GOLD_DARK)
    for y in range(2, 15, 3):
        for x in range(2 + (y // 3) % 2 * 2, 15, 4):
            box(rows, x - 1, y - 1, x, y, lit)
            rows[y - 1][x - 1] = WHITE
    return rows


def floor():
    """The casino floor's furniture: the plaque, the cashier's cage, table-top chips and the card
    shoe, the bar, the lights. Then the bouncer, who is not a block texture at all."""
    plaque = grid((0x10, 0x0E, 0x14))
    frame(plaque, GOLD)
    faces = {
        "casino_plaque_front": plaque,
        "casino_plaque_side": shaded(GOLD_LIT, GOLD_DARK),
        "cashier_cage_wood": panel(WOOD, WOOD_DARK),
        "cashier_cage_marble": marble((0xE8, 0xE4, 0xDC), (0xB8, 0xB0, 0xA8)),
        "chip_red": chips((0xC8, 0x18, 0x20)),
        "chip_green": chips((0x18, 0x8A, 0x3A)),
        "chip_black": chips((0x20, 0x1C, 0x24)),
        "chip_blue": chips((0x1C, 0x40, 0xB0)),
        "card_shoe": body((0x24, 0x22, 0x2A), (0x10, 0x0E, 0x14)),
        "card_back": grid((0xB0, 0x14, 0x1C)),
        "bar_counter_front": panel((0x5A, 0x28, 0x18), (0x34, 0x16, 0x0C)),
        "bar_counter_top": marble((0x18, 0x16, 0x1C), (0x40, 0x3A, 0x44)),
        "bar_shelf_back": grid((0x7A, 0x84, 0x90)),
        "bottle_green": bottle((0x1C, 0x60, 0x24), (0xE8, 0xDC, 0xB0)),
        "bottle_amber": bottle((0x9A, 0x50, 0x0C), (0x20, 0x18, 0x14)),
        "bottle_clear": bottle((0xA0, 0xC8, 0xD8), (0xC0, 0x20, 0x28)),
        "bottle_red": bottle((0x70, 0x0C, 0x18), GOLD),
        "chandelier_crystal": shaded((0xF4, 0xFA, 0xFF), (0xA8, 0xC8, 0xE0)),
        "chandelier_bulb": grid((0xFF, 0xF0, 0xC0)),
        "light_pillar_side": bulbs((0x1A, 0x10, 0x22), GOLD_LIT),
        "light_pillar_top": body(GOLD, GOLD_DARK),
    }
    frame(faces["card_back"], WHITE)
    # A mirror behind the bottles, as every back bar has: pale, with a diagonal glint.
    for i in range(16):
        faces["bar_shelf_back"][i][15 - i] = (0xB8, 0xC4, 0xD0)
        if i < 15:
            faces["bar_shelf_back"][i + 1][15 - i] = (0xA4, 0xB0, 0xBC)
    for name, rows in faces.items():
        write_png(os.path.join(OUT_DIR, name + ".png"), rows)
    write_png(os.path.join(OUT_DIR, "..", "entity", "casino_bouncer.png"), bouncer_skin())
    write_png(os.path.join(OUT_DIR, "..", "items", "casino_bouncer.png"), bouncer_icon())
    print("wrote floor (%d) and the bouncer" % len(faces))
    return len(faces) + 2


SKIN = (0x9A, 0x6A, 0x4A)
SKIN_DARK = (0x7A, 0x50, 0x36)
SUIT = (0x1A, 0x1A, 0x20)
SUIT_DARK = (0x10, 0x10, 0x14)
SHADES = (0x06, 0x06, 0x08)


def bouncer_skin():
    """A 64x32 skin in ModelBiped's layout: black suit, white shirt and black tie, dark glasses, a
    buzz cut. Only the regions ModelBiped samples are painted; the hat layer stays unused."""
    rows = [[SUIT_DARK for _ in range(64)] for _ in range(32)]

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1):
            for x in range(x0, x1):
                rows[y][x] = colour

    # Head: 8x8x8 at (0, 0). Top and the upper sides are cropped hair; the front has the glasses.
    fill(0, 0, 32, 16, SKIN)
    fill(8, 0, 16, 8, (0x1E, 0x16, 0x12))
    fill(0, 8, 32, 10, (0x1E, 0x16, 0x12))
    fill(24, 8, 32, 13, (0x1E, 0x16, 0x12))
    fill(9, 11, 15, 13, SHADES)
    rows[11][10] = rows[11][13] = (0x50, 0x58, 0x70)
    fill(10, 14, 14, 15, SKIN_DARK)
    # Body: 8x12x4 at (16, 16). Jacket all round; a shirt and tie down the front.
    fill(16, 16, 40, 32, SUIT)
    fill(23, 20, 25, 26, WHITE)
    fill(23, 21, 25, 30, (0x08, 0x08, 0x0A))
    rows[20][22] = rows[20][25] = WHITE
    fill(16, 30, 40, 32, (0x0C, 0x0C, 0x10))
    # Arm: 4x12x4 at (40, 16). Sleeve, a white cuff, a hand.
    fill(40, 16, 56, 32, SUIT)
    fill(40, 28, 56, 29, WHITE)
    fill(40, 29, 56, 32, SKIN)
    fill(48, 16, 52, 20, SKIN)
    # Leg: 4x12x4 at (0, 16). Trousers and polished shoes.
    fill(0, 16, 16, 32, SUIT_DARK)
    fill(0, 29, 16, 32, (0x04, 0x04, 0x06))
    fill(8, 16, 12, 20, (0x04, 0x04, 0x06))
    return rows


def bouncer_icon():
    """The bouncer's inventory icon: head and shoulders, on transparency."""
    clear = (0, 0, 0, 0)
    rows = [[clear for _ in range(16)] for _ in range(16)]

    def fill(x0, y0, x1, y1, colour):
        for y in range(y0, y1):
            for x in range(x0, x1):
                rows[y][x] = colour + (0xFF,)

    fill(5, 1, 11, 7, SKIN)
    fill(5, 1, 11, 2, (0x1E, 0x16, 0x12))
    fill(5, 3, 11, 4, SHADES)
    fill(7, 5, 9, 6, SKIN_DARK)
    fill(2, 8, 14, 16, SUIT)
    fill(7, 8, 9, 12, WHITE)
    fill(7, 9, 9, 15, (0x08, 0x08, 0x0A))
    fill(6, 7, 10, 8, SKIN_DARK)
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

    # The leaderboard: a dark board in a gold frame. Its lines are drawn by the renderer, so the
    # face is deliberately plain.
    board = grid((0x16, 0x12, 0x20))
    frame(board, GOLD)
    box(board, 1, 1, 14, 1, GOLD_LIT)
    board_side = grid(GOLD)
    box(board_side, 0, 0, 15, 0, GOLD_LIT)
    write_png(os.path.join(OUT_DIR, "casino_leaderboard_front.png"), board)
    write_png(os.path.join(OUT_DIR, "casino_leaderboard_side.png"), board_side)
    written += 2
    print("wrote casino_leaderboard")

    # The progressive sign: the same frame round a deep red board, so a bank of slots reads as
    # having its own jackpot at a glance.
    sign = grid((0x3A, 0x08, 0x10))
    frame(sign, GOLD)
    box(sign, 1, 1, 14, 1, GOLD_LIT)
    box(sign, 1, 14, 14, 14, GOLD_DARK)
    write_png(os.path.join(OUT_DIR, "progressive_sign_front.png"), sign)
    write_png(os.path.join(OUT_DIR, "progressive_sign_side.png"), board_side)
    written += 2
    print("wrote progressive_sign")

    # The loot box vendor: a teal cabinet with gold bands. Its window is a separate texture for
    # the recess's back wall, lit so the box turning in front of it stands out.
    vendor = body((0x1E, 0x5A, 0x5E), (0x10, 0x34, 0x38))
    box(vendor, 1, 1, 14, 2, GOLD)
    box(vendor, 1, 1, 14, 1, GOLD_LIT)
    box(vendor, 1, 13, 14, 13, GOLD)
    box(vendor, 6, 14, 9, 14, BLACK)          # the coin slot
    vendor_side = body((0x1E, 0x5A, 0x5E), (0x10, 0x34, 0x38))
    box(vendor_side, 1, 1, 14, 2, GOLD)
    vendor_top = body((0x10, 0x34, 0x38), BLACK)
    window = grid((0x0C, 0x1C, 0x30))
    frame(window, (0x2A, 0x4C, 0x70))
    for name, rows in (("loot_box_vendor_front", vendor), ("loot_box_vendor_side", vendor_side),
                       ("loot_box_vendor_top", vendor_top), ("loot_box_vendor_window", window)):
        write_png(os.path.join(OUT_DIR, name + ".png"), rows)
        written += 1
    print("wrote loot_box_vendor")
    written += decor()
    print("%d textures written" % written)


if __name__ == "__main__":
    main()
