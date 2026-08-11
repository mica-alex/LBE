package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.casino.economy.Wager;
import com.micatechnologies.minecraft.lbe.casino.slots.SlotSpin;
import com.micatechnologies.minecraft.lbe.network.LbeNetwork;
import com.micatechnologies.minecraft.lbe.network.PacketSlotResult;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * One slot machine. Holds no money and remembers nothing across a restart.
 *
 * <p><b>Every spin is decided here, on the server.</b> The client asks to bet; it is never asked
 * what came up. A reel that stops where the client says is a machine that pays what the client says,
 * and the client is the one place in Minecraft an attacker fully controls.
 *
 * <p>Deliberately not an inventory and deliberately not persistent: a machine's whole state is the
 * per-player cooldown below, which exists only to stop a held-down button. Anything worth keeping —
 * the money — lives in SUM, which already knows how to survive a crash.
 */
public class TileEntitySlotMachine extends TileEntity {

    /** Server-side only: when each player may spin again, in world time. */
    private final Map<UUID, Long> nextSpinAllowed = new HashMap<>();

    /**
     * Deliberately not seeded. {@link Random}'s default constructor seeds from a source a player
     * cannot see or reproduce, whereas anything derived from world time or position would let
     * somebody with the source work out when to pull the lever.
     */
    private final Random random = new Random();

    /**
     * A player right-clicked the cabinet.
     *
     * <p>Runs on <b>both</b> sides. The client opens the screen; the server sends the player what
     * the screen needs to draw, because a client cannot read a wallet balance it does not hold.
     */
    public void onActivated(EntityPlayer player) {
        if (world.isRemote) {
            Lbe.proxy.openSlotMachineGui(pos);
            return;
        }
        if (player instanceof EntityPlayerMP) {
            sendState((EntityPlayerMP) player);
        }
    }

    /**
     * Takes a bet, spins, settles, and tells the player what happened.
     *
     * <p>Server side only — called from the packet handler, which has already put us on the server
     * thread. Every path either settles the wager or never opens one.
     */
    public void spin(EntityPlayerMP player, double bet) {
        if (!LbeConfig.enableCasino) {
            reject(player, "The casino is closed on this server.");
            return;
        }
        if (!LbeEconomy.isOpen()) {
            reject(player, LbeEconomy.bank().unavailableReason());
            return;
        }
        double rounded = Math.floor(bet * 100.0) / 100.0;
        if (!LbeConfig.isBetAllowed(rounded)) {
            reject(player, "Bets here are between " + LbeEconomy.format(LbeConfig.minimumBet)
                + " and " + LbeEconomy.format(LbeConfig.maximumBet) + ".");
            return;
        }
        if (!cooldownExpired(player)) {
            // Silent: a player spamming the button does not need a wall of chat about it, and an
            // attacker gets no signal either way.
            return;
        }

        Wager wager = LbeEconomy.bank().stake(player, rounded, "slot machine wager");
        if (wager == null) {
            reject(player, LbeEconomy.bank().lastFailure());
            return;
        }

        // Past this point the money is held and MUST be settled on every path.
        SlotSpin result;
        try {
            result = SlotSpin.roll(random);
        } catch (RuntimeException e) {
            // Nothing here should throw, but a stake that is already held must not be stranded by
            // a bug in code that decides what it was for.
            Lbe.LOGGER.error("[casino] A spin failed after the bet was taken; refunding it.", e);
            wager.cancel();
            reject(player, "The machine jammed. Your bet has been returned.");
            return;
        }

        boolean settled = result.isWin()
            ? wager.payOut(result.payoutFor(rounded))
            : wager.loseToHouse();
        if (!settled) {
            // The bank has already logged why and left the hold open, so the money is not lost.
            reject(player, "Your bet could not be settled. It is safe — tell an operator.");
            return;
        }

        markSpun(player);
        double payout = result.isWin() ? result.payoutFor(rounded) : 0.0;
        LbeNetwork.CHANNEL.sendTo(new PacketSlotResult(result, payout, balanceOf(player)), player);

        if (result.isJackpot() && LbeConfig.announceJackpots) {
            announceJackpot(player, payout);
        }
    }

    /** Sends the player what the screen needs: their balance and the server's bet limits. */
    public void sendState(EntityPlayerMP player) {
        LbeNetwork.CHANNEL.sendTo(new PacketSlotResult(null, 0.0, balanceOf(player)), player);
    }

    private double balanceOf(EntityPlayerMP player) {
        OptionalDouble balance = LbeEconomy.bank().balance(player);
        // -1 is the wire's "unknown", which the screen renders as "—" rather than as zero. Showing
        // a player $0.00 when the truth is "we could not ask" would read as being robbed.
        return balance.isPresent() ? balance.getAsDouble() : -1.0;
    }

    private void reject(EntityPlayerMP player, String message) {
        String text = message == null || message.isEmpty() ? "That bet was refused." : message;
        player.sendMessage(new TextComponentString(text).setStyle(
            new net.minecraft.util.text.Style().setColor(TextFormatting.RED)));
    }

    private boolean cooldownExpired(EntityPlayer player) {
        Long allowedAt = nextSpinAllowed.get(player.getUniqueID());
        return allowedAt == null || world.getTotalWorldTime() >= allowedAt;
    }

    private void markSpun(EntityPlayer player) {
        long ticks = Math.max(1L, (long) (LbeConfig.spinCooldownSeconds * 20.0));
        nextSpinAllowed.put(player.getUniqueID(), world.getTotalWorldTime() + ticks);
        // The map would otherwise grow one entry per player who ever touched this machine, for the
        // lifetime of the chunk. Cheap to bound, and nothing here is worth remembering.
        if (nextSpinAllowed.size() > 64) {
            long now = world.getTotalWorldTime();
            nextSpinAllowed.values().removeIf(when -> when < now);
        }
    }

    private void announceJackpot(EntityPlayerMP player, double payout) {
        String text = player.getName() + " hit the jackpot for " + LbeEconomy.format(payout) + "!";
        player.getServer().getPlayerList().sendMessage(
            new TextComponentString(text).setStyle(
                new net.minecraft.util.text.Style().setColor(TextFormatting.GOLD)));
    }

    /**
     * The machine occupies two blocks, so the render box has to as well.
     *
     * <p>Without this the upper half is culled the moment the lower one leaves the frustum, and a
     * player standing close enough to use the machine sees the top of it disappear.
     */
    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos, pos.add(1, 2, 1));
    }
}
