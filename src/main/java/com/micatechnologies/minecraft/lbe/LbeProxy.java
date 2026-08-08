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

    /**
     * Show the loot-reveal screen for a box that has just been opened. A no-op on the server, so the
     * network handler that calls this stays side-safe.
     *
     * <p>The items are already in the player's inventory by the time this is called — see
     * {@link com.micatechnologies.minecraft.lbe.network.PacketRevealLoot}. This is a dramatisation,
     * and it is free to be skipped, closed or never shown at all.</p>
     *
     * @param tier     the box's tier
     * @param contents what came out, in roll order
     */
    void openRevealGui(com.micatechnologies.minecraft.lbe.rarity.Rarity tier,
                       java.util.List<net.minecraft.item.ItemStack> contents);
}
