package com.micatechnologies.minecraft.lbe.casino.decor;

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** Which message a plaque shows: chosen by a builder, so saved, and sent to clients to draw. */
public class TileEntityCasinoPlaque extends TileEntity {

    /**
     * The messages a plaque can show, one line per entry, in the order right-clicking steps
     * through them. Append only: a plaque saves its message by index.
     */
    public static final String[][] MESSAGES = {
        {"ADULTS ONLY", "21+"},
        {"NO MINORS", "BEYOND", "THIS POINT"},
        {"PLEASE", "GAMBLE", "RESPONSIBLY"},
        {"CASHIER"},
        {"HIGH LIMIT", "ROOM"},
        {"VIP", "LOUNGE"},
        {"RESTROOMS"},
        {"STAFF", "ONLY"},
    };

    private int message;

    /** Server: shows the next message. */
    void nextMessage() {
        message = (message + 1) % MESSAGES.length;
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    public String[] lines() {
        return MESSAGES[message];
    }

    private NBTTagCompound write(NBTTagCompound tag) {
        tag.setByte("lbeMessage", (byte) message);
        return tag;
    }

    private void read(NBTTagCompound tag) {
        int m = tag.getByte("lbeMessage");
        message = m >= 0 && m < MESSAGES.length ? m : 0;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        return write(super.writeToNBT(tag));
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        read(tag);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return write(super.getUpdateTag());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, write(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        read(packet.getNbtCompound());
    }
}
