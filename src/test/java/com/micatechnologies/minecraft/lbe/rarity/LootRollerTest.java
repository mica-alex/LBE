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
 * <p>Statistical assertions over many rolls with a fixed seed, so they are deterministic. "Does a
 * legendary box feel different from a common one?" is unanswerable by opening boxes in a dev world
 * and trivial here.</p>
 */
class LootRollerTest {

    private static RarityTable fourTierTable() {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < 400; i++) {
            scores.put("mod:item" + i + "#0", (double) i);
        }
        return RarityTable.build(scores, null, null);
    }

    /** No bleed-up, so "at the box's tier or above" means exactly "at the box's tier". */
    private static LootRules noJackpot() {
        return new LootRules(new int[] { 4, 4, 3, 3 }, new int[] { 6, 5, 5, 4 },
            new int[] { 2, 2, 1, 1 }, new int[] { 3, 2, 2, 2 },
            new int[] { 16, 8, 4, 1 }, 0.35D, 0.0D);
    }

    @Test
    @DisplayName("total item counts stay inside the configured range")
    void totalsRespectTheRules() {
        RarityTable table = fourTierTable();
        LootRules rules = LootRules.defaults();
        Random random = new Random(1234L);

        for (Rarity tier : Rarity.values()) {
            for (int trial = 0; trial < 500; trial++) {
                int rolled = LootRoller.roll(tier, table, rules, random).size();
                assertTrue(rolled >= rules.minItems(tier) && rolled <= rules.maxItems(tier),
                    tier.id() + " produced " + rolled + " items, outside ["
                        + rules.minItems(tier) + ", " + rules.maxItems(tier) + "]");
            }
        }
    }

    @Test
    @DisplayName("a legendary box is generous AND exclusive — four items, some genuinely legendary")
    void legendaryBoxIsNotStingy() {
        RarityTable table = fourTierTable();
        LootRules rules = noJackpot();
        Random random = new Random(99L);

        for (int trial = 0; trial < 500; trial++) {
            List<LootRoller.RolledEntry> rolled =
                LootRoller.roll(Rarity.LEGENDARY, table, rules, random);

            assertTrue(rolled.size() >= 3,
                "a legendary box should still be a boxful, got " + rolled.size());

            long atTier = rolled.stream()
                .filter(entry -> entry.drawnFrom() == Rarity.LEGENDARY).count();
            assertTrue(atTier >= rules.minFeatures(Rarity.LEGENDARY),
                "expected at least " + rules.minFeatures(Rarity.LEGENDARY)
                    + " legendary items, got " + atTier);
            assertTrue(atTier <= rules.maxFeatures(Rarity.LEGENDARY),
                "expected at most " + rules.maxFeatures(Rarity.LEGENDARY)
                    + " legendary items, got " + atTier);
            assertTrue(atTier < rolled.size(),
                "a legendary box should also contain filler, not be wall-to-wall legendary");
        }
    }

    @Test
    @DisplayName("every tier guarantees its own tier at least minFeatures times")
    void featuresAreGuaranteedForEveryTier() {
        RarityTable table = fourTierTable();
        LootRules rules = noJackpot();
        Random random = new Random(7L);

        for (Rarity tier : Rarity.values()) {
            for (int trial = 0; trial < 300; trial++) {
                long atOrAbove = LootRoller.roll(tier, table, rules, random).stream()
                    .filter(entry -> entry.drawnFrom().atLeast(tier)).count();
                assertTrue(atOrAbove >= rules.minFeatures(tier),
                    tier.id() + " box guaranteed " + rules.minFeatures(tier)
                        + " at-tier items but produced " + atOrAbove);
            }
        }
    }

    @Test
    @DisplayName("filler sits below the box's tier, never above it")
    void fillerStaysBelow() {
        RarityTable table = fourTierTable();
        LootRules rules = noJackpot();
        Random random = new Random(31L);

        for (int trial = 0; trial < 300; trial++) {
            List<LootRoller.RolledEntry> rolled =
                LootRoller.roll(Rarity.RARE, table, rules, random);
            for (LootRoller.RolledEntry entry : rolled) {
                assertTrue(entry.drawnFrom().ordinal() <= Rarity.RARE.ordinal(),
                    "with no jackpot, nothing in a rare box may exceed rare; got "
                        + entry.drawnFrom());
            }
        }
    }

    @Test
    @DisplayName("the box builds to its best item — features come last")
    void featuresComeLast() {
        RarityTable table = fourTierTable();
        LootRules rules = noJackpot();
        Random random = new Random(5L);

        for (int trial = 0; trial < 300; trial++) {
            List<LootRoller.RolledEntry> rolled =
                LootRoller.roll(Rarity.LEGENDARY, table, rules, random);
            // Once an at-tier item appears, everything after it must also be at-tier: the reveal
            // walks this list in order, and a box that peaks in its first second is a bad reveal.
            boolean seenFeature = false;
            for (LootRoller.RolledEntry entry : rolled) {
                boolean isFeature = entry.drawnFrom() == Rarity.LEGENDARY;
                if (seenFeature) {
                    assertTrue(isFeature, "filler appeared after a feature item: " + rolled);
                }
                seenFeature |= isFeature;
            }
        }
    }

    @Test
    @DisplayName("a legendary box still averages higher than a common one")
    void higherBoxesGiveBetterLoot() {
        RarityTable table = fourTierTable();
        LootRules rules = LootRules.defaults();
        Random random = new Random(99L);

        double commonAverage = averageDrawnTier(Rarity.COMMON, table, rules, random);
        double legendaryAverage = averageDrawnTier(Rarity.LEGENDARY, table, rules, random);
        assertTrue(legendaryAverage > commonAverage + 1.5D,
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
    @DisplayName("a common box has nothing below it, so all of it is common")
    void commonBoxHasNoFillerTierToFallTo() {
        RarityTable table = fourTierTable();
        Random random = new Random(11L);

        for (LootRoller.RolledEntry entry
                : LootRoller.roll(Rarity.COMMON, table, noJackpot(), random)) {
            assertEquals(Rarity.COMMON, entry.drawnFrom());
        }
    }

    @Test
    @DisplayName("the jackpot can lift a feature above the box's tier")
    void bleedUpReachesTheTierAbove() {
        RarityTable table = fourTierTable();
        // Always bleed up, to force the edge case rather than wait on a 4% chance.
        LootRules always = new LootRules(new int[] { 4, 4, 4, 4 }, new int[] { 4, 4, 4, 4 },
            new int[] { 1, 1, 1, 1 }, new int[] { 1, 1, 1, 1 },
            new int[] { 1, 1, 1, 1 }, 0.0D, 1.0D);
        Random random = new Random(3L);

        boolean sawUplift = false;
        for (LootRoller.RolledEntry entry : LootRoller.roll(Rarity.RARE, table, always, random)) {
            if (entry.drawnFrom() == Rarity.LEGENDARY) {
                sawUplift = true;
            }
        }
        assertTrue(sawUplift, "a guaranteed bleed-up should produce a legendary in a rare box");

        // ...but never past the top of the ladder.
        for (LootRoller.RolledEntry entry
                : LootRoller.roll(Rarity.LEGENDARY, table, always, random)) {
            assertTrue(entry.drawnFrom().ordinal() <= Rarity.highest().ordinal());
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
    @DisplayName("a box can never hold fewer items than it guarantees at its own tier")
    void totalsAreWidenedToCoverFeatures() {
        // Three features but a maximum of two items: a config typo that would otherwise silently
        // drop one of the good ones.
        LootRules rules = new LootRules(new int[] { 1, 1, 1, 1 }, new int[] { 2, 2, 2, 2 },
            new int[] { 3, 3, 3, 3 }, new int[] { 3, 3, 3, 3 },
            new int[] { 1, 1, 1, 1 }, 0.0D, 0.0D);

        assertEquals(3, rules.maxItems(Rarity.RARE));
        assertEquals(3, rules.minItems(Rarity.RARE));
    }

    @Test
    @DisplayName("a max-below-min config typo widens the range instead of crashing")
    void invertedRangeIsSurvivable() {
        LootRules rules = new LootRules(new int[] { 5, 5, 5, 5 }, new int[] { 1, 1, 1, 1 },
            new int[] { 1, 1, 1, 1 }, new int[] { 1, 1, 1, 1 },
            new int[] { 1, 1, 1, 1 }, 0.0D, 0.0D);
        assertEquals(5, rules.minItems(Rarity.COMMON));
        assertEquals(5, rules.maxItems(Rarity.COMMON));
    }
}
