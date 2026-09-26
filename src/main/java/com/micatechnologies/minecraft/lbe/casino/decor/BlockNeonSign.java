package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.casino.block.BlockCasinoLeaderboard;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A neon sign: a glowing word on a dark backing board, hung on a wall.
 *
 * <p>Right-click shows the next word; right-click with dye changes the colour. The words are a fixed
 * set rather than typed text, so there is no screen and no new client-to-server message. The panel,
 * its shape and how it hangs are the leaderboard's.
 */
public class BlockNeonSign extends BlockCasinoLeaderboard {

    public BlockNeonSign() {
        super("neon_sign");
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityNeonSign();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (world.isRemote || !(tile instanceof TileEntityNeonSign)) {
            return true;
        }
        TileEntityNeonSign sign = (TileEntityNeonSign) tile;
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() == Items.DYE) {
            sign.setColour(EnumDyeColor.byDyeDamage(held.getMetadata()));
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
        } else {
            sign.nextWord();
        }
        return true;
    }
}
