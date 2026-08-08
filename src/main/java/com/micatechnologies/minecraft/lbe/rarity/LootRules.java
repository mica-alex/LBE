package com.micatechnologies.minecraft.lbe.rarity;

/**
 * What opening a box of a given tier is worth: how many things fall out, how many of those are
 * <b>at the box's own tier</b>, how big the piles are, and how often a box pays out above itself.
 *
 * <h2>Features and filler</h2>
 *
 * <p>A box's contents are two different things wearing one name, and conflating them is a trap:</p>
 *
 * <ul>
 *   <li><b>Feature items</b> — guaranteed to be drawn at the box's own tier. This is what the tier on
 *       the lid actually promises. A legendary box guarantees one or two genuinely legendary things.</li>
 *   <li><b>Filler</b> — everything else, drawn from the tiers <i>below</i>. This is what stops a box
 *       from feeling thin.</li>
 * </ul>
 *
 * <p>Keeping them separate is the whole point. With a single "how many items" number, the only way to
 * make a legendary box feel exclusive is to make it hand over one item — so the rarest box in the game
 * becomes the emptiest one, which is precisely backwards. Splitting them lets a legendary box be
 * <i>exclusive and generous at once</i>: two legendary items plus a couple of rare ones, rather than
 * two legendary items and nothing else.</p>
 *
 * <p>Immutable, Minecraft-free, and fed straight from the config. Separate from {@link RarityWeights}
 * because the two answer genuinely different questions — the weights decide <i>what tier an item
 * is</i>, and these decide <i>what a box of that tier gives you</i>. Conflating those would make it
 * impossible to be more generous without also reclassifying every item in the pack.</p>
 */
public final class LootRules {

    private final int[] minItems;
    private final int[] maxItems;
    private final int[] minFeatures;
    private final int[] maxFeatures;
    private final int[] maxStackPerRoll;
    private final double fillerFalloff;
    private final double bleedUpChance;

    /**
     * @param minItems        per tier ({@link Rarity#ordinal()}), fewest total items a box gives
     * @param maxItems        per tier, most total items
     * @param minFeatures     per tier, fewest items guaranteed at the box's own tier
     * @param maxFeatures     per tier, most items at the box's own tier
     * @param maxStackPerRoll per <b>drawn</b> tier, the largest pile one entry may be — capped again
     *                        at the item's own max stack size when the roll is realised. Indexed by
     *                        the tier the item came from, not the box's, so a common filler item can
     *                        arrive as a stack of sixteen inside a legendary box while the legendary
     *                        item beside it arrives as one
     * @param fillerFalloff   chance that a filler item drops one tier further than the tier below the
     *                        box. Applied repeatedly, so most filler is one tier down, some is two,
     *                        and very little is further. At 0 all filler sits exactly one tier below
     * @param bleedUpChance   probability that a <i>feature</i> slot is drawn from the tier
     *                        <b>above</b> the box's. The jackpot. Small by default: the entire point
     *                        of a tiered box is that the tier means something, and a common box that
     *                        regularly pays out legendary loot has quietly abolished its own ladder
     */
    public LootRules(int[] minItems, int[] maxItems, int[] minFeatures, int[] maxFeatures,
                     int[] maxStackPerRoll, double fillerFalloff, double bleedUpChance) {
        int tiers = Rarity.values().length;
        this.minItems = fit(minItems, tiers, 1);
        this.maxItems = fit(maxItems, tiers, 1);
        this.minFeatures = fit(minFeatures, tiers, 1);
        this.maxFeatures = fit(maxFeatures, tiers, 1);
        this.maxStackPerRoll = fit(maxStackPerRoll, tiers, 1);

        for (int i = 0; i < tiers; i++) {
            // Every one of these is a config typo waiting to happen, and every one of them would
            // otherwise surface as a negative bound inside a chunk-generation or block-interaction
            // callback. Widen rather than throw.
            this.minFeatures[i] = Math.max(0, this.minFeatures[i]);
            this.maxFeatures[i] = Math.max(this.minFeatures[i], this.maxFeatures[i]);
            this.minItems[i] = Math.max(0, this.minItems[i]);
            this.maxItems[i] = Math.max(this.minItems[i], this.maxItems[i]);
            // A box cannot contain fewer items than it guarantees at its own tier. If someone asks
            // for three features and two items, they get three items — honouring the guarantee is
            // less surprising than silently dropping one of the good ones.
            this.maxItems[i] = Math.max(this.maxItems[i], this.maxFeatures[i]);
            this.minItems[i] = Math.max(this.minItems[i], this.minFeatures[i]);
            this.maxStackPerRoll[i] = Math.max(1, this.maxStackPerRoll[i]);
        }
        this.fillerFalloff = clamp01(fillerFalloff);
        this.bleedUpChance = clamp01(bleedUpChance);
    }

    /**
     * The shipped defaults. Every box hands over roughly four things; what changes with the tier is
     * <b>how many of those four are worth having</b>.
     *
     * <pre>
     *   tier       total   at-tier   max pile per at-tier item
     *   COMMON      4–6      2–3      16
     *   UNCOMMON    4–5      2–2       8
     *   RARE        4–5      1–2       4
     *   LEGENDARY   4–5      1–2       1
     * </pre>
     */
    public static LootRules defaults() {
        return new LootRules(
            new int[] { 4, 4, 4, 4 },
            new int[] { 6, 5, 5, 5 },
            new int[] { 2, 2, 1, 1 },
            new int[] { 3, 2, 2, 2 },
            new int[] { 16, 8, 4, 1 },
            0.35D,
            0.04D);
    }

    /** Fewest total items a box of {@code tier} gives. */
    public int minItems(Rarity tier) {
        return minItems[tier.ordinal()];
    }

    /** Most total items a box of {@code tier} gives. */
    public int maxItems(Rarity tier) {
        return maxItems[tier.ordinal()];
    }

    /** Fewest items guaranteed at {@code tier} itself. */
    public int minFeatures(Rarity tier) {
        return minFeatures[tier.ordinal()];
    }

    /** Most items at {@code tier} itself. */
    public int maxFeatures(Rarity tier) {
        return maxFeatures[tier.ordinal()];
    }

    /** Largest pile an item <b>drawn from</b> {@code tier} may be, before its own stack limit. */
    public int maxStackPerRoll(Rarity tier) {
        return maxStackPerRoll[tier.ordinal()];
    }

    /** Chance a filler item drops one tier further down. */
    public double fillerFalloff() {
        return fillerFalloff;
    }

    /** Chance a feature slot is drawn from the tier above. */
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
