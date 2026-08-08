# The boxes themselves

The rarity model ([`RARITY_MODEL.md`](RARITY_MODEL.md)) decides *what tier an item is*. This document
covers everything else: what a box is, where it comes from, and what happens when you open one.

## A box is a block, not an entity

Four blocks — `lbe:loot_box_common`, `..._uncommon`, `..._rare`, `..._legendary` — each with its own
`ItemBlock`, model and creative-tab entry.

**Four blocks rather than one block with a tier property**, because the tier is *identity*, not
state. Each wants its own registry name so a pack can reference `lbe:loot_box_rare` in a structure or
a `/setblock`, its own item so two tiers do not stack together in an inventory, and its own model. A
metadata property would have bought one registry entry and then needed subtype `ItemBlock`s,
per-metadata models and a metadata-aware creative tab to claw all of that back.

Higher tiers emit light (uncommon 3, rare 7, legendary 12) so a good find is visible across a dark
cave. Common boxes emit nothing — if every box glowed, the glow would stop meaning anything.

## The seed, and why boxes cannot be re-rolled

`TileEntityLootBox` holds a **seed**, fixed when the box enters the world, and the contents are
derived from it.

Without this, a box could roll from `world.rand` at the moment it is opened — until you notice that a
player can then break the box, place it again, and re-roll it until they like the answer. A loot box
with a free respin is not a loot box.

So the seed travels with the box:

- `BlockLootBox#getDrops` writes it into the dropped `ItemStack`'s NBT.
- `BlockLootBox#onBlockPlacedBy` reads it back into the new tile entity.
- `BlockLootBox#removedByPlayer` + `#harvestBlock` **defer the block's removal** so the tile entity
  still exists when `getDrops` runs. Vanilla's player-harvest path is `removedByPlayer` (which sets
  the block to air, destroying the tile entity) *before* `harvestBlock` → `getDrops`, so without this
  pair the seed is silently lost on every mined box and the guarantee quietly stops holding. It is
  invisible unless you specifically inspect the NBT of a mined box.
- `/lbe give` mints one stack per box, each with its own seed, so boxes in an inventory do not merge
  into a single shared roll.

Contents are still computed **lazily at open time** rather than stored. That is deliberate: a config
change or a newly installed mod is then reflected in boxes that generated before it. The guarantee
players care about is "this box cannot be re-rolled by me", not "this box's contents were frozen at
world generation and are now stale".

## Breaking a box moves it; it does not open it

`getDrops` returns the box itself, seed intact — not its contents.

This is a design choice rather than an implementation convenience. A box you can pick up is a box you
can carry home and open where your storage is, which is a far nicer thing to find in a cave than one
that scatters twelve stacks across a ravine the moment you touch it.

Opening is right-click, and every decision is server-side: the client returns early from
`onBlockActivated` so the swing animation plays and the held item is not used.

The block is set to air *before* anything is handed out. The other order lets an item land in the
block space and be swept up by the block-break drop logic, which is a duplication bug several mods
have shipped.

### Pay first, then perform

The server puts every item in the player's inventory **before** the reveal screen is told they
exist. `PacketRevealLoot` is presentation only — nothing is awaited, nothing is acknowledged, and no
reward depends on the animation finishing.

This is the load-bearing ordering constraint of the whole feature. A reveal that gates the payout is
a reveal that eats payouts on disconnect, and no amount of care inside the GUI fixes it afterwards.
Skipping the animation, closing it, alt-tabbing, or the screen never opening at all are all outcomes
where the player has already been paid.

Items go to the inventory rather than the floor for the same reason: watching an animation while
your legendary rolls into a ravine is not a reward. Overflow drops at the player, not at the box —
by then the box is gone and the player may have moved.

## The reveal screen

`GuiLootReveal` is a **case-opening reel**: a horizontal strip of items scrolls past a fixed
tier-coloured marker, decelerates on a quintic ease-out, and lands on the real reward, which then
drops into a collected row along the bottom. One item at a time, each spin shorter than the last.

A reel rather than three slot-machine wheels because the anticipation is horizontal and shared — you
can see what is *coming* as it slows, which is the feeling a loot box is selling. Three wheels give
you three small uncertainties instead of one big one.

Details that matter more than they look:

- **One click per cell crossed.** The deceleration becomes audible, which is most of why a reel
  feels like it is slowing rather than just moving less.
- **Decoys come from the client's own catalogue**, drawn from the box's tier and the one below.
  A reel full of dirt while you wait for a legendary undersells the moment; a reel full of beacons
  oversells it. They are cosmetic, so they are not worth shipping in the packet.
- **Any key or click skips.** Not a concession — this screen will be seen hundreds of times in a
  weekend, and the hundredth viewing of an animation is an obstacle between a player and their
  inventory. Skipping collects everything at once rather than fast-forwarding.
- **The game does not pause.** It is cosmetic; the world should keep running.

## The glint

`TileEntityLootBoxRenderer` draws a tier-coloured sparkle hovering over each box, turning and
bobbing. The crate itself is a static block model in the chunk mesh where it costs nothing — only
the part that has to move is drawn per-frame.

Higher tiers turn faster and are larger as well as differently coloured, so tier stays legible to
anyone who cannot easily distinguish the hues. Animation is driven from world time offset by block
position, so nothing has to tick and neighbouring boxes are visibly out of phase rather than pulsing
in lockstep.

`TileEntityLootBox#getRenderBoundingBox` is expanded upward to cover it — without that the glint
vanishes whenever the block's own cube leaves the camera frustum, which happens constantly when
looking up at a box on a ledge.

**There is deliberately no lid-opening animation.** Opening puts a full-screen reveal up
immediately, so anything animating in the world would play entirely behind it. The box's moment is a
particle burst and a sound, which is what *other* players nearby see — and what the opener sees if
they skip.

## What comes out

`LootRules` (config category `loot`) decides generosity, independently of the scoring model — so
legendary boxes can be made more generous without reclassifying a single item.

| Tier | Entries | Max pile per entry |
| --- | --- | --- |
| Common | 3–5 | 16 |
| Uncommon | 3–4 | 8 |
| Rare | 2–3 | 4 |
| Legendary | 1–2 | 1 |

The per-entry pile is capped again at the item's real max stack size when the roll is realised —
`LootRules` works in tier-level caps and has no way to know that a given item only stacks to 16.

### Tier bleed

Each entry has a small chance of being drawn from a neighbouring tier: **25% down**, **4% up**.

Bleeding *down* is what stops a high-tier box being a humourless list of trophies with nothing
ordinary in it, and it is the main lever on how generous the mod feels overall. Bleeding *up* is the
jackpot, and it is small on purpose — a common box that regularly pays out legendary loot has quietly
abolished its own ladder.

Where both would fire, up wins. The two chances are independent and both small; on the rare occasion
they collide there is no reason to hand the player the worse outcome.

### Empty tiers

A tier can end up empty — a tiny pack, an aggressive blacklist, or percentile cuts set close
together. `LootRoller` walks **down** to a populated tier rather than up, so the failure mode is a
disappointing box and never a free jackpot. `LootCatalog.rebuild` also logs a `WARN` per empty tier at
startup, because otherwise the misconfiguration is invisible until a box disappoints someone.

## World generation

`LootBoxWorldGen` is an `IWorldGenerator` registered at weight 0. Each tier gets an **independent**
per-chunk roll:

| Tier | Default chance per chunk | Roughly |
| --- | --- | --- |
| Common | 0.125 | one chunk in eight |
| Uncommon | 0.05 | one in twenty |
| Rare | 0.012 | one in eighty |
| Legendary | 0.001 | one in a thousand |

Independent rather than "pick one tier, weighted", because the tiers are meant to be found at
genuinely different rates. A single weighted pick would tie those rates together, so making
legendaries rarer would silently make commons more frequent.

Placement tries up to 12 positions: first the surface (the terrain height, if it is inside the
configured Y band), then — if `allowUnderground` — random cave air pockets within the band. A position
is usable when it is air, has air above it, and stands on a full solid top face. The head-room check
stops boxes generating in the one-block gap under an overhang where nobody will ever see them; the
top-face check stops them perching on fence posts and snow layers.

**Positions are offset by 8 blocks into the chunk being populated.** Writing outside it is the classic
world-gen cascade bug: it forces a neighbouring chunk to generate mid-population, which recurses and
can hang a server on first world load.

Nothing in world generation touches the catalogue. Placement writes a block; the box does not decide
what is inside it until someone opens it. That keeps the world-gen thread's work to a `setBlockState`.

## Commands

`/lbe`, operator level 2. Gated as a whole rather than per subcommand: `reload` stalls the server
thread for around a second on a large pack, and `give` produces items from nothing.

| Subcommand | Does |
| --- | --- |
| `rarity [item]` | Tier and full score breakdown for the held item, or a named one |
| `dump` | Writes the whole scored catalogue to `rarity-dumps/*.tsv` |
| `reload` | Re-reads the config **and** rebuilds the catalogue — both, in that order |
| `give <tier> [n]` | Gives loot boxes |
| `place <tier>` | Places a box at your feet |
| `loottables` | Forces every configured loot table to load and reports which got a box pool |

`loottables` exists because injection is otherwise **unobservable**. Tables load lazily, so a config
entry that matches nothing looks exactly like one that matches a chest you have not opened yet — and
the pack author finds out weeks later.

## Loot-table injection

Off by default (`worldgen.injectIntoLootTables`). Natural generation already places boxes, and a pack
that has balanced its dungeon loot did not ask a newly installed mod to edit it.

When on, a pool named `lbe_loot_boxes` is **appended** to each configured table — nothing existing is
replaced or removed. The pool holds one weighted entry per tier plus a heavily weighted empty entry,
so most chests are untouched and the ones that are not usually hold a common box. Tier weights are
steeper than the world-generation rates on purpose: a chest is already a reward, so a legendary box
inside one should be rarer than one found in a cave.

**An entry ending in `/` matches by prefix; anything else must match exactly.** Treating every entry
as a prefix looks equivalent and is not — `minecraft:chests/jungle_temple` is a prefix of
`minecraft:chests/jungle_temple_dispenser`, so listing the temple's chest silently put loot boxes in
its *arrow trap*. That was a real bug, caught by reading the injection log.

Injection is idempotent: `LootTableLoadEvent` fires again on reload and `addPool` throws on a
duplicate pool name, so the pool is only added if it is not already there.
