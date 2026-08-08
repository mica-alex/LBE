# The rarity model

How LBE decides that a diamond pickaxe is rare and a stick is not, without anyone having written
that down.

## The problem

A loot mod that supports "all other mods" has exactly two options.

1. **Hand-written loot tables per mod.** This is what almost everything does. It works beautifully
   for the three mods someone got round to, is wrong for the other three hundred, and is stale the
   moment any of them updates.
2. **Derive rarity from what is already in the registries.** Nobody wrote a rarity for their items,
   but they did write recipes — and a recipe is a statement about what something costs.

LBE takes the second option. The recipe graph is the only cross-mod, always-present, always-current
description of progression that exists in Minecraft, so it is what the model is built on.

## What the model computes

Every item gets a **score**. Scores are then cut into tiers by percentile (see below); the score
itself is never compared against a fixed threshold.

Every item gets **two** numbers: a `score` (what it is worth as loot — this is what tiers are cut
from) and a `cost` (what a recipe consuming it pays). They differ by exactly the depth bonus; see
"Depth is paid once" below for why that matters more than it sounds like it should.

```
declared in materialScores:
    score = cost = declaredScore + properties(item)   # the recipe is not walked

raw material (no recipe):
    score = cost = rawMaterialBase + properties(item)

crafted:
    ingredientSum = Σ over consumed slots of min(COST of the slot's alternatives)
    dearest       = max over consumed slots of that same per-slot cost
    craftCost     = max( dearest / outputCount,
                         (ingredientSum / outputCount) ^ bulkCompression )
    cost          = craftCostWeight  · craftCost
                  + varietyWeight    · distinctIngredientCount
                  + properties(item)                  # clamped at 0
    score         = cost + depthWeight · ln(1 + depth)

properties(item)  = unstackableWeight    · [maxStackSize == 1]
                  + durabilityWeight     · ln(1 + maxDurability)
                  + enchantabilityWeight · enchantability
                  + vanillaRarityWeight  · EnumRarity ordinal
                  + blockWeight          · [is a block]
                  + containerItemWeight  · [has a container item]
```

The recursion is memoised, so scoring a whole modded registry is linear in the number of recipe
edges rather than exponential in tree depth.

`cost` is clamped at zero because `blockWeight` is negative: a nearly-free block can price out below
zero, and a negative ingredient sum raised to a fractional power is `NaN` — which would spread
silently through every item that consumes it.

### The three terms that carry the model

**`craftCost`** is "what went into it", recursively. This is where the user's "recipe complexity and
the amount required" lives, and it needs no separate quantity term — an ingredient list carries one
entry per consumed slot, so nine cobblestone is nine additions.

**`depth`** is how many crafting steps separate an item from raw materials. Logarithmic, so the
first step is a milestone and the eleventh is not. Without it, a tech mod with a long chain of
individually cheap intermediates would rank its end product no higher than its second step.

**`variety`** counts *distinct* ingredient kinds. This is the only thing that separates "eight of one
thing" (a shape) from "eight different things" (a milestone recipe), because `craftCost` cannot tell
them apart.

### Depth is paid once

The depth bonus must be paid by the finished item and **never charged again to whatever consumes
it**. Letting it flow into the ingredient sum bills every parent for its children's chain length on
top of its own, and the error compounds with every level.

This is not theoretical. On real vanilla data it put a **wooden pickaxe in the rare tier** — three
cheap steps from a log (log → planks → stick → pickaxe), worth nothing at all, and scoring above iron
gear purely because it paid for the plank's depth, then the stick's depth on top of that, then added
its own.

Hence the `score` / `cost` split: parents pay `cost`, and only the tier calculation ever sees
`score`.

### `bulkCompression`, and why it is the most consequential number here

Set it to 1 and the model is linear in quantity. A block of iron is nine ingots, so it scores nine
times an ingot, and every storage block in the pack lands in the top tier next to things that took a
nether trip and a dozen crafts.

That is wrong in a way players notice instantly: **a storage block is not a rare item, it is a tidy
pile of a common one.** Compressing the ingredient sum (0.75 by default) says bulk is worth less than
progression, which is what a loot box should reward.

### The dearest-ingredient floor

Compression applied to the raw sum has a failure of its own: it discounts *every* input equally, so a
recipe with one very expensive ingredient can come out worth less than that ingredient alone. The
case that made this obvious was a beacon scoring below the nether star inside it.

So `craftCost` is floored at the dearest single ingredient (divided by the output count, or
uncrafting would be free value). The rule needs no tuning and is hard to argue with: **crafting
something can never make it worth less than the best thing you put in.** Compression still governs
wherever cost is spread across many cheap inputs — which is where it was always meant to bite.

## What the model cannot see, and what to do about it

**Scarcity is not in the input.** Recipe data says nothing about how hard a material was to obtain,
so to the recursion a diamond and a lump of cobblestone are identical: neither is crafted from
anything. This is not a tuning problem, and no weighting recovers it. The mod's first acceptance test
caught it immediately — an **iron pickaxe outscored a diamond one**, because smelting an ingot is a
crafting step and finding a diamond is not.

The fix is a data input, not a hack: `rarity.materialScores` declares values for materials the walk
cannot price. Vanilla's ores, gems and boss drops ship as defaults; a pack author adds a line per
modded ore. Where a value is declared, the item's own recipe is not walked at all — which
conveniently also severs the ingot↔block cycles that riddle a modded graph.

`VanillaOrderingTest.scarcityIsNotDerivableFromRecipes` pins the gap in place — it asserts that a raw
diamond still scores the plain base value with the table removed — so nobody deletes the table
thinking it decorative.

Two failure shapes in particular are worth recognising, because both showed up in the vanilla
catalogue and both generalise straight to modded packs:

- **Recolouring is not progression.** To the walk, `wool → dye → coloured wool` is two crafting steps
  like any other, and everything built from coloured wool inherits the inflation. Left alone, all
  sixteen beds and most of the banners floated into rare and legendary on nothing but colour. Pin the
  coloured variants at their base material's value.
- **A mob drop that *also* has a crafting recipe gets priced as if you always crafted it.** Leather is
  the vanilla case: four rabbit hides make one, so every cow drop was billed at rabbit-hunting rates
  and leather armour came out legendary. Declare the drop.

**Other blind spots**, currently accepted:

| Not modelled | Why not | Workaround |
| --- | --- | --- |
| Fuel cost of smelting | The currency (coal / lava / RF) varies so wildly between packs that counting it would make the model depend on which power mod is installed | — |
| Machine/multiblock requirements | A recipe does not say "and you need a 5×5×5 structure first" in any readable way | `materialScores` on the output |
| Mob drops and dungeon loot | Not recipes at all | `materialScores` |
| Recipes gated behind research/quests | Mod-specific, no common API | `overrides` |

**And some things will simply be wrong.** In the shipped vanilla catalogue, `carrot_on_a_stick` comes
out legendary — it is unstackable, has durability, and sits three steps from a log via a fishing rod,
which is everything the model rewards and nothing a player values. That is what `overrides` is for,
and reaching for it is not an admission of failure: a heuristic over data nobody wrote for it will
have a tail, and the config is how you cut the tail off.

## Percentiles, not thresholds

A score of 12.4 means nothing on its own. In vanilla it is near the top; in a kitchen-sink pack it is
lower-middle. Fixed score thresholds would therefore behave completely differently per pack and would
need retuning by hand every time someone added a mod — exactly the manual work this mod exists to
avoid.

Cutting by **percentile of the scored population** makes the tiers self-normalising. "The top 2.5% of
what is installed" is legendary whether that population is vanilla's few hundred items or a
12,000-item pack's, and adding a mod redistributes the tiers automatically.

Defaults: `0.60 / 0.88 / 0.975` — bottom 60% common, next 28% uncommon, next 9.5% rare, top 2.5%
legendary. Deliberately steep at the top; a legendary tier holding one item in ten is not legendary,
it is just the fourth tier.

## The two config tables, which are not the same thing

This is the distinction most likely to trip up a pack author.

| | `rarity.materialScores` | `overrides.overrides` |
| --- | --- | --- |
| Sets | the item's **score** | the item's **final tier** |
| Propagates | yes — into everything crafted from it | no — that item only |
| Use for | a material whose cost the walk cannot see | one item that landed in the wrong tier |
| Example | `minecraft:diamond=8.0` makes diamond *tools* valuable too | `minecraft:dragon_egg=legendary` |

Overridden items are also **excluded from the percentile population**, so declaring fifty items
legendary does not drag every unrelated item's tier down with them. An override that had
action-at-a-distance over items it never named would not be an override.

## Tuning it

`/lbe rarity <item>` prints the full term-by-term breakdown, including whether the dearest-ingredient
floor applied. That is the only sane way to work out whether an item needs a weight change, a
declared material value, or a plain tier override — and the answer is usually the third.

`/lbe dump` writes the whole scored catalogue to a TSV, which is what to look at when the *shape* of
the tiers seems wrong rather than one item.

## Where the code lives

Everything above is in `com.micatechnologies.minecraft.lbe.rarity`, which contains **zero Minecraft
types**. `catalog.ForgeItemGraph` is the only class that knows both worlds; it walks Forge's
registries and produces the plain-data `ItemGraph` the scorer consumes. That seam is what makes the
whole model testable on a bare JVM in milliseconds — see `docs/README.md`.
