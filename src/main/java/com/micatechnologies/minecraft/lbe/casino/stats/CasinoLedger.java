package com.micatechnologies.minecraft.lbe.casino.stats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * What the casino remembers about who played what: per player, per game, how many rounds, how much
 * was staked and how much came back, and a short list of the most recent big wins.
 *
 * <p>Pure, with no Minecraft types, like the rest of {@code casino/}; {@code CasinoStatsData}
 * stores it in the world save. It is a record of money that has <b>already</b> moved — nothing
 * that decides a game or a payout ever reads it — so a lost or reset ledger loses history and
 * nothing else.
 *
 * <p>Server thread only.
 */
public final class CasinoLedger {

    /** How many recent big wins are kept for the leaderboard. */
    public static final int RECENT_BIG_WINS = 10;

    /** One game's totals for one player. */
    public static final class Tally {
        long rounds;
        double staked;
        double returned;
        double biggestReturn;

        public long rounds() {
            return rounds;
        }

        public double staked() {
            return staked;
        }

        public double returned() {
            return returned;
        }

        /** What came back minus what went in. Negative for a player who is down. */
        public double net() {
            return returned - staked;
        }

        /** The largest single round's total return. */
        public double biggestReturn() {
            return biggestReturn;
        }

        void add(double bet, double totalReturn) {
            rounds++;
            staked += bet;
            returned += totalReturn;
            biggestReturn = Math.max(biggestReturn, totalReturn);
        }
    }

    /** One player's record: the name they last played under, and a tally per game. */
    public static final class Player {
        String name;
        final Map<String, Tally> games = new TreeMap<>();

        Player(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        /** Tallies keyed by game id, in a stable order. */
        public Map<String, Tally> games() {
            return Collections.unmodifiableMap(games);
        }

        /** Every game added together. */
        public Tally total() {
            Tally total = new Tally();
            for (Tally tally : games.values()) {
                total.rounds += tally.rounds;
                total.staked += tally.staked;
                total.returned += tally.returned;
                total.biggestReturn = Math.max(total.biggestReturn, tally.biggestReturn);
            }
            return total;
        }
    }

    /** A win worth putting on the board. */
    public static final class BigWin {
        final String name;
        final String game;
        final double amount;
        final long when;

        public BigWin(String name, String game, double amount, long when) {
            this.name = name;
            this.game = game;
            this.amount = amount;
            this.when = when;
        }

        public String name() {
            return name;
        }

        /** The game's display name. */
        public String game() {
            return game;
        }

        /** The round's total return. */
        public double amount() {
            return amount;
        }

        /** Epoch milliseconds. */
        public long when() {
            return when;
        }
    }

    private final Map<UUID, Player> players = new HashMap<>();
    private final Deque<BigWin> recent = new ArrayDeque<>();
    private long version;

    /** Records a settled round. {@code name} replaces any older name, so renames follow through. */
    public void record(UUID id, String name, String gameId, double bet, double totalReturn) {
        Player player = players.computeIfAbsent(id, key -> new Player(name));
        player.name = name;
        player.games.computeIfAbsent(gameId, key -> new Tally()).add(bet, totalReturn);
        version++;
    }

    /** Puts a big win at the top of the recent list, dropping the oldest past the limit. */
    public void bigWin(BigWin win) {
        recent.addFirst(win);
        while (recent.size() > RECENT_BIG_WINS) {
            recent.removeLast();
        }
        version++;
    }

    /** A player's record, or null if they have never played. */
    public Player player(UUID id) {
        return players.get(id);
    }

    /** Looks a player up by the name they last played under, ignoring case. */
    public Map.Entry<UUID, Player> playerNamed(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (Map.Entry<UUID, Player> entry : players.entrySet()) {
            if (entry.getValue().name.toLowerCase(Locale.ROOT).equals(wanted)) {
                return entry;
            }
        }
        return null;
    }

    /** Every player, for saving. */
    public Map<UUID, Player> players() {
        return Collections.unmodifiableMap(players);
    }

    /** The recent big wins, newest first. */
    public List<BigWin> recentBigWins() {
        return new ArrayList<>(recent);
    }

    /**
     * The players furthest ahead, best first. Only players who are up are listed: a board of the
     * casino's biggest losers is not something anyone asked to be on.
     */
    public List<Player> topWinners(int limit) {
        List<Player> ahead = new ArrayList<>();
        for (Player player : players.values()) {
            if (player.total().net() > 0.0) {
                ahead.add(player);
            }
        }
        ahead.sort(Comparator.comparingDouble((Player p) -> p.total().net()).reversed()
            .thenComparing(p -> p.name));
        return ahead.size() > limit ? new ArrayList<>(ahead.subList(0, limit)) : ahead;
    }

    /** Bumped on every change, so a leaderboard can tell whether it needs to refresh. */
    public long version() {
        return version;
    }

    // ---------------------------------------------------------------------------------------------
    // Restoring from a save. Public only because the save lives outside this pure package; nothing
    // but CasinoStatsData should call these.
    // ---------------------------------------------------------------------------------------------

    public void restoreTally(UUID id, String name, String gameId, long rounds, double staked,
                      double returned, double biggestReturn) {
        Player player = players.computeIfAbsent(id, key -> new Player(name));
        Tally tally = player.games.computeIfAbsent(gameId, key -> new Tally());
        tally.rounds = rounds;
        tally.staked = staked;
        tally.returned = returned;
        tally.biggestReturn = biggestReturn;
    }

    public void restoreBigWin(BigWin win) {
        if (recent.size() < RECENT_BIG_WINS) {
            recent.addLast(win);
        }
    }
}
