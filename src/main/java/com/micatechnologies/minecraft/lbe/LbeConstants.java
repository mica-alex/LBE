package com.micatechnologies.minecraft.lbe;

/**
 * Compile-time constants for the mod's identity.
 *
 * <p>The values come from {@code Tags}, a class generated at build time by the GTCEu buildscript
 * from {@code buildscript.properties} ({@code generateGradleTokenClass}). Do not hard-code the mod
 * id or version anywhere else — the version in particular is derived from the latest git tag, so a
 * literal would drift the moment a release is cut.</p>
 */
public final class LbeConstants {

    /** Registry namespace and Forge mod id. Every {@code ResourceLocation} we create uses this. */
    public static final String MOD_NAMESPACE = Tags.MODID;

    /** Human-readable mod name, as shown in the Forge mod list. */
    public static final String MOD_NAME = Tags.MODNAME;

    /** Version string, derived from the latest git tag ({@code YYYY.MM.DD} for releases). */
    public static final String MOD_VERSION = Tags.VERSION;

    /** Registry name prefix for the loot-box blocks: {@code loot_box_common} and friends. */
    public static final String LOOT_BOX_PREFIX = "loot_box_";

    /**
     * NBT key under which a loot box's roll seed travels in a dropped {@code ItemStack}.
     *
     * <p>Its existence is the answer to "can I break a box I don't like and put it back down for a
     * different roll?" — no, because the seed comes with it. See {@code TileEntityLootBox}.</p>
     */
    public static final String NBT_SEED = "LbeSeed";

    private LbeConstants() {
        throw new AssertionError("No instances.");
    }
}
