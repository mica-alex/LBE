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

/**
 * A bar stool for the front of a machine or a table: a chrome pole under a red cushion.
 * Decoration only; sitting would need a seat entity, which nothing here has asked for yet.
 */
public class BlockBarStool extends Block {

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.1875D, 0.0D, 0.1875D, 0.8125D,
        0.75D, 0.8125D);

    public BlockBarStool() {
        super(Material.IRON);
        setRegistryName(LbeConstants.MOD_NAMESPACE, "bar_stool");
        setTranslationKey(LbeConstants.MOD_NAMESPACE + ".bar_stool");
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(1.5F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
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
        return BlockFaceShape.UNDEFINED;
    }
}
