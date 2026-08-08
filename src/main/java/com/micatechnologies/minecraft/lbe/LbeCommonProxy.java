package com.micatechnologies.minecraft.lbe;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Server-side (and shared) proxy. {@link LbeClientProxy} extends this, so anything put here runs on
 * both sides. Nothing here may reference a {@code net.minecraft.client} type.
 */
public class LbeCommonProxy implements LbeProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Override
    public void init(FMLInitializationEvent event) {
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
    }
}
