package com.micatechnologies.minecraft.lbe.catalog;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.rarity.KeyFilter;
import com.micatechnologies.minecraft.lbe.rarity.LootRoller;
import com.micatechnologies.minecraft.lbe.rarity.LootRules;
import com.micatechnologies.minecraft.lbe.rarity.MaterialScores;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import com.micatechnologies.minecraft.lbe.rarity.RarityOverrides;
import com.micatechnologies.minecraft.lbe.rarity.RarityScorer;
import com.micatechnologies.minecraft.lbe.rarity.RarityTable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.item.ItemStack;

/**
 * The scored catalogue: every item in the pack, sorted into tiers, ready to be handed out.
 *
 * <p>Built once at postInit and rebuilt on {@code /lbe reload}. Everything expensive happens in
 * {@link #rebuild()} — walking the registries, scoring the recipe graph, cutting the percentiles.
 * {@link #roll(Rarity, Random)}, which runs whenever a player opens a box, does nothing but index
 * into the finished table.</p>
 *
 * <p><b>Server-side state.</b> This is built from the server's config and consulted when a box is
 * opened, which only ever happens server-side. A client has its own copy from its own config and
 * simply never asks it anything. Nothing here is synced and nothing here should be.</p>
 */
public final class LootCatalog {

    private static ForgeItemGraph graph;
    private static RarityScorer scorer;
    private static RarityTable table = RarityTable.empty();
    private static LootRules rules = LootRules.defaults();

    private LootCatalog() {
        throw new AssertionError("No instances.");
    }

    /**
     * Walk the registries, score everything, and cut the tiers.
     *
     * <p>Call at postInit, after every mod has registered, and again after a config reload. Takes on
     * the order of a second on a large pack — acceptable once at startup, which is why nothing else
     * in the mod is allowed to trigger it.</p>
     */
    public static void rebuild() {
        long startedAt = System.currentTimeMillis();

        KeyFilter blacklist = LbeConfig.blacklist();
        RarityOverrides overrides = LbeConfig.overrides();
        MaterialScores declared = LbeConfig.declaredMaterials();
        reportProblems("blacklist", blacklist.problems());
        reportProblems("overrides", overrides.problems());
        reportProblems("materialScores", declared.problems());

        graph = ForgeItemGraph.snapshot(blacklist);
        scorer = new RarityScorer(graph, LbeConfig.weights(), declared);
        Map<String, Double> scores = scorer.scoreAll();
        table = RarityTable.build(scores, LbeConfig.tierPercentileCuts, overrides);
        rules = LbeConfig.lootRules();

        long elapsed = System.currentTimeMillis() - startedAt;
        double[] thresholds = table.scoreThresholds();
        StringBuilder counts = new StringBuilder();
        for (Rarity rarity : Rarity.values()) {
            if (counts.length() > 0) {
                counts.append(", ");
            }
            counts.append(rarity.id()).append('=').append(table.itemsOf(rarity).size());
        }
        Lbe.LOGGER.info("Scored {} items in {} ms ({} overridden by config). Tiers: {}",
            table.size(), elapsed, table.overriddenCount(), counts);
        Lbe.LOGGER.info("Score thresholds: {}", java.util.Arrays.toString(thresholds));

        // An empty tier is not fatal — LootRoller walks down to a populated one — but it always
        // means something is misconfigured, and it is invisible in-game until a box disappoints
        // someone. Say so loudly at startup instead.
        for (Rarity rarity : Rarity.values()) {
            if (table.itemsOf(rarity).isEmpty()) {
                Lbe.LOGGER.warn("Tier '{}' has NO items. Boxes of this tier will fall back to a "
                    + "lower tier. Check 'tierPercentileCuts' and the blacklist.", rarity.id());
            }
        }

        if (LbeConfig.logCatalogueOnStartup) {
            for (Map.Entry<String, Rarity> entry : table.all().entrySet()) {
                Lbe.LOGGER.info("  {} = {}", entry.getKey(), entry.getValue().id());
            }
        }
    }

    private static void reportProblems(String source, List<String> problems) {
        for (String problem : problems) {
            Lbe.LOGGER.warn("Config '{}': {}", source, problem);
        }
    }

    /** {@code true} once {@link #rebuild()} has produced a usable catalogue. */
    public static boolean isReady() {
        return graph != null && table.size() > 0;
    }

    /** The tier assigned to an item key, or {@link Rarity#COMMON} for anything unknown. */
    public static Rarity tierOf(String key) {
        return table.tierOf(key);
    }

    /** The tier assigned to a stack. */
    public static Rarity tierOf(ItemStack stack) {
        String key = ForgeItemGraph.keyOf(stack);
        return key == null ? Rarity.lowest() : table.tierOf(key);
    }

    /** The finished tier table. */
    public static RarityTable table() {
        return table;
    }

    /**
     * A term-by-term explanation of one item's score, for {@code /lbe rarity}.
     *
     * @return the breakdown, or a short message if the catalogue has not been built
     */
    public static String explain(String key) {
        if (scorer == null) {
            return "The catalogue has not been built yet.";
        }
        return scorer.explain(key) + "  tier: " + table.tierOf(key).id();
    }

    /**
     * Roll the contents of one box.
     *
     * @param tier   the box's tier
     * @param random source of randomness; seed it per box for stable contents
     * @return the stacks to give the player, never {@code null}
     */
    public static List<ItemStack> roll(Rarity tier, Random random) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!isReady()) {
            Lbe.LOGGER.warn("A {} loot box was opened before the catalogue was built", tier.id());
            return stacks;
        }
        for (LootRoller.RolledEntry entry : LootRoller.roll(tier, table, rules, random)) {
            ItemStack stack = graph.stackFor(entry.key());
            if (stack.isEmpty()) {
                continue;
            }
            // The roller works in tier-level caps and has no way to know that a particular item only
            // stacks to 16 — or to 1. Clamping here rather than there is what keeps the roller free
            // of Minecraft types.
            stack.setCount(Math.min(entry.count(), stack.getMaxStackSize()));
            stacks.add(stack);
        }
        return stacks;
    }

    /**
     * A handful of arbitrary stacks around {@code tier}, for the reveal screen's spinning reel.
     *
     * <p>Purely decorative — these are the items that blur past before the real reward lands, so
     * they only have to look plausible. Drawn from the box's own tier and the one below it, because a
     * reel full of dirt while you wait for a legendary undersells the moment, and a reel full of
     * beacons oversells it.</p>
     *
     * <p>Deliberately reads the <b>client's own</b> catalogue rather than shipping decoys in the
     * packet: they are cosmetic, so a client whose config differs from the server's showing slightly
     * different blur is not a bug worth paying bandwidth to prevent, on every box, forever.</p>
     *
     * @return up to {@code count} stacks; empty if the catalogue is not built
     */
    public static List<ItemStack> randomStacks(int count, Rarity tier, Random random) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!isReady() || count <= 0) {
            return stacks;
        }
        List<String> pool = new ArrayList<>(table.itemsOf(tier));
        if (tier != Rarity.lowest()) {
            pool.addAll(table.itemsOf(Rarity.values()[tier.ordinal() - 1]));
        }
        if (pool.isEmpty()) {
            pool = new ArrayList<>(table.all().keySet());
        }
        if (pool.isEmpty()) {
            return stacks;
        }
        for (int i = 0; i < count; i++) {
            ItemStack stack = graph.stackFor(pool.get(random.nextInt(pool.size())));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /**
     * Write the whole scored catalogue to a file — every item, its tier, and its score.
     *
     * <p>Backs {@code /lbe dump}. This is the artefact a pack author actually needs when they think
     * the tiers came out wrong: a sortable list of what the scorer made of their pack, rather than
     * twenty thousand lines in the server log.</p>
     *
     * @return the number of lines written
     */
    public static int dumpTo(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        int written = 0;
        Writer writer = new BufferedWriter(
            new OutputStreamWriter(new java.io.FileOutputStream(file), StandardCharsets.UTF_8));
        try {
            writer.write("# LBE scored catalogue\n");
            writer.write("# tier\tscore\titem\n");
            for (Map.Entry<String, Rarity> entry : table.all().entrySet()) {
                writer.write(entry.getValue().id());
                writer.write('\t');
                writer.write(scorer == null ? "?"
                    : String.format(java.util.Locale.ROOT, "%.3f", scorer.score(entry.getKey())));
                writer.write('\t');
                writer.write(entry.getKey());
                writer.write('\n');
                written++;
            }
        }
        finally {
            writer.close();
        }
        return written;
    }
}
