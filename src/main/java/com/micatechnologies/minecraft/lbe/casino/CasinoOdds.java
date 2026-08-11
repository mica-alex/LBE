package com.micatechnologies.minecraft.lbe.casino;

/**
 * The house edge, in one place, for the games that need one applied.
 *
 * <p>Some games here carry their edge structurally and are not listed: roulette's comes from
 * spinning 37 pockets and paying 36, plinko's from a payout table shaped against a binomial
 * distribution, slots' from its symbol weights. Those are already right and nothing here touches
 * them.
 *
 * <p>The rest arrived from the Discord bot returning <b>100% or more</b> — a fair coin paying
 * 2-for-1, a war where ties push, and a high-low where the player picks a side after seeing the
 * card. All three are fine against a Discord score, where the currency is engagement and inflation
 * costs nobody anything. Against a balance that also buys plots and shop goods, a game at 100% funds
 * nothing and a game at 150% is an income.
 *
 * <h2>The principle</h2>
 *
 * <p><b>Every choice a player can make must have the same return.</b> That is what roulette gets
 * right and what high-low got wrong: if one option pays better than another, the game stops being a
 * game and becomes a lever. Getting there means paying the inverse of each outcome's true
 * probability, scaled by the figure below — so a near-certain call pays barely more than the stake
 * and a long shot pays handsomely, and neither is the "right" answer.
 *
 * <p>These are honest numbers, and the screens show them. A machine that hides its edge has
 * something to hide.
 */
public final class CasinoOdds {

    /**
     * What the even-money games return over time: 97%, so the house keeps 3%.
     *
     * <p>In the range players tolerate without feeling robbed — real blackjack and baccarat sit near
     * here, roulette at 97.3% — and enough to be a real sink at the volume a casino runs at.
     */
    public static final double STANDARD_RETURN = 0.97;

    /**
     * What keno returns: 92%.
     *
     * <p>Deliberately worse than the rest, because keno really is: a real one runs 70-80%, and its
     * appeal is the size of the top prize rather than the odds of reaching it. 92% is generous by
     * that standard and far kinder than the bot's table, which ran 45-75% and kept more than half of
     * everything staked on a ten-pick ticket.
     */
    public static final double KENO_RETURN = 0.92;

    private CasinoOdds() {
        throw new AssertionError("No instances.");
    }

    /**
     * The payout that gives {@code targetReturn} on a bet that wins with probability
     * {@code winChance} and pushes with probability {@code pushChance}.
     *
     * <p>Solves {@code winChance × payout + pushChance × 1 = targetReturn}. A push returns the stake
     * and so contributes its own probability to the return, which is exactly why war came out at
     * 100% when the tie was simply handed back — the push has to be paid for out of the win.
     *
     * @return the multiplier a win should pay, "for 1". May be below 1 for a near-certain bet, which
     *     is correct and not a bug: something that wins 48 times in 51 cannot pay more than it
     *     costs, or it would be free money. The screens print the figure so nobody is surprised.
     */
    public static double payoutFor(double winChance, double pushChance, double targetReturn) {
        if (winChance <= 0.0) {
            // A bet that cannot win has no honest price. Callers must refuse the bet instead.
            return 0.0;
        }
        return (targetReturn - pushChance) / winChance;
    }

    /** {@link #payoutFor(double, double, double)} at the standard 97%. */
    public static double payoutFor(double winChance, double pushChance) {
        return payoutFor(winChance, pushChance, STANDARD_RETURN);
    }

    /** Rounds a payout to something a screen can print without a wall of decimals. */
    public static double round(double multiplier) {
        return Math.round(multiplier * 100.0) / 100.0;
    }
}
