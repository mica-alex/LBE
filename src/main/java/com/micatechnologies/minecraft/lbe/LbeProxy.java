package com.micatechnologies.minecraft.lbe;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Sided-proxy contract. The only sanctioned bridge from common code into client-only classes —
 * see the side-discipline note on {@link Lbe}.
 */
public interface LbeProxy {

    void preInit(FMLPreInitializationEvent event);

    void init(FMLInitializationEvent event);

    void postInit(FMLPostInitializationEvent event);
}
