package com.micatechnologies.minecraft.lbe.catalog;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.rarity.CraftingRecipe;
import com.micatechnologies.minecraft.lbe.rarity.ItemGraph;
import com.micatechnologies.minecraft.lbe.rarity.ItemKeys;
import com.micatechnologies.minecraft.lbe.rarity.ItemProfile;
import com.micatechnologies.minecraft.lbe.rarity.KeyFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
// 1.12.2 keeps ForgeRegistries under fml.common.registry; the net.minecraftforge.registries package
// every modern tutorial imports from is 1.13+ and does not exist here.
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Builds an {@link ItemGraph} from Forge's live item and recipe registries — the adapter between the
 * game and the Minecraft-free rarity engine.
 *
 * <p><b>This is the only class in the mod that knows both worlds.</b> Everything above it works in
 * {@code ItemStack}s and registries; everything below it works in string keys and plain numbers. That
 * is what makes the scoring model unit-testable, so keep the seam intact: no Minecraft type may leak
 * past {@link #snapshot()}, and no scoring arithmetic belongs in here.</p>
 *
 * <p><b>Build it once, at postInit.</b> Every mod has finished registering by then, and the result
 * is an immutable snapshot — the scorer memoises against it and would produce order-dependent
 * results if the underlying registries moved.</p>
 */
public final class ForgeItemGraph implements ItemGraph {

    /**
     * Most alternatives kept for one ore-dictionary-style ingredient slot.
     *
     * <p>Some ore dictionary names resolve to hundreds of stacks in a large pack ({@code plankWood}
     * is the classic), and the scorer only ever uses the cheapest one. Keeping all of them would
     * cost a lot of memory and a lot of scoring time to reach the same answer. The cap risks missing
     * a cheaper alternative beyond the cutoff, which would slightly overstate one item's cost —
     * a far better trade than a multi-second startup stall.</p>
     */
    private static final int MAX_INGREDIENT_ALTERNATIVES = 24;

    private final Map<String, ItemProfile> profiles;
    private final Map<String, CraftingRecipe> recipes;
    private final Map<String, ItemStack> templates;
    private final List<String> lootableKeys;
    private final int excludedByBlacklist;

    private ForgeItemGraph(Map<String, ItemProfile> profiles, Map<String, CraftingRecipe> recipes,
                           Map<String, ItemStack> templates, List<String> lootableKeys,
                           int excludedByBlacklist) {
        this.profiles = profiles;
        this.recipes = recipes;
        this.templates = templates;
        this.lootableKeys = Collections.unmodifiableList(lootableKeys);
        this.excludedByBlacklist = excludedByBlacklist;
    }

    /**
     * Walk the registries and build the snapshot.
     *
     * @param blacklist items that may not be handed out as loot. They are still profiled and still
     *                  scored as ingredients — they are simply absent from {@link #keys()}, which is
     *                  what the loot tables are drawn from. Excluding a command block from the loot
     *                  pool must not make everything crafted from one look cheap.
     */
    public static ForgeItemGraph snapshot(KeyFilter blacklist) {
        Map<String, ItemProfile> profiles = new LinkedHashMap<>();
        Map<String, ItemStack> templates = new LinkedHashMap<>();
        List<String> lootableKeys = new ArrayList<>();
        int excluded = 0;

        // Two coarse steps, not one per item. The per-item counts here come from iterating live
        // registries, and ProgressManager.pop throws on a step-count mismatch — a bar that can
        // crash the load if a registry's size disagrees with its iterator is not worth a smoother
        // animation. The scoring bar in LootCatalog, which owns its own loop, does the fine-grained
        // part. (fml.common, so server-safe — see the note in LootCatalog.rebuild.)
        ProgressManager.ProgressBar bar = ProgressManager.push("LBE: reading registries", 2);
        bar.step("item variants");

        for (Item item : ForgeRegistries.ITEMS) {
            if (item == null || item.getRegistryName() == null) {
                continue;
            }
            for (ItemStack stack : variantsOf(item)) {
                String key = keyOf(stack);
                if (key == null || profiles.containsKey(key)) {
                    continue;
                }
                profiles.put(key, profileOf(stack));
                templates.put(key, stack.copy());
                if (blacklist != null && blacklist.matches(key)) {
                    excluded++;
                }
                else {
                    lootableKeys.add(key);
                }
            }
        }

        Lbe.LOGGER.info("Profiled {} item variants ({} blacklisted); indexing recipes...",
            profiles.size(), excluded);
        bar.step("recipes");
        Map<String, CraftingRecipe> recipes = collectRecipes(profiles);
        ProgressManager.pop(bar);
        Lbe.LOGGER.info("Registry snapshot: {} item variants ({} blacklisted), {} recipes",
            profiles.size(), excluded, recipes.size());
        return new ForgeItemGraph(profiles, recipes, templates, lootableKeys, excluded);
    }

    /** Convenience for the no-blacklist case, used by tests and by {@code /lbe} diagnostics. */
    public static ForgeItemGraph snapshot() {
        return snapshot(KeyFilter.empty());
    }

    // --- ItemGraph -------------------------------------------------------------------------------

    /**
     * The loot-eligible keys — i.e. everything except what the blacklist removed.
     *
     * <p>Note the asymmetry with {@link #profile(String)} and {@link #recipe(String)}, which answer
     * for <i>any</i> key including blacklisted ones. That is deliberate and is the whole point of
     * the blacklist being a loot filter rather than a registry filter.</p>
     */
    @Override
    public Collection<String> keys() {
        return lootableKeys;
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

    // --- back to Minecraft -----------------------------------------------------------------------

    /**
     * A fresh stack of the item {@code key} names, or {@link ItemStack#EMPTY} if this snapshot has
     * never heard of it. Always a copy — callers mutate the count.
     */
    public ItemStack stackFor(String key) {
        ItemStack template = templates.get(key);
        return template == null ? ItemStack.EMPTY : template.copy();
    }

    /** Every item variant this snapshot profiled, blacklisted ones included. */
    public Collection<String> allKeys() {
        return profiles.keySet();
    }

    /** How many variants the blacklist removed from the loot pool. */
    public int excludedByBlacklist() {
        return excludedByBlacklist;
    }

    /**
     * The key for a stack: {@code modid:name#meta}.
     *
     * <p>A wildcard metadata (32767, which turns up in ingredients meaning "any damage value")
     * collapses to 0. It has to collapse to something concrete — the graph is keyed by real items —
     * and 0 is the variant that exists for essentially everything, so it is the choice least likely
     * to point at nothing.</p>
     *
     * @return the key, or {@code null} for an empty or unregistered stack
     */
    public static String keyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return null;
        }
        int meta = stack.getMetadata();
        if (meta == OreDictionary.WILDCARD_VALUE) {
            meta = 0;
        }
        return ItemKeys.of(stack.getItem().getRegistryName().toString(), meta);
    }

    // --- construction internals ------------------------------------------------------------------

    /**
     * Every metadata variant of an item, as the creative search tab would show them.
     *
     * <p>{@code CreativeTabs.SEARCH} is the trick that makes this one call instead of a metadata
     * scan: an item reports its own variants into it, so a mod that packs sixteen colours into
     * damage values enumerates correctly without us guessing at a metadata range. It also filters
     * out items with no creative tab at all, which are the technical/internal ones a player is not
     * supposed to hold — exactly what we want excluded from loot.</p>
     *
     * <p>Wrapped in a catch-all because this calls straight into third-party code during startup.
     * A single mod throwing out of {@code getSubItems} must not take the whole catalogue down with
     * it; we log it, fall back to the plain stack, and carry on.</p>
     */
    private static List<ItemStack> variantsOf(Item item) {
        NonNullList<ItemStack> variants = NonNullList.create();
        try {
            item.getSubItems(CreativeTabs.SEARCH, variants);
        }
        catch (Throwable t) {
            Lbe.LOGGER.warn("getSubItems threw for {} — falling back to its base stack",
                item.getRegistryName(), t);
            variants.clear();
        }
        if (variants.isEmpty()) {
            return Collections.emptyList();
        }
        return variants;
    }

    private static ItemProfile profileOf(ItemStack stack) {
        Item item = stack.getItem();
        int enchantability = 0;
        boolean hasContainer = false;
        int declaredRarity = 0;
        try {
            enchantability = item.getItemEnchantability(stack);
            hasContainer = item.hasContainerItem(stack);
            declaredRarity = stack.getRarity().ordinal();
        }
        catch (Throwable t) {
            // Same reasoning as variantsOf: third-party code, called during startup, over every item
            // in the pack. A profile with default properties is worth vastly more than a crash.
            Lbe.LOGGER.warn("Failed to profile {} — using default properties",
                item.getRegistryName(), t);
        }
        return new ItemProfile(
            keyOf(stack),
            stack.getMaxStackSize(),
            stack.getMaxDamage(),
            enchantability,
            item instanceof ItemBlock,
            declaredRarity,
            hasContainer);
    }

    /**
     * Index every crafting and smelting recipe by what it produces, keeping the <b>cheapest</b>
     * route to each item.
     *
     * <p>"Cheapest" here means "fewest consumed slots", which is a proxy rather than an answer —
     * properly comparing two recipes would mean scoring them, and scoring them needs this index to
     * already exist. The proxy is right about the case that matters: where a mod offers both a cheap
     * and an expensive route to the same item, a player takes the cheap one, so that is the one the
     * item's rarity should reflect.</p>
     */
    private static Map<String, CraftingRecipe> collectRecipes(Map<String, ItemProfile> known) {
        Map<String, CraftingRecipe> best = new HashMap<>();

        for (IRecipe recipe : ForgeRegistries.RECIPES) {
            ItemStack output;
            NonNullList<Ingredient> ingredients;
            try {
                output = recipe.getRecipeOutput();
                ingredients = recipe.getIngredients();
            }
            catch (Throwable t) {
                Lbe.LOGGER.warn("Skipping unreadable recipe {}", recipe.getRegistryName(), t);
                continue;
            }
            // Dynamic recipes (map cloning, shield decoration, firework assembly) report an empty
            // output because their result depends on the inputs. There is nothing to index them by.
            String key = keyOf(output);
            if (key == null || ingredients == null) {
                continue;
            }
            List<List<String>> slots = new ArrayList<>(ingredients.size());
            for (Ingredient ingredient : ingredients) {
                List<String> alternatives = alternativesFor(ingredient);
                if (!alternatives.isEmpty()) {
                    slots.add(alternatives);
                }
            }
            if (slots.isEmpty()) {
                continue;
            }
            keepCheapest(best, key, new CraftingRecipe(slots, output.getCount()));
        }

        for (Map.Entry<ItemStack, ItemStack> smelting
                : FurnaceRecipes.instance().getSmeltingList().entrySet()) {
            String inputKey = keyOf(smelting.getKey());
            String outputKey = keyOf(smelting.getValue());
            if (inputKey == null || outputKey == null) {
                continue;
            }
            // Fuel is deliberately not counted. It is real expense, but it is expense in a currency
            // (coal, lava, RF) that varies so wildly between packs that including it would make the
            // whole model depend on which power mod happens to be installed.
            keepCheapest(best, outputKey,
                CraftingRecipe.of(smelting.getValue().getCount(), inputKey));
        }

        // A recipe producing an item nothing else knows about is harmless — the scorer treats an
        // unknown ingredient as raw — but it is worth knowing about when a catalogue looks wrong.
        int orphans = 0;
        for (String key : best.keySet()) {
            if (!known.containsKey(key)) {
                orphans++;
            }
        }
        if (orphans > 0) {
            Lbe.LOGGER.debug("{} recipes produce items absent from the creative-search registry "
                + "(usually items with no creative tab)", orphans);
        }
        return best;
    }

    private static void keepCheapest(Map<String, CraftingRecipe> best, String key,
                                     CraftingRecipe candidate) {
        CraftingRecipe existing = best.get(key);
        if (existing == null || candidate.totalIngredientCount() < existing.totalIngredientCount()) {
            best.put(key, candidate);
        }
    }

    private static List<String> alternativesFor(Ingredient ingredient) {
        if (ingredient == null || ingredient == Ingredient.EMPTY) {
            return Collections.emptyList();
        }
        ItemStack[] matching;
        try {
            matching = ingredient.getMatchingStacks();
        }
        catch (Throwable t) {
            // A custom Ingredient implementation that cannot enumerate itself. Dropping the slot
            // understates the recipe; refusing to load the pack would be worse.
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>(Math.min(matching.length, MAX_INGREDIENT_ALTERNATIVES));
        for (ItemStack candidate : matching) {
            if (keys.size() >= MAX_INGREDIENT_ALTERNATIVES) {
                break;
            }
            String key = keyOf(candidate);
            if (key != null && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }
}
