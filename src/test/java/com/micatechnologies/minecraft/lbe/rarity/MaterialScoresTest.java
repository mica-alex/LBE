package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parsing and precedence for the declared-material-value table. */
class MaterialScoresTest {

    @Test
    @DisplayName("scores parse, and metadata defaults to 0")
    void basicParsing() {
        MaterialScores scores = MaterialScores.parse(new String[] {
            "minecraft:diamond=8.0",
            "minecraft:emerald=8",
        });
        assertEquals(8.0D, scores.declaredFor("minecraft:diamond#0"), 1e-9);
        assertEquals(8.0D, scores.declaredFor("minecraft:emerald#0"), 1e-9);
        assertNull(scores.declaredFor("minecraft:diamond#1"));
        assertTrue(scores.problems().isEmpty(), scores.problems().toString());
    }

    @Test
    @DisplayName("exact keys beat wildcards, as in the override table")
    void exactBeatsWildcard() {
        MaterialScores scores = MaterialScores.parse(new String[] {
            "mod:ore#*=2.0",
            "mod:ore#3=12.0",
        });
        assertEquals(2.0D, scores.declaredFor("mod:ore#0"), 1e-9);
        assertEquals(12.0D, scores.declaredFor("mod:ore#3"), 1e-9);
    }

    @Test
    @DisplayName("a declared value short-circuits the item's own recipe")
    void declarationStopsTheRecipeWalk() {
        // gem_block is nine gems, but its value is dictated — so the nine gems are never counted.
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mod:gem#0")
            .crafted("mod:gem_block#0", 1,
                "mod:gem#0", "mod:gem#0", "mod:gem#0", "mod:gem#0", "mod:gem#0",
                "mod:gem#0", "mod:gem#0", "mod:gem#0", "mod:gem#0");
        MaterialScores declared = MaterialScores.parse(new String[] { "mod:gem_block=2.0" });

        RarityScorer scorer = new RarityScorer(graph, new RarityWeights(), declared);
        assertEquals(2.0D, scorer.score("mod:gem_block#0"), 1e-9);
        assertEquals(0, scorer.depth("mod:gem_block#0"),
            "a declared item is a leaf — its recipe is not walked");
    }

    @Test
    @DisplayName("a declaration severs the ingot<->block cycle")
    void declarationBreaksCycles() {
        FakeItemGraph graph = new FakeItemGraph()
            .crafted("mod:ingot#0", 9, "mod:block#0")
            .crafted("mod:block#0", 1,
                "mod:ingot#0", "mod:ingot#0", "mod:ingot#0", "mod:ingot#0", "mod:ingot#0",
                "mod:ingot#0", "mod:ingot#0", "mod:ingot#0", "mod:ingot#0");
        MaterialScores declared = MaterialScores.parse(new String[] { "mod:ingot=3.0" });

        RarityScorer scorer = new RarityScorer(graph, new RarityWeights(), declared);
        assertEquals(3.0D, scorer.score("mod:ingot#0"), 1e-9);
        assertTrue(Double.isFinite(scorer.score("mod:block#0")));
    }

    @Test
    @DisplayName("malformed and negative values are reported, not thrown")
    void malformedLinesAreReported() {
        MaterialScores scores = MaterialScores.parse(new String[] {
            "mod:good=4.0",
            "mod:no_equals",
            "mod:not_a_number=lots",
            "mod:negative=-3",
        });
        assertEquals(4.0D, scores.declaredFor("mod:good#0"), 1e-9);
        assertEquals(1, scores.size());
        assertEquals(3, scores.problems().size(), scores.problems().toString());
    }

    @Test
    @DisplayName("explain calls out a declared value so a tuner can see why the walk stopped")
    void explainMentionsTheDeclaration() {
        FakeItemGraph graph = new FakeItemGraph().raw("mod:gem#0");
        MaterialScores declared = MaterialScores.parse(new String[] { "mod:gem=8.0" });

        String explanation =
            new RarityScorer(graph, new RarityWeights(), declared).explain("mod:gem#0");
        assertTrue(explanation.contains("declared value"), explanation);
    }

    @Test
    @DisplayName("an empty table declares nothing")
    void emptyDeclaresNothing() {
        assertNull(MaterialScores.empty().declaredFor("anything:at_all#0"));
        assertNull(MaterialScores.parse(null).declaredFor("anything:at_all#0"));
    }
}
