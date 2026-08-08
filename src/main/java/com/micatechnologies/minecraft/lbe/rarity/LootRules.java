package com.micatechnologies.minecraft.lbe.rarity;

/**
 * What opening a box of a given tier is worth: how many things fall out, how big the piles are, and
 * how often a box hands out something from a neighbouring tier.
 *
 * <p>Immutable, Minecraft-free, and fed straight from the config. Separate from
 * {@link RarityWeights} because the two answer genuinely different questions — the weights decide
 * <i>what tier an item is</i>, and these decide <i>what a box of that tier gives you</i>. Conflating
 * them is how you end up unable to make legendary boxes more generous without also reclassifying
 * every item in the pack.</p>
 */
public final class LootRules {

    private final int[] minRolls;
    private final int[] maxRolls;
    private final int[] maxStackPerRoll;
    private final double bleedDownChance;
    private final double bleedUpChance;

    /**
     * @param minRolls        per tier (indexed by {@link Rarity#ordinal()}), the fewest item entries
     *                        a box of that tier produces
     * @param maxRolls        per tier, the most
     * @param maxStackPerRoll per tier, the largest pile a single entry may be — capped again at the
     *                        item's own max stack size when the roll is realised. This is what stops
     *                        a legendary box handing out 32 of something: the tier caps it at 1
     * @param bleedDownChance probability that any one entry is drawn from the tier <i>below</i> the
     *                        box's. Keeps high-tier boxes from feeling like a list of trophies with
     *                        nothing ordinary in it, and is the main lever on how generous the mod
     *                        feels overall
     * @param bleedUpChance   probability that any one entry is drawn from the tier <i>above</i>.
     *                        The jackpot. Small by default: the entire point of a tiered box is that
     *                        the tier means something, and a common box that regularly pays out
     *                        legendary loot has quietly abolished its own ladder
     */
    public LootRules(int[] minRolls, int[] maxRolls, int[] maxStackPerRoll,
                     double bleedDownChance, double bleedUpChance) {
        int tiers = Rarity.values().length;
        this.minRolls = fit(minRolls, tiers, 1);
        this.maxRolls = fit(maxRolls, tiers, 1);
        this.maxStackPerRoll = fit(maxStackPerRoll, tiers, 1);
        for (int i = 0; i < tiers; i++) {
            this.minRolls[i] = Math.max(0, this.minRolls[i]);
            // A max below the min is a config typo that would otherwise produce a negative range and
            // an exception inside a chunk-generation callback. Widen instead.
            this.maxRolls[i] = Math.max(this.minRolls[i], this.maxRolls[i]);
            this.maxStackPerRoll[i] = Math.max(1, this.maxStackPerRoll[i]);
        }
        this.bleedDownChance = clamp01(bleedDownChance);
        this.bleedUpChance = clamp01(bleedUpChance);
    }

    /**
     * The shipped defaults. A common box is a handful of ordinary stuff; a legendary box is one or
     * two things that matter.
     *
     * <pre>
     *   COMMON     3–5 entries, up to 16 per pile
     *   UNCOMMON   3–4 entries, up to  8 per pile
     *   RARE       2–3 entries, up to  4 per pile
     *   LEGENDARY  1–2 entries, up to  1 per pile
     * </pre>
     */
    public static LootRules defaults() {
        return new LootRules(
            new int[] { 3, 3, 2, 1 },
            new int[] { 5, 4, 3, 2 },
            new int[] { 16, 8, 4, 1 },
            0.25D,
            0.04D);
    }

    /** Fewest entries a box of {@code tier} produces. */
    public int minRolls(Rarity tier) {
        return minRolls[tier.ordinal()];
    }

    /** Most entries a box of {@code tier} produces. */
    public int maxRolls(Rarity tier) {
        return maxRolls[tier.ordinal()];
    }

    /** Largest pile one entry from {@code tier} may be, before the item's own stack limit applies. */
    public int maxStackPerRoll(Rarity tier) {
        return maxStackPerRoll[tier.ordinal()];
    }

    /** Chance any one entry drops a tier. */
    public double bleedDownChance() {
        return bleedDownChance;
    }

    /** Chance any one entry gains a tier. */
    public double bleedUpChance() {
        return bleedUpChance;
    }

    private static int[] fit(int[] source, int length, int fallback) {
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            out[i] = (source != null && i < source.length) ? source[i] : fallback;
        }
        return out;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return value < 0.0D ? 0.0D : (value > 1.0D ? 1.0D : value);
    }
}
