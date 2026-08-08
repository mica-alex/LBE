package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Percentile bucketing, and the interaction between overrides and the cut points. */
class RarityTableTest {

    private static Map<String, Double> evenlySpread(int count) {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            scores.put("mod:item" + i + "#0", (double) i);
        }
        return scores;
    }

    @Test
    @DisplayName("the default cuts put roughly the intended share in each tier")
    void defaultCutsSplitTheExpectedShares() {
        RarityTable table = RarityTable.build(evenlySpread(1000), null, null);

        // 0.60 / 0.88 / 0.975 over a uniform population. Generous tolerances — the point is the
        // shape (most items common, very few legendary), not an exact count.
        assertTrue(table.itemsOf(Rarity.COMMON).size() > 550, "common should hold the bulk");
        assertTrue(table.itemsOf(Rarity.LEGENDARY).size() < 40,
            "legendary should be a sliver, not a tier");
        assertTrue(table.itemsOf(Rarity.COMMON).size() > table.itemsOf(Rarity.UNCOMMON).size());
        assertTrue(table.itemsOf(Rarity.UNCOMMON).size() > table.itemsOf(Rarity.RARE).size());
        assertTrue(table.itemsOf(Rarity.RARE).size() > table.itemsOf(Rarity.LEGENDARY).size());
        assertEquals(1000, table.size());
    }

    @Test
    @DisplayName("percentiles self-normalise: the same shape holds for a tiny and a huge population")
    void percentilesSelfNormalise() {
        RarityTable small = RarityTable.build(evenlySpread(50), null, null);
        RarityTable large = RarityTable.build(evenlySpread(12000), null, null);

        double smallShare = small.itemsOf(Rarity.COMMON).size() / 50.0D;
        double largeShare = large.itemsOf(Rarity.COMMON).size() / 12000.0D;
        assertTrue(Math.abs(smallShare - largeShare) < 0.1D,
            "the common share should be about the same in vanilla and in a kitchen-sink pack");
    }

    @Test
    @DisplayName("an override wins over the score")
    void overridesWin() {
        Map<String, Double> scores = evenlySpread(100);
        RarityOverrides overrides = RarityOverrides.parse(new String[] { "mod:item0=legendary" });
        RarityTable table = RarityTable.build(scores, null, overrides);

        assertSame(Rarity.LEGENDARY, table.tierOf("mod:item0#0"),
            "the lowest-scoring item should still be legendary if the config says so");
        assertEquals(1, table.overriddenCount());
    }

    @Test
    @DisplayName("overridden items do not shift the cut points for everyone else")
    void overridesDoNotDragTheRestAround() {
        Map<String, Double> scores = evenlySpread(200);

        RarityTable without = RarityTable.build(scores, null, null);

        // Declare the entire top half legendary. If they counted toward the percentiles, every
        // remaining item's tier would move.
        String[] lines = new String[100];
        for (int i = 0; i < 100; i++) {
            lines[i] = "mod:item" + (100 + i) + "=legendary";
        }
        RarityTable with = RarityTable.build(scores, null, RarityOverrides.parse(lines));

        assertEquals(100, with.overriddenCount());
        assertEquals(100, with.scoredPopulation());
        // A low-scoring item is common either way — the overrides did not promote it by shrinking
        // the population above it.
        assertSame(Rarity.COMMON, without.tierOf("mod:item5#0"));
        assertSame(Rarity.COMMON, with.tierOf("mod:item5#0"));
    }

    @Test
    @DisplayName("ties land in the same tier rather than being split by sort position")
    void tiesStayTogether() {
        Map<String, Double> scores = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            scores.put("mod:same" + i + "#0", 5.0D);
        }
        RarityTable table = RarityTable.build(scores, null, null);

        assertEquals(100, table.itemsOf(Rarity.COMMON).size(),
            "a hundred identical scores must not be split across tiers");
    }

    @Test
    @DisplayName("a nonsense cut list falls back to the defaults instead of throwing")
    void badCutsAreSurvivable() {
        RarityTable table = RarityTable.build(evenlySpread(100), new double[] { 5.0D, -1.0D }, null);
        assertEquals(100, table.size());
        assertNotEquals(0, table.itemsOf(Rarity.COMMON).size());
    }

    @Test
    @DisplayName("an empty population produces an empty table, not an exception")
    void emptyPopulation() {
        RarityTable table = RarityTable.build(new LinkedHashMap<String, Double>(), null, null);
        assertEquals(0, table.size());
        assertSame(Rarity.COMMON, table.tierOf("anything:at_all#0"));
    }
}
