package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Casino carpet: the loud patterned floor every casino has. A vanilla-style carpet, one pixel
 * thick, that needs something under it. Each colourway is its own block.
 */
public class BlockCasinoCarpet extends Block {

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D,
        1.0D);

    public BlockCasinoCarpet(String name) {
        super(Material.CARPET);
        setRegistryName(LbeConstants.MOD_NAMESPACE, name);
        setTranslationKey(LbeConstants.MOD_NAMESPACE + "." + name);
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(0.1F);
        setSoundType(SoundType.CLOTH);
        setLightOpacity(0);
    }

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
        return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
    }

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && !world.isAirBlock(pos.down());
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block,
                                BlockPos fromPos) {
        // Like vanilla carpet: with nothing under it, it drops as an item.
        if (world.isAirBlock(pos.down())) {
            dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
        }
    }

    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos,
                                        EnumFacing side) {
        return side == EnumFacing.UP || super.shouldSideBeRendered(state, world, pos, side);
    }
}
