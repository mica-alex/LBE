package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The scoring model's behavioural contract.
 *
 * <p>These assert on <b>orderings</b>, not on numbers. The exact score of a diamond pickaxe is an
 * implementation detail that any weight change legitimately moves; "a diamond pickaxe outranks a
 * stick" is the property the mod actually promises, and it is what must survive a retune.</p>
 */
class RarityScorerTest {

    private static RarityScorer scorer(ItemGraph graph) {
        return new RarityScorer(graph, new RarityWeights());
    }

    @Test
    @DisplayName("the depth bonus is paid once, not compounded up the chain")
    void depthIsNotChargedToParents() {
        // Three cheap steps from a raw material, against one step from something ten times dearer.
        // If each level's depth bonus flowed into its parent's ingredient cost, the long cheap chain
        // would win on chain length alone — which is exactly what put a wooden pickaxe in the rare
        // tier on real vanilla data.
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:twig#0")
            .crafted("mc:step1#0", 1, "mc:twig#0")
            .crafted("mc:step2#0", 1, "mc:step1#0")
            .crafted("mc:cheapDeep#0", 1, "mc:step2#0")
            .raw("mc:goldNugget#0", new ItemProfile("mc:goldNugget#0", 64, 0, 0, false, 2, false))
            .crafted("mc:dearShallow#0", 1, "mc:goldNugget#0", "mc:goldNugget#0", "mc:goldNugget#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:dearShallow#0") > scorer.score("mc:cheapDeep#0"),
            "three of something valuable should beat three steps of shuffling twigs; got "
                + scorer.score("mc:dearShallow#0") + " vs " + scorer.score("mc:cheapDeep#0"));
    }

    @Test
    @DisplayName("a negative-cost ingredient does not poison the sum with NaN")
    void negativeCostsAreClamped() {
        // blockWeight is negative by default, so a nearly-free block can price out below zero. A
        // negative ingredient sum raised to a fractional power is NaN, and it would spread silently
        // through every item that consumes it.
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:mud#0", new ItemProfile("mc:mud#0", 64, 0, 0, true, 0, false))
            .crafted("mc:mudBrick#0", new ItemProfile("mc:mudBrick#0", 64, 0, 0, true, 0, false),
                4, "mc:mud#0")
            .crafted("mc:mudHouse#0", 1, "mc:mudBrick#0", "mc:mudBrick#0", "mc:mudBrick#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(Double.isFinite(scorer.score("mc:mudBrick#0")), "brick score must be finite");
        assertTrue(Double.isFinite(scorer.score("mc:mudHouse#0")), "house score must be finite");
    }

    @Test
    @DisplayName("a crafted item outranks the raw materials it is made from")
    void craftingAddsValue() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:wood#0")
            .crafted("mc:stick#0", 4, "mc:wood#0", "mc:wood#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:stick#0") > scorer.score("mc:wood#0"),
            "a stick should be worth more than the wood it came from");
    }

    @Test
    @DisplayName("depth beats breadth: a long chain outranks one wide cheap recipe")
    void progressionBeatsQuantity() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:stone#0")
            // Nine of one raw material, one step.
            .crafted("mc:stone_pile#0", 1,
                "mc:stone#0", "mc:stone#0", "mc:stone#0", "mc:stone#0", "mc:stone#0",
                "mc:stone#0", "mc:stone#0", "mc:stone#0", "mc:stone#0")
            // Four steps, two ingredients each.
            .crafted("mc:tier1#0", 1, "mc:stone#0", "mc:stone#0")
            .crafted("mc:tier2#0", 1, "mc:tier1#0", "mc:tier1#0")
            .crafted("mc:tier3#0", 1, "mc:tier2#0", "mc:tier2#0")
            .crafted("mc:tier4#0", 1, "mc:tier3#0", "mc:tier3#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:tier4#0") > scorer.score("mc:stone_pile#0"),
            "four steps of progression should outrank one nine-of-a-thing recipe");
        assertEquals(4, scorer.depth("mc:tier4#0"));
        assertEquals(1, scorer.depth("mc:stone_pile#0"));
    }

    @Test
    @DisplayName("a storage block does not become legendary just for holding nine of something")
    void bulkCompressionKeepsStorageBlocksSane() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:ingot#0")
            .crafted("mc:block#0", 1,
                "mc:ingot#0", "mc:ingot#0", "mc:ingot#0", "mc:ingot#0", "mc:ingot#0",
                "mc:ingot#0", "mc:ingot#0", "mc:ingot#0", "mc:ingot#0");
        RarityScorer scorer = scorer(graph);

        double ingot = scorer.score("mc:ingot#0");
        double block = scorer.score("mc:block#0");
        assertTrue(block > ingot, "a block of nine ingots is still worth more than one ingot");
        assertTrue(block < 9.0D * ingot,
            "but not nine times more — that is what bulkCompression is for");
    }

    @Test
    @DisplayName("crafting never makes something worth less than its dearest ingredient")
    void dearestIngredientIsAFloor() {
        // One very expensive input among a pile of cheap ones. Compressing the whole sum would
        // discount the expensive one along with everything else and put the result below it — the
        // "a beacon scores less than the nether star inside it" case.
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:cheap#0")
            .crafted("mc:precious#0", 1,
                "mc:cheap#0", "mc:cheap#0", "mc:cheap#0", "mc:cheap#0", "mc:cheap#0",
                "mc:cheap#0", "mc:cheap#0", "mc:cheap#0", "mc:cheap#0")
            .crafted("mc:setting#0", 1, "mc:precious#0", "mc:cheap#0", "mc:cheap#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:setting#0") > scorer.score("mc:precious#0"),
            "mounting a precious thing in a cheap frame must not devalue it");
    }

    @Test
    @DisplayName("the floor divides by output count, so uncrafting is not free value")
    void floorRespectsOutputCount() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:cheap#0")
            .crafted("mc:precious#0", 1,
                "mc:cheap#0", "mc:cheap#0", "mc:cheap#0", "mc:cheap#0", "mc:cheap#0",
                "mc:cheap#0", "mc:cheap#0", "mc:cheap#0", "mc:cheap#0")
            .crafted("mc:ninth#0", 9, "mc:precious#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:ninth#0") < scorer.score("mc:precious#0"),
            "one ninth of a precious thing must be worth less than the whole");
    }

    @Test
    @DisplayName("uncrafting: a recipe yielding many is cheaper per item than one yielding one")
    void outputCountDividesCost() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:wood#0")
            .crafted("mc:plank_one#0", 1, "mc:wood#0")
            .crafted("mc:plank_four#0", 4, "mc:wood#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:plank_four#0") < scorer.score("mc:plank_one#0"),
            "the same input yielding four should score each one lower");
    }

    @Test
    @DisplayName("an ingredient with alternatives is worth its cheapest member")
    void oreDictionarySlotTakesTheCheapest() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:cheap#0")
            .crafted("mc:pricey#0", 1, "mc:cheap#0", "mc:cheap#0", "mc:cheap#0", "mc:cheap#0")
            .craftedFromAnyOf("mc:either#0", 1, "mc:pricey#0", "mc:cheap#0")
            .crafted("mc:onlyPricey#0", 1, "mc:pricey#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:either#0") < scorer.score("mc:onlyPricey#0"),
            "a slot that accepts the cheap alternative should not be priced at the dear one");
    }

    @Test
    @DisplayName("a cyclic recipe graph terminates instead of blowing the stack")
    void cyclesTerminate() {
        // ingot -> block -> ingot, the single most common cycle in modded Minecraft.
        FakeItemGraph graph = new FakeItemGraph()
            .crafted("mc:ingot#0", 9, "mc:block#0")
            .crafted("mc:block#0", 1,
                "mc:ingot#0", "mc:ingot#0", "mc:ingot#0", "mc:ingot#0", "mc:ingot#0",
                "mc:ingot#0", "mc:ingot#0", "mc:ingot#0", "mc:ingot#0");
        RarityScorer scorer = scorer(graph);

        double ingot = scorer.score("mc:ingot#0");
        double block = scorer.score("mc:block#0");
        assertTrue(Double.isFinite(ingot) && Double.isFinite(block),
            "a cycle must produce finite scores, not an overflow or a hang");
    }

    @Test
    @DisplayName("a chain longer than maxRecipeDepth is truncated, not followed forever")
    void depthCapHolds() {
        FakeItemGraph graph = new FakeItemGraph().raw("mc:base#0");
        String previous = "mc:base#0";
        for (int i = 0; i < 200; i++) {
            String key = "mc:step" + i + "#0";
            graph.crafted(key, 1, previous);
            previous = key;
        }
        RarityWeights weights = new RarityWeights(1.0D, 0.75D, 1.0D, 3.0D, 0.35D, 1.5D, 0.6D,
            0.02D, 4.0D, -0.75D, 1.0D, 8);
        RarityScorer scorer = new RarityScorer(graph, weights);

        double score = scorer.score(previous);
        assertTrue(Double.isFinite(score), "the depth cap must bound the recursion");
    }

    @Test
    @DisplayName("declared EnumRarity outranks an otherwise identical plain item")
    void declaredRarityCounts() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:plain#0", ItemProfile.blank("mc:plain#0"))
            .raw("mc:epic#0", new ItemProfile("mc:epic#0", 64, 0, 0, false, 3, false));
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:epic#0") > scorer.score("mc:plain#0"),
            "an author who declared their item EPIC should be believed");
    }

    @Test
    @DisplayName("a durable unstackable tool outranks a stackable material of the same recipe cost")
    void itemPropertiesCount() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:gem#0")
            .crafted("mc:gemBlock#0", new ItemProfile("mc:gemBlock#0", 64, 0, 0, true, 0, false),
                1, "mc:gem#0", "mc:gem#0", "mc:gem#0")
            .crafted("mc:gemPick#0", new ItemProfile("mc:gemPick#0", 1, 1561, 10, false, 0, false),
                1, "mc:gem#0", "mc:gem#0", "mc:gem#0");
        RarityScorer scorer = scorer(graph);

        assertTrue(scorer.score("mc:gemPick#0") > scorer.score("mc:gemBlock#0"),
            "same three gems in, but one is a tool and one is a building block");
    }

    @Test
    @DisplayName("scoreAll covers exactly the graph's loot-eligible keys")
    void scoreAllCoversTheGraph() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:a#0")
            .raw("mc:b#0")
            .crafted("mc:c#0", 1, "mc:a#0", "mc:b#0");

        Map<String, Double> scores = scorer(graph).scoreAll();
        assertEquals(3, scores.size());
        assertTrue(scores.keySet().containsAll(graph.keys()));
    }

    @Test
    @DisplayName("explain names every term that contributed")
    void explainMentionsTheTerms() {
        FakeItemGraph graph = new FakeItemGraph()
            .raw("mc:gem#0")
            .crafted("mc:pick#0", new ItemProfile("mc:pick#0", 1, 1561, 10, false, 0, false),
                1, "mc:gem#0", "mc:gem#0");

        String explanation = scorer(graph).explain("mc:pick#0");
        assertTrue(explanation.contains("craft cost"), explanation);
        assertTrue(explanation.contains("depth"), explanation);
        assertTrue(explanation.contains("unstackable"), explanation);
        assertTrue(explanation.contains("durability"), explanation);
    }
}
