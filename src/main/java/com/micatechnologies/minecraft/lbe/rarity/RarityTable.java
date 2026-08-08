package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The finished answer: every item, assigned to a tier.
 *
 * <h2>Why percentiles rather than fixed score thresholds</h2>
 *
 * <p>A score of 12.4 means nothing on its own. In vanilla it is near the top; in a kitchen-sink pack
 * with a deep tech chain it is somewhere in the lower middle. Fixed thresholds would therefore
 * behave completely differently from pack to pack — and would need retuning by hand every time
 * someone added a mod, which is exactly the manual work this mod exists to avoid.</p>
 *
 * <p>Cutting by <b>percentile of the scored population</b> makes the tiers self-normalising. "The top
 * 2.5% of what is installed" is legendary whether that population is vanilla's 400 items or a
 * 12,000-item pack's, and adding a mod redistributes the tiers automatically rather than silently
 * flooding the top one.</p>
 *
 * <h2>Overridden items are excluded from the population</h2>
 *
 * <p>An item whose tier the config dictates does not vote on where the cut points fall. Otherwise a
 * pack author declaring fifty items legendary would drag every unrelated item's tier down with them —
 * an override would have quiet action-at-a-distance over items it never named, which is not what
 * anyone means by "override". They are assigned their forced tier and take no further part.</p>
 */
public final class RarityTable {

    /**
     * Default percentile cut points, one fewer than there are tiers.
     *
     * <p>{@code 0.60 / 0.88 / 0.975} — the bottom 60% common, the next 28% uncommon, the next 9.5%
     * rare, the top 2.5% legendary. Deliberately steep at the top: a legendary tier holding one item
     * in ten is not legendary, it is just the fourth tier. This shape means a 12,000-item pack still
     * ends up with a few hundred genuinely top-end items rather than a thousand.</p>
     */
    public static final double[] DEFAULT_PERCENTILE_CUTS = { 0.60D, 0.88D, 0.975D };

    private final Map<String, Rarity> tiers;
    private final Map<Rarity, List<String>> byTier;
    private final double[] scoreThresholds;
    private final int scoredPopulation;
    private final int overriddenCount;

    private RarityTable(Map<String, Rarity> tiers, Map<Rarity, List<String>> byTier,
                        double[] scoreThresholds, int scoredPopulation, int overriddenCount) {
        this.tiers = Collections.unmodifiableMap(tiers);
        this.byTier = Collections.unmodifiableMap(byTier);
        this.scoreThresholds = scoreThresholds;
        this.scoredPopulation = scoredPopulation;
        this.overriddenCount = overriddenCount;
    }

    /**
     * Bucket a scored population into tiers.
     *
     * @param scores          every candidate item and its score, from {@link RarityScorer#scoreAll()}
     * @param percentileCuts  {@code Rarity.values().length - 1} ascending fractions in {@code (0,1)};
     *                        {@code null} uses {@link #DEFAULT_PERCENTILE_CUTS}
     * @param overrides       forced tiers; {@code null} means none
     */
    public static RarityTable build(Map<String, Double> scores, double[] percentileCuts,
                                    RarityOverrides overrides) {
        RarityOverrides forced = overrides == null ? RarityOverrides.empty() : overrides;
        double[] cuts = sanitiseCuts(percentileCuts);

        // Pass 1: split the population. Overridden items are assigned immediately and take no part
        // in where the cut points land — see the class doc for why that matters.
        Map<String, Rarity> tiers = new LinkedHashMap<>(Math.max(16, scores.size() * 2));
        List<Double> scoredValues = new ArrayList<>(scores.size());
        int overriddenCount = 0;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            Rarity override = forced.forKey(entry.getKey());
            if (override != null) {
                tiers.put(entry.getKey(), override);
                overriddenCount++;
            }
            else {
                scoredValues.add(entry.getValue());
            }
        }

        // Pass 2: turn percentiles into actual score thresholds.
        double[] thresholds = thresholdsFor(scoredValues, cuts);

        // Pass 3: assign everything that wasn't overridden.
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            if (!tiers.containsKey(entry.getKey())) {
                tiers.put(entry.getKey(), tierForScore(entry.getValue(), thresholds));
            }
        }

        Map<Rarity, List<String>> byTier = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            byTier.put(rarity, new ArrayList<String>());
        }
        for (Map.Entry<String, Rarity> entry : tiers.entrySet()) {
            byTier.get(entry.getValue()).add(entry.getKey());
        }
        for (Rarity rarity : Rarity.values()) {
            byTier.put(rarity, Collections.unmodifiableList(byTier.get(rarity)));
        }

        return new RarityTable(tiers, byTier, thresholds, scoredValues.size(), overriddenCount);
    }

    /** An empty table — every lookup returns {@link Rarity#COMMON}, every tier list is empty. */
    public static RarityTable empty() {
        Map<Rarity, List<String>> byTier = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            byTier.put(rarity, Collections.<String>emptyList());
        }
        return new RarityTable(new LinkedHashMap<String, Rarity>(), byTier,
            new double[Rarity.values().length - 1], 0, 0);
    }

    /** The tier of {@code key}, or {@link Rarity#COMMON} for an item this table has never seen. */
    public Rarity tierOf(String key) {
        Rarity tier = tiers.get(key);
        return tier == null ? Rarity.lowest() : tier;
    }

    /** {@code true} if this table has an entry for {@code key}. */
    public boolean contains(String key) {
        return tiers.containsKey(key);
    }

    /** Every item in a tier, in the order the scored population was iterated. Never {@code null}. */
    public List<String> itemsOf(Rarity rarity) {
        List<String> items = byTier.get(rarity);
        return items == null ? Collections.<String>emptyList() : items;
    }

    /** Every item, with its tier. */
    public Map<String, Rarity> all() {
        return tiers;
    }

    /** How many items this table covers, overrides included. */
    public int size() {
        return tiers.size();
    }

    /** How many items had their tier forced by the config. */
    public int overriddenCount() {
        return overriddenCount;
    }

    /** How many items the percentile cuts were computed from (i.e. excluding overrides). */
    public int scoredPopulation() {
        return scoredPopulation;
    }

    /**
     * The score cut points the percentiles resolved to, ascending. Useful in a startup log line —
     * they are the one number that tells a pack author whether their pack scored the way they
     * expected.
     */
    public double[] scoreThresholds() {
        return scoreThresholds.clone();
    }

    // --- internals -------------------------------------------------------------------------------

    /**
     * Force the cut points into something usable: right length, ascending, strictly inside
     * {@code (0,1)}.
     *
     * <p>Clamped rather than rejected because these come from a hand-edited config. An author who
     * writes {@code 0.9, 0.8, 0.99} has made a mistake, but the right response is a sensible loot
     * table, not a refusal to load the world.</p>
     */
    private static double[] sanitiseCuts(double[] requested) {
        int needed = Rarity.values().length - 1;
        double[] cuts = new double[needed];
        for (int i = 0; i < needed; i++) {
            double value = (requested != null && i < requested.length)
                ? requested[i] : DEFAULT_PERCENTILE_CUTS[i];
            if (value <= 0.0D || value >= 1.0D || Double.isNaN(value)) {
                value = DEFAULT_PERCENTILE_CUTS[i];
            }
            cuts[i] = value;
        }
        Arrays.sort(cuts);
        return cuts;
    }

    /** Resolve percentile positions to the score values at those positions. */
    private static double[] thresholdsFor(List<Double> scoredValues, double[] cuts) {
        double[] thresholds = new double[cuts.length];
        if (scoredValues.isEmpty()) {
            // Nothing scored — every threshold is +inf, so everything that arrives later is COMMON.
            Arrays.fill(thresholds, Double.POSITIVE_INFINITY);
            return thresholds;
        }
        double[] sorted = new double[scoredValues.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = scoredValues.get(i);
        }
        Arrays.sort(sorted);
        for (int i = 0; i < cuts.length; i++) {
            int index = (int) Math.floor(cuts[i] * (sorted.length - 1));
            thresholds[i] = sorted[Math.min(sorted.length - 1, Math.max(0, index))];
        }
        return thresholds;
    }

    /**
     * The tier a score falls into: one step up per threshold it strictly exceeds.
     *
     * <p>Strictly, so that a run of identical scores — very common, since a pack full of "9 of X
     * makes a block of X" recipes produces plenty of ties — all land in the same tier instead of
     * being split arbitrarily by their position in the sorted array.</p>
     */
    private static Rarity tierForScore(double score, double[] thresholds) {
        int tier = 0;
        for (double threshold : thresholds) {
            if (score > threshold) {
                tier++;
            }
        }
        return Rarity.values()[Math.min(tier, Rarity.values().length - 1)];
    }
}
