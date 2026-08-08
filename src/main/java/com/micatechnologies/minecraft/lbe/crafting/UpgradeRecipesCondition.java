package com.micatechnologies.minecraft.lbe.crafting;

import com.google.gson.JsonObject;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import java.util.function.BooleanSupplier;
import net.minecraftforge.common.crafting.IConditionFactory;
import net.minecraftforge.common.crafting.JsonContext;

/**
 * Recipe condition backing {@code general.enableUpgradeRecipes}.
 *
 * <p>Lets the three box-upgrade recipes stay as ordinary JSON — overridable by a resource pack,
 * removable by CraftTweaker, visible to JEI — while still being switchable from the config. The
 * alternative, registering them in code behind an {@code if}, would take all of that away for the
 * sake of one boolean.</p>
 *
 * <p>Wired up by {@code assets/lbe/recipes/_factories.json}; the condition is evaluated when recipes
 * load, which in 1.12.2 is during the registry events and therefore <b>after</b> {@code preInit} has
 * read the config. Nothing here needs to guard against an unloaded config.</p>
 */
public class UpgradeRecipesCondition implements IConditionFactory {

    @Override
    public BooleanSupplier parse(JsonContext context, JsonObject json) {
        return () -> LbeConfig.enableUpgradeRecipes;
    }
}
