package com.micatechnologies.minecraft.lbe.casino.slots;

/**
 * The progressive jackpot shared by every slot machine on a server.
 *
 * <p>A slice of each slot stake is added to the pool; three sevens win the pool on top of the
 * spin's own payout, and the pool drops back to its seed. Unlike the casino's ledger this <b>is</b>
 * money-bearing state: the pool decides what a jackpot pays.
 *
 * <h2>What it costs the house</h2>
 *
 * <p>Over the long run every unit contributed is paid back out, so the pool adds exactly its share
 * to slots' return: {@link SlotPaytable#returnToPlayer()} plus {@link #share}, plus a little for
 * each reseed. With the default 1% that is about 85%, still well inside the band
 * {@code HouseEdgeTest} enforces. {@link #MAX_SHARE} keeps a misconfigured server from turning slots
 * into a machine that pays out more than it takes.
 *
 * <p>Pure, with no Minecraft types. Server thread only.
 */
public final class ProgressiveJackpot {

    /** The share of each slot stake that feeds the pool by default. */
    public static final double DEFAULT_SHARE = 0.01;

    /** The largest share a server may configure: slots plus this stays well under break-even. */
    public static final double MAX_SHARE = 0.05;

    private double pool;
    private long version;

    public ProgressiveJackpot(double seed) {
        this.pool = Math.max(0.0, seed);
    }

    /** What the pool would pay right now, in whole cents, rounded down. */
    public double pool() {
        // The nudge (a ten-thousandth of a cent) is for pools saved before contribute() rounded
        // the share, which sit a hair under a whole cent and would otherwise show a cent short.
        return Math.floor(pool * 100.0 + 1.0e-4) / 100.0;
    }

    /** Adds a stake's share to the pool. Kept exact; only paying out rounds. */
    public void contribute(double stake, double share) {
        // Config stores the share as a float, so 0.01 arrives as 0.0099999998; twenty $100 spins
        // then fed $19.9999995 and the sign showed a cent short. Round it back to what was meant.
        double clamped = Math.max(0.0, Math.min(MAX_SHARE, Math.round(share * 1.0e7) / 1.0e7));
        if (stake > 0.0 && clamped > 0.0) {
            pool += stake * clamped;
            version++;
        }
    }

    /**
     * Empties the pool into a win and reseeds it. Call only once the win has actually been paid;
     * a payout that fails must leave the pool where it was.
     *
     * @return what the pool paid, in whole cents
     */
    public double collect(double seed) {
        double won = pool();
        pool = Math.max(0.0, seed);
        version++;
        return won;
    }

    /** Bumped on every change, so a sign knows when to refresh. */
    public long version() {
        return version;
    }

    /** Slots' long-run return with the pool included, ignoring the reseeds. */
    public static double slotsReturnWith(double share) {
        return SlotPaytable.returnToPlayer() + Math.max(0.0, Math.min(MAX_SHARE, share));
    }

    /** For restoring from a save; nothing else should set the pool directly. */
    public void restore(double pool) {
        this.pool = Math.max(0.0, pool);
    }

    /** The exact pool, for saving. */
    public double exactPool() {
        return pool;
    }
}
