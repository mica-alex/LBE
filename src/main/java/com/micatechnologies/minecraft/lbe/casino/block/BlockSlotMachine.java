package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A slot machine: one block wide and deep, two tall.
 *
 * <p><b>Two blocks, one machine.</b> 1.12.2 has no multi-block primitive, so this is the same trick
 * vanilla doors and beds use — two block positions sharing a {@link #HALF} property, placed and
 * broken together. The lower half carries the {@link TileEntitySlotMachine}; the upper half is
 * scenery that forwards every interaction downward. Anything that needs the machine's state should
 * go through {@link #machineAt}, which resolves either half to the one tile entity.
 *
 * <p>The game itself is not here. This block's whole job is to be right-clickable and to survive
 * being broken from either end; the reels live in {@code casino/slots} and the money in
 * {@code casino/economy}, neither of which knows a block exists.
 */
public class BlockSlotMachine extends Block {

    /** Which half of the machine this block is. True for the top. */
    public static final PropertyBool HALF = PropertyBool.create("top");

    /** The direction the cabinet faces — the side with the reels on it. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing",
        EnumFacing.Plane.HORIZONTAL);

    /** Slightly narrower than a full block, so a row of them reads as separate cabinets. */
    private static final AxisAlignedBB SHAPE =
        new AxisAlignedBB(0.0625D, 0.0D, 0.0625D, 0.9375D, 1.0D, 0.9375D);

    public BlockSlotMachine() {
        super(Material.IRON);
        setRegistryName(LbeConstants.MOD_NAMESPACE, LbeConstants.SLOT_MACHINE_NAME);
        setTranslationKey(LbeConstants.MOD_NAMESPACE + "." + LbeConstants.SLOT_MACHINE_NAME);
        setCreativeTab(LbeTab.LBE_TAB);
        // Heavy enough to need a pickaxe: a cabinet full of money should not come apart in a fist,
        // and on a server it is usually somebody's build rather than loose scenery.
        setHardness(3.5F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setLightLevel(0.5F);
        setDefaultState(blockState.getBaseState()
            .withProperty(HALF, Boolean.FALSE)
            .withProperty(FACING, EnumFacing.NORTH));
    }

    // ---------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HALF, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        // Bit 3 is the half; bits 0-1 are the facing. Four horizontal directions fit in two bits,
        // which leaves the metadata budget comfortable.
        return getDefaultState()
            .withProperty(HALF, (meta & 8) != 0)
            .withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex()
            | (state.getValue(HALF) ? 8 : 0);
    }

    @Override
    public IBlockState withRotation(IBlockState state, net.minecraft.util.Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, net.minecraft.util.Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    // ---------------------------------------------------------------------------------------------
    // Shape
    // ---------------------------------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos,
                                            EnumFacing face) {
        // Nothing should treat a slot machine as a surface to attach a torch or a fence to.
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    // ---------------------------------------------------------------------------------------------
    // Placement and breaking — the two halves live and die together
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        // Both the space and the space above it, and the top must be inside the world.
        return super.canPlaceBlockAt(world, pos)
            && pos.getY() < world.getHeight() - 1
            && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        // Faces the player, like a furnace: they are looking at the front as they place it.
        return getDefaultState()
            .withProperty(FACING, placer.getHorizontalFacing().getOpposite())
            .withProperty(HALF, Boolean.FALSE);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        world.setBlockState(pos.up(), state.withProperty(HALF, Boolean.TRUE), 3);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // Take the other half with this one. Done here rather than in neighbourChanged so that
        // breaking either end is symmetric, and so a machine cannot be left as a floating top.
        BlockPos other = state.getValue(HALF) ? pos.down() : pos.up();
        IBlockState otherState = world.getBlockState(other);
        if (otherState.getBlock() == this && otherState.getValue(HALF) != state.getValue(HALF)) {
            world.setBlockToAir(other);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block,
                                BlockPos fromPos) {
        // Covers the ways a half can vanish without breakBlock running on it — /setblock, another
        // mod's tooling, world edits. A lone half is removed rather than left as a broken machine.
        BlockPos other = state.getValue(HALF) ? pos.down() : pos.up();
        IBlockState otherState = world.getBlockState(other);
        if (otherState.getBlock() != this || otherState.getValue(HALF) == state.getValue(HALF)) {
            world.setBlockToAir(pos);
            if (!state.getValue(HALF)) {
                dropBlockAsItem(world, pos, state, 0);
            }
        }
    }

    @Override
    public net.minecraft.item.Item getItemDropped(IBlockState state, java.util.Random rand,
                                                  int fortune) {
        // Only the bottom half drops, or breaking one machine would hand back two.
        return state.getValue(HALF) ? net.minecraft.init.Items.AIR
            : super.getItemDropped(state, rand, fortune);
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        // What middle-click hands you. Without this it would be a "top half" item.
        return new ItemStack(this);
    }

    // ---------------------------------------------------------------------------------------------
    // Tile entity
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        // Only the lower half. One machine, one tile entity, one place for its state to live.
        return !state.getValue(HALF);
    }

    @Override
    @Nullable
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(HALF) ? null : new TileEntitySlotMachine();
    }

    /**
     * The tile entity for the machine at {@code pos}, whichever half was given.
     *
     * @return the machine, or null if {@code pos} is not part of one.
     */
    @Nullable
    public static TileEntitySlotMachine machineAt(World world, BlockPos pos, IBlockState state) {
        BlockPos base = state.getValue(HALF) ? pos.down() : pos;
        TileEntity tile = world.getTileEntity(base);
        return tile instanceof TileEntitySlotMachine ? (TileEntitySlotMachine) tile : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        // NOT guarded on world.isRemote. The screen is a plain GuiScreen with no Container behind
        // it, so the client has to run this too — it is the side that actually opens the window.
        // Guarding here is the classic 1.12.2 mistake that produces a block which does nothing.
        TileEntitySlotMachine machine = machineAt(world, pos, state);
        if (machine == null) {
            return true;
        }
        machine.onActivated(player);
        return true;
    }
}
