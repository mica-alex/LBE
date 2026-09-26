package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.casino.economy.LbeEconomy;
import com.micatechnologies.minecraft.lbe.casino.stats.CasinoLedger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.util.Constants;

/**
 * Keeps a leaderboard's lines in step with the ledger, and hands them to clients to draw. The same
 * tile entity serves a {@link BlockProgressiveSign}, whose single line is the progressive pool.
 *
 * <p>The server builds the lines — names, formatted amounts, the privacy setting applied — so a
 * client is only ever sent what the board shows. It checks every couple of seconds whether the
 * ledger has changed and sends an update only when it has. Nothing is saved: a board rebuilds from
 * the ledger on its first tick.
 */
public class TileEntityCasinoLeaderboard extends TileEntity implements ITickable {

    /** Rows per section. The board has room for two sections of five. */
    public static final int ROWS = 5;

    /** How often the server checks the ledger, in ticks. */
    private static final int CHECK_EVERY = 40;

    /** Longest name a row shows before trimming, so the amount always fits. */
    private static final int NAME_LIMIT = 10;

    /** Server only: what was last sent, so an unchanged ledger sends nothing. */
    private long sentVersion = -1L;
    private boolean sentNames;
    private int ticks;

    /** Each row is "left\tright": a name and an amount. */
    private List<String> bigWins = Collections.emptyList();
    private List<String> topWinners = Collections.emptyList();

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (ticks++ % CHECK_EVERY != 0) {
            return;
        }
        CasinoStatsData data = CasinoStatsData.get(world);
        if (isProgressiveSign()) {
            long version = data.progressive().version();
            if (version == sentVersion && ticks > 1) {
                return;
            }
            sentVersion = version;
            bigWins = Collections.singletonList(LbeConfig.progressiveEnabled
                ? LbeEconomy.format(data.progressive().pool()) : "CLOSED");
            topWinners = Collections.emptyList();
        } else {
            CasinoLedger ledger = data.ledger();
            boolean names = LbeConfig.leaderboardShowsNames;
            if (ledger.version() == sentVersion && names == sentNames) {
                return;
            }
            sentVersion = ledger.version();
            sentNames = names;
            rebuild(ledger, names);
        }
        IBlockState state = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, state, state, 2);
    }

    private void rebuild(CasinoLedger ledger, boolean names) {
        List<String> wins = new ArrayList<>();
        for (CasinoLedger.BigWin win : ledger.recentBigWins()) {
            if (wins.size() == ROWS) {
                break;
            }
            wins.add(name(win.name(), names) + "\t" + LbeEconomy.format(win.amount()));
        }
        List<String> top = new ArrayList<>();
        for (CasinoLedger.Player player : ledger.topWinners(ROWS)) {
            top.add(name(player.name(), names) + "\t+" + LbeEconomy.format(player.total().net()));
        }
        bigWins = wins;
        topWinners = top;
    }

    private boolean isProgressiveSign() {
        return world != null && world.getBlockState(pos).getBlock() instanceof BlockProgressiveSign;
    }

    private static String name(String name, boolean shown) {
        if (!shown) {
            return "A player";
        }
        return name.length() <= NAME_LIMIT ? name : name.substring(0, NAME_LIMIT - 1) + ".";
    }

    /** Recent big wins, newest first, as "name\tamount". */
    public List<String> bigWins() {
        return bigWins;
    }

    /** Biggest winners, best first, as "name\tamount". */
    public List<String> topWinners() {
        return topWinners;
    }

    // ---------------------------------------------------------------------------------------------
    // Sync
    // ---------------------------------------------------------------------------------------------

    private NBTTagCompound writeLines(NBTTagCompound tag) {
        tag.setTag("lbeWins", toList(bigWins));
        tag.setTag("lbeTop", toList(topWinners));
        return tag;
    }

    private void readLines(NBTTagCompound tag) {
        bigWins = fromList(tag.getTagList("lbeWins", Constants.NBT.TAG_STRING));
        topWinners = fromList(tag.getTagList("lbeTop", Constants.NBT.TAG_STRING));
    }

    private static NBTTagList toList(List<String> lines) {
        NBTTagList list = new NBTTagList();
        for (String line : lines) {
            list.appendTag(new NBTTagString(line));
        }
        return list;
    }

    /** Bounded like anything a client is handed: a board draws at most ROWS lines per section. */
    private static List<String> fromList(NBTTagList list) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < list.tagCount() && i < ROWS; i++) {
            String line = list.getStringTagAt(i);
            lines.add(line.length() <= 48 ? line : line.substring(0, 48));
        }
        return lines;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeLines(super.getUpdateTag());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readLines(tag);
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, writeLines(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readLines(packet.getNbtCompound());
    }
}
