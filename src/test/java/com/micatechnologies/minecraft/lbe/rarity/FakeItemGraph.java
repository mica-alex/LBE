package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hand-built {@link ItemGraph} for the tests — a few dozen items, no Minecraft anywhere.
 *
 * <p>This class is the payoff for keeping the {@code rarity} package Minecraft-free. Everything the
 * scoring model does can be asserted against a graph small enough to read in one screen, in a test
 * suite that runs in milliseconds on a bare JVM. If a change ever makes it impossible to write a
 * test this way, that change has broken the seam and should be reconsidered.</p>
 */
final class FakeItemGraph implements ItemGraph {

    private final Map<String, ItemProfile> profiles = new LinkedHashMap<>();
    private final Map<String, CraftingRecipe> recipes = new HashMap<>();

    /** Add a raw material — no recipe, ordinary properties. */
    FakeItemGraph raw(String key) {
        profiles.put(key, ItemProfile.blank(key));
        return this;
    }

    /** Add an item with explicit properties and no recipe. */
    FakeItemGraph raw(String key, ItemProfile profile) {
        profiles.put(key, profile);
        return this;
    }

    /** Add an item crafted from the given ingredient keys, yielding {@code outputCount}. */
    FakeItemGraph crafted(String key, int outputCount, String... ingredients) {
        profiles.put(key, ItemProfile.blank(key));
        recipes.put(key, CraftingRecipe.of(outputCount, ingredients));
        return this;
    }

    /** Add a crafted item with explicit properties. */
    FakeItemGraph crafted(String key, ItemProfile profile, int outputCount, String... ingredients) {
        profiles.put(key, profile);
        recipes.put(key, CraftingRecipe.of(outputCount, ingredients));
        return this;
    }

    /** Add an item whose single ingredient slot accepts any of {@code alternatives}. */
    FakeItemGraph craftedFromAnyOf(String key, int outputCount, String... alternatives) {
        profiles.put(key, ItemProfile.blank(key));
        List<List<String>> slots = new ArrayList<>();
        slots.add(java.util.Arrays.asList(alternatives));
        recipes.put(key, new CraftingRecipe(slots, outputCount));
        return this;
    }

    @Override
    public Collection<String> keys() {
        return profiles.keySet();
    }

    @Override
    public ItemProfile profile(String key) {
        ItemProfile profile = profiles.get(key);
        return profile == null ? ItemProfile.blank(key) : profile;
    }

    @Override
    public CraftingRecipe recipe(String key) {
        return recipes.get(key);
    }
}
