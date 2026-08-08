package com.micatechnologies.minecraft.lbe.network;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client: "you just opened a {@code tier} box and these came out."
 *
 * <p><b>This packet is presentation only.</b> By the time it is sent the server has already put the
 * items in the player's inventory; the reveal screen is a dramatisation of something that has
 * already happened. That ordering is the whole safety property — a player who skips the animation,
 * closes the screen, alt-tabs, or drops connection mid-reveal has still been paid. Nothing about
 * the loot is decided here, and nothing is waiting on the client to acknowledge anything.</p>
 *
 * <p>The stacks are sent in full rather than as ids because the reveal draws them: a client needs
 * the real {@code ItemStack} to render the icon, the count and the tooltip. Box contents are a
 * handful of stacks, so the cost is trivial and only paid when someone actually opens one.</p>
 */
public class PacketRevealLoot implements IMessage {

    /**
     * Cap on stacks carried by one packet.
     *
     * <p>A box cannot legitimately produce this many — {@code LootRules} tops out well below it —
     * so the limit exists to bound what a malformed or hostile payload can make the client allocate
     * while reading. Reading a length prefix from the wire and trusting it is the classic way to
     * turn a bad packet into an out-of-memory error.</p>
     */
    private static final int MAX_STACKS = 64;

    private Rarity tier;
    private List<ItemStack> contents;

    /** Required no-arg constructor for the Forge network system. */
    public PacketRevealLoot() {
        this.tier = Rarity.lowest();
        this.contents = Collections.emptyList();
    }

    public PacketRevealLoot(Rarity tier, List<ItemStack> contents) {
        this.tier = tier;
        this.contents = contents;
    }

    public Rarity tier() {
        return tier;
    }

    public List<ItemStack> contents() {
        return contents;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int ordinal = buf.readByte();
        // Clamped rather than indexed: a tier ordinal from a server running a different version of
        // the mod would otherwise throw inside the netty read and drop the connection.
        Rarity[] values = Rarity.values();
        tier = values[Math.max(0, Math.min(values.length - 1, ordinal))];

        int count = Math.max(0, Math.min(MAX_STACKS, buf.readByte()));
        List<ItemStack> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buf);
            if (stack != null && !stack.isEmpty()) {
                read.add(stack);
            }
        }
        contents = read;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(tier.ordinal());
        int count = Math.min(MAX_STACKS, contents.size());
        buf.writeByte(count);
        for (int i = 0; i < count; i++) {
            ByteBufUtils.writeItemStack(buf, contents.get(i));
        }
    }

    /**
     * Client-side handler.
     *
     * <p>Loaded on a dedicated server too — {@link LbeNetwork} registers it there — so it must not
     * name a {@code net.minecraft.client} type. The hop through {@code Lbe.proxy} is what keeps
     * that true; the class-loading failure this avoids only shows up at server boot.</p>
     */
    public static class Handler implements IMessageHandler<PacketRevealLoot, IMessage> {

        @Override
        public IMessage onMessage(PacketRevealLoot message, MessageContext ctx) {
            // Never touch the world or a GUI from the netty thread. The proxy schedules onto the
            // client thread; doing it here would be a race against rendering.
            Lbe.proxy.openRevealGui(message.tier(), message.contents());
            return null;
        }
    }
}
