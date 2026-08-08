package com.micatechnologies.minecraft.lbe.block;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.catalog.LootCatalog;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * The state behind one loot box: a seed, and whether it has been opened.
 *
 * <p><b>The seed is the whole point of this class existing.</b> A box could perfectly well roll its
 * contents from {@code world.rand} at the moment it is opened — until you notice that a player can
 * then break the box, place it again, and re-roll it until they like the answer. Deciding the
 * contents from a seed fixed when the box enters the world, and carrying that seed through the
 * dropped item ({@link BlockLootBox#getDrops}), makes the box's contents a property of the box
 * rather than of the moment it was opened.</p>
 *
 * <p>The contents are still computed lazily at open time rather than stored, so a config change or a
 * newly installed mod is reflected in boxes that were generated before it. That is the right trade:
 * the guarantee players care about is "this box's contents cannot be re-rolled by me", not "this
 * box's contents were fixed at world generation and are now stale".</p>
 */
public class TileEntityLootBox extends TileEntity {

    private static final String NBT_OPENED = "Opened";

    /** {@code 0} means "not yet assigned" — see {@link #seed()}. */
    private long seed;

    private boolean opened;

    /**
     * The roll seed, generating one on first request if this box never got one.
     *
     * <p>Lazy rather than assigned in the constructor because a tile entity is also constructed when
     * a chunk is <i>loaded</i>, immediately before {@link #readFromNBT} overwrites its fields. Seeding
     * eagerly would burn a random number per box per chunk load and then throw it away.</p>
     */
    public long seed() {
        if (seed == 0L) {
            seed = (world == null ? new Random() : world.rand).nextLong();
            markDirty();
        }
        return seed;
    }

    /** Set the seed, normally from a placed item's NBT. */
    public void setSeed(long seed) {
        this.seed = seed;
        markDirty();
    }

    /** Whether this box has already been opened. An opened box is removed, so this is belt-and-braces. */
    public boolean isOpened() {
        return opened;
    }

    /**
     * Open the box: roll the contents, spawn them, tell the player what they got, remove the block.
     *
     * <p>Server-side only — {@link BlockLootBox#onBlockActivated} returns before reaching here on a
     * client.</p>
     *
     * @param player the opener, for the chat feedback
     * @param tier   the box's tier, which lives on the block rather than here
     */
    public void open(EntityPlayer player, Rarity tier) {
        if (opened || world == null || world.isRemote) {
            return;
        }
        opened = true;

        List<ItemStack> contents = LootCatalog.roll(tier, new Random(seed()));

        // Removed before the drops are spawned. Doing it the other way round lets an item land in the
        // block space and immediately be swept up by the block-break drop logic, which has produced
        // duplicate-item reports in mods that got this order wrong.
        world.setBlockToAir(pos);

        for (ItemStack stack : contents) {
            spawnAt(world, pos, stack);
        }

        world.playSound(null, pos, net.minecraft.init.SoundEvents.ENTITY_ITEM_PICKUP,
            SoundCategory.BLOCKS, 0.7F, tier == Rarity.LEGENDARY ? 0.6F : 1.2F);

        if (player != null) {
            player.sendStatusMessage(new TextComponentString(
                tier.colourCode() + "Opened a " + tier.id() + " loot box"
                    + (contents.isEmpty() ? " — and it was empty!" : " (" + contents.size()
                        + (contents.size() == 1 ? " item)" : " items)"))), true);
        }
    }

    /**
     * Drop a stack at the box's position with no pickup delay, so the opener gets it immediately
     * rather than watching it sit on the floor for the vanilla 10-tick delay.
     */
    private static void spawnAt(World world, BlockPos pos, ItemStack stack) {
        EntityItem item = new EntityItem(world,
            pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
        item.setPickupDelay(0);
        // A small upward nudge with no horizontal component: contents pop up and settle where the box
        // was, instead of scattering down whatever slope the box happened to generate on.
        item.motionX = 0.0D;
        item.motionY = 0.12D;
        item.motionZ = 0.0D;
        world.spawnEntity(item);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        seed = compound.getLong(LbeConstants.NBT_SEED);
        opened = compound.getBoolean(NBT_OPENED);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setLong(LbeConstants.NBT_SEED, seed);
        compound.setBoolean(NBT_OPENED, opened);
        return compound;
    }

    /**
     * Survive a block-state change without being recreated.
     *
     * <p>Not currently reachable — the box has no block states to change between — but the default
     * is to discard the tile entity on any state change, and rediscovering that the hard way (every
     * box in the world silently re-seeding after a state property is added) is a bad afternoon.</p>
     */
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }
}
