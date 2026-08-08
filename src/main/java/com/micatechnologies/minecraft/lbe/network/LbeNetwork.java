package com.micatechnologies.minecraft.lbe.network;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * LBE's network channel. Registered in {@code preInit}, on both sides.
 *
 * <p>Only one packet so far, and only one direction: the server tells a client what came out of a
 * box it just opened, so the client can put on a show about it. Nothing is ever sent client → server
 * — opening is a block interaction, which vanilla already delivers.</p>
 */
public final class LbeNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
        NetworkRegistry.INSTANCE.newSimpleChannel(LbeConstants.MOD_NAMESPACE);

    /** Discriminators are positional and must stay stable across versions. Append only. */
    private static int nextId = 0;

    private LbeNetwork() {
        throw new AssertionError("No instances.");
    }

    public static void init() {
        // Registered with Side.CLIENT: this message is handled on the client. The handler class is
        // still LOADED on a dedicated server, though, which is why it may not import a
        // net.minecraft.client type and goes through Lbe.proxy instead.
        CHANNEL.registerMessage(PacketRevealLoot.Handler.class, PacketRevealLoot.class,
            nextId++, Side.CLIENT);
    }
}
