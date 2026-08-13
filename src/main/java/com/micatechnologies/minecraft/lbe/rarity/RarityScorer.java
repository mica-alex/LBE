package com.micatechnologies.minecraft.lbe.rarity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns an {@link ItemGraph} into a number per item. The heart of the mod.
 *
 * <p>An item's score is the cost of everything that goes into it, plus what the item itself looks
 * like:</p>
 *
 * <pre>
 *   declared in MaterialScores:
 *       score = declaredScore + properties(item)      // the recipe is not walked at all
 *
 *   raw material (no recipe):
 *       score = rawMaterialBase + properties(item)
 *
 *   crafted:
 *       ingredientSum = Σ over consumed slots of min(COST of the slot's alternatives)
 *       dearest       = max over consumed slots of that same per-slot cost
 *       craftCost     = max( dearest / outputCount,
 *                            (ingredientSum / outputCount) ^ bulkCompression )
 *       cost          = craftCostWeight  · craftCost                       // what parents pay
 *                     + varietyWeight    · distinctIngredientCount
 *                     + properties(item)
 *       score         = cost + depthWeight · ln(1 + depth)                 // what tiers are cut from
 *
 *   properties(item) = unstackableWeight    · [maxStackSize == 1]
 *                    + durabilityWeight     · ln(1 + maxDurability)
 *                    + enchantabilityWeight · enchantability
 *                    + vanillaRarityWeight  · EnumRarity ordinal
 *                    + blockWeight          · [is a block]
 *                    + containerItemWeight  · [has a container item]
 * </pre>
 *
 * <p>Note the two-value result. <b>Ingredients contribute their {@code cost}, not their
 * {@code score}</b> — the depth bonus is paid once, by the finished item, and never charged again to
 * whatever consumes it. See {@code Eval} for what happens when it is not.</p>
 *
 * <p>The recursion is memoised, so scoring a whole modded registry is linear in the number of
 * recipe edges rather than exponential in tree depth. Every coefficient lives in
 * {@link RarityWeights}, where each one is justified.</p>
 *
 * <h2>What this model cannot see</h2>
 *
 * <p><b>Scarcity.</b> Recipe data says nothing about how hard a material was to obtain, so to this
 * recursion a diamond and a lump of cobblestone are the same thing: neither is crafted from
 * anything. That is not a tuning problem — the fact is absent from the input — and it is what
 * {@link MaterialScores} exists to supply. Where a score is declared for an item, it is used
 * directly and that item's own recipe is not walked.</p>
 *
 * <h2>Cycles, and why the cache is conditional</h2>
 *
 * <p>Modded recipe graphs are <b>full of cycles</b>: ingot → block → ingot is the polite version, and
 * a tech pack will happily route ore → dust → ingot → ore through three mods and a grinder. The
 * recursion guards against them with a visiting set, and against merely-very-deep chains with
 * {@link RarityWeights#maxRecipeDepth}; either guard tripping scores the offending sub-item as if it
 * were raw.</p>
 *
 * <p>That truncation is what makes the cache subtle. A truncated score is <b>context-dependent</b> —
 * it is only that low because of what happened to be on the stack at the time — so caching it would
 * let one item's cycle permanently deflate an unrelated item that merely shares an ingredient, with
 * the damage depending on registry iteration order. So a result enters the permanent cache only if
 * <i>no</i> truncation occurred anywhere beneath it.</p>
 *
 * <p>That rule alone, though, is <b>exponential on exactly the graphs that need it most</b>. On a
 * vanilla-ish registry cycles are rare and nearly everything caches. On a tech pack, ore → dust →
 * ingot → block cycles sit underneath essentially all metal processing, so everything crafted from
 * metal — most of the pack — is transitively barred from the cache; with no memoisation at all for
 * those items, one query re-walks every <i>path</i> through the recipe graph rather than every
 * <i>node</i>, and with ore-dictionary slots fanning out dozens of alternatives the path count is
 * astronomical. The symptom is postInit never finishing: not an infinite loop, a finite walk that
 * will not complete in a human lifetime.</p>
 *
 * <p>Hence the second, <b>per-query</b> memo: truncated results are remembered for the duration of
 * a single top-level query and discarded before the next one. Within one query the context concern
 * is bounded — whatever a truncated value cost in accuracy, it cost this item only — while across
 * queries nothing leaks, which is the property the permanent-cache rule was protecting. Entries are
 * budget-aware: a value computed with little remaining depth is not reused where a deeper walk was
 * allowed, so memoisation never makes a score worse than the depth cap already made it.</p>
 *
 * <p><b>Not thread-safe.</b> One scorer, one thread, built once during postInit.</p>
 */
public final class RarityScorer {

    private final ItemGraph graph;
    private final RarityWeights weights;
    private final MaterialScores declared;

    /** Memoised results, holding only scores computed with no truncation beneath them. */
    private final Map<String, Eval> cache = new HashMap<>();

    /**
     * Memoised results for the current top-level query <b>only</b>, holding the scores that a
     * truncation beneath them barred from {@link #cache}. Cleared at every public entry point, so
     * nothing in here can ever influence another item's score — see the class doc for why this
     * two-cache split is what keeps dense cyclic graphs linear without reintroducing the
     * cross-item contamination the permanent cache refuses.
     */
    private final Map<String, Scoped> scoped = new HashMap<>();

    /** Keys currently on the recursion stack — the cycle guard. */
    private final Set<String> visiting = new HashSet<>();

    /**
     * Monotonic count of truncations (cycle hits and depth-cap hits). Compared before and after a
     * sub-tree is evaluated to decide whether that sub-tree's result is safe to cache; the absolute
     * value is meaningless, only the difference matters.
     */
    private int truncations;

    /** Score with no declared material values — everything comes from the recipe walk. */
    public RarityScorer(ItemGraph graph, RarityWeights weights) {
        this(graph, weights, MaterialScores.empty());
    }

    public RarityScorer(ItemGraph graph, RarityWeights weights, MaterialScores declared) {
        if (graph == null) {
            throw new IllegalArgumentException("RarityScorer needs a graph");
        }
        this.graph = graph;
        this.weights = weights == null ? new RarityWeights() : weights;
        this.declared = declared == null ? MaterialScores.empty() : declared;
    }

    /** The weighting in force. */
    public RarityWeights weights() {
        return weights;
    }

    /** Score one item. Higher is more valuable. */
    public double score(String key) {
        return query(key).score;
    }

    /**
     * How many crafting steps separate this item from raw materials. {@code 0} for a raw material,
     * {@code 1} for something crafted directly from raw materials, and so on.
     */
    public int depth(String key) {
        return query(key).depth;
    }

    /**
     * Score every item in the graph, in the graph's own iteration order.
     *
     * <p>This is the call that runs once at postInit over the whole registry. It is
     * {@code O(recipe edges)} thanks to the memoisation, not {@code O(items × depth)}.</p>
     */
    public Map<String, Double> scoreAll() {
        return scoreAll(null);
    }

    /**
     * {@link #scoreAll()}, reporting progress as it goes.
     *
     * @param progress called after each item with how many have been scored so far, ending at
     *                 {@code graph.keys().size()}. This is how the catalogue drives the loading
     *                 screen's progress bar without this package knowing what a loading screen
     *                 is — keep it that way: the callback is plain {@code java.util.function},
     *                 and nothing Minecraft-shaped may replace it. May be {@code null}.
     */
    public Map<String, Double> scoreAll(java.util.function.IntConsumer progress) {
        Collection<String> keys = graph.keys();
        Map<String, Double> scores = new LinkedHashMap<>(Math.max(16, keys.size() * 2));
        int scored = 0;
        for (String key : keys) {
            scores.put(key, score(key));
            scored++;
            if (progress != null) {
                progress.accept(scored);
            }
        }
        return scores;
    }

    /**
     * A human-readable breakdown of how {@code key} got its score — every term, with its value.
     *
     * <p>Exists for the {@code /lbe rarity} command. Tuning the weights blind is miserable: an
     * author who thinks a particular item landed in the wrong tier needs to see <i>which</i> term put
     * it there before they can decide whether to change a weight or just write an override.</p>
     */
    public String explain(String key) {
        ItemProfile profile = graph.profile(key);
        CraftingRecipe recipe = graph.recipe(key);
        Double declaredScore = declared.declaredFor(key);
        // One query: the per-slot breakdown below deliberately reuses the scoped memo this warms.
        Eval result = query(key);
        StringBuilder out = new StringBuilder();
        out.append(key).append(" -> ").append(round(result.score))
            .append(" (depth ").append(result.depth).append(")\n");

        if (declaredScore != null) {
            out.append("  declared value    : ").append(round(declaredScore))
                .append("  (config materialScores — the recipe was not walked)\n");
        }
        else if (recipe == null || recipe.isEmpty()) {
            out.append("  raw material base : ").append(round(weights.rawMaterialBase)).append('\n');
        }
        else {
            double ingredientSum = 0.0D;
            double dearest = 0.0D;
            for (List<String> slot : recipe.ingredients()) {
                double slotCost = cheapestAlternative(slot, 1).cost;
                ingredientSum += slotCost;
                dearest = Math.max(dearest, slotCost);
            }
            double compressed = Math.pow(ingredientSum / recipe.outputCount(), weights.bulkCompression);
            double craftCost = craftCost(ingredientSum, dearest, recipe.outputCount());
            out.append("  ingredients       : ").append(recipe.totalIngredientCount())
                .append(" slots, ").append(recipe.distinctIngredientCount()).append(" distinct, sum ")
                .append(round(ingredientSum)).append(", dearest ").append(round(dearest)).append('\n');
            out.append("  craft cost        : ").append(round(weights.craftCostWeight * craftCost))
                .append("  (compressed ").append(round(compressed))
                .append(", dearest-ingredient floor ").append(round(dearest / recipe.outputCount()))
                .append(craftCost > compressed ? " — FLOOR APPLIED" : "").append(")\n");
            out.append("  depth             : ")
                .append(round(weights.depthWeight * Math.log1p(result.depth))).append('\n');
            out.append("  variety           : ")
                .append(round(weights.varietyWeight * recipe.distinctIngredientCount())).append('\n');
        }
        appendPropertyTerms(out, profile);
        return out.toString();
    }

    private void appendPropertyTerms(StringBuilder out, ItemProfile profile) {
        if (profile.isUnstackable()) {
            out.append("  unstackable       : ").append(round(weights.unstackableWeight)).append('\n');
        }
        if (profile.maxDurability() > 0) {
            out.append("  durability        : ")
                .append(round(weights.durabilityWeight * Math.log1p(profile.maxDurability())))
                .append("  (").append(profile.maxDurability()).append(")\n");
        }
        if (profile.enchantability() > 0) {
            out.append("  enchantability    : ")
                .append(round(weights.enchantabilityWeight * profile.enchantability()))
                .append("  (").append(profile.enchantability()).append(")\n");
        }
        if (profile.vanillaRarityOrdinal() > 0) {
            out.append("  declared rarity   : ")
                .append(round(weights.vanillaRarityWeight * profile.vanillaRarityOrdinal()))
                .append("  (EnumRarity ").append(profile.vanillaRarityOrdinal()).append(")\n");
        }
        if (profile.isBlock()) {
            out.append("  is a block        : ").append(round(weights.blockWeight)).append('\n');
        }
        if (profile.hasContainerItem()) {
            out.append("  container item    : ").append(round(weights.containerItemWeight)).append('\n');
        }
    }

    // --- the recursion ---------------------------------------------------------------------------

    /** The top-level entry: one fresh query, with the per-query memo wiped before it starts. */
    private Eval query(String key) {
        scoped.clear();
        return evaluate(key, 0);
    }

    private Eval evaluate(String key, int depth) {
        Eval cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        if (depth >= weights.maxRecipeDepth || visiting.contains(key)) {
            // Either a cycle or a chain longer than we are willing to walk. Score it as if raw: an
            // understatement, but a bounded and deterministic one. Never memoised anywhere, not
            // even in the scoped map: this value stands for "key, as seen from inside its own
            // walk", which is not an answer to any other question — and it is O(1) to recreate.
            truncations++;
            return Eval.leaf(rawScore(key));
        }

        // The remaining walk budget below this point. A truncated result memoised at one budget may
        // be reused only where the budget is no larger, so reuse never costs accuracy the depth cap
        // had not already taken.
        int budget = weights.maxRecipeDepth - depth;
        Scoped remembered = scoped.get(key);
        if (remembered != null && remembered.budget >= budget) {
            return remembered.eval;
        }

        visiting.add(key);
        int truncationsBefore = truncations;
        Eval result;
        try {
            result = compute(key, depth);
        }
        finally {
            visiting.remove(key);
        }
        if (truncations == truncationsBefore) {
            cache.put(key, result);
        }
        else {
            // Barred from the permanent cache, but remembered for the rest of THIS query. Without
            // this, every path through the graph is re-walked and a query above a dense cycle
            // (any tech pack's metal processing) goes exponential — the postInit hang.
            scoped.put(key, new Scoped(result, budget));
        }
        return result;
    }

    private Eval compute(String key, int depth) {
        ItemProfile profile = graph.profile(key);
        double properties = propertyScore(profile);

        // A declared value is the final word: it short-circuits the recipe walk entirely, which is
        // both the intended semantics and what breaks the ingot<->block cycles for these items.
        Double declaredScore = declared.declaredFor(key);
        if (declaredScore != null) {
            return Eval.leaf(declaredScore + properties);
        }

        CraftingRecipe recipe = graph.recipe(key);

        if (recipe == null || recipe.isEmpty()) {
            return Eval.leaf(weights.rawMaterialBase + properties);
        }

        double ingredientSum = 0.0D;
        double dearestIngredient = 0.0D;
        int deepestIngredient = -1;
        for (List<String> slot : recipe.ingredients()) {
            Eval cheapest = cheapestAlternative(slot, depth + 1);
            // Ingredients contribute their COST, not their score — see Eval. Charging the parent for
            // a child's depth bonus counts the same chain twice, and compounds: a wooden pickaxe is
            // three steps from a log, and paying for the stick's depth, then the plank's depth
            // inside that, then adding its own, made it score as rare on nothing but chain length.
            ingredientSum += cheapest.cost;
            if (cheapest.cost > dearestIngredient) {
                dearestIngredient = cheapest.cost;
            }
            if (cheapest.depth > deepestIngredient) {
                deepestIngredient = cheapest.depth;
            }
        }

        double craftCost = craftCost(ingredientSum, dearestIngredient, recipe.outputCount());
        int ownDepth = deepestIngredient + 1;

        double cost = weights.craftCostWeight * craftCost
            + weights.varietyWeight * recipe.distinctIngredientCount()
            + properties;
        return Eval.crafted(cost, weights.depthWeight * Math.log1p(ownDepth), ownDepth);
    }

    /**
     * What a recipe's inputs cost, compressed for bulk but floored at the dearest single ingredient.
     *
     * <p>The compression exponent is what stops nine ingots scoring nine times an ingot — see
     * {@link RarityWeights#bulkCompression}. Applied to the raw sum it has a side effect that is
     * clearly wrong, though: it discounts <i>every</i> input equally, so a recipe with one very
     * expensive ingredient comes out worth less than that ingredient on its own. A beacon scoring
     * below the nether star inside it is the case that made this obvious.</p>
     *
     * <p>The floor fixes it with a rule that needs no tuning and cannot be argued with:
     * <b>crafting something can never make it worth less than the best thing you put in.</b>
     * Compression still governs everywhere the cost is spread across many cheap inputs, which is
     * where it was always meant to bite; the floor only takes over when a single ingredient
     * dominates, which is exactly when compressing was the wrong thing to do.</p>
     */
    private double craftCost(double ingredientSum, double dearestIngredient, int outputCount) {
        double compressed = Math.pow(ingredientSum / outputCount, weights.bulkCompression);
        // The floor is divided by the output count too — three of a thing each cost a third of the
        // dearest input, or the recipe would make uncrafting free value.
        return Math.max(dearestIngredient / outputCount, compressed);
    }

    /**
     * The cheapest way to satisfy one ingredient slot.
     *
     * <p>A slot with alternatives (an ore-dictionary ingredient, most often) is worth what its
     * cheapest member is worth, because a player holding any one of them has satisfied it. Scoring
     * such a slot at its average or its maximum would make an item look expensive purely because
     * some mod registered an exotic alternative nobody uses.</p>
     */
    private Eval cheapestAlternative(List<String> alternatives, int depth) {
        Eval best = null;
        for (String alternative : alternatives) {
            Eval candidate = evaluate(alternative, depth);
            // Compared on cost, because cost is what the recipe will actually be charged.
            if (best == null || candidate.cost < best.cost) {
                best = candidate;
            }
        }
        return best == null ? Eval.leaf(weights.rawMaterialBase) : best;
    }

    /** What an item is worth on its own properties alone, ignoring how it is made. */
    private double propertyScore(ItemProfile profile) {
        double score = 0.0D;
        if (profile.isUnstackable()) {
            score += weights.unstackableWeight;
        }
        if (profile.maxDurability() > 0) {
            score += weights.durabilityWeight * Math.log1p(profile.maxDurability());
        }
        score += weights.enchantabilityWeight * profile.enchantability();
        score += weights.vanillaRarityWeight * profile.vanillaRarityOrdinal();
        if (profile.isBlock()) {
            score += weights.blockWeight;
        }
        if (profile.hasContainerItem()) {
            score += weights.containerItemWeight;
        }
        return score;
    }

    /** The score an item would have if it had no recipe. The truncation fallback and the base case. */
    private double rawScore(String key) {
        Double declaredScore = declared.declaredFor(key);
        double base = declaredScore == null ? weights.rawMaterialBase : declaredScore;
        return base + propertyScore(graph.profile(key));
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    /**
     * One item's evaluation: what it is worth as loot, what it is worth as an <i>ingredient</i>, and
     * how deep its recipe chain runs.
     *
     * <p><b>{@code score} and {@code cost} differ by exactly the depth bonus</b>, and that difference
     * is the whole reason this is a class rather than a {@code double}.</p>
     *
     * <p>The depth term rewards progression: an item ten crafting steps from raw materials is a
     * milestone. But it must be paid <b>once</b>, by the finished item. Letting it flow into the
     * ingredient sum charges every parent for its children's chain length on top of its own, and the
     * error compounds with every level — which on real vanilla data put a <i>wooden pickaxe</i> in the
     * rare tier, three cheap steps from a log and worth nothing at all. Parents therefore pay
     * {@code cost}; only the tier calculation sees {@code score}.</p>
     *
     * <p>{@code cost} is clamped at zero. With a negative weight in play (and {@code blockWeight} is
     * negative by default) a cheap block's cost can go below zero, and a negative ingredient sum
     * raised to a fractional power is {@code NaN} — which would propagate silently through the whole
     * catalogue.</p>
     */
    private static final class Eval {

        /** What this item is worth as loot: {@link #cost} plus its own depth bonus. */
        final double score;

        /** What a recipe consuming this item pays for it. Never negative. */
        final double cost;

        /** Crafting steps from raw materials. */
        final int depth;

        private Eval(double score, double cost, int depth) {
            this.score = score;
            this.cost = cost;
            this.depth = depth;
        }

        /** A raw material or a declared value: no depth bonus, so score and cost coincide. */
        static Eval leaf(double value) {
            return new Eval(value, Math.max(0.0D, value), 0);
        }

        static Eval crafted(double cost, double depthBonus, int depth) {
            double clamped = Math.max(0.0D, cost);
            return new Eval(cost + depthBonus, clamped, depth);
        }
    }

    /**
     * A truncation-tainted result in the per-query memo, tagged with the walk budget it was
     * computed under. It answers later lookups only at that budget or less; a lookup with more
     * budget recomputes, because the deeper walk might genuinely do better.
     */
    private static final class Scoped {

        final Eval eval;

        /** Remaining recursion depth at the time this was computed. */
        final int budget;

        Scoped(Eval eval, int budget) {
            this.eval = eval;
            this.budget = budget;
        }
    }
}
