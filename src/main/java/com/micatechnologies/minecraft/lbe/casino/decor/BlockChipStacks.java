package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Stacks of chips in four colours, for dressing a table or the bar.
 *
 * <p>Tables and the bar counter are 14/16 high, so anything put on one would float two pixels
 * above its top. {@link #LOW} sits the model down onto it instead. Like a rope post's connections,
 * it is worked out from the block underneath each time, never saved.
 */
public class BlockChipStacks extends Block {

    /** Sitting on something 14/16 high: drawn and selected two pixels lower. */
    public static final PropertyBool LOW = PropertyBool.create("low");

    /** The height a casino table's felt and the bar counter's top share. */
    private static final double TABLE_TOP = 14.0D / 16.0D;

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.1875D, 0.0D, 0.1875D, 0.75D,
        0.375D, 0.75D);

    public BlockChipStacks() {
        super(Material.CIRCUITS);
        setRegistryName(LbeConstants.MOD_NAMESPACE, "chip_stacks");
        setTranslationKey(LbeConstants.MOD_NAMESPACE + ".chip_stacks");
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(0.2F);
        setSoundType(SoundType.STONE);
        setDefaultState(blockState.getBaseState().withProperty(LOW, Boolean.FALSE));
    }

    /** Whether the block at {@code pos} stands on a table top or the bar, rather than a floor. */
    static boolean sitsLow(IBlockAccess world, BlockPos pos) {
        BlockPos below = pos.down();
        IBlockState under = world.getBlockState(below);
        if (under.getBlock().isAir(under, world, below)) {
            return false;
        }
        return Math.abs(under.getBoundingBox(world, below).maxY - TABLE_TOP) < 1.0E-3D;
    }

    /** {@code shape}, moved down onto a table top when there is one under {@code pos}. */
    static AxisAlignedBB placed(AxisAlignedBB shape, IBlockAccess world, BlockPos pos) {
        return sitsLow(world, pos) ? shape.offset(0.0D, TABLE_TOP - 1.0D, 0.0D) : shape;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LOW);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(LOW, sitsLow(world, pos));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return placed(SHAPE, source, pos);
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world,
                                                 BlockPos pos) {
        // Table dressing: nothing to walk into or stand on.
        return NULL_AABB;
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
        return BlockFaceShape.UNDEFINED;
    }
}
