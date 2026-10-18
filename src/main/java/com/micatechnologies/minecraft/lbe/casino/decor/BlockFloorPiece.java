package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A piece of casino-floor furniture with a front: the cashier's cage, the bar counter, the back
 * bar. It faces whoever places it, like a machine, and its shape turns with it.
 *
 * <p>Nothing here ticks or keeps a tile entity; the facing is the only state, and it lives in the
 * block's metadata.
 */
public class BlockFloorPiece extends Block {

    /** The customer's side: the way the front looks. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing",
        EnumFacing.Plane.HORIZONTAL);

    /** The shape for each facing, by horizontal index. */
    private final AxisAlignedBB[] shapes = new AxisAlignedBB[4];

    /**
     * @param shape the shape facing north, as the model is drawn; turned for the other facings.
     */
    public BlockFloorPiece(String name, Material material, SoundType sound, float hardness,
                           AxisAlignedBB shape) {
        super(material);
        setRegistryName(LbeConstants.MOD_NAMESPACE, name);
        setTranslationKey(LbeConstants.MOD_NAMESPACE + "." + name);
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(hardness);
        setResistance(6.0F);
        setSoundType(sound);
        for (EnumFacing facing : EnumFacing.HORIZONTALS) {
            shapes[facing.getHorizontalIndex()] = turn(shape, facing);
        }
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    /** Turns a north-facing box the way the blockstate turns the model. */
    static AxisAlignedBB turn(AxisAlignedBB box, EnumFacing facing) {
        switch (facing) {
            case EAST:
                return new AxisAlignedBB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ,
                    box.maxY, box.maxX);
            case SOUTH:
                return new AxisAlignedBB(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX,
                    box.maxY, 1 - box.minZ);
            case WEST:
                return new AxisAlignedBB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY,
                    1 - box.minX);
            default:
                return box;
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return shapes[state.getValue(FACING).getHorizontalIndex()];
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
