package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides what comes out of a box. Still Minecraft-free — it produces {@link RolledEntry item key +
 * count} pairs, and the game side turns those into {@code ItemStack}s.
 *
 * <p>Keeping the draw here rather than in the block means the interesting question — "does a
 * legendary box actually feel different from a common one?" — is a unit test over a seeded
 * {@link Random} rather than something you find out by opening two hundred boxes in a dev world.</p>
 */
public final class LootRoller {

    private LootRoller() {
        throw new AssertionError("No instances.");
    }

    /**
     * Roll the contents of one box.
     *
     * @param boxTier the box's own tier
     * @param table   the scored catalogue to draw from
     * @param rules   how generous to be
     * @param random  the source of randomness; seed it per box if you want stable contents
     * @return the entries, possibly empty if the table has nothing to give
     */
    public static List<RolledEntry> roll(Rarity boxTier, RarityTable table, LootRules rules,
                                         Random random) {
        List<RolledEntry> rolled = new ArrayList<>();
        if (table == null || table.size() == 0) {
            return rolled;
        }
        int min = rules.minRolls(boxTier);
        int max = rules.maxRolls(boxTier);
        int entries = min + (max > min ? random.nextInt(max - min + 1) : 0);

        for (int i = 0; i < entries; i++) {
            Rarity drawTier = shiftTier(boxTier, rules, random);
            String key = drawFrom(table, drawTier, random);
            if (key == null) {
                continue;
            }
            int count = 1 + random.nextInt(Math.max(1, rules.maxStackPerRoll(drawTier)));
            rolled.add(new RolledEntry(key, count, drawTier));
        }
        return rolled;
    }

    /**
     * Apply the bleed-up/bleed-down chances to one draw.
     *
     * <p>Up is checked first so that when both rolls would succeed the player gets the good outcome.
     * That is a deliberate thumb on the scale — the two chances are independent, both are small, and
     * on the rare occasion they collide there is no reason to hand the player the worse of the two.</p>
     */
    private static Rarity shiftTier(Rarity boxTier, LootRules rules, Random random) {
        if (random.nextDouble() < rules.bleedUpChance() && boxTier != Rarity.highest()) {
            return Rarity.values()[boxTier.ordinal() + 1];
        }
        if (random.nextDouble() < rules.bleedDownChance() && boxTier != Rarity.lowest()) {
            return Rarity.values()[boxTier.ordinal() - 1];
        }
        return boxTier;
    }

    /**
     * Pick one item uniformly from a tier, walking <b>down</b> to a populated tier if that one is
     * empty.
     *
     * <p>An empty tier is not a hypothetical. A tiny pack, or an aggressive blacklist, or a config
     * that put every cut point close together can all leave a tier with nothing in it — and a box
     * that silently produces nothing reads as a bug to a player. Walking down rather than up means
     * the failure mode is a disappointing box, never a free jackpot.</p>
     */
    private static String drawFrom(RarityTable table, Rarity tier, Random random) {
        for (int ordinal = tier.ordinal(); ordinal >= 0; ordinal--) {
            List<String> pool = table.itemsOf(Rarity.values()[ordinal]);
            if (!pool.isEmpty()) {
                return pool.get(random.nextInt(pool.size()));
            }
        }
        return null;
    }

    /** One line of a rolled box: which item, how many, and the tier it was drawn from. */
    public static final class RolledEntry {

        private final String key;
        private final int count;
        private final Rarity drawnFrom;

        public RolledEntry(String key, int count, Rarity drawnFrom) {
            this.key = key;
            this.count = count;
            this.drawnFrom = drawnFrom;
        }

        /** The item key, {@code modid:name#meta}. */
        public String key() {
            return key;
        }

        /**
         * How many, before the item's real max stack size is applied. The game side clamps this —
         * {@link LootRules} works in tier-level caps and has no way to know that a particular item
         * only stacks to 16.
         */
        public int count() {
            return count;
        }

        /**
         * The tier this entry was drawn from, which is the box's tier unless a bleed happened.
         * Carried through so the open message can call out the lucky ones.
         */
        public Rarity drawnFrom() {
            return drawnFrom;
        }

        @Override
        public String toString() {
            return count + "x " + key + " (" + drawnFrom.id() + ")";
        }
    }
}
