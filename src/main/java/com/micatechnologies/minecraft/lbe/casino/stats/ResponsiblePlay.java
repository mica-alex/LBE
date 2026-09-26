package com.micatechnologies.minecraft.lbe.casino.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comp points, daily loss limits and self-exclusion: the casino looking after its players.
 *
 * <h2>Comp points</h2>
 *
 * <p>One point per unit staked, redeemable for loot boxes. A comp is a rebate, and a rebate adds
 * straight onto every game's return, so its rate is capped at {@link #MAX_COMP_RATE}: with it,
 * even blackjack played perfectly stays under break-even, so comps can never be farmed at a profit.
 *
 * <h2>Loss limits</h2>
 *
 * <p>A day is a UTC day. A player's loss for the day is what they staked minus what came back. A
 * bet is refused if losing <i>all</i> of it would take the day past their limit: the operator's
 * server-wide cap, or their own lower personal limit, whichever is smaller. Lowering a personal
 * limit takes effect at once; raising or removing one waits {@link #RAISE_DELAY_MILLIS}, the
 * standard guard against raising it in the heat of a losing streak.
 *
 * <h2>Self-exclusion</h2>
 *
 * <p>A player can bar themselves for a number of days. It cannot be undone early by the player,
 * which is the point of it; an operator can lift it.
 *
 * <p>Pure, with no Minecraft types. Times are epoch milliseconds, passed in.
 */
public final class ResponsiblePlay {

    /** The most a comp can be worth, per unit staked. See the class notes. */
    public static final double MAX_COMP_RATE = 0.005;

    /** How long raising or removing a personal limit takes to come into force. */
    public static final long RAISE_DELAY_MILLIS = 24L * 60L * 60L * 1000L;

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    /** One player's record. */
    public static final class Player {
        double points;
        long day = -1L;
        double lossToday;
        /** Personal daily limit in force, or negative for none. */
        double limit = -1.0;
        /** A raised limit waiting to take effect (negative: removal), and when it does. */
        double pendingLimit;
        long pendingAt;
        boolean hasPending;
        long excludedUntil;

        public long points() {
            return (long) Math.floor(points);
        }

        public double lossToday() {
            return lossToday;
        }

        /** The personal limit in force, or negative for none. */
        public double limit() {
            return limit;
        }

        public boolean hasPendingRaise() {
            return hasPending;
        }

        public double pendingLimit() {
            return pendingLimit;
        }

        public long pendingAt() {
            return pendingAt;
        }

        public long excludedUntil() {
            return excludedUntil;
        }
    }

    private final Map<UUID, Player> players = new HashMap<>();

    private Player player(UUID id) {
        return players.computeIfAbsent(id, key -> new Player());
    }

    /** A player's record, brought up to date for {@code now}. */
    public Player view(UUID id, long now) {
        Player p = player(id);
        roll(p, now);
        return p;
    }

    /** Every player, for saving. */
    public Map<UUID, Player> players() {
        return players;
    }

    /** Starts a new day's loss count, and brings a waiting raise into force once it is due. */
    private static void roll(Player p, long now) {
        long today = Math.floorDiv(now, DAY_MILLIS);
        if (p.day != today) {
            p.day = today;
            p.lossToday = 0.0;
        }
        if (p.hasPending && now >= p.pendingAt) {
            p.limit = p.pendingLimit;
            p.hasPending = false;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Playing
    // ---------------------------------------------------------------------------------------------

    /**
     * Whether a player may stake {@code amount} now, and if not, why, phrased for them.
     *
     * @param serverCap the operator's daily cap, or 0 or less for none
     * @return null when the bet may go ahead
     */
    public String refusal(UUID id, double amount, double serverCap, long now) {
        return refusal(id, amount, serverCap, now,
            value -> String.format(java.util.Locale.ROOT, "%.2f", value));
    }

    /** As {@link #refusal(UUID, double, double, long)}, with amounts written by {@code money}. */
    public String refusal(UUID id, double amount, double serverCap, long now,
                          java.util.function.DoubleFunction<String> money) {
        Player p = view(id, now);
        if (p.excludedUntil > now) {
            long days = (p.excludedUntil - now + DAY_MILLIS - 1) / DAY_MILLIS;
            return "You have excluded yourself from the casino for " + days + " more day"
                + (days == 1 ? "" : "s") + ".";
        }
        double limit = effectiveLimit(p, serverCap);
        if (limit >= 0.0 && p.lossToday + amount > limit + 1e-9) {
            return "That bet could take you past your daily loss limit of "
                + money.apply(limit) + ". It resets at midnight UTC.";
        }
        return null;
    }

    /** Whether the player is excluded right now. */
    public boolean isExcluded(UUID id, long now) {
        return view(id, now).excludedUntil > now;
    }

    /** The limit that applies: the smaller of the server's cap and the player's own. */
    public static double effectiveLimit(Player p, double serverCap) {
        boolean server = serverCap > 0.0;
        boolean personal = p.limit >= 0.0;
        if (server && personal) {
            return Math.min(serverCap, p.limit);
        }
        return server ? serverCap : personal ? p.limit : -1.0;
    }

    /** Records a settled round: today's loss, and the comp points it earned. */
    public void record(UUID id, double staked, double returned, double compRate, long now) {
        Player p = view(id, now);
        p.lossToday = Math.max(0.0, p.lossToday + staked - returned);
        if (compRate > 0.0) {
            p.points += staked;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Comps
    // ---------------------------------------------------------------------------------------------

    /** Points a reward costs: its money value divided by the comp rate. */
    public static long pointsFor(double value, double compRate) {
        // Config stores the rate as a float, so 0.005 arrives as 0.00499999988...; dividing by that
        // made a $40 box cost 8,001 points. Rounding the rate back to what was meant fixes every
        // price at once, where a tolerance on the quotient only fixed the small ones.
        double rate = Math.min(MAX_COMP_RATE, Math.round(compRate * 1.0e7) / 1.0e7);
        return rate <= 0.0 ? Long.MAX_VALUE : (long) Math.ceil(value / rate);
    }

    /** Spends points if the player has enough. True when spent. */
    public boolean spend(UUID id, long cost) {
        Player p = player(id);
        if (cost <= 0 || p.points + 1e-9 < cost) {
            return false;
        }
        p.points -= cost;
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Limits and exclusion
    // ---------------------------------------------------------------------------------------------

    /**
     * Sets a personal daily limit, or removes it with a negative amount.
     *
     * @return true when it took effect at once; false when it is a raise and waits
     */
    public boolean setLimit(UUID id, double amount, long now) {
        Player p = view(id, now);
        boolean lowering = amount >= 0.0 && (p.limit < 0.0 || amount <= p.limit);
        if (lowering) {
            p.limit = amount;
            p.hasPending = false;
            return true;
        }
        p.pendingLimit = amount;
        p.pendingAt = now + RAISE_DELAY_MILLIS;
        p.hasPending = true;
        return false;
    }

    /** Excludes a player for {@code days}. An exclusion is only ever extended, never shortened. */
    public long exclude(UUID id, int days, long now) {
        Player p = view(id, now);
        p.excludedUntil = Math.max(p.excludedUntil, now + days * DAY_MILLIS);
        return p.excludedUntil;
    }

    /** Operator: removes a player's personal limit at once, waiting raise and all. */
    public void clearLimit(UUID id) {
        Player p = player(id);
        p.limit = -1.0;
        p.hasPending = false;
    }

    /** Operator: ends an exclusion. */
    public void lift(UUID id) {
        player(id).excludedUntil = 0L;
    }

    /** For restoring from a save. */
    public Player restore(UUID id) {
        return player(id);
    }

    /** Package-free setters for the save; nothing else should use these. */
    public static void restoreFields(Player p, double points, long day, double lossToday,
                                     double limit, boolean hasPending, double pendingLimit,
                                     long pendingAt, long excludedUntil) {
        p.points = points;
        p.day = day;
        p.lossToday = lossToday;
        p.limit = limit;
        p.hasPending = hasPending;
        p.pendingLimit = pendingLimit;
        p.pendingAt = pendingAt;
        p.excludedUntil = excludedUntil;
    }

    /** The raw points, for saving. */
    public static double rawPoints(Player p) {
        return p.points;
    }

    public static long rawDay(Player p) {
        return p.day;
    }
}
