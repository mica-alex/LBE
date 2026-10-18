#!/usr/bin/env python3
"""Generate the crafting recipes for the casino decor, and the advancement that unlocks each one in
the recipe book.

Decor only. The machines, the vendor, the leaderboard and the progressive sign are not craftable:
a casino belongs to whoever runs the server (no player-owned machines; see docs/design/CASINO.md),
and loot boxes are never made from raw materials (docs/design/LOOT_BOXES.md). Everything here is
furniture, priced in common materials so a builder can fill a room with it.

Ingredients use the ore dictionary where Forge has a name for the material, so a pack's own gold
or glass works; wool and concrete colours have no ore names in 1.12, so those are item and data.

Each recipe's advancement unlocks it once the player holds its key ingredient, the way the loot box
upgrades unlock on holding a box.

Writes src/main/resources/assets/lbe/recipes/<name>.json and
src/main/resources/assets/lbe/advancements/recipes/lbe/<name>.json.

Usage (from the repo root):
    python tools/gen_decor_recipes.py
"""

import json
import os

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "lbe")
GROUP = "lbe:casino_decor"


def ore(name):
    return {"type": "forge:ore_dict", "ore": name}


def item(name, data=None):
    i = {"item": name}
    if data is not None:
        i["data"] = data
    return i


RED_WOOL = item("minecraft:wool", 14)
BLUE_WOOL = item("minecraft:wool", 11)
BLACK_WOOL = item("minecraft:wool", 15)
BLACK_CONCRETE = item("minecraft:concrete", 15)
GOLD = ore("ingotGold")
NUGGET = ore("nuggetGold")
PLANKS = ore("plankWood")
PANE = ore("paneGlass")
GLOWSTONE = ore("dustGlowstone")


# name -> (count, pattern or None for shapeless, key or ingredient list, unlocking item)
RECIPES = {
    "casino_carpet_block": (8, ["WWW", "WGW", "WWW"], {"W": RED_WOOL, "G": GOLD},
                            item("minecraft:wool", 14)),
    "casino_carpet_royal_block": (8, ["WWW", "WGW", "WWW"], {"W": BLUE_WOOL, "G": GOLD},
                                  item("minecraft:wool", 11)),
    # As vanilla carpet comes from wool: two blocks side by side, three carpets.
    "casino_carpet": (3, ["BB"], {"B": item("lbe:casino_carpet_block")},
                      item("lbe:casino_carpet_block")),
    "casino_carpet_royal": (3, ["BB"], {"B": item("lbe:casino_carpet_royal_block")},
                            item("lbe:casino_carpet_royal_block")),
    "bar_stool": (2, ["W", "I", "I"], {"W": RED_WOOL, "I": ore("ingotIron")},
                  item("minecraft:iron_ingot")),
    "velvet_rope_post": (2, ["W", "G", "G"], {"W": RED_WOOL, "G": GOLD},
                         item("minecraft:gold_ingot")),
    "neon_sign": (1, ["PPP", "DDD", "PPP"], {"P": PANE, "D": GLOWSTONE},
                  item("minecraft:glowstone_dust")),
    "casino_plaque": (2, ["NNN", "NWN", "NNN"], {"N": NUGGET, "W": BLACK_WOOL},
                      item("minecraft:gold_nugget")),
    # A suit on an armour stand.
    "casino_bouncer": (1, ["W", "A", "W"], {"W": BLACK_WOOL, "A": item("minecraft:armor_stand")},
                       item("minecraft:armor_stand")),
    "cashier_cage": (3, ["NNN", "QQQ", "PPP"], {"N": NUGGET, "Q": ore("gemQuartz"), "P": PLANKS},
                     item("minecraft:quartz")),
    "cashier_grille": (4, ["NNN", "NNN"], {"N": NUGGET}, item("lbe:cashier_cage")),
    "bar_counter": (4, ["CCC", "PPP", "PPP"], {"C": BLACK_CONCRETE, "P": PLANKS},
                    item("minecraft:concrete", 15)),
    "bar_shelf": (2, ["PPP", "BGB", "PPP"],
                  {"P": PLANKS, "B": item("minecraft:glass_bottle"), "G": PANE},
                  item("minecraft:glass_bottle")),
    "chip_stacks": (2, None, [ore("dyeRed"), ore("dyeGreen"), ore("dyeBlack"), ore("dyeBlue"),
                              NUGGET],
                    item("minecraft:gold_nugget")),
    "card_shoe": (1, None, [item("minecraft:paper"), item("minecraft:paper"),
                            item("minecraft:paper"), BLACK_WOOL],
                  item("minecraft:paper")),
    "casino_chandelier": (1, [" G ", "GLG", " P "],
                          {"G": GOLD, "L": item("minecraft:glowstone"), "P": PANE},
                          item("minecraft:glowstone")),
    "light_pillar": (2, ["NDN", "NCN", "NDN"], {"N": NUGGET, "D": GLOWSTONE, "C": BLACK_CONCRETE},
                     item("minecraft:glowstone_dust")),
}


def recipe(name, count, pattern, ingredients):
    result = {"item": "lbe:" + name, "count": count}
    if pattern is None:
        return {"type": "minecraft:crafting_shapeless", "group": GROUP,
                "ingredients": ingredients, "result": result}
    return {"type": "minecraft:crafting_shaped", "group": GROUP, "pattern": pattern,
            "key": ingredients, "result": result}


def advancement(name, unlock):
    return {
        "parent": "minecraft:recipes/root",
        "rewards": {"recipes": ["lbe:" + name]},
        "criteria": {
            "has_ingredient": {"trigger": "minecraft:inventory_changed",
                               "conditions": {"items": [unlock]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                               "conditions": {"recipe": "lbe:" + name}},
        },
        "requirements": [["has_ingredient", "has_the_recipe"]],
    }


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, indent=2, ensure_ascii=True) + "\n")


def main():
    for name, (count, pattern, ingredients, unlock) in RECIPES.items():
        write(os.path.join(ASSETS, "recipes", name + ".json"),
              recipe(name, count, pattern, ingredients))
        write(os.path.join(ASSETS, "advancements", "recipes", "lbe", name + ".json"),
              advancement(name, unlock))
    print("wrote %d decor recipes" % len(RECIPES))


if __name__ == "__main__":
    main()
