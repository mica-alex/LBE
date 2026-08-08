package com.micatechnologies.minecraft.lbe.block;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A loot box. One instance of this class exists per {@link Rarity} — four blocks, not one block with
 * a tier property.
 *
 * <p>Four separate blocks because the tier is <b>identity</b>, not state: each wants its own registry
 * name so a pack can reference {@code lbe:loot_box_rare} in a structure or a command, its own item so
 * a player can hold and stack one tier without it merging with another, and its own model. A metadata
 * property would have given us one registry entry and then required subtype ItemBlocks, per-metadata
 * models and a metadata-aware creative tab to claw all of that back.</p>
 *
 * <p>Opening happens entirely server-side: {@link #onBlockActivated} returns early on the client, the
 * tile entity rolls its contents from its own persisted seed, and the items are spawned into the
 * world rather than into the player's inventory so a full pack cannot swallow a legendary box.</p>
 */
public class BlockLootBox extends Block {

    private final Rarity rarity;

    public BlockLootBox(Rarity rarity) {
        super(Material.WOOD);
        this.rarity = rarity;
        // Soft enough to open with a fist — a box you need a pickaxe for is a box a new player walks
        // past. Blast resistance is low to match: these are crates, not vaults.
        setHardness(1.0F);
        setResistance(3.0F);
        setSoundType(SoundType.WOOD);
        setRegistryName(LbeConstants.MOD_NAMESPACE, LbeConstants.LOOT_BOX_PREFIX + rarity.id());
        setTranslationKey(LbeConstants.MOD_NAMESPACE + "." + LbeConstants.LOOT_BOX_PREFIX
            + rarity.id());
        setCreativeTab(LbeTab.LBE_TAB);
    }

    /** The tier of boxes this block places. */
    public Rarity rarity() {
        return rarity;
    }

    /**
     * Higher tiers glow, so a legendary box is visible from across a dark cave.
     *
     * <p>Common boxes emit nothing at all — if every box glowed, the glow would stop meaning
     * anything, and the point of the light is to make the rare find findable.</p>
     */
    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        switch (rarity) {
            case LEGENDARY:
                return 12;
            case RARE:
                return 7;
            case UNCOMMON:
                return 3;
            default:
                return 0;
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityLootBox();
    }

    /**
     * Carry the seed from the placed item into the tile entity.
     *
     * <p>This is the half of the anti-reroll mechanism that lives on placement; {@link #getDrops}
     * is the other half. Together they mean a box's contents are decided the moment it enters the
     * world and cannot be changed by breaking it and putting it back down. Without this, "mine it,
     * replace it, open it again" is a loot-box slot machine with a free respin.</p>
     */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
                                ItemStack stack) {
        if (world.isRemote) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityLootBox)) {
            return;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(LbeConstants.NBT_SEED)) {
            ((TileEntityLootBox) tile).setSeed(tag.getLong(LbeConstants.NBT_SEED));
        }
    }

    /**
     * Drop the box itself, with its seed intact, rather than dropping its contents.
     *
     * <p>Breaking a box is <b>moving</b> it, not opening it. That is a deliberate design choice and
     * not just an implementation convenience: a box you can pick up is a box you can carry home and
     * open where your storage is, which is a much nicer thing to find in a cave than one that
     * scatters twelve stacks across a ravine the moment you touch it.</p>
     */
    @Override
    public void getDrops(net.minecraft.util.NonNullList<ItemStack> drops, IBlockAccess world,
                         BlockPos pos, IBlockState state, int fortune) {
        ItemStack stack = new ItemStack(this);
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityLootBox) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong(LbeConstants.NBT_SEED, ((TileEntityLootBox) tile).seed());
            stack.setTagCompound(tag);
        }
        drops.add(stack);
    }

    /**
     * Delay this block's removal until after {@link #harvestBlock} has run.
     *
     * <p><b>Without this pair of overrides the seed is silently lost every time a player mines a
     * box</b>, and the anti-reroll guarantee the whole design rests on quietly stops holding.</p>
     *
     * <p>The vanilla player-harvest path is {@code onBlockHarvested} → {@code removedByPlayer} →
     * {@code harvestBlock} → {@code getDrops}. {@code removedByPlayer} sets the block to air, which
     * runs {@code breakBlock} and destroys the tile entity — so by the time {@link #getDrops} looks
     * for the seed, there is nothing there to ask, and the replacement box gets a fresh roll.</p>
     *
     * <p>Returning {@code true} here when the block is about to be harvested defers the removal;
     * {@link #harvestBlock} then does it explicitly once the drops have been built. This is the
     * standard 1.12.2 idiom for any block whose drops carry tile-entity data, and the failure it
     * prevents is invisible in testing unless you specifically check the NBT of a mined box.</p>
     */
    @Override
    public boolean removedByPlayer(IBlockState state, World world, BlockPos pos, EntityPlayer player,
                                   boolean willHarvest) {
        if (willHarvest) {
            return true;
        }
        return super.removedByPlayer(state, world, pos, player, willHarvest);
    }

    /** Completes the deferred removal set up by {@link #removedByPlayer}. */
    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
                             TileEntity tile, ItemStack tool) {
        super.harvestBlock(world, player, pos, state, tile, tool);
        world.setBlockToAir(pos);
    }

    /**
     * Right-click to open.
     *
     * <p>The client returns {@code true} without doing anything so the swing animation plays and the
     * interaction is not passed on to the item in hand; every decision that matters — rolling the
     * contents, spawning them, removing the block — happens on the server only.</p>
     */
    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                    EnumHand hand, EnumFacing facing, float hitX, float hitY,
                                    float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityLootBox) {
            ((TileEntityLootBox) tile).open(player, rarity);
        }
        return true;
    }

    /**
     * A stack of this block carrying a freshly generated seed.
     *
     * <p>Used by {@code /lbe give} and by world generation. Note that a box created without going
     * through here still works — {@link TileEntityLootBox} generates its own seed on first use —
     * so this is about giving a <i>stack</i> its identity before it is ever placed, which is what
     * makes two boxes in an inventory not stack together into one shared roll.</p>
     */
    public ItemStack createStack(Random random) {
        ItemStack stack = new ItemStack(this);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong(LbeConstants.NBT_SEED, random.nextLong());
        stack.setTagCompound(tag);
        return stack;
    }
}
