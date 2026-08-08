package com.micatechnologies.minecraft.lbe;

import com.micatechnologies.minecraft.lbe.rarity.KeyFilter;
import com.micatechnologies.minecraft.lbe.rarity.LootRules;
import com.micatechnologies.minecraft.lbe.rarity.MaterialScores;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import com.micatechnologies.minecraft.lbe.rarity.RarityOverrides;
import com.micatechnologies.minecraft.lbe.rarity.RarityTable;
import com.micatechnologies.minecraft.lbe.rarity.RarityWeights;
import java.io.File;
import net.minecraftforge.common.config.Configuration;

/**
 * Forge {@link Configuration}-backed settings, loaded once in
 * {@link Lbe#preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent)}.
 *
 * <p>Values are read into static fields at load time rather than queried per-use: world generation
 * asks these questions once per chunk, and {@code Configuration.get(...)} does string lookups and
 * I/O bookkeeping that have no place in a chunk-populate callback.</p>
 *
 * <p><b>Server authority.</b> Every value here shapes what the <i>server</i> puts in the world and
 * what it hands a player who opens a box. None of it is client presentation, so none of it is
 * synced — a client's copy is simply never consulted. If a client-side setting is ever added (a
 * particle effect, an open animation), it belongs in a separate {@code client} category and must
 * stay out of the {@code rarity} and {@code loot} categories, which are the server's business
 * alone.</p>
 *
 * <h2>The category layout</h2>
 * <ul>
 *   <li>{@code general} — cross-cutting switches.</li>
 *   <li>{@code worldgen} — where boxes appear and how often.</li>
 *   <li>{@code rarity} — the scoring model: weights, percentile cuts, and what to exclude.</li>
 *   <li>{@code loot} — what a box of each tier is worth when opened.</li>
 *   <li>{@code overrides} — the per-item rarity table. The reason most people will open this file.</li>
 * </ul>
 */
public final class LbeConfig {

    public static final String CATEGORY_GENERAL = "general";
    public static final String CATEGORY_WORLDGEN = "worldgen";
    public static final String CATEGORY_RARITY = "rarity";
    public static final String CATEGORY_LOOT = "loot";
    public static final String CATEGORY_OVERRIDES = "overrides";

    // --- general ----------------------------------------------------------------------------------

    /** Master switch for natural loot-box generation. Off still leaves the boxes placeable by hand. */
    public static boolean enableWorldGen = true;

    /**
     * Log the full scored catalogue at startup.
     *
     * <p>Off by default and should stay off in production — on a large pack this is tens of
     * thousands of lines. {@code /lbe dump} writes the same thing to a file on demand, which is
     * almost always what someone actually wanted.</p>
     */
    public static boolean logCatalogueOnStartup = false;

    // --- worldgen ---------------------------------------------------------------------------------

    /**
     * Chance per chunk that a box of each tier is placed, indexed by {@link Rarity#ordinal()}.
     *
     * <p>Per <i>chunk</i>, not per attempt: at the default a common box turns up in roughly one
     * chunk in eight, and a legendary in about one in a thousand — a few hours of exploring apart.
     * These are the numbers to change if the mod feels too generous or too sparse; nothing else in
     * the config affects the pace of discovery as directly.</p>
     */
    public static double[] spawnChancePerChunk = { 0.125D, 0.05D, 0.012D, 0.001D };

    /** Dimension ids boxes may generate in. Overworld only by default. */
    public static int[] worldGenDimensions = { 0 };

    /** Lowest Y a naturally generated box may sit at. */
    public static int worldGenMinY = 8;

    /** Highest Y a naturally generated box may sit at. */
    public static int worldGenMaxY = 120;

    /**
     * Whether boxes may generate underground (in an air pocket in a cave) as well as on the surface.
     *
     * <p>On by default because a box you have to go and find is worth more than one you trip over,
     * and caves are where players already look for reward.</p>
     */
    public static boolean worldGenUnderground = true;

    /**
     * Whether to add loot boxes to chest loot tables (dungeons, villages, temples and so on).
     *
     * <p><b>Off by default, deliberately.</b> Natural generation already puts boxes in the world, and
     * a pack author who has balanced their dungeon loot did not ask a newly installed mod to start
     * editing it. Boxes are <i>added</i> to a table, never replacing what is already there.</p>
     */
    public static boolean injectIntoLootTables = false;

    /**
     * Loot tables to add boxes to when {@link #injectIntoLootTables} is on.
     *
     * <p>An entry ending in {@code /} matches by prefix, so {@code minecraft:chests/} covers every
     * vanilla chest table at once. Anything else must match a table name exactly.</p>
     */
    public static String[] lootTableTargets = {
        "# Loot tables to add boxes to. An entry ending in '/' matches by PREFIX, so",
        "# 'minecraft:chests/' covers every vanilla chest table at once. Anything else must match",
        "# a table name EXACTLY -- otherwise naming jungle_temple would also hit the",
        "# jungle_temple_dispenser arrow trap, which is not what anyone means.",
        "# Only consulted when injectIntoLootTables is true.",
        "minecraft:chests/simple_dungeon",
        "minecraft:chests/abandoned_mineshaft",
        "minecraft:chests/desert_pyramid",
        "minecraft:chests/jungle_temple",
        "minecraft:chests/stronghold_corridor",
        "minecraft:chests/stronghold_crossing",
        "minecraft:chests/stronghold_library",
        "minecraft:chests/village_blacksmith",
        "minecraft:chests/nether_bridge",
        "minecraft:chests/igloo_chest",
        "minecraft:chests/woodland_mansion",
        "minecraft:chests/end_city_treasure",
    };

    /**
     * Relative weight of "no box" in the injected pool. Higher means boxes turn up in fewer chests.
     *
     * <p>At the defaults a matching chest has roughly a one-in-six chance of containing any box.</p>
     */
    public static int lootTableEmptyWeight = 100;

    /**
     * Relative weight of each tier in the injected pool, indexed by {@link Rarity#ordinal()}.
     *
     * <p>Steeper than the world-generation rates on purpose: a chest is already a reward, so finding
     * a legendary box inside one should be rarer than stumbling over one in a cave, not commoner.
     * A weight of 0 keeps that tier out of chests entirely.</p>
     */
    public static int[] lootTableTierWeights = { 12, 6, 2, 1 };

    // --- rarity: the scoring model ----------------------------------------------------------------

    /** @see RarityWeights#rawMaterialBase */
    public static double weightRawMaterialBase = 1.0D;

    /** @see RarityWeights#bulkCompression */
    public static double weightBulkCompression = 0.75D;

    /** @see RarityWeights#craftCostWeight */
    public static double weightCraftCost = 1.0D;

    /** @see RarityWeights#depthWeight */
    public static double weightDepth = 3.0D;

    /** @see RarityWeights#varietyWeight */
    public static double weightVariety = 0.35D;

    /** @see RarityWeights#unstackableWeight */
    public static double weightUnstackable = 1.5D;

    /** @see RarityWeights#durabilityWeight */
    public static double weightDurability = 0.6D;

    /** @see RarityWeights#enchantabilityWeight */
    public static double weightEnchantability = 0.02D;

    /** @see RarityWeights#vanillaRarityWeight */
    public static double weightVanillaRarity = 4.0D;

    /** @see RarityWeights#blockWeight */
    public static double weightBlock = -0.75D;

    /** @see RarityWeights#containerItemWeight */
    public static double weightContainerItem = 1.0D;

    /** @see RarityWeights#maxRecipeDepth */
    public static int maxRecipeDepth = 24;

    /**
     * Percentile cut points between the tiers, ascending. Three values for four tiers.
     *
     * @see RarityTable#DEFAULT_PERCENTILE_CUTS
     */
    public static double[] tierPercentileCuts = RarityTable.DEFAULT_PERCENTILE_CUTS.clone();

    /**
     * Declared scores for materials the recipe walk cannot value — the model's scarcity input.
     *
     * <p>The defaults are vanilla's, and they are not decoration: without them the model has an
     * outright bug rather than a rough edge. Recipe data contains no notion of how hard something was
     * to obtain, so an uncorrected diamond scores exactly what a lump of cobblestone does, and an iron
     * pickaxe therefore outranks a diamond one (smelting an ingot is a crafting step; finding a
     * diamond is not). See {@link MaterialScores}.</p>
     *
     * <p>Values are in units of {@link #weightRawMaterialBase} — an ordinary raw material is 1. This
     * is the list a pack author extends when a mod adds ores or drops the scorer has no way to value:
     * one line per material, and everything crafted from it moves with it.</p>
     */
    public static String[] materialScores = {
        "# Declared value for materials the recipe walk cannot see the cost of.",
        "#   modid:name=score      score is in units of 'rawMaterialBase' (an ordinary raw material is 1)",
        "#   modid:name#*=score    every metadata variant",
        "#",
        "# NOT the same as the 'overrides' category. An override sets ONE item's final tier and",
        "# affects nothing else; a score here flows into everything crafted from the item, which is",
        "# what makes diamond tools valuable rather than just diamonds. Where a score is declared,",
        "# the item's own recipe is not walked at all.",
        "#",
        "# This is the list to extend for a modded ore or a boss drop the scorer undervalues.",
        "",
        "# --- gems and ores dug out of the ground ---",
        "minecraft:diamond=8.0",
        "minecraft:emerald=8.0",
        "minecraft:quartz=2.5",
        "minecraft:gold_ore=3.0",
        "minecraft:iron_ore=1.5",
        "minecraft:redstone=1.5",
        "minecraft:dye#4=1.5",
        "minecraft:coal=1.0",
        "",
        "# --- mob drops that also happen to have a crafting recipe ---",
        "# A trap worth knowing about. An item you normally get by killing something has no recipe,",
        "# so the walk treats it as raw and all is well — UNLESS the game also provides a crafting",
        "# route, in which case the walk finds that route and prices the drop as if you always took",
        "# it. Leather is the vanilla example (four rabbit hides make one), and pricing every cow",
        "# drop that way pushed leather armour into the legendary tier.",
        "minecraft:leather=1.0",
        "",
        "# --- nether and end ---",
        "minecraft:blaze_rod=5.0",
        "minecraft:ghast_tear=6.0",
        "minecraft:ender_pearl=4.0",
        "minecraft:nether_wart=2.5",
        "minecraft:shulker_shell=10.0",
        "minecraft:chorus_fruit=3.0",
        "",
        "# --- things you fight something for ---",
        "minecraft:nether_star=25.0",
        "minecraft:dragon_egg=50.0",
        "minecraft:elytra=30.0",
        "minecraft:totem_of_undying=15.0",
        "",
        "# --- recolouring is not progression ---",
        "# Dyeing something does not make it more valuable, but the recipe walk cannot know that:",
        "# to it, wool -> dye -> coloured wool is two crafting steps like any other, and everything",
        "# built from coloured wool inherits the inflation. Left alone, all sixteen beds and most of",
        "# the banners float into the rare and legendary tiers on nothing but colour.",
        "#",
        "# Pinning the coloured variants at their base material's value is the fix, and it is the",
        "# pattern to copy for any mod that ships sixteen colours of something.",
        "# (lapis is minecraft:dye#4 and is listed above; an exact key beats this wildcard)",
        "minecraft:dye#*=1.0",
        "minecraft:wool#*=1.0",
        "minecraft:carpet#*=1.0",
        "minecraft:stained_glass#*=1.0",
        "minecraft:stained_glass_pane#*=1.0",
        "minecraft:stained_hardened_clay#*=1.0",
        "minecraft:concrete#*=1.0",
        "minecraft:concrete_powder#*=1.0",
    };

    /**
     * Items that may never appear as loot, as {@link KeyFilter} patterns.
     *
     * <p>The defaults cover the vanilla items that are either uncraftable-and-unplaceable, would
     * trivialise the game, or are outright creative-only. Note that a blacklisted item is still
     * scored as an <b>ingredient</b> of things that are not blacklisted — excluding a command block
     * from the loot pool should not make everything crafted from one look cheap.</p>
     */
    public static String[] lootBlacklist = {
        "# Items that may never be given as loot. Three forms:",
        "#   modid:name        one item (metadata 0)",
        "#   modid:name#*      every metadata variant",
        "#   modid:*           everything from that mod",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:barrier",
        "minecraft:bedrock",
        "minecraft:end_portal_frame",
        "minecraft:spawn_egg#*",
        "minecraft:knowledge_book",
        // Our own boxes. A box that contains a box is a fine joke exactly once, and then it is an
        // unbounded chain that a player can farm without ever leaving the spot they are standing in.
        "lbe:*",
    };

    // --- loot: what a box is worth ----------------------------------------------------------------

    /** Fewest item entries a box of each tier gives, indexed by {@link Rarity#ordinal()}. */
    public static int[] lootMinRolls = { 3, 3, 2, 1 };

    /** Most item entries a box of each tier gives. */
    public static int[] lootMaxRolls = { 5, 4, 3, 2 };

    /** Largest pile one entry may be, per tier, before the item's own stack limit applies. */
    public static int[] lootMaxStackPerRoll = { 16, 8, 4, 1 };

    /** @see LootRules#bleedDownChance() */
    public static double lootBleedDownChance = 0.25D;

    /** @see LootRules#bleedUpChance() */
    public static double lootBleedUpChance = 0.04D;

    // --- overrides --------------------------------------------------------------------------------

    /**
     * Per-item rarity overrides — the manual answer to anything the scorer gets wrong.
     *
     * <p>Ships with a handful of entries that are less about correcting the scorer and more about
     * demonstrating every syntax the parser accepts, since this is the file a pack author will copy
     * from. See {@link RarityOverrides} for the grammar.</p>
     */
    public static String[] rarityOverrides = {
        "# Force an item's tier, bypassing the automatic scoring entirely.",
        "#   modid:name=tier          tier is common/uncommon/rare/legendary, or 0-3",
        "#   modid:name#meta=tier     for items whose variants live in metadata",
        "#   modid:name#*=tier        every metadata variant",
        "# Exact keys beat wildcards; among wildcards the first line that matches wins.",
        "minecraft:dragon_egg=legendary",
        "minecraft:nether_star=legendary",
        "minecraft:elytra=legendary",
        "minecraft:beacon=legendary",
        "minecraft:totem_of_undying=rare",
        "minecraft:enchanted_golden_apple=legendary",
        "# Wool is nine hundred recipes deep in some packs and is still just wool.",
        "minecraft:wool#*=common",
    };

    private static Configuration config;

    private LbeConfig() {
        throw new AssertionError("No instances.");
    }

    public static void init(File configFile) {
        config = new Configuration(configFile);
        load();
    }

    /**
     * Re-read the file from disk. Backs {@code /lbe reload}.
     *
     * <p>Callers must rebuild the catalogue afterwards — this only refreshes the fields, and the
     * scored table is derived from them. {@code CommandLbe} does both in that order; anything else
     * that calls this must too, or the config will say one thing and the loot tables another.</p>
     */
    public static void reload() {
        if (config != null) {
            config.load();
            load();
        }
    }

    private static void load() {
        config.addCustomCategoryComment(CATEGORY_GENERAL,
            "Cross-cutting switches.");
        enableWorldGen = config.getBoolean("enableWorldGen", CATEGORY_GENERAL, enableWorldGen,
            "Whether loot boxes generate naturally in the world. Turning this off does not remove "
                + "the boxes — they can still be given with /lbe give, placed from the creative "
                + "tab, or written into structures and other mods' loot tables.");
        logCatalogueOnStartup = config.getBoolean("logCatalogueOnStartup", CATEGORY_GENERAL,
            logCatalogueOnStartup,
            "Log every scored item and its tier at startup. Tens of thousands of lines on a large "
                + "pack; prefer '/lbe dump', which writes the same data to a file on demand.");

        config.addCustomCategoryComment(CATEGORY_WORLDGEN,
            "Where loot boxes appear and how often. These are the settings that decide how the mod "
                + "paces itself; change them first if it feels too generous or too sparse.");
        spawnChancePerChunk = config.get(CATEGORY_WORLDGEN, "spawnChancePerChunk",
            spawnChancePerChunk,
            "Chance per chunk of placing a box of each tier, in order: "
                + tierOrderComment() + ". A value of 0 disables that tier's natural generation.")
            .getDoubleList();
        worldGenDimensions = config.get(CATEGORY_WORLDGEN, "dimensions", worldGenDimensions,
            "Dimension ids boxes may generate in. 0 = overworld, -1 = nether, 1 = the end.")
            .getIntList();
        worldGenMinY = config.getInt("minY", CATEGORY_WORLDGEN, worldGenMinY, 0, 255,
            "Lowest Y a naturally generated box may sit at.");
        worldGenMaxY = config.getInt("maxY", CATEGORY_WORLDGEN, worldGenMaxY, 0, 255,
            "Highest Y a naturally generated box may sit at.");
        worldGenUnderground = config.getBoolean("allowUnderground", CATEGORY_WORLDGEN,
            worldGenUnderground,
            "Whether boxes may generate in cave air pockets as well as on the surface.");
        injectIntoLootTables = config.getBoolean("injectIntoLootTables", CATEGORY_WORLDGEN,
            injectIntoLootTables,
            "Whether to ADD loot boxes to chest loot tables (dungeons, villages, temples...).\n"
                + "Off by default: natural generation already places boxes, and a pack that has "
                + "balanced its dungeon loot did not ask a newly installed mod to edit it. Nothing "
                + "already in a table is ever replaced or removed — a new pool is appended.");
        lootTableTargets = config.getStringList("lootTableTargets", CATEGORY_WORLDGEN,
            lootTableTargets,
            "Which loot tables to add boxes to. An entry ending in '/' matches by PREFIX, so "
                + "'minecraft:chests/' covers every vanilla chest table at once; anything else must "
                + "match a table name exactly. Only used when injectIntoLootTables is true.");
        lootTableEmptyWeight = config.getInt("lootTableEmptyWeight", CATEGORY_WORLDGEN,
            lootTableEmptyWeight, 1, 100000,
            "Relative weight of 'no box' in the injected pool. Higher = boxes in fewer chests. At "
                + "the defaults a matching chest has roughly a 1 in 6 chance of holding any box.");
        lootTableTierWeights = config.get(CATEGORY_WORLDGEN, "lootTableTierWeights",
            lootTableTierWeights,
            "Relative weight of each tier within the injected pool, in order: " + tierOrderComment()
                + ". Steeper than the world-gen rates on purpose — a chest is already a reward, so a "
                + "legendary box in one should be rarer than one found in a cave. 0 keeps that tier "
                + "out of chests entirely.")
            .getIntList();

        config.addCustomCategoryComment(CATEGORY_RARITY,
            "The automatic scoring model.\n"
                + "\n"
                + "Every item in every installed mod is scored once at startup from what it is made "
                + "of, how deep its recipe chain runs, and its own properties. The scores are then "
                + "cut into tiers BY PERCENTILE, so the tiers mean the same thing whether you are "
                + "running vanilla or a 300-mod pack.\n"
                + "\n"
                + "You very likely do not need to touch the weights. If one specific item is in the "
                + "wrong tier, use the 'overrides' category instead — it is faster, it is exact, and "
                + "it cannot have side effects on anything else. Reach for the weights only when a "
                + "whole CLASS of items is wrong. '/lbe rarity <item>' prints the full term-by-term "
                + "breakdown of how an item got its score, which is the only sane way to tune these.");
        weightRawMaterialBase = config.getFloat("rawMaterialBase", CATEGORY_RARITY,
            (float) weightRawMaterialBase, 0.0F, 100.0F,
            "Score of an item with no recipe at all. The unit everything else is measured in.");
        weightBulkCompression = config.getFloat("bulkCompression", CATEGORY_RARITY,
            (float) weightBulkCompression, 0.0F, 1.0F,
            "Exponent applied to a recipe's total ingredient cost. THE most consequential number "
                + "here. Below 1, quantity is worth less than progression: a block of iron scores a "
                + "little above an ingot rather than nine times it. At 1.0 every storage block in "
                + "the pack becomes legendary.");
        weightCraftCost = config.getFloat("craftCost", CATEGORY_RARITY, (float) weightCraftCost,
            0.0F, 100.0F, "Multiplier on the compressed ingredient cost.");
        weightDepth = config.getFloat("recipeDepth", CATEGORY_RARITY, (float) weightDepth,
            0.0F, 100.0F,
            "Weight on ln(1 + how many crafting steps from raw materials). Logarithmic so a mod "
                + "with a long chain of cheap intermediates cannot dominate the top tier by length.");
        weightVariety = config.getFloat("ingredientVariety", CATEGORY_RARITY, (float) weightVariety,
            0.0F, 100.0F,
            "Weight per DISTINCT ingredient kind. Separates 'eight of one thing' from 'eight "
                + "different things'.");
        weightUnstackable = config.getFloat("unstackable", CATEGORY_RARITY,
            (float) weightUnstackable, -100.0F, 100.0F,
            "Flat bonus for items that do not stack. The game's own signal that an item is "
                + "individually significant.");
        weightDurability = config.getFloat("durability", CATEGORY_RARITY, (float) weightDurability,
            -100.0F, 100.0F, "Weight on ln(1 + max durability).");
        weightEnchantability = config.getFloat("enchantability", CATEGORY_RARITY,
            (float) weightEnchantability, -100.0F, 100.0F,
            "Weight per point of enchantability. Small on purpose: enchantability is not monotonic "
                + "in tier (gold is 22, diamond is 10), so a large weight here promotes gold gear "
                + "above diamond.");
        weightVanillaRarity = config.getFloat("declaredRarity", CATEGORY_RARITY,
            (float) weightVanillaRarity, -100.0F, 100.0F,
            "Weight per EnumRarity level the item declares for itself. The largest per-property "
                + "weight, because it is the only one that is not a guess — a mod author who set "
                + "EPIC on their item knows their progression better than we do.");
        weightBlock = config.getFloat("isBlock", CATEGORY_RARITY, (float) weightBlock,
            -100.0F, 100.0F,
            "Applied to items that are blocks. Negative by default: the median block in any pack is "
                + "bulk building material. Genuinely valuable blocks clear this easily on recipe "
                + "cost, which is the intent — it is a prior, not a verdict.");
        weightContainerItem = config.getFloat("containerItem", CATEGORY_RARITY,
            (float) weightContainerItem, -100.0F, 100.0F,
            "Bonus for items that survive being crafted with (buckets and the like). Reusability is "
                + "real value that recipe cost misses entirely.");
        maxRecipeDepth = config.getInt("maxRecipeDepth", CATEGORY_RARITY, maxRecipeDepth, 1, 128,
            "Hard cap on how far the recipe walk recurses. A safety limit against cyclic modded "
                + "recipe graphs, not a tuning knob. Hitting it scores the remainder as raw.");
        tierPercentileCuts = config.get(CATEGORY_RARITY, "tierPercentileCuts", tierPercentileCuts,
            "Percentile cut points between tiers, ascending — three values for four tiers. The "
                + "default 0.60/0.88/0.975 makes the bottom 60% common and the top 2.5% legendary. "
                + "Raise the last value to make legendary rarer. Items with a manual override do "
                + "not count toward these percentiles.")
            .getDoubleList();
        materialScores = config.getStringList("materialScores", CATEGORY_RARITY, materialScores,
            "Declared values for materials whose cost the recipe walk cannot see — ores, gems, mob "
                + "drops. THE MODEL NEEDS THESE: recipe data says nothing about how hard something "
                + "was to obtain, so without an entry a diamond scores exactly what cobblestone "
                + "does. One 'item=score' per line, in units of rawMaterialBase. Unlike the "
                + "'overrides' category, a score here flows into everything crafted from the item. "
                + "Extend this list for a modded ore or boss drop that comes out too cheap.");
        lootBlacklist = config.getStringList("blacklist", CATEGORY_RARITY, lootBlacklist,
            "Items that may never be GIVEN as loot. They are still scored as ingredients of things "
                + "that can be — blacklisting a command block should not make everything built from "
                + "one look cheap. Patterns: 'modid:name', 'modid:name#*', 'modid:*'.");

        config.addCustomCategoryComment(CATEGORY_LOOT,
            "What opening a box of each tier is actually worth. Independent of the scoring model "
                + "above on purpose: you can make legendary boxes more generous without "
                + "reclassifying a single item.");
        lootMinRolls = config.get(CATEGORY_LOOT, "minRolls", lootMinRolls,
            "Fewest item entries a box gives, in order: " + tierOrderComment()).getIntList();
        lootMaxRolls = config.get(CATEGORY_LOOT, "maxRolls", lootMaxRolls,
            "Most item entries a box gives, in order: " + tierOrderComment()).getIntList();
        lootMaxStackPerRoll = config.get(CATEGORY_LOOT, "maxStackPerRoll", lootMaxStackPerRoll,
            "Largest pile a single entry may be, per tier, before the item's own stack limit is "
                + "applied. This is what keeps a legendary box from handing out 32 of anything.")
            .getIntList();
        lootBleedDownChance = config.getFloat("bleedDownChance", CATEGORY_LOOT,
            (float) lootBleedDownChance, 0.0F, 1.0F,
            "Chance that any one entry is drawn from the tier BELOW the box's. Keeps high-tier "
                + "boxes from being a list of trophies with nothing ordinary in them.");
        lootBleedUpChance = config.getFloat("bleedUpChance", CATEGORY_LOOT,
            (float) lootBleedUpChance, 0.0F, 1.0F,
            "Chance that any one entry is drawn from the tier ABOVE. The jackpot. Keep it small: a "
                + "common box that regularly pays out legendary loot has abolished its own ladder.");

        config.addCustomCategoryComment(CATEGORY_OVERRIDES,
            "Manual per-item rarity. This is the escape hatch for anything the automatic scoring "
                + "gets wrong, and it is meant to be used — the scorer is a heuristic over data mod "
                + "authors never wrote for it, and on a big enough pack it will be wrong about "
                + "something.\n"
                + "\n"
                + "  modid:name=tier         tier is common/uncommon/rare/legendary, or 0-3\n"
                + "  modid:name#meta=tier    for items whose variants live in metadata\n"
                + "  modid:name#*=tier       every metadata variant\n"
                + "\n"
                + "Exact keys beat wildcards, so 'all wool is common, except that one' works. Among "
                + "wildcards, the first matching line wins. Overridden items are also removed from "
                + "the percentile calculation, so declaring fifty items legendary will not drag "
                + "every other item's tier down with them.");
        rarityOverrides = config.getStringList("overrides", CATEGORY_OVERRIDES, rarityOverrides,
            "One 'item=tier' per line. Lines starting with # are comments.");

        if (config.hasChanged()) {
            config.save();
        }
    }

    /** {@code "common, uncommon, rare, legendary"} — for array-setting comments. */
    private static String tierOrderComment() {
        StringBuilder out = new StringBuilder();
        for (Rarity rarity : Rarity.values()) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(rarity.id());
        }
        return out.toString();
    }

    // --- assembled views of the above -------------------------------------------------------------

    /** The scoring weights, as the engine wants them. */
    public static RarityWeights weights() {
        return new RarityWeights(weightRawMaterialBase, weightBulkCompression, weightCraftCost,
            weightDepth, weightVariety, weightUnstackable, weightDurability, weightEnchantability,
            weightVanillaRarity, weightBlock, weightContainerItem, maxRecipeDepth);
    }

    /** The loot generosity rules, as the roller wants them. */
    public static LootRules lootRules() {
        return new LootRules(lootMinRolls, lootMaxRolls, lootMaxStackPerRoll,
            lootBleedDownChance, lootBleedUpChance);
    }

    /** The parsed override table. */
    public static RarityOverrides overrides() {
        return RarityOverrides.parse(rarityOverrides);
    }

    /** The parsed declared-material-value table. */
    public static MaterialScores declaredMaterials() {
        return MaterialScores.parse(materialScores);
    }

    /** The parsed loot blacklist. */
    public static KeyFilter blacklist() {
        return KeyFilter.parse(lootBlacklist);
    }

    /** Chance per chunk for one tier, safe against a short or absent config array. */
    public static double spawnChance(Rarity tier) {
        int index = tier.ordinal();
        if (spawnChancePerChunk == null || index >= spawnChancePerChunk.length) {
            return 0.0D;
        }
        return spawnChancePerChunk[index];
    }

    /**
     * Whether {@code tableName} is one of the tables boxes should be added to.
     *
     * <p><b>An entry ending in {@code /} is a prefix; anything else must match exactly.</b> Treating
     * every entry as a prefix looks equivalent and is not: {@code minecraft:chests/jungle_temple}
     * is a prefix of {@code minecraft:chests/jungle_temple_dispenser}, so listing the temple's chest
     * silently added loot boxes to the temple's <i>arrow trap</i> as well. Every table whose name
     * happens to extend another one has the same problem, and a pack author naming one table has
     * plainly not consented to its neighbours.</p>
     *
     * <p>Comment and blank lines are skipped, so the config's inline documentation cannot
     * accidentally become a target.</p>
     */
    public static boolean injectsInto(String tableName) {
        if (lootTableTargets == null || tableName == null) {
            return false;
        }
        for (String target : lootTableTargets) {
            if (target == null) {
                continue;
            }
            String trimmed = target.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
                continue;
            }
            boolean matched = trimmed.endsWith("/")
                ? tableName.startsWith(trimmed)
                : tableName.equals(trimmed);
            if (matched) {
                return true;
            }
        }
        return false;
    }

    /** Whether boxes may generate in {@code dimensionId}. */
    public static boolean generatesIn(int dimensionId) {
        if (worldGenDimensions == null) {
            return false;
        }
        for (int allowed : worldGenDimensions) {
            if (allowed == dimensionId) {
                return true;
            }
        }
        return false;
    }
}
