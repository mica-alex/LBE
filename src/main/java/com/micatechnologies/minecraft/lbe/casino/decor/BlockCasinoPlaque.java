package com.micatechnologies.minecraft.lbe.casino.decor;

import com.micatechnologies.minecraft.lbe.casino.block.BlockCasinoLeaderboard;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A brass-framed wall plaque: ADULTS ONLY 21+ at the door, the way to the cashier, the high
 * limit room. Right-click steps through a fixed set of messages, as a neon sign does with its
 * words, so there is no screen and no new client-to-server message.
 *
 * <p>It enforces nothing. Who may play is a server's rule, not a sign's.
 */
public class BlockCasinoPlaque extends BlockCasinoLeaderboard {

    public BlockCasinoPlaque() {
        super("casino_plaque");
        // Printed, not lit: it should read like signage, not like the neon beside it.
        setLightLevel(0.0F);
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityCasinoPlaque();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) {
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof TileEntityCasinoPlaque) {
            ((TileEntityCasinoPlaque) tile).nextMessage();
        }
        return true;
    }
}
