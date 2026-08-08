package com.micatechnologies.minecraft.lbe.rarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The model's acceptance test: does it put <b>vanilla</b> in the order a player would?
 *
 * <p>This is the check the default weights were tuned against, and the reasoning is simple — a model
 * that cannot rank the game everyone already knows has no business ranking a modpack nobody has
 * seen. If a weight change breaks one of these, the weight change is wrong until proven otherwise.</p>
 *
 * <p>The graph below is a hand-built slice of vanilla: real recipes, real item properties, and
 * nothing else. It is not complete and does not need to be — what it contains is one clean path from
 * "dug out of the ground" to "the thing you build last".</p>
 */
class VanillaOrderingTest {

    /**
     * The scarcity input, matching the defaults LBE ships in {@code LbeConfig.materialScores}.
     *
     * <p>Recipe data cannot distinguish a diamond from a lump of cobblestone — neither is crafted
     * from anything — so without a table like this the model has no scarcity signal at all. This
     * suite is how that was found, and {@link #scarcityIsNotDerivableFromRecipes()} pins the gap in
     * place so nobody deletes the table thinking it decorative.</p>
     *
     * <p>Keep these values in step with the config defaults; if they drift, this suite stops testing
     * what actually ships.</p>
     */
    private static final MaterialScores VANILLA_SCARCITY = MaterialScores.parse(new String[] {
        "minecraft:diamond=8.0",
        "minecraft:iron_ore=1.5",
        "minecraft:nether_star=25.0",
    });

    private static final ItemProfile TOOL_WOOD =
        new ItemProfile("minecraft:wooden_pickaxe#0", 1, 59, 15, false, 0, false);
    private static final ItemProfile TOOL_IRON =
        new ItemProfile("minecraft:iron_pickaxe#0", 1, 250, 14, false, 0, false);
    private static final ItemProfile TOOL_DIAMOND =
        new ItemProfile("minecraft:diamond_pickaxe#0", 1, 1561, 10, false, 0, false);
    private static final ItemProfile BLOCK =
        new ItemProfile("block", 64, 0, 0, true, 0, false);

    private static FakeItemGraph vanillaSlice() {
        return new FakeItemGraph()
            // Dug up or chopped down: no recipe.
            .raw("minecraft:cobblestone#0", new ItemProfile("minecraft:cobblestone#0", 64, 0, 0,
                true, 0, false))
            .raw("minecraft:log#0", new ItemProfile("minecraft:log#0", 64, 0, 0, true, 0, false))
            .raw("minecraft:iron_ore#0", new ItemProfile("minecraft:iron_ore#0", 64, 0, 0, true, 0,
                false))
            .raw("minecraft:diamond#0")
            .raw("minecraft:sand#0", new ItemProfile("minecraft:sand#0", 64, 0, 0, true, 0, false))
            .raw("minecraft:obsidian#0", new ItemProfile("minecraft:obsidian#0", 64, 0, 0, true, 0,
                false))
            .raw("minecraft:nether_star#0",
                new ItemProfile("minecraft:nether_star#0", 64, 0, 0, false, 3, false))

            .crafted("minecraft:planks#0", 4, "minecraft:log#0")
            .crafted("minecraft:stick#0", 4, "minecraft:planks#0", "minecraft:planks#0")
            .crafted("minecraft:iron_ingot#0", 1, "minecraft:iron_ore#0")
            .crafted("minecraft:glass#0", BLOCK, 1, "minecraft:sand#0")

            .crafted("minecraft:wooden_pickaxe#0", TOOL_WOOD, 1,
                "minecraft:planks#0", "minecraft:planks#0", "minecraft:planks#0",
                "minecraft:stick#0", "minecraft:stick#0")
            .crafted("minecraft:iron_pickaxe#0", TOOL_IRON, 1,
                "minecraft:iron_ingot#0", "minecraft:iron_ingot#0", "minecraft:iron_ingot#0",
                "minecraft:stick#0", "minecraft:stick#0")
            .crafted("minecraft:diamond_pickaxe#0", TOOL_DIAMOND, 1,
                "minecraft:diamond#0", "minecraft:diamond#0", "minecraft:diamond#0",
                "minecraft:stick#0", "minecraft:stick#0")

            .crafted("minecraft:iron_block#0", BLOCK, 1,
                "minecraft:iron_ingot#0", "minecraft:iron_ingot#0", "minecraft:iron_ingot#0",
                "minecraft:iron_ingot#0", "minecraft:iron_ingot#0", "minecraft:iron_ingot#0",
                "minecraft:iron_ingot#0", "minecraft:iron_ingot#0", "minecraft:iron_ingot#0")

            .crafted("minecraft:beacon#0", BLOCK, 1,
                "minecraft:glass#0", "minecraft:glass#0", "minecraft:glass#0", "minecraft:glass#0",
                "minecraft:glass#0", "minecraft:nether_star#0",
                "minecraft:obsidian#0", "minecraft:obsidian#0", "minecraft:obsidian#0");
    }

    @Test
    @DisplayName("the pickaxe ladder comes out in the right order")
    void pickaxeLadder() {
        RarityScorer scorer = new RarityScorer(vanillaSlice(), new RarityWeights(), VANILLA_SCARCITY);

        double wood = scorer.score("minecraft:wooden_pickaxe#0");
        double iron = scorer.score("minecraft:iron_pickaxe#0");
        double diamond = scorer.score("minecraft:diamond_pickaxe#0");

        assertTrue(wood < iron, "wooden " + wood + " should be below iron " + iron);
        assertTrue(iron < diamond, "iron " + iron + " should be below diamond " + diamond);
    }

    @Test
    @DisplayName("a beacon is the most valuable thing in the slice")
    void beaconIsTheTop() {
        FakeItemGraph graph = vanillaSlice();
        RarityScorer scorer = new RarityScorer(graph, new RarityWeights(), VANILLA_SCARCITY);

        double beacon = scorer.score("minecraft:beacon#0");
        for (String key : graph.keys()) {
            if (!"minecraft:beacon#0".equals(key)) {
                assertTrue(scorer.score(key) < beacon,
                    key + " scored " + scorer.score(key) + ", above the beacon's " + beacon);
            }
        }
    }

    @Test
    @DisplayName("bulk building material stays below tools")
    void bulkStaysBelowTools() {
        RarityScorer scorer = new RarityScorer(vanillaSlice(), new RarityWeights(), VANILLA_SCARCITY);

        assertTrue(scorer.score("minecraft:cobblestone#0") < scorer.score("minecraft:stick#0"),
            "raw cobble should sit below anything crafted");
        assertTrue(scorer.score("minecraft:iron_block#0")
                < scorer.score("minecraft:diamond_pickaxe#0"),
            "a block of iron is a tidy pile of a common thing, not a diamond pickaxe");
    }

    @Test
    @DisplayName("the tiers land where you would put them by hand")
    void tiersMatchIntuition() {
        FakeItemGraph graph = vanillaSlice();
        RarityScorer scorer = new RarityScorer(graph, new RarityWeights(), VANILLA_SCARCITY);
        Map<String, Double> scores = scorer.scoreAll();

        // A deliberately coarse split for a 15-item population — the assertion is about the ends of
        // the ladder, which is where a wrong model shows up first.
        RarityTable table = RarityTable.build(scores, new double[] { 0.4D, 0.7D, 0.92D }, null);

        assertSame(Rarity.COMMON, table.tierOf("minecraft:cobblestone#0"));
        assertSame(Rarity.LEGENDARY, table.tierOf("minecraft:beacon#0"));
        assertTrue(table.tierOf("minecraft:diamond_pickaxe#0").atLeast(Rarity.RARE),
            "a diamond pickaxe should be at least rare, was "
                + table.tierOf("minecraft:diamond_pickaxe#0"));
        assertTrue(table.tierOf("minecraft:iron_pickaxe#0").atLeast(Rarity.UNCOMMON),
            "an iron pickaxe should be at least uncommon, was "
                + table.tierOf("minecraft:iron_pickaxe#0"));
    }

    @Test
    @DisplayName("without a scarcity table a diamond is worth exactly a base material — the blind spot")
    void scarcityIsNotDerivableFromRecipes() {
        RarityWeights weights = new RarityWeights();
        RarityScorer blind = new RarityScorer(vanillaSlice(), weights);

        // The model's one irreducible blind spot, asserted where it actually lives. A diamond has no
        // recipe, so the walk has nothing to go on and hands it the same base value it hands any
        // other uncrafted thing — obsidian, sand, a log. Nothing in recipe data can distinguish them.
        //
        // The symptom that originally surfaced this was an iron pickaxe outscoring a diamond one.
        // That particular inversion no longer happens (a diamond pickaxe's durability now carries the
        // comparison on its own), but the gap underneath it is unchanged, and everything else made of
        // diamond still depends on MaterialScores to be valued correctly.
        assertEquals(weights.rawMaterialBase, blind.score("minecraft:diamond#0"), 1e-9,
            "a diamond scores the plain raw-material base with no scarcity table — if this ever "
                + "changes, the model gained a scarcity signal from somewhere and MaterialScores may "
                + "no longer be load-bearing. Find out why before deleting anything.");
        assertEquals(blind.score("minecraft:diamond#0"), blind.score("minecraft:obsidian#0")
            + Math.abs(weights.blockWeight), 1e-9,
            "and it is worth exactly what obsidian is, once obsidian's block penalty is removed");
    }

    @Test
    @DisplayName("a declared material value propagates into everything made from it")
    void declaredValuePropagates() {
        FakeItemGraph graph = vanillaSlice();
        RarityScorer blind = new RarityScorer(graph, new RarityWeights());
        RarityScorer informed = new RarityScorer(graph, new RarityWeights(), VANILLA_SCARCITY);

        assertTrue(informed.score("minecraft:diamond_pickaxe#0")
                > blind.score("minecraft:diamond_pickaxe#0"),
            "declaring the diamond valuable must lift the pickaxe made from it — this is the whole "
                + "difference between MaterialScores and a tier override");
    }

    @Test
    @DisplayName("an override reclassifies without disturbing its neighbours")
    void overrideIsSurgical() {
        FakeItemGraph graph = vanillaSlice();
        Map<String, Double> scores =
            new RarityScorer(graph, new RarityWeights(), VANILLA_SCARCITY).scoreAll();

        RarityTable before = RarityTable.build(scores, new double[] { 0.4D, 0.7D, 0.92D }, null);
        RarityTable after = RarityTable.build(scores, new double[] { 0.4D, 0.7D, 0.92D },
            RarityOverrides.parse(new String[] { "minecraft:cobblestone=legendary" }));

        assertSame(Rarity.LEGENDARY, after.tierOf("minecraft:cobblestone#0"));
        assertSame(before.tierOf("minecraft:beacon#0"), after.tierOf("minecraft:beacon#0"));
    }
}
