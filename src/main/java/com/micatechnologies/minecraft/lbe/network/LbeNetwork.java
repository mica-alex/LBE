package com.micatechnologies.minecraft.lbe.network;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * LBE's network channel. Registered in {@code preInit}, on both sides.
 *
 * <p>Loot boxes need one direction only: the server tells a client what came out of a box it just
 * opened, so the client can put on a show about it. Opening is a block interaction, which vanilla
 * already delivers, so nothing is sent the other way.</p>
 *
 * <p>The casino needs both. A bet is a decision a player makes inside a screen, not a block
 * interaction, so {@link PacketCasinoPlay} is LBE's only client → server message — and therefore the
 * only one that has to treat its contents as hostile. It says so at length in its own javadoc.</p>
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
        CHANNEL.registerMessage(PacketCasinoResult.Handler.class, PacketCasinoResult.class,
            nextId++, Side.CLIENT);
        // The only one handled on the server, and the only one a player can forge.
        CHANNEL.registerMessage(PacketCasinoPlay.Handler.class, PacketCasinoPlay.class,
            nextId++, Side.SERVER);
    }
}
