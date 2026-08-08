# LBE — Loot Box Extravaganza

A Minecraft **1.12.2 Forge** mod that scatters four tiers of **loot box** through the world and fills
them with items drawn from **every mod you have installed** — with no per-mod loot table written by
anyone.

That last part is the mod. Everything else is scaffolding around it.

## The idea

A loot mod that claims to "support all other mods" has two options. It can ship hand-written loot
tables per mod — which works beautifully for the three someone got round to, is wrong for the other
three hundred, and is stale the moment any of them updates. Or it can work out rarity from what is
already in the registries.

Nobody wrote a rarity for their items. But they did write **recipes**, and a recipe is a statement
about what something costs. LBE reads every recipe in the pack once at startup and scores every item
from:

- what goes into it, recursively — and how much of it
- how many crafting steps separate it from raw materials
- how many *distinct* things its recipe wants (eight of one thing is a shape; eight different things
  is a milestone)
- the item's own properties — does it stack, does it have durability, what `EnumRarity` did its
  author declare

Scores are then cut into tiers **by percentile**, so "the top 2.5% of what is installed" is legendary
whether that is vanilla's few hundred items or a 12,000-item kitchen sink. Add a mod and the tiers
redistribute themselves.

And when the model gets something wrong — it will, on a big enough pack — the config has two
different ways to fix it, and they are not the same thing:

| | `rarity.materialScores` | `overrides.overrides` |
| --- | --- | --- |
| Sets | the item's **score** | the item's **final tier** |
| Propagates to things made from it | **yes** | no |
| For | a material whose cost the recipe walk cannot see (an ore, a mob drop) | one item that landed in the wrong tier |
| e.g. | `minecraft:diamond=8.0` — makes diamond *tools* valuable too | `minecraft:dragon_egg=legendary` |

> **Status: alpha — playable, and confirmed working in-game.** `./gradlew build` is green (Forge
> 1.12.2, 64 unit tests, jar produced); a real dedicated server boots with the catalogue built; and a
> dev client generates, renders and opens boxes with no missing models. On vanilla + TheOneProbe it
> scores 629 item variants in ~30 ms and lands them at 384 / 168 / 58 / 19 across the four tiers.
>
> Not yet done: a GitHub remote (the release workflow is wired and waiting), and richer structure
> integration beyond the opt-in chest loot.

## How it comes out on vanilla

Straight from a real server boot, no hand-tuning per item:

| Tier | Sample |
| --- | --- |
| Legendary | beacon, nether star, elytra, diamond & emerald blocks, all nine pieces of diamond gear, golden apple |
| Rare | enchanting table, iron pickaxe, leather chestplate, shulker box, end crystal, bow, cake |
| Uncommon | wooden pickaxe, beds, banners, raw diamond, minecart, bookshelf |
| Common | cobblestone, dirt, sticks, iron ingot, wool, planks |

It is not perfect and does not pretend to be — `carrot_on_a_stick` comes out legendary, because it is
unstackable, has durability and sits three steps from a log. That is exactly what the override table
is for.

## The boxes

Four blocks — `lbe:loot_box_common` through `lbe:loot_box_legendary` — generated per-chunk at
independent rates (roughly one common box every eight chunks; one legendary every thousand), on the
surface or in cave air pockets. Higher tiers glow.

- **Right-click to open.** Contents roll from a seed fixed when the box entered the world.
- **Break it to move it, not to open it.** The box drops itself with its seed intact, so you can
  carry a find home and open it where your storage is — and so you cannot break-and-replace a box to
  re-roll it until you like the answer.
- **Every box is a boxful; the tier decides the quality.** All four tiers give 4–5 items. What
  changes is how many are at the box's own tier — a legendary box guarantees 1–2 genuinely legendary
  items and fills the rest with mostly rare ones. A single "how many items" number would have forced
  the rarest box in the game to also be the emptiest.
- **It builds to the good stuff.** Filler is revealed first, features last, so a box peaks at the end
  rather than in its opening second.
- **Boxes combine upward, and cannot be crafted from scratch.** 4 common → 1 uncommon, 6 → 1 rare,
  9 → 1 legendary. Crafting a box out of raw materials would be an item generator whose profitability
  the mod has no way to predict; upgrades are a *sink*, so the total number of boxes only ever falls.
  Upgrading costs ~1.7× more than finding one, so it buys you control rather than efficiency.

## Commands

```
/lbe rarity [item]     tier + full term-by-term score breakdown for the held or named item
/lbe dump              write the whole scored catalogue to rarity-dumps/*.tsv
/lbe reload            re-read the config and rebuild the catalogue
/lbe give <tier> [n]   give yourself loot boxes, each separately seeded
/lbe place <tier>      place a box where you stand
/lbe loottables        force the configured loot tables to load, report which got a box pool
```

`/lbe rarity` is the one that matters. Tuning weights blind is miserable; it prints every term with
its value, including whether the dearest-ingredient floor applied, so you can see *which* term put an
item where it is before deciding whether you need a weight change, a declared material value, or just
an override. (Usually the third.)

## Building

Requires a **JDK 17–22** (`21` is the sweet spot — see `CLAUDE.md`). The mod itself targets Java 8 via
Jabel regardless of which JDK runs Gradle.

```sh
./gradlew build          # compile, run unit tests, produce the jar
./gradlew test           # unit tests only (pure JVM, no game instance needed)
./gradlew runClient      # dev client
./gradlew runServer      # dev dedicated server
```

Build system is [GregTechCEu Buildscripts](https://github.com/GregTechCEu/Buildscripts)
(a RetroFuturaGradle wrapper), matching the other Mica Technologies 1.12.2 mods (RCMC, CSM, SUM,
LDIB).

## Architecture at a glance

```
com.micatechnologies.minecraft.lbe
├── Lbe, LbeConfig, LbeRegistry, LbeTab, Lbe*Proxy    # Forge plumbing
├── rarity/      # the scoring engine — pure Java, ZERO Minecraft types
│   ├── Rarity, ItemKeys                 # the four tiers; the modid:name#meta key format
│   ├── ItemProfile, CraftingRecipe      # plain-data views of an item and a recipe
│   ├── ItemGraph                        # the seam: what the scorer is allowed to know
│   ├── RarityWeights, RarityScorer      # the model itself, memoised and cycle-safe
│   ├── MaterialScores                   # declared values — the scarcity input
│   ├── RarityOverrides, KeyFilter       # the config's tier overrides and blacklist
│   └── RarityTable, LootRules, LootRoller
├── catalog/     # the ONLY place that knows both worlds
│   ├── ForgeItemGraph                   # walks Forge's item + recipe registries
│   └── LootCatalog                      # built once at postInit; rolls boxes
├── block/       # BlockLootBox ×4, TileEntityLootBox (the seed)
├── world/       # LootBoxWorldGen, LootTableInjector (opt-in chest loot)
├── network/     # LbeNetwork, PacketRevealLoot — presentation only, never authoritative
├── command/     # /lbe
└── client/      # reached ONLY via LbeClientProxy
    ├── gui/     # GuiLootReveal — the case-opening reel
    └── render/  # TileEntityLootBoxRenderer — the hovering tier glint
```

**The load-bearing constraint:** `rarity` contains no Minecraft types. That keeps the parts most
likely to be subtly wrong testable on a bare JVM (`./gradlew test` runs 64 tests in seconds), against
graphs small enough to read in one screen. Every real modelling bug so far was caught that way — see
[`docs/README.md`](docs/README.md) for the list.

**The other one:** nothing outside `client/` may touch `net.minecraft.client`. A stray client import
in common code compiles perfectly and only fails when a dedicated server boots — hence the CI smoke
test below.

## CI

- **Pull requests** — compile + unit tests, then a dedicated-server smoke test that boots a real
  server and asserts it reaches startup. LBE has a sharper reason for that second job than most mods:
  the rarity engine walks every registered item and every registered recipe at postInit, and a
  malformed recipe or a cyclic ingredient graph surfaces *there* and nowhere else. The unit tests
  score hand-built profiles and never touch a registry.
- **Push to `main`** — builds and publishes a pre-release with checksums; a manual dispatch with
  `release=true` cuts a full `YYYY.MM.DD` release.
- Pre-releases older than 90 days are pruned automatically.

## License

LGPL 2.1 — see `LICENSE`.
