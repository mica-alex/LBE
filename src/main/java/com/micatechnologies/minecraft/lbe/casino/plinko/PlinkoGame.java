package com.micatechnologies.minecraft.lbe.casino.plinko;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.Arrays;
import java.util.Random;

/**
 * Plinko: a ball falls through eight rows of pegs, and where it lands decides the payout.
 *
 * <p>Ported from the Discord bot's {@code plinko_game.py}, with its three risk levels and their
 * multiplier tables unchanged.
 *
 * <p>Each row nudges the ball left or right with even chance, so the landing slot is the number of
 * rights: a binomial distribution over nine buckets, heavily weighted to the centre. The tables pay
 * big at the edges and less than the stake in the middle, which is where the house edge comes from —
 * the middle is where the ball nearly always goes.
 *
 * <p>The path is kept so a client can animate the drop peg by peg rather than cutting to the answer.
 */
public final class PlinkoGame {

    /** Rows of pegs. Eight gives nine landing slots. */
    public static final int ROWS = 8;

    /** How much of a swing the player is asking for. */
    public enum Risk {

        LOW("Low Risk", new double[] {3, 1.5, 1.2, 1.05, 0.5, 1.05, 1.2, 1.5, 3}),
        MEDIUM("Medium Risk", new double[] {8, 2.5, 1.3, 0.8, 0.3, 0.8, 1.3, 2.5, 8}),
        HIGH("High Risk", new double[] {22, 4, 1.2, 0.4, 0.2, 0.4, 1.2, 4, 22});

        private final String label;
        private final double[] multipliers;

        Risk(String label, double[] multipliers) {
            this.label = label;
            this.multipliers = multipliers;
        }

        public String label() {
            return label;
        }

        /** What each of the nine slots pays, "for 1", left to right. */
        public double[] multipliers() {
            return Arrays.copyOf(multipliers, multipliers.length);
        }

        public double multiplierFor(int slot) {
            return multipliers[slot];
        }
    }

    /** The default the bot uses when no risk level is named. */
    public static final Risk DEFAULT_RISK = Risk.MEDIUM;

    private PlinkoGame() {
        throw new AssertionError("No instances.");
    }

    /** Drops one ball. */
    public static Result drop(Risk risk, Random random) {
        boolean[] path = new boolean[ROWS];
        int slot = 0;
        for (int row = 0; row < ROWS; row++) {
            boolean right = random.nextBoolean();
            path[row] = right;
            if (right) {
                slot++;
            }
        }
        return new Result(risk, path, slot);
    }

    /**
     * The chance of landing in {@code slot}: {@code C(8, slot) / 256}.
     *
     * <p>Binomial, because each row is an independent even chance. The centre slot is 70/256 — more
     * than a quarter of every drop — and the edges are 1/256 each, which is what makes a 22× edge
     * payout affordable.
     */
    public static double slotProbability(int slot) {
        return binomial(ROWS, slot) / Math.pow(2, ROWS);
    }

    /** The long-run fraction of money wagered that comes back at this risk level. */
    public static double returnToPlayer(Risk risk) {
        double total = 0.0;
        for (int slot = 0; slot <= ROWS; slot++) {
            total += slotProbability(slot) * risk.multiplierFor(slot);
        }
        return total;
    }

    private static double binomial(int n, int k) {
        double result = 1.0;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /** One drop. */
    public static final class Result implements GameResult {

        private final Risk risk;
        private final boolean[] path;
        private final int slot;

        Result(Risk risk, boolean[] path, int slot) {
            this.risk = risk;
            this.path = path;
            this.slot = slot;
        }

        /** Which way the ball went at each row; true is right. For the animation. */
        public boolean[] path() {
            return Arrays.copyOf(path, path.length);
        }

        /** Where it landed, 0-8. */
        public int slot() {
            return slot;
        }

        public Risk risk() {
            return risk;
        }

        @Override
        public double totalReturnMultiplier() {
            return risk.multiplierFor(slot);
        }

        @Override
        public String describe() {
            double multiplier = totalReturnMultiplier();
            String amount = multiplier == Math.floor(multiplier)
                ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
            return "Landed on " + amount + "x";
        }
    }
}
