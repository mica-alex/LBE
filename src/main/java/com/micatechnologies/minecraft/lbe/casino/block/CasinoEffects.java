package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.casino.CasinoFanfare;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * What the rest of the casino floor notices about a round: the sound other players hear, and the
 * chat line when it is a jackpot.
 *
 * <p><b>Why these are delayed.</b> The server settles a round the moment the bet arrives, but the
 * player's screen deliberately animates for {@link CasinoFanfare#REVEAL_TICKS} before it shows
 * them the outcome. A win chime played at settlement, or a "you won" line in chat, would tell the
 * player how the reels land two seconds before the reels do. So each effect is queued here and
 * fired when the reveal ends.
 *
 * <p>The player's own sounds are not played from here at all: the screen plays them, in step with
 * its own animation. Every world sound here excludes the player for that reason, so nothing plays
 * twice.
 *
 * <p>Entirely cosmetic. Nothing here is read back by anything that decides money, and a queue lost
 * to a server stop loses nothing but a sound.
 *
 * <p>Server thread only.
 */
public final class CasinoEffects {

    /** Every effect still waiting for its reveal to end, across all worlds. */
    private static final List<Pending> PENDING = new ArrayList<>();

    /** Enough for a busy floor; past this, new effects are dropped rather than queued forever. */
    private static final int MAX_PENDING = 256;

    /** Constructed once, by {@code Lbe}, to register {@link #onServerTick} on the event bus. */
    public CasinoEffects() {
    }

    /**
     * Queues what the floor notices about a round that has just settled.
     *
     * @param player who played it, excluded from the world sound because their screen plays it
     * @param delayTicks how long until their screen shows the result; 0 when it already has
     * @param announcement the chat line for the whole server, or {@code null} for none
     */
    public static void roundSettled(WorldServer world, BlockPos pos, EntityPlayer player,
                                    CasinoFanfare fanfare, int delayTicks,
                                    @Nullable ITextComponent announcement) {
        if (!fanfare.isHeardByBystanders() && announcement == null) {
            return;
        }
        if (PENDING.size() >= MAX_PENDING) {
            return;
        }
        PENDING.add(new Pending(world, pos, player.getUniqueID(), fanfare,
            world.getTotalWorldTime() + Math.max(0, delayTicks), announcement));
    }

    /** Drops everything queued. Called when the server stops. */
    public static void clear() {
        PENDING.clear();
    }

    /** Fires every effect whose reveal has ended. Registered on the Forge event bus. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            if (pending.world.getTotalWorldTime() < pending.dueTick) {
                continue;
            }
            it.remove();
            fire(pending);
        }
    }

    private static void fire(Pending pending) {
        WorldServer world = pending.world;
        // The player may have logged out during the reveal; then there is no one to exclude and
        // everyone nearby, which no longer includes them, hears it.
        EntityPlayer player = world.getMinecraftServer() == null ? null
            : world.getMinecraftServer().getPlayerList().getPlayerByUUID(pending.playerId);
        if (pending.fanfare.isHeardByBystanders() && world.isBlockLoaded(pending.pos)) {
            playForBystanders(world, pending.pos, player, pending.fanfare);
        }
        if (pending.announcement != null && world.getMinecraftServer() != null) {
            world.getMinecraftServer().getPlayerList().sendMessage(pending.announcement);
        }
    }

    private static void playForBystanders(WorldServer world, BlockPos pos,
                                          @Nullable EntityPlayer player, CasinoFanfare fanfare) {
        switch (fanfare) {
            case JACKPOT:
                play(world, pos, player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
                play(world, pos, player, SoundEvents.ENTITY_FIREWORK_TWINKLE, 1.0F, 1.0F);
                break;
            case BIG_WIN:
                play(world, pos, player, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.7F, 1.0F);
                break;
            case WIN:
                play(world, pos, player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.2F);
                break;
            default:
                break;
        }
    }

    /**
     * {@code World#playSound} with a player argument sends to everyone nearby <i>except</i> that
     * player, which is exactly the split wanted here.
     */
    private static void play(WorldServer world, BlockPos pos, @Nullable EntityPlayer except,
                             SoundEvent sound, float volume, float pitch) {
        world.playSound(except, pos, sound, SoundCategory.BLOCKS, volume, pitch);
    }

    private static final class Pending {
        final WorldServer world;
        final BlockPos pos;
        final UUID playerId;
        final CasinoFanfare fanfare;
        final long dueTick;
        @Nullable
        final ITextComponent announcement;

        Pending(WorldServer world, BlockPos pos, UUID playerId, CasinoFanfare fanfare,
                long dueTick, @Nullable ITextComponent announcement) {
            this.world = world;
            this.pos = pos;
            this.playerId = playerId;
            this.fanfare = fanfare;
            this.dueTick = dueTick;
            this.announcement = announcement;
        }
    }
}
