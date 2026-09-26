package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.casino.CasinoFanfare;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * What the rest of the casino floor notices about a round: the sound other players hear, the
 * particles over the machine, the redstone signal a win gives off, the advancements it earns, the
 * chat line and bonus loot box when it is a jackpot.
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
     * @param tall whether the machine is a two-block cabinet, so effects rise from its top
     * @param player who played it, excluded from the world sound because their screen plays it
     * @param delayTicks how long until their screen shows the result; 0 when it already has
     * @param announcement the chat line for the whole server, or {@code null} for none
     * @param advancements casino advancements the round earned, granted at the reveal
     */
    public static void roundSettled(WorldServer world, BlockPos pos, boolean tall,
                                    EntityPlayer player, CasinoFanfare fanfare, int delayTicks,
                                    @Nullable ITextComponent announcement,
                                    List<String> advancements) {
        if (!fanfare.isHeardByBystanders() && announcement == null && advancements.isEmpty()) {
            return;
        }
        if (PENDING.size() >= MAX_PENDING) {
            return;
        }
        PENDING.add(new Pending(world, pos, tall, player.getUniqueID(), fanfare,
            world.getTotalWorldTime() + Math.max(0, delayTicks), announcement,
            new ArrayList<>(advancements)));
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
            spawnParticles(world, pending.pos, pending.tall, pending.fanfare);
            net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pending.pos);
            if (tile instanceof TileEntityCasinoMachine) {
                ((TileEntityCasinoMachine) tile).pulseSignal(pending.fanfare);
            }
        }
        if (pending.announcement != null && world.getMinecraftServer() != null) {
            world.getMinecraftServer().getPlayerList().sendMessage(pending.announcement);
        }
        if (player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            for (String advancement : pending.advancements) {
                CasinoAdvancements.grant((net.minecraft.entity.player.EntityPlayerMP) player,
                    advancement, "earned");
            }
        }
        if (pending.fanfare == CasinoFanfare.JACKPOT
                && com.micatechnologies.minecraft.lbe.LbeConfig.jackpotLootBox
                && world.isBlockLoaded(pending.pos)) {
            dropJackpotBox(world, pending.pos, pending.tall);
        }
    }

    /**
     * A legendary loot box, popped out of the top of the machine. Seeded like any other box, so it
     * cannot be re-rolled; items rather than money, so no game's return changes.
     */
    private static void dropJackpotBox(WorldServer world, BlockPos pos, boolean tall) {
        net.minecraft.item.ItemStack box = com.micatechnologies.minecraft.lbe.block.LbeBlocks
            .box(com.micatechnologies.minecraft.lbe.rarity.Rarity.LEGENDARY)
            .createStack(world.rand);
        net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(world,
            pos.getX() + 0.5D, pos.getY() + (tall ? 2.1D : 1.0D), pos.getZ() + 0.5D, box);
        item.motionX = 0.0D;
        item.motionY = 0.25D;
        item.motionZ = 0.0D;
        item.setDefaultPickupDelay();
        world.spawnEntity(item);
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
     * A burst over the machine, scaled by the win. Sent to every client in range, the player
     * included: they see it the moment they close the screen, and everyone else sees it at once.
     */
    private static void spawnParticles(WorldServer world, BlockPos pos, boolean tall,
                                       CasinoFanfare fanfare) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + (tall ? 2.1D : 1.0D);
        double z = pos.getZ() + 0.5D;
        switch (fanfare) {
            case JACKPOT:
                world.spawnParticle(EnumParticleTypes.TOTEM, x, y, z, 60, 0.4D, 0.4D, 0.4D, 0.6D);
                world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y + 0.3D, z, 40,
                    0.3D, 0.3D, 0.3D, 0.15D);
                break;
            case BIG_WIN:
                world.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x, y, z, 24,
                    0.3D, 0.2D, 0.3D, 0.08D);
                world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y, z, 10,
                    0.4D, 0.3D, 0.4D, 0.0D);
                break;
            case WIN:
                world.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY, x, y, z, 6,
                    0.35D, 0.2D, 0.35D, 0.0D);
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
        final boolean tall;
        final UUID playerId;
        final CasinoFanfare fanfare;
        final long dueTick;
        @Nullable
        final ITextComponent announcement;
        final List<String> advancements;

        Pending(WorldServer world, BlockPos pos, boolean tall, UUID playerId, CasinoFanfare fanfare,
                long dueTick, @Nullable ITextComponent announcement, List<String> advancements) {
            this.world = world;
            this.pos = pos;
            this.tall = tall;
            this.playerId = playerId;
            this.fanfare = fanfare;
            this.dueTick = dueTick;
            this.announcement = announcement;
            this.advancements = advancements;
        }
    }
}
