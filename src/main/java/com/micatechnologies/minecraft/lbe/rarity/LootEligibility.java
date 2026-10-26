package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Which items may be handed out as loot at all, and why the rest may not.
 *
 * <p>Four filters, checked in this order, and the first that objects names the reason:</p>
 *
 * <ol>
 *   <li><b>{@code blacklist}</b> — the pack author's own list.</li>
 *   <li><b>{@code creative-list}</b> — items known to be creative-only or admin-only whose names
 *       give nothing away ({@code buildcraftlib:debugger}, the whole of CustomNPCs). A separate
 *       list from the blacklist so that a server with an existing config still picks it up: Forge
 *       never rewrites a list the file already has, but it does write a new key's default.</li>
 *   <li><b>{@code creative-name}</b> — anything whose registry path or English name has a word
 *       starting with {@code creative} or {@code admin}. See {@link #looksCreativeOrAdmin}.</li>
 *   <li><b>{@code not-whitelisted}</b> — when the whitelist is non-empty, everything it does not
 *       name.</li>
 * </ol>
 *
 * <p>The reason these exist at all: an item with no recipe scores as a raw material, and raw
 * materials land in the common tier. That is right for dirt and logs, and badly wrong for a
 * creative generator, which is uncraftable precisely because no survival player is meant to own
 * one. The scorer has no way to tell the two apart from recipe data, so they are kept out of the
 * pool before it is asked.</p>
 *
 * <p>Like the blacklist, this is a <b>loot</b> filter, not a registry filter: an excluded item is
 * still scored as an ingredient of anything that is not excluded.</p>
 */
public final class LootEligibility {

    public static final String BLACKLIST = "blacklist";
    public static final String CREATIVE_LIST = "creative-list";
    public static final String CREATIVE_NAME = "creative-name";
    public static final String NOT_WHITELISTED = "not-whitelisted";

    /** Word prefixes that mark an item as not meant for survival play. */
    private static final String[] CREATIVE_WORDS = { "creative", "admin" };

    private final KeyFilter blacklist;
    private final KeyFilter creativeItems;
    private final boolean excludeCreativeNames;
    private final KeyFilter whitelist;

    public LootEligibility(KeyFilter blacklist, KeyFilter creativeItems,
                           boolean excludeCreativeNames, KeyFilter whitelist) {
        this.blacklist = blacklist == null ? KeyFilter.empty() : blacklist;
        this.creativeItems = creativeItems == null ? KeyFilter.empty() : creativeItems;
        this.excludeCreativeNames = excludeCreativeNames;
        this.whitelist = whitelist == null ? KeyFilter.empty() : whitelist;
    }

    /** Everything is eligible. */
    public static LootEligibility everything() {
        return new LootEligibility(null, null, false, null);
    }

    /** Just a blacklist, the way the catalogue worked before the other filters existed. */
    public static LootEligibility blacklistOnly(KeyFilter blacklist) {
        return new LootEligibility(blacklist, null, false, null);
    }

    /**
     * Why {@code key} may not be loot, or {@code null} if it may.
     *
     * @param key         a normalised item key
     * @param displayName the item's English name, or {@code null} if it could not be read
     */
    public String exclusionReason(String key, String displayName) {
        if (blacklist.matches(key)) {
            return BLACKLIST;
        }
        if (creativeItems.matches(key)) {
            return CREATIVE_LIST;
        }
        if (excludeCreativeNames && key != null
            && (looksCreativeOrAdmin(pathOf(key)) || looksCreativeOrAdmin(displayName))) {
            return CREATIVE_NAME;
        }
        if (!whitelist.isEmpty() && !whitelist.matches(key)) {
            return NOT_WHITELISTED;
        }
        return null;
    }

    /**
     * Does any word of {@code name} start with {@code creative} or {@code admin}?
     *
     * <p>Words are split on anything that is not a letter or digit and on lower-to-upper case
     * changes, so {@code locomotive_creative}, {@code CreativeEngine} and "Admin Steam Producer"
     * all match. A word has to <i>start</i> with the marker, not merely contain it: a substring
     * test would also catch {@code badminton} and {@code procreative}, and over a registry of tens
     * of thousands of names that kind of accident is a certainty.</p>
     *
     * <p>A run-together path such as {@code casecreative} is missed by the path and caught by the
     * name ("Creative computer case"), which is why both are checked.</p>
     */
    public static boolean looksCreativeOrAdmin(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (String word : words(name)) {
            for (String marker : CREATIVE_WORDS) {
                if (word.startsWith(marker)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> words(String name) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        char previous = 0;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean boundary = !Character.isLetterOrDigit(c)
                || (Character.isUpperCase(c) && Character.isLowerCase(previous));
            if (boundary && word.length() > 0) {
                words.add(word.toString().toLowerCase(Locale.ROOT));
                word.setLength(0);
            }
            if (Character.isLetterOrDigit(c)) {
                word.append(c);
            }
            previous = c;
        }
        if (word.length() > 0) {
            words.add(word.toString().toLowerCase(Locale.ROOT));
        }
        return words;
    }

    /** {@code "railcraft:admin_steam_producer#0"} → {@code "admin_steam_producer"}. */
    private static String pathOf(String key) {
        String name = ItemKeys.registryName(key);
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    /** Lines from every list that did not parse, prefixed with which list. */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (String problem : blacklist.problems()) {
            problems.add("blacklist " + problem);
        }
        for (String problem : creativeItems.problems()) {
            problems.add("creativeItems " + problem);
        }
        for (String problem : whitelist.problems()) {
            problems.add("whitelist " + problem);
        }
        return problems;
    }
}
