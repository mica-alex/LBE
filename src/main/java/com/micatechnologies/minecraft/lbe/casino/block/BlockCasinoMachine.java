package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeConstants;
import com.micatechnologies.minecraft.lbe.LbeTab;
import com.micatechnologies.minecraft.lbe.casino.CasinoGame;
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
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Any casino machine: one class, one instance per {@link CasinoGame}.
 *
 * <p>Two shapes, chosen by the game's {@link CasinoGame.Cabinet}. A <b>tall</b> cabinet is two block
 * positions sharing a {@link #HALF} property, placed and broken together the way a vanilla door is —
 * the lower half carries the tile entity and the upper half is scenery that forwards interactions
 * down. A <b>table</b> is a single waist-height block.
 *
 * <p>No game logic here at all. This block's whole job is to be right-clickable, to face the way it
 * was placed, and to survive being broken from either end. What happens next belongs to
 * {@link TileEntityCasinoMachine}, and what it means belongs to the pure classes under
 * {@code casino/}.
 */
public class BlockCasinoMachine extends Block {

    /** Which half of a tall cabinet this is. Always false for a table. */
    public static final PropertyBool HALF = PropertyBool.create("top");

    /** The side the player stands at. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing",
        EnumFacing.Plane.HORIZONTAL);

    /** Slightly inset, so a row of machines reads as separate units rather than a wall. */
    private static final AxisAlignedBB TALL_SHAPE =
        new AxisAlignedBB(0.0625D, 0.0D, 0.0625D, 0.9375D, 1.0D, 0.9375D);

    /** A table is full width and waist height, so it reads as something to stand at. */
    private static final AxisAlignedBB TABLE_SHAPE =
        new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D);

    private final CasinoGame game;

    public BlockCasinoMachine(CasinoGame game) {
        super(Material.IRON);
        this.game = game;
        setRegistryName(LbeConstants.MOD_NAMESPACE, game.registryName());
        setTranslationKey(game.translationKey());
        setCreativeTab(LbeTab.LBE_TAB);
        // Needs a pickaxe: a cabinet full of money should not come apart in a fist, and on a server
        // it is usually somebody's build rather than loose scenery.
        setHardness(3.5F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setLightLevel(0.5F);
        setDefaultState(blockState.getBaseState()
            .withProperty(HALF, Boolean.FALSE)
            .withProperty(FACING, EnumFacing.NORTH));
    }

    /** Which game this cabinet runs. */
    public CasinoGame game() {
        return game;
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

    // ---------------------------------------------------------------------------------------------
    // Shape
    // ---------------------------------------------------------------------------------------------

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return game.isTall() ? TALL_SHAPE : TABLE_SHAPE;
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
        // Nothing should treat a casino machine as a surface to hang a torch on.
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    // ---------------------------------------------------------------------------------------------
    // Placement and breaking — a tall cabinet's halves live and die together
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        if (!super.canPlaceBlockAt(world, pos)) {
            return false;
        }
        if (!game.isTall()) {
            return true;
        }
        return pos.getY() < world.getHeight() - 1
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
        if (game.isTall()) {
            world.setBlockState(pos.up(), state.withProperty(HALF, Boolean.TRUE), 3);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (game.isTall()) {
            // Take the other half with this one, so breaking either end is symmetric and a machine
            // cannot be left as a floating top.
            BlockPos other = state.getValue(HALF) ? pos.down() : pos.up();
            IBlockState otherState = world.getBlockState(other);
            if (otherState.getBlock() == this && otherState.getValue(HALF) != state.getValue(HALF)) {
                world.setBlockToAir(other);
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block,
                                BlockPos fromPos) {
        if (!game.isTall()) {
            return;
        }
        // Covers the ways a half can vanish without breakBlock running — /setblock, world edits,
        // another mod's tooling. A lone half is removed rather than left as a broken machine.
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
        // One machine, one tile entity, one place for its state to live.
        return !state.getValue(HALF);
    }

    @Override
    @Nullable
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(HALF) ? null : new TileEntityCasinoMachine();
    }

    /**
     * The tile entity for the machine at {@code pos}, whichever half was given.
     *
     * @return the machine, or null if {@code pos} is not part of one.
     */
    @Nullable
    public static TileEntityCasinoMachine machineAt(World world, BlockPos pos, IBlockState state) {
        BlockPos base = state.getValue(HALF) ? pos.down() : pos;
        TileEntity tile = world.getTileEntity(base);
        return tile instanceof TileEntityCasinoMachine ? (TileEntityCasinoMachine) tile : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Interaction
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        // NOT guarded on world.isRemote. These screens are plain GuiScreens with no Container behind
        // them, so the client has to run this too — it is the side that opens the window. Guarding
        // here is the classic 1.12.2 mistake that produces a block which does nothing at all.
        TileEntityCasinoMachine machine = machineAt(world, pos, state);
        if (machine != null) {
            machine.onActivated(player, game);
        }
        return true;
    }
}
