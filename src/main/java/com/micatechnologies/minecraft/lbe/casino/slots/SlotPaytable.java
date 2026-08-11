package com.micatechnologies.minecraft.lbe.casino.slots;

/**
 * What a set of reels pays, and — the part that matters — what that costs the economy.
 *
 * <p>Pure arithmetic over {@link SlotSymbol}. No Minecraft types, no randomness, no money: this
 * exists so the question "how much does this machine take per spin, on average?" has an exact answer
 * that a unit test can hold to, rather than an impression formed by playing it for a while.
 *
 * <p><b>Why exact and not simulated.</b> A slot machine's return converges slowly — the jackpot here
 * lands about once in 14,000 spins and carries a fifth of the total return with it, so a million
 * simulated spins still leaves the estimate visibly noisy. Closed form has no such problem, and it
 * turns a tuning mistake into a failing test instead of a slow leak somebody notices three weeks
 * later when the server's money supply has doubled.
 */
public final class SlotPaytable {

    /** Two cherries and one other symbol. Consolation, and the only non-triple win. */
    public static final int TWO_CHERRY_MULTIPLIER = 2;

    /** A machine has three reels. Assumed throughout; the maths below is written for it. */
    public static final int REELS = 3;

    private SlotPaytable() {
        throw new AssertionError("No instances.");
    }

    /** Sum of every symbol's weight — the denominator for a single reel. */
    public static int totalWeight() {
        int total = 0;
        for (SlotSymbol symbol : SlotSymbol.values()) {
            total += symbol.weight();
        }
        return total;
    }

    /** The chance one reel stops on {@code symbol}. */
    public static double probability(SlotSymbol symbol) {
        return (double) symbol.weight() / totalWeight();
    }

    /**
     * What {@code reels} pays, as a multiple of the bet, "for 1".
     *
     * <p>Zero means the bet is lost. One would mean the stake back exactly, which this paytable
     * never returns — every win here is a real win.
     *
     * @throws IllegalArgumentException if the wrong number of reels is supplied, which would
     *     otherwise silently evaluate as a loss and quietly eat a player's bet.
     */
    public static int multiplierFor(SlotSymbol[] reels) {
        if (reels == null || reels.length != REELS) {
            throw new IllegalArgumentException("A spin has exactly " + REELS + " reels.");
        }
        if (reels[0] == reels[1] && reels[1] == reels[2]) {
            return reels[0].tripleMultiplier();
        }
        int cherries = 0;
        for (SlotSymbol reel : reels) {
            if (reel == SlotSymbol.CHERRY) {
                cherries++;
            }
        }
        return cherries == 2 ? TWO_CHERRY_MULTIPLIER : 0;
    }

    /**
     * The long-run fraction of money wagered that comes back to players, in {@code [0, 1]}.
     *
     * <p>0.84 means a player keeps 84 cents of every dollar they push through and the house keeps
     * 16. Above 1.0 the machine is a money printer and the server's economy inflates without limit,
     * which is the failure mode this method exists to make impossible to ship.
     */
    public static double returnToPlayer() {
        double total = 0.0;
        for (SlotSymbol symbol : SlotSymbol.values()) {
            double p = probability(symbol);
            total += p * p * p * symbol.tripleMultiplier();
        }
        return total + twoCherryProbability() * TWO_CHERRY_MULTIPLIER;
    }

    /** The house's long-run cut of everything wagered. The complement of {@link #returnToPlayer()}. */
    public static double houseEdge() {
        return 1.0 - returnToPlayer();
    }

    /** The chance of exactly two cherries — the three-cherry case is a triple and pays far more. */
    public static double twoCherryProbability() {
        double p = probability(SlotSymbol.CHERRY);
        // Three ways to place the odd reel out, hence the 3.
        return REELS * p * p * (1.0 - p);
    }

    /** The chance of any win at all, paying or not. Useful for describing the machine to a player. */
    public static double winProbability() {
        double total = twoCherryProbability();
        for (SlotSymbol symbol : SlotSymbol.values()) {
            double p = probability(symbol);
            total += p * p * p;
        }
        return total;
    }

    /** The chance of the top prize: three sevens. */
    public static double jackpotProbability() {
        double p = probability(SlotSymbol.SEVEN);
        return p * p * p;
    }
}
