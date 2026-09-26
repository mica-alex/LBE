package com.micatechnologies.minecraft.lbe.casino.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Several stakes in one round, each riding on one of the round's hands: blackjack's base bet, the
 * second hand a split opens, and the extra stake a double adds.
 *
 * <p>Every stake here is an ordinary {@link Wager}, taken through {@link CasinoBank} the same way a
 * single bet is, so nothing about SUM changes: a set is only bookkeeping over wagers that already
 * obey the one-settlement rule. What it adds is the guarantee across all of them: {@link #settle}
 * or {@link #cancelAll} settles <b>every</b> stake exactly once, and a second call does nothing.
 *
 * <p>No SUM types, like everything in this package except the bridge.
 */
public final class WagerSet {

    private final List<Wager> wagers = new ArrayList<>();
    private final List<Integer> hands = new ArrayList<>();
    private boolean settled;

    /** What happened when the set was settled. */
    public static final class Outcome {
        private final double paid;
        private final int failures;

        Outcome(double paid, int failures) {
            this.paid = paid;
            this.failures = failures;
        }

        /** Total handed back to the player across every stake, returned stakes included. */
        public double paid() {
            return paid;
        }

        /**
         * How many stakes could not be settled. The bank logs why and keeps each one held, so the
         * money is safe, but the player should be told.
         */
        public int failures() {
            return failures;
        }
    }

    /** Adds a stake riding on hand {@code hand}. */
    public void add(int hand, Wager wager) {
        if (settled) {
            throw new IllegalStateException("This set has already been settled");
        }
        wagers.add(wager);
        hands.add(hand);
    }

    /** Everything staked so far. */
    public double staked() {
        double total = 0.0;
        for (Wager wager : wagers) {
            total += wager.amount();
        }
        return total;
    }

    public int size() {
        return wagers.size();
    }

    /**
     * Settles every stake by its hand's return, "for 1": 0 loses the stake to the house, anything
     * else pays {@code round(stake * return)} in total.
     *
     * @param returnsByHand what each hand returns per stake, indexed by hand
     * @param round the money rounding the caller uses everywhere else
     */
    public Outcome settle(double[] returnsByHand, DoubleUnaryOperator round) {
        if (settled) {
            return new Outcome(0.0, 0);
        }
        settled = true;
        double paid = 0.0;
        int failures = 0;
        for (int i = 0; i < wagers.size(); i++) {
            Wager wager = wagers.get(i);
            int hand = hands.get(i);
            double perStake = hand >= 0 && hand < returnsByHand.length ? returnsByHand[hand] : 0.0;
            double total = round.applyAsDouble(wager.amount() * perStake);
            boolean ok = total > 0.0 ? wager.payOut(total) : wager.loseToHouse();
            if (ok) {
                paid += total;
            } else {
                failures++;
            }
        }
        return new Outcome(paid, failures);
    }

    /** Refunds every stake, for a round that did not happen. Does nothing once settled. */
    public boolean cancelAll() {
        if (settled) {
            return true;
        }
        settled = true;
        boolean all = true;
        for (Wager wager : wagers) {
            all &= wager.cancel();
        }
        return all;
    }
}
