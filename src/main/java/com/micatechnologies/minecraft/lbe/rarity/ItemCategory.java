package com.micatechnologies.minecraft.lbe.rarity;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The coarse kinds of item that loot weights can name as {@code @food}, {@code @tool} and so on.
 *
 * <p>Plain string ids, worked out on the game side ({@code catalog.ForgeItemGraph}) from the item's
 * class and its ore-dictionary names, so that nobody has to write a list. An item can be in several
 * at once: an ore block is both {@code @ore} and {@code @block}.</p>
 */
public final class ItemCategory {

    /** {@code ItemFood}. */
    public static final String FOOD = "food";
    /** {@code ItemTool}, {@code ItemHoe}, or anything that reports a harvest tool class. */
    public static final String TOOL = "tool";
    /** {@code ItemSword}, {@code ItemBow}. */
    public static final String WEAPON = "weapon";
    /** {@code ItemArmor}. */
    public static final String ARMOR = "armor";
    /** An ore-dictionary name starting {@code ingot}. */
    public static final String INGOT = "ingot";
    /** An ore-dictionary name starting {@code gem}. */
    public static final String GEM = "gem";
    /** An ore-dictionary name starting {@code dust}. */
    public static final String DUST = "dust";
    /** An ore-dictionary name starting {@code ore}. */
    public static final String ORE = "ore";
    /** {@code ItemBlock}. */
    public static final String BLOCK = "block";

    /** Every category id, in the order the config documents them. */
    public static final Set<String> ALL = Collections.unmodifiableSet(new LinkedHashSet<>(
        Arrays.asList(FOOD, TOOL, WEAPON, ARMOR, INGOT, GEM, DUST, ORE, BLOCK)));

    /** The ore-dictionary prefixes that map to a category of the same name. */
    public static final String[] ORE_DICT_PREFIXES = { INGOT, GEM, DUST, ORE };

    private ItemCategory() {
        throw new AssertionError("No instances.");
    }

    /**
     * The category an ore-dictionary name puts an item in, or {@code null}.
     *
     * <p>The prefix must be followed by an upper-case letter, the ore dictionary's own convention
     * ({@code ingotCopper}, {@code oreIron}): a bare {@code startsWith("ore")} would also take
     * {@code oreberry}-style names that are not ores at all.</p>
     */
    public static String forOreName(String oreName) {
        if (oreName == null) {
            return null;
        }
        for (String prefix : ORE_DICT_PREFIXES) {
            if (oreName.length() > prefix.length() && oreName.startsWith(prefix)
                && Character.isUpperCase(oreName.charAt(prefix.length()))) {
                return prefix;
            }
        }
        return null;
    }
}
