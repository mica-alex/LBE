package com.micatechnologies.minecraft.lbe.network;

import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
import com.micatechnologies.minecraft.lbe.casino.block.BlockCasinoMachine;
import com.micatechnologies.minecraft.lbe.casino.block.TileEntityCasinoMachine;
import com.micatechnologies.minecraft.lbe.casino.keno.KenoGame;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client to server: "I want to play the machine at this position, for this much, with these
 * choices."
 *
 * <p><b>LBE's only client-to-server message, and therefore the only one that has to treat its
 * contents as hostile.</b> It carries a position, an amount and a few option numbers — deliberately
 * no cards, no reels, no payout, no balance. Everything that decides money is worked out server-side
 * from these, because anything a client sends is a number an attacker chose.
 *
 * <p>The checks here are the ones about the machine. The ones about the bet and the options live in
 * {@link TileEntityCasinoMachine}, next to the rules they belong to.
 */
public class PacketCasinoPlay implements IMessage {

    /** How far a player may be from a machine and still use it, squared. Generous but finite. */
    private static final double MAX_REACH_SQUARED = 64.0;

    private BlockPos pos;
    private double bet;
    private int optionA;
    private int optionB;
    private int[] numbers;

    /** Required by the network system. */
    public PacketCasinoPlay() {
    }

    public PacketCasinoPlay(BlockPos pos, double bet, int optionA, int optionB, int[] numbers) {
        this.pos = pos;
        this.bet = bet;
        this.optionA = optionA;
        this.optionB = optionB;
        this.numbers = numbers == null ? new int[0] : numbers;
    }

    public PacketCasinoPlay(BlockPos pos, double bet, int optionA) {
        this(pos, bet, optionA, 0, null);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        bet = buf.readDouble();
        optionA = buf.readInt();
        optionB = buf.readInt();
        // Bounded before allocating: a client naming a huge count would otherwise have the server
        // reserve that much memory on its say-so, which is a cheap way to hurt it.
        int count = Math.max(0, Math.min(KenoGame.MAX_PICKS, buf.readByte()));
        numbers = new int[count];
        for (int i = 0; i < count; i++) {
            numbers[i] = buf.readByte();
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeDouble(bet);
        buf.writeInt(optionA);
        buf.writeInt(optionB);
        buf.writeByte(numbers.length);
        for (int number : numbers) {
            buf.writeByte(number);
        }
    }

    public static class Handler implements IMessageHandler<PacketCasinoPlay, IMessage> {

        @Override
        public IMessage onMessage(PacketCasinoPlay message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // Scheduled rather than run here: this arrives on a netty IO thread, and everything it
            // touches — the world, an inventory, a balance — belongs to the server thread. SUM
            // would refuse the off-thread call anyway; this is what stops it happening.
            player.getServerWorld().addScheduledTask(() -> handle(message, player));
            return null;
        }

        private static void handle(PacketCasinoPlay message, EntityPlayerMP player) {
            if (message.pos == null || !Double.isFinite(message.bet) || message.bet < 0.0) {
                return;
            }
            World world = player.getServerWorld();
            // isBlockLoaded first: naming an unloaded position would otherwise force the chunk to
            // load, which is a cheap way to make a server do work on request.
            if (!world.isBlockLoaded(message.pos)) {
                return;
            }
            if (player.getDistanceSq(message.pos) > MAX_REACH_SQUARED) {
                return;
            }
            IBlockState state = world.getBlockState(message.pos);
            if (!(state.getBlock() instanceof BlockCasinoMachine)) {
                return;
            }
            CasinoGame game = ((BlockCasinoMachine) state.getBlock()).game();
            TileEntityCasinoMachine machine =
                BlockCasinoMachine.machineAt(world, message.pos, state);
            if (machine != null) {
                machine.play(player, game, message.bet, message.optionA, message.optionB,
                    message.numbers);
            }
        }
    }
}
