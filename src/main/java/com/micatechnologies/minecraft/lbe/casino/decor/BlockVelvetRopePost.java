package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A brass stanchion. Put two side by side and a red velvet rope runs between them.
 *
 * <p>Connections work like a fence's: worked out from the neighbours each time the state is asked
 * for, never saved. They only reach other posts, so a rope line is always post to post. A rope
 * blocks walking through at fence height, which is what makes a queue line a queue.
 */
public class BlockVelvetRopePost extends Block {

    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool WEST = PropertyBool.create("west");

    private static final AxisAlignedBB POST = new AxisAlignedBB(0.3125D, 0.0D, 0.3125D, 0.6875D,
        1.0D, 0.6875D);

    /** Collision is fence height, so a rope cannot simply be stepped over. */
    private static final AxisAlignedBB POST_COLLISION = new AxisAlignedBB(0.375D, 0.0D, 0.375D,
        0.625D, 1.5D, 0.625D);
    private static final AxisAlignedBB ROPE_NORTH = new AxisAlignedBB(0.4375D, 0.0D, 0.0D, 0.5625D,
        1.5D, 0.5D);
    private static final AxisAlignedBB ROPE_SOUTH = new AxisAlignedBB(0.4375D, 0.0D, 0.5D, 0.5625D,
        1.5D, 1.0D);
    private static final AxisAlignedBB ROPE_WEST = new AxisAlignedBB(0.0D, 0.0D, 0.4375D, 0.5D,
        1.5D, 0.5625D);
    private static final AxisAlignedBB ROPE_EAST = new AxisAlignedBB(0.5D, 0.0D, 0.4375D, 1.0D,
        1.5D, 0.5625D);

    public BlockVelvetRopePost() {
        super(Material.IRON);
        setRegistryName(LbeConstants.MOD_NAMESPACE, "velvet_rope_post");
        setTranslationKey(LbeConstants.MOD_NAMESPACE + ".velvet_rope_post");
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(1.5F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
        setDefaultState(blockState.getBaseState().withProperty(NORTH, Boolean.FALSE)
            .withProperty(EAST, Boolean.FALSE).withProperty(SOUTH, Boolean.FALSE)
            .withProperty(WEST, Boolean.FALSE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(NORTH, connects(world, pos, EnumFacing.NORTH))
            .withProperty(EAST, connects(world, pos, EnumFacing.EAST))
            .withProperty(SOUTH, connects(world, pos, EnumFacing.SOUTH))
            .withProperty(WEST, connects(world, pos, EnumFacing.WEST));
    }

    private boolean connects(IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getBlockState(pos.offset(side)).getBlock() == this;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return POST;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox, List<AxisAlignedBB> boxes,
                                      @Nullable Entity entity, boolean isActualState) {
        IBlockState actual = isActualState ? state : getActualState(state, world, pos);
        addCollisionBoxToList(pos, entityBox, boxes, POST_COLLISION);
        if (actual.getValue(NORTH)) {
            addCollisionBoxToList(pos, entityBox, boxes, ROPE_NORTH);
        }
        if (actual.getValue(SOUTH)) {
            addCollisionBoxToList(pos, entityBox, boxes, ROPE_SOUTH);
        }
        if (actual.getValue(WEST)) {
            addCollisionBoxToList(pos, entityBox, boxes, ROPE_WEST);
        }
        if (actual.getValue(EAST)) {
            addCollisionBoxToList(pos, entityBox, boxes, ROPE_EAST);
        }
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
