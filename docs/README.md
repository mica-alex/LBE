# LBE Documentation

| Document | What it covers |
| --- | --- |
| [`design/RARITY_MODEL.md`](design/RARITY_MODEL.md) | How an item's tier is worked out: the scoring formula, every coefficient, the percentile cuts, and what the model cannot see |
| [`design/LOOT_BOXES.md`](design/LOOT_BOXES.md) | The boxes themselves: the block, the seed, world generation, what comes out when you open one |
| [`AGENT-PLANS/`](AGENT-PLANS/) | **Gitignored.** Phased implementation plan and agent working notes |

## Conventions used across these docs

- **"Score" is a number, "tier" is one of four names.** Scores are never compared against a fixed
  threshold — they are cut into tiers by percentile of whatever is installed. A score of 12 means
  nothing without knowing the pack it came from.
- **An item key is `modid:name#meta`.** The metadata suffix is mandatory internally, because a great
  deal of 1.12.2 content is only distinguishable by it. Config files may omit it and get `#0`.
- **"Cost" and "score" are different numbers.** Cost is what a recipe pays for an ingredient; score is
  what the item is worth as loot. They differ by the depth bonus. See `RARITY_MODEL.md`.
- **Blocks are `lbe:loot_box_<tier>`**, one per tier, lowercase tier id throughout — registry names,
  lang keys, config keys and commands all use the same four strings.

## The seam that makes this testable

`com.micatechnologies.minecraft.lbe.rarity` contains **zero Minecraft types**. It works in string
keys, plain numbers and small immutable value classes; the game side hands it an `ItemGraph` and
takes back a `RarityTable`.

`catalog.ForgeItemGraph` is the only class in the mod that knows both worlds.

That is not architectural neatness for its own sake — it is what lets the parts most likely to be
subtly wrong be tested on a bare JVM in milliseconds, against a graph small enough to read in one
screen. Every genuine modelling bug found so far was found that way:

| Found by | Was |
| --- | --- |
| `VanillaOrderingTest.pickaxeLadder` | An iron pickaxe outscoring a diamond one — no scarcity signal existed at all |
| `VanillaOrderingTest.beaconIsTheTop` | A beacon scoring below the nether star inside it — bulk compression was discounting quality as well as quantity |
| Reading the real vanilla catalogue | A wooden pickaxe in the rare tier — the depth bonus was compounding up the recipe chain |

If a change ever makes it impossible to write a test that way, that change has broken the seam and
should be reconsidered before it lands.
