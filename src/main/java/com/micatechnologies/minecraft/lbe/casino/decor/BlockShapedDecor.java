package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import javax.annotation.Nullable;
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
 * Decor that is one shape and nothing else: no facing, no state, no tile entity. The chandelier
 * and the slot-bank light pillar; both give off light, which costs a lighting update when placed
 * and nothing after.
 */
public class BlockShapedDecor extends Block {

    private final AxisAlignedBB shape;
    private final boolean solid;

    /**
     * @param solid whether it can be walked into; a chandelier hangs over heads and cannot.
     */
    public BlockShapedDecor(String name, Material material, SoundType sound, float light,
                            AxisAlignedBB shape, boolean solid) {
        super(material);
        this.shape = shape;
        this.solid = solid;
        setRegistryName(LbeConstants.MOD_NAMESPACE, name);
        setTranslationKey(LbeConstants.MOD_NAMESPACE + "." + name);
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(1.5F);
        setResistance(6.0F);
        setSoundType(sound);
        setLightLevel(light);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return shape;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world,
                                                 BlockPos pos) {
        return solid ? shape : NULL_AABB;
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
