package com.micatechnologies.minecraft.lbe.block;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.catalog.LootCatalog;
import com.micatechnologies.minecraft.lbe.network.LbeNetwork;
import com.micatechnologies.minecraft.lbe.network.PacketRevealLoot;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

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

        // Removed before anything is handed out. The other order lets an item land in the block space
        // and immediately be swept up by the block-break drop logic, which has produced
        // duplicate-item reports in mods that got this order wrong.
        world.setBlockToAir(pos);

        // PAY FIRST, THEN PERFORM. Every item is in the player's hands before the reveal screen is so
        // much as told they exist, so skipping the animation, closing it, or dropping connection
        // halfway through cannot cost anyone a reward. A reveal that gates the payout is a reveal
        // that eats payouts on disconnect, and no amount of care in the GUI fixes that afterwards.
        for (ItemStack stack : contents) {
            give(player, stack.copy());
        }

        world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_CHEST_OPEN,
            SoundCategory.BLOCKS, 0.7F, tier == Rarity.LEGENDARY ? 0.7F : 1.1F);
        spawnOpenParticles(tier);

        if (player instanceof EntityPlayerMP) {
            LbeNetwork.CHANNEL.sendTo(new PacketRevealLoot(tier, contents), (EntityPlayerMP) player);
        }
    }

    /**
     * Put a stack in the player's inventory, dropping whatever will not fit at their feet.
     *
     * <p>Into the inventory rather than onto the floor because the reveal screen shows what you
     * <i>got</i>, and items scattered around your feet while you watch an animation is a good way to
     * lose a legendary down a ravine. The overflow drop is at the player, not at the box — by the
     * time it happens the box is gone and the player may already have stepped away.</p>
     */
    private static void give(EntityPlayer player, ItemStack stack) {
        if (player == null) {
            return;
        }
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    /**
     * A burst of tier-coloured sparks where the box was.
     *
     * <p>The opener normally has a full-screen reveal in front of this, so it is mostly for everyone
     * <i>else</i> — and for the opener the moment they skip the screen. Spawned through
     * {@code WorldServer} so the particles are sent to every nearby client rather than being created
     * on a server that has no renderer.</p>
     */
    private void spawnOpenParticles(Rarity tier) {
        if (!(world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) world;
        int count = 12 + tier.ordinal() * 12;
        server.spawnParticle(EnumParticleTypes.END_ROD,
            pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D,
            count, 0.25D, 0.3D, 0.25D, 0.04D);
        if (tier.atLeast(Rarity.RARE)) {
            // Only the top two tiers get the second, showier burst — if every box threw fireworks,
            // none of them would mean anything.
            server.spawnParticle(EnumParticleTypes.TOTEM,
                pos.getX() + 0.5D, pos.getY() + 0.7D, pos.getZ() + 0.5D,
                count, 0.3D, 0.3D, 0.3D, 0.22D);
        }
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

    /**
     * Expand the render box upward to cover the hovering glint.
     *
     * <p>The default render bounding box is the block itself, and the glint drawn by
     * {@code TileEntityLootBoxRenderer} floats well above it — so without this it vanishes the moment
     * the block's own cube leaves the camera frustum, which happens constantly when you look slightly
     * up at a box on a ledge.</p>
     *
     * <p>Annotated client-only because that is what the annotation means here: the method exists on
     * {@code TileEntity} for the renderer's benefit and is never called on a server. The
     * <em>class</em> stays common — a server loads it for the NBT and the roll.</p>
     */
    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        return new net.minecraft.util.math.AxisAlignedBB(pos, pos.add(1, 3, 1));
    }
}
