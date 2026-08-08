package com.micatechnologies.minecraft.lbe.rarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A recipe, reduced to the only three things the scorer cares about: what goes in, how many times,
 * and how many come out.
 *
 * <p>Deliberately not a model of Minecraft's recipe system. Shaped and shapeless recipes score
 * identically (a 3×3 grid arrangement costs a player nothing), the crafting <i>station</i> is not
 * represented (a furnace recipe and a bench recipe with the same inputs are the same expense), and
 * NBT is ignored. What survives the reduction is exactly the "recipe complexity and amount required"
 * the rarity model is built on.</p>
 *
 * <p><b>Ingredients carry multiplicity.</b> {@link #ingredients()} lists one entry per consumed
 * slot, so eight cobblestone in a furnace recipe appears eight times. That is what makes "how much
 * of it you need" fall out of the arithmetic for free rather than needing a second field.</p>
 *
 * <p><b>An ingredient with alternatives collapses to its cheapest.</b> An ore-dictionary ingredient
 * like {@code ingotCopper} may accept a dozen items across as many mods; the scorer resolves it to
 * whichever alternative scores lowest, because a player who has any of them has satisfied the
 * recipe. Representing that here as a list-of-alternatives per slot is why {@link #ingredients()}
 * is a {@code List<List<String>>} rather than the flatter {@code List<String>} it looks like it
 * wants to be.</p>
 */
public final class CraftingRecipe {

    private final List<List<String>> ingredients;
    private final int outputCount;

    /**
     * @param ingredients one entry per consumed slot; each entry is the non-empty list of item keys
     *                    that would satisfy that slot
     * @param outputCount how many items the recipe yields; clamped to at least 1
     */
    public CraftingRecipe(List<List<String>> ingredients, int outputCount) {
        List<List<String>> copy = new ArrayList<>(ingredients.size());
        for (List<String> slot : ingredients) {
            if (slot == null || slot.isEmpty()) {
                // An unsatisfiable slot (an ore dict name nothing registered against, most often)
                // is dropped rather than kept as an empty list. Keeping it would make every
                // downstream loop guard against emptiness for a case that carries no information.
                continue;
            }
            copy.add(Collections.unmodifiableList(new ArrayList<>(slot)));
        }
        this.ingredients = Collections.unmodifiableList(copy);
        this.outputCount = Math.max(1, outputCount);
    }

    /** Convenience for the common single-alternative-per-slot case, and for tests. */
    public static CraftingRecipe of(int outputCount, String... ingredientKeys) {
        List<List<String>> slots = new ArrayList<>(ingredientKeys.length);
        for (String key : ingredientKeys) {
            slots.add(Collections.singletonList(key));
        }
        return new CraftingRecipe(slots, outputCount);
    }

    /** One entry per consumed slot; each entry lists the alternatives that satisfy it. */
    public List<List<String>> ingredients() {
        return ingredients;
    }

    /** How many items the recipe yields. Always at least 1. */
    public int outputCount() {
        return outputCount;
    }

    /** Total consumed slots — "how much stuff this costs", counting duplicates. */
    public int totalIngredientCount() {
        return ingredients.size();
    }

    /**
     * How many <i>kinds</i> of thing the recipe wants, counting a multi-alternative slot once by its
     * first alternative.
     *
     * <p>This is the "complexity" half of the model, as distinct from the "amount" half above: a
     * recipe wanting eight of one thing is cheap, and a recipe wanting eight different things is a
     * milestone, even though {@link #totalIngredientCount()} cannot tell them apart.</p>
     */
    public int distinctIngredientCount() {
        Set<String> distinct = new LinkedHashSet<>();
        for (List<String> slot : ingredients) {
            distinct.add(slot.get(0));
        }
        return distinct.size();
    }

    /** {@code true} when nothing at all goes in — a degenerate recipe we score as if raw. */
    public boolean isEmpty() {
        return ingredients.isEmpty();
    }

    @Override
    public String toString() {
        return "CraftingRecipe[slots=" + ingredients.size() + " -> " + outputCount + "]";
    }
}
