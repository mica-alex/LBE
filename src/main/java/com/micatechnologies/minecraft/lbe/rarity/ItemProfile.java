package com.micatechnologies.minecraft.lbe.rarity;

/**
 * Everything the scorer knows about one item that is <b>not</b> about how it is made.
 *
 * <p>Immutable plain data with no Minecraft types, built on the game side by
 * {@code catalog.ItemProfiler} and consumed here. That boundary is the whole reason the rarity
 * engine is testable: a unit test hands {@link RarityScorer} a handful of these plus a
 * {@link ItemGraph} and asserts on the ordering that comes out, with no game instance anywhere.</p>
 *
 * <p><b>Keys.</b> {@link #key()} is {@code "modid:name#meta"} — the metadata suffix is mandatory
 * because a great many 1.12.2 items are only distinguishable by it (dye, wool, every "variants in
 * metadata" block a mod ships). {@link ItemKeys} builds and parses these; nothing else should be
 * doing string surgery on them.</p>
 */
public final class ItemProfile {

    private final String key;
    private final int maxStackSize;
    private final int maxDurability;
    private final int enchantability;
    private final boolean isBlock;
    private final int vanillaRarityOrdinal;
    private final boolean hasContainerItem;

    /**
     * @param key                  {@code "modid:name#meta"}; see {@link ItemKeys}
     * @param maxStackSize         1–64. A stack size of 1 is the game's own signal that an item is
     *                             individually significant (tools, armour, most machines)
     * @param maxDurability        0 for anything that is not a damageable tool
     * @param enchantability       Minecraft's {@code Item#getItemEnchantability}; 0 for most things.
     *                             Higher means better enchantments, which correlates with tier
     *                             (wood 15, stone 5, iron 14, diamond 10, gold 22) — note that this
     *                             correlation is <b>weak and non-monotonic</b>, which is why it
     *                             carries a small weight rather than a large one
     * @param isBlock              whether this is an {@code ItemBlock}. Blocks skew toward bulk
     *                             building material, so this contributes negatively by default
     * @param vanillaRarityOrdinal {@code EnumRarity} ordinal (0 common → 3 epic) as reported by the
     *                             item itself. When a mod author has bothered to set this, they have
     *                             told us their own opinion of the item's tier, and it is worth more
     *                             than anything we can infer
     * @param hasContainerItem     whether crafting with it returns a container (buckets, some
     *                             modded tools). Such items are reusable, which makes them worth more
     *                             than their recipe cost alone suggests
     */
    public ItemProfile(String key, int maxStackSize, int maxDurability, int enchantability,
                       boolean isBlock, int vanillaRarityOrdinal, boolean hasContainerItem) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("ItemProfile needs a key");
        }
        this.key = key;
        this.maxStackSize = Math.max(1, maxStackSize);
        this.maxDurability = Math.max(0, maxDurability);
        this.enchantability = Math.max(0, enchantability);
        this.isBlock = isBlock;
        this.vanillaRarityOrdinal = Math.max(0, vanillaRarityOrdinal);
        this.hasContainerItem = hasContainerItem;
    }

    /**
     * A profile with nothing but a key — every property at its least-remarkable value.
     *
     * <p>Used for the "an ingredient references something not in our registry snapshot" case, which
     * happens more often than it should: recipes can name items from a mod that failed to load, and
     * ore-dictionary entries can resolve to nothing at all. Returning a flat profile rather than
     * {@code null} keeps that from becoming a {@code NullPointerException} in the middle of a
     * postInit pass over the whole registry.</p>
     */
    public static ItemProfile blank(String key) {
        return new ItemProfile(key, 64, 0, 0, false, 0, false);
    }

    public String key() {
        return key;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public int maxDurability() {
        return maxDurability;
    }

    public int enchantability() {
        return enchantability;
    }

    public boolean isBlock() {
        return isBlock;
    }

    public int vanillaRarityOrdinal() {
        return vanillaRarityOrdinal;
    }

    public boolean hasContainerItem() {
        return hasContainerItem;
    }

    /** {@code true} when the game itself treats this as individually significant. */
    public boolean isUnstackable() {
        return maxStackSize == 1;
    }

    @Override
    public String toString() {
        return "ItemProfile[" + key + ", stack=" + maxStackSize + ", durability=" + maxDurability
            + ", ench=" + enchantability + ", block=" + isBlock
            + ", vanillaRarity=" + vanillaRarityOrdinal + "]";
    }
}
