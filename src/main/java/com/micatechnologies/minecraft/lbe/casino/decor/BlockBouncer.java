package com.micatechnologies.minecraft.lbe.casino.decor;

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
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A bouncer in a black suit and dark glasses, standing at the door.
 *
 * <p>Not an entity. A placed NPC would be a living entity ticking every tick for as long as its
 * chunk is loaded, and a casino wants several. This is a two-block-tall block instead, with a
 * renderer that draws a figure on it and turns its head toward whoever is nearby — on the client,
 * every frame, at no cost to the server. It cannot wander, despawn, be pushed or be killed, which
 * is everything a bouncer should not do anyway.
 *
 * <p>It blocks the space it stands in, and right-clicking it gets a line. It enforces nothing.
 */
public class BlockBouncer extends Block {

    /** Which half this is. The tile entity, and so the renderer, is on the bottom one. */
    public static final PropertyBool HALF = PropertyBool.create("top");

    /** The way the bouncer faces: toward whoever placed it. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing",
        EnumFacing.Plane.HORIZONTAL);

    /** How many {@code lbe.bouncer.line.N} keys the lang file has. */
    private static final int LINES = 8;

    private static final AxisAlignedBB SHAPE = new AxisAlignedBB(0.1875D, 0.0D, 0.1875D, 0.8125D,
        1.0D, 0.8125D);

    public BlockBouncer() {
        super(Material.CLOTH);
        setRegistryName(LbeConstants.MOD_NAMESPACE, "casino_bouncer");
        setTranslationKey(LbeConstants.MOD_NAMESPACE + ".casino_bouncer");
        setCreativeTab(LbeTab.LBE_TAB);
        setHardness(1.5F);
        setResistance(10.0F);
        setSoundType(SoundType.CLOTH);
        setDefaultState(blockState.getBaseState()
            .withProperty(HALF, Boolean.FALSE)
            .withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, HALF, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
            .withProperty(HALF, (meta & 8) != 0)
            .withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(HALF) ? 8 : 0);
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

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        // Drawn by TileEntityBouncerRenderer, like a chest: nothing goes into the chunk mesh.
        return EnumBlockRenderType.ENTITYBLOCK_ANIMATED;
    }

    // ---------------------------------------------------------------------------------------------
    // Placement and breaking: both halves live and die together, as a tall machine's do
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos) && pos.getY() < world.getHeight() - 1
            && world.getBlockState(pos.up()).getBlock().isReplaceable(world, pos.up());
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
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
        // A half left alone by /setblock or a world edit goes too, rather than standing there as
        // a pair of legs.
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
        // Only the bottom half drops, or breaking one bouncer would hand back two.
        return state.getValue(HALF) ? net.minecraft.init.Items.AIR
            : super.getItemDropped(state, rand, fortune);
    }

    @Override
    public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
        return new ItemStack(this);
    }

    // ---------------------------------------------------------------------------------------------
    // Tile entity and talking
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return !state.getValue(HALF);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(HALF) ? null : new TileEntityBouncer();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return true;
        }
        if (!world.isRemote) {
            // Translated on the reader's client, so a line is in whatever language they play in.
            TextComponentTranslation line = new TextComponentTranslation(
                "lbe.bouncer.line." + world.rand.nextInt(LINES));
            TextComponentTranslation said = new TextComponentTranslation("lbe.bouncer.says", line);
            said.setStyle(new Style().setColor(TextFormatting.GRAY));
            player.sendMessage(said);
        }
        return true;
    }
}
