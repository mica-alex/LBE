package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A wall-mounted board showing the casino's recent big wins and biggest winners.
 *
 * <p>A thin panel against the block behind it, like a painting. It shows what {@code /casino top}
 * shows and nothing more: the public view of the ledger, refreshed by its tile entity when the
 * ledger changes. Decoration with no money anywhere near it, so it works with or without SUM.
 */
public class BlockCasinoLeaderboard extends Block {

    /** The side the board's face looks out of, toward whoever is reading it. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing",
        EnumFacing.Plane.HORIZONTAL);

    /** Two pixels deep, against the wall behind the face. Indexed by horizontal index. */
    private static final AxisAlignedBB[] SHAPES = {
        new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 0.125D),     // south: wall to the north
        new AxisAlignedBB(0.875D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D),     // west: wall to the east
        new AxisAlignedBB(0.0D, 0.0D, 0.875D, 1.0D, 1.0D, 1.0D),     // north: wall to the south
        new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.125D, 1.0D, 1.0D),     // east: wall to the west
    };

    public BlockCasinoLeaderboard() {
        this("casino_leaderboard");
    }

    /** For boards that show something else on the same kind of panel. */
    protected BlockCasinoLeaderboard(String name) {
        super(Material.IRON);
        setRegistryName(LbeConstants.MOD_NAMESPACE, name);
        setTranslationKey(LbeConstants.MOD_NAMESPACE + "." + name);
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(2.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setLightLevel(0.4F);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
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
        // Clicked on a wall: hang flat against it. Otherwise face whoever placed it.
        EnumFacing out = facing.getAxis().isHorizontal() ? facing
            : placer.getHorizontalFacing().getOpposite();
        return getDefaultState().withProperty(FACING, out);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return SHAPES[state.getValue(FACING).getHorizontalIndex()];
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

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityCasinoLeaderboard();
    }
}
