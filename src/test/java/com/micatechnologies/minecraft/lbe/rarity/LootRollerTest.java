package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a box is actually worth.
 *
 * <p>These are statistical assertions over many rolls, with a fixed seed so they are deterministic.
 * Answering "does a legendary box feel different from a common one?" by opening boxes in a dev world
 * is exactly the kind of question that is unanswerable by hand and trivial here.</p>
 */
class LootRollerTest {

    private static RarityTable fourTierTable() {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < 400; i++) {
            scores.put("mod:item" + i + "#0", (double) i);
        }
        return RarityTable.build(scores, null, null);
    }

    @Test
    @DisplayName("roll counts stay inside the configured range")
    void rollCountsRespectTheRules() {
        RarityTable table = fourTierTable();
        LootRules rules = LootRules.defaults();
        Random random = new Random(1234L);

        for (Rarity tier : Rarity.values()) {
            for (int trial = 0; trial < 500; trial++) {
                int rolled = LootRoller.roll(tier, table, rules, random).size();
                assertTrue(rolled >= rules.minRolls(tier) && rolled <= rules.maxRolls(tier),
                    tier.id() + " produced " + rolled + " entries");
            }
        }
    }

    @Test
    @DisplayName("a legendary box gives higher-tier loot than a common one")
    void higherBoxesGiveBetterLoot() {
        RarityTable table = fourTierTable();
        LootRules rules = LootRules.defaults();
        Random random = new Random(99L);

        double commonAverage = averageDrawnTier(Rarity.COMMON, table, rules, random);
        double legendaryAverage = averageDrawnTier(Rarity.LEGENDARY, table, rules, random);
        assertTrue(legendaryAverage > commonAverage + 2.0D,
            "legendary boxes averaged tier " + legendaryAverage + " vs common " + commonAverage);
    }

    private static double averageDrawnTier(Rarity boxTier, RarityTable table, LootRules rules,
                                           Random random) {
        long total = 0;
        long entries = 0;
        for (int trial = 0; trial < 2000; trial++) {
            for (LootRoller.RolledEntry entry : LootRoller.roll(boxTier, table, rules, random)) {
                total += entry.drawnFrom().ordinal();
                entries++;
            }
        }
        return entries == 0 ? 0.0D : (double) total / entries;
    }

    @Test
    @DisplayName("a common box never bleeds down below common, and legendary never bleeds up")
    void bleedStaysInsideTheLadder() {
        RarityTable table = fourTierTable();
        // Bleed both ways every single time, to force the edge cases.
        LootRules rules = new LootRules(new int[] { 4, 4, 4, 4 }, new int[] { 4, 4, 4, 4 },
            new int[] { 1, 1, 1, 1 }, 1.0D, 1.0D);
        Random random = new Random(7L);

        for (LootRoller.RolledEntry entry : LootRoller.roll(Rarity.LEGENDARY, table, rules, random)) {
            assertTrue(entry.drawnFrom().ordinal() <= Rarity.LEGENDARY.ordinal());
        }
        for (LootRoller.RolledEntry entry : LootRoller.roll(Rarity.COMMON, table, rules, random)) {
            assertTrue(entry.drawnFrom().ordinal() >= Rarity.COMMON.ordinal());
        }
    }

    @Test
    @DisplayName("the same seed gives the same box, which is what stops re-rolling")
    void seededRollsAreStable() {
        RarityTable table = fourTierTable();
        LootRules rules = LootRules.defaults();

        List<LootRoller.RolledEntry> first = LootRoller.roll(Rarity.RARE, table, rules, new Random(42L));
        List<LootRoller.RolledEntry> second = LootRoller.roll(Rarity.RARE, table, rules, new Random(42L));

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).key(), second.get(i).key());
            assertEquals(first.get(i).count(), second.get(i).count());
        }
    }

    @Test
    @DisplayName("an empty tier falls back downward rather than producing nothing")
    void emptyTierWalksDown() {
        // Every item scores the same, so the percentile cuts put them all in COMMON and the three
        // tiers above it are empty.
        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            scores.put("mod:flat" + i + "#0", 1.0D);
        }
        RarityTable table = RarityTable.build(scores, null, null);
        assertTrue(table.itemsOf(Rarity.LEGENDARY).isEmpty(), "precondition: legendary is empty");

        List<LootRoller.RolledEntry> rolled =
            LootRoller.roll(Rarity.LEGENDARY, table, LootRules.defaults(), new Random(3L));
        assertFalse(rolled.isEmpty(), "an empty top tier must fall back, not hand out nothing");
    }

    @Test
    @DisplayName("an empty catalogue produces an empty box instead of throwing")
    void emptyTableIsSurvivable() {
        List<LootRoller.RolledEntry> rolled =
            LootRoller.roll(Rarity.RARE, RarityTable.empty(), LootRules.defaults(), new Random(1L));
        assertTrue(rolled.isEmpty());
    }

    @Test
    @DisplayName("a max-below-min config typo widens the range instead of crashing")
    void invertedRangeIsSurvivable() {
        LootRules rules = new LootRules(new int[] { 5, 5, 5, 5 }, new int[] { 1, 1, 1, 1 },
            new int[] { 1, 1, 1, 1 }, 0.0D, 0.0D);
        assertEquals(5, rules.minRolls(Rarity.COMMON));
        assertEquals(5, rules.maxRolls(Rarity.COMMON));
    }
}
