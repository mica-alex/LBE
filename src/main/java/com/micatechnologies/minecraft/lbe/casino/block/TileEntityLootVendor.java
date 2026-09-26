package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.Lbe;
import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.block.LbeBlocks;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.casino.economy.Wager;
import com.micatechnologies.minecraft.lbe.rarity.Rarity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

/**
 * A machine that sells loot boxes for money: the one place the casino's currency turns into the
 * mod's other half.
 *
 * <p><b>No new packet.</b> Sneak-right-click shows the next tier; right-click asks to buy it, and a
 * second right-click within a few seconds buys. A stray click therefore never spends money, and
 * {@code PacketCasinoPlay} stays the only message a client can send the casino.
 *
 * <p><b>A true money sink.</b> The price is staked through {@code CasinoBank} and forfeited to the
 * house, exactly like a lost bet, so it goes through SUM's escrow and leaves the economy. The box is
 * minted with its own seed, like any other.
 */
public class TileEntityLootVendor extends TileEntity {

    /** How long a purchase waits for its confirming click, in ticks. */
    private static final long CONFIRM_TICKS = 60L;

    /** The tier on sale. Saved: it is the vendor's setting, not game state. */
    private Rarity tier = Rarity.lowest();

    /** Client only: the price as the server formatted it, for the display. */
    private String priceText = "";

    /** Server only: who asked to buy, and until when their confirming click counts. */
    private final Map<UUID, Long> pending = new HashMap<>();

    /** Server: a right-click on the vendor. */
    public void use(EntityPlayer player, boolean sneaking) {
        if (world == null || world.isRemote) {
            return;
        }
        if (sneaking) {
            Rarity[] all = Rarity.values();
            tier = all[(tier.ordinal() + 1) % all.length];
            pending.clear();
            markDirty();
            sync();
            status(player, TextFormatting.YELLOW, "Now selling: ",
                tierName(), " for " + LbeEconomy.format(LbeConfig.boxPrice(tier)));
            return;
        }
        if (!LbeConfig.enableCasino || !LbeEconomy.isOpen()) {
            status(player, TextFormatting.RED, LbeConfig.enableCasino
                ? LbeEconomy.bank().unavailableReason() : "The casino is closed on this server.",
                null, "");
            return;
        }
        if (CasinoStatsData.get(world).responsible().isExcluded(player.getUniqueID(),
                System.currentTimeMillis())) {
            status(player, TextFormatting.RED, "You have excluded yourself from the casino.", null,
                "");
            return;
        }
        long now = world.getTotalWorldTime();
        Long until = pending.get(player.getUniqueID());
        if (until == null || now > until) {
            pending.put(player.getUniqueID(), now + CONFIRM_TICKS);
            status(player, TextFormatting.GOLD, "Right-click again to buy a ", tierName(),
                " box for " + LbeEconomy.format(LbeConfig.boxPrice(tier)));
            return;
        }
        pending.remove(player.getUniqueID());
        buy(player);
    }

    private void buy(EntityPlayer player) {
        double price = LbeConfig.boxPrice(tier);
        Wager wager = LbeEconomy.bank().stake(player, price, "Loot box purchase");
        if (wager == null) {
            status(player, TextFormatting.RED, LbeEconomy.bank().lastFailure(), null, "");
            return;
        }
        if (!wager.loseToHouse()) {
            // The bank has logged why and kept the hold open, so the money is safe; no box.
            status(player, TextFormatting.RED,
                "The purchase could not be completed. Your money is safe - tell an operator.",
                null, "");
            return;
        }
        ItemStack box = LbeBlocks.box(tier).createStack(world.rand);
        if (!player.inventory.addItemStackToInventory(box)) {
            EntityItem item = new EntityItem(world, player.posX, player.posY, player.posZ, box);
            item.setNoPickupDelay();
            world.spawnEntity(item);
        }
        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.8F, 1.0F);
        world.playSound(null, pos, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.BLOCKS, 0.6F, 1.4F);
        status(player, TextFormatting.GREEN, "Bought a ", tierName(),
            " box for " + LbeEconomy.format(price));
        Lbe.LOGGER.info("[casino] {} bought a {} loot box for {}", player.getName(), tier.id(),
            LbeEconomy.format(price));
    }

    private TextComponentTranslation tierName() {
        TextComponentTranslation name = new TextComponentTranslation("lbe.tier." + tier.id());
        name.getStyle().setColor(TextFormatting.WHITE);
        return name;
    }

    /** An action-bar line: a coloured lead, an optional tier name, and a tail. */
    private static void status(EntityPlayer player, TextFormatting colour, String lead,
                               @Nullable TextComponentTranslation tier, String tail) {
        TextComponentString line = new TextComponentString(lead);
        line.getStyle().setColor(colour);
        if (tier != null) {
            line.appendSibling(tier);
        }
        if (!tail.isEmpty()) {
            TextComponentString rest = new TextComponentString(tail);
            rest.getStyle().setColor(colour);
            line.appendSibling(rest);
        }
        player.sendStatusMessage(line, true);
    }

    /** The tier on sale. */
    public Rarity tier() {
        return tier;
    }

    /** Client: the price to show, as the server formatted it. */
    public String priceText() {
        return priceText;
    }

    private void sync() {
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 2);
    }

    // ---------------------------------------------------------------------------------------------
    // Save and sync
    // ---------------------------------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("lbeTier", tier.id());
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        Rarity saved = tag.hasKey("lbeTier") ? Rarity.byId(tag.getString("lbeTier")) : null;
        tier = saved == null ? Rarity.lowest() : saved;
    }

    private NBTTagCompound writeDisplay(NBTTagCompound tag) {
        tag.setString("lbeTier", tier.id());
        // Config is never synced, so the server formats the price it will actually charge.
        tag.setString("lbePrice", LbeEconomy.format(LbeConfig.boxPrice(tier)));
        return tag;
    }

    private void readDisplay(NBTTagCompound tag) {
        Rarity sent = Rarity.byId(tag.getString("lbeTier"));
        if (sent != null) {
            tier = sent;
        }
        String price = tag.getString("lbePrice");
        priceText = price.length() <= 24 ? price : "";
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeDisplay(super.getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readDisplay(tag);
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, writeDisplay(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readDisplay(packet.getNbtCompound());
    }
}
