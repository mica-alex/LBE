package com.micatechnologies.minecraft.lbe.casino.decor;

import javax.annotation.Nullable;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * A dealer's card shoe, its next card half drawn toward whoever placed it. Table dressing that
 * sits down onto a table or the bar the way {@link BlockChipStacks} does.
 */
public class BlockCardShoe extends BlockFloorPiece {

    public BlockCardShoe() {
        super("card_shoe", Material.CIRCUITS, SoundType.WOOD, 0.2F,
            new AxisAlignedBB(0.25D, 0.0D, 0.03125D, 0.75D, 0.25D, 0.8125D));
        setDefaultState(getDefaultState().withProperty(BlockChipStacks.LOW, Boolean.FALSE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, BlockChipStacks.LOW);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(BlockChipStacks.LOW, BlockChipStacks.sitsLow(world, pos));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BlockChipStacks.placed(super.getBoundingBox(state, source, pos), source, pos);
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world,
                                                 BlockPos pos) {
        return NULL_AABB;
    }
}
