package com.micatechnologies.minecraft.lbe.network;

import com.micatechnologies.minecraft.lbe.casino.block.BlockSlotMachine;
import com.micatechnologies.minecraft.lbe.casino.block.TileEntitySlotMachine;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server: "I want to bet this much on the machine at this position."
 *
 * <p><b>This is the one message in LBE a player controls, so it is the one that gets checked.</b>
 * It carries a position and an amount and nothing else — deliberately no reels, no payout, no
 * balance. Everything that decides money is worked out server-side from these two numbers, because
 * anything a client sends is a number an attacker chose.
 *
 * <p>The checks that matter, all on the server:
 * <ul>
 *   <li>the position really holds a slot machine — not an arbitrary block the sender named;</li>
 *   <li>that machine is loaded and close enough to reach, so a bet cannot be placed across the
 *       world or into an unloaded chunk;</li>
 *   <li>the amount is within the server's limits, and the player can afford it.</li>
 * </ul>
 * The last of those lives in {@link TileEntitySlotMachine#spin}; the rest are here.
 */
public class PacketSlotSpin implements IMessage {

    /** How far a player may be from a machine and still use it, squared. Generous but finite. */
    private static final double MAX_REACH_SQUARED = 64.0;

    private BlockPos pos;
    private double bet;

    /** Required by the network system. */
    public PacketSlotSpin() {
    }

    public PacketSlotSpin(BlockPos pos, double bet) {
        this.pos = pos;
        this.bet = bet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = BlockPos.fromLong(buf.readLong());
        bet = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeDouble(bet);
    }

    public static class Handler implements IMessageHandler<PacketSlotSpin, IMessage> {

        @Override
        public IMessage onMessage(PacketSlotSpin message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            // Scheduled rather than run here: this arrives on a netty IO thread, and everything it
            // touches — the world, an inventory, a balance — is the server thread's alone. SUM
            // would refuse the off-thread call anyway; this is what makes it not happen.
            player.getServerWorld().addScheduledTask(() -> handle(message, player));
            return null;
        }

        private static void handle(PacketSlotSpin message, EntityPlayerMP player) {
            if (message.pos == null || !Double.isFinite(message.bet) || message.bet <= 0.0) {
                return;
            }
            World world = player.getServerWorld();
            // isBlockLoaded, not getTileEntity directly: naming an unloaded position would
            // otherwise force the chunk to load, which is a cheap way to make a server do work.
            if (!world.isBlockLoaded(message.pos)) {
                return;
            }
            if (player.getDistanceSq(message.pos) > MAX_REACH_SQUARED) {
                return;
            }
            net.minecraft.block.state.IBlockState state = world.getBlockState(message.pos);
            if (!(state.getBlock() instanceof BlockSlotMachine)) {
                return;
            }
            TileEntitySlotMachine machine = BlockSlotMachine.machineAt(world, message.pos, state);
            if (machine != null) {
                machine.spin(player, message.bet);
            }
        }
    }
}
