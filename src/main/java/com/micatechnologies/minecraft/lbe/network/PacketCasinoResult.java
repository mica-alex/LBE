package com.micatechnologies.minecraft.lbe.network;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server to client: what happened, and the balance it left behind.
 *
 * <p>Three jobs, distinguished by {@link #stage()}. A {@link Stage#BALANCE} message is sent when a
 * player opens a machine, so the screen has something true to draw. A {@link Stage#DEALT} message is
 * the middle of a two-step game — high-low's base card — where money has been taken but nothing is
 * decided. A {@link Stage#SETTLED} message is a finished game.
 *
 * <p><b>The money has already moved by the time any of these is sent.</b> The client is being told,
 * not asked to apply anything, and a player who drops this packet loses an animation rather than a
 * payout. {@link #reveal()} is whatever numbers that game's screen needs to draw the outcome — reel
 * indices, card ids, a pocket number, a plinko path — and none of it decides a penny.
 */
public class PacketCasinoResult implements IMessage {

    /** Sent as the balance when the server could not determine one. Screens draw a dash. */
    public static final double UNKNOWN_BALANCE = -1.0;

    /** Longest message the server will send. Bounded so a client cannot be handed a huge string. */
    private static final int MAX_MESSAGE_BYTES = 256;

    /** Most reveal values any game needs: keno's twenty draws, with room to spare. */
    private static final int MAX_REVEAL = 64;

    /** What kind of message this is. */
    public enum Stage {
        /** Just a balance; no game in progress. */
        BALANCE,
        /** A two-step game has taken the stake and dealt. Nothing decided yet. */
        DEALT,
        /** A finished game. */
        SETTLED
    }

    private Stage stage = Stage.BALANCE;
    private CasinoGame game = CasinoGame.SLOTS;
    private double multiplier;
    private double payout;
    private double balance = UNKNOWN_BALANCE;
    private int[] reveal = new int[0];
    private String message = "";

    /** Required by the network system. */
    public PacketCasinoResult() {
    }

    public PacketCasinoResult(CasinoGame game, double multiplier, double payout, double balance,
                              int[] reveal, String message) {
        this.stage = Stage.SETTLED;
        this.game = game;
        this.multiplier = multiplier;
        this.payout = payout;
        this.balance = balance;
        this.reveal = reveal == null ? new int[0] : reveal;
        this.message = message == null ? "" : message;
    }

    /** A balance with no game attached, for a screen just being opened. */
    public static PacketCasinoResult balanceOnly(CasinoGame game, double balance) {
        PacketCasinoResult packet = new PacketCasinoResult();
        packet.stage = Stage.BALANCE;
        packet.game = game;
        packet.balance = balance;
        return packet;
    }

    /** The middle of a two-step game: the stake is taken and something has been dealt. */
    public static PacketCasinoResult dealt(CasinoGame game, double balance, int[] reveal,
                                           String message) {
        PacketCasinoResult packet = new PacketCasinoResult();
        packet.stage = Stage.DEALT;
        packet.game = game;
        packet.balance = balance;
        packet.reveal = reveal == null ? new int[0] : reveal;
        packet.message = message == null ? "" : message;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        stage = stageOf(buf.readByte());
        CasinoGame read = CasinoGame.byOrdinal(buf.readByte());
        game = read == null ? CasinoGame.SLOTS : read;
        multiplier = buf.readDouble();
        payout = buf.readDouble();
        balance = buf.readDouble();
        // Bounded before allocating, for the same reason the play packet bounds its picks.
        int count = Math.max(0, Math.min(MAX_REVEAL, buf.readByte()));
        reveal = new int[count];
        for (int i = 0; i < count; i++) {
            reveal[i] = buf.readByte();
        }
        int length = Math.max(0, Math.min(MAX_MESSAGE_BYTES, buf.readShort()));
        byte[] text = new byte[length];
        buf.readBytes(text);
        message = new String(text, StandardCharsets.UTF_8);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(stage.ordinal());
        buf.writeByte(game.ordinal());
        buf.writeDouble(multiplier);
        buf.writeDouble(payout);
        buf.writeDouble(balance);
        buf.writeByte(reveal.length);
        for (int value : reveal) {
            buf.writeByte(value);
        }
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(text.length, MAX_MESSAGE_BYTES);
        buf.writeShort(length);
        buf.writeBytes(text, 0, length);
    }

    private static Stage stageOf(int ordinal) {
        Stage[] all = Stage.values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : Stage.BALANCE;
    }

    public Stage stage() {
        return stage;
    }

    public CasinoGame game() {
        return game;
    }

    /** What the bet returned, "for 1". Zero for a loss. */
    public double multiplier() {
        return multiplier;
    }

    /** What the bet paid, in currency. */
    public double payout() {
        return payout;
    }

    public double balance() {
        return balance;
    }

    /** Game-specific numbers for the screen to draw. Never affects money. */
    public int[] reveal() {
        return reveal;
    }

    /** One line describing the outcome, already written for a player. */
    public String message() {
        return message;
    }

    /** A reveal value, or {@code fallback} when the server sent fewer than expected. */
    public int reveal(int index, int fallback) {
        return index >= 0 && index < reveal.length ? reveal[index] : fallback;
    }

    public static class Handler implements IMessageHandler<PacketCasinoResult, IMessage> {

        @Override
        public IMessage onMessage(PacketCasinoResult message, MessageContext ctx) {
            // Handed to the proxy rather than acted on here: this class is loaded on a dedicated
            // server too, so it may not touch a net.minecraft.client type. See LbeNetwork.
            Lbe.proxy.onCasinoResult(message);
            return null;
        }
    }
}
