package com.micatechnologies.minecraft.lbe.casino.decor;

import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** A neon sign's word and colour: chosen by a builder, so saved, and sent to clients to draw. */
public class TileEntityNeonSign extends TileEntity {

    /** The words a sign can show, in the order right-clicking steps through them. */
    public static final String[] WORDS = {
        "CASINO", "JACKPOT", "SLOTS", "LUCKY", "777", "OPEN", "WIN", "BAR",
    };

    private int word;
    private int colour = EnumDyeColor.MAGENTA.getMetadata();

    /** Server: shows the next word. */
    void nextWord() {
        word = (word + 1) % WORDS.length;
        changed();
    }

    /** Server: changes the colour. */
    void setColour(EnumDyeColor dye) {
        colour = dye.getMetadata();
        changed();
    }

    private void changed() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    public String word() {
        return WORDS[word];
    }

    /** The neon's colour as RGB. */
    public int rgb() {
        return EnumDyeColor.byMetadata(colour).getColorValue();
    }

    private NBTTagCompound write(NBTTagCompound tag) {
        tag.setByte("lbeWord", (byte) word);
        tag.setByte("lbeColour", (byte) colour);
        return tag;
    }

    private void read(NBTTagCompound tag) {
        int w = tag.getByte("lbeWord");
        word = w >= 0 && w < WORDS.length ? w : 0;
        int c = tag.hasKey("lbeColour") ? tag.getByte("lbeColour") : EnumDyeColor.MAGENTA.getMetadata();
        colour = c >= 0 && c < 16 ? c : EnumDyeColor.MAGENTA.getMetadata();
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
