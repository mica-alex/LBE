package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Loot weights: the {@code lootWeights} grammar, and what weighting does and does not change about
 * a draw.
 */
class LootPoolsTest {

    /** Everything common: four items, two of them food. */
    private static RarityTable commonOnly() {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("minecraft:bread#0", 1.0);
        scores.put("minecraft:cooked_beef#0", 1.0);
        scores.put("csm:sign#0", 1.0);
        scores.put("csm:bench#0", 1.0);
        return RarityTable.build(scores, null, RarityOverrides.parse(new String[] {
            "minecraft:bread=common", "minecraft:cooked_beef=common", "csm:sign=common",
            "csm:bench=common" }));
    }

    private static List<String> categories(String key) {
        return key.startsWith("minecraft:") ? Arrays.asList(ItemCategory.FOOD)
            : Collections.<String>emptyList();
    }

    private static Map<String, Integer> drawMany(LootPools pools, Rarity tier, int draws) {
        Map<String, Integer> counts = new HashMap<>();
        Random random = new Random(42L);
        for (int i = 0; i < draws; i++) {
            String key = pools.draw(tier, random);
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    @Test
    @DisplayName("the most specific rule wins, and categories multiply")
    void precedence() {
        ItemPatternTable table = ItemPatternTable.parse(new String[] {
            "@food=3",
            "@block=0.5",
            "minecraft:*=2",
            "minecraft:fish#*=5",
            "minecraft:fish#1=7",
        });
        assertTrue(table.problems().isEmpty(), table.problems().toString());
        assertEquals(7.0, table.weightOf("minecraft:fish#1", categories("minecraft:fish#1")));
        assertEquals(5.0, table.weightOf("minecraft:fish#0", categories("minecraft:fish#0")));
        assertEquals(2.0, table.weightOf("minecraft:bread#0", categories("minecraft:bread#0")));
        assertEquals(1.5, table.weightOf("mod:pie_block#0",
            Arrays.asList(ItemCategory.FOOD, ItemCategory.BLOCK)));
        assertEquals(1.0, table.weightOf("mod:thing#0", Collections.<String>emptyList()));
    }

    @Test
    @DisplayName("stack caps take the largest category, not the product")
    void capsTakeTheLargest() {
        ItemPatternTable caps = ItemPatternTable.parse(new String[] { "@food=16", "@block=8" });
        assertEquals(Integer.valueOf(16), caps.capOf("mod:pie_block#0",
            Arrays.asList(ItemCategory.FOOD, ItemCategory.BLOCK)));
        assertNull(caps.capOf("mod:sword#0", Arrays.asList(ItemCategory.WEAPON)));
    }

    @Test
    @DisplayName("malformed lines are reported, not thrown, and do not stop the others")
    void problemsAreCollected() {
        ItemPatternTable table = ItemPatternTable.parse(new String[] {
            "# a comment",
            "@food=3",
            "@snacks=2",
            "minecraft:bread",
            "minecraft:bread=lots",
            "minecraft:bread=-1",
            "bread=2",
        });
        assertEquals(5, table.problems().size(), table.problems().toString());
        assertEquals(3.0, table.weightOf("minecraft:bread#0", Arrays.asList(ItemCategory.FOOD)));
    }

    @Test
    @DisplayName("a weighted item comes up proportionally more often, from its own tier only")
    void weightsShiftTheDrawWithinTheTier() {
        LootPools pools = LootPools.build(commonOnly(), LootPoolsTest::categories,
            ItemPatternTable.parse(new String[] { "@food=4" }), null);
        Map<String, Integer> counts = drawMany(pools, Rarity.COMMON, 20000);
        int food = counts.getOrDefault("minecraft:bread#0", 0)
            + counts.getOrDefault("minecraft:cooked_beef#0", 0);
        // Food weighs 4+4 against 1+1, so 80% of draws.
        double share = food / 20000.0;
        assertTrue(share > 0.78 && share < 0.82, "food share was " + share);
        assertEquals(0.4, pools.topShares(Rarity.COMMON, 1).get(0).fraction(), 1e-9);
    }

    @Test
    @DisplayName("weight 0 removes an item from the draw")
    void zeroWeightExcludes() {
        LootPools pools = LootPools.build(commonOnly(), LootPoolsTest::categories,
            ItemPatternTable.parse(new String[] { "csm:*=0" }), null);
        Map<String, Integer> counts = drawMany(pools, Rarity.COMMON, 2000);
        assertEquals(2, counts.size(), counts.toString());
        assertEquals(2, pools.drawableCount(Rarity.COMMON));
        assertEquals(4, pools.tierSize(Rarity.COMMON));
        assertEquals(0.0, pools.weightOf("csm:sign#0"));
    }

    @Test
    @DisplayName("a tier weighted entirely to 0 falls through to the tier below, never above")
    void zeroedTierFallsDown() {
        Map<String, Double> scores = new LinkedHashMap<>();
        scores.put("minecraft:bread#0", 1.0);
        scores.put("csm:statue#0", 1.0);
        RarityTable table = RarityTable.build(scores, null, RarityOverrides.parse(new String[] {
            "minecraft:bread=common", "csm:statue=rare" }));
        LootPools pools = LootPools.build(table, null,
            ItemPatternTable.parse(new String[] { "csm:*=0" }), null);
        Random random = new Random(1L);
        for (int i = 0; i < 100; i++) {
            assertEquals("minecraft:bread#0", pools.draw(Rarity.RARE, random));
        }
    }

    @Test
    @DisplayName("with no weights every item is equally likely, as before")
    void uniformWithoutRules() {
        LootPools pools = LootPools.uniform(commonOnly());
        Map<String, Integer> counts = drawMany(pools, Rarity.COMMON, 40000);
        for (int count : counts.values()) {
            assertTrue(Math.abs(count - 10000) < 400, counts.toString());
        }
    }

    @Test
    @DisplayName("a category stack cap replaces the tier's cap in a rolled box")
    void stackCapReachesTheRoll() {
        LootPools pools = LootPools.build(commonOnly(), LootPoolsTest::categories,
            ItemPatternTable.parse(new String[] { "csm:*=0" }),
            ItemPatternTable.parse(new String[] { "@food=32" }));
        LootRules rules = new LootRules(new int[] { 6, 6, 6, 6 }, new int[] { 6, 6, 6, 6 },
            new int[] { 2, 2, 2, 2 }, new int[] { 2, 2, 2, 2 }, new int[] { 2, 2, 2, 2 },
            0.0D, 0.0D);
        int largest = 0;
        Random random = new Random(7L);
        for (int trial = 0; trial < 200; trial++) {
            for (LootRoller.RolledEntry entry : LootRoller.roll(Rarity.COMMON, pools, rules, random)) {
                largest = Math.max(largest, entry.count());
            }
        }
        assertTrue(largest > 2 && largest <= 32, "largest pile was " + largest);
    }
}
