package com.micatechnologies.minecraft.lbe.catalog;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.rarity.ItemPatternTable;
import com.micatechnologies.minecraft.lbe.rarity.LootEligibility;
import com.micatechnologies.minecraft.lbe.rarity.LootPools;
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
import net.minecraftforge.fml.common.ProgressManager;

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
    private static LootPools pools = LootPools.uniform(RarityTable.empty());
    private static RarityOverrides overridesInUse = RarityOverrides.empty();
    private static MaterialScores declaredInUse = MaterialScores.empty();

    /**
     * Items per loading-screen progress step while scoring. Coarse on purpose: the bar exists to
     * show a mega-pack's launch is alive, not to be a speedometer, and a step per item would spend
     * more time telling the story than living it.
     */
    private static final int SCORING_PROGRESS_CHUNK = 1000;

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

        LootEligibility eligibility = LbeConfig.lootEligibility();
        RarityOverrides overrides = LbeConfig.overrides();
        MaterialScores declared = LbeConfig.declaredMaterials();
        reportProblems("rarity", eligibility.problems());
        reportProblems("overrides", overrides.problems());
        reportProblems("materialScores", declared.problems());

        // ProgressManager is deliberately NOT gated to the client: it lives in fml.common, it is
        // the same API FML's own loading stages report through, and on a dedicated server it is
        // inert state tracking. The client splash picks the bars up automatically. Gating it
        // through the proxy would add a seam for zero benefit — and rule 2 already forbids the
        // alternative of touching a client class from here. Harmless on /lbe reload too: with no
        // loading screen up, nobody renders the bar.
        Lbe.LOGGER.info("Building the loot catalogue: reading the item and recipe registries...");
        graph = ForgeItemGraph.snapshot(eligibility);
        scorer = new RarityScorer(graph, LbeConfig.weights(), declared);
        overridesInUse = overrides;
        declaredInUse = declared;

        int totalItems = graph.keys().size();
        Lbe.LOGGER.info("Scoring {} item variants...", totalItems);
        // ProgressManager.pop THROWS if the steps do not come out exact, so the arithmetic here is
        // load-bearing: ceil(total/chunk) steps, and the callback fires the bar once per chunk
        // boundary plus once for a final partial chunk — the || never double-fires because a total
        // on a chunk boundary satisfies the first test and short-circuits the second.
        int steps = (totalItems + SCORING_PROGRESS_CHUNK - 1) / SCORING_PROGRESS_CHUNK;
        ProgressManager.ProgressBar bar = ProgressManager.push("LBE: scoring items", steps);
        Map<String, Double> scores = scorer.scoreAll(scored -> {
            if (scored % SCORING_PROGRESS_CHUNK == 0 || scored == totalItems) {
                bar.step(scored + " / " + totalItems);
            }
        });
        ProgressManager.pop(bar);

        table = RarityTable.build(scores, LbeConfig.tierPercentileCuts, overrides);
        rules = LbeConfig.lootRules();
        ItemPatternTable weights = LbeConfig.lootWeights();
        ItemPatternTable stackCaps = LbeConfig.lootStackCaps();
        reportProblems("lootWeights", weights.problems());
        reportProblems("categoryMaxStack", stackCaps.problems());
        ForgeItemGraph snapshot = graph;
        pools = LootPools.build(table, snapshot::categoriesOf, weights, stackCaps);

        long elapsed = System.currentTimeMillis() - startedAt;
        double[] thresholds = table.scoreThresholds();
        StringBuilder counts = new StringBuilder();
        for (Rarity rarity : Rarity.values()) {
            if (counts.length() > 0) {
                counts.append(", ");
            }
            counts.append(rarity.id()).append('=').append(table.itemsOf(rarity).size());
            int zeroed = table.itemsOf(rarity).size() - pools.drawableCount(rarity);
            if (zeroed > 0) {
                counts.append(" (").append(zeroed).append(" weighted 0)");
            }
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
            else if (pools.drawableCount(rarity) == 0) {
                Lbe.LOGGER.warn("Every item in tier '{}' has a loot weight of 0. Boxes of this tier "
                    + "will fall back to a lower tier. Check 'lootWeights'.", rarity.id());
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
        String excluded = graph.exclusions().get(key);
        if (excluded != null) {
            return scorer.explain(key) + "  not loot: excluded (" + excluded + ")";
        }
        if (!table.contains(key)) {
            // Usually an item with no creative tab, which the snapshot never enumerates.
            return scorer.explain(key) + "  not loot: not in the catalogue";
        }
        String categories = categoriesText(key);
        return scorer.explain(key) + "  tier: " + table.tierOf(key).id()
            + "\n  loot weight: " + formatWeight(pools.weightOf(key))
            + (categories.isEmpty() ? "" : "  categories: " + categories);
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
        for (LootRoller.RolledEntry entry : LootRoller.roll(tier, pools, rules, random)) {
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
        // Through the weighted pools, so a reel never flashes an item the weights forbid, and a
        // pack that favours food sees food blur past too.
        Rarity below = tier == Rarity.lowest() ? tier : Rarity.values()[tier.ordinal() - 1];
        for (int i = 0; i < count; i++) {
            String key = pools.draw(random.nextBoolean() ? tier : below, random);
            if (key == null) {
                break;
            }
            ItemStack stack = graph.stackFor(key);
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
            writer.write("# LBE scored catalogue. Items kept out of the loot pool are listed last, "
                + "with tier 'excluded'.\n");
            writer.write("# weight is the item's draw weight within its tier (0: never drawn); "
                + "categories are the @ names lootWeights can use.\n");
            writer.write("# tier\tscore\tweight\titem\tname\tcategories\treason\n");
            for (Map.Entry<String, Rarity> entry : table.all().entrySet()) {
                writeRow(writer, entry.getValue().id(), entry.getKey(), reasonFor(entry.getKey()));
                written++;
            }
            if (graph != null) {
                for (Map.Entry<String, String> entry : graph.exclusions().entrySet()) {
                    writeRow(writer, "excluded", entry.getKey(), entry.getValue());
                    written++;
                }
            }
        }
        finally {
            writer.close();
        }
        return written;
    }

    private static void writeRow(Writer writer, String tier, String key, String reason)
            throws IOException {
        writer.write(tier);
        writer.write('\t');
        writer.write(scorer == null ? "?"
            : String.format(java.util.Locale.ROOT, "%.3f", scorer.score(key)));
        writer.write('\t');
        writer.write(table.contains(key) ? formatWeight(pools.weightOf(key)) : "-");
        writer.write('\t');
        writer.write(key);
        writer.write('\t');
        writer.write(nameOf(key));
        writer.write('\t');
        writer.write(categoriesText(key));
        writer.write('\t');
        writer.write(reason);
        writer.write('\n');
    }

    private static String formatWeight(double weight) {
        return String.format(java.util.Locale.ROOT, "%.3f", weight);
    }

    /** The item's categories as {@code @food @tool}, or empty. */
    private static String categoriesText(String key) {
        StringBuilder out = new StringBuilder();
        if (graph != null) {
            for (String category : graph.categoriesOf(key)) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append('@').append(category);
            }
        }
        return out.toString();
    }

    /**
     * The items a tier's draw lands on most often, for {@code /lbe weights}: one line per item
     * with its share of the draw, after a summary line.
     */
    public static List<String> weightSummary(Rarity tier, int limit) {
        List<String> lines = new ArrayList<>();
        if (!isReady()) {
            lines.add("The catalogue has not been built yet.");
            return lines;
        }
        int size = pools.tierSize(tier);
        int drawable = pools.drawableCount(tier);
        lines.add(tier.id() + ": " + drawable + " of " + size + " items can be drawn"
            + (drawable < size ? " (" + (size - drawable) + " weighted 0)" : "")
            + ". Most likely per draw:");
        for (LootPools.Share share : pools.topShares(tier, limit)) {
            String name = nameOf(share.key());
            lines.add(String.format(java.util.Locale.ROOT, "  %5.2f%%  x%s  %s%s",
                share.fraction() * 100.0D, formatWeight(share.weight()), share.key(),
                name.isEmpty() ? "" : "  (" + name + ")"));
        }
        return lines;
    }

    /** The item's display name, made safe for one TSV cell. */
    private static String nameOf(String key) {
        String name = graph == null ? null : graph.displayName(key);
        return name == null ? "" : name.replaceAll("[\\t\\r\\n]+", " ").trim();
    }

    /**
     * Where an item's tier came from: {@code override} (forced by the config), {@code declared}
     * (a materialScores entry), {@code no-recipe} (scored as a raw material, the case worth a
     * second look in a big pack) or {@code recipe}.
     */
    private static String reasonFor(String key) {
        if (overridesInUse.covers(key)) {
            return "override";
        }
        if (declaredInUse.declaredFor(key) != null) {
            return "declared";
        }
        return graph != null && graph.recipe(key) == null ? "no-recipe" : "recipe";
    }
}
