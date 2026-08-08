package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A list of patterns that an item key either matches or does not. Backs the config's blacklist and
 * whitelist.
 *
 * <p>Three pattern forms, and deliberately no more:</p>
 *
 * <pre>
 *   minecraft:diamond          # exactly that item (metadata 0)
 *   minecraft:wool#*           # every metadata variant of that item
 *   thermalexpansion:*         # every item from that mod
 * </pre>
 *
 * <p>The mod-wide form is the one that earns its keep. "Exclude everything from this mod" is the
 * single most common thing a pack author wants to say — creative-only content, a library mod's
 * internal items, a mod whose progression the scorer has no hope of reading correctly — and without
 * it they would be writing out hundreds of lines by hand.</p>
 *
 * <p>Full glob matching is not supported and should not be added. It reads as if it would be handy
 * and in practice it is an ambiguity trap ({@code *ore*} matches {@code storage_drawer}) over a
 * registry with tens of thousands of entries.</p>
 */
public final class KeyFilter {

    private final Set<String> exactKeys;
    private final Set<String> anyMetaNames;
    private final Set<String> wholeMods;
    private final List<String> problems;

    private KeyFilter(Set<String> exactKeys, Set<String> anyMetaNames, Set<String> wholeMods,
                      List<String> problems) {
        this.exactKeys = exactKeys;
        this.anyMetaNames = anyMetaNames;
        this.wholeMods = wholeMods;
        this.problems = problems;
    }

    /** A filter that matches nothing. */
    public static KeyFilter empty() {
        return new KeyFilter(new HashSet<String>(), new HashSet<String>(), new HashSet<String>(),
            new ArrayList<String>());
    }

    /**
     * Parse config lines. Blank lines and lines starting with {@code #} are ignored; anything else
     * that does not parse is reported through {@link #problems()} rather than thrown, for the same
     * reason as {@link RarityOverrides#parse(String[])} — a typo must not stop a world loading.
     */
    public static KeyFilter parse(String[] lines) {
        Set<String> exactKeys = new HashSet<>();
        Set<String> anyMetaNames = new HashSet<>();
        Set<String> wholeMods = new HashSet<>();
        List<String> problems = new ArrayList<>();
        if (lines == null) {
            return new KeyFilter(exactKeys, anyMetaNames, wholeMods, problems);
        }

        for (int index = 0; index < lines.length; index++) {
            String line = lines[index] == null ? "" : lines[index].trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                problems.add("line " + (index + 1) + ": '" + line
                    + "' has no mod id; expected 'modid:name', 'modid:name#*' or 'modid:*'");
                continue;
            }
            String modId = line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String remainder = line.substring(colon + 1).trim();
            if (ItemKeys.META_WILDCARD.equals(remainder)) {
                wholeMods.add(modId);
                continue;
            }
            String key = ItemKeys.normalise(line);
            if (key == null) {
                problems.add("line " + (index + 1) + ": empty key");
                continue;
            }
            if (ItemKeys.META_WILDCARD.equals(ItemKeys.metaPart(key))) {
                anyMetaNames.add(ItemKeys.registryName(key));
            }
            else {
                exactKeys.add(key);
            }
        }
        return new KeyFilter(exactKeys, anyMetaNames, wholeMods, problems);
    }

    /** Does any pattern in this filter match {@code key}? {@code key} must already be normalised. */
    public boolean matches(String key) {
        if (key == null) {
            return false;
        }
        if (exactKeys.contains(key)) {
            return true;
        }
        String registryName = ItemKeys.registryName(key);
        return anyMetaNames.contains(registryName) || wholeMods.contains(ItemKeys.modId(key));
    }

    /** How many patterns parsed successfully. */
    public int size() {
        return exactKeys.size() + anyMetaNames.size() + wholeMods.size();
    }

    /** {@code true} when there are no patterns at all — worth a fast path at the call site. */
    public boolean isEmpty() {
        return size() == 0;
    }

    /** Lines that did not parse, in file order. Log these at {@code WARN}. */
    public List<String> problems() {
        return problems;
    }
}
