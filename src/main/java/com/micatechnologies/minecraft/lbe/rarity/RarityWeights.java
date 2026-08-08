package com.micatechnologies.minecraft.lbe.rarity;

/**
 * Every tunable coefficient in the scoring model, in one immutable object.
 *
 * <p>Bundled rather than spread across static fields for two reasons. A unit test can build a
 * weighting that isolates one term (set the rest to zero) and assert on it exactly. And the config
 * can hand the scorer a fresh instance on reload without any code path having to remember to update
 * a static — which is the mistake that makes half a mod pick up a config change and the other half
 * keep the old value.</p>
 *
 * <p>The defaults are the interesting part; each is justified on its field. They were chosen to put
 * vanilla in the right order first (cobblestone below iron below diamond below beacon), on the
 * theory that a model that cannot rank the game everyone knows has no business ranking a modpack
 * nobody has seen.</p>
 *
 * @see RarityScorer for how these combine
 */
public final class RarityWeights {

    /**
     * The score of a raw material — anything with no recipe at all. The floor everything else is
     * measured up from.
     *
     * <p>Nonzero on purpose. At zero, an item crafted from four raw materials would score zero too,
     * since the craft term multiplies the ingredient total, and the entire recipe tree would collapse
     * to a flat line. This is the unit the rest of the model is denominated in.</p>
     */
    public final double rawMaterialBase;

    /**
     * Exponent applied to a recipe's total ingredient cost. <b>Below 1, and that matters more than
     * any other single number here.</b>
     *
     * <p>At 1.0 the model is linear in quantity, and a block of iron — nine ingots, one step — scores
     * nine times an ingot and lands in the top tier, alongside things that took a nether trip and a
     * dozen intermediate crafts. That is wrong in a way players notice immediately: a storage block
     * is not a rare item, it is a tidy pile of a common one. Compressing the sum says bulk is worth
     * less than progression, which is what a loot box should reward.</p>
     *
     * <p>0.75 puts a block of iron a little above an ingot and a long way below a diamond pickaxe.</p>
     *
     * <p>Note that compression is <b>floored at the dearest single ingredient</b>, so it can never
     * make a crafted item worth less than the best thing that went into it. That floor is not a
     * tunable and is not affected by this exponent — see
     * {@code RarityScorer.craftCost(double, double, int)}.</p>
     */
    public final double bulkCompression;

    /**
     * Multiplier on the compressed ingredient cost. Scales the "what it is made of" term against the
     * per-item property terms below.
     */
    public final double craftCostWeight;

    /**
     * Weight on {@code ln(1 + depth)}, where depth is how many crafting steps separate an item from
     * raw materials.
     *
     * <p>Logarithmic rather than linear: the difference between a raw material and a once-crafted
     * item is a real milestone, the difference between the eleventh and twelfth step of a tech mod's
     * chain is not, and a linear term would let a mod with a deliberately long chain of cheap
     * intermediates dominate the top tier by sheer length.</p>
     */
    public final double depthWeight;

    /**
     * Weight per <i>distinct</i> ingredient kind in a recipe.
     *
     * <p>This is the term that separates "eight of one thing" from "eight different things" — the
     * second is a milestone recipe and the first is a shape. See
     * {@link CraftingRecipe#distinctIngredientCount()}.</p>
     */
    public final double varietyWeight;

    /**
     * Flat bonus for an item the game will not let you stack.
     *
     * <p>Minecraft's own strongest signal that an item is individually significant, and it costs
     * nothing to read. Tools, armour, and nearly every modded machine are unstackable; nearly no bulk
     * material is.</p>
     */
    public final double unstackableWeight;

    /** Weight on {@code ln(1 + maxDurability)}. A tool that lasts longer is a better tool. */
    public final double durabilityWeight;

    /**
     * Weight per point of enchantability.
     *
     * <p>Small, and deliberately so. Enchantability tracks tier only loosely and not monotonically —
     * gold is 22 and diamond is 10 — so a large weight here would confidently promote gold tools
     * above diamond ones. It is a tiebreaker, not a signal.</p>
     */
    public final double enchantabilityWeight;

    /**
     * Weight per {@code EnumRarity} ordinal the item reports for itself.
     *
     * <p>The largest per-property weight in the model, because it is the only one that is not an
     * inference: an author who set {@code EnumRarity.EPIC} on their item has stated their opinion of
     * its tier, and they know their mod's progression better than a recipe walk does.</p>
     */
    public final double vanillaRarityWeight;

    /**
     * Applied to items that are blocks. <b>Negative by default.</b>
     *
     * <p>Blocks skew heavily toward bulk building material — the median block in any pack is
     * something you want a stack of, not one of. The genuinely valuable blocks (beacons, end-game
     * machines) clear this penalty easily on their recipe cost, which is the intended behaviour: the
     * penalty is a prior, and evidence overrides it.</p>
     */
    public final double blockWeight;

    /**
     * Bonus for an item that survives being crafted with — buckets, and a good many modded tools.
     *
     * <p>Reusability is real value that the recipe cost misses entirely: a bucket is three iron
     * ingots and infinitely many uses.</p>
     */
    public final double containerItemWeight;

    /**
     * Hard cap on recursion depth through the recipe graph.
     *
     * <p>A safety limit, not a tuning knob. Modded recipe graphs contain cycles that no cycle check
     * catches cheaply (A needs B needs C needs A, across three mods and a machine), and some tech
     * packs have genuinely deep chains. Hitting the cap scores the remainder as raw, which
     * understates the item — an acceptable failure, and a far better one than a stack overflow
     * during postInit.</p>
     */
    public final int maxRecipeDepth;

    /**
     * Default weighting. Tuned so vanilla comes out in the order a player would put it in.
     *
     * @see #RarityWeights(double, double, double, double, double, double, double, double, double, double, double, int)
     */
    public RarityWeights() {
        this(1.0D, 0.75D, 1.0D, 3.0D, 0.35D, 1.5D, 0.6D, 0.02D, 4.0D, -0.75D, 1.0D, 24);
    }

    public RarityWeights(double rawMaterialBase, double bulkCompression, double craftCostWeight,
                         double depthWeight, double varietyWeight, double unstackableWeight,
                         double durabilityWeight, double enchantabilityWeight,
                         double vanillaRarityWeight, double blockWeight, double containerItemWeight,
                         int maxRecipeDepth) {
        this.rawMaterialBase = rawMaterialBase;
        // Clamped rather than validated: these arrive straight from a hand-edited config file, and a
        // pack author who types 2.0 for the compression exponent should get a linear model and a
        // slightly odd loot table, not a crash on world load. 1.0 is the highest value that still
        // means anything (above it, quantity would beat progression outright).
        this.bulkCompression = clamp(bulkCompression, 0.0D, 1.0D);
        this.craftCostWeight = craftCostWeight;
        this.depthWeight = depthWeight;
        this.varietyWeight = varietyWeight;
        this.unstackableWeight = unstackableWeight;
        this.durabilityWeight = durabilityWeight;
        this.enchantabilityWeight = enchantabilityWeight;
        this.vanillaRarityWeight = vanillaRarityWeight;
        this.blockWeight = blockWeight;
        this.containerItemWeight = containerItemWeight;
        this.maxRecipeDepth = Math.max(1, maxRecipeDepth);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    @Override
    public String toString() {
        return "RarityWeights[raw=" + rawMaterialBase + ", bulk^" + bulkCompression
            + ", craft=" + craftCostWeight + ", depth=" + depthWeight + ", variety=" + varietyWeight
            + ", unstackable=" + unstackableWeight + ", durability=" + durabilityWeight
            + ", ench=" + enchantabilityWeight + ", vanillaRarity=" + vanillaRarityWeight
            + ", block=" + blockWeight + ", container=" + containerItemWeight
            + ", maxDepth=" + maxRecipeDepth + "]";
    }
}
