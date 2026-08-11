package com.micatechnologies.minecraft.lbe.network;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSpin;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSymbol;
import io.netty.buffer.ByteBuf;
import javax.annotation.Nullable;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server → client: a finished spin, and the balance it left behind.
 *
 * <p>Two jobs in one message. With a spin attached it is the result of a bet, which the screen
 * animates toward; with no spin it is just the current balance, sent when a player first opens a
 * machine so the screen has something true to draw.
 *
 * <p><b>The money has already moved by the time this is sent.</b> The client is being told what
 * happened, not asked to apply it — nothing here changes a balance, and a player who drops this
 * packet loses the animation, not their winnings.
 */
public class PacketSlotResult implements IMessage {

    /** Sent as the balance when the server could not determine one. The screen draws a dash. */
    public static final double UNKNOWN_BALANCE = -1.0;

    @Nullable
    private SlotSpin spin;
    private double payout;
    private double balance;

    /** Required by the network system. */
    public PacketSlotResult() {
    }

    public PacketSlotResult(@Nullable SlotSpin spin, double payout, double balance) {
        this.spin = spin;
        this.payout = payout;
        this.balance = balance;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        if (buf.readBoolean()) {
            SlotSymbol[] reels = new SlotSymbol[3];
            for (int i = 0; i < reels.length; i++) {
                // byIndex wraps, so a malformed or hostile index lands on a real symbol instead of
                // throwing on the render thread. Nothing here decides money, so a wrong symbol is
                // a cosmetic lie at worst — and one that can only be told to the liar's own client.
                reels[i] = SlotSymbol.byIndex(buf.readByte());
            }
            spin = SlotSpin.of(reels);
        } else {
            spin = null;
        }
        payout = buf.readDouble();
        balance = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(spin != null);
        if (spin != null) {
            for (SlotSymbol symbol : spin.reels()) {
                buf.writeByte(symbol.index());
            }
        }
        buf.writeDouble(payout);
        buf.writeDouble(balance);
    }

    @Nullable
    public SlotSpin spin() {
        return spin;
    }

    public double payout() {
        return payout;
    }

    /** The player's balance, or {@link #UNKNOWN_BALANCE}. */
    public double balance() {
        return balance;
    }

    public static class Handler implements IMessageHandler<PacketSlotResult, IMessage> {

        @Override
        public IMessage onMessage(PacketSlotResult message, MessageContext ctx) {
            // Handed to the proxy rather than acted on here: this class is loaded on a dedicated
            // server too, so it may not touch a net.minecraft.client type. See LbeNetwork.
            Lbe.proxy.onSlotResult(message);
            return null;
        }
    }
}
