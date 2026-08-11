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

    @Override
    public void openRevealGui(com.micatechnologies.minecraft.lbe.rarity.Rarity tier,
                              java.util.List<net.minecraft.item.ItemStack> contents) {
        // No screens on a dedicated server.
    }

    /**
     * No-op: there is no screen on a server.
     *
     * <p>Present so the packet handlers and the tile entity can call the proxy unconditionally,
     * which is what keeps a {@code net.minecraft.client} import out of common code.
     */
    @Override
    public void openCasinoGui(net.minecraft.util.math.BlockPos pos,
                              com.micatechnologies.minecraft.lbe.casino.CasinoGame game) {
    }

    /** No-op: a dedicated server never receives its own result packet. */
    @Override
    public void onCasinoResult(
        com.micatechnologies.minecraft.lbe.network.PacketCasinoResult result) {
    }
}
