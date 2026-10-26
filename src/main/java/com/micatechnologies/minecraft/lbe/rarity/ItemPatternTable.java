package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A number per item pattern: the grammar behind {@code loot.lootWeights} and
 * {@code loot.categoryMaxStack}.
 *
 * <pre>
 *   minecraft:bread=4          # exactly that item (metadata 0)
 *   minecraft:fish#*=3         # every metadata variant
 *   mts:*=0.25                 # every item from that mod
 *   &#64;food=3                     # every item in a category; see {@link ItemCategory}
 * </pre>
 *
 * <p><b>Most specific wins.</b> An exact item beats {@code #*}, which beats {@code modid:*}, and
 * any of those beats every category. Only when no item or mod rule applies do the categories
 * count, and an item can be in several; how those combine is the caller's business
 * ({@link #categoryValues}), because weights multiply and stack caps do not. A later line for the
 * same pattern replaces an earlier one.</p>
 *
 * <p>Never throws, like the other config parsers: a malformed line goes to {@link #problems()}.</p>
 */
public final class ItemPatternTable {

    private final Map<String, Double> exact;
    private final Map<String, Double> anyMeta;
    private final Map<String, Double> wholeMods;
    private final Map<String, Double> categories;
    private final List<String> problems;

    private ItemPatternTable(Map<String, Double> exact, Map<String, Double> anyMeta,
                             Map<String, Double> wholeMods, Map<String, Double> categories,
                             List<String> problems) {
        this.exact = exact;
        this.anyMeta = anyMeta;
        this.wholeMods = wholeMods;
        this.categories = categories;
        this.problems = Collections.unmodifiableList(problems);
    }

    /** A table with no rules. */
    public static ItemPatternTable empty() {
        return parse(null);
    }

    /**
     * Parse config lines. Blank lines and lines starting with {@code #} are ignored.
     */
    public static ItemPatternTable parse(String[] lines) {
        Map<String, Double> exact = new HashMap<>();
        Map<String, Double> anyMeta = new HashMap<>();
        Map<String, Double> wholeMods = new HashMap<>();
        Map<String, Double> categories = new HashMap<>();
        List<String> problems = new ArrayList<>();
        if (lines == null) {
            return new ItemPatternTable(exact, anyMeta, wholeMods, categories, problems);
        }

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index] == null ? "" : lines[index].trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String where = "line " + (index + 1) + ": '" + line + "' ";
            int equals = line.lastIndexOf('=');
            if (equals < 0) {
                problems.add(where + "has no '='; expected 'pattern=number'");
                continue;
            }
            String pattern = line.substring(0, equals).trim();
            double value;
            try {
                value = Double.parseDouble(line.substring(equals + 1).trim());
            }
            catch (NumberFormatException e) {
                problems.add(where + "has no number after '='");
                continue;
            }
            if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
                problems.add(where + "must be a number of 0 or more");
                continue;
            }

            if (pattern.startsWith("@")) {
                String category = pattern.substring(1).trim().toLowerCase(Locale.ROOT);
                if (!ItemCategory.ALL.contains(category)) {
                    problems.add(where + "names an unknown category; expected one of @"
                        + String.join(", @", ItemCategory.ALL));
                    continue;
                }
                categories.put(category, value);
                continue;
            }
            int colon = pattern.indexOf(':');
            if (colon <= 0) {
                problems.add(where + "has no mod id; expected 'modid:name', 'modid:name#*', "
                    + "'modid:*' or '@category'");
                continue;
            }
            String remainder = pattern.substring(colon + 1).trim();
            if (ItemKeys.META_WILDCARD.equals(remainder)) {
                wholeMods.put(pattern.substring(0, colon).trim().toLowerCase(Locale.ROOT), value);
                continue;
            }
            String key = ItemKeys.normalise(pattern);
            if (ItemKeys.META_WILDCARD.equals(ItemKeys.metaPart(key))) {
                anyMeta.put(ItemKeys.registryName(key), value);
            }
            else {
                exact.put(key, value);
            }
        }
        return new ItemPatternTable(exact, anyMeta, wholeMods, categories, problems);
    }

    /**
     * The value of the most specific item or mod rule matching {@code key}, or {@code null} if
     * only categories (or nothing) apply.
     */
    public Double specificValue(String key) {
        Double value = exact.get(key);
        if (value == null) {
            value = anyMeta.get(ItemKeys.registryName(key));
        }
        if (value == null) {
            value = wholeMods.get(ItemKeys.modId(key));
        }
        return value;
    }

    /** The values of every category rule naming one of {@code itemCategories}. */
    public List<Double> categoryValues(Collection<String> itemCategories) {
        List<Double> values = new ArrayList<>();
        if (itemCategories == null || categories.isEmpty()) {
            return values;
        }
        for (String category : itemCategories) {
            Double value = categories.get(category);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * The draw weight for an item: the most specific item or mod rule, otherwise every matching
     * category's weight <b>multiplied</b> together, otherwise 1.
     *
     * <p>Multiplied because categories describe independent things: {@code @ore=2} and
     * {@code @block=0.5} on an ore block should land at 1, not at whichever line came first.</p>
     */
    public double weightOf(String key, Collection<String> itemCategories) {
        Double specific = specificValue(key);
        if (specific != null) {
            return specific;
        }
        double weight = 1.0D;
        for (double value : categoryValues(itemCategories)) {
            weight *= value;
        }
        return weight;
    }

    /**
     * A stack cap for an item: the most specific item or mod rule, otherwise the <b>largest</b>
     * matching category cap, otherwise {@code null} (use the tier's own cap).
     *
     * <p>The largest, not a product: two caps of 16 should still mean 16.</p>
     */
    public Integer capOf(String key, Collection<String> itemCategories) {
        Double specific = specificValue(key);
        if (specific != null) {
            return Math.max(1, (int) Math.round(specific));
        }
        Double largest = null;
        for (double value : categoryValues(itemCategories)) {
            if (largest == null || value > largest) {
                largest = value;
            }
        }
        return largest == null ? null : Math.max(1, (int) Math.round(largest));
    }

    /** {@code true} when there are no rules at all. */
    public boolean isEmpty() {
        return exact.isEmpty() && anyMeta.isEmpty() && wholeMods.isEmpty() && categories.isEmpty();
    }

    /** Lines that did not parse, in file order. Log these at {@code WARN}. */
    public List<String> problems() {
        return problems;
    }
}
