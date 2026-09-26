package com.micatechnologies.minecraft.lbe.casino.block;

import com.micatechnologies.minecraft.lbe.casino.stats.CasinoLedger;
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
 * <p>Server thread only.
 */
public class CasinoStatsData extends WorldSavedData {

    private static final String NAME = "lbe_casino_stats";

    private final CasinoLedger ledger = new CasinoLedger();

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

    /** Records a settled round and marks the save dirty. */
    public void record(UUID id, String name, String gameId, double bet, double totalReturn) {
        ledger.record(id, name, gameId, bet, totalReturn);
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
        return tag;
    }
}
