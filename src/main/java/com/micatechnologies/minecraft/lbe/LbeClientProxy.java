package com.micatechnologies.minecraft.lbe;

import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Client-side proxy. Item-model binding, and anywhere a renderer eventually lands, get installed
 * from here.
 *
 * <p>This class and everything it reaches may reference {@code net.minecraft.client}. Nothing
 * outside {@code LbeClientProxy}'s reachable graph may — a single stray client import in common
 * code compiles perfectly and only fails when a dedicated server boots, which is exactly what the
 * CI server smoke test exists to catch.</p>
 */
public class LbeClientProxy extends LbeCommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // ModelRegistryEvent arrives on the Forge bus in 1.12.2; subscribe this proxy so
        // registerModels below is reached.
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Binds item models. Must run on {@code ModelRegistryEvent}: models bake before {@code init}, so
     * registering a variant any later leaves the item rendering as the missing-model cube.
     */
    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        for (Rarity rarity : Rarity.values()) {
            bindModel(LbeBlocks.boxItem(rarity));
        }
    }

    private static void bindModel(Item item) {
        if (item == null || item.getRegistryName() == null) {
            return;
        }
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }
}
