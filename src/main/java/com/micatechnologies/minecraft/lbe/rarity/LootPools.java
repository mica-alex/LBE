package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

/**
 * What can actually be drawn from each tier, and how often: the {@link RarityTable}'s tier lists
 * with a draw weight on every item.
 *
 * <p>Weights change the pick <b>within</b> a tier and nothing else. A heavily weighted loaf of bread
 * is still a common item and still only comes out of the common pool, so the rarity model and
 * every box's tier mix are untouched; what changes is which common item a common slot lands on.
 * That is the whole point: a pack with thousands of decorative blocks can make food and tools come
 * up more often without blacklisting every other mod.</p>
 *
 * <p>Built once per catalogue rebuild, as a cumulative-weight array per tier, so a draw is a binary
 * search rather than a walk. An item with weight 0 is left out of its tier entirely, and a tier
 * whose items all weigh 0 counts as empty, so the draw walks down past it exactly as it does for a
 * tier with nothing in it.</p>
 */
public final class LootPools {

    private final Map<Rarity, Pool> pools;
    private final Map<String, Double> weights;
    private final Map<String, Integer> stackCaps;

    private LootPools(Map<Rarity, Pool> pools, Map<String, Double> weights,
                      Map<String, Integer> stackCaps) {
        this.pools = pools;
        this.weights = weights;
        this.stackCaps = stackCaps;
    }

    /** Every item at weight 1, no stack caps: the plain uniform pick. */
    public static LootPools uniform(RarityTable table) {
        return build(table, null, null, null);
    }

    /**
     * @param table        the tiers
     * @param categoriesOf an item's {@link ItemCategory} ids; {@code null} means none for anything
     * @param weights      {@code loot.lootWeights}; {@code null} means every item weighs 1
     * @param stackCaps    {@code loot.categoryMaxStack}; {@code null} means the tier caps apply
     */
    public static LootPools build(RarityTable table,
                                  Function<String, ? extends Collection<String>> categoriesOf,
                                  ItemPatternTable weights, ItemPatternTable stackCaps) {
        Map<Rarity, Pool> pools = new EnumMap<>(Rarity.class);
        Map<String, Double> drawable = new HashMap<>();
        Map<String, Integer> caps = new HashMap<>();
        for (Rarity tier : Rarity.values()) {
            List<String> items = table == null ? Collections.<String>emptyList() : table.itemsOf(tier);
            List<String> keys = new ArrayList<>(items.size());
            double[] cumulative = new double[items.size()];
            double total = 0.0D;
            for (String key : items) {
                Collection<String> categories = categoriesOf == null ? null : categoriesOf.apply(key);
                double weight = weights == null ? 1.0D : weights.weightOf(key, categories);
                if (weight <= 0.0D) {
                    continue;
                }
                total += weight;
                cumulative[keys.size()] = total;
                keys.add(key);
                drawable.put(key, weight);
                if (stackCaps != null) {
                    Integer cap = stackCaps.capOf(key, categories);
                    if (cap != null) {
                        caps.put(key, cap);
                    }
                }
            }
            pools.put(tier, new Pool(keys, Arrays.copyOf(cumulative, keys.size()), items.size()));
        }
        return new LootPools(pools, drawable, caps);
    }

    /**
     * Draw one item from a tier, walking <b>down</b> to a drawable tier if that one has nothing to
     * give.
     *
     * <p>Down rather than up because the failure mode should be a disappointing box, never a free
     * jackpot. See {@link LootRoller}.</p>
     *
     * @return the item key, or {@code null} if no tier at or below {@code tier} can give anything
     */
    public String draw(Rarity tier, Random random) {
        for (int ordinal = tier.ordinal(); ordinal >= 0; ordinal--) {
            Pool pool = pools.get(Rarity.values()[ordinal]);
            if (pool != null && !pool.keys.isEmpty()) {
                return pool.draw(random);
            }
        }
        return null;
    }

    /**
     * The largest pile one entry of {@code key} may be: its {@code categoryMaxStack} cap if it has
     * one, otherwise {@code tierCap}. The item's own max stack size is applied later, on the game
     * side.
     */
    public int maxStackFor(String key, int tierCap) {
        Integer cap = stackCaps.get(key);
        return cap == null ? tierCap : cap;
    }

    /** The draw weight of {@code key}, or 0 if it cannot be drawn (weight 0, or not in the table). */
    public double weightOf(String key) {
        Double weight = weights.get(key);
        return weight == null ? 0.0D : weight;
    }

    /** How many items in {@code tier} can be drawn, i.e. carry a weight above 0. */
    public int drawableCount(Rarity tier) {
        Pool pool = pools.get(tier);
        return pool == null ? 0 : pool.keys.size();
    }

    /** How many items the table put in {@code tier}, drawable or not. */
    public int tierSize(Rarity tier) {
        Pool pool = pools.get(tier);
        return pool == null ? 0 : pool.tierSize;
    }

    /**
     * The {@code limit} items in {@code tier} most likely to be drawn, each with its share of the
     * tier's draw. Backs {@code /lbe weights}.
     */
    public List<Share> topShares(Rarity tier, int limit) {
        Pool pool = pools.get(tier);
        List<Share> shares = new ArrayList<>();
        if (pool == null || pool.keys.isEmpty()) {
            return shares;
        }
        double total = pool.cumulative[pool.cumulative.length - 1];
        for (int i = 0; i < pool.keys.size(); i++) {
            double weight = pool.cumulative[i] - (i == 0 ? 0.0D : pool.cumulative[i - 1]);
            shares.add(new Share(pool.keys.get(i), weight, weight / total));
        }
        shares.sort((a, b) -> Double.compare(b.weight, a.weight));
        return shares.size() > limit ? new ArrayList<>(shares.subList(0, limit)) : shares;
    }

    /** One item's place in its tier's draw. */
    public static final class Share {

        private final String key;
        private final double weight;
        private final double fraction;

        Share(String key, double weight, double fraction) {
            this.key = key;
            this.weight = weight;
            this.fraction = fraction;
        }

        public String key() {
            return key;
        }

        public double weight() {
            return weight;
        }

        /** The chance one draw from this tier lands on this item, 0 to 1. */
        public double fraction() {
            return fraction;
        }
    }

    private static final class Pool {

        private final List<String> keys;
        private final double[] cumulative;
        private final int tierSize;

        Pool(List<String> keys, double[] cumulative, int tierSize) {
            this.keys = keys;
            this.cumulative = cumulative;
            this.tierSize = tierSize;
        }

        String draw(Random random) {
            double target = random.nextDouble() * cumulative[cumulative.length - 1];
            int index = Arrays.binarySearch(cumulative, target);
            // Not found gives -(insertion point) - 1, and the insertion point is the first entry
            // above the target, which is the one whose range holds it. An exact hit sits on a
            // range's upper edge, which belongs to the next entry.
            index = index < 0 ? -index - 1 : index + 1;
            return keys.get(Math.min(index, keys.size() - 1));
        }
    }
}
