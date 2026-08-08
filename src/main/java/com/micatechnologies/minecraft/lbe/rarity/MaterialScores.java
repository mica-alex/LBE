package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Declared scores for materials the recipe walk cannot value — the model's answer to <b>scarcity</b>.
 *
 * <h2>Why this has to exist</h2>
 *
 * <p>The scoring model measures <i>effort</i>: what went into a thing, how many steps, how many
 * kinds. That is derivable from recipe data, and it is most of what makes an item valuable. What is
 * <b>not</b> in recipe data, anywhere, at all, is how hard the raw material was to obtain — and to
 * the recipe graph a diamond and a lump of cobblestone are indistinguishable, because neither is
 * crafted from anything.</p>
 *
 * <p>That is not a tuning problem. No weighting recovers "diamonds are one block in eight hundred at
 * y=12"; the fact simply is not present in the input. The mod's first acceptance test caught it
 * immediately — an iron pickaxe outscored a diamond one, because smelting an ingot is a crafting step
 * and finding a diamond is not.</p>
 *
 * <h2>How it differs from {@link RarityOverrides}</h2>
 *
 * <p>The two look similar and do very different jobs, and confusing them is the most likely way for
 * a pack author to be surprised:</p>
 *
 * <ul>
 *   <li>{@link RarityOverrides} sets an item's <b>final tier</b>. It applies to that item and
 *       <i>nothing else</i> — declaring diamond "rare" does not make a diamond pickaxe rare.</li>
 *   <li>This sets an item's <b>score</b>, which flows through the recursion into everything crafted
 *       from it. Declaring a diamond worth 8 makes diamond tools, diamond armour and diamond blocks
 *       more valuable automatically, along with every modded recipe that uses one.</li>
 * </ul>
 *
 * <p>Where a score is declared, <b>the item's own recipe is not walked at all</b>. That is deliberate
 * twice over: the declaration is meant to be the final word, and it conveniently severs the
 * ingot↔block cycles that otherwise dominate a modded graph.</p>
 *
 * <p>Scores are in units of {@link RarityWeights#rawMaterialBase} — an ordinary raw material is 1.</p>
 */
public final class MaterialScores {

    private final Map<String, Double> exact;
    private final List<WildcardRule> wildcards;
    private final List<String> problems;

    private MaterialScores(Map<String, Double> exact, List<WildcardRule> wildcards,
                           List<String> problems) {
        this.exact = exact;
        this.wildcards = wildcards;
        this.problems = Collections.unmodifiableList(problems);
    }

    /** A table with nothing in it — every item falls back to the recipe walk. */
    public static MaterialScores empty() {
        return new MaterialScores(Collections.<String, Double>emptyMap(),
            Collections.<WildcardRule>emptyList(), new ArrayList<String>());
    }

    /**
     * Parse {@code key=score} config lines. Never throws; malformed lines land in
     * {@link #problems()}.
     */
    public static MaterialScores parse(String[] lines) {
        Map<String, Double> exact = new HashMap<>();
        List<WildcardRule> wildcards = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (lines == null) {
            return new MaterialScores(exact, wildcards, problems);
        }

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index] == null ? "" : lines[index].trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                problems.add("line " + (index + 1) + ": expected 'key=score', got '" + line + "'");
                continue;
            }
            String key = ItemKeys.normalise(line.substring(0, equals));
            if (key == null) {
                problems.add("line " + (index + 1) + ": empty item key in '" + line + "'");
                continue;
            }
            double score;
            try {
                score = Double.parseDouble(line.substring(equals + 1).trim());
            }
            catch (NumberFormatException e) {
                problems.add("line " + (index + 1) + ": '" + line.substring(equals + 1).trim()
                    + "' is not a number");
                continue;
            }
            if (Double.isNaN(score) || Double.isInfinite(score) || score < 0.0D) {
                problems.add("line " + (index + 1) + ": score must be a non-negative number");
                continue;
            }
            if (ItemKeys.META_WILDCARD.equals(ItemKeys.metaPart(key))) {
                wildcards.add(new WildcardRule(key, score));
            }
            else {
                exact.put(key, score);
            }
        }
        return new MaterialScores(exact, wildcards, problems);
    }

    /**
     * The declared score for {@code key}, or {@code null} to let the recipe walk decide.
     *
     * <p>Exact keys beat wildcards; among wildcards, the first in file order wins. Same precedence
     * as {@link RarityOverrides}, so the two files read the same way.</p>
     */
    public Double declaredFor(String key) {
        Double direct = exact.get(key);
        if (direct != null) {
            return direct;
        }
        for (WildcardRule rule : wildcards) {
            if (ItemKeys.matches(rule.pattern, key)) {
                return rule.score;
            }
        }
        return null;
    }

    /** How many rules parsed successfully. */
    public int size() {
        return exact.size() + wildcards.size();
    }

    /** Lines that did not parse, in file order. Log these at {@code WARN}. */
    public List<String> problems() {
        return problems;
    }

    private static final class WildcardRule {
        final String pattern;
        final double score;

        WildcardRule(String pattern, double score) {
            this.pattern = pattern;
            this.score = score;
        }
    }
}
