package com.micatechnologies.minecraft.lbe.casino.coinflip;

import com.micatechnologies.minecraft.lbe.casino.GameResult;
import java.util.Random;

/**
 * Call heads or tails; a correct call pays 2× the stake.
 *
 * <p>Ported from the Discord bot's {@code !coinflip} (its {@code COINFLIP_PAYOUT} is 2), rules
 * unchanged.
 *
 * <p><b>This game has no house edge at all.</b> A fair coin paying 2-for-1 returns exactly 100% of
 * everything wagered, forever — see {@link #returnToPlayer()}, which is 1.0 and is pinned by a test.
 * That is fine and intended in Discord, where the currency is a score; it is a different proposition
 * against a server economy that also buys plots and shop goods. It does not <i>drain</i> money, so
 * the casino cannot fund itself from it, and it does not <i>print</i> money either — it is pure
 * variance, and over time the house and the players both end up roughly where they started while
 * individual balances swing hard.
 *
 * <p>If an edge is ever wanted, the conventional fix is to pay 1.95 rather than 2, which costs a
 * player 2.5% and is invisible at a glance. That is a rules change, so it is not made here.
 */
public final class CoinFlipGame {

    /** What a correct call returns, "for 1". The bot's {@code COINFLIP_PAYOUT}. */
    public static final double WIN_MULTIPLIER = 2.0;

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
     * The long-run fraction of money wagered that comes back: exactly 1.0.
     *
     * <p>Half the flips return twice the stake and half return nothing, so
     * {@code 0.5 × 2 + 0.5 × 0 = 1}. There is no rounding here and no approximation — the figure is
     * exact, and so is the conclusion that the house makes nothing.
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
