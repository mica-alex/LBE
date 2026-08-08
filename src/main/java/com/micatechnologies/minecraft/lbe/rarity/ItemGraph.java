package com.micatechnologies.minecraft.lbe.rarity;

import java.util.Collection;

/**
 * The scorer's whole view of the world: a set of item keys, what each one is like, and how each one
 * is made.
 *
 * <p><b>This interface is the seam that makes the rarity engine testable.</b> The game implements it
 * over Forge's item and recipe registries ({@code catalog.ForgeItemGraph}); a unit test implements it
 * over a HashMap with nine hand-written entries. {@link RarityScorer} cannot tell the difference, has
 * no way to reach a Minecraft type, and therefore runs on a bare JVM in milliseconds.</p>
 *
 * <p>Implementations must be <b>snapshots</b>, not live views. The scorer walks the graph recursively
 * and memoises as it goes; a graph that changed underneath it would produce scores that depend on
 * visit order. Build it once, after every mod has finished registering.</p>
 */
public interface ItemGraph {

    /**
     * Every item to be scored. Should already exclude anything the pack has blacklisted — an item
     * absent from here is absent from the loot tables, but may still legitimately appear as an
     * <i>ingredient</i> of something that is present.
     */
    Collection<String> keys();

    /**
     * The non-recipe properties of {@code key}. Never {@code null}: return
     * {@link ItemProfile#blank(String)} for a key that is referenced as an ingredient but is not
     * itself in {@link #keys()}, which happens whenever a recipe names an item from a mod that is
     * not installed.
     */
    ItemProfile profile(String key);

    /**
     * The cheapest recipe producing {@code key}, or {@code null} if the item has none and is
     * therefore a <b>raw material</b> — the base case the recursion terminates on.
     *
     * <p>"Cheapest" is the implementation's judgement, and the honest answer for most items is
     * "the one with the fewest ingredient slots", because properly choosing between several recipes
     * would require scoring them, which requires this method. Where a mod offers both a cheap and an
     * expensive route to the same item, the cheap one is the correct one to score against: a player
     * will take it.</p>
     */
    CraftingRecipe recipe(String key);
}
