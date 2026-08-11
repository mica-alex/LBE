package com.micatechnologies.minecraft.lbe.casino.highlow;

import com.micatechnologies.minecraft.lbe.casino.CasinoOdds;
import com.micatechnologies.minecraft.lbe.casino.GameResult;
import com.micatechnologies.minecraft.lbe.casino.cards.Card;
import com.micatechnologies.minecraft.lbe.casino.cards.Deck;
import com.micatechnologies.minecraft.lbe.casino.cards.Rank;
import com.micatechnologies.minecraft.lbe.casino.cards.Suit;
import java.util.Random;

/**
 * A card is shown; call whether the next one is higher or lower.
 *
 * <p>Ported from the Discord bot's {@code highlow_game.py}, with <b>one deliberate rules change</b>:
 * the payout is odds-weighted rather than even money. The bot's own docstring called its rules
 * "casual — no odds-weighted payout", and that phrase was doing a great deal of work.
 *
 * <h2>Why the change was necessary</h2>
 *
 * <p>The player sees the base card <b>before</b> choosing a direction. Under even money both
 * directions paid the same, so "call the side with more cards left" was always correct and always
 * available — on a base of 2 you call higher and win 48 times in 51. That returned <b>150.7%</b> of
 * everything staked: every dollar came back as a dollar fifty, at whatever rate a player could press
 * a button. Not an exploit anyone had to discover; the obvious way to play.
 *
 * <h2>What it does now</h2>
 *
 * <p>Each direction pays the inverse of its actual chance, scaled to
 * {@link CasinoOdds#STANDARD_RETURN}: calling higher on a 3 pays 1.06×, calling lower on it pays
 * 11.62×, and the ladder is symmetric about the eight. <b>Every call returns the same 97%</b> —
 * which is roulette's property, and the thing that makes a game a game rather than a lever: there is
 * no longer a better side to pick, only a safer one and a bolder one.
 *
 * <p>Both multipliers are computed before the player commits and shown on the buttons, so the choice
 * is informed rather than a trap. A near-certain call paying barely more than the stake looks odd
 * for a moment and is exactly correct.
 */
public final class HighLowGame {

    /** What an equal rank returns: the stake back, as in the bot. */
    public static final double PUSH_MULTIPLIER = 1.0;

    /** The 51 cards a player has not seen once the base card is face up. */
    public static final int UNSEEN = 51;

    /** Which way the next card is called to go. */
    public enum Call {
        HIGHER,
        LOWER
    }

    private final Card base;
    private final Deck deck;
    private boolean resolved;

    /**
     * Deals the base card and waits for a call.
     *
     * <p><b>A two or an ace is never the base card.</b> Those are the only ranks with a call that
     * cannot win at all — nothing is below a two — and, worse, their one legal call is a
     * near-certainty that honest pricing values below the stake: calling higher on a two wins 48
     * times in 51 and so pays 0.97×. A hand whose only move is "win and still lose three cents" is
     * not a hand, so it is not dealt. Skipping those eight cards leaves every hand with two real
     * choices, both paying more than they cost.
     *
     * <p>This does not touch the return: every call is priced at
     * {@link CasinoOdds#STANDARD_RETURN} whatever the base card, so removing some base cards removes
     * no edge and creates none.
     */
    public HighLowGame(Random random) {
        this.deck = new Deck(random);
        Card dealt = deck.deal();
        while (!isPlayableBase(dealt)) {
            dealt = deck.deal();
        }
        this.base = dealt;
    }

    /**
     * Whether a card makes a hand worth playing — that is, whether both calls can win.
     *
     * <p>True for 3 through king. See the constructor for why the other two are skipped.
     */
    public static boolean isPlayableBase(Card card) {
        return cardsAbove(card) > 0 && cardsBelow(card) > 0;
    }

    /** The card on show. */
    public Card base() {
        return base;
    }

    /**
     * Turns the next card and settles.
     *
     * @throws IllegalStateException if called twice. A second call would deal another card against
     *     a stake that has already been settled, which is money moved for a game nobody played.
     */
    public Result call(Call call) {
        if (resolved) {
            throw new IllegalStateException("This hand has already been resolved");
        }
        resolved = true;
        return new Result(base, deck.deal(), call);
    }

    /**
     * How many of the 51 unseen cards beat {@code card}.
     *
     * <p>Public because it is the number a player works out in their head, and a screen that shows
     * it is being honest rather than generous — see the class warning.
     */
    public static int cardsAbove(Card card) {
        return 4 * (Rank.ACE.value() - card.value());
    }

    /** How many of the 51 unseen cards are below {@code card}. */
    public static int cardsBelow(Card card) {
        return 4 * (card.value() - Rank.TWO.value());
    }

    /** How many of the 51 unseen cards tie with {@code card}: always 3. */
    public static int cardsEqual() {
        return 3;
    }

    /**
     * How many unseen cards make {@code call} on {@code base} a winner.
     *
     * <p>Zero means the call cannot win — calling higher on an ace, or lower on a two — and such a
     * call must be refused rather than priced, which {@link #isCallable} exists to check.
     */
    public static int winningCards(Card base, Call call) {
        return call == Call.HIGHER ? cardsAbove(base) : cardsBelow(base);
    }

    /** Whether {@code call} on {@code base} can win at all. */
    public static boolean isCallable(Card base, Call call) {
        return winningCards(base, call) > 0;
    }

    /**
     * What {@code call} pays if it comes in, "for 1".
     *
     * <p>The inverse of its true chance, scaled so every legal call on every base card returns the
     * same {@link CasinoOdds#STANDARD_RETURN}. Below 1 for a near-certain call, which is correct:
     * something that wins 48 times in 51 cannot pay more than it costs without being free money.
     *
     * @return 0 for a call that cannot win.
     */
    public static double payoutFor(Card base, Call call) {
        int winners = winningCards(base, call);
        if (winners <= 0) {
            return 0.0;
        }
        double winChance = winners / (double) UNSEEN;
        double pushChance = cardsEqual() / (double) UNSEEN;
        return CasinoOdds.round(CasinoOdds.payoutFor(winChance, pushChance));
    }

    /**
     * The return on one specific call — the same for every legal one, up to rounding.
     *
     * <p>This is the property the whole redesign exists to produce, so it is worth being able to ask
     * about directly rather than inferring it from a simulation.
     */
    public static double returnToPlayer(Card base, Call call) {
        int winners = winningCards(base, call);
        if (winners <= 0) {
            return 0.0;
        }
        return winners / (double) UNSEEN * payoutFor(base, call)
            + cardsEqual() / (double) UNSEEN * PUSH_MULTIPLIER;
    }

    /**
     * The worst return available across every legal call on every base card.
     *
     * <p>With odds-weighted payouts this sits at the target, a hair either side of it from rounding
     * the printed multipliers to two decimals. The old even-money rules had a spread of 0.61 to 1.88
     * depending on which card came up and which way you called — which is precisely why a player
     * could pick the good end of it every time.
     */
    public static double worstReturnToPlayer() {
        return extremeReturn(true);
    }

    /** The best return available across every legal call. See {@link #worstReturnToPlayer()}. */
    public static double bestReturnToPlayer() {
        return extremeReturn(false);
    }

    private static double extremeReturn(boolean lowest) {
        double extreme = lowest ? Double.MAX_VALUE : 0.0;
        for (Rank rank : Rank.values()) {
            Card card = new Card(rank, Suit.SPADES);
            if (!isPlayableBase(card)) {
                continue;   // never dealt as a base card, so its prices are not on offer
            }
            for (Call call : Call.values()) {
                if (!isCallable(card, call)) {
                    continue;
                }
                double rtp = returnToPlayer(card, call);
                extreme = lowest ? Math.min(extreme, rtp) : Math.max(extreme, rtp);
            }
        }
        return extreme;
    }

    /** One resolved hand. */
    public static final class Result implements GameResult {

        private final Card base;
        private final Card next;
        private final Call call;

        Result(Card base, Card next, Call call) {
            this.base = base;
            this.next = next;
            this.call = call;
        }

        public Card base() {
            return base;
        }

        public Card next() {
            return next;
        }

        public Call call() {
            return call;
        }

        @Override
        public double totalReturnMultiplier() {
            if (next.value() == base.value()) {
                return PUSH_MULTIPLIER;
            }
            boolean higher = next.value() > base.value();
            boolean correct = (call == Call.HIGHER) == higher;
            // The price was fixed the moment the base card was shown, so it is recomputed from the
            // base card rather than stored — the same input gives the same answer, and there is no
            // way for a stale multiplier to be paid against a different card.
            return correct ? payoutFor(base, call) : 0.0;
        }

        /** What this call was paying before it was made, "for 1". */
        public double offeredMultiplier() {
            return payoutFor(base, call);
        }

        @Override
        public String describe() {
            String hands = base + " then " + next + " — ";
            if (next.value() == base.value()) {
                return hands + "a tie; your bet is returned.";
            }
            return totalReturnMultiplier() > 0.0 ? hands + "you called it!" : hands + "wrong call.";
        }
    }
}
