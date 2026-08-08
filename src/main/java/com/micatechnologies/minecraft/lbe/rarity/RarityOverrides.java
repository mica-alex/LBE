package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pack author's veto: a table of {@code key=tier} lines that replace whatever the scorer decided.
 *
 * <p>The automatic scoring is a heuristic over data mod authors never wrote for us, and on a big
 * enough pack it will be wrong about something. This is the escape hatch, and it is meant to be used
 * — not a debugging aid but a first-class part of the config, which is why it accepts the forms a
 * person would actually type:</p>
 *
 * <pre>
 *   minecraft:diamond=rare          # by tier name
 *   minecraft:diamond=2             # by tier number (COMMON 0 .. LEGENDARY 3)
 *   minecraft:wool#*=common         # every metadata variant
 *   thermalfoundation:material#1024=legendary
 *   # lines starting with # are comments, and blank lines are ignored
 * </pre>
 *
 * <p><b>Exact keys beat wildcards</b>, so a pack can say "all wool is common, except that one" and
 * have it mean what it looks like. Among wildcards, the <b>first match in file order wins</b>, which
 * makes the file readable top-to-bottom and lets an author put their specific rules above their
 * catch-alls.</p>
 *
 * <p>Malformed lines are collected in {@link #problems()} rather than thrown. A typo on line 40 of a
 * config file must not stop a world from loading, and an author needs to be told about all of their
 * typos at once rather than one server start at a time.</p>
 */
public final class RarityOverrides {

    private final Map<String, Rarity> exact;
    private final List<WildcardRule> wildcards;
    private final List<String> problems;

    private RarityOverrides(Map<String, Rarity> exact, List<WildcardRule> wildcards,
                            List<String> problems) {
        this.exact = exact;
        this.wildcards = wildcards;
        this.problems = Collections.unmodifiableList(problems);
    }

    /** An override table with nothing in it. */
    public static RarityOverrides empty() {
        return new RarityOverrides(Collections.<String, Rarity>emptyMap(),
            Collections.<WildcardRule>emptyList(), new ArrayList<String>());
    }

    /**
     * Parse config lines. Never throws.
     *
     * @param lines raw lines, as the config array holds them; {@code null} is treated as empty
     */
    public static RarityOverrides parse(String[] lines) {
        Map<String, Rarity> exact = new HashMap<>();
        List<WildcardRule> wildcards = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (lines == null) {
            return new RarityOverrides(exact, wildcards, problems);
        }

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index] == null ? "" : lines[index].trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                problems.add("line " + (index + 1) + ": expected 'key=tier', got '" + line + "'");
                continue;
            }
            String key = ItemKeys.normalise(line.substring(0, equals));
            Rarity tier = Rarity.byId(line.substring(equals + 1));
            if (key == null) {
                problems.add("line " + (index + 1) + ": empty item key in '" + line + "'");
                continue;
            }
            if (tier == null) {
                problems.add("line " + (index + 1) + ": '" + line.substring(equals + 1).trim()
                    + "' is not a tier name (common/uncommon/rare/legendary) or number (0-3)");
                continue;
            }
            if (ItemKeys.META_WILDCARD.equals(ItemKeys.metaPart(key))) {
                wildcards.add(new WildcardRule(key, tier));
            }
            else {
                Rarity previous = exact.put(key, tier);
                if (previous != null && previous != tier) {
                    problems.add("line " + (index + 1) + ": '" + key + "' was already set to "
                        + previous.id() + "; using " + tier.id());
                }
            }
        }
        return new RarityOverrides(exact, wildcards, problems);
    }

    /**
     * The tier forced for {@code key}, or {@code null} to let the scorer decide.
     *
     * @param key a normalised, concrete (non-wildcard) item key
     */
    public Rarity forKey(String key) {
        Rarity direct = exact.get(key);
        if (direct != null) {
            return direct;
        }
        for (WildcardRule rule : wildcards) {
            if (ItemKeys.matches(rule.pattern, key)) {
                return rule.tier;
            }
        }
        return null;
    }

    /** {@code true} if this table would decide the tier of {@code key}. */
    public boolean covers(String key) {
        return forKey(key) != null;
    }

    /** How many rules parsed successfully. */
    public int size() {
        return exact.size() + wildcards.size();
    }

    /**
     * Human-readable complaints about lines that did not parse, in file order. Empty when the file
     * was clean. Log these at {@code WARN} on load — silently dropping an author's override is worse
     * than the typo that caused it.
     */
    public List<String> problems() {
        return problems;
    }

    private static final class WildcardRule {
        final String pattern;
        final Rarity tier;

        WildcardRule(String pattern, Rarity tier) {
            this.pattern = pattern;
            this.tier = tier;
        }
    }
}
