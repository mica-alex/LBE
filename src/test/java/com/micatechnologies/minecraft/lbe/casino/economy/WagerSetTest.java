package com.micatechnologies.minecraft.lbe.casino.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every stake in a set is settled exactly once, by its own hand's result. */
class WagerSetTest {

    /** A wager that records how it was settled, and refuses a second settlement. */
    private static final class Recorder implements Wager {
        final double amount;
        int settlements;
        double paid = -1.0;
        boolean lost;
        boolean cancelled;

        Recorder(double amount) {
            this.amount = amount;
        }

        @Override
        public double amount() {
            return amount;
        }

        @Override
        public boolean payOut(double totalReturn) {
            settlements++;
            paid = totalReturn;
            return true;
        }

        @Override
        public boolean loseToHouse() {
            settlements++;
            lost = true;
            return true;
        }

        @Override
        public boolean cancel() {
            settlements++;
            cancelled = true;
            return true;
        }
    }

    @Test
    @DisplayName("each stake is paid by its own hand, once")
    void settlesByHand() {
        WagerSet set = new WagerSet();
        Recorder base = new Recorder(10.0);
        Recorder split = new Recorder(10.0);
        Recorder doubled = new Recorder(10.0);
        set.add(0, base);
        set.add(1, split);
        set.add(1, doubled);
        assertEquals(30.0, set.staked(), 1e-9);

        WagerSet.Outcome outcome = set.settle(new double[] {0.0, 2.0}, x -> x);
        assertTrue(base.lost, "hand 0 lost");
        assertEquals(20.0, split.paid, 1e-9);
        assertEquals(20.0, doubled.paid, 1e-9);
        assertEquals(40.0, outcome.paid(), 1e-9);
        assertEquals(0, outcome.failures());

        set.settle(new double[] {2.0, 2.0}, x -> x);
        set.cancelAll();
        assertEquals(1, base.settlements, "a second settle or cancel does nothing");
        assertEquals(1, split.settlements);
    }

    @Test
    @DisplayName("an abandoned round refunds every stake")
    void cancelRefundsAll() {
        WagerSet set = new WagerSet();
        Recorder a = new Recorder(5.0);
        Recorder b = new Recorder(5.0);
        set.add(0, a);
        set.add(0, b);
        assertTrue(set.cancelAll());
        assertTrue(a.cancelled && b.cancelled);
        assertEquals(1, a.settlements);
    }
}
