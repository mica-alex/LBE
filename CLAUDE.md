# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

LBE ("Lucky Bucks Extravaganza", formerly "Loot Box Extravaganza") is a **Minecraft 1.12.2 Forge
mod** (mod id: `lbe`) with two halves:

1. **Loot boxes** — four tiers scattered through the world, filled with items from **every installed
   mod**, sorted into those tiers automatically.
2. **The casino** — gambling games played for real server currency, starting with a slot machine.

**The sorting is the first half.** Everything else around it — the block, the world generator, the
roll — is scaffolding around a scoring model that reads Forge's item and recipe registries at
postInit and works out what each item is worth. If you are changing something there, know which side
of that line it is on.

**The casino needs money, and LBE does not have any.** It runs on SUM's economy API, as a
**compile-only, optional** dependency: on a pack without SUM the mod loads, the loot boxes work
unchanged, and the machines say they are closed. That property is enforced by structure — exactly
one class in LBE may name a SUM type — and it is easy to break without noticing.
**Read [`docs/design/CASINO.md`](docs/design/CASINO.md) before touching anything under `casino/`.**

The mod id stays `lbe` through the rename, and so do every registry name and config key: changing
them would orphan blocks in existing worlds and silently reset every server's config.

Build system is GregTechCEu Buildscripts (a RetroFuturaGradle wrapper), the same as the sibling mods
`RCMC`, `minecraft-city-super-mod` (CSM), `uia-server-utility-mod` (SUM) and `LDIB`. This repo was
scaffolded from LDIB, and `build.gradle` / `gradlew` / the workflows came from it verbatim.

## Build Commands

Set `JAVA_HOME` to a **JDK 17–22** install before each `./gradlew` invocation. **21 is the
recommended sweet spot** (and what CI uses): RetroFuturaGradle wants the Gradle process on Java 21+,
and the pinned Gradle 8.9 officially supports running only on Java ≤ 22. The compiler and mod code
target **Java 8** via Jabel regardless — only the JVM that runs Gradle changes.

```bash
JAVA_HOME="..." ./gradlew build      # compile + unit tests + jar
JAVA_HOME="..." ./gradlew test       # unit tests only — pure JVM, seconds not minutes
JAVA_HOME="..." ./gradlew runClient  # dev client
JAVA_HOME="..." ./gradlew runServer  # dev dedicated server
JAVA_HOME="..." ./gradlew clean

# Apple Silicon: use the Rosetta path (see addon.gradle for the one-time setup it needs):
JAVA_HOME="..." ./gradlew runClient -Prosetta
```

Heap is `-Xmx3G` in `gradle.properties` for decompilation.

### Inspecting what the model actually did

`./gradlew test` proves the model behaves; it does not tell you what it made of a real pack. For that:

```bash
# boot a server with the catalogue logged, then read it back
bash .github/scripts/server-smoke-test.sh          # generates run/config/lbe.cfg on first run
# set B:logCatalogueOnStartup=true in run/config/lbe.cfg, re-run, then:
grep -a "\[lbe\]:" server-smoke.log | sed 's/.*\[lbe\]: *//' | grep " = "
```

In-game, `/lbe rarity <item>` and `/lbe dump` do the same job with less ceremony. **Do this after any
change to the scoring model.** Three of the model's real bugs were invisible to the unit tests and
obvious the moment the vanilla catalogue was read.

## Architecture

### The central design decision

**Rarity is derived, not authored.** The recipe graph is the only cross-mod, always-present,
always-current description of progression that exists in Minecraft, so the model is built on it: an
item is worth what went into it, plus how far down the chain it sits, plus what the item itself is
like. Scores are cut into tiers by *percentile* so the result self-normalises to whatever is
installed.

Read [`docs/design/RARITY_MODEL.md`](docs/design/RARITY_MODEL.md) before touching `rarity/`. It has
the full formula, the justification for every coefficient, and — importantly — the list of things the
model structurally *cannot* see.

### Layers

```
casino/      The games. See docs/design/CASINO.md.
  slots/                  pure game logic, ZERO Minecraft types, RTP pinned by test
  economy/                the SUM seam — CasinoBank/Wager are SUM-free; only
                          SumEconomyBridge names a SUM type, and only LbeEconomy
                          reaches it, behind Loader.isModLoaded("sum")
  block/                  the machines

rarity/      The scoring model. Pure Java, ZERO Minecraft types.
  ItemGraph               the seam — what the scorer is allowed to know
  RarityScorer            memoised, cycle-safe recursion over the recipe graph
  RarityWeights           every coefficient, each justified on its field
  MaterialScores          declared values for what the walk cannot price
  RarityTable             percentile bucketing + overrides
  LootRules / LootRoller  what a box of each tier is worth

catalog/     The ONLY place that knows both worlds.
  ForgeItemGraph          walks ForgeRegistries.ITEMS / .RECIPES + FurnaceRecipes
  LootCatalog             built once at postInit; rolls boxes

block/       BlockLootBox ×4 (one per tier) + TileEntityLootBox (the seed)
world/       LootBoxWorldGen — per-chunk, per-tier, independent rolls
command/     /lbe — rarity, dump, reload, give, place
Lbe*.java    Forge plumbing: @Mod class, config, registry, creative tab, proxies
```

### Rules that are load-bearing — do not break these

1. **`rarity` must never import a Minecraft type.** That is what makes the scoring model unit-testable
   on a bare JVM (`./gradlew test` runs 58 tests in seconds with no game instance). Convert at
   `ForgeItemGraph`; if you need an `ItemStack`, build it in `LootCatalog`, not in `rarity`.

2. **Common code must never reach client-only classes.** A stray `net.minecraft.client` import in
   common code compiles perfectly and only fails when a dedicated server boots — which is exactly what
   the CI server smoke test exists to catch. (SUM shipped three such bugs at once and took a server
   down.)

3. **The catalogue is built at postInit and nowhere else.** Every other mod has finished registering
   its items and recipes by then and not one moment earlier. It is also expensive (~a second on a
   large pack), so nothing may trigger a rebuild except `/lbe reload`.

4. **Ingredients contribute their `cost`, not their `score`.** The two differ by the depth bonus, and
   charging a parent for its children's chain length compounds catastrophically — it is what put a
   *wooden pickaxe* in the rare tier. See the `Eval` class doc in `RarityScorer`.

5. **A truncated score is never cached.** Modded recipe graphs are full of cycles; hitting one scores
   the sub-item as raw, and that result is context-dependent. Caching it would let one item's cycle
   permanently deflate an unrelated item that merely shares an ingredient, with the damage depending
   on registry iteration order. `RarityScorer` tracks a truncation counter for exactly this.

6. **World generation must not write outside the chunk being populated.** Hence the `+8` offset. The
   alternative is a generation cascade that can hang a server on first world load.

7. **Never hard-code the mod id/name/version** — use `LbeConstants`, which reads the build-generated
   `Tags` class. The version comes from the latest git tag (`YYYY.MM.DD` for releases).

### Config

`LbeConfig` reads Forge `Configuration` into static fields at load time. Categories: `general`,
`worldgen`, `rarity`, `loot`, `overrides`.

**Nothing here is synced, and nothing here should be.** Every value shapes what the *server* puts in
the world and what it hands a player who opens a box; a client's copy is simply never consulted. If a
genuinely client-side setting is ever added (a particle effect, an open animation), it belongs in a
new `client` category and must stay out of `rarity` and `loot`.

The two override tables are **not the same thing** and confusing them is the most likely way to
mislead someone:

- `rarity.materialScores` sets an item's **score**, which flows into everything crafted from it.
- `overrides.overrides` sets an item's **final tier**, and affects that item only.

Overridden items are excluded from the percentile population, so a pack declaring fifty items
legendary does not drag every unrelated item's tier down with them.

### Mixins — not needed

`usesMixins = false`, and this mod is unlikely ever to need them. Everything it does is public-hook
territory: `IWorldGenerator` for placement, registry iteration for scoring, a `Block` + `TileEntity`
for the box. Before writing a coremod for anything here, **check for a Forge event first** — LDIB
presumed camera lean needed one and found `EntityViewRenderEvent.CameraSetup` already carried it.

## Conventions

- Package root: `com.micatechnologies.minecraft.lbe`
- Item keys are `modid:name#meta` throughout. `ItemKeys` builds and parses them; nothing else should
  be doing string surgery on them. Config files may omit the metadata and get `#0`.
- Tier ids (`common`/`uncommon`/`rare`/`legendary`) are one set of strings used for registry names,
  lang keys, config keys and command arguments alike. `Rarity#id()` is the only source.
- `Rarity` ordinal order is ascending value and a lot of code depends on it. Do not reorder.
- Config parsers (`RarityOverrides`, `MaterialScores`, `KeyFilter`) **never throw** — malformed lines
  go to `problems()` and get logged at `WARN`. A typo on line 40 of a hand-edited config must not stop
  a world loading, and an author needs to be told about all of their typos at once.
- Textures under `assets/lbe/textures/blocks` are **generated** by `tools/gen_box_textures.py`. Edit
  the script, not the PNGs, unless you are replacing them with real art.

## Planning docs

`docs/AGENT-PLANS/` is **gitignored** — it holds the phased implementation plan and agent working
notes. `docs/design/` holds the committed design notes (the rarity model, the boxes).

## CI

- `test-mod-build-pr.yml` — compile + unit tests, then a **dedicated-server smoke test** that boots a
  real server and greps for `Done (`. That second job is doing double duty here: it catches side
  violations *and* it is the only place the scorer meets a real registry before a player does.
- `build-mod-release-pre-release-main.yml` — on push to `main`, tags and publishes a pre-release with
  checksums; `workflow_dispatch` with `release=true` cuts a full release.
- `cleanup-mod-pre-releases.yml` — prunes pre-releases older than 90 days.
