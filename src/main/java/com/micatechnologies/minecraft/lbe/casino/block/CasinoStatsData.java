package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.LbeConfig;
import com.micatechnologies.minecraft.lbe.casino.slots.ProgressiveJackpot;
import com.micatechnologies.minecraft.lbe.casino.stats.CasinoLedger;
import com.micatechnologies.minecraft.lbe.casino.stats.ResponsiblePlay;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * The {@link CasinoLedger}, kept in the world save as {@code data/lbe_casino_stats.dat}.
 *
 * <p>One ledger per server, not per dimension: it lives in the global map storage, so a casino in
 * the Nether and one in the Overworld add up to the same player's totals.
 *
 * <p>It also holds the slots' {@link ProgressiveJackpot}. The two share a file for convenience
 * only: the ledger is history nothing reads back, while the pool is money-bearing state that
 * decides what a jackpot pays.
 *
 * <p>Server thread only.
 */
public class CasinoStatsData extends WorldSavedData {

    private static final String NAME = "lbe_casino_stats";

    private final CasinoLedger ledger = new CasinoLedger();
    private final ProgressiveJackpot progressive =
        new ProgressiveJackpot(LbeConfig.progressiveSeed);
    private final ResponsiblePlay responsible = new ResponsiblePlay();

    /** Called reflectively by {@link MapStorage} when loading; the name must be accepted as is. */
    public CasinoStatsData(String name) {
        super(name);
    }

    /** The server's ledger, loaded from the save or created on first use. */
    public static CasinoStatsData get(World world) {
        MapStorage storage = world.getMapStorage();
        CasinoStatsData data = (CasinoStatsData) storage.getOrLoadData(CasinoStatsData.class, NAME);
        if (data == null) {
            data = new CasinoStatsData(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    public CasinoLedger ledger() {
        return ledger;
    }

    public ProgressiveJackpot progressive() {
        return progressive;
    }

    public ResponsiblePlay responsible() {
        return responsible;
    }

    /** Why a player may not stake {@code amount} right now, or null if they may. */
    public String refusal(UUID id, double amount) {
        return responsible.refusal(id, amount, LbeConfig.dailyLossCap, System.currentTimeMillis());
    }

    /** Anything that changes the responsible-play records marks the save. */
    public void changed() {
        markDirty();
    }

    /** Feeds a slot stake's share into the pool and marks the save dirty. */
    public void contributeProgressive(double stake) {
        progressive.contribute(stake, LbeConfig.progressiveShare);
        markDirty();
    }

    /** Empties the pool into a win that has been paid, reseeds it, and marks the save dirty. */
    public double collectProgressive() {
        double won = progressive.collect(LbeConfig.progressiveSeed);
        markDirty();
        return won;
    }

    /** Records a settled round and marks the save dirty. */
    public void record(UUID id, String name, String gameId, double bet, double totalReturn) {
        ledger.record(id, name, gameId, bet, totalReturn);
        responsible.record(id, bet, totalReturn, LbeConfig.compRate, System.currentTimeMillis());
        markDirty();
    }

    /** Adds a big win to the board's list and marks the save dirty. */
    public void bigWin(CasinoLedger.BigWin win) {
        ledger.bigWin(win);
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        NBTTagList players = tag.getTagList("players", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound player = players.getCompoundTagAt(i);
            UUID id;
            try {
                id = UUID.fromString(player.getString("id"));
            } catch (IllegalArgumentException e) {
                continue;   // a mangled entry loses one player's history, not the file
            }
            String name = player.getString("name");
            NBTTagList games = player.getTagList("games", Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < games.tagCount(); j++) {
                NBTTagCompound game = games.getCompoundTagAt(j);
                ledger.restoreTally(id, name, game.getString("game"), game.getLong("rounds"),
                    game.getDouble("staked"), game.getDouble("returned"),
                    game.getDouble("biggest"));
            }
        }
        if (tag.hasKey("progressive")) {
            progressive.restore(tag.getDouble("progressive"));
        }
        NBTTagList care = tag.getTagList("responsible", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < care.tagCount(); i++) {
            NBTTagCompound entry = care.getCompoundTagAt(i);
            try {
                ResponsiblePlay.Player p = responsible.restore(UUID.fromString(entry.getString("id")));
                ResponsiblePlay.restoreFields(p, entry.getDouble("points"), entry.getLong("day"),
                    entry.getDouble("loss"), entry.getDouble("limit"), entry.getBoolean("pending"),
                    entry.getDouble("pendingLimit"), entry.getLong("pendingAt"),
                    entry.getLong("excludedUntil"));
            } catch (IllegalArgumentException e) {
                // One mangled entry loses one player's record, not the file.
            }
        }
        NBTTagList wins = tag.getTagList("bigWins", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < wins.tagCount(); i++) {
            NBTTagCompound win = wins.getCompoundTagAt(i);
            ledger.restoreBigWin(new CasinoLedger.BigWin(win.getString("name"),
                win.getString("game"), win.getDouble("amount"), win.getLong("when")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList players = new NBTTagList();
        for (Map.Entry<UUID, CasinoLedger.Player> entry : ledger.players().entrySet()) {
            NBTTagCompound player = new NBTTagCompound();
            player.setString("id", entry.getKey().toString());
            player.setString("name", entry.getValue().name());
            NBTTagList games = new NBTTagList();
            for (Map.Entry<String, CasinoLedger.Tally> game : entry.getValue().games().entrySet()) {
                NBTTagCompound tally = new NBTTagCompound();
                tally.setString("game", game.getKey());
                tally.setLong("rounds", game.getValue().rounds());
                tally.setDouble("staked", game.getValue().staked());
                tally.setDouble("returned", game.getValue().returned());
                tally.setDouble("biggest", game.getValue().biggestReturn());
                games.appendTag(tally);
            }
            player.setTag("games", games);
            players.appendTag(player);
        }
        tag.setTag("players", players);
        NBTTagList wins = new NBTTagList();
        for (CasinoLedger.BigWin win : ledger.recentBigWins()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("name", win.name());
            entry.setString("game", win.game());
            entry.setDouble("amount", win.amount());
            entry.setLong("when", win.when());
            wins.appendTag(entry);
        }
        tag.setTag("bigWins", wins);
        tag.setDouble("progressive", progressive.exactPool());
        NBTTagList care = new NBTTagList();
        for (Map.Entry<UUID, ResponsiblePlay.Player> entry : responsible.players().entrySet()) {
            ResponsiblePlay.Player p = entry.getValue();
            NBTTagCompound out = new NBTTagCompound();
            out.setString("id", entry.getKey().toString());
            out.setDouble("points", ResponsiblePlay.rawPoints(p));
            out.setLong("day", ResponsiblePlay.rawDay(p));
            out.setDouble("loss", p.lossToday());
            out.setDouble("limit", p.limit());
            out.setBoolean("pending", p.hasPendingRaise());
            out.setDouble("pendingLimit", p.pendingLimit());
            out.setLong("pendingAt", p.pendingAt());
            out.setLong("excludedUntil", p.excludedUntil());
            care.appendTag(out);
        }
        tag.setTag("responsible", care);
        return tag;
    }
}
