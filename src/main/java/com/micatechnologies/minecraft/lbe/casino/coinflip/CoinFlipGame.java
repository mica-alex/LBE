package com.micatechnologies.minecraft.lbe.casino.coinflip;

import com.micatechnologies.minecraft.lbe.casino.CasinoOdds;
import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.Random;

/**
 * Call heads or tails.
 *
 * <p>Ported from the Discord bot's {@code !coinflip}, with <b>one deliberate rules change</b>: it
 * paid a flat 2× ({@code COINFLIP_PAYOUT}), which on a fair coin returns exactly 100% of everything
 * staked, forever. The house made nothing and lost nothing — pure variance, and a table the casino
 * could never fund itself from.
 *
 * <p>It now pays 1.94×, giving the standard 3% edge. That is a 3% cut on a
 * number most players never look at closely, and it is the difference between a table that pays for
 * itself and one that is decoration.
 */
public final class CoinFlipGame {

    /**
     * What a correct call returns, "for 1": 1.94.
     *
     * <p>The bot paid a round 2, which is break-even on a fair coin. This is that scaled by
     * {@link CasinoOdds#STANDARD_RETURN} — {@code 2 × 0.97} — so the coin stays fair and the price
     * of playing is the payout rather than a rigged flip. A weighted coin would be the other way to
     * get an edge and a far worse one: players can count outcomes.
     */
    public static final double WIN_MULTIPLIER = 2.0 * CasinoOdds.STANDARD_RETURN;

    private CoinFlipGame() {
        throw new AssertionError("No instances.");
    }

    /** The two faces. */
    public enum Side {
        HEADS,
        TAILS;

        public Side other() {
            return this == HEADS ? TAILS : HEADS;
        }
    }

    /** Flips once and reports whether {@code call} was right. */
    public static Result flip(Side call, Random random) {
        Side landed = random.nextBoolean() ? Side.HEADS : Side.TAILS;
        return new Result(call, landed);
    }

    /**
     * The long-run fraction of money wagered that comes back: 97%.
     *
     * <p>Half the flips return {@link #WIN_MULTIPLIER} and half return nothing, so the figure is
     * simply half of it. Exact, with no rounding anywhere.
     */
    public static double returnToPlayer() {
        return 0.5 * WIN_MULTIPLIER;
    }

    /** One flip. */
    public static final class Result implements GameResult {

        private final Side call;
        private final Side landed;

        Result(Side call, Side landed) {
            this.call = call;
            this.landed = landed;
        }

        public Side call() {
            return call;
        }

        public Side landed() {
            return landed;
        }

        @Override
        public double totalReturnMultiplier() {
            return call == landed ? WIN_MULTIPLIER : 0.0;
        }

        @Override
        public String describe() {
            String face = landed == Side.HEADS ? "Heads" : "Tails";
            return call == landed ? face + " — you called it!" : face + " — bad luck.";
        }
    }
}
